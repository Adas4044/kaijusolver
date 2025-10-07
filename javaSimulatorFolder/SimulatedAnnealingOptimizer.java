import java.io.*;
import java.util.*;

/**
 * Simulated Annealing Optimizer for Kaiju Cats
 *
 * Uses simulated annealing to find optimal movement patterns by:
 * 1. Starting with a random or existing solution
 * 2. Making small modifications to tile commands
 * 3. Accepting better solutions and occasionally worse ones (with decreasing probability)
 * 4. Incorporating distance-to-bed heuristic to guide search
 *
 * Score Function combines:
 * - Total power collected (primary)
 * - Distance penalties for cats not reaching beds (heuristic)
 * - Bonus for cats reaching beds
 */
public class SimulatedAnnealingOptimizer {

    private static class ValidTile {
        int x, y;
        String tileType;
        int maxFloors;  // 0 for PowerPlant, 1-2 for buildings

        ValidTile(int x, int y, String tileType, int maxFloors) {
            this.x = x;
            this.y = y;
            this.tileType = tileType;
            this.maxFloors = maxFloors;
        }
    }

    private static class Solution {
        Map<String, CommandType> commands;  // Key: "x,y,floor"
        int score;
        double fitness;  // Combined score with heuristics

        Solution() {
            commands = new HashMap<>();
            score = 0;
            fitness = 0.0;
        }

        Solution(Solution other) {
            commands = new HashMap<>(other.commands);
            score = other.score;
            fitness = other.fitness;
        }

        String toKey(int x, int y, int floor) {
            return x + "," + y + "," + floor;
        }
    }

    private final String[][] layout;
    private final List<ValidTile> validTiles;
    private final int budget;
    private final Random random;

    // Simulated Annealing parameters
    private double temperature;
    private final double coolingRate;
    private final int iterationsPerTemp;

    // Bed positions for distance heuristic
    private Map<CatColor, int[]> bedPositions;

    public SimulatedAnnealingOptimizer(String[][] layout, int budget) {
        this.layout = layout;
        this.budget = budget;
        this.validTiles = findValidTiles();
        this.random = new Random();

        // SA parameters
        this.temperature = 100.0;
        this.coolingRate = 0.003;  // Slow cooling for thorough search
        this.iterationsPerTemp = 50;

        this.bedPositions = findBedPositions();

        System.out.println("Found " + validTiles.size() + " valid tiles for command placement");
        System.out.println("Budget: $" + budget);
    }

