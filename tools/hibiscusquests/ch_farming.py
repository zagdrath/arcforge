from dsl import *

ch = Chapter("farming", "Farming", "compost_bin",
             "Treated wood, living soil, five new crops and the hand tools and unpowered machines of a traditional farm, "
             "then the mills, presses and dryers that turn a harvest into something useful.")

ch.q("infuser", "The Infuser", "infuser",
     "Steel and copper plates, steel rods, a redstone block and a Wrought Fluid Tank. It soaks planks, logs and wood in "
     "Creosote (200 mB a log, 50 a plank) or Pine Resin and makes them fireproof treated wood, with FE.",
     [placed("infuser")], rewards=[item("creosote_bucket", 2), xp(3)], items=["infuser"])

ch.q("treated_wood", "Treated Wood", "treated_planks",
     "Treated Planks are the farm's timber: the Glass Cloche, Planter, Harvester, Millstone, Resin Tap and Solar casings "
     "all use them, and their slabs make the Compost Bin and Trellis. Treated Logs, Stripped Treated Logs, Treated Wood "
     "and Stripped Treated Wood come from soaking the matching vanilla block.",
     [have("treated_planks"), have("treated_log", "stripped_treated_log", "treated_wood", "stripped_treated_wood", label="Treat a log or wood block")],
     deps=["infuser"], rewards=[item("treated_planks", 32), xp(3)],
     items=["treated_planks", "treated_log", "stripped_treated_log", "treated_wood", "stripped_treated_wood"])

ch.q("treated_set", "Fireproof Carpentry", "treated_stairs",
     "Treated Planks craft like any planks: stairs, slabs, fence, fence gate, door, trapdoor, pressure plate and button, "
     "none of which burn. Make a slab; you'll need seven for a Compost Bin and five for a Trellis.",
     [have("treated_slab"), have("treated_stairs", "treated_fence", "treated_fence_gate", "treated_door", "treated_trapdoor",
                                 "treated_pressure_plate", "treated_button", label="Craft another treated-wood shape")],
     deps=["treated_wood"], rewards=[item("treated_slab", 16), xp(2)],
     items=["treated_stairs", "treated_slab", "treated_fence", "treated_fence_gate", "treated_door", "treated_trapdoor",
            "treated_pressure_plate", "treated_button"])

ch.q("compost_bin", "Compost Bin", "compost_bin",
     "Seven Treated Slabs. Throw in plant matter (seeds, leaves, Press Cake, crop waste) and it fills by the composter's "
     "chances into Compost: a +2 fertilizer, the half of Loam, a Mixed Fertilizer ingredient and digester feed. No power.",
     [placed("compost_bin"), have("compost")], deps=["treated_set"], rewards=[item("compost", 8), xp(3)], frame="goal",
     items=["compost_bin", "compost"])

ch.q("loam", "Loam", "loam",
     "Dirt and Compost, shapeless. Till it with a hoe for Loam Farmland: it holds 0 to 15 nutrients, grows crops 1.5× "
     "as fast, can't be trampled, and counts as 1.25× soil in a Planting Bed. Four Loam round a Copper Plate make four "
     "Irrigated Loam Farmland, always moist, never reverting.",
     [used_on("#minecraft:hoes", "loam_farmland", icon="loam", label="Till Loam into Loam Farmland")], deps=["compost_bin"],
     rewards=[item("loam", 16), xp(3)], items=["loam", "irrigated_loam_farmland"])

ch.q("fertilizers", "Feed the Soil", "mixed_fertilizer",
     "Use a fertilizer on Loam Farmland. Compost +2, Seed Meal +1, Wood Ash +3 (from burning charcoal), Basic Slag +3 "
     "(crushed furnace Slag), Digestate +4, Mixed Fertilizer +8 (Compost, Wood Ash and Basic Slag together), NPK +15. "
     "Nutrients drain as crops grow.",
     [used_on("#arcforge:fertilizers", "loam_farmland", "irrigated_loam_farmland", icon="mixed_fertilizer", label="Fertilize Loam Farmland"),
      have("mixed_fertilizer")],
     deps=["loam"], rewards=[item("mixed_fertilizer", 8), xp(4)], frame="goal",
     items=["mixed_fertilizer", "wood_ash", "basic_slag", "compost", "seed_meal"])

