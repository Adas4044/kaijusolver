import java.io.*;
import java.util.*;

/**
 * Simulated Annealing Optimizer - uses FAST SIMULATOR only
 * No game logic replication!
 */
public class SAOptimizer {

    private static class ValidTile {
        int x, y;
        int maxFloors;

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

        Solution() {
            commands = new HashMap<>();
        }

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

    // SA params
    private double temp = 100.0;
    private final double coolRate = 0.003;

    public SAOptimizer(String[][] layout, int budget) {
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
        int budget = this.budget;

        for (ValidTile t : validTiles) {
            if (budget < 10 || random.nextDouble() > 0.3) continue;

            for (int f = 0; f < t.maxFloors; f++) {
                if (budget < 10 || random.nextDouble() > 0.5) continue;

                CommandType[] cmds = CommandType.values();
                List<CommandType> affordable = new ArrayList<>();
                for (CommandType c : cmds) if (c.getCost() <= budget) affordable.add(c);

                if (!affordable.isEmpty()) {
                    CommandType cmd = affordable.get(random.nextInt(affordable.size()));
                    s.commands.put(t.x + "," + t.y + "," + f, cmd);
                    budget -= cmd.getCost();
                }
            }
        }

        evaluate(s);
        return s;
    }

    // KEY METHOD: Call fast simulator, get score + distances
    private void evaluate(Solution s) {
        GameSimulator sim = new GameSimulator(layout, false);

        for (Map.Entry<String, CommandType> e : s.commands.entrySet()) {
            String[] p = e.getKey().split(",");
            sim.placeCommand(Integer.parseInt(p[0]), Integer.parseInt(p[1]), e.getValue(), Integer.parseInt(p[2]));
        }

        // Fast simulator returns: [score, redDist, greenDist, blueDist]
        int[] result = sim.runSimulationWithDistances();

        s.score = result[0];
        s.distances[0] = result[1];
        s.distances[1] = result[2];
        s.distances[2] = result[3];

        // Fitness = score - distance penalty
        s.fitness = s.score - (result[1] + result[2] + result[3]) * 100.0;
    }

    private Solution neighbor(Solution cur) {
        Solution n = new Solution(cur);

        double r = random.nextDouble();

        if (r < 0.4 && !n.commands.isEmpty()) {
            // Modify/remove existing
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String key = keys.get(random.nextInt(keys.size()));

            if (random.nextDouble() < 0.5) {
                CommandType[] cmds = CommandType.values();
                n.commands.put(key, cmds[random.nextInt(cmds.length)]);
            } else {
                n.commands.remove(key);
            }
        } else if (r < 0.8) {
            // Add new
            ValidTile t = validTiles.get(random.nextInt(validTiles.size()));
            int f = t.maxFloors == 1 ? 0 : random.nextInt(t.maxFloors);
            CommandType[] cmds = CommandType.values();
            n.commands.put(t.x + "," + t.y + "," + f, cmds[random.nextInt(cmds.length)]);
        } else if (n.commands.size() >= 2) {
            // Swap
            List<String> keys = new ArrayList<>(n.commands.keySet());
            String k1 = keys.get(random.nextInt(keys.size()));
            String k2 = keys.get(random.nextInt(keys.size()));
            CommandType tmp = n.commands.get(k1);
            n.commands.put(k1, n.commands.get(k2));
            n.commands.put(k2, tmp);
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
        System.out.println("\n╔═══════════════════════════════════════╗");
        System.out.println("║   Simulated Annealing - Starting     ║");
        System.out.println("╚═══════════════════════════════════════╝\n");

        Solution cur = randomSolution();
        Solution best = new Solution(cur);

        System.out.printf("Initial: Score=%d, Dist=[%d,%d,%d], Fit=%.0f\n",
            cur.score, cur.distances[0], cur.distances[1], cur.distances[2], cur.fitness);

        long start = System.currentTimeMillis();
        int iter = 0, accept = 0;

        while (temp > 1.0 && iter < maxIter) {
            iter++;

            Solution nei = neighbor(cur);
            double delta = nei.fitness - cur.fitness;

            if (delta > 0 || Math.exp(delta / temp) > random.nextDouble()) {
                cur = nei;
                accept++;

                if (cur.score > best.score) {
                    best = new Solution(cur);
                    System.out.printf("[%d] NEW BEST! Score=%d, Dist=[%d,%d,%d], Cost=$%d\n",
                        iter, best.score, best.distances[0], best.distances[1], best.distances[2], best.cost());
                }
            }

            if (iter % 1000 == 0) {
                double sec = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("[%d] Best=%d, Dist=[%d,%d,%d], Temp=%.1f, Accept=%.1f%%, Speed=%.0f/s\n",
                    iter, best.score, best.distances[0], best.distances[1], best.distances[2],
                    temp, 100.0 * accept / 1000, iter / sec);
                accept = 0;
            }

            if (iter % 50 == 0) temp *= (1 - coolRate);
        }

        double sec = (System.currentTimeMillis() - start) / 1000.0;
        System.out.println("\n╔═══════════════════════════════════════╗");
        System.out.println("║          Complete!                    ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.printf("\nIterations: %d in %.2fs (%.0f it/s)\n", iter, sec, iter / sec);
        System.out.printf("Best: Score=%d, Distances=[%d,%d,%d], Cost=$%d/$%d\n",
            best.score, best.distances[0], best.distances[1], best.distances[2], best.cost(), budget);

        return best;
    }

    public void save(Solution s, String file) throws IOException {
        String[][] grid = new String[layout.length][layout[0].length];
        for (int y = 0; y < layout.length; y++)
            for (int x = 0; x < layout[0].length; x++)
                grid[y][x] = ".";

        Map<String, String[]> tiles = new HashMap<>();
        for (Map.Entry<String, CommandType> e : s.commands.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int f = Integer.parseInt(p[2]);
            String pos = x + "," + y;

            if (!tiles.containsKey(pos)) tiles.put(pos, new String[]{null, null});
            tiles.get(pos)[f] = toSymbol(e.getValue());
        }

        for (Map.Entry<String, String[]> e : tiles.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            String[] floors = e.getValue();

            StringBuilder cell = new StringBuilder();
            if (floors[1] != null) cell.append(floors[1]);
            if (floors[0] != null) cell.append(floors[0]);
            if (cell.length() > 0) grid[y][x] = cell.toString();
        }

        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("# SA Optimized - Score: " + s.score);
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
            int iter = args.length > 2 ? Integer.parseInt(args[2]) : 10000;

            String[][] layout = loadLayout(map);
            SAOptimizer opt = new SAOptimizer(layout, 200);
            Solution best = opt.optimize(iter);
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
