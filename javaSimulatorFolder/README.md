# Kaiju Cats Java Simulator

A high-performance Java implementation of the Kaiju Cats game with visualization support and command system.

## Files

- **Tile.java** - All tile types, cats, commands, and game entities
- **GameSimulator.java** - Main game logic and simulation engine
- **Visualizer.java** - Text-based visualization generator
- **SimulatorExample.java** - Example usage patterns
- **starting.txt** - Default game layout
- **key.MD** - Game rules and tile descriptions

## Quick Start

### Compile
```bash
javac *.java
```

### Run Examples
```bash
# Run the example demonstrating all modes
java SimulatorExample

# Run visualization only
java Visualizer

# Run basic simulation
java GameSimulator starting.txt
```

## Usage Modes

### 1. Visualization Mode (for understanding)
Use when you want to see what's happening turn-by-turn:

```java
// Enable history tracking
GameSimulator sim = new GameSimulator(layout, true);

// Place commands
sim.placeCommand(x, y, CommandType.TURN_N);

// Run simulation
for (int i = 1; i <= 15; i++) {
    sim.simulateTurn();
}

// Generate visualization
Visualizer viz = new Visualizer(sim, true);
viz.generateVisualization("output.txt");
```

### 2. Performance Mode (for solving)
Use when running millions of simulations to find optimal strategies:

```java
// No history tracking (default)
GameSimulator sim = new GameSimulator(layout);

// Place commands
sim.placeCommand(x, y, CommandType.POWERUP);

// Run silently and get score
int score = sim.runSimulationSilent();
```

**Performance:** Can run ~10,000+ simulations per second

### 3. Debug Mode (with text output)
Use for debugging specific scenarios:

```java
GameSimulator sim = new GameSimulator(layout);
sim.placeCommand(x, y, CommandType.TURN_S);

// Run with verbose output and save to file
int score = sim.runSimulation(true, "debug.txt");
```

## Command System

### Available Commands
| Command | Cost | Effect |
|---------|------|--------|
| `TURN_N` | $10 | Cat turns North |
| `TURN_S` | $10 | Cat turns South |
| `TURN_E` | $10 | Cat turns East |
| `TURN_W` | $10 | Cat turns West |
| `STOMP` | $20 | Cat stomps again (destroys next floor) |
| `POWERUP` | $30 | Cat gains ×2 power |

### Placement Rules
- Commands can only be placed on houses (`h`, `hh`, `H`, `HH`) or power plants (`P`)
- Multi-floor houses: each floor can hold one command
- Budget: $200 total
- Commands execute when cat stomps the house

### Example Command Placement
```java
GameSimulator sim = new GameSimulator(layout);

// Place command on single-floor house
sim.placeCommand(1, 2, CommandType.TURN_N);

// Place command on specific floor of multi-floor house
sim.placeCommand(3, 4, CommandType.STOMP, 0); // Floor 0 (bottom)
sim.placeCommand(3, 4, CommandType.POWERUP, 1); // Floor 1 (top)

// Check remaining budget
int remaining = sim.getBudgetRemaining();
```

## Tile Codes

### Cats
- `RStart`, `GStart`, `BStart` - Cat starting positions
- `RBed`, `GBed`, `BBed` - Cat destinations

### Houses
- `h` - Small house (1 floor, +250 power)
- `hh` - Small house (2 floors, +500 power total)
- `H` - Big house (1 floor, +500 power)
- `HH` - Big house (2 floors, +1000 power total)

### Special Tiles
- `P` - Power plant (×2 power)
- `.` - Empty tile
- `X` - Boulder (cats rebound)
- `S` - Spike trap (halves power)
- `M` - Mud (cat skips next turn)
- `#` - Wall (impassable)

## Game Mechanics

### Turn Order
1. **Planning Phase** - Calculate where each cat will move
2. **Movement Phase** - Cats move in order of current power (lowest first)
3. **Fight Resolution** - If multiple cats on same tile, highest power wins

### Cat Power
- Red: starts with 1000 power
- Green: starts with 2000 power
- Blue: starts with 3000 power

### Arrival Bonuses (at bed)
- 1st cat: +2000 power
- 2nd cat: ×3 power
- 3rd cat: ×5 power

### Building Destruction
When a cat stomps a building:
1. Top floor command executes
2. Cat gains power for that floor
3. Floor is destroyed
4. If command was `STOMP`, repeat for next floor

## Performance Tips

For running many simulations (e.g., genetic algorithms, Monte Carlo):

```java
// DON'T track history
GameSimulator sim = new GameSimulator(layout); // Default is false

// Use runSimulationSilent()
int score = sim.runSimulationSilent();

// Reuse layout array
String[][] layout = GameSimulator.loadLayoutFromFile("starting.txt");
for (int i = 0; i < 1000000; i++) {
    GameSimulator sim = new GameSimulator(layout);
    // ... place commands ...
    int score = sim.runSimulationSilent();
}
```

## Board Layout Format

The `starting.txt` file should contain:
```java
String[][] board = {
    {"BStart", "H",  "h",  ".",  "h",  "P",   "#"},
    {"#",      "H",  ".",  "X",  "h",  "RBed","UI_R"},
    {"RStart", "hh", "H",  "X",  "S",  "H",   "UI_G"},
    {"#",      "H",  "P",  ".",  "M",  ".",   "UI_B"},
    {"GStart", "h",  "h",  ".",  "h",  "h",   "#"}
};
```

## Examples

See `SimulatorExample.java` for complete working examples of:
- Visualization generation
- High-performance simulation
- Strategy search
- Debug output
