from dsl import *

ch = Chapter("processing", "Ore Processing", "arc_crusher",
             "Arcforge's ores and what to do with them: crushing, sifting, pressing and induction smelting, the 3×3×3 "
             "arrays that triple each machine, and the components every later machine is built from.")

# --- ores ---
ch.q("ores", "New Veins", "silver_ore",
     "Arcforge adds ores to the Overworld: Silver (Y -32..64), Nickel (Y -64..16), Wolframite for tungsten (Y -64..-16), "
     "Fluorite (Y -16..48), Bismuth (Y 0..56), Galena for lead (Y -16..64), flat Halite beds, Spodumene for lithium, rare "
     "Uraninite, and glowing Arcite deep down (Y -64..-40, diamond pickaxe). Each has a deepslate variant. Dig up a raw "
     "piece of five of them.",
     [have("raw_silver"), have("raw_nickel"), have("raw_wolframite"), have("raw_fluorite"), have("raw_bismuth")],
     rewards=[item("minecraft:iron_pickaxe", 1), xp(3)],
     items=["silver_ore", "deepslate_silver_ore", "raw_silver", "nickel_ore", "deepslate_nickel_ore", "raw_nickel",
            "wolframite_ore", "deepslate_wolframite_ore", "raw_wolframite", "fluorite_ore", "deepslate_fluorite_ore",
            "raw_fluorite", "bismuth_ore", "deepslate_bismuth_ore", "raw_bismuth"])

ch.q("ingots", "Smelt the New Metals", "nickel_ingot",
     "Raw Silver, Nickel, Bismuth and Wolframite smelt in any furnace to Silver, Nickel, Bismuth and Tungsten Ingots; "
     "Raw Fluorite becomes a Fluorite Crystal. Nine of each pack into a storage block, and nine raw pieces into a raw "
     "block that an Arc Crusher turns into nine dust.",
     [have("silver_ingot"), have("nickel_ingot"), have("tungsten_ingot"), have("bismuth_ingot"), have("fluorite_crystal")],
     deps=["ores"], rewards=[item("nickel_ingot", 8), item("silver_ingot", 8), xp(3)],
     items=["silver_ingot", "silver_block", "raw_silver_block", "nickel_ingot", "nickel_block", "raw_nickel_block",
            "tungsten_ingot", "tungsten_block", "raw_wolframite_block", "bismuth_ingot", "bismuth_block",
            "raw_bismuth_block", "fluorite_crystal", "fluorite_block", "raw_fluorite_block"])

ch.q("sulfur", "Brimstone", "sulfur_dust",
     "Nether Sulfur Ore (Nether, Y 10..117) drops 2–4 Sulfur Dust; crushing gunpowder gives two, netherrack a chance. "
     "Sulfur Dust plus water in a Chemical Reactor is Sulfuric Acid, and the Vulcanizer needs it to cure rubber.",
     [have("sulfur_dust")], deps=["ores"], rewards=[item("sulfur_dust", 8), xp(2)], items=["nether_sulfur_ore", "sulfur_dust"])

ch.q("iolite", "Iolite", "iolite_crystal",
     "Iolite Ore grows in End Stone (Y 0..80) and drops Iolite Crystals (Fortune works). The crystal is the resonant part "
     "of the Link Conduit and Wired Machine Link. Crushing the ore gives two Iolite Dust, which blasts back into a "
     "crystal; nine crystals make a Block of Iolite.",
     [have("iolite_crystal")], deps=["ores"], rewards=[item("minecraft:ender_pearl", 4), xp(5)],
     items=["iolite_ore", "iolite_crystal", "iolite_dust", "iolite_block"])

ch.q("arcite", "Arcite", "arcite_crystal",
     "Glowing Arcite Ore sits below Y -40 in veins of four and needs a diamond pickaxe. Raw Arcite blasts into an Arcite "
     "Crystal (or crushes to Arcite Dust, which blasts to a crystal). Arcite is in every Arcforged recipe: the composite, "
     "the top-tier conduits, cells and storage, the Fortune III Module and the Quantum Tunnel. Nine make a glowing block.",
     [have("arcite_crystal")], deps=["ores"], rewards=[item("arcite_crystal", 2), xp(8)], frame="goal",
     items=["arcite_ore", "deepslate_arcite_ore", "raw_arcite", "raw_arcite_block", "arcite_dust", "arcite_crystal", "arcite_block"])

