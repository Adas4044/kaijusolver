# Grid-Based Movement System

The movement commands are now stored in a **visual 2D grid** that mirrors your board layout, making it easy to see where commands are placed!

## Quick Start

### 1. Generate Grid Template

```bash
javac GridMovementGenerator.java
java GridMovementGenerator board_simple.txt movements.txt
```

This creates a `movements.txt` file with a grid matching your board layout.

### 2. Edit the Grid

Open `movements.txt` and add command codes directly in the grid:

```
# Your movement grid (matches board layout):
.       D       .       .       .       P       .       
.       UR      .       .       .       .       .       
.       L       .       .       .       .       .       
```

### 3. Run Simulation

```bash
javac GridMovementDemo.java
java GridMovementDemo
```

## Command Codes

| Code | Direction | Cost | Description |
|------|-----------|------|-------------|
| `L` | Left (West) | $10 | Cat turns west |
| `R` | Right (East) | $10 | Cat turns east |
| `U` | Up (North) | $10 | Cat turns north |
| `D` | Down (South) | $10 | Cat turns south |
| `P` | Powerup | $30 | Double cat's power |
| `S` | Stomp | $20 | Loop and execute commands again |

## Grid Format

### Single-Floor Buildings (h, H, P)
Use **1 character**:
```
.   D   .   .   .   P   .
```
- Position [1,0]: Big house (H) with DOWN command
- Position [5,0]: Power plant (P) with POWERUP command

### Multi-Floor Buildings (hh, HH)
Use **2 characters**:
```
.   UR  .   .   .   .   .
```
- Position [1,1]: 2-floor house (hh)
- **First char (U)** = TOP floor (executes first)
- **Second char (R)** = BOTTOM floor (executes second)

### Empty Tiles
Use `.` for tiles without commands:
```
.   .   .   .   .   .   .
```

## Complete Example

**Board Layout:**
```
BStart  H   h   .   h   P   #
RStart  hh  H   X   S   H   GBed
GStart  h   h   .   h   h   #
```

**Movement Grid:**
```
.       D       .       .       .       P       .       
.       UR      .       .       .       .       .       
.       L       .       .       .       .       .       
```

**What Happens:**
- Blue cat (row 0) hits house at [1,0] → turns DOWN
- Blue cat hits power plant at [5,0] → gets POWERUP
- Red cat (row 1) hits 2-floor house at [1,1]:
  - Floor 1 (top): Turns UP
  - Floor 0 (bottom): Turns RIGHT
- Green cat (row 2) hits house at [1,2] → turns LEFT

**Result:** Score: 8500, Budget: $70

## Multi-Floor Execution Order

Multi-floor buildings execute **TOP to BOTTOM**:

```
UR  =  U (top floor 1) → R (bottom floor 0)
DS  =  D (top floor 1) → S (bottom floor 0)
SS  =  S (top floor 1) → S (bottom floor 0)  # Double stomp!
```

## Advanced Strategies

### Looping with STOMP
```
.   SS  .   .   .   .   .
```
- Top floor: STOMP (loops back)
- Bottom floor: STOMP (loops again)
- Result: Cat keeps stomping this building!

### Direction + STOMP
```
.   US  .   .   .   .   .
```
- Top floor: Turn UP
- Bottom floor: STOMP
- Result: Cat turns up, then stomps again (turns up twice!)

### Power Boost Strategy
```
.   .   .   .   .   P   .
```
Place POWERUP (P) on power plants to maximize power gains.

## Grid Dimensions

⚠️ **Important:** Your movement grid must have the **same dimensions** as your board!

- Board: 7 columns × 5 rows → Movement grid: 7 × 5
- Each cell must align with the corresponding board tile

## Only Commandable Tiles

Commands can only be placed on:
- **h** - Small house (1 floor)
- **hh** - Small house (2 floors) ← Use 2 characters
- **H** - Big house (1 floor)
- **HH** - Big house (2 floors) ← Use 2 characters
- **P** - Power plant

Placing commands on other tiles (X, S, M, #, ., cat starts, beds) will be ignored with a warning.

## Whitespace

The grid uses whitespace for alignment, but the parser is flexible:
```
.   D   .   .   .   P   .     ← Spaces for readability
.  D  .  .  .  P  .           ← Also works
.D..P.                        ← Minimal spacing (harder to read)
```

**Recommendation:** Use consistent spacing for easy editing!

## Tips

1. **Visualize first** - Look at your board layout reference in the file
2. **Mark buildings** - Only add codes where buildings/plants exist
3. **Plan routes** - Think about cat paths and where they'll step
4. **Budget wisely** - You have $200 total
5. **Test iteratively** - Run simulation, check visualization, adjust grid
6. **Multi-floor power** - Use 2-character codes for more complex strategies

## Files

- **GridMovementGenerator.java** - Generates grid template
- **GridMovementLoader.java** - Parses grid and loads commands
- **GridMovementDemo.java** - Complete demo workflow
- **movements.txt** - Your editable movement grid
- **board_simple.txt** - Simple board layout format

## Troubleshooting

**Dimension mismatch error?**
- Make sure movement grid has same rows/columns as board
- Don't add or remove rows

**Command not loading?**
- Check that tile at that position is a building or power plant
- Verify code is valid: L/R/U/D/P/S
- Multi-floor buildings need exactly 2 characters

**Multi-floor not working?**
- Make sure building is actually 2-floor (hh or HH)
- Use 2 characters (e.g., UR, not just U)

**Cat not following commands?**
- Check visualization to see if cat actually steps on that tile
- Commands only execute when cat stomps the building

## Budget Planning

| Command Type | Cost | Tip |
|--------------|------|-----|
| Turn (L/R/U/D) | $10 | Cheapest, use liberally |
| STOMP (S) | $20 | Expensive, use strategically |
| POWERUP (P) | $30 | Most expensive, place on power plants |

**Budget:** $200 total = 20 turns OR 10 stomps OR 6-7 powerups + some turns

## Example Strategies

### Maze Navigation
Use turns to navigate around obstacles:
```
.   D   .   .   .   .   .
.   .   R   .   .   .   .
.   .   .   U   .   .   .
```

### Power Maximization
Combine powerups with power plants:
```
.   .   .   .   .   P   .    ← POWERUP on power plant = 2x power
```

### Loop Strategy
Use STOMP for repeated actions:
```
.   DS  .   .   .   .   .    ← Turn Down + STOMP = loop
```

Great for training cats to circle back!
