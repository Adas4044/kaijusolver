import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * SimpleScoreOptimizer - Just maximize raw score, no fancy heuristics
 * Pure simulated annealing on actual game score
 */
public class SimpleScoreOptimizer {
    
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    private static volatile Solution globalBest = null;
    private static final Object bestLock = new Object();
    private static final AtomicInteger globalImprovementCount = new AtomicInteger(0);
    private static final AtomicLong totalIterations = new AtomicLong(0);
    
    static class ValidTile {
        int x, y, floors;
        String tileType;
        
        ValidTile(int x, int y, int floors, String type) {
            this.x = x;
            this.y = y;
            this.floors = floors;
            this.tileType = type;
        }
    }
    
    static class Solution {
        Map<String, String> commands;
        int score;
        int cost;
        
        Solution() {
            commands = new HashMap<>();
            score = 0;
            cost = 0;
        }
        
        Solution copy() {
            Solution s = new Solution();
            s.commands.putAll(this.commands);
            s.score = this.score;
            s.cost = this.cost;
            return s;
        }
    }
    
    static class WorkerThread implements Callable<Integer> {
        private final int threadId;
        private final String[][] layout;
        private final List<ValidTile> validTiles;
        private final double initialTemp;
        private final double coolingRate;
        private final long maxIterations;
        private final Random rand;
        private volatile double currentTemperature;
        
        WorkerThread(int id, String[][] layout, List<ValidTile> validTiles, 
                     double initialTemp, double coolingRate, long maxIterations) {
            this.threadId = id;
            this.layout = layout;
            this.validTiles = validTiles;
            this.initialTemp = initialTemp;
            this.coolingRate = coolingRate;
            this.maxIterations = maxIterations;
            this.rand = new Random(System.currentTimeMillis() + id);
            this.currentTemperature = initialTemp;
        }
        
        @Override
        public Integer call() {
            Solution current = new Solution();
            current = evaluateSolution(current);
            
            Solution localBest = current.copy();
            int localImprovements = 0;
            
            currentTemperature = initialTemp;
            long iteration = 0;
            int iterationsSinceImprovement = 0;
            
            while (iteration < maxIterations) {
                iteration++;
                iterationsSinceImprovement++;
                totalIterations.incrementAndGet();
                
                // Restart after 10K without improvement
                if (iterationsSinceImprovement > 10000) {
                    currentTemperature = initialTemp;
                    iterationsSinceImprovement = 0;
                    current = new Solution();
                    current = evaluateSolution(current);
                }
                
                Solution neighbor = generateNeighbor(current);
                neighbor = evaluateSolution(neighbor);
                
                // Accept based on SCORE ONLY
                double delta = neighbor.score - current.score;
                boolean accept = false;
                
                if (delta > 0) {
                    accept = true;
                } else {
                    double probability = Math.exp(delta / currentTemperature);
                    accept = rand.nextDouble() < probability;
                }
                
                if (accept) {
                    current = neighbor;
                    
                    if (current.score > localBest.score) {
                        localBest = current.copy();
                        localImprovements++;
                        iterationsSinceImprovement = 0;
                        updateGlobalBest(localBest, threadId, iteration);
                    }
                }
                
                currentTemperature = Math.max(10.0, currentTemperature * coolingRate);
            }
            
            return localImprovements;
        }
        
