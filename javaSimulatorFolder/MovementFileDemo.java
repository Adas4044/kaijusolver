import java.io.*;

/**
 * Demo showing how to use movements.txt file to control the game.
 * 
 * Workflow:
 * 1. Generate movements.txt template from starting.txt
 * 2. Edit movements.txt to add your commands
 * 3. Run simulation with commands from movements.txt
 */
public class MovementFileDemo {
    
    public static void main(String[] args) {
        try {
            String layoutFile = "starting.txt";
            String movementFile = "movements.txt";
            String outputFile = "movement_demo_output.txt";
            
            // Check if movements.txt exists, if not generate it
            File movFile = new File(movementFile);
            if (!movFile.exists()) {
                System.out.println("movements.txt not found. Generating template...\n");
                MovementFileGenerator.generateMovementFile(layoutFile, movementFile);
                System.out.println("\n" + "=".repeat(60));
                System.out.println("NEXT STEPS:");
                System.out.println("1. Edit " + movementFile + " to add your commands");
                System.out.println("2. Run this program again to simulate");
                System.out.println("=".repeat(60));
                return;
            }
            
            // Load the board layout
            System.out.println("Loading layout from: " + layoutFile);
            String[][] layout = GameSimulator.loadLayoutFromFile(layoutFile);
            
            // Create simulator
            GameSimulator simulator = new GameSimulator(layout, true); // Enable history for visualization
            
            System.out.println("Grid size: " + layout[0].length + "x" + layout.length);
            
            // Load commands from movements.txt
            int commandsLoaded = MovementFileLoader.loadAndApplyCommands(movementFile, simulator);
            
            if (commandsLoaded == 0) {
                System.out.println("\n⚠ No commands loaded. Edit " + movementFile + " to add commands.");
                System.out.println("\nExample commands:");
                System.out.println("1,0,TURN_S");
                System.out.println("1,2,TURN_N,0");
                System.out.println("1,2,TURN_E,1");
                System.out.println("5,0,POWERUP");
                return;
            }
            
            // Run the simulation
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                   RUNNING SIMULATION                       ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            simulator.runSimulation(false);
            
            // Generate visualization
            System.out.println("Generating visualization...");
            Visualizer visualizer = new Visualizer(simulator, true);
            visualizer.generateVisualization(outputFile);
            System.out.println("Visualization written to: " + outputFile);
            
            // Show results with emoji
            System.out.println("\n╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                          RESULTS                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            for (Cat cat : simulator.cats.values()) {
                String statusIcon = cat.getStatus() == CatStatus.FINISHED ? "🏁" : 
                                   cat.getStatus() == CatStatus.ACTIVE ? "🐱" : "💀";
                
                System.out.println(statusIcon + " " + cat.getColor() + " Cat: " +
                                 "Power=" + cat.getCurrentPower() + ", " +
                                 "Position=(" + cat.getX() + "," + cat.getY() + "), " +
                                 "Status=" + cat.getStatus());
            }
            
            int totalScore = simulator.cats.values().stream()
                .filter(cat -> cat.getStatus() != CatStatus.DEFEATED)
                .mapToInt(Cat::getCurrentPower)
                .sum();
            System.out.println("\nTotal Score: " + totalScore);
            System.out.println("Commands Used: $" + simulator.getTotalCommandCost());
            System.out.println("\nFull visualization saved to: " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
