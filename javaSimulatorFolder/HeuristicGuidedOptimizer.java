import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * HeuristicGuidedOptimizer - Uses distance to beds as a heuristic
 * 
 * Combines score with distance-to-bed heuristic to guide the search
 * Similar to A* algorithm: evaluation = score + heuristic_bonus
 * 
 * Heuristic: Rewards solutions where cats end closer to their beds
 */
public class HeuristicGuidedOptimizer {
    
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    // Heuristic weights - ABSURDLY favor cats in bed
    private static final double FINAL_SCORE_WEIGHT = 1.0; // Base score
    private static final double CATS_IN_BED_BONUS = 200000.0; // ABSURD bonus per cat in bed (200k!)
    private static final double EXCLUSIVE_POWERPLANT_BONUS = 10000.0; // Bonus for solo power plant hits
    private static final double DISTANCE_PENALTY = 500.0; // Penalty per unit away from bed
    private static final double BUDGET_USAGE_BONUS = 50.0; // Bonus for using more budget (more commands = more control)
    
    private static volatile Solution globalBest = null;
    private static final Object bestLock = new Object();
    private static final AtomicInteger globalImprovementCount = new AtomicInteger(0);
    private static final AtomicLong totalIterations = new AtomicLong(0);
    private static final ConcurrentHashMap<Integer, Solution> threadBestSolutions = new ConcurrentHashMap<>();
    
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
        double heuristicScore; // score + heuristic bonuses
        double totalDistanceToBeds;
        int exclusivePowerPlants; // Power plants hit by only one cat
        int catsInBed; // Number of cats that reached their bed
        
        Solution() {
            commands = new HashMap<>();
            score = 0;
            cost = 0;
            heuristicScore = 0;
            totalDistanceToBeds = 0;
            exclusivePowerPlants = 0;
            catsInBed = 0;
        }
        
        Solution copy() {
            Solution s = new Solution();
            s.commands.putAll(this.commands);
            s.score = this.score;
            s.cost = this.cost;
            s.heuristicScore = this.heuristicScore;
            s.totalDistanceToBeds = this.totalDistanceToBeds;
            s.exclusivePowerPlants = this.exclusivePowerPlants;
            s.catsInBed = this.catsInBed;
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
        
        // Bed positions for each cat color
        private Map<String, Position> bedPositions;
        
        static class Position {
            int x, y;
            Position(int x, int y) { this.x = x; this.y = y; }
        }
        
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
            this.bedPositions = findBedPositions(layout);
        }
        
        private Map<String, Position> findBedPositions(String[][] layout) {
            Map<String, Position> beds = new HashMap<>();
            
            for (int y = 0; y < layout.length; y++) {
                for (int x = 0; x < layout[y].length; x++) {
                    String tile = layout[y][x];
                    if (tile.contains("Bed")) {
                        if (tile.contains("r")) beds.put("RED", new Position(x, y));
                        if (tile.contains("g")) beds.put("GREEN", new Position(x, y));
                        if (tile.contains("b")) beds.put("BLUE", new Position(x, y));
                    }
                }
            }
            
            return beds;
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
                    currentTemperature = initialTemp;
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
                
                // Acceptance based on HEURISTIC SCORE (score + distance bonus)
                double delta = neighbor.heuristicScore - current.heuristicScore;
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
                    
                    // Update local best based on HEURISTIC SCORE
                    if (current.heuristicScore > localBest.heuristicScore) {
                        localBest = current.copy();
                        localImprovements++;
                        iterationsSinceImprovement = 0;
                        
                        threadBestSolutions.put(threadId, localBest.copy());
                        updateGlobalBest(localBest, threadId, iteration);
                    }
                }
                
                // Cool down
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
                // No history tracking - keep it fast
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
                
                // Calculate raw score
                int score = sim.cats.values().stream()
                    .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                    .mapToInt(Cat::getCurrentPower)
                    .sum();
                
                sol.score = score;
                
                // Calculate distance heuristic
                double totalDistance = 0;
                int catCount = 0;
                
