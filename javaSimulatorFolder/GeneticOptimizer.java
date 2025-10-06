import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * GeneticOptimizer - Advanced genetic algorithm with elite multi-objective heuristic
 * 
 * Heuristic factors (weighted linear combination):
 * 1. Final score (actual game score)
 * 2. Cats in bed (cats that reached their bed)
 * 3. Power plants hit by only one cat (exclusive access)
 * 4. Powerups used by the cat that hit power plants
 * 5. Power plant cat enters bed last (maximizes power retention)
 * 6. Spike avoidance (cats avoiding spike damage)
 * 
 * Algorithm: Genetic Algorithm + Hill Climbing hybrid
 * - Population-based search with crossover and mutation
 * - Elite preservation
 * - Local search (hill climbing) on best individuals
 * - Multi-threaded evaluation
 */
public class GeneticOptimizer {
    
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    // HEURISTIC WEIGHTS - Carefully tuned for 100k+ score
    private static final double WEIGHT_FINAL_SCORE = 1.0;           // Base score
    private static final double WEIGHT_CATS_IN_BED = 2000.0;        // Bonus per cat that reaches bed
    private static final double WEIGHT_EXCLUSIVE_PP = 8000.0;       // Huge bonus for exclusive power plant
    private static final double WEIGHT_PP_CAT_POWERUPS = 3000.0;    // Bonus per powerup used by PP cat
    private static final double WEIGHT_PP_CAT_LAST = 5000.0;        // Bonus if PP cat enters bed last
    private static final double WEIGHT_SPIKE_AVOID = 1000.0;        // Bonus per spike avoided
    
    // Genetic Algorithm parameters
    private static final int POPULATION_SIZE = 200;
    private static final int ELITE_SIZE = 20;
    private static final double MUTATION_RATE = 0.3;
    private static final double CROSSOVER_RATE = 0.7;
    private static final int TOURNAMENT_SIZE = 5;
    private static final int LOCAL_SEARCH_ITERATIONS = 100;
    
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
    
    static class Individual implements Comparable<Individual> {
        Map<String, String> commands;
        int cost;
        double fitness;
        
        // Detailed metrics
        int actualScore;
        int catsInBed;
        int exclusivePowerPlants;
        int ppCatPowerups;
        boolean ppCatEntersLast;
        int spikesAvoided;
        
        Individual() {
            commands = new HashMap<>();
            cost = 0;
            fitness = 0;
        }
        
        Individual copy() {
            Individual ind = new Individual();
            ind.commands.putAll(this.commands);
            ind.cost = this.cost;
            ind.fitness = this.fitness;
            ind.actualScore = this.actualScore;
            ind.catsInBed = this.catsInBed;
            ind.exclusivePowerPlants = this.exclusivePowerPlants;
            ind.ppCatPowerups = this.ppCatPowerups;
            ind.ppCatEntersLast = this.ppCatEntersLast;
            ind.spikesAvoided = this.spikesAvoided;
            return ind;
        }
        
        @Override
        public int compareTo(Individual other) {
            return Double.compare(other.fitness, this.fitness); // Descending
        }
    }
    
    static class GeneticWorker {
        private final String[][] layout;
        private final List<ValidTile> validTiles;
        private final Random rand;
        private List<Individual> population;
        private Individual globalBest;
        private int gridWidth, gridHeight;
        
        GeneticWorker(String[][] layout, List<ValidTile> validTiles, long seed) {
            this.layout = layout;
            this.validTiles = validTiles;
            this.rand = new Random(seed);
            this.population = new ArrayList<>();
            this.gridHeight = layout.length;
            this.gridWidth = layout[0].length;
        }
        
        public Individual evolve(int generations) {
            // Initialize population
            initializePopulation();
            
            for (int gen = 0; gen < generations; gen++) {
                // Evaluate all individuals
                evaluatePopulation();
                
                // Sort by fitness
                Collections.sort(population);
                
                // Track best
                if (globalBest == null || population.get(0).fitness > globalBest.fitness) {
                    globalBest = population.get(0).copy();
                    
                    if (gen % 10 == 0) {
                        System.out.printf("Gen %d: Fitness %.1f, Score %d, InBed %d, ExclusivePP %d, PPPowerups %d, PPLast %b, SpikesAvoid %d\n",
                            gen, globalBest.fitness, globalBest.actualScore, globalBest.catsInBed,
                            globalBest.exclusivePowerPlants, globalBest.ppCatPowerups, 
                            globalBest.ppCatEntersLast, globalBest.spikesAvoided);
                    }
                }
                
                // Local search on elite individuals (every 5 generations)
                if (gen % 5 == 0) {
                    for (int i = 0; i < Math.min(3, ELITE_SIZE); i++) {
                        population.set(i, localSearch(population.get(i)));
                    }
                }
                
                // Create next generation
                List<Individual> nextGen = new ArrayList<>();
                
                // Elitism - keep best individuals
                for (int i = 0; i < ELITE_SIZE; i++) {
                    nextGen.add(population.get(i).copy());
                }
                
                // Fill rest with offspring
                while (nextGen.size() < POPULATION_SIZE) {
                    Individual parent1 = tournamentSelect();
                    Individual parent2 = tournamentSelect();
                    
                    Individual child;
                    if (rand.nextDouble() < CROSSOVER_RATE) {
                        child = crossover(parent1, parent2);
                    } else {
                        child = parent1.copy();
                    }
                    
                    if (rand.nextDouble() < MUTATION_RATE) {
                        mutate(child);
                    }
                    
                    nextGen.add(child);
                }
                
                population = nextGen;
            }
            
            return globalBest;
        }
        
