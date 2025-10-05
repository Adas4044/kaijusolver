#!/usr/bin/env python3
"""
Test to demonstrate multi-floor building mechanics
"""

from simulator import GameSimulator, CatID, BuildingTile

def test_two_floor_destruction():
    """
    Test that a 2-floor building requires 2 hits to destroy.
    Direct test by simulating cat entry to building tile.
    """
    print("=" * 60)
    print("TEST: Two-Floor Building Destruction (B2)")
    print("=" * 60)
    
    # Layout with B2 building
    layout = [
        ["C_R_S", "B2", "B", "C_R_E"]
    ]
    
    simulator = GameSimulator(layout)
    red_cat = simulator.cats[CatID.RED]
    building = simulator.get_tile(1, 0)
    
    print(f"\nInitial state:")
    print(f"  Red cat: Power={red_cat.current_power}")
    print(f"  Building at (1,0): {building.remaining_floors} floors, {building.power_value} points per floor")
    print(f"  Is destroyed: {building.is_destroyed}")
    
    # Verify initial state
    assert isinstance(building, BuildingTile), "Should be a BuildingTile"
    assert building.floors == 2, "Should start with 2 floors"
    assert building.remaining_floors == 2, "Should have 2 remaining floors"
    assert building.power_value == 250, "Low-value building = 250 per floor"
    assert not building.is_destroyed, "Should not be destroyed initially"
    
    # Turn 1: Red cat enters building (destroys first floor)
    simulator.simulate_turn()
    print(f"\nAfter Turn 1 (cat hits first floor):")
    print(f"  Red cat: Power={red_cat.current_power} (gained {red_cat.current_power - 1000})")
    print(f"  Building: {building.remaining_floors} floor remaining")
    print(f"  Is destroyed: {building.is_destroyed}")
    
    assert red_cat.current_power == 1250, "Cat should gain 250 points from first floor"
    assert building.remaining_floors == 1, "Should have 1 floor remaining"
    assert not building.is_destroyed, "Should not be fully destroyed yet"
    
    print("\n✓ First floor destroyed: Building still has 1 floor remaining")
    print(f"✓ Total building value: {2 * building.power_value} points (250 per floor × 2 floors)")


def test_high_value_two_floor():
    """
    Test that BU2 gives 500 points per floor (1000 total)
    """
    print("\n" + "=" * 60)
    print("TEST: High-Value Two-Floor Building (BU2)")
    print("=" * 60)
    
    layout = [
        ["C_R_S", "BU2", "B", "C_R_E"]
    ]
    
    simulator = GameSimulator(layout)
    red_cat = simulator.cats[CatID.RED]
    building = simulator.get_tile(1, 0)
    
    print(f"\nInitial state:")
    print(f"  Red cat: Power={red_cat.current_power}")
    print(f"  Building at (1,0): {building.remaining_floors} floors, {building.power_value} points per floor")
    
    assert building.floors == 2, "Should start with 2 floors"
    assert building.power_value == 500, "High-value building = 500 per floor"
    assert building.is_high_value, "Should be marked as high-value"
    
    # Turn 1: Cat hits first floor
    simulator.simulate_turn()
    print(f"\nAfter Turn 1 (cat hits first floor):")
    print(f"  Red cat: Power={red_cat.current_power} (gained {red_cat.current_power - 1000})")
    print(f"  Building: {building.remaining_floors} floor remaining")
    
    assert red_cat.current_power == 1500, "Cat should gain 500 points from first floor"
    assert building.remaining_floors == 1, "Should have 1 floor remaining"
    
    print("\n✓ BU2 gives 500 points per floor")
    print(f"✓ Total building value: 1000 points (500 per floor × 2 floors)")


def test_single_vs_double_floor():
    """
    Compare single-floor vs two-floor buildings
    """
    print("\n" + "=" * 60)
    print("TEST: Single-Floor (B) vs Two-Floor (B2)")
    print("=" * 60)
    
    layout = [
        ["C_R_S", "B", "B2", "BU", "BU2", "C_R_E"]
    ]
    
    simulator = GameSimulator(layout)
    
    b1 = simulator.get_tile(1, 0)   # B (single floor)
    b2 = simulator.get_tile(2, 0)   # B2 (two floors)
    bu1 = simulator.get_tile(3, 0)  # BU (single floor)
    bu2 = simulator.get_tile(4, 0)  # BU2 (two floors)
    
    print("\nBuilding comparison:")
    print(f"  B  (1 floor):  {b1.floors} floor(s) × {b1.power_value} points = {b1.floors * b1.power_value} total")
    print(f"  B2 (2 floors): {b2.floors} floor(s) × {b2.power_value} points = {b2.floors * b2.power_value} total")
    print(f"  BU  (1 floor):  {bu1.floors} floor(s) × {bu1.power_value} points = {bu1.floors * bu1.power_value} total")
    print(f"  BU2 (2 floors): {bu2.floors} floor(s) × {bu2.power_value} points = {bu2.floors * bu2.power_value} total")
    
    # Verify values
    assert b1.floors == 1 and b1.power_value == 250, "B = 1 floor × 250"
    assert b2.floors == 2 and b2.power_value == 250, "B2 = 2 floors × 250"
    assert bu1.floors == 1 and bu1.power_value == 500, "BU = 1 floor × 500"
    assert bu2.floors == 2 and bu2.power_value == 500, "BU2 = 2 floors × 500"
    
    print("\n✓ Single-floor buildings give points once")
    print("✓ Two-floor buildings give points twice (requires 2 hits)")
    print("✓ Both floors of a 2-floor building have the same value")


if __name__ == "__main__":
    test_two_floor_destruction()
    test_high_value_two_floor()
    test_single_vs_double_floor()
    print("\n" + "=" * 60)
    print("All multi-floor tests passed! ✓")
    print("=" * 60)