                for (Cat cat : sim.cats.values()) {
                    if (cat.getStatus() != CatStatus.DEFEATED) {
                        String colorStr = cat.getColor().toString();
                        Position bedPos = bedPositions.get(colorStr);
                        
                        if (bedPos != null) {
                            int catX = cat.getX();
                            int catY = cat.getY();
                            
                            // Manhattan distance
                            double distance = Math.abs(catX - bedPos.x) + Math.abs(catY - bedPos.y);
                            
                            // Euclidean distance (alternative)
                            // double distance = Math.sqrt(Math.pow(catX - bedPos.x, 2) + Math.pow(catY - bedPos.y, 2));
                            
                            totalDistance += distance;
                            catCount++;
                        }
                    }
                }
                
                sol.totalDistanceToBeds = totalDistance;
                
                // Count cats IN BED (at their exact bed position)
                Map<String, int[]> bedPositions = new HashMap<>();
                for (int y = 0; y < layout.length; y++) {
                    for (int x = 0; x < layout[y].length; x++) {
                        String tile = layout[y][x];
                        if (tile.contains("Bed")) {
                            if (tile.contains("r")) bedPositions.put("RED", new int[]{x, y});
                            if (tile.contains("g")) bedPositions.put("GREEN", new int[]{x, y});
                            if (tile.contains("b")) bedPositions.put("BLUE", new int[]{x, y});
                        }
                    }
                }
                
                int catsInBed = 0;
                for (Cat cat : sim.cats.values()) {
                    if (cat.getStatus() != CatStatus.DEFEATED) {
                        String colorStr = cat.getColor().toString();
                        int[] bedPos = bedPositions.get(colorStr);
                        if (bedPos != null && cat.getX() == bedPos[0] && cat.getY() == bedPos[1]) {
                            catsInBed++;
                        }
                    }
                }
                sol.catsInBed = catsInBed;
                
                // Check exclusive power plants
                Map<String, Set<String>> powerPlantNearbyCats = new HashMap<>();
                for (int y = 0; y < layout.length; y++) {
                    for (int x = 0; x < layout[y].length; x++) {
                        if (layout[y][x].contains("P")) {
                            String ppKey = x + "," + y;
                            powerPlantNearbyCats.put(ppKey, new HashSet<>());
                            
                            for (Cat cat : sim.cats.values()) {
                                if (cat.getStatus() != CatStatus.DEFEATED) {
                                    int dist = Math.abs(cat.getX() - x) + Math.abs(cat.getY() - y);
                                    if (dist <= 2) {
                                        powerPlantNearbyCats.get(ppKey).add(cat.getColor().toString());
                                    }
                                }
                            }
                        }
                    }
                }
                
                int exclusiveCount = 0;
                for (Set<String> nearbyCats : powerPlantNearbyCats.values()) {
                    if (nearbyCats.size() == 1) {
                        exclusiveCount++;
                    }
                }
                sol.exclusivePowerPlants = exclusiveCount;
                
                // HEURISTIC: MASSIVELY favor cats in bed + budget usage
                double catsInBedBonus = catsInBed * CATS_IN_BED_BONUS;
                double exclusiveBonus = exclusiveCount * EXCLUSIVE_POWERPLANT_BONUS;
                double distancePenalty = totalDistance * DISTANCE_PENALTY;
                double budgetBonus = sol.cost * BUDGET_USAGE_BONUS; // Reward using more budget
                
                // Base heuristic
                sol.heuristicScore = (score * FINAL_SCORE_WEIGHT) + catsInBedBonus + exclusiveBonus + budgetBonus - distancePenalty;
                