        private void initializePopulation() {
            for (int i = 0; i < POPULATION_SIZE; i++) {
                Individual ind = new Individual();
                
                // Random number of commands
                int numCommands = 5 + rand.nextInt(10);
                
                for (int j = 0; j < numCommands && ind.cost < BUDGET; j++) {
                    ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
                    int floor = rand.nextInt(tile.floors);
                    String key = tile.x + "," + tile.y + "," + floor;
                    String cmd = randomCommand(tile.tileType);
                    int cmdCost = getCommandCost(cmd);
                    
                    if (ind.cost + cmdCost <= BUDGET) {
                        ind.commands.put(key, cmd);
                        ind.cost += cmdCost;
                    }
                }
                
                population.add(ind);
            }
        }
        
        private void evaluatePopulation() {
            // Sequential evaluation to avoid memory issues and allow longer runtime
            for (Individual ind : population) {
                evaluate(ind);
            }
        }
        
        private void evaluate(Individual ind) {
            try {
                // NO history tracking to save memory - use approximations
                GameSimulator sim = new GameSimulator(layout, false);
                
                for (Map.Entry<String, String> entry : ind.commands.entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int floor = Integer.parseInt(parts[2]);
                    String cmd = entry.getValue();
                    
                    CommandType cmdType = parseCommand(cmd);
                    sim.placeCommand(x, y, cmdType, floor);
                }
                
                sim.runSimulation(false);
                
                // 1. Actual score
                int score = sim.cats.values().stream()
                    .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                    .mapToInt(Cat::getCurrentPower)
                    .sum();
                ind.actualScore = score;
                
                // 2. Cats in bed (cats that finished at their bed position)
                int catsInBed = 0;
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
                
                for (Cat cat : sim.cats.values()) {
                    if (cat.getStatus() != CatStatus.DEFEATED) {
                        int[] bedPos = bedPositions.get(cat.getColor().toString());
                        if (bedPos != null && cat.getX() == bedPos[0] && cat.getY() == bedPos[1]) {
                            catsInBed++;
                        }
                    }
                }
                ind.catsInBed = catsInBed;
                
                // 3. Approximate exclusive power plants based on proximity
                Map<String, Set<String>> powerPlantNearbyCats = new HashMap<>();
                for (int y = 0; y < layout.length; y++) {
                    for (int x = 0; x < layout[y].length; x++) {
                        if (layout[y][x].contains("P")) {
                            String ppKey = x + "," + y;
                            powerPlantNearbyCats.put(ppKey, new HashSet<>());
                            
                            // Check final cat positions near power plants
                            for (Cat cat : sim.cats.values()) {
                                if (cat.getStatus() != CatStatus.DEFEATED) {
                                    int dist = Math.abs(cat.getX() - x) + Math.abs(cat.getY() - y);
                                    if (dist <= 3) { // Relaxed proximity
                                        powerPlantNearbyCats.get(ppKey).add(cat.getColor().toString());
                                    }
                                }
                            }
                        }
                    }
                }
                
                int exclusiveCount = 0;
                String ppCatColor = null;
                Cat ppCat = null;
                for (Map.Entry<String, Set<String>> entry : powerPlantNearbyCats.entrySet()) {
                    if (entry.getValue().size() == 1) {
                        exclusiveCount++;
                        ppCatColor = entry.getValue().iterator().next();
                        // Find the actual cat
                        for (Cat cat : sim.cats.values()) {
                            if (cat.getColor().toString().equals(ppCatColor)) {
                                ppCat = cat;
                                break;
                            }
                        }
                    }
                }
                ind.exclusivePowerPlants = exclusiveCount;
                
                // 4. Approximate powerups for PP cat based on its final power
                int ppCatPowerups = 0;
                if (ppCat != null) {
                    // Higher power suggests more powerups were collected
                    ppCatPowerups = Math.min(ppCat.getCurrentPower() / 10000, 5);
                }
                ind.ppCatPowerups = ppCatPowerups;
                
                // 5. PP cat enters bed last (check if PP cat has highest power or is in bed)
                boolean ppCatLast = false;
                if (ppCat != null && catsInBed > 0) {
                    int[] ppBedPos = bedPositions.get(ppCatColor);
                    if (ppBedPos != null) {
                        boolean ppInBed = (ppCat.getX() == ppBedPos[0] && ppCat.getY() == ppBedPos[1]);
                        // Reward if PP cat is in bed or has highest power (suggesting it went last)
                        int maxPower = sim.cats.values().stream()
                            .mapToInt(Cat::getCurrentPower)
                            .max().orElse(0);
                        ppCatLast = ppInBed || (ppCat.getCurrentPower() == maxPower && maxPower > 10000);
                    }
                }
                ind.ppCatEntersLast = ppCatLast;
                
                // 6. Approximate spike avoidance based on cat health/status
                // Cats that avoided spikes typically have higher power and are ACTIVE/FINISHED
                int spikesAvoided = 0;
                for (Cat cat : sim.cats.values()) {
                    CatStatus status = cat.getStatus();
                    // DEFEATED cats likely hit spikes
                    if (status == CatStatus.FINISHED || status == CatStatus.ACTIVE) {
                        spikesAvoided++;
                    }
                }
                ind.spikesAvoided = spikesAvoided;
                
                // CALCULATE FITNESS - Linear combination
                double fitness = 
                    (ind.actualScore * WEIGHT_FINAL_SCORE) +
                    (ind.catsInBed * WEIGHT_CATS_IN_BED) +
                    (ind.exclusivePowerPlants * WEIGHT_EXCLUSIVE_PP) +
                    (ind.ppCatPowerups * WEIGHT_PP_CAT_POWERUPS) +
                    (ind.ppCatEntersLast ? WEIGHT_PP_CAT_LAST : 0) +
                    (ind.spikesAvoided * WEIGHT_SPIKE_AVOID);
                
                ind.fitness = fitness;
                
            } catch (Exception e) {
                ind.fitness = -999999;
                ind.actualScore = 0;
            }
        }
        
