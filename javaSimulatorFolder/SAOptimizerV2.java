import java.io.*;
import java.util.*;

/**
 * Simulated Annealing V2 - TUNED FOR HIGH SCORES (80k+)
 * - Correct format output
 * - Higher distance penalty to push cats to beds
 * - Slower cooling for better exploration
 */
public class SAOptimizerV2 {

    private static class ValidTile {
        int x, y, maxFloors;
        ValidTile(int x, int y, int maxFloors) {
            this.x = x;
            this.y = y;
            this.maxFloors = maxFloors;
        }
    }

    private static class Solution {
        Map<String, CommandType> commands; // "x,y,floor" -> CommandType
        int score;
        int[] distances = new int[3]; // red, green, blue
        double fitness;

        Solution() { commands = new HashMap<>(); }
        Solution(Solution other) {
            commands = new HashMap<>(other.commands);
            score = other.score;
            distances = other.distances.clone();
            fitness = other.fitness;
        }

        int cost() {
            return commands.values().stream().mapToInt(CommandType::getCost).sum();
        }
    }

    private final String[][] layout;
    private final List<ValidTile> validTiles;
    private final int budget;
    private final Random random = new Random();

    // TUNED SA parameters for high scores
    private double temp = 150.0;  // Higher initial temp for more exploration
    private final double coolRate = 0.0015;  // Slower cooling
    private double distancePenalty = 500.0;  // MUCH higher penalty to prioritize reaching beds

    public SAOptimizerV2(String[][] layout, int budget) {
        this.layout = layout;
        this.budget = budget;
        this.validTiles = findValidTiles();
        System.out.println("Valid tiles: " + validTiles.size() + ", Budget: $" + budget);
        System.out.println("Tuned for HIGH SCORES - Distance penalty: " + distancePenalty);
    }

