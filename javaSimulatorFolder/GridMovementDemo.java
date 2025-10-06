import java.io.*;

/**
 * Demo showing how to use grid-based movements.txt file.
 * 
 * Workflow:
 * 1. Generate movements.txt grid template from starting.txt
 * 2. Edit movements.txt grid to add your command codes
 * 3. Run simulation with commands from movements.txt
 */
public class GridMovementDemo {
    
    public static void main(String[] args) {
        try {
            String layoutFile = "starting_simple.txt";
            String movementFile = "movements.txt";
            String outputFile = "grid_demo_output.txt";
            
            // Check if movements.txt exists, if not generate it
            File movFile = new File(movementFile);
            if (!movFile.exists()) {
                System.out.println("movements.txt not found. Generating grid template...\n");
                GridMovementGenerator.generateGridMovementFile(layoutFile, movementFile);
                System.out.println("\n" + "=".repeat(60));
                System.out.println("NEXT STEPS:");
                System.out.println("1. Edit " + movementFile + " grid to add command codes");
                System.out.println("2. Use: L(left), R(right), U(up), D(down), P(powerup), S(stomp)");
                System.out.println("3. Multi-floor: Use 2 chars (e.g., 'UD' = U on top, D on bottom)");
                System.out.println("4. Run this program again to simulate");
                System.out.println("=".repeat(60));
                return;
            }
            
            // Load the board layout
            System.out.println("Loading layout from: " + layoutFile);
            String[][] layout = loadSimpleLayout(layoutFile);
            
            // Create simulator
            GameSimulator simulator = new GameSimulator(layout, true); // Enable history for visualization
            
            System.out.println("Grid size: " + layout[0].length + "x" + layout.length);
            
            // Load commands from grid-based movements.txt
            int commandsLoaded = GridMovementLoader.loadAndApplyCommands(movementFile, simulator, layout);
            
            if (commandsLoaded == 0) {
                System.out.println("\n⚠ No commands loaded. Edit the grid in " + movementFile);
                System.out.println("\nCommand codes:");
                System.out.println("  L = Turn Left (West)");
                System.out.println("  R = Turn Right (East)");
                System.out.println("  U = Turn Up (North)");
                System.out.println("  D = Turn Down (South)");
                System.out.println("  P = Powerup");
                System.out.println("  S = Stomp");
                System.out.println("\nExample:");
                System.out.println("  .   D   .   .   .   P   .");
                System.out.println("  .   UD  .   .   .   .   .");
                return;
            }
            
            // Run the simulation
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                   RUNNING SIMULATION                       ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            simulator.runSimulation(false);
            
            // Generate visualization
            System.out.println("Generating visualization...");
            Visualizer visualizer = new Visualizer(simulator, true);
            visualizer.generateVisualization(outputFile);
            System.out.println("Visualization written to: " + outputFile);
            
            // Show results with emoji
            System.out.println("\n╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                          RESULTS                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            for (Cat cat : simulator.cats.values()) {
                String statusIcon = cat.getStatus() == CatStatus.FINISHED ? "🏁" : 
                                   cat.getStatus() == CatStatus.ACTIVE ? "🐱" : "💀";
                
                System.out.println(statusIcon + " " + cat.getColor() + " Cat: " +
                                 "Power=" + cat.getCurrentPower() + ", " +
                                 "Position=(" + cat.getX() + "," + cat.getY() + "), " +
                                 "Status=" + cat.getStatus());
            }
            
            int totalScore = simulator.cats.values().stream()
                .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                .mapToInt(Cat::getCurrentPower)
                .sum();
            System.out.println("\nTotal Score: " + totalScore);
            System.out.println("Commands Used: $" + simulator.getTotalCommandCost());
            System.out.println("\nFull visualization saved to: " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load a simple whitespace-separated board layout file.
     */
    private static String[][] loadSimpleLayout(String filename) throws IOException {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Don't skip lines where # is a tile - only skip pure comment lines
                if (line.startsWith("# ") && line.split("\\s+").length < 5) {
                    continue;
                }
                
                String[] tiles = line.split("\\s+");
                rows.add(tiles);
            }
        }
        
        return rows.toArray(new String[0][]);
    }
}
