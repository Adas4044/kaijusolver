import java.io.*;
import java.util.*;

/**
 * OptimalSolver - Finds optimal command placements from scratch using simulated annealing
 * 
 * Features:
 * - Starts with EMPTY grid (no commands)
 * - Only places valid commands on buildings/power plants
 * - Respects $200 budget constraint
 * - Continuously outputs improving solutions
 * - Saves best solution to movements_optimal.txt
 */
public class OptimalSolver {
    
    private static final Random rand = new Random();
    
    // Command costs
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    
    // Command types
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    // Valid tile location
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
    
    // Solution state
    static class Solution {
        Map<String, String> commands; // "x,y,floor" -> command
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
    
    private static int gridWidth, gridHeight;
    
    public static void main(String[] args) throws IOException {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        double initialTemp = args.length > 1 ? Double.parseDouble(args[1]) : 10000.0;
        double coolingRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.9999;
        int maxIterations = args.length > 3 ? Integer.parseInt(args[3]) : 1000000;
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║      Optimal Solver - Starting from Scratch    ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("Map: " + mapFile);
        System.out.println("Initial Temperature: " + initialTemp);
        System.out.println("Cooling Rate: " + coolingRate);
        System.out.println("Max Iterations: " + maxIterations);
        System.out.println("Budget: $" + BUDGET);
        System.out.println();
        
        // Load map and find valid tiles
        String[][] layout = loadLayout(mapFile);
        gridHeight = layout.length;
        gridWidth = layout[0].length;
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Grid: " + gridWidth + "x" + gridHeight);
        System.out.println("Found " + validTiles.size() + " valid command locations:");
        for (ValidTile t : validTiles) {
            System.out.printf("  [%d,%d] %s (%d floor%s)\n", 
                t.x, t.y, t.tileType, t.floors, t.floors > 1 ? "s" : "");
        }
        System.out.println();
        
        // Start with EMPTY solution
        Solution current = new Solution();
        current = evaluateSolution(current, layout);
        
        Solution best = current.copy();
        
        System.out.println("Starting from EMPTY grid: Score=" + current.score);
        System.out.println("Beginning optimization...\n");
        System.out.println("Iter      | Temp     | Current    | Best       | Accept | Cost  | Commands");
        System.out.println("─".repeat(85));
        
        double temperature = initialTemp;
        int acceptCount = 0;
        int iteration = 0;
        long startTime = System.currentTimeMillis();
        int improvementCount = 0;
        int iterationsSinceImprovement = 0;
        int restartCount = 0;
        
        while (iteration < maxIterations) {
            iteration++;
            iterationsSinceImprovement++;
            
            // AGGRESSIVE RESTART: If stuck for 10,000 iterations, reheat dramatically!
            if (iterationsSinceImprovement > 10000) {
                // Reheat to FULL initial temperature or higher based on restart count
                double reheatMultiplier = 1.0 + (restartCount * 0.5); // 1x, 1.5x, 2x, 2.5x...
                temperature = initialTemp * reheatMultiplier;
                restartCount++;
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                System.out.printf("\n🔥🔥🔥 AGGRESSIVE RESTART #%d - SUPERHEATING! (stuck for %d iterations) Temp: %.1f (%.1fx) (%ds)\n\n",
                    restartCount, iterationsSinceImprovement, temperature, reheatMultiplier, elapsed);
                iterationsSinceImprovement = 0;
                
                // Jump to completely new solution to escape local maxima
                current = new Solution();
                current = evaluateSolution(current, layout);
            }
            
            // Generate neighbor solution
            Solution neighbor = generateNeighbor(current, validTiles);
            neighbor = evaluateSolution(neighbor, layout);
            
            // Calculate acceptance probability
            double delta = neighbor.score - current.score;
            boolean accept = false;
            
            if (delta > 0) {
                // Always accept better solutions
                accept = true;
            } else {
                // Accept worse solutions probabilistically
                double probability = Math.exp(delta / temperature);
                accept = rand.nextDouble() < probability;
            }
            
            if (accept) {
                current = neighbor;
                acceptCount++;
                
                // Update best
                if (current.score > best.score) {
                    best = current.copy();
                    improvementCount++;
                    iterationsSinceImprovement = 0; // Reset counter
                    
                    // Output new best
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf("%9d | %8.1f | %10d | %10d | %6d | $%3d | %8d  ✨ NEW BEST! (%ds)\n",
                        iteration, temperature, current.score, best.score, acceptCount, current.cost, 
                        current.commands.size(), elapsed);
                    
                    // Save best solution
                    saveSolution(best, validTiles, "movements_optimal.txt", layout);
                }
            }
            
            // Print progress every 5000 iterations
            if (iteration % 5000 == 0) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                System.out.printf("%9d | %8.1f | %10d | %10d | %6d | $%3d | %8d  (%ds)\n",
                    iteration, temperature, current.score, best.score, acceptCount, current.cost,
                    current.commands.size(), elapsed);
            }
            
            // Cool down (but never below 1.0 to maintain some exploration)
            temperature = Math.max(1.0, temperature * coolingRate);
        }
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("\n" + "═".repeat(85));
        System.out.println("OPTIMIZATION COMPLETE!");
        System.out.println("═".repeat(85));
        System.out.println("Total iterations: " + iteration);
        System.out.println("Total time: " + totalTime + " seconds");
        System.out.println("Final temperature: " + temperature);
        System.out.println("Restarts: " + restartCount);
        System.out.println("Acceptance rate: " + String.format("%.2f%%", 100.0 * acceptCount / iteration));
        System.out.println("Improvements found: " + improvementCount);
        System.out.println();
        System.out.println("🏆 BEST SOLUTION:");
        System.out.println("  Score: " + best.score);
        System.out.println("  Cost: $" + best.cost + " / $" + BUDGET);
        System.out.println("  Commands placed: " + best.commands.size());
        System.out.println();
        
