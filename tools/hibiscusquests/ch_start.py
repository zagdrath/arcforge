from dsl import *

ch = Chapter("getting_started", "Getting Started", "steel_ingot",
             "From bare hands to a working grid: the Wrench, your first power, Coal Coke and the Arcforge Furnace, your first "
             "steel, the Metal Press, the Thermoelectric Plant and the Wrought-tier blocks that move energy, items, fluids, "
             "heat and gas.")

ch.q("handbook", "Read the Manual", "engineers_handbook",
     "The Engineer's Handbook is the reference: every machine, every multiblock with a step-by-step build view, every "
     "recipe link (click an item to open JEI). Craft it from a Book and a Copper Ingot and keep it next to your quest book.",
     [have("engineers_handbook")], deps=[], rewards=[item("minecraft:book", 1), xp(1)],
     items=["engineers_handbook"])

ch.q("wrench", "Turn of the Wrench", "wrench",
     "Three iron and a copper. Sneak-scroll to switch modes: Configure opens side settings, Rotate turns a machine, "
     "Port sets a multiblock's ports (every array starts with none), Dismantle picks a machine up with its contents. "
     "You'll use it on every structure in this book.",
     [have("wrench")], deps=[], rewards=[item("minecraft:copper_ingot", 8), xp(1)], items=["wrench"])

ch.q("combustion_plant", "Burn Something", "combustion_plant",
     "The Combustion Plant burns coal, charcoal, coal blocks or Bio-Coal straight to FE: 40 FE/t, 32,000 FE per coal. "
     "It's the simplest generator and the least efficient. Place one: it needs no steel, so it is your first power, and it will run the Metal Press later in this chapter.",
     [placed("combustion_plant")], deps=["wrench"], rewards=[item("minecraft:coal", 16), xp(2)],
     items=["combustion_plant"])

ch.q("furnace_bricks", "Firebrick", "arcforge_furnace_bricks",
     "Two bricks and two nether bricks make four Arcforge Furnace Bricks. They line the Firebox, build the Carbonizer "
     "and the Arcforge Furnace, and are the heat-proof block in every early recipe.",
     [have("arcforge_furnace_bricks")], deps=["wrench"], rewards=[item("minecraft:nether_brick", 8), xp(1)],
     items=["arcforge_furnace_bricks"])

ch.q("carbonizer", "The Carbonizer", "carbonizer",
     "A Carbonizer is a slice 1 wide, 2 to 3 tall and 2 to 3 deep; up to 8 slices side by side share one GUI. It needs "
     "no fuel and no power: it bakes coal (600 ticks), Carbon Dust, logs or Bio-Coal into Coal Coke and drips Creosote "
     "into its tank. Set an Output port with the Wrench and let it run.",
     [formed("carbonizer", icon="carbonizer")], deps=["furnace_bricks"], rewards=[item("minecraft:coal", 32), xp(3)], frame="goal",
     items=["carbonizer"])

ch.q("coal_coke", "Coal Coke", "coal_coke",
     "Coke is coal with the tar baked out: it burns hotter (1,300°C, 1.5× a coal in a Firebox Array) and is the "
     "reducing agent the Arcforge Furnace needs for every smelt. Nine make a Block of Coal Coke, and a Block of Bio-Coal "
     "carbonizes straight into one.",
     [have("coal_coke")], deps=["carbonizer"], rewards=[item("coal_coke", 16), xp(2)],
     items=["coal_coke", "coal_coke_block"])

ch.q("creosote", "Creosote", "creosote_bucket",
     "250 mB per coal, 100 per log. Creosote burns in a Fuel Burner at 850°C, soaks wood in the Infuser into fireproof "
     "treated wood, and is the first feed for a Distillation Array (Naphtha, Light Oil, Heavy Oil and Pitch). Pipe it out "
     "or bucket it from the Carbonizer's tank slot.",
     [produced(fluid="creosote", label="Make Creosote in the Carbonizer", icon="creosote_bucket")],
     deps=["carbonizer"], rewards=[item("wrought_fluid_tank", 1), xp(2)], items=["creosote_bucket"])

ch.q("furnace_parts", "Bricks and Walls", "arcforge_furnace_brick_wall",
     "The Arcforge Furnace is a 3×3×6 cross-shaped column: 24 Arcforge Furnace Bricks, 24 Arcforge Furnace Brick Walls "
     "on the four corners, and one Arcforge Furnace Port (bricks, a blast furnace and iron bars) as its face. Six bricks "
     "make six walls.",
     [have("arcforge_furnace_brick_wall"), have("arcforge_furnace_port")], deps=["coal_coke"],
     rewards=[item("arcforge_furnace_bricks", 16), xp(2)], items=["arcforge_furnace_bricks", "arcforge_furnace_brick_wall",
                                                                  "arcforge_furnace_port"])

