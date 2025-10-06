import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * MultithreadedOptimalSolver - Parallel simulated annealing optimizer
 * 
 * Features:
 * - Uses all available CPU cores
 * - Restarts at initial temperature (no escalation)
 * - Slower cooling rate
 * - 10K iteration restart threshold
 * - Shares best solution across all threads
 */
public class MultithreadedOptimalSolver {
    
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    // Shared best solution across all threads
    private static volatile Solution globalBest = null;
    private static final Object bestLock = new Object();
    private static final AtomicInteger globalImprovementCount = new AtomicInteger(0);
    private static final AtomicLong totalIterations = new AtomicLong(0);
    
    // For diversity monitoring
    private static final ConcurrentHashMap<Integer, Solution> threadBestSolutions = new ConcurrentHashMap<>();
    private static volatile long lastDiversityCheck = System.currentTimeMillis();
    
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
        private int gridWidth, gridHeight;
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
            this.gridHeight = layout.length;
            this.gridWidth = layout[0].length;
            this.currentTemperature = initialTemp;
        }
        
        public void reheat(double newTemp) {
            this.currentTemperature = newTemp;
        }
        
        @Override
        public Integer call() {
            Solution current = new Solution();
            current = evaluateSolution(current);
            
            Solution localBest = current.copy();
            int localImprovements = 0;
            
            currentTemperature = initialTemp;
            int acceptCount = 0;
            long iteration = 0;
            int iterationsSinceImprovement = 0;
            int restartCount = 0;
            
            while (iteration < maxIterations) {
                iteration++;
                iterationsSinceImprovement++;
                totalIterations.incrementAndGet();
                
                // RESTART: Reset to initial temperature after 10K without improvement
                if (iterationsSinceImprovement > 10000) {
                    currentTemperature = initialTemp; // Reset to initial, not escalate
                    restartCount++;
                    iterationsSinceImprovement = 0;
                    
                    if (threadId == 0 && restartCount % 100 == 0) {
                        System.out.printf("🔄 Thread %d: Restart #%d (temp reset to %.1f)\n", 
                            threadId, restartCount, currentTemperature);
                    }
                    
                    // Jump to new solution
                    current = new Solution();
                    current = evaluateSolution(current);
                }
                
                // Generate neighbor
                Solution neighbor = generateNeighbor(current);
                neighbor = evaluateSolution(neighbor);
                
                // Acceptance
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
                    acceptCount++;
                    
                    // Update local best
                    if (current.score > localBest.score) {
                        localBest = current.copy();
                        localImprovements++;
                        iterationsSinceImprovement = 0;
                        
                        // Store thread's best solution for diversity monitoring
                        threadBestSolutions.put(threadId, localBest.copy());
                        
                        // Try to update global best
                        updateGlobalBest(localBest, threadId, iteration);
                    }
                }
                
                // Cool down (slower than before)
                currentTemperature = Math.max(10.0, currentTemperature * coolingRate);
            }
            
            return localImprovements;
        }
        
        private Solution generateNeighbor(Solution current) {
            Solution neighbor = current.copy();
            double r = rand.nextDouble();
            
            if (r < 0.25 && !neighbor.commands.isEmpty()) {
                // Remove command
                String key = randomKey(neighbor.commands);
                String cmd = neighbor.commands.get(key);
                neighbor.commands.remove(key);
                neighbor.cost -= getCommandCost(cmd);
                
            } else if (r < 0.5) {
                // Add command
                ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
                int floor = rand.nextInt(tile.floors);
                String key = tile.x + "," + tile.y + "," + floor;
                String cmd = randomCommand(tile.tileType);
                int cmdCost = getCommandCost(cmd);
                
                if (neighbor.cost + cmdCost <= BUDGET) {
                    String oldCmd = neighbor.commands.get(key);
                    if (oldCmd != null) {
                        neighbor.cost -= getCommandCost(oldCmd);
                    }
                    neighbor.commands.put(key, cmd);
                    neighbor.cost += cmdCost;
                }
                
            } else if (r < 0.75 && !neighbor.commands.isEmpty()) {
                // Modify command
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
                
            } else if (r < 0.90 && neighbor.commands.size() >= 2) {
                // Swap commands
                List<String> keys = new ArrayList<>(neighbor.commands.keySet());
                String key1 = keys.get(rand.nextInt(keys.size()));
                String key2 = keys.get(rand.nextInt(keys.size()));
                String cmd1 = neighbor.commands.get(key1);
                String cmd2 = neighbor.commands.get(key2);
                neighbor.commands.put(key1, cmd2);
                neighbor.commands.put(key2, cmd1);
                
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
                        if (oldCmd != null) {
                            neighbor.cost -= getCommandCost(oldCmd);
                        }
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
                
                System.out.printf("✨ Thread %d found NEW BEST at iter %d: Score %d (cost $%d, %d commands)\n",
                    threadId, iteration, globalBest.score, globalBest.cost, globalBest.commands.size());
                
                // Save immediately
                saveBestSolution();
            }
        }
    }
    
    private static void saveBestSolution() {
        // This is called from synchronized block, so globalBest is safe
        // Implementation simplified - just save to file
        // (Full implementation would need validTiles and layout)
    }
    
    // Diversity monitor - reheats cores that converge
    static class DiversityMonitor implements Runnable {
        private final List<WorkerThread> workers;
        private final double initialTemp;
        private volatile boolean running = true;
        
        DiversityMonitor(List<WorkerThread> workers, double initialTemp) {
            this.workers = workers;
            this.initialTemp = initialTemp;
        }
        
        public void stop() {
            running = false;
        }
        
        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds
                    
                    // Check for convergence
                    if (threadBestSolutions.size() >= 2) {
                        List<Map.Entry<Integer, Solution>> entries = new ArrayList<>(threadBestSolutions.entrySet());
                        
                        for (int i = 0; i < entries.size(); i++) {
                            for (int j = i + 1; j < entries.size(); j++) {
                                Solution sol1 = entries.get(i).getValue();
                                Solution sol2 = entries.get(j).getValue();
                                
                                // Check if solutions are too similar
                                double similarity = calculateSimilarity(sol1, sol2);
                                
                                if (similarity > 0.7) { // 70% similar
                                    // Reheat the weaker core
                                    int thread1 = entries.get(i).getKey();
                                    int thread2 = entries.get(j).getKey();
                                    
                                    if (sol1.score < sol2.score) {
                                        workers.get(thread1).reheat(initialTemp * 2.0);
                                        System.out.printf("🔥 DIVERSITY: Thread %d reheated (too similar to Thread %d, score %d < %d)\n",
                                            thread1, thread2, sol1.score, sol2.score);
                                    } else {
                                        workers.get(thread2).reheat(initialTemp * 2.0);
                                        System.out.printf("🔥 DIVERSITY: Thread %d reheated (too similar to Thread %d, score %d < %d)\n",
                                            thread2, thread1, sol2.score, sol1.score);
                                    }
                                }
                            }
                        }
                    }
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        
        private double calculateSimilarity(Solution s1, Solution s2) {
            if (s1.commands.isEmpty() || s2.commands.isEmpty()) return 0.0;
            
            // Count matching commands
            int matches = 0;
            int total = Math.max(s1.commands.size(), s2.commands.size());
            
            for (Map.Entry<String, String> entry : s1.commands.entrySet()) {
                if (entry.getValue().equals(s2.commands.get(entry.getKey()))) {
                    matches++;
                }
            }
            
            return (double) matches / total;
        }
    }
    
    public static void main(String[] args) throws Exception {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        double initialTemp = args.length > 1 ? Double.parseDouble(args[1]) : 100000.0;
        double coolingRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.9999995; // Much slower!
        long maxIterPerThread = args.length > 3 ? Long.parseLong(args[3]) : 50000000L; // 50M for ~15 min
        
        int numThreads = Runtime.getRuntime().availableProcessors();
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Multithreaded Optimal Solver                 ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("Threads: " + numThreads);
        System.out.println("Map: " + mapFile);
        System.out.println("Initial Temperature: " + initialTemp);
        System.out.println("Cooling Rate: " + coolingRate);
        System.out.println("Max Iterations per thread: " + maxIterPerThread);
        System.out.println("Total iterations: " + (numThreads * maxIterPerThread));
        System.out.println();
        
        // Load map
        String[][] layout = loadLayout(mapFile);
        int gridHeight = layout.length;
        int gridWidth = layout[0].length;
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Grid: " + gridWidth + "x" + gridHeight);
        System.out.println("Valid tiles: " + validTiles.size());
        System.out.println();
        System.out.println("Starting optimization on " + numThreads + " threads...\n");
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Integer>> futures = new ArrayList<>();
        List<WorkerThread> workers = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        // Start all worker threads
        for (int i = 0; i < numThreads; i++) {
            WorkerThread worker = new WorkerThread(i, layout, validTiles, 
                initialTemp, coolingRate, maxIterPerThread);
            workers.add(worker);
            futures.add(executor.submit(worker));
        }
        
        // Start diversity monitor
        DiversityMonitor diversityMonitor = new DiversityMonitor(workers, initialTemp);
        Thread monitorThread = new Thread(diversityMonitor);
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        // Wait for completion
        executor.shutdown();
        
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.err.println("Interrupted!");
        }
        
        // Stop diversity monitor
        diversityMonitor.stop();
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        
        // Collect results
        int totalLocalImprovements = 0;
        for (Future<Integer> f : futures) {
            try {
                totalLocalImprovements += f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("\n" + "═".repeat(85));
        System.out.println("OPTIMIZATION COMPLETE!");
        System.out.println("═".repeat(85));
        System.out.println("Total time: " + totalTime + " seconds");
        System.out.println("Total iterations: " + totalIterations.get());
        System.out.println("Global improvements: " + globalImprovementCount.get());
        System.out.println();
        
        if (globalBest != null) {
            System.out.println("🏆 BEST SOLUTION:");
            System.out.println("  Score: " + globalBest.score);
            System.out.println("  Cost: $" + globalBest.cost + " / $" + BUDGET);
            System.out.println("  Commands: " + globalBest.commands.size());
            
            // Count command types
            int turns = 0, stomps = 0, powerups = 0;
            for (String cmd : globalBest.commands.values()) {
                if (cmd.equals("S")) stomps++;
                else if (cmd.equals("P")) powerups++;
                else turns++;
            }
            System.out.println();
            System.out.println("  Breakdown:");
            System.out.println("    Turns: " + turns + " × $10 = $" + (turns * 10));
            System.out.println("    Stomps: " + stomps + " × $20 = $" + (stomps * 20));
            System.out.println("    Powerups: " + powerups + " × $30 = $" + (powerups * 30));
            
            // Save final solution
            saveSolution(globalBest, validTiles, "movements_optimal.txt", layout, gridWidth, gridHeight);
            System.out.println();
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
                                     String filename, String[][] layout, 
                                     int gridWidth, int gridHeight) {
        try {
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
            writer.println("# Generated by MultithreadedOptimalSolver");
            writer.println("# Score: " + sol.score);
            writer.println("# Cost: $" + sol.cost + " / $" + BUDGET);
            writer.println("# Commands: " + sol.commands.size());
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
