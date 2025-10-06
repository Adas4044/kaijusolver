import java.io.*;
import java.util.*;

/**
 * Utility to load movement commands from a movements.txt file.
 */
public class MovementFileLoader {
    
    /**
     * Load commands from a movements.txt file and apply them to a simulator.
     * @param filename Path to the movements.txt file
     * @param simulator GameSimulator instance to apply commands to
     * @return Number of commands successfully loaded
     */
    public static int loadAndApplyCommands(String filename, GameSimulator simulator) throws IOException {
        List<CommandEntry> commands = loadCommands(filename);
        
        int successCount = 0;
        int totalCost = 0;
        
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              LOADING COMMANDS FROM " + filename + "              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        for (CommandEntry cmd : commands) {
            boolean success;
            if (cmd.floor >= 0) {
                success = simulator.placeCommand(cmd.x, cmd.y, cmd.commandType, cmd.floor);
            } else {
                success = simulator.placeCommand(cmd.x, cmd.y, cmd.commandType);
            }
            
            if (success) {
                successCount++;
                totalCost += cmd.commandType.getCost();
                String floorInfo = cmd.floor >= 0 ? " floor " + cmd.floor : "";
                System.out.println("✓ [" + cmd.x + "," + cmd.y + "] " + cmd.commandType.name() + 
                                 floorInfo + " ($" + cmd.commandType.getCost() + ")");
            } else {
                System.out.println("✗ [" + cmd.x + "," + cmd.y + "] " + cmd.commandType.name() + 
                                 " - FAILED (invalid position or out of budget)");
            }
        }
        
        System.out.println("\nLoaded " + successCount + "/" + commands.size() + " commands");
        System.out.println("Total cost: $" + totalCost);
        System.out.println("Budget remaining: $" + simulator.getBudgetRemaining() + " / $200\n");
        
        return successCount;
    }
    
    /**
     * Load commands from a movements.txt file.
     * @param filename Path to the movements.txt file
     * @return List of command entries
     */
    public static List<CommandEntry> loadCommands(String filename) throws IOException {
        List<CommandEntry> commands = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    CommandEntry cmd = parseCommandLine(line);
                    commands.add(cmd);
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Skipping invalid line " + lineNumber + ": " + line);
                    System.err.println("  Error: " + e.getMessage());
                }
            }
        }
        
        return commands;
    }
    
    /**
     * Parse a single command line.
     * Format: x,y,command[,floor]
     */
    private static CommandEntry parseCommandLine(String line) {
        // Remove inline comments
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            line = line.substring(0, commentIndex).trim();
        }
        
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Expected format: x,y,command[,floor]");
        }
        
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            String commandStr = parts[2].trim().toUpperCase();
            
            CommandType commandType;
            try {
                commandType = CommandType.valueOf(commandStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid command: " + commandStr + 
                    ". Valid: TURN_N, TURN_S, TURN_E, TURN_W, STOMP, POWERUP");
            }
            
            int floor = -1;
            if (parts.length >= 4) {
                floor = Integer.parseInt(parts[3].trim());
            }
            
            return new CommandEntry(x, y, commandType, floor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format: " + e.getMessage());
        }
    }
    
    /**
     * Represents a command entry from the file.
     */
    public static class CommandEntry {
        public final int x;
        public final int y;
        public final CommandType commandType;
        public final int floor; // -1 if not specified
        
        public CommandEntry(int x, int y, CommandType commandType, int floor) {
            this.x = x;
            this.y = y;
            this.commandType = commandType;
            this.floor = floor;
        }
        
        @Override
        public String toString() {
            String floorStr = floor >= 0 ? ",floor=" + floor : "";
            return "Command[" + x + "," + y + "," + commandType + floorStr + "]";
        }
    }
}
