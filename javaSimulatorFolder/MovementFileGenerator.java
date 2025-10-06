import java.io.*;
import java.util.*;

/**
 * Utility to generate a movements.txt template from a starting.txt board layout.
 * Lists all buildings (h, hh, H, HH) and power plants (P) with their coordinates
 * where commands can be placed.
 */
public class MovementFileGenerator {
    
    public static void main(String[] args) {
        String inputFile = "starting.txt";
        String outputFile = "movements.txt";
        
        if (args.length >= 1) {
            inputFile = args[0];
        }
        if (args.length >= 2) {
            outputFile = args[1];
        }
        
        try {
            generateMovementFile(inputFile, outputFile);
            System.out.println("✓ Generated movement template: " + outputFile);
            System.out.println("\nYou can now edit " + outputFile + " to add commands.");
            System.out.println("\nAvailable commands:");
            System.out.println("  TURN_N, TURN_S, TURN_E, TURN_W  ($10 each)");
            System.out.println("  STOMP                            ($20)");
            System.out.println("  POWERUP                          ($30)");
            System.out.println("\nNote: Multi-floor buildings (hh, HH) can have multiple commands.");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate a movements.txt file from a board layout file.
     * @param inputFile Path to the starting.txt file
     * @param outputFile Path to the output movements.txt file
     */
    public static void generateMovementFile(String inputFile, String outputFile) throws IOException {
        // Read the board layout
        String[][] layout = loadLayout(inputFile);
        
        // Find all buildings and power plants
        List<CommandableLocation> locations = new ArrayList<>();
        
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length; x++) {
                String tile = layout[y][x];
                
                if (isCommandable(tile)) {
                    int floors = getFloors(tile);
                    String type = getTileTypeName(tile);
                    locations.add(new CommandableLocation(x, y, tile, type, floors));
                }
            }
        }
        
        // Group by type first
        Map<String, List<CommandableLocation>> byType = new LinkedHashMap<>();
        byType.put("Small House (1 floor)", new ArrayList<>());
        byType.put("Small House (2 floors)", new ArrayList<>());
        byType.put("Big House (1 floor)", new ArrayList<>());
        byType.put("Big House (2 floors)", new ArrayList<>());
        byType.put("Power Plant", new ArrayList<>());
        
        for (CommandableLocation loc : locations) {
            byType.get(loc.typeName).add(loc);
        }
        
        // Write to movements.txt
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("# Kaiju Cats - Movement Commands");
            writer.println("# Format: x,y,command[,floor]");
            writer.println("#");
            writer.println("# Commands:");
            writer.println("#   TURN_N, TURN_S, TURN_E, TURN_W  - Change cat direction ($10)");
            writer.println("#   STOMP                           - Loop and execute again ($20)");
            writer.println("#   POWERUP                         - Double cat power ($30)");
            writer.println("#");
            writer.println("# For multi-floor buildings (hh, HH), specify floor (0=bottom, 1=top)");
            writer.println("# Top floor commands execute first!");
            writer.println("#");
            writer.println("# Budget: $200 total");
            writer.println("#");
            writer.println("# Example:");
            writer.println("# 1,0,TURN_S        # Single floor building");
            writer.println("# 1,2,TURN_N,0      # Multi-floor building, floor 0");
            writer.println("# 1,2,TURN_E,1      # Multi-floor building, floor 1");
            writer.println("# 5,0,POWERUP       # Power plant");
            writer.println("#");
            writer.println("# ================================================================");
            writer.println();
            
            // Write organized sections
            for (Map.Entry<String, List<CommandableLocation>> entry : byType.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                
                writer.println("# " + entry.getKey() + ":");
                for (CommandableLocation loc : entry.getValue()) {
                    writer.println("# " + loc.x + "," + loc.y + ",COMMAND" + 
                                 (loc.floors > 1 ? ",FLOOR" : "") + 
                                 "   # " + loc.tile);
                }
                writer.println();
            }
            
            writer.println("# ================================================================");
            writer.println("# Add your commands below (uncomment and modify):");
            writer.println("# ================================================================");
            writer.println();
        }
        
        System.out.println("\nFound " + locations.size() + " commandable locations:");
        for (Map.Entry<String, List<CommandableLocation>> entry : byType.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue().size());
            }
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
    
    private static String getTileTypeName(String tile) {
        switch (tile) {
            case "h": return "Small House (1 floor)";
            case "hh": return "Small House (2 floors)";
            case "H": return "Big House (1 floor)";
            case "HH": return "Big House (2 floors)";
            case "P": return "Power Plant";
            default: return "Unknown";
        }
    }
    
    private static String[][] loadLayout(String filename) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] tiles = line.split("\\s+");
                rows.add(tiles);
            }
        }
        
        return rows.toArray(new String[0][]);
    }
    
    static class CommandableLocation {
        int x, y;
        String tile;
        String typeName;
        int floors;
        
        CommandableLocation(int x, int y, String tile, String typeName, int floors) {
            this.x = x;
            this.y = y;
            this.tile = tile;
            this.typeName = typeName;
            this.floors = floors;
        }
    }
}
