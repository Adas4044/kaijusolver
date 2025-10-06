import java.io.*;

/**
 * CommandDemo.java - Demonstrates the command system
 * Shows how cats change direction when they stomp houses with commands
 */

public class CommandDemo {

    public static void main(String[] args) throws IOException {
        String layoutFile = args.length > 0 ? args[0] : "starting.txt";
        String outputFile = "command_demo_output.txt";

        // Load the layout
        String[][] layout = GameSimulator.loadLayoutFromFile(layoutFile);
        System.out.println("Loaded layout from " + layoutFile);
        System.out.println("Grid size: " + layout[0].length + "x" + layout.length);
        System.out.println();

        // Create simulator with history tracking
        GameSimulator simulator = new GameSimulator(layout, 200, 15, true);

        System.out.println("╔" + "═".repeat(60) + "╗");
        System.out.println("║" + center("COMMAND PLACEMENT DEMO", 60) + "║");
        System.out.println("╚" + "═".repeat(60) + "╝");
        System.out.println();

        // Place strategic commands to help cats reach their beds
        // Blue cat starts at (0,0), needs to go to BBed at (6,3)
        // Red cat starts at (0,2), needs to go to RBed at (6,1)
        // Green cat starts at (0,4), needs to go to GBed at (6,2)

        System.out.println("Placing commands:");
        
        // Command 1: Turn Blue cat south when it hits position (1,0)
        if (simulator.placeCommand(1, 0, CommandType.TURN_S)) {
            System.out.println("✓ [1,0] TURN_S ($10) - Blue cat will turn south");
        }
        
        // Command 2: Turn Blue cat east when it hits position (1,1)
        if (simulator.placeCommand(1, 1, CommandType.TURN_E)) {
            System.out.println("✓ [1,1] TURN_E ($10) - After turning south, turn back east");
        }
        
        // Command 3: Turn Red cat north when it hits position (1,2) 
        if (simulator.placeCommand(1, 2, CommandType.TURN_N, 0)) {
            System.out.println("✓ [1,2] TURN_N ($10) - Red cat will turn north (floor 0)");
        }
        
        // Command 4: Turn Red cat east when red reaches (1,1)
        if (simulator.placeCommand(1, 2, CommandType.TURN_E, 1)) {
            System.out.println("✓ [1,2] TURN_E ($10) - Red cat will turn east (floor 1)");
        }
        
        // Command 5: Add POWERUP to help score
        if (simulator.placeCommand(5, 0, CommandType.POWERUP)) {
            System.out.println("✓ [5,0] POWERUP ($30) - Double power when stomped");
        }

        System.out.println();
        System.out.println("Budget remaining: $" + simulator.getBudgetRemaining() + " / $200");
        System.out.println();
        System.out.println("Running simulation...");
        System.out.println();

        // Run simulation
        for (int i = 1; i <= 15; i++) {
            simulator.simulateTurn();
        }

        // Generate visualization
        Visualizer visualizer = new Visualizer(simulator, true);
        visualizer.generateVisualization(outputFile);

        System.out.println();
        System.out.println("╔" + "═".repeat(60) + "╗");
        System.out.println("║" + center("RESULTS", 60) + "║");
        System.out.println("╚" + "═".repeat(60) + "╝");
        System.out.println();

        // Show final results
        java.util.List<GameSimulator.GameState> history = simulator.getHistory();
        GameSimulator.GameState finalState = history.get(history.size() - 1);
        
        for (GameSimulator.CatState cat : finalState.catStates.values()) {
            String statusIcon = cat.status == CatStatus.FINISHED ? "🏁" : 
                               cat.status == CatStatus.ACTIVE ? "🐱" : "💀";
            System.out.printf("%s %s Cat: Power=%d, Position=(%d,%d), Status=%s%n",
                    statusIcon, cat.color, cat.power, cat.x, cat.y, cat.status);
        }

        int totalScore = finalState.catStates.values().stream()
                .mapToInt(c -> c.power)
                .sum();

        System.out.println();
        System.out.println("Total Score: " + totalScore);
        System.out.println("Commands Used: $" + simulator.getTotalCommandCost());
        System.out.println();
        System.out.println("Full visualization saved to: " + outputFile);
    }

    private static String center(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }
}
