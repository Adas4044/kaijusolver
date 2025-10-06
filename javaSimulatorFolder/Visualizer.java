import java.util.*;
import java.io.*;

/**
 * Visualizer.java - Basic visualizer for Kaiju Cats game simulation
 * Creates a simple text-based visualization showing all turns
 */

public class Visualizer {
    
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_ORANGE = "\u001B[38;5;208m";
    private static final String ANSI_GRAY = "\u001B[90m";

    private final GameSimulator simulator;
    private final boolean useColors;

    public Visualizer(GameSimulator simulator, boolean useColors) {
        this.simulator = simulator;
        this.useColors = useColors;
    }

    public void generateVisualization(String outputFile) {
        List<String> output = new ArrayList<>();

        output.add("╔" + "═".repeat(78) + "╗");
        output.add("║" + center("KAIJU CATS - GAME VISUALIZATION", 78) + "║");
        output.add("╚" + "═".repeat(78) + "╝");
        output.add("");

        // Show initial state
        List<GameSimulator.GameState> history = simulator.getHistory();
        if (!history.isEmpty()) {
            GameSimulator.GameState initial = history.get(0);
            output.add("┌" + "─".repeat(78) + "┐");
            output.add("│" + center("INITIAL STATE", 78) + "│");
            output.add("└" + "─".repeat(78) + "┘");
            output.add("");
            renderGameState(initial, output);
            output.add("");
        }

        // Show each turn
        for (int i = 1; i < history.size(); i++) {
            GameSimulator.GameState state = history.get(i);
            output.add("┌" + "─".repeat(78) + "┐");
            output.add("│" + center("TURN " + i, 78) + "│");
            output.add("└" + "─".repeat(78) + "┘");
            output.add("");
            renderGameState(state, output);
            output.add("");
        }

        // Final summary
        output.add("╔" + "═".repeat(78) + "╗");
        output.add("║" + center("FINAL RESULTS", 78) + "║");
        output.add("╚" + "═".repeat(78) + "╝");
        output.add("");

        GameSimulator.GameState finalState = history.get(history.size() - 1);
        for (GameSimulator.CatState cat : finalState.catStates.values()) {
            String status = String.format("%-6s Cat: Power=%6d  Status=%-10s  Position=(%d,%d)",
                    cat.color, cat.power, cat.status, cat.x, cat.y);
            output.add(colorize(status, cat.color.name()));
        }

        int totalScore = finalState.catStates.values().stream()
                .mapToInt(c -> c.power)
                .sum();
        output.add("");
        output.add("Total Score: " + totalScore);
        output.add("Budget Used: $" + simulator.getTotalCommandCost() + " / $" + simulator.getStartingBudget());
        output.add("");

        // Write to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            for (String line : output) {
                writer.println(line);
            }
            System.out.println("Visualization written to: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error writing visualization: " + e.getMessage());
        }

        // Also print to console
        for (String line : output) {
            System.out.println(line);
        }
    }

    private void renderGameState(GameSimulator.GameState state, List<String> output) {
        // Show cat statuses
        for (GameSimulator.CatState cat : state.catStates.values()) {
            String dirArrow = getDirectionArrow(cat.direction.name());
            String status = String.format("%-6s: Power=%5d  Dir=%s  Pos=(%d,%d)  Status=%s",
                    cat.color, cat.power, dirArrow, cat.x, cat.y, cat.status);
            output.add(colorize(status, cat.color.name()));
        }
        output.add("");

        // Render grid
        String[][] grid = state.grid;
        int height = grid.length;
        int width = grid[0].length;

        // Add column numbers
        StringBuilder colNumbers = new StringBuilder("   ");
        for (int x = 0; x < width; x++) {
            colNumbers.append(String.format("%-4d", x));
        }
        output.add(colNumbers.toString());

        // Add top border
        output.add("  ┌" + "───┬".repeat(width - 1) + "───┐");

        // Add grid rows
        for (int y = 0; y < height; y++) {
            StringBuilder row = new StringBuilder(String.format("%d │", y));
            
            for (int x = 0; x < width; x++) {
                String cell = grid[y][x];
                String display = formatCell(cell);
                row.append(display);
                if (x < width - 1) {
                    row.append("│");
                }
            }
            row.append("│");
            output.add(row.toString());

            // Add separator (except for last row)
            if (y < height - 1) {
                output.add("  ├" + "───┼".repeat(width - 1) + "───┤");
            }
        }

        // Add bottom border
        output.add("  └" + "───┴".repeat(width - 1) + "───┘");
    }

    private String formatCell(String cell) {
        if (cell == null || cell.isEmpty()) {
            return "   ";
        }

        // Cat markers (uppercase to distinguish from houses)
        if (cell.equals("R")) return colorize(" R ", "RED");
        if (cell.equals("G")) return colorize(" G ", "GREEN");
        if (cell.equals("B")) return colorize(" B ", "BLUE");

        // Houses - orange color
        if (cell.startsWith("h") || cell.startsWith("H")) {
            return colorize(String.format("%-3s", cell), "ORANGE");
        }

        // Power plant - yellow
        if (cell.equals("P")) {
            return colorize(" P ", "YELLOW");
        }

        // Obstacles
        if (cell.equals("X")) return " X ";
        if (cell.equals("#")) return " # ";
        if (cell.equals("S")) return " S ";
        if (cell.equals("M")) return " M ";

        // Cat beds
        if (cell.startsWith("UI_")) {
            String color = cell.substring(3);
            return colorize("BED", color);
        }

        // Empty
        if (cell.equals(".")) return " . ";

        return String.format("%-3s", cell);
    }

    private String colorize(String text, String type) {
        if (!useColors) {
            return text;
        }

        switch (type) {
            case "RED":
                return ANSI_RED + text + ANSI_RESET;
            case "GREEN":
                return ANSI_GREEN + text + ANSI_RESET;
            case "BLUE":
                return ANSI_BLUE + text + ANSI_RESET;
            case "ORANGE":
                return ANSI_ORANGE + text + ANSI_RESET;
            case "YELLOW":
                return ANSI_YELLOW + text + ANSI_RESET;
            default:
                return text;
        }
    }

    private String getDirectionArrow(String direction) {
        switch (direction) {
            case "NORTH": return "↑";
            case "SOUTH": return "↓";
            case "EAST": return "→";
            case "WEST": return "←";
            default: return direction;
        }
    }

    private String center(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }

    // ============================================================================
    // MAIN METHOD FOR TESTING
    // ============================================================================

    public static void main(String[] args) {
        String layoutFile = args.length > 0 ? args[0] : "starting.txt";
        String outputFile = "visualization.txt";
        boolean useColors = true;

        try {
            String[][] layout = GameSimulator.loadLayoutFromFile(layoutFile);
            System.out.println("Loaded layout from " + layoutFile);
            System.out.println("Grid size: " + layout[0].length + "x" + layout.length);
            System.out.println();

            // Create simulator with history tracking enabled
            GameSimulator simulator = new GameSimulator(layout, 200, 15, true);

            // Example: Place some commands
            // simulator.placeCommand(1, 0, CommandType.TURN_S);
            // simulator.placeCommand(5, 0, CommandType.POWERUP);

            // Run simulation
            for (int i = 1; i <= 15; i++) {
                simulator.simulateTurn();
            }

            Visualizer visualizer = new Visualizer(simulator, useColors);
            visualizer.generateVisualization(outputFile);

        } catch (FileNotFoundException e) {
            System.out.println("Error: File '" + layoutFile + "' not found.");
            System.out.println("\nUsing example layout instead...");

            String[][] exampleLayout = {
                {"BStart", "H",  "h",  ".",  "h",  "P",     "#"},
                {"#",      "H",  ".",  "X",  "h",  "RBed",  "UI_R"},
                {"RStart", "hh", "H",  "X",  "S",  "H",     "UI_G"},
                {"#",      "H",  "P",  ".",  "M",  ".",     "UI_B"},
                {"GStart", "h",  "h",  ".",  "h",  "h",     "#"}
            };

            GameSimulator simulator = new GameSimulator(exampleLayout, 200, 15, true);
            
            for (int i = 1; i <= 15; i++) {
                simulator.simulateTurn();
            }

            Visualizer visualizer = new Visualizer(simulator, useColors);
            visualizer.generateVisualization(outputFile);

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }
}