        private Individual tournamentSelect() {
            Individual best = null;
            for (int i = 0; i < TOURNAMENT_SIZE; i++) {
                Individual ind = population.get(rand.nextInt(population.size()));
                if (best == null || ind.fitness > best.fitness) {
                    best = ind;
                }
            }
            return best;
        }
        
        private Individual crossover(Individual p1, Individual p2) {
            Individual child = new Individual();
            
            // Uniform crossover - randomly pick commands from each parent
            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(p1.commands.keySet());
            allKeys.addAll(p2.commands.keySet());
            
            for (String key : allKeys) {
                String cmd = null;
                if (p1.commands.containsKey(key) && p2.commands.containsKey(key)) {
                    cmd = rand.nextBoolean() ? p1.commands.get(key) : p2.commands.get(key);
                } else if (p1.commands.containsKey(key)) {
                    if (rand.nextDouble() < 0.5) cmd = p1.commands.get(key);
                } else if (p2.commands.containsKey(key)) {
                    if (rand.nextDouble() < 0.5) cmd = p2.commands.get(key);
                }
                
                if (cmd != null) {
                    int cmdCost = getCommandCost(cmd);
                    if (child.cost + cmdCost <= BUDGET) {
                        child.commands.put(key, cmd);
                        child.cost += cmdCost;
                    }
                }
            }
            
            return child;
        }
        
        private void mutate(Individual ind) {
            double r = rand.nextDouble();
            
            if (r < 0.3 && !ind.commands.isEmpty()) {
                // Remove random command
                String key = randomKey(ind.commands);
                String cmd = ind.commands.get(key);
                ind.commands.remove(key);
                ind.cost -= getCommandCost(cmd);
                
            } else if (r < 0.6) {
                // Add random command
                ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
                int floor = rand.nextInt(tile.floors);
                String key = tile.x + "," + tile.y + "," + floor;
                String cmd = randomCommand(tile.tileType);
                int cmdCost = getCommandCost(cmd);
                
                if (ind.cost + cmdCost <= BUDGET) {
                    String oldCmd = ind.commands.get(key);
                    if (oldCmd != null) {
                        ind.cost -= getCommandCost(oldCmd);
                    }
                    ind.commands.put(key, cmd);
                    ind.cost += cmdCost;
                }
                
            } else if (!ind.commands.isEmpty()) {
                // Modify random command
                String key = randomKey(ind.commands);
                ValidTile tile = findTileByKey(key);
                String oldCmd = ind.commands.get(key);
                String newCmd = randomCommand(tile.tileType);
                int oldCost = getCommandCost(oldCmd);
                int newCost = getCommandCost(newCmd);
                
                if (ind.cost - oldCost + newCost <= BUDGET) {
                    ind.commands.put(key, newCmd);
                    ind.cost = ind.cost - oldCost + newCost;
                }
            }
        }
        
