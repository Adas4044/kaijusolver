import unittest

from simulator import (
    GameSimulator,
    STARTING_BUDGET,
    DEFAULT_COMMAND_CATALOG,
    CatID,
    CatStatus,
    MudTile,
)


class GameSimulatorTests(unittest.TestCase):
    def test_command_placement_rules(self):
        layout = [
            ["C_R_S", "B", "E", "C_R_E"],
            ["C_G_S", "B", "E", "C_G_E"],
            ["C_B_S", "B", "E", "C_B_E"],
            ["E", "B", "E", "E"],
        ]

        simulator = GameSimulator(layout)

        # Can place on building tile
        self.assertTrue(simulator.place_command(1, 0, "RIGHT"))
        expected_remaining = STARTING_BUDGET - DEFAULT_COMMAND_CATALOG["RIGHT"]["cost"]
        self.assertEqual(simulator.get_budget_remaining(), expected_remaining)

        # Cannot place on empty tile
        self.assertFalse(simulator.place_command(0, 0, "UP"))

        # Cannot place on destroyed building tile
        tile = simulator.get_tile(1, 0)
        self.assertIsNotNone(tile)
        tile.is_destroyed = True  # type: ignore[attr-defined]
        self.assertFalse(simulator.place_command(1, 0, "LEFT"))

    def test_command_budget_enforced(self):
        layout = [
            ["C_R_S", "B", "B", "B", "B", "C_R_E"],
            ["B", "B", "B", "B", "B", "B"],
            ["C_G_S", "B", "B", "BS", "B", "C_G_E"],
            ["B", "B", "B", "B", "B", "B"],
            ["C_B_S", "B", "B", "B", "B", "C_B_E"],
        ]

        simulator = GameSimulator(layout)
        command_name = "RIGHT"
        cost = DEFAULT_COMMAND_CATALOG[command_name]["cost"]

        placements = [
            (1, 0), (2, 0), (3, 0), (4, 0),
            (0, 1), (1, 1), (2, 1), (3, 1),
        ]

        for coords in placements:
            self.assertTrue(simulator.place_command(*coords, command_name))

        self.assertEqual(simulator.get_budget_remaining(), STARTING_BUDGET - len(placements) * cost)

        # Next placement should exceed budget
        self.assertFalse(simulator.place_command(4, 1, command_name))

    def test_s2_tile_maps_to_mud(self):
        layout = [
            ["C_R_S", "S2", "C_R_E"],
            ["C_G_S", "E", "C_G_E"],
            ["C_B_S", "E", "C_B_E"],
        ]

        simulator = GameSimulator(layout)
        tile = simulator.get_tile(1, 0)
        self.assertIsInstance(tile, MudTile)

    def test_bed_arrival_bonus(self):
        layout = [
            ["C_R_S", "C_R_E", "E"],
            ["C_G_S", "E", "C_G_E"],
            ["C_B_S", "E", "C_B_E"],
        ]

        simulator = GameSimulator(layout)
        simulator.simulate_turn()

        red_cat = simulator.cats[CatID.RED]
        self.assertEqual(red_cat.status, CatStatus.FINISHED)
        self.assertEqual(red_cat.current_power, 1000 + 2000)
        self.assertEqual(simulator.global_bed_arrival_counter, 1)
        self.assertEqual(red_cat.position, simulator.cat_beds[CatID.RED])

        green_cat = simulator.cats[CatID.GREEN]
        self.assertEqual(green_cat.status, CatStatus.ACTIVE)


if __name__ == "__main__":
    unittest.main()
