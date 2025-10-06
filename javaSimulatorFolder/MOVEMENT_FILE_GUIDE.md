# Movement File System

This system allows you to manage game commands through a separate `movements.txt` file that can be generated from your board layout and then manually edited.

## Quick Start

### 1. Generate Movement Template

Generate a `movements.txt` template from your board layout:

```bash
javac MovementFileGenerator.java
java MovementFileGenerator board_simple.txt movements.txt
```

This scans your board and creates a template showing all buildings (h, hh, H, HH) and power plants (P) where commands can be placed.

### 2. Edit movements.txt

Open `movements.txt` and add your commands. Format:

```
x,y,COMMAND[,FLOOR]
```

**Examples:**
```
# Single floor building - change direction
1,0,TURN_S

# Power plant - double power
5,0,POWERUP

# Multi-floor building - execute top floor first
1,1,TURN_E,1    # Floor 1 (top) - turn east
1,1,TURN_N,0    # Floor 0 (bottom) - turn north

# STOMP command - loop and execute again
2,3,STOMP
```

**Available Commands:**
- `TURN_N`, `TURN_S`, `TURN_E`, `TURN_W` - Change cat direction ($10 each)
- `STOMP` - Loop back and execute commands again ($20)
- `POWERUP` - Double cat's power ($30)

**Budget:** $200 total

### 3. Run Simulation

Run your simulation with the commands from movements.txt:

```bash
javac MovementFileDemo.java
java MovementFileDemo
```

The program will:
1. Load commands from `movements.txt`
2. Run the simulation
3. Generate visualization in `movement_demo_output.txt`
4. Display final results

## Files

### MovementFileGenerator.java
Generates `movements.txt` template from a board layout file. Lists all commandable locations (buildings and power plants) organized by type.

**Usage:**
```bash
java MovementFileGenerator [input_file] [output_file]
# Defaults: starting.txt -> movements.txt
```

### MovementFileLoader.java
Loads commands from `movements.txt` and applies them to a GameSimulator. Validates commands and reports budget usage.

### MovementFileDemo.java
Complete demo showing the workflow:
- Generates template if movements.txt doesn't exist
- Loads and applies commands
- Runs simulation with visualization
- Displays results

### movements.txt
Your command file! Edit this to control the game. Comments (lines starting with #) are ignored.

**Format:**
```
# Comment lines start with #
x,y,COMMAND          # Single floor
x,y,COMMAND,FLOOR    # Multi-floor (0=bottom, 1=top)
```

## Board Layout File Format

The generator expects a simple whitespace-separated format:

```
BStart  H   h   .   h   P   #
#       H   .   X   h   H   RBed
RStart  hh  H   X   S   H   GBed
#       H   P   .   M   .   BBed
GStart  h   h   .   h   h   #
```

**Tile Codes:**
- **h** - Small house (1 floor), +250 power
- **hh** - Small house (2 floors), +250 power
- **H** - Big house (1 floor), +500 power
- **HH** - Big house (2 floors), +500 power
- **P** - Power plant, ×2 power multiplier
- **X** - Boulder (blocks movement)
- **S** - Spike trap (defeats cats)
- **M** - Mud (skip next turn)
- **#** - Wall (blocks movement)
- **.** - Empty tile

## Multi-Floor Buildings

Multi-floor buildings (hh, HH) can store multiple commands. When a cat stomps a multi-floor building:

1. **Top floor executes first** (higher floor number)
2. Then each lower floor in sequence
3. Commands can include STOMP to loop back

**Example:**
```
# Building at (1,1) with 2 floors
1,1,TURN_E,1    # Floor 1 executes first: turn east
1,1,STOMP,0     # Floor 0 executes second: STOMP loops back to floor 1
```

Result: Cat turns east, then STOMP makes it turn east again!

## STOMP Command

The STOMP command makes the cat continue executing commands from the current building:

- Costs $20 (most expensive command)
- Loops back to top floor of the building
- Useful for repeated direction changes
- Can create complex movement patterns

## Workflow Example

1. **Create board layout** - `board_simple.txt`
2. **Generate template** - `java MovementFileGenerator board_simple.txt movements.txt`
3. **Edit movements.txt** - Add your strategy
4. **Test** - `java MovementFileDemo`
5. **Iterate** - Edit movements.txt and re-run
6. **Optimize** - Try different command placements to maximize score

## Tips

- Only buildings (h, hh, H, HH) and power plants (P) can have commands
- Budget is $200 - plan carefully!
- Multi-floor buildings offer more strategy options
- Power plants are great for POWERUP commands
- Use TURN commands to route cats around obstacles
- Check visualization output to see if cats are following your plan
- Remember: Top floor executes first in multi-floor buildings!

## Troubleshooting

**Command not loading?**
- Check x,y coordinates are correct (0-indexed)
- Verify the tile at that position is a building or power plant
- Check budget hasn't been exceeded
- Make sure command syntax is correct: `x,y,COMMAND` or `x,y,COMMAND,FLOOR`

**Cat not turning?**
- Verify cat actually steps on that tile
- Check turn visualization to see when cat reaches the building
- Make sure command was successfully loaded (check output)

**Multi-floor not working?**
- Remember to specify floor: `x,y,COMMAND,0` or `x,y,COMMAND,1`
- Top floor (1) executes before bottom floor (0)
- Building must actually have 2 floors (hh or HH)
