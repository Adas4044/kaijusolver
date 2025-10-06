import java.util.*;

/**
 * Tile.java - Contains all tile types, commands, and related classes
 */

// ============================================================================
// ENUMS
// ============================================================================

enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx() { return dx; }
    public int getDy() { return dy; }

    public Direction reverse() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST: return WEST;
            case WEST: return EAST;
            default: return this;
        }
    }
}

enum CommandType {
    TURN_N(10),
    TURN_S(10),
    TURN_E(10),
    TURN_W(10),
    STOMP(20),
    POWERUP(30);

    private final int cost;

    CommandType(int cost) {
        this.cost = cost;
    }

    public int getCost() { return cost; }

    public Direction getDirection() {
        switch (this) {
            case TURN_N: return Direction.NORTH;
            case TURN_S: return Direction.SOUTH;
            case TURN_E: return Direction.EAST;
            case TURN_W: return Direction.WEST;
            default: return null;
        }
    }
}

enum TileType {
    EMPTY,
    SMALL_BUILDING,
    BIG_BUILDING,
    POWER_PLANT,
    MUD,
    SPIKE_TRAP,
    BOULDER,
    CAT_BED,
    WALL
}

enum CatColor {
    RED,
    GREEN,
    BLUE
}

enum CatStatus {
    ACTIVE,
    STUCK_MUD,
    STOMPING,
    FINISHED,
    DEFEATED
}

// ============================================================================
// COMMAND CLASS
// ============================================================================

class Command {
    private final CommandType type;

    public Command(CommandType type) {
        this.type = type;
    }

    public CommandType getType() {
        return type;
    }

    public int getCost() {
        return type.getCost();
    }

    public Direction getDirection() {
        return type.getDirection();
    }

    @Override
    public String toString() {
        return type.name();
    }
}

// ============================================================================
// CAT CLASS
// ============================================================================

class Cat {
    private final CatColor color;
    private final int hierarchy;
    private final int initialPower;
    private int currentPower;
    private int x;
    private int y;
    private Direction direction;
    private CatStatus status;

    public Cat(CatColor color, int x, int y, Direction initialDirection) {
        this.color = color;
        this.x = x;
        this.y = y;
        this.direction = initialDirection;
        this.status = CatStatus.ACTIVE;

        // Set hierarchy and initial power based on color
        switch (color) {
            case RED:
                this.hierarchy = 1;
                this.initialPower = 0;
                break;
            case GREEN:
                this.hierarchy = 2;
                this.initialPower = 0;
                break;
            case BLUE:
                this.hierarchy = 3;
                this.initialPower = 0;
                break;
            default:
                this.hierarchy = 0;
                this.initialPower = 0;
        }
        this.currentPower = initialPower;
    }

    // Getters
    public CatColor getColor() { return color; }
    public int getHierarchy() { return hierarchy; }
    public int getInitialPower() { return initialPower; }
    public int getCurrentPower() { return currentPower; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Direction getDirection() { return direction; }
    public CatStatus getStatus() { return status; }

    // Setters
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void setStatus(CatStatus status) {
        this.status = status;
    }

    public void addPower(int amount) {
        this.currentPower += amount;
    }

    public void setPower(int power) {
        this.currentPower = power;
    }

    public void multiplyPower(int multiplier) {
        this.currentPower *= multiplier;
    }

    public void halvePower() {
        this.currentPower = (int)(this.currentPower * 0.5);
    }

    public void reverseDirection() {
        this.direction = direction.reverse();
    }

    @Override
    public String toString() {
        return String.format("Cat(%s, Power=%d, Pos=(%d,%d), Dir=%s, Status=%s)",
                color, currentPower, x, y, direction, status);
    }
}

// ============================================================================
// TILE CLASSES
// ============================================================================

abstract class Tile {
    protected final TileType type;

    public Tile(TileType type) {
        this.type = type;
    }

    public TileType getType() {
        return type;
    }

    public abstract boolean isPassable();
    public abstract boolean canHoldCommand();
    public abstract void applyEffects(Cat cat);
    public abstract String getDisplayChar();

    // Default implementations for command handling
    public boolean placeCommand(Command command, int floor) {
        return false; // Most tiles can't hold commands
    }

    public Command getAttachedCommand() {
        return null;
    }
}

// ============================================================================

class EmptyTile extends Tile {
    public EmptyTile() {
        super(TileType.EMPTY);
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        // No effects
    }

    @Override
    public String getDisplayChar() { return "."; }
}

// ============================================================================

class BoulderTile extends Tile {
    public BoulderTile() {
        super(TileType.BOULDER);
    }

    @Override
    public boolean isPassable() { return false; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        // Cats rebound before reaching here
    }

    @Override
    public String getDisplayChar() { return "X"; }
}

// ============================================================================

class WallTile extends Tile {
    public WallTile() {
        super(TileType.WALL);
    }

    @Override
    public boolean isPassable() { return false; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        // Cats rebound before reaching here
    }

