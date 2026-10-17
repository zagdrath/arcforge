from dsl import *

ch = Chapter("steam", "Steam Power", "steam_turbine_array_casing",
             "Water, heat and pressure: the boiler, turbine, superheater and condenser arrays, the industrial water supply, "
             "and the cylinders and conduits that hold a gas.")

ch.q("pressure_glass", "Pressure Glass", "pressure_glass",
     "Glass braced with steel rods, plates and Fluorite Crystals. It's the window block allowed in the Boiler, Turbine, "
     "Firebox Array, Battery Array, Greenhouse and Thermal Evaporator walls, and the Grow Chamber is built from it.",
     [have("pressure_glass")], rewards=[item("pressure_glass", 8), xp(2)], items=["pressure_glass"])

ch.q("electric_pump", "Electric Pump", "electric_pump",
     "Set it over a water source and it pumps the block below: 1,000 mB every second for 10 FE/t, infinite water from an "
     "infinite source. In an ocean or on a beach it pumps Seawater instead, which is the Salt chapter's feed.",
     [placed("electric_pump")], rewards=[item("wrought_fluid_conduit", 8), xp(2)], items=["electric_pump"])

ch.q("pressurized_cylinder", "Pressurized Cylinder", "wrought_pressurized_cylinder",
     "Gases never go in a Fluid Tank. A Wrought Pressurized Cylinder (steel plates, Wrought Alloy and a Wrought Fluid "
     "Tank) holds 64,000 mB of one gas; Tempered 256,000 (Rubber Gaskets), Hardened 1,024,000, Arcforged 4,096,000. Its "
     "slots fill Gas Cartridges and Jetpacks. Four PVC Sheets line one for uranium hexafluoride.",
     [have("wrought_pressurized_cylinder", "tempered_pressurized_cylinder", "hardened_pressurized_cylinder", "arcforged_pressurized_cylinder")],
     rewards=[item("wrought_pressurized_cylinder", 1), xp(3)],
     items=["wrought_pressurized_cylinder", "tempered_pressurized_cylinder", "hardened_pressurized_cylinder",
            "arcforged_pressurized_cylinder"])

ch.q("boiler", "Steam Boiler Array", "steam_boiler_array_casing",
     "A hollow box of Tempered casings, 3×3×3 up to 7×7×16 (a 3×3 needs only corners; bigger boxes need every edge). "
     "Heat in, water in, and it boils by temperature: Steam at 100°C, High-Pressure Steam at 500°C, Superheated Steam at "
     "900°C, chosen in its Pressure tab. The biggest wants 50,400 HU/t.",
     [formed("steam_boiler_array", icon="steam_boiler_array_casing")], deps=["pressure_glass", "electric_pump"],
     rewards=[item("steam_boiler_array_casing", 8), xp(6)], frame="goal", items=["steam_boiler_array_casing"])

ch.q("pressurized_conduits", "Pressurized Conduits", "tempered_pressurized_conduit",
     "Steam travels in Pressurized Conduits only. Eight Wrought conduits round Tempered Alloy and a Rubber Gasket make "
     "Tempered: 1,600 mB/t. Hardened 6,400 mB/t, Arcforged 25,600 mB/t (with Arcite). A Gas Reservoir (steel plates, "
     "Pressure Glass, Fluid Reservoir) stores 64,000 mB per block and merges with its neighbours.",
     [have("tempered_pressurized_conduit", "hardened_pressurized_conduit", "arcforged_pressurized_conduit")],
     deps=["pressurized_cylinder"], rewards=[item("tempered_pressurized_conduit", 8), xp(4)],
     items=["tempered_pressurized_conduit", "hardened_pressurized_conduit", "arcforged_pressurized_conduit", "gas_reservoir"])

ch.q("turbine", "Steam Turbine Array", "steam_turbine_array_casing",
     "A hollow tube of Tempered Alloy, nickel plate and steel gear casings, 3×3 to 7×7 across and 3 to 27 long, with "
     "steam, energy and Exhaust ports on its fitted spots. 20 FE per mB of Steam, 36 for High-Pressure, 56 for "
     "Superheated. Heavy Oil in its lubricant tank adds 8%, Lubricant 12%, and an Exhaust port another 10%.",
     [formed("steam_turbine_array", icon="steam_turbine_array_casing")], deps=["boiler", "pressurized_conduits"],
     rewards=[item("steam_turbine_array_casing", 8), xp(8)], frame="goal", items=["steam_turbine_array_casing"])

ch.q("superheated", "Superheated", "steam_boiler_array_casing",
     "Run the boiler at 900°C and make Superheated Steam: 56 FE/mB, nearly three times plain steam from the same water. "
     "A Firebox Array on coke, a Geothermal bank at 1,400°C, or Hydrogen will get you there.",
     [produced(fluid="superheated_steam", label="Make Superheated Steam", icon="steam_boiler_array_casing")],
     deps=["turbine"], rewards=[item("heat_upgrade", 4), xp(8)], frame="goal")

ch.q("superheater", "Superheater Array", "superheater_array_casing",
     "A solid box 3 to 7 on a side of Hardened casings (invar plates, heating coils, Pressure Glass). It lifts steam one "
     "grade for only 3 HU per mB, as long as its heat is at least 500°C (to High-Pressure) or 900°C (to Superheated). "
     "Boil cheap, upgrade cheap.",
     [formed("superheater_array", icon="superheater_array_casing")], deps=["superheated"],
     rewards=[item("superheater_array_casing", 8), xp(8)], items=["superheater_array_casing"])

ch.q("condenser", "Condenser Array", "condenser_array_casing",
     "Exhaust Steam from the turbine's Exhaust port goes back to water 1:1 in a solid box of Condenser casings (copper "
     "plates and rods, Tempered Alloy): 400 mB/t per 27 blocks, faster touching water or ice. Boiler, turbine, condenser: "
     "a closed loop that never needs a pump.",
     [formed("condenser_array", icon="condenser_array_casing")], deps=["turbine"],
     rewards=[item("condenser_array_casing", 8), xp(6)], frame="goal", items=["condenser_array_casing"])

ch.q("water_intake", "Industrial Water Intake", "water_intake_casing",
     "A 3×3×3 of Water Intake casings (an Electric Pump, copper and steel plates) standing in 400 or more connected water "
     "sources: 8,000 mB/t for 0.25 FE/mB, enough for a full-size boiler without a condenser.",
     [formed("water_intake", icon="water_intake_casing")], deps=["boiler"], rewards=[item("electric_pump", 1), xp(5)],
     items=["water_intake_casing"])

ch.q("nine_stage_spin", "Nine-Stage Spin", "steam_turbine_array_casing",
     "A Steam Turbine Array at least 9 long, running at full speed.",
     [turbine("steam_turbine_array", min_length=9, icon="steam_turbine_array_casing", label="Run a 9-long turbine at full speed")],
     deps=["condenser", "superheated"], rewards=[item("lubricant_bucket", 1), item("tempered_alloy", 16), xp(15)],
     frame="challenge", hidden=True)
