# Movement System - Java Array Format

The `movements.txt` file now uses **the exact same format as `starting.txt`** - Java 2D array syntax!

## Format Comparison

### starting.txt (Board Layout)
```java
String[][] board = {
    {"BStart", "H",  "h",  ".",  "h",  "P",   "#"},
    {"#",      "H",  ".",  "X",  "h",  "H","RBed"},
    {"RStart", "hh",  "H",  "X",  "S",  "H",   "GBed"},
    {"#",      "H",  "P",  ".",  "M",  ".",   "BBed"},
    {"GStart", "h",  "h",  ".",  "h",  "h",   "#"}
};
```

### movements.txt (Commands)
```java
String[][] movements = {
    {".", "D", ".", ".", ".", "P", "."},
    {".", ".", ".", ".", ".", ".", "."},
    {".", "UR", ".", ".", ".", ".", "."},
    {".", ".", ".", ".", ".", ".", "."},
    {".", "L", ".", ".", ".", ".", "."}
};
```

**Same structure, same format!** Just replace tile names with command codes.

## Command Codes

| Code | Direction | Cost | Description |
|------|-----------|------|-------------|
| `"L"` | Left (West) | $10 | Cat turns west |
| `"R"` | Right (East) | $10 | Cat turns east |
| `"U"` | Up (North) | $10 | Cat turns north |
| `"D"` | Down (South) | $10 | Cat turns south |
| `"P"` | Powerup | $30 | Double cat's power |
| `"S"` | Stomp | $20 | Loop and execute again |
| `"."` | No command | $0 | Empty (no command) |

## Multi-Floor Buildings

For 2-floor buildings (`hh` or `HH`), use **2 characters**:

```java
String[][] movements = {
    {".", "UR", ".", ".", ".", ".", "."},  // Multi-floor at [1,0]
    //     ^^
    //     ||
    //     |+-- R = Bottom floor (floor 0)
    //     +--- U = Top floor (floor 1, executes first)
};
```

**Examples:**
- `"UR"` = Turn Up (top), then Turn Right (bottom)
- `"DS"` = Turn Down (top), then Stomp (bottom)
- `"SS"` = Stomp (top), then Stomp (bottom) - double loop!
- `"PP"` = Powerup twice (only works on HH buildings)

## Quick Start

### 1. Generate Template

```bash
javac GridMovementGenerator.java
java GridMovementGenerator starting_simple.txt movements.txt
```

### 2. Edit movements.txt

Replace `"."` with command codes where buildings or power plants exist:

```java
String[][] movements = {
    {".", "D", ".", ".", ".", "P", "."},   // Row 0: Blue cat path
    {".", ".", ".", ".", ".", ".", "."},   // Row 1: Wall, no commands
    {".", "UR", ".", ".", ".", ".", "."},  // Row 2: Red cat path
    {".", ".", "P", ".", ".", ".", "."},   // Row 3: Power plant
    {".", "L", ".", ".", ".", ".", "."}    // Row 4: Green cat path
};
```

### 3. Run Simulation

Update GridMovementDemo to use starting_simple.txt:

```bash
javac GridMovementDemo.java
java GridMovementDemo
```

Or use the demo manually:
```java
String[][] layout = loadSimpleLayout("starting_simple.txt");
String[][] movements = loadMovementGrid("movements.txt");
// ... create simulator and run
```

## Where to Place Commands

Commands can **only** be placed on these tiles:

| Tile | Description | Floors | Command Format |
|------|-------------|--------|----------------|
| `h` | Small house | 1 | `"D"` (1 char) |
| `hh` | Small house | 2 | `"UR"` (2 chars) |
| `H` | Big house | 1 | `"L"` (1 char) |
| `HH` | Big house | 2 | `"DS"` (2 chars) |
| `P` | Power plant | 1 | `"P"` (1 char) |

**Do NOT place commands on:**
- Empty tiles (`.`)
- Boulders (`X`)
- Spike traps (`S`)
- Mud (`M`)
- Walls (`#`)
- Cat starts (`BStart`, `RStart`, `GStart`)
- Cat beds (`RBed`, `GBed`, `BBed`)

## Example Strategy