ch.q("arcforge_furnace", "The Arcforge Furnace", "arcforge_furnace_port",
     "Form the column and open the Port. It burns Coal Coke for heat and smelts by temperature: steel at 1,200°C, "
     "Tempered at 1,300°C, Hardened at 1,400°C, Arcforged and Graphite at 1,500°C. Every smelt leaves Slag. The "
     "Handbook's build view shows it layer by layer.",
     [formed("arcforge_furnace", icon="arcforge_furnace_port")], deps=["furnace_parts"],
     rewards=[item("coal_coke", 32), xp(5)], frame="goal", items=["arcforge_furnace_port"])

ch.q("steel_ingot", "First Steel", "steel_ingot",
     "One iron and one coke at 1,200°C. Add a Fluorite Crystal as flux and 4 iron + 4 coke give 4 steel in one go. Steel "
     "is the backbone of the mod: plates, gears, rods, tools, armour and nearly every machine casing.",
     [have("steel_ingot")], deps=["arcforge_furnace"], rewards=[item("steel_ingot", 16), xp(4)], frame="goal",
     items=["steel_ingot", "steel_block"])

ch.q("slag", "Slag", "slag",
     "The furnace's by-product. Crush it in an Arc Crusher for two Basic Slag (a +3 fertilizer and an LFP Cathode "
     "ingredient), or spin it in a Fiberizer into Slag Wool for heat cells and the Foundry Suit. Don't throw it out.",
     [have("slag")], deps=["steel_ingot"], rewards=[item("basic_slag", 8), xp(2)], items=["slag", "basic_slag"])

ch.q("wrought_alloy", "Wrought Alloy", "wrought_alloy",
     "The first tier alloy: 2 iron + 1 gold + 1 Coal Coke in the Arcforge Furnace at 1,200°C gives 2 Wrought Alloy. "
     "Every Wrought-tier conduit, tank, cell, crate and portable is made from it. (The Steelmaking chapter builds the "
     "furnace.)",
     [have("wrought_alloy")], deps=["steel_ingot"], rewards=[item("wrought_alloy", 4), xp(3)],
     items=["wrought_alloy"])

ch.q("metal_press", "The Metal Press", "metal_press",
     "Steel, a piston, an anvil and a redstone block. It needs a die: Plate Die (1 ingot → 1 plate), Gear Die (4 → 1 "
     "gear) or Rod Die (1 → 2 rods), each three Steel Ingots and two Steel Blocks round a pressure plate, clock or "
     "lightning rod. 20 FE/t, 100 ticks a press.",
     [placed("metal_press"), have("plate_die", "gear_die", "rod_die", label="Make any die")], deps=["steel_ingot", "combustion_plant"],
     rewards=[item("plate_die", 1), xp(4)], frame="goal", items=["metal_press", "plate_die", "gear_die", "rod_die"])

ch.q("steel_parts", "Plates, Gears and Rods", "steel_plate",
     "Steel Plates armour nearly every machine. Steel Gears drive the Arc Crusher, Assembler, turbines and pumpjack. "
     "Steel Rods make meshes, hammers, Pressure Glass and derrick frames; a plate, rod, plate crafts a Steel Pipe for "
     "the Oil Derrick's drill string. Copper Plates, Gears and Rods fill the cheaper recipes.",
     [have("steel_plate"), have("steel_gear"), have("steel_rod"), have("copper_plate"), have("copper_rod")],
     deps=["metal_press"], rewards=[item("steel_plate", 16), item("steel_gear", 4), xp(4)],
     items=["steel_plate", "steel_gear", "steel_rod", "steel_pipe", "copper_plate", "copper_gear", "copper_rod"])

ch.q("firebox", "Heat First", "firebox",
     "The Firebox doesn't make FE at all: it makes heat (HU), 80 HU/t up to 1,100°C, and pushes it out of its Heat "
     "face. Heat is Arcforge's second currency; boilers, dryers and furnaces all want it. Charcoal leaves Wood Ash "
     "in the ash slot half the time, which farmers will want later.",
     [placed("firebox")], deps=["furnace_bricks"], rewards=[item("minecraft:charcoal", 16), xp(2)], items=["firebox"])

