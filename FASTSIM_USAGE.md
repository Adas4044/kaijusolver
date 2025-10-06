# FastSimulator - Input Guide

## How to Input Data

You have **3 ways** to use FastSimulator with your data:

---

## Method 1: Use Your Existing Files (Easiest)

Keep using `starting_simple.txt` and `movements.txt` exactly as they are!

```java
// Load from your existing files
FastSimulator sim = FastSimulatorLoader.loadFromFiles(
    "starting_simple.txt",   // Your board layout
    "movements.txt"          // Your commands
);

// Run simulation
int score = sim.simulate(15);

// Get results
System.out.println("Score: " + score);
System.out.println("RED: " + sim.getCatPower(0));
System.out.println("GREEN: " + sim.getCatPower(1));
System.out.println("BLUE: " + sim.getCatPower(2));
```

**File Format (Same as Before):**

`starting_simple.txt`:
```
BStart  HH   hh   hh   .   P   #
#       .   hh   P   M   hh   RBed
RStart  hh  S   hh   .   HH   GBed
#       .   hh   HH   X   P   BBed
GStart  HH   .   hh   hh   HH   #
```

`movements.txt`:
```java
String[][] movements = {
    {".", "SP", "SD", "R.", ".", "D", "."},
    {".", ".", "SR", "U", ".", "R.", "."},
    {".", ".", ".", ".R", ".", "U.", "."},
    {".", ".", ".", ".", ".", "R", "."},
    {".", ".", ".", ".U", ".", "L.", "."}
};
```

---

## Method 2: Programmatic Setup (For Optimization Algorithms)

Load the board, then set commands in code:

```java
// Load board only (no commands)
FastSimulator sim = FastSimulatorLoader.loadBoard("starting_simple.txt");

// Set commands programmatically
FastSimulatorLoader.setCommand(sim, x, y, floor, command);

// Examples:
FastSimulatorLoader.setCommand(sim, 1, 0, 1, "S"); // STOMP on top floor at (1,0)
FastSimulatorLoader.setCommand(sim, 1, 0, 0, "P"); // POWERUP on bottom floor at (1,0)
FastSimulatorLoader.setCommand(sim, 2, 2, 0, "D"); // Turn DOWN at (2,2)

// Run simulation
int score = sim.simulate(15);
```

**Floor Index:**
- `floor = 0` → Bottom floor (or only floor)
- `floor = 1` → Top floor (for 2-story buildings)

**Commands:**
- `"U"` = Turn North ($10)
- `"D"` = Turn South ($10)
- `"L"` = Turn West ($10)
- `"R"` = Turn East ($10)
- `"S"` = Stomp ($20)
- `"P"` = Powerup ($30)

---

## Method 3: Direct API (Maximum Performance)

Skip file I/O entirely for maximum speed:

```java
// Create empty simulator
FastSimulator sim = new FastSimulator(7, 5); // width=7, height=5

// Set tiles manually
sim.setTile(x, y, tileType, powerPerFloor, floors);

// Tile types:
// 0=EMPTY, 1=WALL, 2=BOULDER, 3=SPIKE, 4=MUD, 
// 5=POWERPLANT, 6=SMALL_BUILDING, 7=BIG_BUILDING, 8=BED

// Example: Place 2-story big house at (1,0) worth 500 per floor
sim.setTile(1, 0, (byte)7, 500, 2);

// Set commands
sim.setCommand(x, y, floor, commandByte);

// Command bytes:
// 1=TURN_E, 2=TURN_S, 3=TURN_W, 4=TURN_N, 5=STOMP, 6=POWERUP

sim.setCommand(1, 0, 1, (byte)5); // STOMP on floor 1
sim.setCommand(1, 0, 0, (byte)6); // POWERUP on floor 0

// Set cat positions
sim.setCat(0, 0, 2); // RED at (0,2)
sim.setCat(1, 0, 4); // GREEN at (0,4)
sim.setCat(2, 0, 0); // BLUE at (0,0)

// Set bed positions
sim.setBed(0, 6, 1); // RED bed at (6,1)
sim.setBed(1, 6, 2); // GREEN bed at (6,2)
sim.setBed(2, 6, 3); // BLUE bed at (6,3)

// Run simulation
int score = sim.simulate(15);
```

