# Kaiju Cats Optimizer - Project Context & Challenges

## Project Overview

I'm working on an optimizer for a puzzle game called "Kaiju Cats". The goal is to maximize the total power score of all cats while staying under a $200 budget.

### Game Rules

**Map Structure:**
- 7×5 grid with various tile types
- Buildings: Single floor (.) or multi-floor (hh, HH)
- Power Plants (P): Grant power when cats walk on them
- Special tiles: Mines (M), Stomps (S), Powerups (X), Beds (RBed, GBed, BBed)
- Cats start at designated positions (RStart, GStart, BStart)

**Cat Behavior:**
- Cats walk automatically each turn in their facing direction
- Default starting direction: East (→)
- Cats walk through buildings and onto power plants
- When a cat hits a TURN command (R/L/U/D), it changes direction
- Cats can be defeated by mines or stomps
- Cats that reach their designated beds enter FINISHED status (this is critical for high scores!)

**Commands & Costs:**
- Turn commands (R/L/U/D): $10 - Change cat direction
- Stomp (S): $20 - Defeats a cat at that location
- Powerup (P): $30 - Multiplies cat power
- Total budget: $200

**Command Placement:**
- Commands must be placed on buildings (hh, HH) or power plants (P)
- Multi-floor buildings: Can have 2 commands (one per floor)
- Format: "hR" = lower-case building with Turn East on first floor
- Format: "RH" = upper-case building with Turn East on second floor
- Format: "PR" = power plant with Turn East

**Scoring:**
- Base power from walking on power plants
- Multipliers from powerups
- **CRITICAL**: Cats that reach their beds get massive score bonuses (estimated 40k-50k each)
- Optimal scores: 140k-200k (with all cats in beds)
- Current best: ~50k (without cats in beds)

## Code Structure

### Core Simulator Classes (READ-ONLY)
These are part of the game engine and cannot be modified:

**GameSimulator.java**
- Main simulation engine
- `placeCommand(int x, int y, CommandType cmd, int floor)` - Places a command on the grid
- `runSimulation(boolean verbose)` - Runs the full simulation
- `cats` - HashMap of Cat objects by color
- Tracks full game state including cat positions, powers, and status

**Cat.java**
- Represents a cat entity
- `getCurrentPower()` - Returns current power value
- `getStatus()` - Returns CatStatus (ACTIVE, FINISHED, DEFEATED)
- `getX()`, `getY()` - Current position
- `getDirection()` - Current facing direction

**CatStatus.java** - Enum: ACTIVE, FINISHED, DEFEATED

**CommandType.java** - Enum: TURN_N, TURN_S, TURN_E, TURN_W, STOMP, POWERUP

### My Optimizer Implementations

#### 1. **MultithreadedOptimalSolver.java** (~650 lines)
**Purpose**: Baseline simulated annealing with 8 parallel threads

**Key Methods:**
- `WorkerThread.run()` - Each thread runs independent SA with its own temperature
- `generateNeighbor(Map<String, String> current)` - Probabilistic mutations:
  - 25% remove random command
  - 25% add random command
  - 25% modify existing command
  - 15% swap two commands
  - 10% multi-change (2-5 operations)
- `evaluateSolution(Map<String, String> solution)` - Tests solution via GameSimulator, returns score
- `DiversityMonitor.run()` - Daemon thread that checks every 5s if solutions are >70% similar, reheats weaker thread

**Parameters:**
- Initial temp: 100,000
- Cooling rate: 0.9999995 (very slow)
- Restart: Every 10K iterations without improvement
- Acceptance: Always accept better, `exp(Δ/T)` for worse

**Performance**: 46,312 score in 27 seconds, but **0 cats reached beds**

**Problems:**
- Random mutations can't discover structured routing paths
- Stuck in local maxima (45k-50k range)
- Even with aggressive temperature management, can't find cat→bed paths

---

#### 2. **HeuristicGuidedOptimizer.java** (~762 lines)
**Purpose**: SA with multi-objective heuristic heavily favoring cats in beds

**Key Methods:**
- `Solution` class (lines 43-60) - Tracks:
  - `score` - Raw game score
  - `catsInBed` - Count of cats that reached their beds
  - `exclusivePowerPlants` - Count of power plants hit by only one cat
  - `cost` - Total budget used
  - `commands` - Command map
  
- `evaluateSolution(Map<String, String> commands)` (lines 285-380) - Multi-objective scoring:
  ```
  heuristic = score 
            + (catsInBed × 200,000)           // Massive bonus for cats in bed
            + (exclusivePowerPlants × 10,000)  // Bonus for exclusive power plant access
            + (budgetUsed × 50)                // Small bonus for using budget
            - (totalDistance × 500)            // Penalty for cats far from beds
  ```
  