# --- crushing ---

ch.q("arc_crusher", "The Arc Crusher", "arc_crusher",
     "Steel plates and gears round a grindstone. 20 FE/t: one ore gives two dust plus a 25% bonus, a raw piece one dust "
     "and a chance of a second, coal gives Carbon Dust, Slag gives two Basic Slag, cobbled deepslate gives Deepslate "
     "Gravel. Dust smelts back to ingots, so crushing doubles your ore.",
     [placed("arc_crusher")], deps=["ingots", "getting_started/steel_parts"], rewards=[item("minecraft:raw_iron", 16), xp(4)], frame="goal",
     items=["arc_crusher"])

ch.q("dusts", "Dust to Dust", "iron_dust",
     "Crush raw iron, copper and gold for Iron, Copper and Gold Dust; quartz for Nether Quartz Dust; coal, charcoal or "
     "Bio-Coal for Carbon Dust (a half-coal fuel and a Carbonizer feed that makes coke without creosote); Ancient Debris "
     "for Ancient Debris Dust that smelts to netherite scrap. Two Iron Dust and one Nickel Dust craft into three Invar "
     "Dust.",
     [have("iron_dust"), have("copper_dust"), have("gold_dust"), have("carbon_dust")], deps=["arc_crusher"],
     rewards=[item("iron_dust", 16), xp(3)],
     items=["iron_dust", "copper_dust", "gold_dust", "carbon_dust", "nether_quartz_dust", "ancient_debris_dust",
            "silver_dust", "nickel_dust", "tungsten_dust", "fluorite_dust", "bismuth_dust", "lead_dust", "invar_dust"])

ch.q("invar", "Invar", "invar_ingot",
     "Smelt Invar Dust (2 Iron Dust + 1 Nickel Dust) for Invar Ingots. Invar barely expands with heat, so its plates line "
     "the Hardened Thermodynamic Conduit and Heat Cell, the Haber Reactor and the Superheater; Invar Rods make the "
     "Sifter's second mesh.",
     [have("invar_ingot")], deps=["dusts"], rewards=[item("invar_ingot", 8), xp(3)],
     items=["invar_ingot", "invar_plate", "invar_gear", "invar_rod"])

ch.q("sifter", "Sift the Fines", "sifter",
     "The Sifter shakes gravel, sand, Deepslate Gravel and Heavy Mineral Sand through a mesh for dusts, flint, quartz and "
     "more. Steel Mesh (8 Steel Rods) is the base: 256 blocks. Invar Mesh gives 1.25× the chances at 1.5× speed; "
     "Tungsten Mesh 1.5× and 2× speed, 1,024 blocks. Deepslate Gravel is the only sifted source of tungsten.",
     [produced(category="sifting", icon="sifter", label="Sift something")], deps=["arc_crusher"],
     rewards=[item("steel_mesh", 1), item("minecraft:gravel", 32), xp(3)],
     items=["sifter", "steel_mesh", "invar_mesh", "tungsten_mesh", "deepslate_gravel"])

ch.q("meshes", "Finer Meshes", "tungsten_mesh",
     "Eight Invar Rods or eight Tungsten Rods in a ring. Swap the mesh in the Sifter's mesh slot.",
     [have("invar_mesh"), have("tungsten_mesh")], all_of=False, deps=["sifter", "invar"],
     rewards=[item("tungsten_rod", 8), xp(3)], items=["invar_mesh", "tungsten_mesh"])

# --- pressing ---

ch.q("alloy_parts", "Pressed Metals", "nickel_plate",
     "Press the new metals too. Nickel Plates go into Hardened Alloy, the Steam Turbine, Electrolyzer and Cracking Unit; "
     "Nickel Gears into the Air Separator, Nickel Rods into the Haber Reactor. Silver Plates make Batteries, Trough "
     "Mirrors and the Quarry Scanner. Tungsten Plates, Gears and Rods build heating coils, the Diamond Press and the "
     "Gas Centrifuge.",
     [have("nickel_plate"), have("silver_plate"), have("tungsten_plate")], deps=["getting_started/steel_parts"],
     rewards=[item("nickel_plate", 8), item("tungsten_plate", 4), xp(4)],
     items=["nickel_plate", "nickel_gear", "nickel_rod", "silver_plate", "silver_gear", "silver_rod", "tungsten_plate",
            "tungsten_gear", "tungsten_rod"])

