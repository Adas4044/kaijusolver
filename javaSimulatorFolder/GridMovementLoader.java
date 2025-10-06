import java.io.*;
import java.util.*;

/**
 * Loader for grid-based movement files.
 * Parses a 2D grid of movement commands and applies them to the simulator.
 */
public class GridMovementLoader {
    
    /**
     * Load commands from a grid-based movements.txt and apply to simulator.
     */
    public static int loadAndApplyCommands(String filename, GameSimulator simulator, String[][] boardLayout) throws IOException {
        String[][] movementGrid = loadMovementGrid(filename);
        
        // Validate dimensions match
        int height = boardLayout.length;
        int width = boardLayout[0].length;
        
        if (movementGrid.length != height || movementGrid[0].length != width) {
            throw new IOException("Movement grid dimensions (" + movementGrid[0].length + "x" + movementGrid.length + 
                                ") don't match board dimensions (" + width + "x" + height + ")");
        }
        
        List<CommandPlacement> placements = parseMovementGrid(movementGrid, boardLayout);
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         LOADING COMMANDS FROM GRID: " + filename + "         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        int successCount = 0;
        int totalCost = 0;
        
        for (CommandPlacement placement : placements) {
            boolean success;
            if (placement.floor >= 0) {
                success = simulator.placeCommand(placement.x, placement.y, placement.commandType, placement.floor);
            } else {
                success = simulator.placeCommand(placement.x, placement.y, placement.commandType);
            }
            
            if (success) {
                successCount++;
                totalCost += placement.commandType.getCost();
                String floorInfo = placement.floor >= 0 ? " floor " + placement.floor : "";
                System.out.println("✓ [" + placement.x + "," + placement.y + "] " + 
                                 placement.code + " → " + placement.commandType.name() + 
                                 floorInfo + " ($" + placement.commandType.getCost() + ")");
            } else {
                System.out.println("✗ [" + placement.x + "," + placement.y + "] " + 
                                 placement.code + " - FAILED (invalid position or out of budget)");
            }
        }
        
        System.out.println("\nLoaded " + successCount + "/" + placements.size() + " commands");
        System.out.println("Total cost: $" + totalCost);
        System.out.println("Budget remaining: $" + simulator.getBudgetRemaining() + " / $200\n");
        
        return successCount;
    }
    
    /**
     * Load the movement grid from file (Java array format).
     */
    private static String[][] loadMovementGrid(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comment lines
                if (line.trim().startsWith("#")) {
                    continue;
                }
                content.append(line).append("\n");
            }
        }
        
        // Parse the Java array format
        String layoutStr = content.toString()
                .replaceAll("String\\[\\]\\[\\]\\s+movements\\s*=\\s*", "")
                .replaceAll(";", "")
                .trim();
        
        if (layoutStr.isEmpty() || !layoutStr.contains("{")) {
            throw new IOException("No movement grid found in file. Expected Java array format: String[][] movements = {...};");
        }
        
        return parseJavaArrayFormat(layoutStr);
    }
    
    /**
     * Parse Java array format string into 2D array.
     */
    private static String[][] parseJavaArrayFormat(String layoutStr) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        // Remove outer braces
        int firstBrace = layoutStr.indexOf('{');
        int lastBrace = layoutStr.lastIndexOf('}');
        
        if (firstBrace == -1 || lastBrace == -1) {
            throw new IOException("Invalid array format - missing braces");
        }
        
        layoutStr = layoutStr.substring(firstBrace + 1, lastBrace);
        
        // Split by rows (looking for patterns like {...}, )
        String[] rowStrings = layoutStr.split("\\},\\s*\\{");
        
        for (String rowStr : rowStrings) {
            rowStr = rowStr.replaceAll("[{}]", "").trim();
            String[] cells = rowStr.split(",\\s*");
            
            // Remove quotes from each cell
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cells[i].replaceAll("\"", "").trim();
            }
            
            rows.add(cells);
        }
        
        if (rows.isEmpty()) {
            throw new IOException("No rows parsed from movement grid");
        }
        
        return rows.toArray(new String[0][]);
    }
    
    /**
     * Parse movement grid into command placements.
     */
    private static List<CommandPlacement> parseMovementGrid(String[][] movementGrid, String[][] boardLayout) {
        List<CommandPlacement> placements = new ArrayList<>();
        int height = movementGrid.length;
        int width = movementGrid[0].length;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String cell = movementGrid[y][x];
                String tile = boardLayout[y][x];
                
                // Skip empty cells
                if (cell.equals(".") || cell.equals("..")) {
                    continue;
                }
                
                // Check if this tile can have commands
                if (!isCommandable(tile)) {
                    System.err.println("Warning: Command '" + cell + "' at [" + x + "," + y + 
                                     "] ignored - tile '" + tile + "' cannot have commands");
                    continue;
                }
                
                // Parse commands
                int floors = getFloors(tile);
                
                if (floors == 2) {
                    // Multi-floor building - expect 2 characters
                    if (cell.length() != 2) {
                        System.err.println("Warning: Multi-floor building at [" + x + "," + y + 
                                         "] expects 2 characters, got: " + cell);
                        continue;
                    }
                    
                    // First char = top floor (1), second char = bottom floor (0)
                    char topCmd = cell.charAt(0);
                    char bottomCmd = cell.charAt(1);
                    
                    // Add top floor command
                    CommandType topType = parseCommandCode(topCmd);
                    if (topType != null) {
                        placements.add(new CommandPlacement(x, y, topType, 1, String.valueOf(topCmd)));
                    }
                    
                    // Add bottom floor command
                    CommandType bottomType = parseCommandCode(bottomCmd);
                    if (bottomType != null) {
                        placements.add(new CommandPlacement(x, y, bottomType, 0, String.valueOf(bottomCmd)));
                    }
                } else {
                    // Single floor - expect 1 character
                    if (cell.length() != 1) {
                        System.err.println("Warning: Single-floor tile at [" + x + "," + y + 
                                         "] expects 1 character, got: " + cell);
                        continue;
                    }
                    
                    CommandType type = parseCommandCode(cell.charAt(0));
                    if (type != null) {
                        placements.add(new CommandPlacement(x, y, type, -1, cell));
                    }
                }
            }
        }
        
        return placements;
    }
    
    /**
     * Parse a single command character.
     */
    private static CommandType parseCommandCode(char code) {
        switch (Character.toUpperCase(code)) {
            case 'L': return CommandType.TURN_W;  // Left = West
            case 'R': return CommandType.TURN_E;  // Right = East
            case 'U': return CommandType.TURN_N;  // Up = North
            case 'D': return CommandType.TURN_S;  // Down = South
            case 'P': return CommandType.POWERUP;
            case 'S': return CommandType.STOMP;
            case '.': return null;  // No command
            default:
                System.err.println("Warning: Unknown command code: " + code);
                return null;
        }
    }
    
    private static boolean isCommandable(String tile) {
        return tile.equals("h") || tile.equals("hh") || 
               tile.equals("H") || tile.equals("HH") || 
               tile.equals("P");
    }
    
    private static int getFloors(String tile) {
        if (tile.equals("hh") || tile.equals("HH")) {
            return 2;
        }
        return 1;
    }
    
    /**
     * Represents a command placement.
     */
    static class CommandPlacement {
        int x, y;
        CommandType commandType;
        int floor; // -1 for single floor
        String code; // Original code for display
        
        CommandPlacement(int x, int y, CommandType commandType, int floor, String code) {
            this.x = x;
            this.y = y;
            this.commandType = commandType;
            this.floor = floor;
            this.code = code;
        }
    }
}