        private Individual localSearch(Individual start) {
            Individual best = start.copy();
            evaluate(best);
            
            for (int i = 0; i < LOCAL_SEARCH_ITERATIONS; i++) {
                Individual neighbor = best.copy();
                mutate(neighbor);
                evaluate(neighbor);
                
                if (neighbor.fitness > best.fitness) {
                    best = neighbor;
                }
            }
            
            return best;
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
    
    public static void main(String[] args) throws Exception {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        int generations = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║       Genetic Algorithm Optimizer v2.0        ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("🧬 MULTI-OBJECTIVE HEURISTIC:");
        System.out.println("   Final Score:           " + WEIGHT_FINAL_SCORE + "x");
        System.out.println("   Cats in Bed:           +" + WEIGHT_CATS_IN_BED + " per cat");
        System.out.println("   Exclusive Power Plant: +" + WEIGHT_EXCLUSIVE_PP + " per plant");
        System.out.println("   PP Cat Powerups:       +" + WEIGHT_PP_CAT_POWERUPS + " per powerup");
        System.out.println("   PP Cat Enters Last:    +" + WEIGHT_PP_CAT_LAST);
        System.out.println("   Spike Avoidance:       +" + WEIGHT_SPIKE_AVOID + " per cat");
        System.out.println();
        System.out.println("Algorithm: Genetic Algorithm + Hill Climbing");
        System.out.println("Population: " + POPULATION_SIZE);
        System.out.println("Elite: " + ELITE_SIZE);
        System.out.println("Generations: " + generations);
        System.out.println("Mutation Rate: " + (MUTATION_RATE * 100) + "%");
        System.out.println("Crossover Rate: " + (CROSSOVER_RATE * 100) + "%");
        System.out.println();
        
        String[][] layout = loadLayout(mapFile);
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Map: " + mapFile);
        System.out.println("Grid: " + layout[0].length + "x" + layout.length);
        System.out.println("Valid tiles: " + validTiles.size());
        System.out.println();
        System.out.println("Starting evolution...\n");
        
        long startTime = System.currentTimeMillis();
        
        GeneticWorker worker = new GeneticWorker(layout, validTiles, System.currentTimeMillis());
        Individual best = worker.evolve(generations);
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("\n" + "═".repeat(85));
        System.out.println("EVOLUTION COMPLETE!");
        System.out.println("═".repeat(85));
        System.out.println("Total time: " + totalTime + " seconds");
        System.out.println();
        
        if (best != null) {
            System.out.println("🏆 BEST SOLUTION:");
            System.out.println("  Fitness: " + String.format("%.1f", best.fitness));
            System.out.println("  Actual Score: " + best.actualScore);
            System.out.println("  Cats in Bed: " + best.catsInBed + " / 3");
            System.out.println("  Exclusive Power Plants: " + best.exclusivePowerPlants);
            System.out.println("  PP Cat Powerups: " + best.ppCatPowerups);
            System.out.println("  PP Cat Enters Last: " + best.ppCatEntersLast);
            System.out.println("  Spikes Avoided: " + best.spikesAvoided + " / 3 cats");
            System.out.println("  Cost: $" + best.cost + " / $" + BUDGET);
            System.out.println("  Commands: " + best.commands.size());
            
            int turns = 0, stomps = 0, powerups = 0;
            for (String cmd : best.commands.values()) {
                if (cmd.equals("S")) stomps++;
                else if (cmd.equals("P")) powerups++;
                else turns++;
            }
            System.out.println();
            System.out.println("  Command Breakdown:");
            System.out.println("    Turns: " + turns + " × $10 = $" + (turns * 10));
            System.out.println("    Stomps: " + stomps + " × $20 = $" + (stomps * 20));
            System.out.println("    Powerups: " + powerups + " × $30 = $" + (powerups * 30));
            
            saveSolution(best, validTiles, "movements_optimal.txt", layout);
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
    
    private static void saveSolution(Individual sol, List<ValidTile> validTiles, 
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
            writer.println("# Generated by GeneticOptimizer v2.0");
            writer.println("# Fitness: " + String.format("%.1f", sol.fitness));
            writer.println("# Actual Score: " + sol.actualScore);
            writer.println("# Cats in Bed: " + sol.catsInBed);
            writer.println("# Exclusive Power Plants: " + sol.exclusivePowerPlants);
            writer.println("# PP Cat Powerups: " + sol.ppCatPowerups);
            writer.println("# PP Cat Enters Last: " + sol.ppCatEntersLast);
            writer.println("# Spikes Avoided: " + sol.spikesAvoided);
            writer.println("# Cost: $" + sol.cost + " / $" + BUDGET);
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