        private Solution generateNeighbor(Solution current) {
            Solution neighbor = current.copy();
            double r = rand.nextDouble();
            
            if (r < 0.25 && !neighbor.commands.isEmpty()) {
                // Remove
                String key = randomKey(neighbor.commands);
                String cmd = neighbor.commands.get(key);
                neighbor.commands.remove(key);
                neighbor.cost -= getCommandCost(cmd);
            } else if (r < 0.5) {
                // Add
                ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
                int floor = rand.nextInt(tile.floors);
                String key = tile.x + "," + tile.y + "," + floor;
                String cmd = randomCommand(tile.tileType);
                int cmdCost = getCommandCost(cmd);
                
                if (neighbor.cost + cmdCost <= BUDGET) {
                    String oldCmd = neighbor.commands.get(key);
                    if (oldCmd != null) neighbor.cost -= getCommandCost(oldCmd);
                    neighbor.commands.put(key, cmd);
                    neighbor.cost += cmdCost;
                }
            } else if (r < 0.75 && !neighbor.commands.isEmpty()) {
                // Modify
                String key = randomKey(neighbor.commands);
                ValidTile tile = findTileByKey(key);
                String oldCmd = neighbor.commands.get(key);
                String newCmd = randomCommand(tile.tileType);
                int oldCost = getCommandCost(oldCmd);
                int newCost = getCommandCost(newCmd);
                
                if (neighbor.cost - oldCost + newCost <= BUDGET) {
                    neighbor.commands.put(key, newCmd);
                    neighbor.cost = neighbor.cost - oldCost + newCost;
                }
            } else {
                // Multi-change
                int numChanges = 1 + rand.nextInt(3);
                for (int i = 0; i < numChanges && neighbor.cost < BUDGET; i++) {
                    if (!neighbor.commands.isEmpty() && rand.nextBoolean()) {
                        String key = randomKey(neighbor.commands);
                        String cmd = neighbor.commands.get(key);
                        neighbor.commands.remove(key);
                        neighbor.cost -= getCommandCost(cmd);
                    }
                    
                    ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
                    int floor = rand.nextInt(tile.floors);
                    String key = tile.x + "," + tile.y + "," + floor;
                    String cmd = randomCommand(tile.tileType);
                    int cmdCost = getCommandCost(cmd);
                    
                    if (neighbor.cost + cmdCost <= BUDGET) {
                        String oldCmd = neighbor.commands.get(key);
                        if (oldCmd != null) neighbor.cost -= getCommandCost(oldCmd);
                        neighbor.commands.put(key, cmd);
                        neighbor.cost += cmdCost;
                    }
                }
            }
            
            return neighbor;
        }
        
        private Solution evaluateSolution(Solution sol) {
            try {
                GameSimulator sim = new GameSimulator(layout, false);
                
                for (Map.Entry<String, String> entry : sol.commands.entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int floor = Integer.parseInt(parts[2]);
                    String cmd = entry.getValue();
                    
                    CommandType cmdType = parseCommand(cmd);
                    sim.placeCommand(x, y, cmdType, floor);
                }
                
                sim.runSimulation(false);
                
                int score = sim.cats.values().stream()
                    .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                    .mapToInt(Cat::getCurrentPower)
                    .sum();
                
                sol.score = score;
                return sol;
                
            } catch (Exception e) {
                sol.score = 0;
                return sol;
            }
        }
        
        private String randomCommand(String tileType) {
            if (tileType.equals("P")) return "P";
            return ALL_COMMANDS[rand.nextInt(ALL_COMMANDS.length)];
        }
        
        private String randomKey(Map<String, String> map) {
            List<String> keys = new ArrayList<>(map.keySet());
            return keys.get(rand.nextInt(keys.size()));
        }
        
