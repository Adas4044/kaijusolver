import java.io.*;
import java.util.*;

/**
 * BenchmarkTest - Test how many simulations we can run per second
 */
public class BenchmarkTest {

    public static void main(String[] args) {
        try {
            String layoutFile = "starting_simple.txt";
            String movementFile = "movements.txt";

            // Load layout once
            String[][] layout = loadSimpleLayout(layoutFile);

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   Kaiju Cats - Benchmark Test         ║");
            System.out.println("╚════════════════════════════════════════╝\n");

            // Warm-up phase
            System.out.println("Warming up JVM...");
            for (int i = 0; i < 100; i++) {
                GameSimulator sim = new GameSimulator(layout, false);
                GridMovementLoader.loadAndApplyCommands(movementFile, sim, layout);
                sim.runSimulationSilent();
            }

            // Benchmark with varying iteration counts
            int[] testCounts = {1000, 10000, 100000};

            for (int iterations : testCounts) {
                System.out.println("\n" + "─".repeat(50));
                System.out.println("Running " + iterations + " simulations...");

                long start = System.nanoTime();

                for (int i = 0; i < iterations; i++) {
                    GameSimulator sim = new GameSimulator(layout, false);
                    GridMovementLoader.loadAndApplyCommands(movementFile, sim, layout);
                    int score = sim.runSimulationSilent();
                }

                long end = System.nanoTime();
                double seconds = (end - start) / 1_000_000_000.0;
                double simsPerSecond = iterations / seconds;
                double avgMicros = (end - start) / (iterations * 1000.0);

                System.out.printf("✓ Completed in %.3f seconds\n", seconds);
                System.out.printf("✓ Average: %.2f μs per simulation\n", avgMicros);
                System.out.printf("✓ Throughput: %.0f simulations/second\n", simsPerSecond);
            }

            System.out.println("\n" + "─".repeat(50));
            System.out.println("\n💡 This throughput is key for optimization algorithms!");
            System.out.println("   Simulated annealing can try thousands of variations");
            System.out.println("   to find optimal movement strategies.\n");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