    private List<ValidTile> findValidTiles() {
        List<ValidTile> tiles = new ArrayList<>();

        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[0].length; x++) {
                String tile = layout[y][x];

                // Buildings
                if (tile.equals("h") || tile.equals("H")) {
                    tiles.add(new ValidTile(x, y, tile, 1));
                } else if (tile.equals("hh") || tile.equals("HH")) {
                    tiles.add(new ValidTile(x, y, tile, 2));
                }
                // Power plants
                else if (tile.equals("P")) {
                    tiles.add(new ValidTile(x, y, tile, 0));
                }
            }
        }

        return tiles;
    }

    private Map<CatColor, int[]> findBedPositions() {
        Map<CatColor, int[]> beds = new HashMap<>();

        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[0].length; x++) {
                String tile = layout[y][x];
                if (tile.equals("RBed") || tile.equals("UI_R")) {
                    beds.put(CatColor.RED, new int[]{x, y});
                } else if (tile.equals("GBed") || tile.equals("UI_G")) {
                    beds.put(CatColor.GREEN, new int[]{x, y});
                } else if (tile.equals("BBed") || tile.equals("UI_B")) {
                    beds.put(CatColor.BLUE, new int[]{x, y});
                }
            }
        }

        return beds;
    }

    /**
     * Generate initial solution - can be random or load from existing file
     */
    private Solution generateInitialSolution(String existingFile) {
        Solution solution = new Solution();

        if (existingFile != null) {
            // Load existing solution
            try {
                solution = loadSolutionFromFile(existingFile);
                System.out.println("Loaded initial solution from " + existingFile);
            } catch (Exception e) {
                System.out.println("Could not load " + existingFile + ", starting with random solution");
                solution = generateRandomSolution();
            }
        } else {
            solution = generateRandomSolution();
        }

        evaluateSolution(solution);
        return solution;
    }

    private Solution generateRandomSolution() {
        Solution solution = new Solution();
        int remainingBudget = budget;

        // Randomly place commands until budget exhausted
        List<ValidTile> shuffled = new ArrayList<>(validTiles);
        Collections.shuffle(shuffled, random);

        for (ValidTile tile : shuffled) {
            if (remainingBudget <= 0) break;

            // Randomly decide if we place commands on this tile
            if (random.nextDouble() < 0.3) {  // 30% chance
                int floors = tile.maxFloors == 0 ? 1 : tile.maxFloors;

                for (int floor = 0; floor < floors; floor++) {
                    if (remainingBudget <= 0) break;

                    // Randomly choose command type
                    CommandType cmd = getRandomCommand(remainingBudget);
                    if (cmd != null) {
                        String key = solution.toKey(tile.x, tile.y, floor);
                        solution.commands.put(key, cmd);
                        remainingBudget -= cmd.getCost();
                    }
                }
            }
        }

        return solution;
    }

    private CommandType getRandomCommand(int maxCost) {
        List<CommandType> affordable = new ArrayList<>();

        for (CommandType cmd : CommandType.values()) {
            if (cmd.getCost() <= maxCost) {
                affordable.add(cmd);
            }
        }

        if (affordable.isEmpty()) return null;
        return affordable.get(random.nextInt(affordable.size()));
    }

    /**
     * Evaluate solution by running simulation and computing fitness
     */
    private void evaluateSolution(Solution solution) {
        GameSimulator sim = new GameSimulator(layout, false);

        // Apply commands
        for (Map.Entry<String, CommandType> entry : solution.commands.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int floor = Integer.parseInt(parts[2]);
            sim.placeCommand(x, y, entry.getValue(), floor);
        }

        // Run simulation silently
        solution.score = sim.runSimulationSilent();

        // Calculate distance-to-bed penalties for cats not finished
        double distancePenalty = 0.0;
        for (Cat cat : sim.cats.values()) {
            if (cat.getStatus() != CatStatus.FINISHED && cat.getStatus() != CatStatus.DEFEATED) {
                int[] bedPos = bedPositions.get(cat.getColor());
                if (bedPos != null) {
                    int dx = Math.abs(cat.getX() - bedPos[0]);
                    int dy = Math.abs(cat.getY() - bedPos[1]);
                    int manhattan = dx + dy;
                    distancePenalty += manhattan * 100;  // Weight distance penalty
                }
            }
        }

        // Fitness = score - distance penalty
        solution.fitness = solution.score - distancePenalty;
    }

    /**
     * Generate neighbor solution by making a small random change
     */
    private Solution generateNeighbor(Solution current) {
        Solution neighbor = new Solution(current);

        // Choose a random modification strategy
        double strategy = random.nextDouble();

        if (strategy < 0.4) {
            // Strategy 1: Modify an existing command
            if (!neighbor.commands.isEmpty()) {
                List<String> keys = new ArrayList<>(neighbor.commands.keySet());
                String key = keys.get(random.nextInt(keys.size()));

                // Either change command type or remove it
                if (random.nextDouble() < 0.7) {
                    // Change command type
                    CommandType newCmd = getRandomCommand(budget);
                    if (newCmd != null) {
                        neighbor.commands.put(key, newCmd);
                    }
                } else {
                    // Remove command
                    neighbor.commands.remove(key);
                }
            }
        } else if (strategy < 0.8) {
            // Strategy 2: Add a new command
            ValidTile tile = validTiles.get(random.nextInt(validTiles.size()));
            int floor = tile.maxFloors == 0 ? 0 : random.nextInt(tile.maxFloors);
            String key = neighbor.toKey(tile.x, tile.y, floor);

            CommandType cmd = getRandomCommand(budget);
            if (cmd != null) {
                neighbor.commands.put(key, cmd);
            }
        } else {
            // Strategy 3: Swap two commands
            if (neighbor.commands.size() >= 2) {
                List<String> keys = new ArrayList<>(neighbor.commands.keySet());
                String key1 = keys.get(random.nextInt(keys.size()));
                String key2 = keys.get(random.nextInt(keys.size()));

                CommandType temp = neighbor.commands.get(key1);
                neighbor.commands.put(key1, neighbor.commands.get(key2));
                neighbor.commands.put(key2, temp);
            }
        }

        // Check budget constraint
        int totalCost = neighbor.commands.values().stream()
            .mapToInt(CommandType::getCost)
            .sum();

        // If over budget, remove random commands until valid
        while (totalCost > budget && !neighbor.commands.isEmpty()) {
            List<String> keys = new ArrayList<>(neighbor.commands.keySet());
            String key = keys.get(random.nextInt(keys.size()));
            CommandType removed = neighbor.commands.remove(key);
            totalCost -= removed.getCost();
        }

        evaluateSolution(neighbor);
        return neighbor;
    }

    /**
     * Run simulated annealing optimization
     */
    public Solution optimize(String initialFile, int maxIterations) {
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Simulated Annealing Optimization Starting...         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        Solution current = generateInitialSolution(initialFile);
        Solution best = new Solution(current);

        System.out.printf("Initial solution: Score=%d, Fitness=%.2f\n", current.score, current.fitness);

        int iteration = 0;
        int acceptedMoves = 0;
        int improvedMoves = 0;

        long startTime = System.currentTimeMillis();

        while (temperature > 1.0 && iteration < maxIterations) {
            for (int i = 0; i < iterationsPerTemp && iteration < maxIterations; i++) {
                iteration++;

                Solution neighbor = generateNeighbor(current);

                double delta = neighbor.fitness - current.fitness;

                // Accept better solutions always
                if (delta > 0) {
                    current = neighbor;
                    acceptedMoves++;
                    improvedMoves++;

                    if (current.score > best.score) {
                        best = new Solution(current);
                        System.out.printf("[%d] NEW BEST! Score=%d, Fitness=%.2f, Temp=%.2f\n",
                            iteration, best.score, best.fitness, temperature);
                    }
                }
                // Accept worse solutions with probability based on temperature
                else if (Math.exp(delta / temperature) > random.nextDouble()) {
                    current = neighbor;
                    acceptedMoves++;
                }

                // Progress report every 1000 iterations
                if (iteration % 1000 == 0) {
                    double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    double simsPerSec = iteration / elapsed;
                    System.out.printf("[%d] Current=%d, Best=%d, Temp=%.2f, Accept=%.2f%%, Speed=%.0f it/s\n",
                        iteration, current.score, best.score, temperature,
                        100.0 * acceptedMoves / 1000, simsPerSec);
                    acceptedMoves = 0;
                    improvedMoves = 0;
                }
            }

            // Cool down
            temperature *= (1 - coolingRate);
        }

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                 Optimization Complete!                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.printf("\nTotal iterations: %d\n", iteration);
        System.out.printf("Time elapsed: %.2f seconds\n", elapsed);
        System.out.printf("Throughput: %.0f iterations/second\n", iteration / elapsed);
        System.out.printf("\nBest score found: %d (fitness: %.2f)\n", best.score, best.fitness);
        System.out.printf("Improvement: %+d from initial\n", best.score - generateInitialSolution(null).score);

        return best;
    }

    /**
     * Save solution to movements.txt format (Java array style)
     */
    public void saveSolution(Solution solution, String filename) throws IOException {
        // Create grid representation
        String[][] grid = new String[layout.length][layout[0].length];

        // Initialize with dots
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[0].length; x++) {
                grid[y][x] = ".";
            }
        }

        // Organize commands by position (need to handle multi-floor)
        Map<String, String[]> tileCommands = new HashMap<>();
        for (Map.Entry<String, CommandType> entry : solution.commands.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int floor = Integer.parseInt(parts[2]);

            String posKey = x + "," + y;
            if (!tileCommands.containsKey(posKey)) {
                tileCommands.put(posKey, new String[]{null, null}); // [floor0, floor1]
            }
            tileCommands.get(posKey)[floor] = commandToSymbol(entry.getValue());
        }

        // Build grid strings
        for (Map.Entry<String, String[]> entry : tileCommands.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            String[] floors = entry.getValue();

            StringBuilder cellStr = new StringBuilder();

            // Multi-floor format: TopFloor then BottomFloor (e.g., "SP" = S on floor1, P on floor0)
            if (floors.length > 1 && floors[1] != null) {
                cellStr.append(floors[1]); // Floor 1 first
            }
            if (floors[0] != null) {
                cellStr.append(floors[0]); // Floor 0 second
            }

            if (cellStr.length() == 0) {
                cellStr.append(".");
            }

            grid[y][x] = cellStr.toString();
        }

        // Write to file in Java array format
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# Kaiju Cats - Optimized by Simulated Annealing");
            writer.println("# Score: " + solution.score);
            writer.println("# Fitness: " + solution.fitness);
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
        }

        System.out.println("✓ Solution saved to " + filename);
    }

    private String commandToSymbol(CommandType cmd) {
        switch (cmd) {
            case TURN_N: return "U";
            case TURN_S: return "D";
            case TURN_E: return "R";
            case TURN_W: return "L";
            case STOMP: return "S";
            case POWERUP: return "P";
            default: return ".";
        }
    }

    private Solution loadSolutionFromFile(String filename) throws IOException {
        // This would load from existing movements.txt
        // For now, return empty solution
        return new Solution();
    }

    public static void main(String[] args) {
        try {
            String layoutFile = args.length > 0 ? args[0] : "starting_simple.txt";
            String initialFile = args.length > 1 ? args[1] : null;
            String outputFile = args.length > 2 ? args[2] : "movements_optimized.txt";
            int maxIterations = args.length > 3 ? Integer.parseInt(args[3]) : 10000;

            // Load layout
            String[][] layout = loadSimpleLayout(layoutFile);

            // Create optimizer
            SimulatedAnnealingOptimizer optimizer = new SimulatedAnnealingOptimizer(layout, 200);

            // Run optimization
            Solution best = optimizer.optimize(initialFile, maxIterations);

            // Save result
            optimizer.saveSolution(best, outputFile);

            System.out.println("\n✅ Run: java FlexibleSimulator " + layoutFile + " " + outputFile);

        } catch (Exception e) {
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
