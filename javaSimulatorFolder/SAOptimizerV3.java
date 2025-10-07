import java.io.*;
import java.util.*;

/**
 * SA V3 - Adaptive cooling for long runs (14 minutes max)
 * - Auto-adjusts cooling rate based on target iterations
 * - High distance penalty for beds
 */
public class SAOptimizerV3 {

    private static class ValidTile {
        int x, y, maxFloors;
        ValidTile(int x, int y, int maxFloors) { this.x = x; this.y = y; this.maxFloors = maxFloors; }
    }

    private static class Solution {
        Map<String, CommandType> commands = new HashMap<>();
        int score;
        int[] distances = new int[3];
        double fitness;

        Solution() {}
        Solution(Solution o) {
            commands = new HashMap<>(o.commands);
            score = o.score;
            distances = o.distances.clone();
            fitness = o.fitness;
        }
        int cost() { return commands.values().stream().mapToInt(CommandType::getCost).sum(); }
    }

    private final String[][] layout;
    private final List<ValidTile> validTiles;
    private final int budget;
    private final Random random = new Random();

    private double temp = 200.0;  // Higher for longer runs
    private double coolRate;  // Calculated adaptively
    private double distancePenalty = 500.0;

    public SAOptimizerV3(String[][] layout, int budget) {
        this.layout = layout;
        this.budget = budget;
        this.validTiles = findValidTiles();
        System.out.println("Valid tiles: " + validTiles.size() + ", Budget: $" + budget);
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
            if (remaining < 10 || random.nextDouble() > 0.4) continue;
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
        s.fitness = s.score - ((result[1] + result[2] + result[3]) * distancePenalty);
    }

    private Solution neighbor(Solution cur) {
        Solution n = new Solution(cur);
        double r = random.nextDouble();

        if (r < 0.35 && !n.commands.isEmpty()) {
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String key = keys.get(random.nextInt(keys.size()));
            if (random.nextDouble() < 0.6) {
                n.commands.put(key, CommandType.values()[random.nextInt(CommandType.values().length)]);
            } else {
                n.commands.remove(key);
            }
        } else if (r < 0.75) {
            ValidTile t = validTiles.get(random.nextInt(validTiles.size()));
            int f = t.maxFloors == 1 ? 0 : random.nextInt(t.maxFloors);
            n.commands.put(t.x + "," + t.y + "," + f,
                         CommandType.values()[random.nextInt(CommandType.values().length)]);
        } else if (n.commands.size() >= 2) {
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String k1 = keys.get(random.nextInt(keys.size()));
            String k2 = keys.get(random.nextInt(keys.size()));
            CommandType tmp = n.commands.get(k1);
            n.commands.put(k1, n.commands.get(k2));
            n.commands.put(k2, tmp);
        }

        while (n.cost() > budget && !n.commands.isEmpty()) {
            List<String> keys = new ArrayList<>(n.commands.keySet());
            n.commands.remove(keys.get(random.nextInt(keys.size())));
        }

        evaluate(n);
        return n;
    }

    public Solution optimize(int maxIter) {
        // ADAPTIVE COOLING: Set rate so temp drops to ~1.0 at maxIter
        // Formula: finalTemp = initialTemp * (1 - rate)^(maxIter/50)
        // Solving for rate: rate = 1 - (finalTemp/initialTemp)^(50/maxIter)
        int coolingSteps = maxIter / 50;
        coolRate = 1.0 - Math.pow(1.0 / temp, 1.0 / coolingSteps);

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SA V3 - ADAPTIVE LONG RUN (14min max)      ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        System.out.printf("Target iterations: %d, Cooling rate: %.6f\n", maxIter, coolRate);
        System.out.printf("Distance penalty: %.0f\n\n", distancePenalty);

        Solution cur = randomSolution();
        Solution best = new Solution(cur);

        System.out.printf("Initial: Score=%d, Dist=[%d,%d,%d], Fit=%.0f\n",
            cur.score, cur.distances[0], cur.distances[1], cur.distances[2], cur.fitness);

        long start = System.currentTimeMillis();
        int iter = 0, accept = 0, lastReport = 0;

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

            if (iter - lastReport >= 50000) {
                double sec = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("[%d @ %.1fs] Best=%d, Dist=[%d,%d,%d], Temp=%.1f, Accept=%.1f%%, Speed=%.0f/s\n",
                    iter, sec, best.score, best.distances[0], best.distances[1], best.distances[2],
                    temp, 100.0 * accept / 50000, iter / sec);
                accept = 0;
                lastReport = iter;
            }

            if (iter % 50 == 0) temp *= (1 - coolRate);
        }

        double sec = (System.currentTimeMillis() - start) / 1000.0;
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║              OPTIMIZATION COMPLETE!          ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.printf("\nTime: %.2fs (%d iterations @ %.0f it/s)\n", sec, iter, iter / sec);
        System.out.printf("BEST SCORE: %d\n", best.score);
        System.out.printf("Distances: [%d,%d,%d] Total=%d\n",
            best.distances[0], best.distances[1], best.distances[2],
            best.distances[0] + best.distances[1] + best.distances[2]);
        System.out.printf("Budget: $%d / $%d\n", best.cost(), budget);

        return best;
    }

    public void save(Solution s, String file) throws IOException {
        String[][] grid = new String[layout.length][layout[0].length];
        for (int y = 0; y < layout.length; y++)
            for (int x = 0; x < layout[0].length; x++)
                grid[y][x] = ".";

        Map<String, String[]> tileData = new HashMap<>();
        for (Map.Entry<String, CommandType> e : s.commands.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int f = Integer.parseInt(p[2]);
            String pos = x + "," + y;
            if (!tileData.containsKey(pos)) tileData.put(pos, new String[]{null, null});
            tileData.get(pos)[f] = toSymbol(e.getValue());
        }

        for (Map.Entry<String, String[]> e : tileData.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            String[] floors = e.getValue();

            String tile = layout[y][x];
            int maxFloors = getMaxFloors(tile);

            if (maxFloors == 2) {
                String f1 = floors[1] != null ? floors[1] : ".";
                String f0 = floors[0] != null ? floors[0] : ".";
                grid[y][x] = f1 + f0;
            } else if (maxFloors == 1) {
                grid[y][x] = floors[0] != null ? floors[0] : ".";
            }
        }

        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("# SA V3 Optimized - TRUE Score: " + s.score);
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
            int maxSec = args.length > 2 ? Integer.parseInt(args[2]) : 60;

            String[][] layout = loadLayout(map);
            SAOptimizerV3 opt = new SAOptimizerV3(layout, 200);

            int estIterations = maxSec * 100000;  // Estimate ~100k it/s
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
