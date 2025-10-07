import java.io.*;
import java.util.*;

/**
 * Quick tester for any movements file
 */
public class TestSolution {
    public static void main(String[] args) {
        try {
            String layoutFile = args.length > 0 ? args[0] : "starting_simple.txt";
            String movementFile = args.length > 1 ? args[1] : "movements.txt";

            String[][] layout = loadSimpleLayout(layoutFile);

            // Silent mode - disable GridMovementLoader output
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(new ByteArrayOutputStream()));

            GameSimulator sim = new GameSimulator(layout, false);
            GridMovementLoader.loadAndApplyCommands(movementFile, sim, layout);
            sim.runSimulation(false);

            // Restore output
            System.setOut(originalOut);

            // Calculate score
            int totalScore = sim.cats.values().stream()
                .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                .mapToInt(Cat::getCurrentPower)
                .sum();

            // Display results
            System.out.println("╔═══════════════════════════════════════════╗");
            System.out.println("║         SOLUTION VERIFICATION             ║");
            System.out.println("╚═══════════════════════════════════════════╝");
            System.out.println("\nMap:       " + layoutFile);
            System.out.println("Movements: " + movementFile);
            System.out.println("\n┌───────────────────────────────────────────┐");
            System.out.printf("│  TOTAL SCORE: %-27d │\n", totalScore);
            System.out.println("└───────────────────────────────────────────┘\n");

            System.out.println("Cat Details:");
            for (CatColor color : new CatColor[]{CatColor.RED, CatColor.GREEN, CatColor.BLUE}) {
                Cat cat = sim.cats.get(color);
                if (cat != null) {
                    System.out.printf("  %-6s: %6d power  [%s]\n",
                        color, cat.getCurrentPower(), cat.getStatus());
                }
            }

            System.out.println("\nBudget: $" + sim.getTotalCommandCost() + " / $" + sim.getStartingBudget());
            System.out.println();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
