# Multi-Floor Building Feature

## Overview
Buildings can now have either 1 or 2 floors. Each time a cat destroys a floor, the building loses one floor until no floors remain.

## Building Codes

### Single-Floor Buildings
- **`B`** - Low-value building (250 points per floor) with 1 floor
- **`BU`** - High-value building (500 points per floor) with 1 floor

### Two-Floor Buildings  
- **`B2`** - Low-value building (250 points per floor) with 2 floors
- **`BU2`** - High-value building (500 points per floor) with 2 floors

## Mechanics

### Floor Destruction
1. When a cat enters a building tile, it destroys one floor
2. The cat gains power equal to the building's value (250 or 500)
3. The building's remaining floors decreases by 1
4. If a building has 0 remaining floors, it is marked as destroyed

### Value Consistency
- **Two-floor buildings**: Both floors have the same value
  - `B2`: Both floors worth 250 points each (total: 500 points)
  - `BU2`: Both floors worth 500 points each (total: 1000 points)

### Visual Display
Buildings are displayed with their type and remaining floors:
- **`N2`** - Normal-value building with 2 floors remaining
- **`N1`** - Normal-value building with 1 floor remaining
- **`H2`** - High-value building with 2 floors remaining
- **`H1`** - High-value building with 1 floor remaining
- **`N`** or **`H`** - Destroyed building (0 floors)

## Example

```python
layout = [
    ["C_R_S", "B2", "BU", "B", "BU2", "C_R_E"],
]
```

This creates:
- A 2-floor low-value building (250 per floor)
- A 1-floor high-value building (500)
- A 1-floor low-value building (250)
- A 2-floor high-value building (500 per floor)

If a cat moves through all buildings:
- **Turn 1**: Hits B2 (first floor) → +250 points, building now has 1 floor
- **Turn 2**: Hits BU → +500 points, building destroyed
- **Turn 3**: Hits B → +250 points, building destroyed
- **Turn 4**: Hits BU2 (first floor) → +500 points, building now has 1 floor
- **Turn 5**: Another cat hits B2 (second floor) → +250 points, building destroyed

Total potential points: 250 + 250 + 500 + 250 + 500 + 500 = 2,250 points

## Implementation Details

### BuildingTile Class
```python
class BuildingTile(Tile):
    def __init__(self, tile_type: TileType, power_value: int, floors: int = 1):
        super().__init__(tile_type)
        self.power_value = power_value  # Points per floor
        self.floors = floors            # Total floors (1 or 2)
        self.remaining_floors = floors  # Current remaining floors
        self.is_destroyed = False       # True when remaining_floors == 0
        self.is_high_value = (power_value == 500)
```

### Tile Effects
```python
def apply_effects(self, cat: Cat) -> None:
    if not self.is_destroyed and self.remaining_floors > 0:
        cat.current_power += self.power_value  # Add points per floor
        self.remaining_floors -= 1              # Destroy one floor
        
        if self.remaining_floors == 0:
            self.is_destroyed = True            # Mark as destroyed
```

## Strategy Implications

1. **Two-floor buildings offer more total points** - Worth destroying twice
2. **High-value two-floor buildings (BU2)** are the most valuable targets (1000 total points)
3. **Path planning** becomes more complex - you might want multiple cats to hit the same building
4. **Command placement** can be done on buildings until they're fully destroyed
