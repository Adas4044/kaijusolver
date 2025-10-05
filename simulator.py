#!/usr/bin/env python3
"""
Cat Kaiju Game Simulator
Simulates a 15-turn game based on the provided technical specification.
"""

from enum import Enum
from typing import List, Tuple, Optional, Dict, TypedDict
from dataclasses import dataclass


TURN_LIMIT = 15
STARTING_BUDGET = 200

class CommandSpec(TypedDict):
    direction: Tuple[int, int]
    cost: int

DEFAULT_COMMAND_CATALOG: Dict[str, CommandSpec] = {
    "UP": {"direction": (0, -1), "cost": 25},
    "DOWN": {"direction": (0, 1), "cost": 25},
    "LEFT": {"direction": (-1, 0), "cost": 25},
    "RIGHT": {"direction": (1, 0), "cost": 25},
}


class CatID(Enum):
    RED = "RED"
    GREEN = "GREEN"
    BLUE = "BLUE"


class CatStatus(Enum):
    ACTIVE = "ACTIVE"
    STUCK_MUD = "STUCK_MUD"
    FINISHED = "FINISHED"
    DEFEATED = "DEFEATED"


class TileType(Enum):
    EMPTY = "EMPTY"
    LOW_VALUE_BUILDING = "LOW_VALUE_BUILDING"
    HIGH_VALUE_BUILDING = "HIGH_VALUE_BUILDING"
    POWER_PLANT = "POWER_PLANT"
    MUD = "MUD"
    SPIKE_TRAP = "SPIKE_TRAP"
    BOULDER = "BOULDER"
    CAT_BED = "CAT_BED"
    OUT_OF_BOUNDS = "OUT_OF_BOUNDS"


@dataclass
class Command:
    """Represents a directional command placed on a building tile"""
    name: str
    direction: Tuple[int, int]  # (dx, dy)
    cost: int


class Cat:
    """Represents a cat with all game attributes"""

    CAT_CONFIGS = {
        CatID.RED: {"initial_power": 1000, "hierarchy": 1},
        CatID.GREEN: {"initial_power": 2000, "hierarchy": 2},
        CatID.BLUE: {"initial_power": 3000, "hierarchy": 3},
    }

    def __init__(self, cat_id: CatID, position: Tuple[int, int], direction: Tuple[int, int]):
        self.cat_id = cat_id
        config = self.CAT_CONFIGS[cat_id]
        self.hierarchy = config["hierarchy"]
        self.initial_power = config["initial_power"]
        self.current_power = config["initial_power"]
        self.position = position
        self.direction = direction
        self.status = CatStatus.ACTIVE

    def __repr__(self):
        return f"Cat({self.cat_id.value}, Power={self.current_power}, Pos={self.position}, Status={self.status.value})"


class Tile:
    """Base tile class"""

    def __init__(self, tile_type: TileType):
        self.tile_type = tile_type
        self.attached_command: Optional[Command] = None

    def is_passable(self) -> bool:
        return self.tile_type not in [TileType.BOULDER, TileType.OUT_OF_BOUNDS]

    def apply_effects(self, cat: Cat) -> None:
        """Apply tile effects to a cat. Override in subclasses."""
        pass


class BuildingTile(Tile):
    """Building floor tile that grants power when destroyed"""

    def __init__(self, tile_type: TileType, power_value: int, floors: int = 1):
        super().__init__(tile_type)
        self.power_value = power_value
        self.floors = floors
        self.remaining_floors = floors
        self.is_destroyed = False
        # Track if this is high-value (500) or normal (250)
        self.is_high_value = (power_value == 500)

    def apply_effects(self, cat: Cat) -> None:
        if not self.is_destroyed and self.remaining_floors > 0:
            cat.current_power += self.power_value
            self.remaining_floors -= 1

            # Mark as destroyed when all floors are gone
            if self.remaining_floors == 0:
                self.is_destroyed = True

        # Apply command direction change if present
        if self.attached_command:
            cat.direction = self.attached_command.direction


class PowerPlantTile(Tile):
    """Special building that doubles cat power"""

    def __init__(self):
        super().__init__(TileType.POWER_PLANT)
        self.is_destroyed = False

    def apply_effects(self, cat: Cat) -> None:
        if not self.is_destroyed:
            cat.current_power *= 2
            self.is_destroyed = True

        if self.attached_command:
            cat.direction = self.attached_command.direction


class MudTile(Tile):
    """Obstacle that gets cat stuck for one turn"""

    def __init__(self):
        super().__init__(TileType.MUD)

    def apply_effects(self, cat: Cat) -> None:
        cat.status = CatStatus.STUCK_MUD