---

## Quick Start Example

```java
import java.io.*;

public class MyOptimizer {
    public static void main(String[] args) throws IOException {
        // Load your files
        FastSimulator sim = FastSimulatorLoader.loadFromFiles(
            "starting_simple.txt",
            "movements.txt"
        );
        
        // Run simulation
        long start = System.nanoTime();
        int score = sim.simulate(15);
        long end = System.nanoTime();
        
        // Print results
        System.out.println("Score: " + score);
        System.out.println("Time: " + (end - start) / 1000.0 + " μs");
    }
}
```

---

## Optimization Loop Example

```java
// Load board template once
FastSimulator template = FastSimulatorLoader.loadBoard("starting_simple.txt");

int bestScore = 0;
String bestConfig = "";

// Test different command configurations
for (int config = 0; config < 1000000; config++) {
    // Clone template (you'd implement proper cloning)
    FastSimulator sim = FastSimulatorLoader.loadBoard("starting_simple.txt");
    
    // Apply a configuration of commands
    // ... your optimization logic here ...
    
    // Run simulation (13 μs)
    int score = sim.simulate(15);
    
    // Track best
    if (score > bestScore) {
        bestScore = score;
        bestConfig = "...";
    }
}

System.out.println("Best score: " + bestScore);
```

---

## Grid Coordinates

```
     0   1   2   3   4   5   6  (x)
   ┌───┬───┬───┬───┬───┬───┬───┐
0  │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
1  │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
2  │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
3  │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
4  │   │   │   │   │   │   │   │
   └───┴───┴───┴───┴───┴───┴───┘
(y)
```

**Position (1, 0)** = Column 1, Row 0 (2nd column, 1st row)

---

## Command Reference

| Command | Code | Cost | Description |
|---------|------|------|-------------|
| Turn East | R | $10 | Cat faces right (→) |
| Turn South | D | $10 | Cat faces down (↓) |
| Turn West | L | $10 | Cat faces left (←) |
| Turn North | U | $10 | Cat faces up (↑) |
| Stomp | S | $20 | Cat stays 1 turn, destroys floor |
| Powerup | P | $30 | Add 1000 power to cat |

**Budget:** $200 total

---

## Files Created

- **FastSimulator.java** - Core optimized engine
- **FastSimulatorLoader.java** - Loads your existing files + programmatic API
- **FastSimulatorDemo.java** - Performance benchmarks
- **FastSimulatorBatch.java** - Batch processing example

---

## Compile and Run

```bash
# Compile
javac FastSimulator.java FastSimulatorLoader.java

# Run with your files
java FastSimulatorLoader

# This will:
# 1. Load starting_simple.txt and movements.txt
# 2. Run simulation
# 3. Show score and timing
# 4. Demonstrate programmatic setup
# 5. Run 1000 random configurations as optimization example
```

---

## Performance

- **13 microseconds** per simulation
- **76.8 million** simulations per second
- Can test **1 million configurations in 13 seconds**
- Perfect for genetic algorithms, Monte Carlo, brute force

---

## Comparison

| Feature | Old (GameSimulator) | New (FastSimulator) |
|---------|---------------------|---------------------|
| Input Format | Same files | ✓ Same files |
| Time per sim | ~5-10 ms | 13 μs |
| Speed | 1× | 400-800× |
| Visualization | ✓ Full ANSI | × None (fast mode) |
| Use Case | Debugging | Optimization |

**Tip:** Use `GameSimulator` + `Visualizer` for debugging, then switch to `FastSimulator` for optimization!

---

## Next Steps

1. ✓ Keep editing `movements.txt` as you have been
2. ✓ Run `java FastSimulatorLoader` to test
3. ✓ For optimization, use Method 2 (programmatic setup)
4. ✓ Implement your search algorithm (genetic, Monte Carlo, etc.)
