from dsl import *

ch = Chapter("refinery", "Distillation & Refinery", "distillation_array_controller",
             "The Distillation Array and every unit downstream of it: cracking, reforming, coking, blending, hydrotreating, "
             "sulfur recovery and steam reforming. Fuels, Pitch, Carbon Fiber and asphalt roads.")

ch.q("distillation_parts", "Column Parts", "distillation_array_casing",
     "Distillation Array Casings are Hardened Alloy, steel plates and a Hardened Fluid Tank. Tray Level Casings (a casing "
     "with glass) make the tray layers between top and bottom. The Controller is a casing with a copper gear, steel rods "
     "and a redstone block, and sits in an outside face that isn't the top or bottom layer.",
     [have("distillation_array_casing"), have("tray_level_casing"), have("distillation_array_controller")],
     rewards=[item("hardened_fluid_tank", 1), xp(4)],
     items=["distillation_array_casing", "tray_level_casing", "distillation_array_controller"])

ch.q("distillation_array", "Distillation Array", "distillation_array_controller",
     "A solid 2×2 or 3×3 column, 4 to 16 tall. Creosote needs 4 layers: Naphtha, Light Oil, Heavy Oil and Pitch. Crude Oil "
     "needs 6: Refinery Gas, Naphtha, Diesel, Heavy Oil and Bitumen, with Kerosene and Light Oil from 10 layers and the "
     "finest cuts at 14. Throughput scales from 10 mB/t at 2×2×4 to 90 mB/t at 3×3×16.",
     [formed("distillation_array", icon="distillation_array_controller")], deps=["distillation_parts"],
     rewards=[item("distillation_array_casing", 8), xp(8)], frame="goal")

ch.q("creosote_cuts", "Creosote Cuts", "naphtha_bucket",
     "Distil Creosote. Naphtha is the hottest liquid burner fuel (1,200°C, makes Superheated Steam) and feeds the "
     "Catalytic Reformer and Steam Cracker. Light Oil burns at 1,000°C and, with Hydrogen, becomes Plastic. Heavy Oil "
     "lubricates turbines (+8%), feeds the Cracking Unit and Lubricant Blender. Pitch is the solid left over.",
     [produced(fluid="naphtha", icon="naphtha_bucket", label="Distil Naphtha"),
      produced(fluid="light_oil", icon="light_oil_bucket", label="Distil Light Oil"),
      produced(fluid="heavy_oil", icon="heavy_oil_bucket", label="Distil Heavy Oil")],
     deps=["distillation_array"], rewards=[item("naphtha_bucket", 2), xp(6)],
     items=["naphtha_bucket", "light_oil_bucket", "heavy_oil_bucket"])

ch.q("pitch", "Pitch and Carbon Fiber", "pitch",
     "Two Pitch per bucket at 4–5 layers, one when taller. Pitch is a long furnace fuel (12 items), the binder in Asphalt, "
     "and in a Fiberizer at 1,000°C it spins into Carbon Fiber: the Arcforged Alloy additive, Turbine Blade Sets and Gas "
     "Centrifuge rotors.",
     [have("pitch"), have("carbon_fiber")], deps=["creosote_cuts"], rewards=[item("carbon_fiber", 4), xp(6)], frame="goal",
     items=["pitch", "carbon_fiber"])

ch.q("crude_cuts", "Crude Cuts", "diesel_bucket",
     "Distil Crude Oil in a 6-tall column or better. Diesel: 1,150°C, 420 FE/mB in a Diesel Generator. Bitumen: the "
     "bottoms, for Coker feed and Asphalt. Refinery Gas: a gas fuel and Steam Cracker feed. Build to 10 tall and Kerosene "
     "appears: jetpack fuel and the Diesel Generator's lighter cut.",
     [produced(fluid="diesel", icon="diesel_bucket", label="Distil Diesel"),
      produced(fluid="bitumen", icon="bitumen_bucket", label="Distil Bitumen"),
      produced(fluid="kerosene", icon="kerosene_bucket", label="Distil Kerosene")],
     deps=["distillation_array"], rewards=[item("diesel_bucket", 2), xp(8)], frame="goal",
     items=["diesel_bucket", "bitumen_bucket", "kerosene_bucket"])

ch.q("refinery_tower", "Refinery Tower", "tray_level_casing",
     "A full 16-tall Distillation Array. Every cut, 90 mB/t.",
     [formed("distillation_array", min_length=16, icon="distillation_array_controller")], deps=["crude_cuts"],
     rewards=[item("hardened_alloy", 16), xp(15)], frame="challenge")

ch.q("cracking_unit", "Cracking Unit", "cracking_unit_controller",
     "A 3×3×5 of Cracking Unit Casings (steel and nickel plates, Wrought Fluid Tank) and a Controller. Catalytic mode "
     "(Cracking Catalyst: clay, sand and Nickel Dust, 50 batches) breaks Heavy Oil into Naphtha, Diesel, Refinery Gas and "
     "Light Oil at 480°C. Hydrocracking adds Hydrogen for Diesel and Kerosene at 520°C.",
     [produced(category="cracking", icon="cracking_unit_controller", label="Crack Heavy Oil")], deps=["crude_cuts"],
     rewards=[item("cracking_catalyst", 8), xp(8)], frame="goal",
     items=["cracking_unit_casing", "cracking_unit_controller", "cracking_catalyst"])