ch.q("heating_coil", "Tungsten Heating Coil", "tungsten_heating_coil",
     "Four Tungsten Plates round a copper rod. It's the element in the Induction Furnace, Arc Melter, Carbon Reclaimer, "
     "Combustor and Superheater casing.",
     [have("tungsten_heating_coil")], deps=["alloy_parts"], rewards=[item("tungsten_heating_coil", 2), xp(3)],
     items=["tungsten_heating_coil"])

ch.q("induction_furnace", "Induction Furnace", "induction_furnace",
     "An FE furnace at twice the speed, built round a Tungsten Heating Coil and a blast furnace. It smelts everything a "
     "furnace does, and later calcines Ammonium Diuranate into Yellowcake.",
     [placed("induction_furnace")], deps=["heating_coil"], rewards=[item("energy_upgrade", 2), xp(4)],
     items=["induction_furnace"])

ch.q("fiberizer", "Mineral Wool", "fiberizer",
     "The Fiberizer takes FE and heat (800°C+) and spins Slag into Slag Wool and basalt into Rock Wool: the insulation "
     "in every Heat Cell, Thermal Capsule, the Insulation Upgrade and the Foundry Suit. At 1,000°C it also spins Pitch "
     "into Carbon Fiber.",
     [have("slag_wool"), have("rock_wool")], deps=["induction_furnace"], rewards=[item("rock_wool", 8), xp(4)],
     items=["fiberizer", "slag_wool", "rock_wool"])

ch.q("arrays", "Three by Three", "arc_crushing_array_casing",
     "Each processing machine has a 3×3×3 array of 27 casings (the machine, Tempered Alloy and steel plates make four): "
     "the Arc Crushing Array (3 lanes, 2× speed, 60% less FE, double ore dust), Induction Furnace Array and Metal "
     "Pressing Array (each lane with its own die). Start with the crushers.",
     [formed("arc_crushing_array", icon="arc_crushing_array_casing", label="Form an Arc Crushing Array")],
     deps=["induction_furnace", "getting_started/steel_parts"], rewards=[item("tempered_alloy", 8), xp(6)], frame="goal",
     items=["arc_crushing_array_casing", "induction_furnace_array_casing", "metal_pressing_array_casing"])

ch.q("array_of_options", "Array of Options", "metal_pressing_array_casing",
     "All three arrays formed. Your ore line now runs at scale.",
     [formed("arc_crushing_array", icon="arc_crushing_array_casing"), formed("induction_furnace_array", icon="induction_furnace_array_casing"),
      formed("metal_pressing_array", icon="metal_pressing_array_casing")],
     deps=["arrays"], rewards=[item("speed_upgrade", 8), xp(12)], frame="challenge", hidden=True)

ch.q("composite", "Arcite-Tungsten Composite", "arcite_tungsten_composite",
     "A Tungsten Plate and two Arcite Crystals, shapeless. It's the hard core of Arcforged Alloy and the Tungsten Drill "
     "Head (four Tungsten Plates round it) that the Arc Quarry drills with.",
     [have("arcite_tungsten_composite")], deps=["arcite", "alloy_parts"], rewards=[item("tungsten_plate", 8), xp(6)],
     items=["arcite_tungsten_composite", "tungsten_drill_head"])

ch.q("diamond_press", "Pressure Makes Diamonds", "diamond_press",
     "Hardened Alloy, tungsten gears and plates round a Metal Press. Four Graphite at 1,400°C and 2,000,000 FE come "
     "out as one Diamond. Slow, hungry, and renewable.",
     [produced(category="diamond_pressing", icon="minecraft:diamond", label="Press a Diamond")], deps=["arrays"],
     rewards=[item("minecraft:diamond", 2), xp(10)], frame="challenge", items=["diamond_press"])
