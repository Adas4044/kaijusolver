import java.io.*;
import java.util.*;

/**
 * Utility to generate a grid-based movements.txt from a board layout.
 * Creates a 2D grid where each cell can contain movement commands.
 */
public class GridMovementGenerator {
    
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
            generateGridMovementFile(inputFile, outputFile);
            System.out.println("✓ Generated grid-based movement file: " + outputFile);
            System.out.println("\nEdit the grid to add commands where buildings/power plants are located.");
            System.out.println("\nCommand codes:");
            System.out.println("  L = Turn Left (West)   ($10)");
            System.out.println("  R = Turn Right (East)  ($10)");
            System.out.println("  U = Turn Up (North)    ($10)");
            System.out.println("  D = Turn Down (South)  ($10)");
            System.out.println("  P = Powerup            ($30)");
            System.out.println("  S = Stomp              ($20)");
            System.out.println("\nMulti-floor buildings: Use 2 characters (e.g., 'UD' = U on top floor, D on bottom)");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void generateGridMovementFile(String inputFile, String outputFile) throws IOException {
        // Read the board layout
        String[][] layout = loadLayout(inputFile);
        int height = layout.length;
        int width = layout[0].length;
        
        // Create movement grid (initially all dots)
        String[][] movements = new String[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                movements[y][x] = ".";
            }
        }
        
        // Write to movements.txt
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("# Kaiju Cats - Grid-Based Movement Commands");
            writer.println("# This grid mirrors your board layout (starting.txt)");
            writer.println("#");
            writer.println("# Command Codes:");
            writer.println("#   L = Turn Left (West)    - Cat turns west ($10)");
            writer.println("#   R = Turn Right (East)   - Cat turns east ($10)");
            writer.println("#   U = Turn Up (North)     - Cat turns north ($10)");
            writer.println("#   D = Turn Down (South)   - Cat turns south ($10)");
            writer.println("#   P = Powerup             - Double cat power ($30)");
            writer.println("#   S = Stomp               - Loop and execute again ($20)");
            writer.println("#");
            writer.println("# For SINGLE-FLOOR buildings/plants: Use 1 character (e.g., 'D', 'P', 'S')");
            writer.println("# For MULTI-FLOOR buildings: Use 2 characters (e.g., 'UD', 'RL', 'SS')");
            writer.println("#   - First character = TOP floor (executes first)");
            writer.println("#   - Second character = BOTTOM floor (executes second)");
            writer.println("#");
            writer.println("# Only add commands where buildings (h/hh/H/HH) or power plants (P) exist!");
            writer.println("# Use '.' for tiles without commands");
            writer.println("#");
            writer.println("# Budget: $200 total");
            writer.println("#");
            writer.println("# Example grid:");
            writer.println("#   .   D   .   .   .   P   .    <- Blue cat turns Down at (1,0), Powerup at (5,0)");
            writer.println("#   .   UD  .   .   .   .   .    <- Multi-floor: Up(top), Down(bottom) at (1,1)");
            writer.println("#   .   .   L   .   .   .   .    <- Red cat turns Left at (2,2)");
            writer.println("#");
            writer.println("# ================================================================");
            writer.println("# YOUR BOARD LAYOUT (for reference):");
            writer.println("# ================================================================");
            writer.println("#");
            
            // Print board layout as reference
            for (int y = 0; y < height; y++) {
                writer.print("# ");
                for (int x = 0; x < width; x++) {
                    String tile = layout[y][x];
                    String display = String.format("%-7s", tile);
                    writer.print(display + " ");
                }
                writer.println();
            }
            
            writer.println("#");
            writer.println("# ================================================================");
            writer.println("# MOVEMENT GRID (edit below - Java array format like starting.txt):");
            writer.println("# ================================================================");
            writer.println();
            
            // Write movement grid in Java array format
            writer.println("String[][] movements = {");
            for (int y = 0; y < height; y++) {
                writer.print("    {");
                for (int x = 0; x < width; x++) {
                    String tile = layout[y][x];
                    
                    // Mark commandable tiles with placeholder
                    if (isCommandable(tile)) {
                        int floors = getFloors(tile);
                        if (floors == 2) {
                            movements[y][x] = ".."; // Two-char placeholder for multi-floor
                        } else {
                            movements[y][x] = ".";  // Single-char placeholder
                        }
                    }
                    
                    String display = String.format("\"%s\"", movements[y][x]);
                    writer.print(display);
                    if (x < width - 1) {
                        writer.print(", ");
                    }
                }
                writer.print("}");
                if (y < height - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("};");
            writer.println();
            writer.println("# Remember: Only place commands where buildings or power plants exist!");
        }
        
        System.out.println("\nBoard dimensions: " + width + "x" + height);
        int commandableCount = countCommandable(layout);
        System.out.println("Commandable tiles: " + commandableCount);
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
    
    private static int countCommandable(String[][] layout) {
        int count = 0;
        for (String[] row : layout) {
            for (String tile : row) {
                if (isCommandable(tile)) count++;
            }
        }
        return count;
    }
    
    private static String[][] loadLayout(String filename) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Only skip lines that are pure comments (start with # followed by space and more text)
                // Don't skip lines where # is a tile (will be split properly)
                if (line.startsWith("# ") && !line.contains("\t") && line.split("\\s+").length < 5) {
                    continue;
                }
                
                String[] tiles = line.split("\\s+");
                rows.add(tiles);
            }
        }
        
        return rows.toArray(new String[0][]);
    }
}
