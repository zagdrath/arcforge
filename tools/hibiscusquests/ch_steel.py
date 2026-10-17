from dsl import *

ch = Chapter("steelmaking", "Alloys & Tiers", "tempered_alloy",
             "The four tier alloys that gate every other line: Wrought (Getting Started), then Tempered, Hardened and "
             "Arcforged, all from the Arcforge Furnace at rising temperatures, plus Graphite.")

ch.q("tempered_alloy", "Tempered Alloy", "tempered_alloy",
     "2 steel + 2 copper + 1 coke at 1,300°C gives 2. Tempered is the second tier: every 3×3×3 processing array casing, "
     "the Steam Boiler and Turbine, the Electrolyzer, Fermenter, Assembler and Arc Melter are made from it, and every "
     "Wrought block upgrades to Tempered by crafting it with Tempered Alloy, keeping its contents.",
     [have("tempered_alloy")], deps=["getting_started/steel_ingot"], rewards=[item("tempered_alloy", 8), xp(5)], frame="goal",
     items=["tempered_alloy"])

ch.q("hardened_alloy", "Hardened Alloy", "hardened_alloy",
     "2 steel + 2 amethyst shards + 1 Nickel Plate + 2 coke at 1,400°C gives 2. Hardened gates the Distillation Array, "
     "Superheater, Gas Turbine, Chemical Reactor, Diamond Press and Arc Quarry: most of the mid-game. Nickel Plates "
     "come from the Metal Press; amethyst from geodes.",
     [have("hardened_alloy")], deps=["tempered_alloy"], rewards=[item("hardened_alloy", 8), xp(8)], frame="goal",
     items=["hardened_alloy"])

ch.q("graphite", "Graphite", "graphite",
     "Run the furnace up to 1,500°C with one Coal Coke or two Carbon Dust in the metal slot and it comes out as Graphite. "
     "Press it into Graphite Anodes for lithium cells, or squeeze four in a Diamond Press into a Diamond. Nine make a "
     "Block of Graphite.",
     [have("graphite")], deps=["hardened_alloy"], rewards=[item("graphite", 8), xp(5)],
     items=["graphite", "graphite_block"])

ch.q("arcforged_alloy", "Arcforged Alloy", "arcforged_alloy",
     "The top tier: 2 Hardened Alloy + 2 Carbon Fiber + 1 Arcite-Tungsten Composite + 2 coke at 1,500°C gives 2. "
     "Carbon Fiber is Pitch from a creosote or crude distillation spun in a Fiberizer; the Composite is a Tungsten Plate "
     "with two Arcite Crystals from below Y -40. Arcforged conduits, cells, the Quantum Tunnel and Heat Pipe need it.",
     [have("arcforged_alloy")], deps=["graphite"], rewards=[item("arcforged_alloy", 8), xp(12)], frame="challenge",
     items=["arcforged_alloy", "carbon_fiber", "arcite_tungsten_composite"])

ch.q("steelworks", "Steelworks", "steel_block",
     "Carbonizer, furnace, every alloy. Nothing in Arcforge is out of reach now; it's a matter of building it.",
     [have("steel_block"), have("wrought_alloy"), have("tempered_alloy"), have("hardened_alloy"), have("arcforged_alloy")],
     deps=["arcforged_alloy", "getting_started/slag"], rewards=[item("steel_block", 4), item("speed_upgrade", 4), xp(15)], frame="challenge")