```java
String[][] movements = {
    // Blue cat (starts row 0, col 0)
    {".", "D",  ".",  ".",  ".",  "P",  "."},   // Turn Down at [1,0], Powerup at [5,0]
    
    // Wall row - no commands needed
    {".", ".",  ".",  ".",  ".",  ".",  "."},
    
    // Red cat (starts row 2, col 0)
    {".", "UR", "L",  ".",  ".",  ".",  "."},   // Multi-floor U+R at [1,2], Left at [2,2]
    
    // Power plant row
    {".", ".",  "P",  ".",  ".",  ".",  "."},   // Powerup at [2,3]
    
    // Green cat (starts row 4, col 0)
    {".", "L",  "R",  ".",  ".",  ".",  "."}    // Left at [1,4], Right at [2,4]
};
```

## Budget Management

- **Total budget:** $200
- **Turn commands** (L/R/U/D): $10 each = 20 commands max
- **Stomp** (S): $20 each = 10 stomps max  
- **Powerup** (P): $30 each = 6-7 powerups max

Mix wisely! Example budget:
- 10 turns ($100) + 3 stomps ($60) + 1 powerup ($30) = $190 ✓

## Tips

1. **Match dimensions:** movements array must have same rows/cols as board
2. **Quote strings:** All commands must be in quotes: `"D"`, not `D`
3. **Commas matter:** Don't forget commas between array elements
4. **Multi-floor:** Exactly 2 chars for `hh`/`HH`, like `"UR"`
5. **Visual alignment:** Spaces in Java arrays are ignored, use them for readability
6. **Test incrementally:** Add a few commands, test, then add more

## Common Patterns

### Routing Around Obstacles
```java
{".", "D", ".", ".", ".", ".", "."},  // Turn down to avoid boulder
{".", ".", "R", ".", ".", ".", "."},  // Turn right to continue
```

### Power Maximization
```java
{".", ".", ".", ".", ".", "P", "."},  // POWERUP on power plant tile
```

### Loop Strategy
```java
{".", "DS", ".", ".", ".", ".", "."},  // Turn Down + STOMP = repeat
```

### Complex Multi-Floor
```java
{".", "SS", ".", ".", ".", ".", "."},  // Double STOMP = keep looping
{".", "UD", ".", ".", ".", ".", "."},  // Turn Up + Turn Down
{".", "PP", ".", ".", ".", ".", "."},  // Double Powerup (on HH only!)
```

## Files

- **GridMovementGenerator.java** - Generates movements.txt template
- **GridMovementLoader.java** - Parses Java array format
- **GridMovementDemo.java** - Demo workflow
- **movements.txt** - Your command array (edit this!)
- **starting_simple.txt** - Board layout in simple format
- **starting.txt** - Original board layout

## Workflow

1. **Generate:** `java GridMovementGenerator starting_simple.txt movements.txt`
2. **Edit:** Open `movements.txt`, replace `"."` with command codes
3. **Test:** `java GridMovementDemo`
4. **View:** Check `grid_demo_output.txt` for visualization
5. **Iterate:** Adjust commands based on results
6. **Optimize:** Try different strategies to maximize score!

## Syntax Checklist

✅ **Correct:**
```java
String[][] movements = {
    {".", "D", "L", ".", ".", "P", "."},
    {".", "UR", ".", ".", ".", ".", "."}
};
```

❌ **Incorrect:**
```java
String[][] movements = {
    {., D, L, ., ., P, .},              // Missing quotes
    {".", "U", ".", ".", ".", ".", "."}  // Missing comma at end of row
    {".", "L", ".", ".", ".", ".", "."}
}                                        // Missing semicolon
```

## Advanced: Direct Integration

You can also load and use movements directly in your code:

```java
// Load board and movements
String[][] board = loadSimpleLayout("starting_simple.txt");
String[][] movements = loadMovementGrid("movements.txt");

// Create simulator
GameSimulator sim = new GameSimulator(board, true);

// Apply commands from movements grid
GridMovementLoader.loadAndApplyCommands("movements.txt", sim, board);

// Run simulation
sim.runSimulation(false);
```

This makes it easy to test many different movement strategies programmatically!
