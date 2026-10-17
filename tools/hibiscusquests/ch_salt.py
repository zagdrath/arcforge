from dsl import *

ch = Chapter("salt_and_lithium", "Salt & Lithium", "battery_array_controller",
             "From halite and seawater to brine, chlorine and lye; from spodumene to lithium; and the Battery Array that "
             "stores a whole base's power.")

ch.q("rock_salt", "Rock Salt", "rock_salt",
     "Halite Ore lies in flat beds between Y -32 and 40, one bed in four chunks, thicker under deserts and oceans. It drops "
     "Rock Salt, which doesn't smelt: an Arc Crusher turns one into two Salt (four in the array). Nine Rock Salt pack into a "
     "block.",
     [have("rock_salt")], rewards=[item("rock_salt", 16), xp(2)],
     items=["halite_ore", "deepslate_halite_ore", "rock_salt", "raw_rock_salt_block"])

ch.q("salt", "Salt", "salt",
     "Crush Rock Salt, or evaporate Brine. A Salt and 250 mB of water in the Chemical Reactor is 250 mB of Brine; nine Salt "
     "make a Salt Block.",
     [have("salt")], deps=["rock_salt"], rewards=[item("salt", 16), xp(2)], items=["salt", "salt_block"])

ch.q("seawater", "Seawater", "seawater_bucket",
     "An Electric Pump in an ocean or on a beach pumps Seawater instead of water. 1,000 mB evaporates to 250 Brine and 675 "
     "fresh water.",
     [produced(fluid="seawater", icon="seawater_bucket", label="Pump Seawater")],
     deps=["salt"], rewards=[item("electric_pump", 1), xp(3)], items=["seawater_bucket"])

ch.q("thermal_evaporator", "Thermal Evaporator", "thermal_evaporator_controller",
     "A 3×3×9 tower: solid base and cap, seven ring layers (the middle of a ring side may be Pressure Glass), the "
     "Controller in the middle of a base side. Heat only, 100°C to run and 400°C for full speed: Seawater to Brine, Brine to "
     "Salt and a little Lithium Brine.",
     [formed("thermal_evaporator", icon="thermal_evaporator_controller")], deps=["seawater"],
     rewards=[item("thermal_evaporator_casing", 8), xp(6)], frame="goal",
     items=["thermal_evaporator_casing", "thermal_evaporator_controller"])

ch.q("brine", "Brine", "brine_bucket",
     "Brine is where the chemistry starts. 100 mB in an Electrolyzer gives 50 Hydrogen, 50 Chlorine and 100 Lye for "
     "60,000 FE.",
     [produced(fluid="brine", icon="brine_bucket", label="Make Brine")], deps=["thermal_evaporator"],
     rewards=[item("brine_bucket", 2), xp(4)], items=["brine_bucket"])

ch.q("chlor_alkali", "Chlor-Alkali", "lye_bucket",
     "Electrolyse Brine. Chlorine makes PVC and Hydrochloric Acid. Lye makes Lithium Hydroxide, and 10 mB in a biodiesel "
     "batch turns 100 mB of Seed Oil into 150 of Biodiesel.",
     [produced(fluid="chlorine", icon="electrolyzer", label="Make Chlorine"), produced(fluid="lye", icon="lye_bucket", label="Make Lye")],
     deps=["brine"], rewards=[item("lye_bucket", 2), xp(6)], frame="goal", items=["lye_bucket"])

ch.q("hydrochloric_acid", "Hydrochloric Acid", "hydrochloric_acid_bucket",
     "50 Chlorine and 50 Hydrogen make 100 Hydrochloric Acid: a second leaching acid that costs no sulfur.",
     [produced(fluid="hydrochloric_acid", icon="hydrochloric_acid_bucket", label="Make Hydrochloric Acid")], deps=["chlor_alkali"],
     rewards=[item("hydrochloric_acid_bucket", 2), xp(5)], items=["hydrochloric_acid_bucket"])

