from dsl import *

ch = Chapter("bio_industry", "Bio-Industry", "fermenter",
             "Fuel, fertilizer and rubber from the farm: ethanol and biodiesel, ammonia and NPK, biogas and Bio-Coal, latex "
             "and vulcanised rubber, and the automated farms up to the Greenhouse Array.")

ch.q("fermenter", "Fermenter", "fermenter",
     "Tempered Alloy, plastic, glass, a cauldron and steel plates. A crop and 100 mB of water ferment into Ethanol (20 to "
     "80 mB; Sorghum is best) for 10 FE/t, giving off Carbon Dioxide. Slimeball, sugar and kelp make a slime culture. "
     "Ethanol burns at 900°C, runs the Gas Turbine, makes Biodiesel and Ethylene.",
     [produced(fluid="ethanol", icon="ethanol_bucket", label="Ferment Ethanol")], rewards=[item("sorghum_seeds", 16), xp(5)],
     frame="goal", items=["fermenter", "ethanol_bucket"])

ch.q("air_separator", "Air Separator", "air_separator",
     "Tempered Alloy, iron bars, nickel gears, a Wrought Fluid Tank: it pulls 80 Nitrogen and 20 Oxygen out of the air "
     "every two seconds for 80 FE/t (not in the End). Nitrogen is half of Ammonia; the Oxygen runs oxy-fuel burners.",
     [produced(fluid="nitrogen", icon="air_separator", label="Separate Nitrogen")], rewards=[item("nickel_gear", 4), xp(5)],
     items=["air_separator"])

ch.q("haber", "Haber Reactor", "haber_reactor",
     "Invar plates, a thermocouple, nickel rods and a Wrought Fluid Tank. 60 Hydrogen and 20 Nitrogen at 450°C become 40 "
     "Ammonia a second for 60 FE/t. Ammonia makes NPK Fertilizer and precipitates uranium.",
     [produced(fluid="ammonia", icon="haber_reactor", label="Make Ammonia")], deps=["air_separator"],
     rewards=[item("invar_plate", 4), xp(6)], frame="goal", items=["haber_reactor"])

ch.q("npk", "NPK Fertilizer", "npk_fertilizer",
     "Basic Slag, Wood Ash and 100 mB of Ammonia in the Chemical Reactor give two NPK Fertilizer: +15 nutrients, and it "
     "enriches Loam to grow at 2×. One NPK and 1,000 mB of water make 1,000 mB of Nutrient Solution for the Hydroponic Cell "
     "and Greenhouse.",
     [have("npk_fertilizer"), have("nutrient_solution_bucket")], deps=["haber"], rewards=[item("npk_fertilizer", 8), xp(6)],
     frame="goal", items=["npk_fertilizer", "nutrient_solution_bucket"])

ch.q("biodiesel", "Biodiesel", "biodiesel_bucket",
     "100 Seed Oil and 25 Ethanol give 100 Biodiesel; add 10 mB of Lye and get 150. It burns at 1,000°C and runs the Diesel "
     "Generator at 340 FE/mB (not the Gas Turbine).",
     [produced(fluid="biodiesel", icon="biodiesel_bucket", label="Make Biodiesel")], deps=["fermenter"],
     rewards=[item("biodiesel_bucket", 2), xp(6)], items=["biodiesel_bucket"])

ch.q("biogas_digester", "Biogas Digester", "biogas_digester_controller",
     "A 3×3×3 of Biogas Digester Casings (steel and copper plates, Wrought Fluid Tank) and a Controller, kept above 35°C. "
     "Plant matter and water digest into Biogas (a turbine fuel) and Digestate (+4 fertilizer), and it scrubs the Sulfur "
     "Dust out of the gas.",
     [formed("biogas_digester", icon="biogas_digester_controller")], deps=["fermenter"],
     rewards=[item("digester_casing", 8), xp(6)], frame="goal", items=["digester_casing", "biogas_digester_controller", "digestate"])

ch.q("biogas_sulfur", "Scrubbed", "sulfur_dust",
     "Take the Sulfur Dust the digester scrubs out of its Biogas.",
     [produced(category="biogas_sulfur", icon="sulfur_dust", label="Scrub sulfur from Biogas")], deps=["biogas_digester"],
     rewards=[item("digestate", 8), xp(4)])

ch.q("bio_coal", "Renewable Coal", "bio_coal",
     "The Hydrothermal Carbonizer (steel plates, bucket, Rubber Gaskets, a Grain Dryer, copper) cooks 8 biomass and 500 mB of "
     "water at 200°C into Bio-Coal, heat only. Bio-Coal is coal: burner fuel, Carbonizer feed for coke and creosote, Carbon "
     "Dust, Syngas. Nine make a block that carbonizes straight into a Block of Coal Coke.",
     [produced(item="bio_coal", label="Make Bio-Coal")], deps=["biogas_digester"], rewards=[item("bio_coal", 16), xp(6)],
     frame="goal", items=["hydrothermal_carbonizer", "bio_coal", "bio_coal_block"])

# --- rubber ---
ch.q("rubber_dandelion", "Rubber Dandelion", "rubber_dandelion_roots",
     "Wild Rubber Dandelions grow in meadows, plains and taiga; their seeds plant on farmland and in every automated farm. "
     "A ripe plant gives 1–3 Rubber Dandelion Roots, and the Oil Press squeezes 100 mB of Latex out of each.",
     [have("rubber_dandelion_roots")], rewards=[item("rubber_dandelion_seeds", 8), xp(3)],
     items=["rubber_dandelion_seeds", "rubber_dandelion_roots", "wild_rubber_dandelion"])