- Approximation strategies (no history tracking):
  - `catsInBed`: Compare final cat positions to bed positions
  - `exclusivePowerPlants`: Check proximity (distance ≤ 2) and count unique visitors
  - `totalDistance`: Manhattan distance from each cat to its bed

**Heuristic Weights (lines 22-27):**
```java
CATS_IN_BED_BONUS = 200,000      // Per cat
EXCLUSIVE_POWERPLANT_BONUS = 10,000
DISTANCE_WEIGHT = 500
BUDGET_USAGE_BONUS = 50
```

**Performance**: **49,750 score** in 29 seconds (BEST raw score), but **0/3 cats in bed!**

**Critical Problem**: Even with 200k bonus per cat, optimizer NEVER found solutions with cats in beds. This proves random search fundamentally cannot solve structured routing.

---

#### 3. **SimpleScoreOptimizer.java** (~450 lines)
**Purpose**: Pure SA baseline without heuristics (for comparison)

**Key Methods:**
- `evaluateSolution()` - Returns ONLY raw game score (no heuristics)
- Same mutation operators as MultithreadedOptimalSolver
- Single-threaded for simplicity

**Performance**: 45,500 score in 28 seconds

**Insight**: Heuristics only provide ~7% improvement (45.5k → 49.8k). The fundamental barrier is routing, not optimization.

---

#### 4. **HybridPathOptimizer.java** (~440 lines) - PARTIALLY WORKING
**Purpose**: Use A* pathfinding to guarantee cats reach beds, then optimize

**Key Methods:**
- `main()` - Four phases:
  1. **Phase 1**: Map analysis - Find cat starts, beds, power plants
  2. **Phase 2**: A* pathfinding for each cat (start → bed)
  3. **Phase 3**: Convert paths to turn commands
  4. **Phase 4**: Test solution

- `findPathAStar(layout, catColor, start, goal, powerPlants)` (lines 140-220):
  - Standard A* with Manhattan distance heuristic
  - PriorityQueue for open set (sorted by f-score)
  - Returns CatPath with sequence of positions
  - **Works correctly** - finds valid spatial paths

