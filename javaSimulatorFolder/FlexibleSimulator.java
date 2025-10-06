import java.io.*;
import java.util.*;

/**
 * FlexibleSimulator - Run simulation with custom map files
 * 
 * Usage:
 *   java FlexibleSimulator                                    # Uses starting_simple.txt + movements.txt
 *   java FlexibleSimulator my_map.txt                         # Uses my_map.txt + movements.txt
 *   java FlexibleSimulator my_map.txt my_moves.txt            # Custom map + custom movements
 *   java FlexibleSimulator my_map.txt my_moves.txt --viz      # + Full visualization
 */
public class FlexibleSimulator {
    
    public static void main(String[] args) {
        try {
            // Parse arguments
            String layoutFile = "starting_simple.txt";
            String movementFile = "movements.txt";
            boolean fullViz = false;
            
            if (args.length >= 1) {
                layoutFile = args[0];
            }
            if (args.length >= 2) {
                movementFile = args[1];
            }
            if (args.length >= 3 && (args[2].equals("--viz") || args[2].equals("-v"))) {
                fullViz = true;
            }
            
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║     Kaiju Cats - Flexible Simulator    ║");
            System.out.println("╚════════════════════════════════════════╝\n");
            
            System.out.println("Map file:       " + layoutFile);
            System.out.println("Movements file: " + movementFile);
            System.out.println("Visualization:  " + (fullViz ? "FULL" : "QUICK"));
            System.out.println();
            
            // Load starting board
            System.out.println("Loading files...");
            String[][] layout = loadSimpleLayout(layoutFile);
            
            // Create simulator
            long start = System.nanoTime();
            GameSimulator sim = new GameSimulator(layout, fullViz); // Track history if viz requested
            
            // Load and apply movements
            GridMovementLoader.loadAndApplyCommands(movementFile, sim, layout);
            
            // Run simulation
            sim.runSimulation(false);
            
            long end = System.nanoTime();
            
            // Calculate total score
            int totalScore = sim.cats.values().stream()
                .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                .mapToInt(Cat::getCurrentPower)
                .sum();
            
            // Display results
            System.out.println("\n╔════════════════ RESULTS ════════════════╗");
            System.out.println("║                                         ║");
            System.out.printf("║  Total Score: %-25d ║\n", totalScore);
            System.out.println("║                                         ║");
            
            // Show each cat's power
            for (CatColor color : new CatColor[]{CatColor.RED, CatColor.GREEN, CatColor.BLUE}) {
                Cat cat = sim.cats.get(color);
                if (cat != null) {
                    System.out.printf("║  %-6s Power: %-25d ║\n", color, cat.getCurrentPower());
                }
            }
            System.out.println("║                                         ║");
            
            // Show status
            for (CatColor color : new CatColor[]{CatColor.RED, CatColor.GREEN, CatColor.BLUE}) {
                Cat cat = sim.cats.get(color);
                if (cat != null) {
                    System.out.printf("║  %-6s Status: %-24s ║\n", color, cat.getStatus());
                }
            }
            System.out.println("║                                         ║");
            
            // Performance
            double millis = (end - start) / 1000000.0;
            System.out.printf("║  Simulation Time: %.3f ms %-13s║\n", millis, "");
            System.out.println("║                                         ║");
            System.out.println("╚═════════════════════════════════════════╝");
            
            // Budget info
            System.out.println("\n💡 Budget Used: $" + sim.getTotalCommandCost() + " / $" + sim.getStartingBudget());
            
            // Generate full visualization if requested
            if (fullViz) {
                String outputFile = layoutFile.replace(".txt", "_output.txt");
                System.out.println("\nGenerating full visualization...");
                Visualizer visualizer = new Visualizer(sim, true);
                visualizer.generateVisualization(outputFile);
                System.out.println("✅ Saved to: " + outputFile);
            }
            
            // Help text
            System.out.println("\n" + "─".repeat(45));
            System.out.println("Usage Examples:");
            System.out.println("  java FlexibleSimulator");
            System.out.println("  java FlexibleSimulator my_map.txt");
            System.out.println("  java FlexibleSimulator my_map.txt my_moves.txt");
            System.out.println("  java FlexibleSimulator my_map.txt my_moves.txt --viz");
            System.out.println("─".repeat(45) + "\n");
            
        } catch (FileNotFoundException e) {
            System.err.println("❌ Error: Could not find file: " + e.getMessage());
            System.err.println("   Make sure the file exists in the current directory!");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Layout loader
    private static String[][] loadSimpleLayout(String filename) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
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
