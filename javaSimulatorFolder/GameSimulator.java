import java.util.*;
import java.io.*;

/**
 * GameSimulator.java - Main game logic for Kaiju Cats
 */

public class GameSimulator {
    private static final int DEFAULT_TURN_LIMIT = 15;
    private static final int DEFAULT_BUDGET = 200;

    protected final Tile[][] grid;
    protected final int width;
    protected final int height;
    protected final Map<CatColor, Cat> cats;
    private final Map<CatColor, Position> catBeds;
    protected int turn;
    protected final int turnLimit;
    protected final int startingBudget;
    protected int totalCommandCost;
    private int globalBedArrivalCounter;
    
    // For visualization
    private final List<GameState> stateHistory;
    private boolean trackHistory;

    // ============================================================================
    // POSITION HELPER CLASS
    // ============================================================================

    private static class Position {
        final int x;
        final int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position)) return false;
            Position position = (Position) o;
            return x == position.x && y == position.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    // ============================================================================
    // CONSTRUCTOR AND INITIALIZATION
    // ============================================================================

    public GameSimulator(String[][] layout) {
        this(layout, DEFAULT_BUDGET, DEFAULT_TURN_LIMIT, false);
    }

    public GameSimulator(String[][] layout, int startingBudget, int turnLimit) {
        this(layout, startingBudget, turnLimit, false);
    }

    public GameSimulator(String[][] layout, boolean trackHistory) {
        this(layout, DEFAULT_BUDGET, DEFAULT_TURN_LIMIT, trackHistory);
    }

    public GameSimulator(String[][] layout, int startingBudget, int turnLimit, boolean trackHistory) {
        this.height = layout.length;
        this.width = layout[0].length;
        this.grid = new Tile[height][width];
        this.cats = new HashMap<>();
        this.catBeds = new HashMap<>();
        this.turn = 0;
        this.turnLimit = turnLimit;
        this.startingBudget = startingBudget;
        this.totalCommandCost = 0;
        this.globalBedArrivalCounter = 0;
        this.trackHistory = trackHistory;
        this.stateHistory = trackHistory ? new ArrayList<>() : null;

        parseLayout(layout);
        
        if (trackHistory) {
            captureState();
        }
    }

    private void parseLayout(String[][] layout) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String code = layout[y][x];
                parseTile(code, x, y);
            }
        }
    }

    private void parseTile(String code, int x, int y) {
        // Cat starting positions
        if (code.equals("RStart")) {
            cats.put(CatColor.RED, new Cat(CatColor.RED, x, y, Direction.EAST));
            grid[y][x] = new EmptyTile();
            return;
        }
        if (code.equals("GStart")) {
            cats.put(CatColor.GREEN, new Cat(CatColor.GREEN, x, y, Direction.EAST));
            grid[y][x] = new EmptyTile();
            return;
        }
        if (code.equals("BStart")) {
            cats.put(CatColor.BLUE, new Cat(CatColor.BLUE, x, y, Direction.EAST));
            grid[y][x] = new EmptyTile();
            return;
        }

        // Cat beds
        if (code.equals("RBed") || code.equals("UI_R")) {
            catBeds.put(CatColor.RED, new Position(x, y));
            grid[y][x] = new CatBedTile(CatColor.RED);
            return;
        }
        if (code.equals("GBed") || code.equals("UI_G")) {
            catBeds.put(CatColor.GREEN, new Position(x, y));
            grid[y][x] = new CatBedTile(CatColor.GREEN);
            return;
        }
        if (code.equals("BBed") || code.equals("UI_B")) {
            catBeds.put(CatColor.BLUE, new Position(x, y));
            grid[y][x] = new CatBedTile(CatColor.BLUE);
            return;
        }

        // Houses (buildings)
        if (code.equals("h")) {
            grid[y][x] = new BuildingTile(true, 1); // Small, 1 floor
            return;
        }
        if (code.equals("hh")) {
            grid[y][x] = new BuildingTile(true, 2); // Small, 2 floors
            return;
        }
        if (code.equals("H")) {
            grid[y][x] = new BuildingTile(false, 1); // Big, 1 floor
            return;
        }
        if (code.equals("HH")) {
            grid[y][x] = new BuildingTile(false, 2); // Big, 2 floors
            return;
        }

        // Special tiles
        if (code.equals("P")) {
            grid[y][x] = new PowerPlantTile();
            return;
        }
        if (code.equals("X")) {
            grid[y][x] = new BoulderTile();
            return;
        }
        if (code.equals("S")) {
            grid[y][x] = new SpikeTrapTile();
            return;
        }
        if (code.equals("M")) {
            grid[y][x] = new MudTile();
            return;
        }
        if (code.equals("#")) {
            grid[y][x] = new WallTile();
            return;
        }
        if (code.equals(".")) {
            grid[y][x] = new EmptyTile();
            return;
        }

        // Default to empty for unknown codes
        grid[y][x] = new EmptyTile();
    }

    // ============================================================================
    // COMMAND PLACEMENT
    // ============================================================================

    public boolean placeCommand(int x, int y, CommandType commandType) {
        return placeCommand(x, y, commandType, 0);
    }

    public boolean placeCommand(int x, int y, CommandType commandType, int floor) {
        if (!isWithinBounds(x, y)) {
            return false;
        }

        Tile tile = grid[y][x];

        if (!tile.canHoldCommand()) {
            return false;
        }

        Command command = new Command(commandType);
        int commandCost = command.getCost();

        // Check budget
        if (totalCommandCost + commandCost > startingBudget) {
            return false;
        }

        // Place the command
        boolean success = tile.placeCommand(command, floor);
        if (success) {
            totalCommandCost += commandCost;
        }

        return success;
    }

    public int getBudgetRemaining() {
        return startingBudget - totalCommandCost;
    }

    // ============================================================================
    // SIMULATION
    // ============================================================================

    public void simulateTurn() {
        turn++;

        // Phase 1: Planning & Target Calculation
        List<CatMovement> movements = new ArrayList<>();

        for (Cat cat : cats.values()) {
            if (cat.getStatus() == CatStatus.ACTIVE) {
                int targetX = cat.getX() + cat.getDirection().getDx();
                int targetY = cat.getY() + cat.getDirection().getDy();
                movements.add(new CatMovement(cat, targetX, targetY));
            } else if (cat.getStatus() == CatStatus.STUCK_MUD) {
                // Cat is stuck, skip movement but clear stuck status
                cat.setStatus(CatStatus.ACTIVE);
            } else if (cat.getStatus() == CatStatus.STOMPING) {
                // Cat is stomping, stay in place and destroy next floor
                Tile currentTile = getTile(cat.getX(), cat.getY());
                if (currentTile != null) {
                    // Apply effects to destroy next floor
                    currentTile.applyEffects(cat);
                }
                // Stomping only lasts one turn, then return to active
                cat.setStatus(CatStatus.ACTIVE);
            }
        }

        // Phase 2: Movement & Tile Interaction Resolution
        // Sort by current power (lowest first for arrival order)
        movements.sort(Comparator.comparingInt(m -> m.cat.getCurrentPower()));

        Map<Position, List<Cat>> tileOccupants = new HashMap<>();

        for (CatMovement movement : movements) {
            Cat cat = movement.cat;
            int targetX = movement.targetX;
            int targetY = movement.targetY;

            Tile targetTile = getTile(targetX, targetY);

            // Handle impassable tiles (Boulder, Wall, out of bounds)
            if (targetTile == null || !targetTile.isPassable()) {
                // Rebound: reverse direction
                cat.reverseDirection();
                // Stay at current position
                Position currentPos = new Position(cat.getX(), cat.getY());
                tileOccupants.computeIfAbsent(currentPos, k -> new ArrayList<>()).add(cat);
                continue;
            }

            // Move cat to target position
            cat.setPosition(targetX, targetY);

            // Track occupants for fight resolution
            Position targetPos = new Position(targetX, targetY);
            tileOccupants.computeIfAbsent(targetPos, k -> new ArrayList<>()).add(cat);
        }

        // Phase 3: Fight Resolution (BEFORE tile effects)
        for (List<Cat> catsAtTile : tileOccupants.values()) {
            if (catsAtTile.size() > 1) {
                // Find winner (highest power, ties broken by hierarchy)
                Cat winner = catsAtTile.stream()
                        .max(Comparator.comparingInt(Cat::getCurrentPower)
                                .thenComparingInt(c -> -c.getHierarchy()))
                        .orElse(null);

                for (Cat cat : catsAtTile) {
                    if (cat != winner) {
                        cat.setStatus(CatStatus.DEFEATED);
                    }
                }
            }
        }

        // Phase 4: Apply Tile Effects (AFTER combat, only for non-defeated cats)
        for (CatMovement movement : movements) {
            Cat cat = movement.cat;
            
            // Skip defeated cats
            if (cat.getStatus() == CatStatus.DEFEATED) {
                continue;
            }

            int targetX = movement.targetX;
            int targetY = movement.targetY;
            Tile targetTile = getTile(targetX, targetY);

            // Apply tile effects only to surviving cats
            if (targetTile != null && targetTile.isPassable()) {
                targetTile.applyEffects(cat);

                // Check for cat bed arrival
                if (targetTile instanceof CatBedTile) {
                    CatBedTile bedTile = (CatBedTile) targetTile;
                    if (bedTile.getAssociatedCat() == cat.getColor()) {
                        cat.setStatus(CatStatus.FINISHED);
                        globalBedArrivalCounter++;

                        // Apply arrival bonus
                        if (globalBedArrivalCounter == 1) {
                            cat.addPower(2000);
                        } else if (globalBedArrivalCounter == 2) {
                            cat.multiplyPower(3);
                        } else if (globalBedArrivalCounter == 3) {
                            cat.multiplyPower(5);
                        }
                    }
                }
            }
        }
        
        if (trackHistory) {
            captureState();
        }
    }

    public void runSimulation(boolean verbose) {
        runSimulation(verbose, null);
    }

    /**
     * Run simulation silently with no output - optimized for performance
     * Use this for running millions of simulations
     */
    public int runSimulationSilent() {
        for (int i = 1; i <= turnLimit; i++) {
            simulateTurn();

            // Check if all cats are finished or defeated
            if (cats.values().stream().allMatch(cat ->
                    cat.getStatus() == CatStatus.FINISHED || cat.getStatus() == CatStatus.DEFEATED)) {
                break;
            }
        }

        // Calculate final score
        return cats.values().stream()
                .mapToInt(Cat::getCurrentPower)
                .sum();
    }

    public int runSimulation(boolean verbose, String outputFile) {
        List<String> outputLines = new ArrayList<>();

        if (verbose || outputFile != null) {
            printState(true, outputLines);
        }

        for (int i = 1; i <= turnLimit; i++) {
            simulateTurn();

            if (verbose || outputFile != null) {
                printState(false, outputLines);
            }

            // Check if all cats are finished or defeated
            if (cats.values().stream().allMatch(cat ->
                    cat.getStatus() == CatStatus.FINISHED || cat.getStatus() == CatStatus.DEFEATED)) {
                break;
            }
        }

        // Calculate final score
        int totalScore = cats.values().stream()
                .mapToInt(Cat::getCurrentPower)
                .sum();

        if (verbose || outputFile != null) {
            outputLines.add("\n" + "=".repeat(60));
            outputLines.add("SIMULATION COMPLETE");
            outputLines.add("=".repeat(60));
            for (Cat cat : cats.values()) {
                outputLines.add(cat.toString());
            }
            outputLines.add("\nFinal Total Score: " + totalScore);
            outputLines.add("Budget Used: $" + totalCommandCost + " / $" + startingBudget);
            outputLines.add("Budget Remaining: $" + getBudgetRemaining());
        }

        // Print to console if verbose
        if (verbose) {
            for (String line : outputLines) {
                System.out.println(line);
            }
        }

        // Write to file if specified
        if (outputFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                for (String line : outputLines) {
                    writer.println(line);
                }
                if (verbose) {
                    System.out.println("\n>>> Simulation output written to " + outputFile);
                }
            } catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
        }

        return totalScore;
    }

    private void printState(boolean initial, List<String> output) {
        if (initial) {
            output.add("=".repeat(60));
            output.add("INITIAL STATE");
            output.add("=".repeat(60));
        } else {
            output.add("\n" + "=".repeat(60));
            output.add("TURN " + turn);
            output.add("=".repeat(60));
        }

        for (Cat cat : cats.values()) {
            output.add(cat.toString());
        }

        // Print grid
        output.add("\nGrid:");
        String[][] gridDisplay = new String[height][width];

        // Initialize with tile display characters
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gridDisplay[y][x] = grid[y][x].getDisplayChar();
            }
        }

        // Mark cats (override tiles) - use uppercase to distinguish from houses
        for (Cat cat : cats.values()) {
            if (cat.getStatus() != CatStatus.DEFEATED) {
                gridDisplay[cat.getY()][cat.getX()] = cat.getColor().name().substring(0, 1); // Keep uppercase
            }
        }

        // Print grid
        for (String[] row : gridDisplay) {
            output.add(String.join(" ", row));
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private Tile getTile(int x, int y) {
        if (isWithinBounds(x, y)) {
            return grid[y][x];
        }
        return null;
    }

    // ============================================================================
    // INNER CLASS FOR MOVEMENT TRACKING
    // ============================================================================

    private static class CatMovement {
        final Cat cat;
        final int targetX;
        final int targetY;

        CatMovement(Cat cat, int targetX, int targetY) {
            this.cat = cat;
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }

    // ============================================================================
    // HISTORY TRACKING FOR VISUALIZATION
    // ============================================================================

    public static class GameState {
        public final int turnNumber;
        public final Map<CatColor, CatState> catStates;
        public final String[][] grid;

        public GameState(int turnNumber, Map<CatColor, CatState> catStates, String[][] grid) {
            this.turnNumber = turnNumber;
            this.catStates = catStates;
            this.grid = grid;
        }
    }

    public static class CatState {
        public final CatColor color;
        public final int power;
        public final int x;
        public final int y;
        public final Direction direction;
        public final CatStatus status;

        public CatState(CatColor color, int power, int x, int y, Direction direction, CatStatus status) {
            this.color = color;
            this.power = power;
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.status = status;
        }
    }

    private void captureState() {
        Map<CatColor, CatState> catStates = new HashMap<>();
        for (Map.Entry<CatColor, Cat> entry : cats.entrySet()) {
            Cat cat = entry.getValue();
            catStates.put(entry.getKey(), new CatState(
                cat.getColor(),
                cat.getCurrentPower(),
                cat.getX(),
                cat.getY(),
                cat.getDirection(),
                cat.getStatus()
            ));
        }

        String[][] gridSnapshot = new String[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gridSnapshot[y][x] = grid[y][x].getDisplayChar();
            }
        }

        // Mark cats on grid - use uppercase to distinguish from houses
        for (Cat cat : cats.values()) {
            if (cat.getStatus() != CatStatus.DEFEATED) {
                gridSnapshot[cat.getY()][cat.getX()] = cat.getColor().name().substring(0, 1); // Keep uppercase
            }
        }

        stateHistory.add(new GameState(turn, catStates, gridSnapshot));
    }

    public List<GameState> getHistory() {
        return stateHistory != null ? new ArrayList<>(stateHistory) : new ArrayList<>();
    }

    public int getTotalCommandCost() {
        return totalCommandCost;
    }

    public int getStartingBudget() {
        return startingBudget;
    }

    // ============================================================================
    // FILE LOADING
    // ============================================================================

    public static String[][] loadLayoutFromFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            // Parse the layout
            String layoutStr = content.toString()
                    .replaceAll("String\\[\\]\\[\\]\\s+board\\s*=\\s*", "")
                    .replaceAll(";", "")
                    .trim();

            return parseLayoutString(layoutStr);
        }
    }

    private static String[][] parseLayoutString(String layoutStr) {
        List<String[]> rows = new ArrayList<>();

        // Remove outer braces
        layoutStr = layoutStr.substring(layoutStr.indexOf('{') + 1, layoutStr.lastIndexOf('}'));

        // Split by rows
        String[] rowStrings = layoutStr.split("\\},\\s*\\{");

        for (String rowStr : rowStrings) {
            rowStr = rowStr.replaceAll("[{}]", "").trim();
            String[] cells = rowStr.split(",\\s*");

            // Remove quotes from each cell
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cells[i].replaceAll("\"", "").trim();
            }

            rows.add(cells);
        }

        return rows.toArray(new String[0][]);
    }

    // ============================================================================
    // MAIN METHOD
    // ============================================================================

    public static void main(String[] args) {
        String filename = args.length > 0 ? args[0] : "starting.txt";
        String outputFile = "simulation_output.txt";

        try {
            String[][] layout = loadLayoutFromFile(filename);
            System.out.println("Loaded layout from " + filename);
            System.out.println("Grid size: " + layout[0].length + "x" + layout.length);
            System.out.println();

            GameSimulator simulator = new GameSimulator(layout);

            // Example: Place some commands
            // simulator.placeCommand(1, 0, CommandType.TURN_S);
            // simulator.placeCommand(2, 2, CommandType.POWERUP);

            simulator.runSimulation(true, outputFile);

        } catch (FileNotFoundException e) {
            System.out.println("Error: File '" + filename + "' not found.");
            System.out.println("\nUsing example layout instead...");

            // Example layout with new compact codes
            String[][] exampleLayout = {
                {"BStart", "H",  "h",  ".",  "h",  "P",     "#"},
                {"#",      "H",  ".",  "X",  "h",  "RBed",  "UI_R"},
                {"RStart", "hh", "H",  "X",  "S",  "H",     "UI_G"},
                {"#",      "H",  "P",  ".",  "M",  ".",     "UI_B"},
                {"GStart", "h",  "h",  ".",  "h",  "h",     "#"}
            };

            GameSimulator simulator = new GameSimulator(exampleLayout);
            simulator.runSimulation(true, outputFile);

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }
}