                return sol;
                
            } catch (Exception e) {
                sol.score = 0;
                sol.heuristicScore = -999999;
                sol.totalDistanceToBeds = 999;
                sol.exclusivePowerPlants = 0;
                sol.catsInBed = 0;
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
            // Update based on ACTUAL SCORE, not heuristic
            if (globalBest == null || candidate.score > globalBest.score) {
                globalBest = candidate.copy();
                int count = globalImprovementCount.incrementAndGet();
                
                System.out.printf("✨ Thread %d iter %d: Score %d | CatsInBed %d/3 | ExclusivePP %d | Heuristic %.1f | Cost $%d\n",
                    threadId, iteration, globalBest.score, globalBest.catsInBed,
                    globalBest.exclusivePowerPlants, globalBest.heuristicScore, globalBest.cost);
            }
        }
    }
    
    // Diversity monitor
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
                    Thread.sleep(5000);
                    
                    if (threadBestSolutions.size() >= 2) {
                        List<Map.Entry<Integer, Solution>> entries = new ArrayList<>(threadBestSolutions.entrySet());
                        
                        for (int i = 0; i < entries.size(); i++) {
                            for (int j = i + 1; j < entries.size(); j++) {
                                Solution sol1 = entries.get(i).getValue();
                                Solution sol2 = entries.get(j).getValue();
                                
                                double similarity = calculateSimilarity(sol1, sol2);
                                
                                if (similarity > 0.7) {
                                    int thread1 = entries.get(i).getKey();
                                    int thread2 = entries.get(j).getKey();
                                    
                                    if (sol1.score < sol2.score) {
                                        workers.get(thread1).reheat(initialTemp * 2.0);
                                        System.out.printf("🔥 DIVERSITY: Thread %d reheated (similar to Thread %d, score %d < %d)\n",
                                            thread1, thread2, sol1.score, sol2.score);
                                    } else {
                                        workers.get(thread2).reheat(initialTemp * 2.0);
                                        System.out.printf("🔥 DIVERSITY: Thread %d reheated (similar to Thread %d, score %d < %d)\n",
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
        double coolingRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.9999995;
        long maxIterPerThread = args.length > 3 ? Long.parseLong(args[3]) : 50000000L;
        
        int numThreads = Runtime.getRuntime().availableProcessors();
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Heuristic-Guided Optimizer (A*-like)        ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("🎯 CATS IN BED PRIORITY Optimization!");
        System.out.println("   Final score:        " + FINAL_SCORE_WEIGHT + "x");
        System.out.println("   Cats in bed bonus:  +" + CATS_IN_BED_BONUS + " per cat ⭐");
        System.out.println("   Exclusive PP bonus: +" + EXCLUSIVE_POWERPLANT_BONUS + " per plant");
        System.out.println("   Budget usage bonus: +" + BUDGET_USAGE_BONUS + " per dollar");
        System.out.println("   Distance penalty:   -" + DISTANCE_PENALTY + " per unit");
        System.out.println();
        System.out.println("Threads: " + numThreads);
        System.out.println("Map: " + mapFile);
        System.out.println("Initial Temperature: " + initialTemp);
        System.out.println("Cooling Rate: " + coolingRate);
        System.out.println("Max Iterations per thread: " + maxIterPerThread);
        System.out.println("Total iterations: " + (numThreads * maxIterPerThread));
        System.out.println();
        
        String[][] layout = loadLayout(mapFile);
        int gridHeight = layout.length;
        int gridWidth = layout[0].length;
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Grid: " + gridWidth + "x" + gridHeight);
        System.out.println("Valid tiles: " + validTiles.size());
        System.out.println();
        System.out.println("Starting optimization on " + numThreads + " threads...\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Integer>> futures = new ArrayList<>();
        List<WorkerThread> workers = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numThreads; i++) {
            WorkerThread worker = new WorkerThread(i, layout, validTiles, 
                initialTemp, coolingRate, maxIterPerThread);
            workers.add(worker);
            futures.add(executor.submit(worker));
        }
        
        DiversityMonitor diversityMonitor = new DiversityMonitor(workers, initialTemp);
        Thread monitorThread = new Thread(diversityMonitor);
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        executor.shutdown();
        
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.err.println("Interrupted!");
        }
        
        diversityMonitor.stop();
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        
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
            System.out.println("  Final Score: " + globalBest.score);
            System.out.println("  Cats in Bed: " + globalBest.catsInBed + " / 3 ⭐");
            System.out.println("  Heuristic Score: " + String.format("%.1f", globalBest.heuristicScore));
            System.out.println("  Exclusive Power Plants: " + globalBest.exclusivePowerPlants);
            System.out.println("  Avg Distance to Beds: " + String.format("%.2f", globalBest.totalDistanceToBeds / 3.0));
            System.out.println("  Cost: $" + globalBest.cost + " / $" + BUDGET);
            System.out.println("  Commands: " + globalBest.commands.size());
            
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
            writer.println("# Generated by HeuristicGuidedOptimizer (Cats In Bed Focus)");
            writer.println("# Score: " + sol.score);
            writer.println("# Cats in Bed: " + sol.catsInBed + " / 3");
            writer.println("# Heuristic Score: " + String.format("%.1f", sol.heuristicScore));
            writer.println("# Exclusive Power Plants: " + sol.exclusivePowerPlants);
            writer.println("# Avg Distance to Beds: " + String.format("%.2f", sol.totalDistanceToBeds / 3.0));
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