class SpikeTrapTile(Tile):
    """Obstacle that reduces cat power by 50%"""

    def __init__(self):
        super().__init__(TileType.SPIKE_TRAP)

    def apply_effects(self, cat: Cat) -> None:
        cat.current_power = int(cat.current_power * 0.5)


class CatBedTile(Tile):
    """Destination bed for a specific cat"""

    def __init__(self, associated_cat_id: CatID):
        super().__init__(TileType.CAT_BED)
        self.associated_cat_id = associated_cat_id


class GameSimulator:
    """Main game simulator"""

    # Tile code to object mapping
    TILE_MAP = {
        "E": lambda: Tile(TileType.EMPTY),
        "B": lambda: BuildingTile(TileType.LOW_VALUE_BUILDING, 250, floors=1),
        "BU": lambda: BuildingTile(TileType.HIGH_VALUE_BUILDING, 500, floors=1),
        "BS": lambda: PowerPlantTile(),
        "R": lambda: Tile(TileType.BOULDER),
        "S1": lambda: SpikeTrapTile(),
        "S2": lambda: MudTile(),
        "M": lambda: MudTile(),
        "OUT": lambda: Tile(TileType.OUT_OF_BOUNDS),
        "UI_R": lambda: Tile(TileType.OUT_OF_BOUNDS),
        "UI_G": lambda: Tile(TileType.OUT_OF_BOUNDS),
        "UI_B": lambda: Tile(TileType.OUT_OF_BOUNDS),
    }

    def __init__(self, layout: List[List[str]], *, command_catalog: Optional[Dict[str, CommandSpec]] = None,
                 starting_budget: int = STARTING_BUDGET, turn_limit: int = TURN_LIMIT):
        self.layout = layout
        self.height = len(layout)
        self.width = len(layout[0]) if layout else 0
        self.grid: List[List[Tile]] = []
        self.cats: Dict[CatID, Cat] = {}
        self.cat_beds: Dict[CatID, Tuple[int, int]] = {}
        self.global_bed_arrival_counter = 0
        self.turn = 0
        self.starting_budget = starting_budget
        self.turn_limit = turn_limit
        self.total_command_cost = 0
        self.command_catalog = self._normalize_command_catalog(command_catalog or DEFAULT_COMMAND_CATALOG)

        self._parse_layout()

    @staticmethod
    def _normalize_command_catalog(catalog: Dict[str, CommandSpec]) -> Dict[str, CommandSpec]:
        """Normalize command catalog keys and validate structure."""
        normalized: Dict[str, CommandSpec] = {}

        for name, data in catalog.items():
            if "direction" not in data or "cost" not in data:
                raise ValueError(f"Invalid command definition for '{name}': missing direction or cost")

            direction = data["direction"]
            cost = data["cost"]

            if not isinstance(direction, tuple) or len(direction) != 2:
                raise ValueError(f"Command '{name}' direction must be a tuple of (dx, dy)")

            if not isinstance(cost, int) or cost < 0:
                raise ValueError(f"Command '{name}' cost must be a non-negative integer")

            normalized[name.upper()] = {"direction": direction, "cost": cost}

        return normalized

    def _parse_layout(self):
        """Parse the 2D layout array into game objects"""
        self.grid = [[None for _ in range(self.width)] for _ in range(self.height)]

        # Map single letter codes to cat IDs
        cat_letter_map = {"R": CatID.RED, "G": CatID.GREEN, "B": CatID.BLUE}

        for y, row in enumerate(self.layout):
            for x, code in enumerate(row):
                # Handle cat starting positions (check length to avoid conflicts)
                if len(code) > 2 and code.startswith("C_") and code.endswith("_S"):
                    parts = code.split("_")
                    if len(parts) == 3:
                        cat_letter = parts[1]
                        if cat_letter in cat_letter_map:
                            cat_id = cat_letter_map[cat_letter]
                            # Cats start moving right by default
                            self.cats[cat_id] = Cat(cat_id, (x, y), (1, 0))
                            self.grid[y][x] = Tile(TileType.EMPTY)
                            continue

                # Handle cat beds (both C_X_E and UI_X formats)
                if len(code) > 2 and code.startswith("C_") and code.endswith("_E"):
                    parts = code.split("_")
                    if len(parts) == 3:
                        cat_letter = parts[1]
                        if cat_letter in cat_letter_map:
                            cat_id = cat_letter_map[cat_letter]
                            self.cat_beds[cat_id] = (x, y)
                            self.grid[y][x] = CatBedTile(cat_id)
                            continue

                # Handle UI_X format for cat beds
                if code in ["UI_R", "UI_G", "UI_B"]:
                    cat_letter = code.split("_")[1]
                    if cat_letter in cat_letter_map:
                        cat_id = cat_letter_map[cat_letter]
                        self.cat_beds[cat_id] = (x, y)
                        self.grid[y][x] = CatBedTile(cat_id)
                        continue

                # Handle regular tiles
                if code in self.TILE_MAP:
                    self.grid[y][x] = self.TILE_MAP[code]()
                else:
                    # Unknown code, treat as empty
                    self.grid[y][x] = Tile(TileType.EMPTY)

    def place_command(self, x: int, y: int, command_name: str) -> bool:
        """Place a command (by name) on a valid building tile within the remaining budget."""
        if not self._is_within_bounds(x, y):
            return False

        tile = self.grid[y][x]

        if not self._is_valid_command_tile(tile):
            return False

        command_key = command_name.upper()
        command_spec = self.command_catalog.get(command_key)

        if not command_spec:
            return False

        existing_cost = tile.attached_command.cost if tile.attached_command else 0
        new_total_cost = self.total_command_cost - existing_cost + command_spec["cost"]

        if new_total_cost > self.starting_budget:
            return False

        tile.attached_command = Command(
            name=command_key,
            direction=command_spec["direction"],
            cost=command_spec["cost"],
        )
        self.total_command_cost = new_total_cost
        return True

    def get_budget_remaining(self) -> int:
        """Return the remaining command budget."""
        return self.starting_budget - self.total_command_cost

    def _is_within_bounds(self, x: int, y: int) -> bool:
        return 0 <= x < self.width and 0 <= y < self.height

    @staticmethod
    def _is_valid_command_tile(tile: Tile) -> bool:
        if isinstance(tile, BuildingTile):
            return not tile.is_destroyed
        if isinstance(tile, PowerPlantTile):
            return not tile.is_destroyed
        return False

    def get_tile(self, x: int, y: int) -> Optional[Tile]:
        """Get tile at position, or None if out of bounds"""
        if 0 <= y < self.height and 0 <= x < self.width:
            return self.grid[y][x]
        return None

    def simulate_turn(self):
        """Execute one turn of the simulation"""
        self.turn += 1

        # Phase 1: Planning & Target Calculation
        movements = []
        for cat in self.cats.values():
            if cat.status == CatStatus.ACTIVE:
                target_x = cat.position[0] + cat.direction[0]
                target_y = cat.position[1] + cat.direction[1]
                movements.append((cat, (target_x, target_y)))
            elif cat.status == CatStatus.STUCK_MUD:
                # Cat is stuck, skip movement but clear stuck status
                cat.status = CatStatus.ACTIVE

        # Phase 2: Movement & Tile Interaction Resolution
        # Sort by current power (lowest first for arrival order)
        movements.sort(key=lambda m: m[0].current_power)

        tile_occupants: Dict[Tuple[int, int], List[Cat]] = {}

        for cat, target_pos in movements:
            target_tile = self.get_tile(target_pos[0], target_pos[1])

            # Handle impassable tiles (Boulder, OUT)
            if target_tile is None or not target_tile.is_passable():
                # Rebound: reverse direction
                cat.direction = (-cat.direction[0], -cat.direction[1])
                # Stay at current position
                if cat.position not in tile_occupants:
                    tile_occupants[cat.position] = []
                tile_occupants[cat.position].append(cat)
                continue

            # Move cat to target position
            cat.position = target_pos

            # Apply tile effects in order:
            # 1) Obstacle Effects (Mud, Spike)
            # 2) Scoring/Bonus Effects (Buildings, Power Plants)
            # 3) Direction Change from commands
            target_tile.apply_effects(cat)

            # Check for cat bed arrival
            if isinstance(target_tile, CatBedTile):
                if target_tile.associated_cat_id == cat.cat_id:
                    cat.status = CatStatus.FINISHED
                    self.global_bed_arrival_counter += 1

                    # Apply arrival bonus
                    if self.global_bed_arrival_counter == 1:
                        cat.current_power += 2000
                    elif self.global_bed_arrival_counter == 2:
                        cat.current_power *= 3
                    elif self.global_bed_arrival_counter == 3:
                        cat.current_power *= 5

            # Track occupants for fight resolution
            if cat.position not in tile_occupants:
                tile_occupants[cat.position] = []
            tile_occupants[cat.position].append(cat)

        # Phase 3: Fight Resolution
        for position, cats_at_tile in tile_occupants.items():
            if len(cats_at_tile) > 1:
                # Find winner (highest power, ties broken by hierarchy)
                winner = max(cats_at_tile, key=lambda c: (c.current_power, -c.hierarchy))

                for cat in cats_at_tile:
                    if cat != winner:
                        cat.status = CatStatus.DEFEATED

    def run_simulation(self, verbose: bool = True, output_file: str = None):
        """Run the simulation up to the configured turn limit."""
        output_lines = []

        def output(line=""):
            if verbose:
                print(line)
            if output_file:
                output_lines.append(line)

        if verbose or output_file:
            self._print_state(initial=True, output_func=output)

        for _ in range(1, self.turn_limit + 1):
            self.simulate_turn()
            if verbose or output_file:
                self._print_state(output_func=output)

            if all(cat.status in (CatStatus.FINISHED, CatStatus.DEFEATED) for cat in self.cats.values()):
                break

        # Calculate final score
        total_score = sum(cat.current_power for cat in self.cats.values())

        if verbose or output_file:
            output("\n" + "="*60)
            output("SIMULATION COMPLETE")
            output("="*60)
            for cat in self.cats.values():
                output(str(cat))
            output(f"\nFinal Total Score: {total_score}")

        # Write to file if specified
        if output_file:
            with open(output_file, 'w') as f:
                f.write('\n'.join(output_lines))
            if verbose:
                print(f"\n>>> Simulation output written to {output_file}")

        return total_score

    def _print_state(self, initial: bool = False, output_func=None):
        """Print current game state"""
        if output_func is None:
            output_func = print

        if initial:
            output_func("="*60)
            output_func("INITIAL STATE")
            output_func("="*60)
        else:
            output_func("\n" + "="*60)
            output_func(f"TURN {self.turn}")
            output_func("="*60)

        for cat in self.cats.values():
            output_func(str(cat))

        # Print grid
        output_func("\nGrid:")
        grid_display = [["." for _ in range(self.width)] for _ in range(self.height)]

        # Mark tiles
        for y in range(self.height):
            for x in range(self.width):
                tile = self.grid[y][x]
                if isinstance(tile, BuildingTile) and not tile.is_destroyed:
                    # Show number of remaining floors and building type (H=high-value, N=normal)
                    prefix = "H" if tile.is_high_value else "N"
                    if tile.remaining_floors == 2:
                        grid_display[y][x] = f"{prefix}2"
                    elif tile.remaining_floors == 1:
                        grid_display[y][x] = f"{prefix}1"
                    else:
                        grid_display[y][x] = prefix
                elif isinstance(tile, PowerPlantTile) and not tile.is_destroyed:
                    grid_display[y][x] = "P"
                elif isinstance(tile, CatBedTile):
                    # Use UI_X format for cat beds
                    bed_codes = {CatID.RED: "UI_R", CatID.GREEN: "UI_G", CatID.BLUE: "UI_B"}
                    grid_display[y][x] = bed_codes[tile.associated_cat_id]
                elif tile.tile_type == TileType.BOULDER:
                    grid_display[y][x] = "R"
                elif tile.tile_type == TileType.SPIKE_TRAP:
                    grid_display[y][x] = "S"
                elif tile.tile_type == TileType.MUD:
                    grid_display[y][x] = "M"
                elif tile.tile_type == TileType.OUT_OF_BOUNDS:
                    grid_display[y][x] = "#"

        # Mark cats
        for cat in self.cats.values():
            if cat.status != CatStatus.DEFEATED:
                x, y = cat.position
                grid_display[y][x] = cat.cat_id.value[0].lower()

        for row in grid_display:
            output_func(" ".join(row))


