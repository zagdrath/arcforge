from dsl import *

ch = Chapter("heat_and_power", "Heat & Power", "geothermal_plant",
             "Everything that makes heat and turns it into FE: lava, liquid and gas burners, the Firebox Array, the sun, "
             "and the cells, capsules and pipes that store and move heat.")

ch.q("geothermal_plant", "Geothermal Plant", "geothermal_plant",
     "Lava into heat, up to 1,400°C. Fill its 8,000 mB tank with buckets or a pipe (1 mB/t, 80,000 HU a bucket), or just "
     "surround it: every touching lava source adds 40 HU/t and every magma block 16 HU/t, forever, free. Five lava sides "
     "is 200 HU/t with no fuel and no pumping.",
     [placed("geothermal_plant")], rewards=[item("minecraft:lava_bucket", 2), xp(3)], frame="goal", items=["geothermal_plant"])

ch.q("thermal_conduits", "Move the Heat", "wrought_thermal_conduit",
     "Heat only flows from hotter to colder, and a touching machine takes at most 100 HU/t. Thermodynamic Conduits carry "
     "more: Wrought 50, Tempered 200, Hardened 800 (Invar Plates), Arcforged 51,200 HU/t. About 25 lava-locked Geothermal "
     "Plants on Tempered conduits run a full Steam Turbine Array.",
     [have("wrought_thermal_conduit", "tempered_thermal_conduit", "hardened_thermal_conduit", "arcforged_thermal_conduit")],
     deps=["geothermal_plant"], rewards=[item("tempered_thermal_conduit", 8), xp(3)],
     items=["wrought_thermal_conduit", "tempered_thermal_conduit", "hardened_thermal_conduit", "arcforged_thermal_conduit"])

ch.q("fuel_burner", "Fuel Burner", "fuel_burner",
     "A Firebox for liquids and gases: each fuel burns at its own temperature and HU per mB. Creosote 850°C, Ethanol "
     "900°C, Biodiesel 1,000°C, Light Oil 1,000°C, Kerosene and Diesel 1,150°C, Naphtha 1,200°C, Gasoline 1,250°C, "
     "Hydrogen 1,400°C. Oxygen on an Oxygen face gives oxy-fuel: +300°C and 1.25× the heat.",
     [placed("fuel_burner")], deps=["geothermal_plant"], rewards=[item("creosote_bucket", 2), xp(3)], items=["fuel_burner"])

ch.q("arc_melter", "Arc Melter", "arc_melter",
     "Tempered Alloy and a heating coil round a blast furnace: cobblestone or stone into 250 mB of lava for 50 FE/mB. "
     "Pipe it into a Geothermal Plant when there's no lava lake nearby.",
     [placed("arc_melter")], deps=["fuel_burner"], rewards=[item("minecraft:cobblestone", 64), xp(3)], items=["arc_melter"])

ch.q("firebox_array", "Firebox Array", "firebox_array_controller",
     "A hollow box of Firebox Array Casings, 3 to 7 on a side, with one Controller in a side wall facing out. Its walls, "
     "floor and roof may be Pressure Glass. It burns solids, liquids and gases: 3,967 HU/t at 3×3×3, 50,400 HU/t at "
     "7×7×7, exactly what the biggest Steam Boiler Array takes. Coke burns at 1,300°C, Hydrogen at 1,400°C.",
     [formed("firebox_array", icon="firebox_array_controller")], deps=["fuel_burner"],
     rewards=[item("firebox_array_casing", 8), xp(6)], frame="goal", items=["firebox_array_casing", "firebox_array_controller"])

ch.q("roaring_inferno", "Roaring Inferno", "firebox_array_casing",
     "A full 7×7×7 Firebox Array: 343 blocks, 50,400 HU/t.",
     [formed("firebox_array", min_length=343, icon="firebox_array_controller")], deps=["firebox_array"],
     rewards=[item("coal_coke_block", 8), xp(15)], frame="challenge")

ch.q("oxy_fuel", "Oxy-Fuel", "firebox",
     "Feed Oxygen (from an Electrolyzer or Air Separator) into a Firebox, Fuel Burner or Firebox Array through an Oxygen "
     "face: +300°C, 1.25× heat per fuel, 3.125 mB of oxygen per 100 HU. The flame turns blue-white.",
     [oxy(1000, label="Burn on oxy-fuel")], deps=["firebox_array"], rewards=[item("heat_upgrade", 2), xp(6)], frame="goal")

