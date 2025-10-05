# Cat Kaiju Game Simulator

A Python-based simulator for the Cat Kaiju game, where three cats (Red, Green, Blue) navigate a city grid, destroying buildings and competing for the highest total power score.

## Installation

Requires Python 3.7 or higher. No external dependencies needed.

## Quick Start

1. Create a `starting.txt` file with your game layout (see format below)
2. Run the simulator:

```bash
python3 simulator.py
```

Or specify a custom layout file:

```bash
python3 simulator.py custom_layout.txt
```

## Game Layout Format

The starting state is a 2D array in Python list format. Here's the example layout:

```python
[
    ["C_B_S", "BU", "B",  "E",  "B",  "BS",    "OUT" ],
    ["OUT",   "BU", "E",  "R",  "B",  "C_R_E", "UI_R"],
    ["C_R_S", "B",  "BU", "R",  "S1", "BU",    "UI_G"],
    ["OUT",   "BU", "BS", "E",  "S2", "E",     "UI_B"],
    ["C_G_S", "B",  "B",  "E",  "B",  "B",     "OUT" ]
]
```

### Tile Codes

| Code | Description | Effect |
|------|-------------|--------|
| `C_R_S`, `C_G_S`, `C_B_S` | Cat Starting Position | Red/Green/Blue cat starts here |
| `C_R_E`, `C_G_E`, `C_B_E` | Cat Bed (Destination) | Arrival bonuses: 1st=+2000, 2nd=×3, 3rd=×5 |
| `B` | Low-Value Building | +250 power when destroyed |
| `BU` | High-Value Building | +500 power when destroyed |
| `BS` | Power Plant | ×2 power when destroyed |
| `E` | Empty Tile | Passable, no effects |
| `R` | Boulder | Impassable, cats rebound (reverse direction) |
| `S1` | Spike Trap | Reduces cat power by 50% |
| `S2`, `M` | Mud | Cat skips its next move |
| `M` | Mud | Cat gets stuck for 1 turn |
| `OUT` | Out of Bounds | Impassable wall/edge |
| `UI_R`, `UI_G`, `UI_B` | UI Placeholder | Non-playable, impassable |

## Game Rules Summary

### Cats
- **Red Cat**: 1,000 initial power, hierarchy 1 (wins ties)
- **Green Cat**: 2,000 initial power, hierarchy 2
- **Blue Cat**: 3,000 initial power, hierarchy 3

### Turn Sequence (15 turns total)
1. **Planning**: Cats calculate target position based on current direction
2. **Movement**: Cats move (lowest power moves first for contested tiles)
   - Apply obstacle effects (mud, spikes)
   - Apply scoring effects (buildings)
   - Apply direction changes (from commands)
   - Check for bed arrival
3. **Fights**: If multiple cats on same tile:
   - Winner = highest power (ties broken by hierarchy)
   - Losers are DEFEATED and removed from play
4. Repeat for next turn

### Scoring
**Final Score** = Sum of all three cats' power (including defeated cats)

## Placing Commands

Commands can be placed on building tiles to redirect cats. Use the `place_command()` method:

```python
simulator = GameSimulator(layout)

# Place a command to turn a cat DOWN at position (1, 0)
simulator.place_command(1, 0, "DOWN")

# Available commands: "UP", "DOWN", "LEFT", "RIGHT"
# Each command costs $25 from the $200 budget by default
# Remaining budget can be checked with simulator.get_budget_remaining()
```

**Important**: Commands can ONLY be placed on:
- Low-Value Buildings (`B`)
- High-Value Buildings (`BU`)
- Power Plants (`BS`)

Commands trigger when a cat enters the tile.

## Example Output

```
============================================================
TURN 1
============================================================
Cat(BLUE, Power=3500, Pos=(1, 0), Status=ACTIVE)
Cat(RED, Power=1250, Pos=(1, 2), Status=ACTIVE)
Cat(GREEN, Power=2250, Pos=(1, 4), Status=ACTIVE)

Grid:
. b B . B P #
# B . R B R #
. r B R S B #
# B P . S . #
. g B . B B #
```

Grid symbols:
- `b`, `r`, `g` = Blue, Red, Green cat (lowercase)
- `B` = Undestroyed building
- `P` = Power plant
- `R` = Boulder
- `S` = Spike trap
- `#` = Out of bounds
- `.` = Empty/destroyed tile

## Custom Layouts

Create your own layouts by:

1. Designing a 2D grid using the tile codes above
2. Place exactly 3 cat starting positions (`C_R_S`, `C_G_S`, `C_B_S`)
3. Place 3 matching cat beds (`C_R_E`, `C_G_E`, `C_B_E`)
4. Add buildings, obstacles, and empty spaces
5. Save as a Python list in a `.txt` file

## Game Strategy Tips

- Cats start moving RIGHT by default
- Buildings are destroyed after giving power (one-time bonus)
- Power Plants double your current power (use strategically!)
- Spike Traps cut power in half (avoid if possible)
- Plan routes to maximize building destruction and reach beds quickly
- Use commands ($25 each, $200 budget) to optimize paths
- Remember arrival bonuses: 1st cat gets +2000, 2nd gets ×3, 3rd gets ×5

## Technical Details

- **Turn Limit**: 15 turns
- **Starting Budget**: $200
- **Command Cost**: $25 each
- **Grid Dimensions**: Configurable (loaded from layout file)

## Files

- `simulator.py` - Main simulator code
- `starting.txt` - Default game layout
- `README.md` - This file