ch.q("thermocouple", "Bismuth Junction", "thermocouple",
     "Two copper rods around a Bismuth Ingot. The Thermocouple turns a temperature difference into electricity and "
     "sits inside the Thermoelectric Plant, its upgrade, the Chemical Reactor and most refinery units. Bismuth comes "
     "from Bismuth Ore between Y 0 and 56; copper rods from the Metal Press (this chapter).",
     [have("thermocouple")], deps=["firebox", "steel_parts"], rewards=[item("bismuth_ingot", 4), xp(2)], items=["thermocouple", "bismuth_ingot"])

ch.q("thermoelectric_plant", "Heat into Power", "thermoelectric_plant",
     "Put a Thermoelectric Plant against a Firebox's Heat face. It takes up to 80 HU/t and converts it at an efficiency "
     "set by its temperature: 0% at 100°C, 100% at 1,100°C. A Firebox gets it close to 64,000 FE per coal, twice the "
     "Combustion Plant. It takes a few minutes to warm up, so be patient.",
     [placed("thermoelectric_plant")],
     deps=["thermocouple"], rewards=[item("thermoelectric_upgrade", 1), xp(3)], frame="goal",
     items=["thermoelectric_plant"])

ch.q("wrought_conduits", "Five Ways to Move Things", "wrought_energy_conduit",
     "Conduits carry one resource each. Wrought Energy Conduit: 256 FE/t. Wrought Item Conduit: 8 items a second. "
     "Wrought Fluid Conduit: 200 mB/t of liquid. Wrought Thermodynamic Conduit: 50 HU/t of heat, hot to cold only. "
     "Wrought Pressurized Conduit: 400 mB/t of gas (steam, hydrogen, oxygen...), the only pipe gases travel in. "
     "Wrench a conduit's end to set it to Insert, Extract or None.",
     [have("wrought_energy_conduit"), have("wrought_item_conduit"), have("wrought_fluid_conduit"),
      have("wrought_thermal_conduit"), have("wrought_pressurized_conduit")],
     deps=["wrought_alloy"], rewards=[item("wrought_energy_conduit", 8), item("wrought_item_conduit", 8), xp(3)],
     items=["wrought_energy_conduit", "wrought_item_conduit", "wrought_fluid_conduit", "wrought_thermal_conduit",
            "wrought_pressurized_conduit"])

ch.q("wrought_energy_cell", "Banked Power", "wrought_energy_cell",
     "A Wrought Energy Cell stores 500,000 FE and passes 512 FE/t. Its slots charge Batteries and FE tools. Set each "
     "face to input or output with the Wrench. Later tiers upgrade in place and keep their charge.",
     [have("wrought_energy_cell")], deps=["wrought_alloy"], rewards=[item("minecraft:redstone", 16), xp(3)],
     items=["wrought_energy_cell"])

ch.q("wrought_fluid_tank", "Hold That Thought", "wrought_fluid_tank",
     "16,000 mB of one liquid, with a bucket slot on each side. The Wrought Fluid Tank is also an ingredient: the "
     "Geothermal Plant, Fuel Burner, Infuser and most refinery units want one inside them.",
     [have("wrought_fluid_tank")], deps=["wrought_alloy"], rewards=[item("minecraft:bucket", 2), xp(2)],
     items=["wrought_fluid_tank"])

ch.q("upgrades", "Upgrade Cards", "speed_upgrade",
     "Every machine has an Upgrades tab taking up to 8 of each card. Speed: 2^(n/2)× faster, same FE per operation. "
     "Energy: FE per operation ×0.8 per card (and more FE per fuel in a Combustion Plant). Thermoelectric Efficiency: "
     "more FE per HU in a Thermoelectric Plant. All three are a Steel Plate and redstone around sugar, copper plates or "
     "thermocouples.",
     [have("speed_upgrade"), have("energy_upgrade"), have("thermoelectric_upgrade")], all_of=False,
     deps=["wrought_energy_cell", "steel_parts"], rewards=[item("speed_upgrade", 2), item("energy_upgrade", 2), xp(3)],
     items=["speed_upgrade", "energy_upgrade", "thermoelectric_upgrade"])

ch.q("powered_up", "Powered Up", "combustion_plant",
     "Generator, cell, conduit, machine: a working grid. From here the book splits into lines: Alloys & Tiers, Ore "
     "Processing and Heat & Power open first.",
     [have("wrought_energy_conduit"), have("wrought_energy_cell"), placed("combustion_plant", "firebox", label="Place a generator")],
     deps=["wrought_conduits", "wrought_energy_cell", "upgrades", "thermoelectric_plant"], rewards=[item("steel_ingot", 8), xp(5)], frame="goal")