ch.q("irrigation", "Irrigation", "copper_sprinkler",
     "A Copper Sprinkler (four copper round a bucket) keeps a 9×9 moist and adds growth ticks for 1 mB/t of water. A "
     "Scarecrow (hay, Linen, wooden rods on a Stripped Treated Log) stops mobs trampling within 9 blocks. The Fertilizer "
     "Spreader (treated planks and fence, hopper, iron) feeds a 5×5 from its hopper when nutrients drop below 4.",
     [placed("copper_sprinkler"), placed("fertilizer_spreader"), placed("scarecrow")], all_of=False, deps=["fertilizers"],
     rewards=[item("copper_sprinkler", 2), xp(4)], items=["copper_sprinkler", "fertilizer_spreader", "scarecrow"])

ch.q("new_crops", "Wild Harvest", "flax_seeds",
     "Five wild plants grow in the world and their seeds drop from grass. Wild Flax in plains, Wild Rapeseed in meadows, "
     "Wild Sorghum in savannas, Wild Hops in forests and taigas, Wild Soybeans in plains and forests, and the Wild Rubber "
     "Dandelion in meadows and taiga. Pick up the seeds of flax, rapeseed, sorghum and hops.",
     [have("flax_seeds"), have("rapeseeds"), have("sorghum_seeds"), have("hop_seeds")], deps=["loam"],
     rewards=[item("flax_seeds", 4), item("rapeseeds", 4), item("sorghum_seeds", 4), item("hop_seeds", 4), xp(4)],
     items=["flax_seeds", "wild_flax", "rapeseeds", "wild_rapeseed", "sorghum_seeds", "wild_sorghum", "hop_seeds", "wild_hops"])

ch.q("flax", "Flax and Linen", "linen",
     "Flax grows two tall and gives Flax Fibre. One fibre is a String; four are Linen for the Scarecrow. Flax Seeds press "
     "to 80 mB of Seed Oil, and a Seed Extractor gets seeds back out of fibre.",
     [have("flax_fibre"), have("linen")], deps=["new_crops"], rewards=[item("minecraft:string", 16), xp(3)],
     items=["flax_fibre", "linen"])

ch.q("sorghum", "Sorghum", "sorghum_stalks",
     "Two-tall Sorghum gives Sorghum Stalks: the best ethanol crop (80 mB in the Fermenter), dried in a Grain Dryer to "
     "Dried Sorghum that ferments in half the time.",
     [have("sorghum_stalks")], deps=["new_crops"], rewards=[item("sorghum_seeds", 8), xp(3)], items=["sorghum_stalks"])

ch.q("hops", "Hops on the Trellis", "hop_cones",
     "Five Treated Slabs make a Trellis; stand it on farmland (two can stack) and use Hop Seeds on the bottom one. A "
     "bearing vine gives 1–3 Hop Cones and keeps growing. Dried Hops in the Fermenter add 20% to four batches.",
     [placed("trellis"), have("hop_cones")], deps=["new_crops"], rewards=[item("trellis", 4), xp(4)],
     items=["trellis", "hop_cones"])

ch.q("soybeans", "Soybeans", "soybeans",
     "A legume: it restores Loam nutrients instead of using them, and a non-legume harvested after it gets a crop-rotation "
     "bonus. Soybeans press to 150 mB of Seed Oil, the richest seed.",
     [have("soybeans")], deps=["new_crops"], rewards=[item("soybeans", 8), xp(3)], items=["soybeans", "wild_soybeans"])

ch.q("crop_rotation", "Crop Rotation", "soybeans",
     "Harvest a non-legume from Loam that just grew Soybeans and take the rotation bonus.",
     [produced(category="crop_rotation", icon="minecraft:wheat", label="Harvest with the rotation bonus")], deps=["soybeans"],
     rewards=[item("npk_fertilizer", 4), xp(6)], frame="goal")

ch.q("every_crop", "Every Crop", "rapeseeds",
     "Flax Fibre, Rapeseeds, Sorghum Stalks, Hop Cones and Soybeans, all from your own fields.",
     [have("flax_fibre"), have("rapeseeds"), have("sorghum_stalks"), have("hop_cones"), have("soybeans")],
     deps=["flax", "sorghum", "hops", "soybeans"], rewards=[item("irrigated_loam_farmland", 16), xp(8)], frame="goal")

