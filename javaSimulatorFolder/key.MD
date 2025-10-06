## 2. Game Description (Kaiju Cats)

Kaiju Cats is a game-based assessment where cats (Red, Green, Blue) move across a grid filled with obstacles, buildings, and goals.

Each cat starts at its Start tile and must reach its matching Bed tile (destination).

Along the way, they may destroy buildings for power points, avoid traps, or get blocked by rocks and walls.

Cats move in a direction until they hit something (like in Ricochet Robots), then either rebound, stop, or continue depending on the tile.

The goal is to maximize cat power and reach their beds efficiently, with bonus multipliers based on finishing order.

It's part puzzle, part resource management: you must route cats smartly to avoid traps and exploit power multipliers.

## 3. Tile Key (New Compact Codes)

| Code | Meaning | Effect / Notes |
|------|---------|----------------|
| `BStart` | Blue Start | Blue cat starting position |
| `RStart` | Red Start | Red cat starting position |
| `GStart` | Green Start | Green cat starting position |
| `H` | Big House (1 floor) | +500 power |
| `HH` | Big House (2 floors) | +1000 power |
| `h` | Small House (1 floor) | +250 power |
| `hh` | Small House (2 floors) | +500 power |
| `P` | Power Plant | Doubles current power |
| `.` | Empty Tile | No effect |
| `X` | Boulder / Rock | Impassable, rebounds cats |
| `S` | Spike Trap | Halves cat power |
| `M` | Mud | Cat skips next move |
| `#` | Wall | Impassable, out of bounds |
| `RBed` | Red Bed (goal) | Destination for Red cat |
| `GBed` | Green Bed (goal) | Destination for Green cat |
| `BBed` | Blue Bed (goal) | Destination for Blue cat |
| `UI_R` | UI Placeholder (Red) | Not part of gameplay |
| `UI_G` | UI Placeholder (Green) | Not part of gameplay |
| `UI_B` | UI Placeholder (Blue) | Not part of gameplay |