# How to Input a New Map

You have **3 ways** to use a new map:

---

## Option 1: Replace the Default Map (Simplest)

Just **overwrite** `starting_simple.txt` with your new map:

```bash
# Backup the old one first (optional)
cp starting_simple.txt starting_simple_backup.txt

# Edit starting_simple.txt with your new map
nano starting_simple.txt
# or
code starting_simple.txt
```

Then run as normal:
```bash
java QuickTest
# or
java GridMovementDemo
```

---

## Option 2: Create a Separate Map File (Recommended)

Create a new file like `my_map.txt`:

```bash
# Example map (5x3 grid)
BStart  HH   P    .    RBed
#       hh   S    M    GBed  
GStart  .    X    hh   BBed
```

Then use **FlexibleSimulator**:

```bash
# Quick test (no visualization)
java FlexibleSimulator my_map.txt movements.txt

# Full visualization
java FlexibleSimulator my_map.txt movements.txt --viz
```

---

## Option 3: Programmatically (For Solvers)

If you're building an optimization algorithm, create maps in code:

```java
String[][] layout = {
    {"BStart", "HH", "P", ".", "RBed"},
    {"#", "hh", "S", "M", "GBed"},
    {"GStart", ".", "X", "hh", "BBed"}
};

GameSimulator sim = new GameSimulator(layout, false);
// Add commands, run simulation...
```

---

## Map Format Reference

### Tile Codes:
- **Cats:** `RStart`, `GStart`, `BStart` (starting positions)
- **Beds:** `RBed`, `GBed`, `BBed` (goal positions)
- **Buildings:**
  - `h` = 1-floor small house (250 power)
  - `hh` = 2-floor small house (250 per floor)
  - `H` = 1-floor big house (500 power)
  - `HH` = 2-floor big house (500 per floor)
- **Power Plant:** `P` (1000 power + powerup command)
- **Obstacles:**
  - `#` = Wall (blocks movement)
  - `X` = Boulder (blocks movement)
  - `S` = Spike (halves power)
  - `M` = Mud (cat stuck 1 turn)
- **Empty:** `.` or space

### Format Rules:
1. **Whitespace-separated** tiles (spaces or tabs)
2. **Rectangular grid** (all rows same width)
3. **One tile per position**
4. Comments: Lines starting with `# ` are ignored

### Example Map:
```
# My Custom Map (7x5)
BStart  HH   hh   .   P   .   #
#       .    hh   S   M   hh  RBed
RStart  hh   X    hh  .   HH  GBed
#       .    P    HH  X   .   BBed
GStart  HH   .    hh  hh  HH  #
```

---

## Creating Movements for New Map

After creating a new map, generate a movements template:

```java
// In GridMovementGenerator.java or manually:
java GridMovementGenerator my_map.txt my_movements.txt
```

Or **manually create** `my_movements.txt`:

```java
String[][] movements = {
    {".", "SP", ".", ".", "."},
    {".", ".", "U", ".", "."},
    {".", ".", ".", "R", "."}
};
```

- Grid dimensions must **match your map**
- Use `.` for empty tiles
- Add commands only where buildings/power plants exist

---

## Full Workflow Example

### 1. Create new map:
```bash
cat > dungeon_map.txt << EOF
BStart  HH   X    P    RBed
#       hh   S    .    GBed
GStart  P    hh   HH   BBed
EOF
```

### 2. Create movements:
```bash
cat > dungeon_moves.txt << EOF
String[][] movements = {
    {".", "SP", ".", "P", "."},
    {".", "U", ".", ".", "."},
    {".", "P", "D", "SR", "."}
};
EOF
```

### 3. Test it:
```bash
# Quick test
java FlexibleSimulator dungeon_map.txt dungeon_moves.txt

# Full visualization
java FlexibleSimulator dungeon_map.txt dungeon_moves.txt --viz
```

---

## Commands Available

All three simulators now support custom maps:

| Command | Maps Supported | Usage |
|---------|----------------|-------|
| `java QuickTest` | `starting_simple.txt` only | Fast, hardcoded default |
| `java GridMovementDemo` | `starting_simple.txt` only | Full viz, hardcoded |
| `java FlexibleSimulator [map] [moves]` | **Any map!** | Flexible, fast or full viz |

---

## FlexibleSimulator Options

```bash
# Use defaults (starting_simple.txt + movements.txt)
java FlexibleSimulator

# Custom map, default movements
java FlexibleSimulator my_map.txt

# Custom map + custom movements (quick)
java FlexibleSimulator my_map.txt my_moves.txt

# Custom map + custom movements + full visualization
java FlexibleSimulator my_map.txt my_moves.txt --viz
```

---

## Tips

1. **Start small:** Test with 3×3 or 5×3 maps first
2. **Match dimensions:** movements.txt grid must match map dimensions
3. **One cat minimum:** Every map needs at least one cat + bed pair
4. **Budget:** Remember the $200 command budget
5. **Test fast, visualize when needed:**
   - Use FlexibleSimulator (no --viz) for quick iterations
   - Add --viz flag when you want to see turn-by-turn action

---

## Quick Reference

```bash
# Quick test default map
java QuickTest

# Test custom map (fast)
java FlexibleSimulator arena.txt commands.txt

# Test custom map (with visualization)
java FlexibleSimulator arena.txt commands.txt --viz

# Full visualization of default
java GridMovementDemo
```

---

## Example: Small Test Map

Create `test_3x3.txt`:
```
BStart  h   RBed
.       P   .
GStart  h   GBed
```

Create `test_3x3_moves.txt`:
```
String[][] movements = {
    {".", "D", "."},
    {".", "P", "."},
    {".", "U", "."}
};
```

Run:
```bash
java FlexibleSimulator test_3x3.txt test_3x3_moves.txt
```

Done! 🎮