- `getNeighbors(layout, pos)` (lines 262-290):
  - Returns walkable adjacent tiles
  - Filters out obstacles (#) and empty tiles

- `findCommandPlacement(layout, pos)` (lines 285-300):
  - Finds valid tile to place command near a position
  - Checks current position and adjacent tiles
  - Returns key format: "x,y,floor"

**Current Status**: 
- ✅ A* pathfinding works correctly
- ✅ Finds paths for all 3 cats
- ❌ **Command placement is broken**
- ❌ Cats don't actually reach beds (score: 10,687 vs target: 140k+)

**The Core Problem:**
The A* finds a *spatial path* (sequence of grid positions), but the game has *temporal mechanics*:
- Cats walk automatically each turn
- Turn commands change direction when cat walks ONTO that tile
- Need to predict: "Where will cat be on turn N?" and place command there
- Current code just places commands along the path without simulating actual movement

**Example of the issue:**
```
Cat starts at (0,2) facing East →
A* finds path: (0,2) → (1,2) → (2,2) → (3,2) → (4,2) → (5,2) → (6,2)
Code places: Turn East at (1,2)
Reality: Cat is already facing East! Command does nothing.
Cat walks off path because we didn't simulate when it needs to turn.
```

---

#### 5. **GeneticOptimizer.java** (~700 lines) - REJECTED
Population-based genetic algorithm. User rejected this approach.

## What We've Tried

### Temperature Management
- ✅ Restart mechanism (reset temp every 10K iterations without improvement)
- ✅ Diversity monitoring (reheat threads with >70% similar solutions)
- ❌ Temperature escalation (caused runaway to 195M, became random search)

### Heuristics
- ✅ Distance to beds (penalty for cats far from beds)
- ✅ Exclusive power plants (bonus for single-cat plant access)
- ✅ Cats in bed tracking (200k bonus per cat)
- ✅ Budget usage incentive
- ❌ **None of these helped cats reach beds** - optimizer found 0 cats in bed despite 200k bonus!

### Algorithms
- ✅ Single-threaded SA
- ✅ Multi-threaded SA (8 cores)
- ✅ Heuristic-guided SA
- ✅ Genetic algorithm (rejected)
- 🔄 A* pathfinding (broken command placement)

### Comparison Testing (20-second runs)
- HeuristicGuidedOptimizer: 49,750 (best)
- MultithreadedOptimalSolver: 46,312
- SimpleScoreOptimizer: 45,500
- **All converge to 45k-50k range regardless of approach**
- **All found 0 cats in bed**

## The Fundamental Challenge

### Why Simulated Annealing Fails

**The Problem**: Getting a cat from its start to its bed requires a *specific sequence* of 10-20 turn commands in correct spatial order (like solving a maze).

**Why SA Can't Find It:**
1. SA treats commands as independent variables
2. Random mutations: add/remove/modify single commands
3. Probability of discovering correct 10-20 command sequence: ~0%
4. Even with 200k bonus per cat, optimizer never found routing in 15 minutes

**Analogy**: Imagine trying to solve a Rubik's Cube by randomly twisting faces. The moves are interdependent - you need a structured sequence, not random perturbations.

### Why A* Is Promising But Broken

**The Idea**: Use constraint satisfaction - GUARANTEE cats reach beds first, then optimize.

**Current Implementation Problem**:
```
A* Phase: Find spatial path (✅ Works)
  (0,2) → (1,2) → (2,2) → (3,2) → ...

Translation Phase: Convert path to commands (❌ Broken)
  Need to simulate: "Cat starts at (0,2) facing →"
                    "Turn 1: Cat walks to (1,2)"
                    "Turn 2: Cat walks to (2,2)"
                    "Turn 3: Cat needs to go to (2,3), so place Turn South at (2,2)"
                    etc.
  
  Current code: Just places commands along path without simulating movement
  Result: Cat walks off path, never reaches bed
```

## What I Need Help With

### Primary Goal
**Get all 3 cats to their beds** to unlock the 140k-200k score range.

### Specific Questions

1. **Fix HybridPathOptimizer command placement**:
   - How do I simulate cat movement turn-by-turn?
   - Where exactly should I place turn commands so cat hits them at the right moment?
   - How do I account for cat's current direction vs required direction?

2. **Alternative approaches**:
   - Should I use a different pathfinding algorithm?
   - Is there a better way to translate spatial paths to temporal commands?
   - Should I simulate the game forward while placing commands?

3. **Command placement mechanics**:
   - When a cat is at position (x,y) and walks East, it moves to (x+1,y) THEN checks for commands
   - Or does it check for commands at current position, THEN move?
   - Do I need to place commands one tile BEFORE the cat needs to turn?

4. **Optimization strategy**:
   - Once cats reach beds reliably, how do I optimize power plant routing?
   - Should I add power plant waypoints to A* paths?
   - How do I balance: bed-reaching (required) vs power collection (optional but valuable)?

### Code I'd Like You to Focus On

**Most Important**: `HybridPathOptimizer.java` lines 120-160 (path to command conversion)

**Secondary**: Understanding the exact game mechanics in `GameSimulator.java` for cat movement and command triggering

### Test Case
File: `starting_simple.txt`
- 3 cats (RED, GREEN, BLUE)
- 3 beds at positions (6,1), (6,2), (6,3)
- 3 power plants
- Current best score: 49,750 (0 cats in bed)
- Target score: 140k-200k (all cats in bed)

### Success Criteria
A solution that:
1. Gets all 3 cats to their designated beds (STATUS: FINISHED)
2. Uses ≤$200 budget
3. Scores >100k (ideally 140k-200k)

## Additional Context

**Files in workspace:**
- `starting_simple.txt` - Test map
- `movements.txt` - Current command grid (loaded by simulator)
- `GameSimulator.java`, `Cat.java`, etc. - Game engine (read-only)
- `MultithreadedOptimalSolver.java` - Working but plateaus at 46k
- `HeuristicGuidedOptimizer.java` - Working but plateaus at 49k
- `HybridPathOptimizer.java` - Broken command placement
- `QuickTest.java` - Quick tester that loads movements.txt and reports score

**How to test:**
```bash
# Compile
javac HybridPathOptimizer.java

# Run optimizer
java HybridPathOptimizer starting_simple.txt

# Test the generated solution
cp movements_optimal.txt movements.txt
java QuickTest
```

**What to look for in output:**
- Cat Status: Should be FINISHED (not ACTIVE)
- Cat positions: Should match bed positions (6,1), (6,2), (6,3)
- Score: Should be >100k

---

**Thank you for any help! I've been stuck on this routing problem for days. Simulated annealing can't solve it, and my A* implementation is close but the command placement logic is wrong.**