ch.q("spodumene", "Spodumene", "raw_spodumene",
     "Spodumene Ore sits at Y -48..48 and in pegmatite veins up to Y 160 under mountains and badlands; a stone pickaxe "
     "will do. Raw Spodumene doesn't smelt: crush it to Spodumene Dust.",
     [have("spodumene_dust")], rewards=[item("spodumene_dust", 8), xp(3)],
     items=["spodumene_ore", "deepslate_spodumene_ore", "raw_spodumene", "raw_spodumene_block", "spodumene_dust"])

ch.q("lithium_brine", "Lithium Brine", "lithium_brine_bucket",
     "Spodumene Dust and 250 mB of Sulfuric Acid give 250 mB of Lithium Brine; the Thermal Evaporator also leaves 25 mB "
     "with every Salt.",
     [produced(fluid="lithium_brine", icon="lithium_brine_bucket", label="Make Lithium Brine")], deps=["spodumene", "thermal_evaporator"],
     rewards=[item("sulfuric_acid_bucket", 2), xp(5)], items=["lithium_brine_bucket"])

ch.q("lithium_hydroxide", "Lithium Hydroxide", "lithium_hydroxide",
     "250 Lithium Brine and 100 Lye give Lithium Hydroxide and 100 Brine back.",
     [have("lithium_hydroxide")], deps=["lithium_brine", "chlor_alkali"], rewards=[item("lithium_hydroxide", 4), xp(6)],
     frame="goal", items=["lithium_hydroxide"])

ch.q("cell_parts", "Cathode, Anode, Separator", "lfp_cathode",
     "An LFP Cathode is Lithium Hydroxide, Iron Dust and Basic Slag in the reactor's three item slots. A Graphite Anode is "
     "Graphite under a Plate Die. Two Plastic Sheets make two Cell Separators.",
     [have("lfp_cathode"), have("graphite_anode"), have("cell_separator")], deps=["lithium_hydroxide"],
     rewards=[item("graphite", 8), xp(6)], items=["lfp_cathode", "graphite_anode", "cell_separator"])

ch.q("lithium_cells", "Lithium Cells", "wrought_lithium_cell",
     "Cathode, separator and anode in Wrought Alloy and steel plates: a Wrought Lithium Cell holds 64,000,000 FE. "
     "Tempered 512,000,000; Hardened 4,000,000,000; Arcforged 32,000,000,000. Each upgrades the one before and keeps its "
     "charge. They only work inside a Battery Array.",
     [have("wrought_lithium_cell", "tempered_lithium_cell", "hardened_lithium_cell", "arcforged_lithium_cell")],
     deps=["cell_parts"], rewards=[item("lfp_cathode", 2), xp(8)],
     items=["wrought_lithium_cell", "tempered_lithium_cell", "hardened_lithium_cell", "arcforged_lithium_cell"])

ch.q("power_regulators", "Power Regulators", "wrought_power_regulator",
     "The same parts with a redstone block. Each regulator inside the array adds throughput: Wrought 65,536 FE/t each way, "
     "Tempered 262,144, Hardened 1,048,576, Arcforged 4,194,304.",
     [have("wrought_power_regulator", "tempered_power_regulator", "hardened_power_regulator", "arcforged_power_regulator")],
     deps=["cell_parts"], rewards=[item("minecraft:redstone_block", 4), xp(6)],
     items=["wrought_power_regulator", "tempered_power_regulator", "hardened_power_regulator", "arcforged_power_regulator"])

ch.q("battery_array", "Battery Array", "battery_array_controller",
     "Battery Array Casings (steel plates, Plastic Sheets, redstone) make a box 3 to 5 on a side, faces of Pressure Glass "
     "if you like, with the Controller (casings round a Wrought Energy Cell) in a wall. Fill the inside with Lithium Cells "
     "and Power Regulators and set Energy Input and Output ports.",
     [formed("battery_array", icon="battery_array_controller")], deps=["lithium_cells", "power_regulators"],
     rewards=[item("battery_array_casing", 8), xp(10)], frame="goal", items=["battery_array_casing", "battery_array_controller"])

ch.q("power_bank", "Power Bank", "arcforged_lithium_cell",
     "A full 5×5×5 Battery Array: 27 cells and regulators inside.",
     [formed("battery_array", min_length=125, icon="battery_array_controller")], deps=["battery_array"],
     rewards=[item("hardened_lithium_cell", 1), xp(20)], frame="challenge", hidden=True)
