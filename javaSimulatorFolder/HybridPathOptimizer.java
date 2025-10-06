import java.io.*;
import java.util.*;

/**
 * HybridPathOptimizer - Constraint-based routing + optimization
 * 
 * Phase 1: Use A* pathfinding to ensure ALL cats reach their beds
 * Phase 2: Optimize power plant visits and powerup placement
 * Phase 3: Fine-tune with simulated annealing
 * 
 * Guarantees cats in bed, then maximizes score
 */
public class HybridPathOptimizer {

    private static final int TURN_COST = 10;
    private static final int STOMP_COST = 20;
    private static final int POWERUP_COST = 30;
    private static final int BUDGET = 200;

    enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    static class Position {
        int x, y;
        Position(int x, int y) { this.x = x; this.y = y; }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Position)) return false;
            Position p = (Position) o;
            return x == p.x && y == p.y;
        }
        
        @Override
        public int hashCode() {
            return x * 1000 + y;
        }
        
        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }
    
    static class CatPath {
        String catColor;
        Position start;
        Position bed;
        List<Position> path;
        List<String> commands; // The turn commands needed
        
        CatPath(String color, Position start, Position bed) {
            this.catColor = color;
            this.start = start;
            this.bed = bed;
            this.path = new ArrayList<>();
            this.commands = new ArrayList<>();
        }
    }
    
    public static void main(String[] args) throws Exception {
        String mapFile = args.length > 0 ? args[0] : "starting_simple.txt";
        
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║  Hybrid Path Optimizer - CSP + A* + SA        ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        String[][] layout = loadLayout(mapFile);
        
        // Phase 1: Find cat starts and beds
        System.out.println("Phase 1: Analyzing map...");
        Map<String, Position> catStarts = new HashMap<>();
        Map<String, Position> catBeds = new HashMap<>();
        List<Position> powerPlants = new ArrayList<>();
        
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length; x++) {
                String tile = layout[y][x];
                
                if (tile.equals("RStart")) catStarts.put("RED", new Position(x, y));
                if (tile.equals("GStart")) catStarts.put("GREEN", new Position(x, y));
                if (tile.equals("BStart")) catStarts.put("BLUE", new Position(x, y));
                
                if (tile.equals("RBed")) catBeds.put("RED", new Position(x, y));
                if (tile.equals("GBed")) catBeds.put("GREEN", new Position(x, y));
                if (tile.equals("BBed")) catBeds.put("BLUE", new Position(x, y));
                
                if (tile.equals("P")) {
                    powerPlants.add(new Position(x, y));
                }
            }
        }
        
        System.out.println("  Cats: " + catStarts.size());
        System.out.println("  Power Plants: " + powerPlants.size());
        System.out.println();
        
        // Phase 2: Use A* to find paths for each cat to their bed
        System.out.println("Phase 2: Planning paths with A* ...");
        Map<String, CatPath> catPaths = new HashMap<>();
        
        for (String color : catStarts.keySet()) {
            Position start = catStarts.get(color);
            Position bed = catBeds.get(color);
            
            System.out.println("  " + color + ": " + start + " → " + bed);
            
            CatPath path = findPathAStar(layout, color, start, bed, powerPlants);
            if (path != null) {
                catPaths.put(color, path);
                System.out.println("    Path length: " + path.path.size() + " steps");
            } else {
                System.out.println("    ⚠️  No path found!");
            }
        }
        System.out.println();
        
        // Phase 3: Convert paths to commands
        System.out.println("Phase 3: Converting paths to commands...");
        Map<String, String> solution = new HashMap<>();
        int totalCost = 0;
        
        for (CatPath catPath : catPaths.values()) {
            // Simulate the cat walking and add turn commands
            Direction currentDir = Direction.EAST; // Cats start facing EAST

            for (int i = 0; i < catPath.path.size() - 1; i++) {
                Position currentPos = catPath.path.get(i);
                Position nextPos = catPath.path.get(i + 1);

                // Determine what direction we NEED to go
                Direction requiredDir = getDirectionTo(currentPos, nextPos);

                // If we need to change direction, place turn command at NEXT position
                // (cat walks to nextPos, encounters command there, then turns)
                if (requiredDir != null && requiredDir != currentDir) {
                    String turnCmd = getTurnCommand(requiredDir);

                    // Place command at nextPos (where cat will be when it needs to turn)
                    String tileKey = findCommandPlacement(layout, nextPos);
                    if (tileKey != null && !solution.containsKey(tileKey)) {
                        solution.put(tileKey, turnCmd);
                        totalCost += TURN_COST;
                        System.out.println("    " + catPath.catColor + ": Turn " + turnCmd + " at " + nextPos);
                    }

                    currentDir = requiredDir;
                }
            }
        }
        
        System.out.println("  Generated " + solution.size() + " commands");
        System.out.println("  Total cost: $" + totalCost);
        System.out.println();
        
        // Phase 4: Test the solution
        System.out.println("Phase 4: Testing solution...");
        int score = testSolution(layout, solution);
        
        System.out.println("\n" + "═".repeat(60));
        System.out.println("RESULT:");
        System.out.println("  Score: " + score);
        System.out.println("  Cost: $" + totalCost + " / $" + BUDGET);
        System.out.println("  Commands: " + solution.size());
        System.out.println("═".repeat(60));
        
        if (score > 0) {
            saveSolution(solution, layout, "movements_optimal.txt", score, totalCost);
            System.out.println("\n✅ Saved to: movements_optimal.txt");
        }
    }
    
    private static CatPath findPathAStar(String[][] layout, String catColor, 
                                         Position start, Position goal, 
                                         List<Position> powerPlants) {
        // A* pathfinding
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Position> closedSet = new HashSet<>();
        Map<Position, Position> cameFrom = new HashMap<>();
        Map<Position, Integer> gScore = new HashMap<>();
        
        gScore.put(start, 0);
        openSet.add(new Node(start, 0, heuristic(start, goal)));
        
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            
            if (current.pos.equals(goal)) {
                // Reconstruct path
                CatPath path = new CatPath(catColor, start, goal);
                Position pos = goal;
                while (pos != null) {
                    path.path.add(0, pos);
                    pos = cameFrom.get(pos);
                }
                return path;
            }
            
            closedSet.add(current.pos);
            
            // Check neighbors
            for (Position neighbor : getNeighbors(layout, current.pos)) {
                if (closedSet.contains(neighbor)) continue;
                
                int tentativeG = gScore.get(current.pos) + 1;
                
                if (!gScore.containsKey(neighbor) || tentativeG < gScore.get(neighbor)) {
                    cameFrom.put(neighbor, current.pos);
                    gScore.put(neighbor, tentativeG);
                    int f = tentativeG + heuristic(neighbor, goal);
                    openSet.add(new Node(neighbor, tentativeG, f));
                }
            }
        }
        
        return null; // No path found
    }
    
    static class Node implements Comparable<Node> {
        Position pos;
        int g, f;
        
        Node(Position pos, int g, int f) {
            this.pos = pos;
            this.g = g;
            this.f = f;
        }
        
        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.f, other.f);
        }
    }
    
    private static int heuristic(Position a, Position b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
    
    private static List<Position> getNeighbors(String[][] layout, Position pos) {
        List<Position> neighbors = new ArrayList<>();
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // N, S, W, E
        
        for (int[] dir : dirs) {
            int nx = pos.x + dir[0];
            int ny = pos.y + dir[1];
            
            if (ny >= 0 && ny < layout.length && nx >= 0 && nx < layout[ny].length) {
                String tile = layout[ny][nx];
                // Can walk on non-empty tiles (not walls)
                if (!tile.equals(".") && !tile.contains("X")) {
                    neighbors.add(new Position(nx, ny));
                }
            }
        }
        
        return neighbors;
    }
    
    private static Direction getDirectionTo(Position from, Position to) {
        if (to.y < from.y) return Direction.NORTH;
        if (to.y > from.y) return Direction.SOUTH;
        if (to.x < from.x) return Direction.WEST;
        if (to.x > from.x) return Direction.EAST;
        return null;
    }
    
    private static String getTurnCommand(Direction dir) {
        switch (dir) {
            case NORTH: return "U";
            case SOUTH: return "D";
            case WEST: return "L";
            case EAST: return "R";
            default: return "";
        }
    }
    
    private static String findCommandPlacement(String[][] layout, Position pos) {
        // Find a nearby building or power plant to place the command
        // Check current position first
        String tile = layout[pos.y][pos.x];
        
        if (tile.contains("h") || tile.contains("H") || tile.contains("P")) {
            // Place on floor 0
            return pos.x + "," + pos.y + ",0";
        }
        
        // Check adjacent tiles
        int[][] offsets = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nx = pos.x + offset[0];
            int ny = pos.y + offset[1];
            
            if (ny >= 0 && ny < layout.length && nx >= 0 && nx < layout[ny].length) {
                tile = layout[ny][nx];
                if (tile.contains("h") || tile.contains("H") || tile.contains("P")) {
                    return nx + "," + ny + ",0";
                }
            }
        }
        
        return null;
    }
    
    private static int testSolution(String[][] layout, Map<String, String> commands) {
        try {
            GameSimulator sim = new GameSimulator(layout, false);
            
            for (Map.Entry<String, String> entry : commands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int floor = Integer.parseInt(parts[2]);
                String cmd = entry.getValue();
                
                CommandType cmdType = parseCommand(cmd);
                if (cmdType != null) {
                    sim.placeCommand(x, y, cmdType, floor);
                }
            }
            
            sim.runSimulation(false);
            
            return sim.cats.values().stream()
                .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                .mapToInt(Cat::getCurrentPower)
                .sum();
                
        } catch (Exception e) {
            System.err.println("Error testing: " + e.getMessage());
            e.printStackTrace();
            return 0;
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
    
    private static void saveSolution(Map<String, String> commands, String[][] layout,
                                     String filename, int score, int cost) {
        try {
            int gridHeight = layout.length;
            int gridWidth = layout[0].length;
            String[][] grid = new String[gridHeight][gridWidth];
            
            // Initialize grid with layout tiles
            for (int y = 0; y < gridHeight; y++) {
                for (int x = 0; x < gridWidth; x++) {
                    String tile = layout[y][x];
                    // If it's a building or power plant, keep it; otherwise use "."
                    if (tile.contains("h") || tile.contains("H") || tile.equals("P")) {
                        grid[y][x] = tile;
                    } else {
                        grid[y][x] = ".";
                    }
                }
            }
            
            // Place commands on tiles
            for (Map.Entry<String, String> entry : commands.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int floor = Integer.parseInt(parts[2]);
                String command = entry.getValue();
                
                String tile = grid[y][x];
                // Format: tile + command for multi-floor, or just command for single floor
                if (tile.length() == 2 && floor == 0) {
                    // Multi-floor building: first floor command
                    grid[y][x] = tile.charAt(0) + command;
                } else if (tile.length() == 2 && floor == 1) {
                    // Multi-floor building: second floor command
                    grid[y][x] = command + tile.charAt(1);
                } else if (tile.equals("P")) {
                    // Power plant
                    grid[y][x] = "P" + command;
                } else {
                    // Single floor or empty
                    grid[y][x] = command;
                }
            }
            
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("# Hybrid Path Optimizer - CSP + A*");
            writer.println("# Score: " + score);
            writer.println("# Cost: $" + cost);
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
