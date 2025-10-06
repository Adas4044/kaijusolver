import java.io.*;
import java.util.*;

/**
 * QuickTest - Simplest way to test your movements.txt
 * Uses the same loading logic as GridMovementDemo but without visualization
 */
public class QuickTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   Kaiju Cats - Quick Simulator Test   ║");
            System.out.println("╚════════════════════════════════════════╝\n");
            
            String layoutFile = "starting_simple.txt";
            String movementFile = "movements.txt";
            
            // Load starting board
            System.out.println("Loading starting_simple.txt and movements.txt...");
            String[][] layout = loadSimpleLayout(layoutFile);
            
            // Create simulator with NO history tracking (fast!)
            long start = System.nanoTime();
            GameSimulator sim = new GameSimulator(layout, false);
            
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
            double micros = (end - start) / 1000.0;
            System.out.printf("║  Simulation Time: %.3f μs %-12s║\n", micros, "");
            System.out.println("║                                         ║");
            System.out.println("╚═════════════════════════════════════════╝");
            
            // Budget info
            System.out.println("\n💡 Tip: Edit movements.txt to try different commands!");
            System.out.println("   Commands: R/L/U/D ($10), S ($20), P ($30)");
            System.out.println("   Budget Used: $" + sim.getTotalCommandCost() + " / $" + sim.getStartingBudget());
            System.out.println("\n✅ For full visualization, run: java GridMovementDemo\n");
            
        } catch (FileNotFoundException e) {
            System.err.println("❌ Error: Could not find starting_simple.txt or movements.txt");
            System.err.println("   Make sure you're running from the correct directory!");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Copy of the layout loader from GridMovementDemo
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