def load_layout_from_file(filename: str) -> List[List[str]]:
    """Load layout from a file containing a 2D array"""
    with open(filename, 'r') as f:
        content = f.read()

    # Parse the Python list format
    layout = eval(content)
    return layout


def main():
    """Main entry point"""
    import sys

    if len(sys.argv) > 1:
        filename = sys.argv[1]
    else:
        filename = "starting.txt"

    # Output file is always simulation_output.txt
    output_file = "simulation_output.txt"

    try:
        layout = load_layout_from_file(filename)
        print(f"Loaded layout from {filename}")
        print(f"Grid size: {len(layout[0])}x{len(layout)}")
        print()

        simulator = GameSimulator(layout)

        # Example: Place some commands (optional)
        # simulator.place_command(1, 0, "RIGHT")
        # simulator.place_command(2, 0, "DOWN")

        simulator.run_simulation(verbose=True, output_file=output_file)

    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.")
        print("\nUsing example layout instead...")

        # Use example layout from spec
        example_layout = [
            ["C_B_S", "BU", "B",  "E",  "B",  "BS",    "OUT" ],
            ["OUT",   "BU", "E",  "R",  "B",  "C_R_E", "UI_R"],
            ["C_R_S", "B",  "BU", "R",  "S1", "BU",    "UI_G"],
            ["OUT",   "BU", "BS", "E",  "S2", "E",     "UI_B"],
            ["C_G_S", "B",  "B",  "E",  "B",  "B",     "OUT" ]
        ]

        simulator = GameSimulator(example_layout)
        simulator.run_simulation(verbose=True, output_file=output_file)


if __name__ == "__main__":
    main()