ch.q("white_heat", "White Heat", "fuel_burner",
     "Oxy-fuel at 1,600°C, the hottest flame in the mod: Hydrogen and Oxygen in a Firebox Array.",
     [oxy(1600, label="Reach 1,600°C on oxy-fuel")], deps=["oxy_fuel"], rewards=[item("hardened_alloy", 4), xp(15)],
     frame="challenge")

ch.q("heat_upgrades", "Hotter, Tighter", "heat_upgrade",
     "The Heat Upgrade (blaze powder, magma cream, Steel Plate) gives more heat per fuel or lava and lifts a "
     "Thermoelectric Plant's efficiency. The Insulation Upgrade (Rock Wool) slows a Heat Cell's leak.",
     [have("heat_upgrade"), have("insulation_upgrade")], all_of=False, deps=["geothermal_plant"],
     rewards=[item("heat_upgrade", 2), xp(3)], items=["heat_upgrade", "insulation_upgrade"])

ch.q("heat_cells", "Heat Cells", "wrought_heat_cell",
     "A battery for heat. Wrought (Slag Wool round a copper block): 50,000 HU, 50 HU/t, leaks 10% a minute. Tempered "
     "200,000 HU; Hardened 800,000 HU with Invar Plates and Rock Wool, holding 1,300°C; Arcforged 3,200,000 HU at "
     "1,400°C, 0.5% leak. Each upgrades the one before and keeps its heat.",
     [have("wrought_heat_cell", "tempered_heat_cell", "hardened_heat_cell", "arcforged_heat_cell")],
     deps=["heat_upgrades"], rewards=[item("slag_wool", 8), xp(4)],
     items=["wrought_heat_cell", "tempered_heat_cell", "hardened_heat_cell", "arcforged_heat_cell"])

ch.q("thermal_capsules", "Thermal Capsules", "wrought_thermal_capsule",
     "Portable heat: fill a capsule in a Heat Cell's slot and carry it to a Grain Dryer or Vulcanizer out in the fields. "
     "50,000 HU Wrought to 3,200,000 HU Arcforged; it leaks while carried.",
     [have("wrought_thermal_capsule", "tempered_thermal_capsule", "hardened_thermal_capsule", "arcforged_thermal_capsule")],
     deps=["heat_cells"], rewards=[item("wrought_thermal_capsule", 1), xp(3)],
     items=["wrought_thermal_capsule", "tempered_thermal_capsule", "hardened_thermal_capsule", "arcforged_thermal_capsule"])

ch.q("solar_parts", "Mirrors and Tubes", "trough_mirror",
     "Three glass over three Silver Plates make a Trough Mirror; glass, copper rods, glass a Receiver Tube. Three mirrors, "
     "two steel rods and a tube build a Solar Collector.",
     [have("trough_mirror"), have("receiver_tube"), have("solar_collector")], deps=["thermal_conduits"],
     rewards=[item("silver_plate", 6), xp(3)], items=["trough_mirror", "receiver_tube", "solar_collector"])

ch.q("solar_thermal_array", "Solar Thermal Array", "solar_thermal_array_controller",
     "A 2×2 tower four tall: three layers of Solar Thermal Array Casings (one is the Controller, in the bottom layer) and "
     "four Solar Collectors on top. It tracks the sun along the controller's facing (north–south full, east–west ×0.7) "
     "for up to 600 HU/t at 1,100°C at a clear noon. Nothing at night, so pair it with Heat Cells.",
     [formed("solar_thermal_array", icon="solar_thermal_array_controller")], deps=["solar_parts"],
     rewards=[item("tempered_heat_cell", 1), xp(6)], frame="goal",
     items=["solar_thermal_array_casing", "solar_thermal_array_controller"])

ch.q("heat_pipe", "Heat Pipe", "heat_pipe",
     "The Arcforged answer to long heat runs: a straight pipe up to 32 blocks of Rock Wool, Arcforged Alloy, tungsten "
     "plates and an Arcforged Thermodynamic Conduit, 262,144 HU/t with 0.5% loss per block. It only connects at its two "
     "ends.",
     [have("heat_pipe")], deps=["thermal_conduits", "heat_cells"], rewards=[item("heat_pipe", 4), xp(10)], frame="challenge",
     items=["heat_pipe"])
