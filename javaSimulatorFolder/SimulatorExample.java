import java.io.*;

/**
 * Example usage of the GameSimulator
 * Shows both visualization mode and high-performance mode
 */

public class SimulatorExample {

    public static void main(String[] args) throws IOException {
        String layoutFile = "starting.txt";
        String[][] layout = GameSimulator.loadLayoutFromFile(layoutFile);

        System.out.println("=".repeat(60));
        System.out.println("KAIJU CATS SIMULATOR - EXAMPLE USAGE");
        System.out.println("=".repeat(60));
        System.out.println();

        // ====================================================================
        // MODE 1: VISUALIZATION MODE (with history tracking)
        // Use this when you want to see what's happening
        // ====================================================================
        System.out.println("1. Running with VISUALIZATION (slow, but shows everything)");
        System.out.println("-".repeat(60));
        
        GameSimulator vizSimulator = new GameSimulator(layout, true); // true = track history
        
        // Place some commands
        vizSimulator.placeCommand(1, 0, CommandType.TURN_S);
        vizSimulator.placeCommand(5, 0, CommandType.POWERUP);
        
        // Run simulation
        for (int i = 1; i <= 15; i++) {
            vizSimulator.simulateTurn();
        }
        
        // Generate visualization
        Visualizer visualizer = new Visualizer(vizSimulator, true);
        visualizer.generateVisualization("visualization_output.txt");
        
        System.out.println("\n>>> Visualization saved to: visualization_output.txt");
        System.out.println();

        // ====================================================================
        // MODE 2: PERFORMANCE MODE (no history, no output)
        // Use this when you need to run millions of simulations
        // ====================================================================
        System.out.println("2. Running PERFORMANCE MODE (fast, no output)");
        System.out.println("-".repeat(60));
        
        long startTime = System.currentTimeMillis();
        int runs = 10000;
        
        for (int run = 0; run < runs; run++) {
            // Create new simulator (no history tracking)
            GameSimulator fastSimulator = new GameSimulator(layout);
            
            // Place commands (same as before)
            fastSimulator.placeCommand(1, 0, CommandType.TURN_S);
            fastSimulator.placeCommand(5, 0, CommandType.POWERUP);
            
            // Run silently - just get the score
            int score = fastSimulator.runSimulationSilent();
            
            // Do something with score (like find best strategy)
            // ...
        }
        
        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;
        
        System.out.println("Completed " + runs + " simulations in " + seconds + " seconds");
        System.out.println("Average: " + (runs / seconds) + " simulations per second");
        System.out.println();

        // ====================================================================
        // MODE 3: SOLVER MODE - Finding the best command placement
        // ====================================================================
        System.out.println("3. SOLVER MODE - Finding best score");
        System.out.println("-".repeat(60));
        
        int bestScore = 0;
        String bestStrategy = "";
        
        // Try different command placements
        CommandType[] commands = {CommandType.TURN_N, CommandType.TURN_S, 
                                  CommandType.TURN_E, CommandType.TURN_W,
                                  CommandType.POWERUP};
        
        // Try a few random combinations
        for (int attempt = 0; attempt < 100; attempt++) {
            GameSimulator solver = new GameSimulator(layout);
            
            // Place random commands (within budget)
            String strategy = "";
            for (int i = 0; i < 5; i++) {
                int x = (int)(Math.random() * 7);
                int y = (int)(Math.random() * 5);
                CommandType cmd = commands[(int)(Math.random() * commands.length)];
                
                if (solver.placeCommand(x, y, cmd)) {
                    strategy += String.format("(%d,%d)=%s ", x, y, cmd);
                }
            }
            
            int score = solver.runSimulationSilent();
            
            if (score > bestScore) {
                bestScore = score;
                bestStrategy = strategy;
            }
        }
        
        System.out.println("Best Score Found: " + bestScore);
        System.out.println("Strategy: " + bestStrategy);
        System.out.println();

        // ====================================================================
        // MODE 4: DETAILED OUTPUT MODE (for debugging)
        // ====================================================================
        System.out.println("4. Running with DETAILED OUTPUT (for debugging)");
        System.out.println("-".repeat(60));
        
        GameSimulator debugSimulator = new GameSimulator(layout);
        debugSimulator.placeCommand(1, 0, CommandType.TURN_S);
        
        int finalScore = debugSimulator.runSimulation(true, "debug_output.txt");
        System.out.println("\n>>> Debug output saved to: debug_output.txt");
        System.out.println(">>> Final Score: " + finalScore);
    }
}