ch.q("sickles", "Sickle and Scythe", "steel_scythe",
     "An Iron Sickle (2 iron on a wooden rod, 250 uses) or Steel Sickle (500) harvests and replants a 3×3. The Steel "
     "Scythe (3 steel, 2 rods, 1,000 uses) does a 5×5.",
     [have("iron_sickle", "steel_sickle", "steel_scythe", label="Make a sickle or scythe")], deps=["new_crops"],
     rewards=[item("steel_sickle", 1), xp(3)], items=["iron_sickle", "steel_sickle", "steel_scythe"])

ch.q("hands_free", "Hands Free", "harvester",
     "The Planter (treated planks and fence, hopper, copper) plants the 3×3 ahead of it on redstone or every 5 seconds, "
     "hops on trellises included. The Harvester (with shears and iron) harvests and replants the same 3×3 into 18 slots, "
     "picking hop cones too. Neither needs power.",
     [placed("planter"), placed("harvester")], deps=["sickles"], rewards=[item("wrought_item_conduit", 8), xp(5)], frame="goal",
     items=["planter", "harvester"])

ch.q("millstone", "Millstone and Mill", "millstone",
     "The Millstone (wooden rod, smooth stone slabs, grindstone, treated planks) turns by hand or redstone: wheat to Flour "
     "(bakes into bread, 25% extra), seeds to Seed Meal, bone to four bone meal, cane to two sugar. The Mill is a Millstone "
     "with steel plates and gears: three lanes at 8 FE/t each, half the time.",
     [placed("millstone"), placed("mill")], all_of=False, deps=["every_crop"], rewards=[item("flour", 8), xp(3)],
     items=["millstone", "mill", "flour", "seed_meal"])

ch.q("seed_extractor", "Seed Extractor", "seed_extractor",
     "Steel plates and gears, a hopper, shears and a redstone block. It threshes wheat, beetroot, melon, pumpkin, sorghum, "
     "hops and flax into seeds for 10 FE/t, so a harvest always replants itself.",
     [placed("seed_extractor")], deps=["every_crop"], rewards=[item("sorghum_seeds", 8), xp(3)], items=["seed_extractor"])

ch.q("oil_press", "Oil Press", "oil_press",
     "Steel plates and gears, a piston, a cauldron and a redstone block. Seeds give Seed Oil and Press Cake for 16 FE/t: "
     "Soybeans 150 mB, Rapeseeds 125, Flax 80, other seeds 25. Seed Oil is half of Biodiesel and a turbine lubricant like "
     "Heavy Oil; Press Cake goes to the Compost Bin or digester.",
     [placed("oil_press"), have("seed_oil_bucket"), have("press_cake")], deps=["every_crop"],
     rewards=[item("rapeseeds", 16), xp(4)], frame="goal", items=["oil_press", "seed_oil_bucket", "press_cake"])

ch.q("grain_dryer", "Grain Dryer", "grain_dryer",
     "Steel plates, iron bars, a barrel and copper. Heat only, 60°C is enough: Hop Cones to Dried Hops, wheat to Dried Grain, "
     "Sorghum Stalks to Dried Sorghum (each ferments in half the time), and 250 mB of Latex to Raw Rubber.",
     [placed("grain_dryer"), have("dried_hops", "dried_grain", "dried_sorghum", label="Dry a crop")], deps=["hops", "sorghum"],
     rewards=[item("wrought_thermal_capsule", 1), xp(4)], items=["grain_dryer", "dried_hops", "dried_grain", "dried_sorghum"])

ch.q("farmstead", "Farmstead", "planter",
     "Soil, water, five crops, and machines that need no power at all. The Bio-Industry chapter takes it from here.",
     [placed("planter"), placed("harvester"), placed("oil_press"), placed("grain_dryer"), have("mixed_fertilizer")],
     deps=["hands_free", "oil_press", "grain_dryer", "millstone", "seed_extractor", "irrigation"],
     rewards=[item("tempered_alloy", 8), xp(12)], frame="challenge")
