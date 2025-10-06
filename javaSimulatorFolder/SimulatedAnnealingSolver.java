import java.io.*;
import java.util.*;

/**
 * SimulatedAnnealingSolver - Optimizes command placements using simulated annealing
 * 
 * This solver:
 * 1. Only places commands on valid tiles (buildings/power plants)
 * 2. Respects the $200 budget
 * 3. Uses simulated annealing to escape local optima
 * 4. Continuously outputs improving solutions
 * 
 * Usage: java SimulatedAnnealingSolver [map_file] [initial_temp] [cooling_rate] [iterations]
 */
public class SimulatedAnnealingSolver {
    
    private static final Random rand = new Random();
    
    // Command costs
    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;
    
    // Command types
    private static final String[] TURN_COMMANDS = {"U", "D", "L", "R"};
    private static final String[] ALL_COMMANDS = {"U", "D", "L", "R", "S", "P"};
    
    // Valid tile info
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
    
    public static void main(String[] args) throws IOException {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        double initialTemp = args.length > 1 ? Double.parseDouble(args[1]) : 1000.0;
        double coolingRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.9995;
        int maxIterations = args.length > 3 ? Integer.parseInt(args[3]) : 100000;
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Simulated Annealing Optimizer for Kaiju     ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        System.out.println("Map: " + mapFile);
        System.out.println("Initial Temperature: " + initialTemp);
        System.out.println("Cooling Rate: " + coolingRate);
        System.out.println("Max Iterations: " + maxIterations);
        System.out.println("Budget: $" + BUDGET);
        System.out.println();
        
        // Load map and find valid tiles
        String[][] layout = loadLayout(mapFile);
        List<ValidTile> validTiles = findValidTiles(layout);
        
        System.out.println("Found " + validTiles.size() + " valid command locations:");
        for (ValidTile t : validTiles) {
            System.out.printf("  [%d,%d] %s (%d floor%s)\n", 
                t.x, t.y, t.tileType, t.floors, t.floors > 1 ? "s" : "");
        }
        System.out.println();
        
        // Load initial solution from movements.txt if it exists
        Solution current = loadInitialSolution(validTiles);
        current = evaluateSolution(current, layout);
        
        Solution best = current.copy();
        
        System.out.println("Initial solution: Score=" + current.score + ", Cost=$" + current.cost);
        System.out.println("Starting optimization...\n");
        System.out.println("Iteration | Temperature | Current Score | Best Score | Accepted | Cost");
        System.out.println("─".repeat(80));
        
        double temperature = initialTemp;
        int acceptCount = 0;
        int iteration = 0;
        long startTime = System.currentTimeMillis();
        
        while (iteration < maxIterations && temperature > 0.1) {
            iteration++;
            
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
                // Accept worse solutions with probability
                double probability = Math.exp(delta / temperature);
                accept = rand.nextDouble() < probability;
            }
            
            if (accept) {
                current = neighbor;
                acceptCount++;
                
                // Update best
                if (current.score > best.score) {
                    best = current.copy();
                    
                    // Output new best
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf("\r%9d | %11.2f | %13d | %10d | %8d | $%3d  ✨ NEW BEST! (t=%ds)\n",
                        iteration, temperature, current.score, best.score, acceptCount, current.cost, elapsed);
                    
                    // Save best solution
                    saveSolution(best, validTiles, "movements_best.txt");
                }
            }
            
            // Print progress every 1000 iterations
            if (iteration % 1000 == 0) {
                System.out.printf("\r%9d | %11.2f | %13d | %10d | %8d | $%3d",
                    iteration, temperature, current.score, best.score, acceptCount, current.cost);
            }
            
            // Cool down
            temperature *= coolingRate;
        }
        
        System.out.println("\n\n" + "═".repeat(80));
        System.out.println("OPTIMIZATION COMPLETE!");
        System.out.println("═".repeat(80));
        System.out.println("Total iterations: " + iteration);
        System.out.println("Final temperature: " + temperature);
        System.out.println("Acceptance rate: " + (100.0 * acceptCount / iteration) + "%");
        System.out.println();
        System.out.println("BEST SOLUTION:");
        System.out.println("  Score: " + best.score);
        System.out.println("  Cost: $" + best.cost + " / $" + BUDGET);
        System.out.println("  Commands placed: " + best.commands.size());
        System.out.println();
        System.out.println("✅ Best solution saved to: movements_best.txt");
        System.out.println();
        System.out.println("To test it, run:");
        System.out.println("  cp movements_best.txt movements.txt");
        System.out.println("  java QuickTest");
    }
    
    private static Solution generateNeighbor(Solution current, List<ValidTile> validTiles) {
        Solution neighbor = current.copy();
        
        // Choose mutation type
        double r = rand.nextDouble();
        
        if (r < 0.4 && !neighbor.commands.isEmpty()) {
            // Remove a random command
            String key = randomKey(neighbor.commands);
            String cmd = neighbor.commands.get(key);
            neighbor.commands.remove(key);
            neighbor.cost -= getCommandCost(cmd);
            
        } else if (r < 0.7) {
            // Add a random command
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
            
        } else {
            // Modify an existing command
            if (!neighbor.commands.isEmpty()) {
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
    
    private static Solution loadInitialSolution(List<ValidTile> validTiles) {
        Solution sol = new Solution();
        
        try {
            // Try to load from existing movements.txt
            BufferedReader reader = new BufferedReader(new FileReader("movements.txt"));
            String content = "";
            String line;
            while ((line = reader.readLine()) != null) {
                content += line + "\n";
            }
            reader.close();
            
            // Parse existing movements (simplified)
            // This is just for initial seed - if it fails, we start random
            
        } catch (Exception e) {
            // Start with random solution
        }
        
        // Fill with some random commands to start
        for (int i = 0; i < Math.min(10, validTiles.size()); i++) {
            ValidTile tile = validTiles.get(rand.nextInt(validTiles.size()));
            int floor = rand.nextInt(tile.floors);
            String key = tile.x + "," + tile.y + "," + floor;
            String cmd = randomCommand(tile.tileType);
            
            if (sol.cost + getCommandCost(cmd) <= BUDGET) {
                sol.commands.put(key, cmd);
                sol.cost += getCommandCost(cmd);
            }
        }
        
        return sol;
    }
    
    private static void saveSolution(Solution sol, List<ValidTile> validTiles, String filename) {
        try {
            // Determine grid dimensions from validTiles
            int maxX = 0, maxY = 0;
            for (ValidTile t : validTiles) {
                maxX = Math.max(maxX, t.x);
                maxY = Math.max(maxY, t.y);
            }
            
            // Create grid
            String[][] grid = new String[maxY + 1][maxX + 1];
            for (int y = 0; y <= maxY; y++) {
                for (int x = 0; x <= maxX; x++) {
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
            
            // Build grid strings
            for (Map.Entry<String, String[]> entry : tileCommands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String[] commands = entry.getValue();
                
                String cellValue = "";
                for (int f = commands.length - 1; f >= 0; f--) {
                    if (!commands[f].isEmpty()) {
                        cellValue += commands[f];
                    }
                }
                
                grid[y][x] = cellValue.isEmpty() ? "." : cellValue;
            }
            
            // Write file
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("# Generated by Simulated Annealing Optimizer");
            writer.println("# Score: " + sol.score);
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