        // Show command breakdown
        int turns = 0, stomps = 0, powerups = 0;
        for (String cmd : best.commands.values()) {
            if (cmd.equals("S")) stomps++;
            else if (cmd.equals("P")) powerups++;
            else turns++;
        }
        System.out.println("  Breakdown:");
        System.out.println("    Turns (U/D/L/R): " + turns + " × $10 = $" + (turns * 10));
        System.out.println("    Stomps (S):      " + stomps + " × $20 = $" + (stomps * 20));
        System.out.println("    Powerups (P):    " + powerups + " × $30 = $" + (powerups * 30));
        System.out.println();
        System.out.println("✅ Best solution saved to: movements_optimal.txt");
        System.out.println();
        System.out.println("To test it, run:");
        System.out.println("  cp movements_optimal.txt movements.txt");
        System.out.println("  java QuickTest");
    }
    
    private static Solution generateNeighbor(Solution current, List<ValidTile> validTiles) {
        Solution neighbor = current.copy();
        
        // Choose mutation type - more aggressive mutations
        double r = rand.nextDouble();
        
        if (r < 0.25 && !neighbor.commands.isEmpty()) {
            // Remove a random command (25%)
            String key = randomKey(neighbor.commands);
            String cmd = neighbor.commands.get(key);
            neighbor.commands.remove(key);
            neighbor.cost -= getCommandCost(cmd);
            
        } else if (r < 0.5) {
            // Add a random command (25%)
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
            // Modify an existing command (25%)
            String key = randomKey(neighbor.commands);
            ValidTile tile = findTileByKey(key, validTiles);
            
            String oldCmd = neighbor.commands.get(key);
            String newCmd = randomCommand(tile.tileType);
            
            int oldCost = getCommandCost(oldCmd);
            int newCost = getCommandCost(newCmd);
            
            if (neighbor.cost - oldCost + newCost <= BUDGET) {
                neighbor.commands.put(key, newCmd);
                neighbor.cost = neighbor.cost - oldCost + newCost;
            }
            
        } else if (r < 0.90 && neighbor.commands.size() >= 2) {
            // Swap two random commands (15%)
            List<String> keys = new ArrayList<>(neighbor.commands.keySet());
            String key1 = keys.get(rand.nextInt(keys.size()));
            String key2 = keys.get(rand.nextInt(keys.size()));
            
            String cmd1 = neighbor.commands.get(key1);
            String cmd2 = neighbor.commands.get(key2);
            
            neighbor.commands.put(key1, cmd2);
            neighbor.commands.put(key2, cmd1);
            
        } else {
            // AGGRESSIVE: Remove multiple commands and add multiple new ones (10%)
            int numChanges = 1 + rand.nextInt(3); // 1-3 changes
            for (int i = 0; i < numChanges && neighbor.cost < BUDGET; i++) {
                if (!neighbor.commands.isEmpty() && rand.nextBoolean()) {
                    // Remove one
                    String key = randomKey(neighbor.commands);
                    String cmd = neighbor.commands.get(key);
                    neighbor.commands.remove(key);
                    neighbor.cost -= getCommandCost(cmd);
                }
                
                // Add one
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
    
    private static Solution evaluateSolution(Solution sol, String[][] layout) {
        try {
            // Create simulator
            GameSimulator sim = new GameSimulator(layout, false);
            
            // Apply commands from solution
            for (Map.Entry<String, String> entry : sol.commands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int floor = Integer.parseInt(parts[2]);
                String cmd = entry.getValue();
                
                CommandType cmdType = parseCommand(cmd);
                sim.placeCommand(x, y, cmdType, floor);
            }
            
            // Run simulation
            sim.runSimulation(false);
            
            // Calculate score
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
    
    private static String randomCommand(String tileType) {
        if (tileType.equals("P")) {
            return "P"; // Power plants only have powerup
        }
        return ALL_COMMANDS[rand.nextInt(ALL_COMMANDS.length)];
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
    
    private static String randomKey(Map<String, String> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        return keys.get(rand.nextInt(keys.size()));
    }
    
    private static ValidTile findTileByKey(String key, List<ValidTile> tiles) {
        String[] parts = key.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        
        for (ValidTile t : tiles) {
            if (t.x == x && t.y == y) return t;
        }
        return tiles.get(0);
    }
    
    private static List<ValidTile> findValidTiles(String[][] layout) {
        List<ValidTile> tiles = new ArrayList<>();
        
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length; x++) {
                String token = layout[y][x];
                
                // Skip cats and beds
                token = token.replaceAll("[rgbRGB]", "").replace("Start", "").replace("Bed", "");
                
                if (token.isEmpty() || token.equals(".")) continue;
                
                // Check for buildings
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
    
    private static void saveSolution(Solution sol, List<ValidTile> validTiles, String filename, String[][] layout) {
        try {
            // Use actual grid dimensions
            String[][] grid = new String[gridHeight][gridWidth];
            for (int y = 0; y < gridHeight; y++) {
                for (int x = 0; x < gridWidth; x++) {
                    grid[y][x] = ".";
                }
            }
            
            // Build multi-floor commands
            Map<String, String[]> tileCommands = new HashMap<>();
            for (Map.Entry<String, String> entry : sol.commands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int floor = Integer.parseInt(parts[2]);
                String cmd = entry.getValue();
                
                String tileKey = x + "," + y;
                ValidTile tile = findTileByKey(entry.getKey(), validTiles);
                
                if (!tileCommands.containsKey(tileKey)) {
                    tileCommands.put(tileKey, new String[tile.floors]);
                    for (int f = 0; f < tile.floors; f++) {
                        tileCommands.get(tileKey)[f] = "";
                    }
                }
                
                tileCommands.get(tileKey)[floor] = cmd;
            }
            
            // Build grid strings (top floor first in string)
            for (Map.Entry<String, String[]> entry : tileCommands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String[] commands = entry.getValue();
                
                String cellValue = "";
                // Reverse order: top floor first
                for (int f = commands.length - 1; f >= 0; f--) {
                    cellValue += (commands[f].isEmpty() ? "." : commands[f]);
                }
                
                // If no commands at all, use single dot
                if (cellValue.replace(".", "").isEmpty()) {
                    cellValue = ".";
                }
                
                grid[y][x] = cellValue;
            }
            
            // Write file
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("# Generated by OptimalSolver - Starting from scratch");
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
            System.err.println("Error saving solution: " + e.getMessage());
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