ch.q("catalytic_reformer", "Gasoline", "catalytic_reformer",
     "The Catalytic Reformer turns 100 Naphtha into 85 Gasoline and 60 Hydrogen at 450°C. Gasoline is the hottest fuel of "
     "all: 450 HU/mB at 1,250°C, 675 FE/mB in a Gas Turbine.",
     [produced(fluid="gasoline", icon="gasoline_bucket", label="Reform Gasoline")], deps=["creosote_cuts"],
     rewards=[item("gasoline_bucket", 2), xp(8)], items=["catalytic_reformer", "gasoline_bucket"])

ch.q("coker", "Petroleum Coke", "coker",
     "The Coker (bricks, Wrought Fluid Tanks, a thermocouple and a firebox) cooks 250 Bitumen into a Petroleum Coke, 50 "
     "Refinery Gas and 50 Heavy Oil at 420°C. Petroleum Coke counts as Coal Coke everywhere.",
     [produced(category="coking", icon="petroleum_coke", label="Coke some Bitumen")], deps=["crude_cuts"],
     rewards=[item("petroleum_coke", 16), xp(6)], items=["coker", "petroleum_coke"])

ch.q("lubricant", "Lubricant", "lubricant_blender",
     "100 Heavy Oil in the Lubricant Blender gives 90 Lubricant for 20 FE/t. In a turbine's lubricant tank it adds 12%, "
     "against Heavy Oil's 8%.",
     [produced(fluid="lubricant", icon="lubricant_bucket", label="Blend Lubricant")], deps=["creosote_cuts"],
     rewards=[item("lubricant_bucket", 2), xp(5)], items=["lubricant_blender", "lubricant_bucket"])

ch.q("hydrotreater", "Hydrotreating", "hydrotreater",
     "Sour Kerosene and Sour Diesel burn at 75%. The Hydrotreater takes 100 of either plus 30 Hydrogen at 300°C and gives "
     "the clean fuel back plus 30 Hydrogen Sulfide.",
     [produced(category="hydrotreating", icon="hydrotreater", label="Sweeten a sour fuel")], deps=["crude_cuts"],
     rewards=[item("kerosene_bucket", 2), xp(6)], items=["hydrotreater", "sour_kerosene_bucket", "sour_diesel_bucket"])

ch.q("sulfur_recovery", "Sulfur Recovery", "sulfur_recovery_unit",
     "100 Hydrogen Sulfide and 50 Oxygen at 250°C make a Sulfur Dust and 100 Steam. Nothing from a sour well goes to "
     "waste.",
     [produced(category="sulfur_recovery", icon="sulfur_dust", label="Recover sulfur")], deps=["hydrotreater"],
     rewards=[item("sulfur_dust", 16), xp(6)], items=["sulfur_recovery_unit"])

ch.q("steam_methane_reformer", "Steam Methane Reformer", "steam_methane_reformer",
     "100 Natural Gas and 100 Steam at 700°C give 100 Hydrogen and 25 Carbon Dioxide: the cheapest Hydrogen in the mod, "
     "for hydrocracking, hydrotreating, plastics, ammonia and the Carbon Reclaimer.",
     [produced(category="steam_reforming", icon="steam_methane_reformer", label="Reform Natural Gas")], deps=["cracking_unit"],
     rewards=[item("wrought_pressurized_cylinder", 1), xp(6)], items=["steam_methane_reformer"])

ch.q("asphalt", "Paving", "asphalt",
     "Eight gravel round a Pitch or a Bitumen Bucket make eight Asphalt. Walking, running and riding on it is about 30% "
     "faster, and it comes in slabs and stairs.",
     [have("asphalt")], deps=["pitch"], rewards=[item("asphalt", 32), xp(4)], items=["asphalt", "asphalt_slab", "asphalt_stairs"])

ch.q("refinery_complete", "Full Refinery", "distillation_array_controller",
     "Column, cracker, reformer, coker, blender, hydrotreater, sulfur plant. Every drop accounted for.",
     [produced(category="cracking", icon="cracking_unit_controller", label="Crack"),
      produced(category="reforming", icon="catalytic_reformer", label="Reform"),
      produced(category="coking", icon="coker", label="Coke"),
      produced(category="lubricant_blending", icon="lubricant_blender", label="Blend"),
      produced(category="hydrotreating", icon="hydrotreater", label="Hydrotreat"),
      produced(category="sulfur_recovery", icon="sulfur_recovery_unit", label="Recover sulfur")],
     deps=["cracking_unit", "catalytic_reformer", "coker", "lubricant", "sulfur_recovery"],
     rewards=[item("arcforged_alloy", 4), xp(20)], frame="challenge")