    @Override
    public String getDisplayChar() { return "#"; }
}

// ============================================================================

class MudTile extends Tile {
    public MudTile() {
        super(TileType.MUD);
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        cat.setStatus(CatStatus.STUCK_MUD);
    }

    @Override
    public String getDisplayChar() { return "M"; }
}

// ============================================================================

class SpikeTrapTile extends Tile {
    public SpikeTrapTile() {
        super(TileType.SPIKE_TRAP);
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        cat.halvePower();
    }

    @Override
    public String getDisplayChar() { return "S"; }
}

// ============================================================================

class CatBedTile extends Tile {
    private final CatColor associatedCat;

    public CatBedTile(CatColor catColor) {
        super(TileType.CAT_BED);
        this.associatedCat = catColor;
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() { return false; }

    @Override
    public void applyEffects(Cat cat) {
        // Arrival bonus handled in game logic
    }

    public CatColor getAssociatedCat() {
        return associatedCat;
    }

    @Override
    public String getDisplayChar() {
        switch (associatedCat) {
            case RED: return "UI_R";
            case GREEN: return "UI_G";
            case BLUE: return "UI_B";
            default: return "BED";
        }
    }
}

// ============================================================================

class BuildingTile extends Tile {
    private final int powerPerFloor;
    private final int totalFloors;
    private int remainingFloors;
    private final List<Command> commandsPerFloor; // index 0 = floor 1 (bottom), etc.
    private final boolean isSmall;

    public BuildingTile(boolean isSmall, int floors) {
        super(isSmall ? TileType.SMALL_BUILDING : TileType.BIG_BUILDING);
        this.isSmall = isSmall;
        this.powerPerFloor = isSmall ? 250 : 500;
        this.totalFloors = floors;
        this.remainingFloors = floors;
        this.commandsPerFloor = new ArrayList<>(Collections.nCopies(floors, null));
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() {
        return remainingFloors > 0;
    }

    @Override
    public boolean placeCommand(Command command, int floor) {
        if (floor < 0 || floor >= totalFloors || floor >= remainingFloors) {
            return false;
        }
        commandsPerFloor.set(floor, command);
        return true;
    }

    @Override
    public Command getAttachedCommand() {
        if (remainingFloors > 0) {
            return commandsPerFloor.get(remainingFloors - 1);
        }
        return null;
    }

    @Override
    public void applyEffects(Cat cat) {
        // Only destroy floor if we have floors remaining
        if (remainingFloors > 0) {
            // Get top floor command
            Command cmd = commandsPerFloor.get(remainingFloors - 1);

            // Award power for destroying this floor
            cat.addPower(powerPerFloor);

            // Destroy the floor
            remainingFloors--;

            // Execute command if present
            if (cmd != null) {
                executeCommand(cmd, cat);
            }
        }
    }

    private void executeCommand(Command cmd, Cat cat) {
        switch (cmd.getType()) {
            case TURN_N:
            case TURN_S:
            case TURN_E:
            case TURN_W:
                cat.setDirection(cmd.getDirection());
                break;
            case STOMP:
                // Set cat to STOMPING status if there are more floors to destroy
                if (remainingFloors > 0) {
                    cat.setStatus(CatStatus.STOMPING);
                }
                break;
            case POWERUP:
                cat.addPower(1000);
                break;
        }
    }

    public int getRemainingFloors() {
        return remainingFloors;
    }

    public int getTotalFloors() {
        return totalFloors;
    }

    public boolean isDestroyed() {
        return remainingFloors == 0;
    }

    @Override
    public String getDisplayChar() {
        if (remainingFloors == 0) {
            return ".";
        }
        String prefix = isSmall ? "h" : "H";
        if (totalFloors > 1) {
            return prefix + remainingFloors;
        }
        return prefix;
    }
}

// ============================================================================

class PowerPlantTile extends Tile {
    private Command attachedCommand;
    private boolean destroyed;

    public PowerPlantTile() {
        super(TileType.POWER_PLANT);
        this.destroyed = false;
    }

    @Override
    public boolean isPassable() { return true; }

    @Override
    public boolean canHoldCommand() {
        return !destroyed;
    }

    @Override
    public boolean placeCommand(Command command, int floor) {
        if (destroyed) {
            return false;
        }
        this.attachedCommand = command;
        return true;
    }

    @Override
    public Command getAttachedCommand() {
        return attachedCommand;
    }

    @Override
    public void applyEffects(Cat cat) {
        if (!destroyed) {
            // Double the cat's power
            cat.multiplyPower(2);
            destroyed = true;

            // Execute command if present
            if (attachedCommand != null) {
                executeCommand(attachedCommand, cat);
            }
        }
    }

    private void executeCommand(Command cmd, Cat cat) {
        switch (cmd.getType()) {
            case TURN_N:
            case TURN_S:
            case TURN_E:
            case TURN_W:
                cat.setDirection(cmd.getDirection());
                break;
            case POWERUP:
                cat.addPower(1000); // Add 1000 power
                break;
            case STOMP:
                // No effect on power plant (single use)
                break;
        }
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public String getDisplayChar() {
        return destroyed ? "." : "P";
    }
}