    private List<ValidTile> findValidTiles() {
        List<ValidTile> tiles = new ArrayList<>();
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[0].length; x++) {
                String t = layout[y][x];
                if (t.equals("h") || t.equals("H")) tiles.add(new ValidTile(x, y, 1));
                else if (t.equals("hh") || t.equals("HH")) tiles.add(new ValidTile(x, y, 2));
                else if (t.equals("P")) tiles.add(new ValidTile(x, y, 1));
            }
        }
        return tiles;
    }

    private Solution randomSolution() {
        Solution s = new Solution();
        int remaining = budget;

        for (ValidTile t : validTiles) {
            if (remaining < 10 || random.nextDouble() > 0.4) continue;  // Slightly more commands

            for (int f = 0; f < t.maxFloors; f++) {
                if (remaining < 10 || random.nextDouble() > 0.5) continue;

                List<CommandType> affordable = new ArrayList<>();
                for (CommandType c : CommandType.values())
                    if (c.getCost() <= remaining) affordable.add(c);

                if (!affordable.isEmpty()) {
                    CommandType cmd = affordable.get(random.nextInt(affordable.size()));
                    s.commands.put(t.x + "," + t.y + "," + f, cmd);
                    remaining -= cmd.getCost();
                }
            }
        }

        evaluate(s);
        return s;
    }

    // CALL FAST SIMULATOR - get real score + distances
    private void evaluate(Solution s) {
        GameSimulator sim = new GameSimulator(layout, false);

        for (Map.Entry<String, CommandType> e : s.commands.entrySet()) {
            String[] p = e.getKey().split(",");
            sim.placeCommand(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                           e.getValue(), Integer.parseInt(p[2]));
        }

        int[] result = sim.runSimulationWithDistances();
        s.score = result[0];
        s.distances[0] = result[1];
        s.distances[1] = result[2];
        s.distances[2] = result[3];

        // HIGHER distance penalty to strongly favor reaching beds
        int totalDist = result[1] + result[2] + result[3];
        s.fitness = s.score - (totalDist * distancePenalty);
    }

    private Solution neighbor(Solution cur) {
        Solution n = new Solution(cur);

        double r = random.nextDouble();

        if (r < 0.35 && !n.commands.isEmpty()) {
            // Modify/remove existing (35%)
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String key = keys.get(random.nextInt(keys.size()));

            if (random.nextDouble() < 0.6) {
                // Change command
                CommandType[] cmds = CommandType.values();
                n.commands.put(key, cmds[random.nextInt(cmds.length)]);
            } else {
                // Remove
                n.commands.remove(key);
            }
        } else if (r < 0.75) {
            // Add new (40%)
            ValidTile t = validTiles.get(random.nextInt(validTiles.size()));
            int f = t.maxFloors == 1 ? 0 : random.nextInt(t.maxFloors);
            CommandType[] cmds = CommandType.values();
            n.commands.put(t.x + "," + t.y + "," + f, cmds[random.nextInt(cmds.length)]);
        } else if (n.commands.size() >= 2) {
            // Swap (15%)
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String k1 = keys.get(random.nextInt(keys.size()));
            String k2 = keys.get(random.nextInt(keys.size()));
            CommandType tmp = n.commands.get(k1);
            n.commands.put(k1, n.commands.get(k2));
            n.commands.put(k2, tmp);
        } else {
            // Clear random tile and rebuild (10%)
            ValidTile t = validTiles.get(random.nextInt(validTiles.size()));
            for (int f = 0; f < t.maxFloors; f++) {
                n.commands.remove(t.x + "," + t.y + "," + f);
            }
        }

        // Enforce budget
        while (n.cost() > budget && !n.commands.isEmpty()) {
            List<String> keys = new ArrayList<>(n.commands.keySet());
            n.commands.remove(keys.get(random.nextInt(keys.size())));
        }

        evaluate(n);
        return n;
    }

    public Solution optimize(int maxIter) {
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   Simulated Annealing V2 - HIGH SCORE MODE  ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        Solution cur = randomSolution();
        Solution best = new Solution(cur);

        System.out.printf("Initial: Score=%d, Dist=[%d,%d,%d], Fit=%.0f\n",
            cur.score, cur.distances[0], cur.distances[1], cur.distances[2], cur.fitness);

        long start = System.currentTimeMillis();
        int iter = 0, accept = 0, lastReportIter = 0;

        while (temp > 1.0 && iter < maxIter) {
            iter++;

            Solution nei = neighbor(cur);
            double delta = nei.fitness - cur.fitness;

            if (delta > 0 || Math.exp(delta / temp) > random.nextDouble()) {
                cur = nei;
                accept++;

                if (cur.score > best.score) {
                    best = new Solution(cur);
                    double sec = (System.currentTimeMillis() - start) / 1000.0;
                    System.out.printf("[%d @ %.1fs] NEW BEST! Score=%d, Dist=[%d,%d,%d], Cost=$%d\n",
                        iter, sec, best.score, best.distances[0], best.distances[1],
                        best.distances[2], best.cost());
                }
            }

            // Progress every 5000 iterations
            if (iter - lastReportIter >= 5000) {
                double sec = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("[%d @ %.1fs] Best=%d, Dist=[%d,%d,%d], Temp=%.1f, Accept=%.1f%%, Speed=%.0f/s\n",
                    iter, sec, best.score, best.distances[0], best.distances[1], best.distances[2],
                    temp, 100.0 * accept / 5000, iter / sec);
                accept = 0;
                lastReportIter = iter;
            }

            // Cool down every 50 iterations
            if (iter % 50 == 0) temp *= (1 - coolRate);
        }

        double sec = (System.currentTimeMillis() - start) / 1000.0;
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║              OPTIMIZATION COMPLETE!          ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.printf("\nTime: %.2fs (%d iterations @ %.0f it/s)\n", sec, iter, iter / sec);
        System.out.printf("BEST SCORE: %d\n", best.score);
        System.out.printf("Distances: Red=%d, Green=%d, Blue=%d (Total=%d)\n",
            best.distances[0], best.distances[1], best.distances[2],
            best.distances[0] + best.distances[1] + best.distances[2]);
        System.out.printf("Budget: $%d / $%d\n", best.cost(), budget);

        return best;
    }

    // FIXED: Correct format matching GridMovementLoader expectations
    public void save(Solution s, String file) throws IOException {
        String[][] grid = new String[layout.length][layout[0].length];
        for (int y = 0; y < layout.length; y++)
            for (int x = 0; x < layout[0].length; x++)
                grid[y][x] = ".";

        // Group by tile
        Map<String, String[]> tileData = new HashMap<>();
        for (Map.Entry<String, CommandType> e : s.commands.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int f = Integer.parseInt(p[2]);
            String pos = x + "," + y;

            if (!tileData.containsKey(pos))
                tileData.put(pos, new String[]{null, null});
            tileData.get(pos)[f] = toSymbol(e.getValue());
        }

        // Build grid with CORRECT format
        for (Map.Entry<String, String[]> e : tileData.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            String[] floors = e.getValue();

            String tile = layout[y][x];
            int maxFloors = getMaxFloors(tile);

            if (maxFloors == 2) {
                // EXACTLY 2 chars: floor1 then floor0
                String f1 = floors[1] != null ? floors[1] : ".";
                String f0 = floors[0] != null ? floors[0] : ".";
                grid[y][x] = f1 + f0;
            } else if (maxFloors == 1) {
                // Single char for floor 0
                grid[y][x] = floors[0] != null ? floors[0] : ".";
            }
        }

        // Write file
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("# SA Optimized V2 - TRUE Score: " + s.score);
            w.println();
            w.println("String[][] movements = {");
            for (int y = 0; y < grid.length; y++) {
                w.print("    {");
                for (int x = 0; x < grid[y].length; x++) {
                    w.print("\"" + grid[y][x] + "\"");
                    if (x < grid[y].length - 1) w.print(", ");
                }
                w.print("}");
                if (y < grid.length - 1) w.println(",");
                else w.println();
            }
            w.println("};");
        }

        System.out.println("✓ Saved to " + file);
    }

    private int getMaxFloors(String tile) {
        if (tile.equals("hh") || tile.equals("HH")) return 2;
        if (tile.equals("h") || tile.equals("H") || tile.equals("P")) return 1;
        return 0;
    }

    private String toSymbol(CommandType c) {
        switch (c) {
            case TURN_N: return "U";
            case TURN_S: return "D";
            case TURN_E: return "R";
            case TURN_W: return "L";
            case STOMP: return "S";
            case POWERUP: return "P";
            default: return ".";
        }
    }

    public static void main(String[] args) {
        try {
            String map = args.length > 0 ? args[0] : "starting_simple.txt";
            String out = args.length > 1 ? args[1] : "movements_optimized.txt";
            int maxSec = args.length > 2 ? Integer.parseInt(args[2]) : 60; // seconds

            String[][] layout = loadLayout(map);
            SAOptimizerV2 opt = new SAOptimizerV2(layout, 200);

            // Estimate iterations for time limit (assume ~30k it/s)
            int estIterations = maxSec * 30000;
            System.out.println("Target runtime: " + maxSec + "s (~" + estIterations + " iterations)\n");

            Solution best = opt.optimize(estIterations);
            opt.save(best, out);

            System.out.println("\n✅ Verify: java TestSolution " + map + " " + out);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String[][] loadLayout(String file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || (line.startsWith("# ") && line.split("\\s+").length < 5)) continue;
                rows.add(line.split("\\s+"));
            }
        }
        return rows.toArray(new String[0][]);
    }
}