        private ValidTile findTileByKey(String key) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            
            for (ValidTile t : validTiles) {
                if (t.x == x && t.y == y) return t;
            }
            return validTiles.get(0);
        }
    }
    
    private static void updateGlobalBest(Solution candidate, int threadId, long iteration) {
        synchronized (bestLock) {
            if (globalBest == null || candidate.score > globalBest.score) {
                globalBest = candidate.copy();
                int count = globalImprovementCount.incrementAndGet();
                System.out.printf("✨ Thread %d iter %d: Score %d | Cost $%d | %d cmds\n",
                    threadId, iteration, globalBest.score, globalBest.cost, globalBest.commands.size());
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        double initialTemp = args.length > 1 ? Double.parseDouble(args[1]) : 100000.0;
        double coolingRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.9999995;
        long maxIterPerThread = args.length > 3 ? Long.parseLong(args[3]) : 50000000L;
        
        int numThreads = Runtime.getRuntime().availableProcessors();
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Simple Score-Only Optimizer                  ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("🎯 Pure score maximization - no heuristics!");
        System.out.println();
        System.out.println("Threads: " + numThreads);
        System.out.println("Initial Temperature: " + initialTemp);
        System.out.println("Cooling Rate: " + coolingRate);
        System.out.println("Max Iterations per thread: " + maxIterPerThread);
        System.out.println();
        
        String[][] layout = loadLayout(mapFile);
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Grid: " + layout[0].length + "x" + layout.length);
        System.out.println("Valid tiles: " + validTiles.size());
        System.out.println();
        System.out.println("Starting optimization...\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Integer>> futures = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numThreads; i++) {
            WorkerThread worker = new WorkerThread(i, layout, validTiles, 
                initialTemp, coolingRate, maxIterPerThread);
            futures.add(executor.submit(worker));
        }
        
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("\n" + "═".repeat(60));
        System.out.println("COMPLETE!");
        System.out.println("═".repeat(60));
        System.out.println("Time: " + totalTime + "s");
        System.out.println("Iterations: " + totalIterations.get());
        System.out.println("Improvements: " + globalImprovementCount.get());
        System.out.println();
        
        if (globalBest != null) {
            System.out.println("🏆 BEST: Score " + globalBest.score + 
                " | Cost $" + globalBest.cost + " | " + globalBest.commands.size() + " cmds");
            saveSolution(globalBest, validTiles, "movements_optimal.txt", layout);
            System.out.println("✅ Saved to: movements_optimal.txt");
        }
    }
    
    private static int getCommandCost(String cmd) {
        switch (cmd) {
            case "S": return STOMP_COST;
            case "P": return POWERUP_COST;
            default: return TURN_COST;
        }
    }
    
    private static CommandType parseCommand(String cmd) {
        switch (cmd) {
            case "U": return CommandType.TURN_N;
            case "D": return CommandType.TURN_S;
            case "L": return CommandType.TURN_W;
            case "R": return CommandType.TURN_E;
            case "S": return CommandType.STOMP;
            case "P": return CommandType.POWERUP;
            default: return null;
        }
    }
    
    private static List<ValidTile> findValidTiles(String[][] layout) {
        List<ValidTile> tiles = new ArrayList<>();
        
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length; x++) {
                String token = layout[y][x];
                token = token.replaceAll("[rgbRGB]", "").replace("Start", "").replace("Bed", "");
                
                if (token.isEmpty() || token.equals(".")) continue;
                
                char base = token.charAt(0);
                int floors = 1;
                
                if (token.length() >= 2 && token.charAt(1) == base) {
                    floors = 2;
                }
                
                if (base == 'h' || base == 'H') {
                    tiles.add(new ValidTile(x, y, floors, "building"));
                } else if (base == 'P') {
                    tiles.add(new ValidTile(x, y, 1, "P"));
                }
            }
        }
        
        return tiles;
    }
    
    private static void saveSolution(Solution sol, List<ValidTile> validTiles, 
                                     String filename, String[][] layout) {
        try {
            int gridHeight = layout.length;
            int gridWidth = layout[0].length;
            String[][] grid = new String[gridHeight][gridWidth];
            
            for (int y = 0; y < gridHeight; y++) {
                for (int x = 0; x < gridWidth; x++) {
                    grid[y][x] = ".";
                }
            }
            
            Map<String, String[]> tileCommands = new HashMap<>();
            for (Map.Entry<String, String> entry : sol.commands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int floor = Integer.parseInt(parts[2]);
                String cmd = entry.getValue();
                
                String tileKey = x + "," + y;
                ValidTile tile = null;
                for (ValidTile t : validTiles) {
                    if (t.x == x && t.y == y) {
                        tile = t;
                        break;
                    }
                }
                
                if (tile != null) {
                    if (!tileCommands.containsKey(tileKey)) {
                        tileCommands.put(tileKey, new String[tile.floors]);
                        for (int f = 0; f < tile.floors; f++) {
                            tileCommands.get(tileKey)[f] = "";
                        }
                    }
                    
                    tileCommands.get(tileKey)[floor] = cmd;
                }
            }
            
            for (Map.Entry<String, String[]> entry : tileCommands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String[] commands = entry.getValue();
                
                String cellValue = "";
                for (int f = commands.length - 1; f >= 0; f--) {
                    cellValue += (commands[f].isEmpty() ? "." : commands[f]);
                }
                
                if (cellValue.replace(".", "").isEmpty()) {
                    cellValue = ".";
                }
                
                grid[y][x] = cellValue;
            }
            
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("# Simple Score-Only Optimizer");
            writer.println("# Score: " + sol.score);
            writer.println("# Cost: $" + sol.cost);
            writer.println();
            writer.println("String[][] movements = {");
            
            for (int y = 0; y < grid.length; y++) {
                writer.print("    {");
                for (int x = 0; x < grid[y].length; x++) {
                    writer.print("\"" + grid[y][x] + "\"");
                    if (x < grid[y].length - 1) writer.print(", ");
                }
                writer.print("}");
                if (y < grid.length - 1) writer.println(",");
                else writer.println();
            }
            
            writer.println("};");
            writer.close();
            
        } catch (Exception e) {
            System.err.println("Error saving: " + e.getMessage());
        }
    }
    
    private static String[][] loadLayout(String filename) throws IOException {
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