ch.q("resin_tap", "Resin Tap", "resin_tap",
     "An iron nugget, a copper ingot and treated planks. On a living jungle log it drips 25 mB of Latex every 10 seconds; "
     "on spruce it gives Pine Resin half the time (15% on other logs). Pine Resin replaces Creosote in the Infuser, and two "
     "with kelp make a slimeball.",
     [produced(category="resin_tap", icon="resin_tap", label="Tap a tree")], deps=["rubber_dandelion"],
     rewards=[item("resin_tap", 4), xp(4)], items=["resin_tap", "pine_resin", "latex_bucket"])

ch.q("raw_rubber", "Raw Rubber", "raw_rubber",
     "Dry 250 mB of Latex in a Grain Dryer for a Raw Rubber. Latex with lime dye also makes two slimeballs.",
     [have("raw_rubber")], deps=["resin_tap"], rewards=[item("raw_rubber", 8), xp(4)], items=["raw_rubber"])

ch.q("vulcanizer", "Vulcanised", "vulcanizer",
     "The Vulcanizer (steel plates, heavy weighted pressure plates, copper, a smoker) cures 2 Raw or Synthetic Rubber with "
     "a Sulfur Dust into 2 Rubber at 140°C, heat only. Four Rubber make eight Rubber Gaskets, which seal every Tempered or "
     "better Pressurized Conduit, Cylinder and Gas Cartridge, the Hardened Fluid Tank, Jetpacks, the Gasifier and the "
     "Hydrothermal Carbonizer.",
     [produced(item="rubber", label="Cure Rubber"), have("rubber_gasket")], deps=["raw_rubber"],
     rewards=[item("rubber_gasket", 8), xp(6)], frame="goal", items=["vulcanizer", "rubber", "rubber_block", "rubber_gasket"])

# --- automated farms ---
ch.q("glass_cloche", "Glass Cloche", "glass_cloche",
     "Glass panes over treated planks. One crop under glass, 100 mB of water a harvest, about two minutes a crop, no power.",
     [placed("glass_cloche")], deps=["rubber_dandelion"], rewards=[item("minecraft:glass_pane", 16), xp(3)], items=["glass_cloche"])

ch.q("grow_chamber", "Grow Chamber", "grow_chamber",
     "Steel plates, a redstone lamp, Pressure Glass and a Glass Cloche: three times the cloche's speed on 24 FE/t and water, "
     "with configurable sides.",
     [placed("grow_chamber")], deps=["glass_cloche"], rewards=[item("pressure_glass", 8), xp(4)], items=["grow_chamber"])

ch.q("hydroponic_cell", "Hydroponic Cell", "hydroponic_cell",
     "Tempered Alloy, a Grow Chamber, Pressure Glass and a Wrought Fluid Tank. Six times a cloche on 48 FE/t and Nutrient "
     "Solution (7.5× with Carbon Dioxide), and it grows saplings, flowers and hops without a trellis.",
     [placed("hydroponic_cell")], deps=["grow_chamber", "npk"], rewards=[item("nutrient_solution_bucket", 2), xp(6)], frame="goal",
     items=["hydroponic_cell"])

ch.q("greenhouse_parts", "Glasshouse Parts", "planting_bed",
     "Greenhouse Frames are steel plates and rods. Planting Beds are plates round a frame: right-click one with soil, then "
     "a seed. A Grow Lamp (steel rods and plate, glass panes, a redstone lamp) hangs under the roof and lights beds within "
     "three blocks at night for 16 FE/t.",
     [have("greenhouse_frame"), have("planting_bed"), have("grow_lamp")], deps=["grow_chamber"],
     rewards=[item("greenhouse_frame", 16), xp(5)], items=["greenhouse_frame", "planting_bed", "grow_lamp"])

ch.q("greenhouse", "Greenhouse Array", "greenhouse_controller",
     "A glass building 5×5×4 to 11×11×8: frames on the edges, Pressure Glass walls and roof, Planting Beds on the floor, "
     "the Controller (a frame with a thermocouple, Pressure Glass and a comparator) in a wall. It grows, harvests and "
     "replants every bed, fed with water, nutrients, Carbon Dioxide, heat and Grow Lamps.",
     [formed("greenhouse", icon="greenhouse_controller")], deps=["greenhouse_parts"],
     rewards=[item("pressure_glass", 32), xp(10)], frame="goal", items=["greenhouse_controller"])

ch.q("night_shift", "Night Shift", "grow_lamp",
     "Harvest from a Greenhouse lit by Grow Lamps at night.",
     [produced(category="greenhouse_lamps", icon="grow_lamp", label="Harvest under lamps")], deps=["greenhouse"],
     rewards=[item("grow_lamp", 4), xp(6)])

ch.q("fully_fed", "Fully Fed", "greenhouse_controller",
     "A Greenhouse harvest with water, Nutrient Solution, Carbon Dioxide and heat all supplied at once.",
     [produced(category="greenhouse_fully_fed", icon="greenhouse_controller", label="Harvest fully fed")], deps=["greenhouse"],
     rewards=[item("npk_fertilizer", 16), xp(12)], frame="challenge")

ch.q("agronomist", "Agronomist", "greenhouse_controller",
     "Fuel, fertilizer and rubber from plants, and a glasshouse that farms itself.",
     [produced(fluid="biodiesel", icon="biodiesel_bucket", label="Biodiesel"), have("npk_fertilizer"), have("rubber_gasket"),
      produced(item="bio_coal", label="Bio-Coal"), formed("greenhouse", icon="greenhouse_controller")],
     deps=["biodiesel", "npk", "vulcanizer", "bio_coal", "greenhouse", "hydroponic_cell"],
     rewards=[item("arcforged_alloy", 4), xp(20)], frame="challenge")
