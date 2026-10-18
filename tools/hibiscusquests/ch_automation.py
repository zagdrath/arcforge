from dsl import *

ch = Chapter("automation", "Automation", "arc_quarry",
             "Machines that do the chores: crafting, breaking, placing, collecting, felling, and the Arc Quarry that mines "
             "for you.")

ch.q("block_breaker", "Block Breaker", "block_breaker",
     "Wrought Alloy, an iron pickaxe, a Steel Plate and redstone. It breaks the block in front of it for 40 FE/t, as slowly "
     "as the block is hard, with a Pulse redstone mode. Point it at a cobblestone generator or a Tree Cutter's trunk.",
     [placed("block_breaker")], rewards=[item("minecraft:iron_pickaxe", 1), xp(3)], items=["block_breaker"])

ch.q("block_placer", "Block Placer", "block_placer",
     "Wrought Alloy, a dispenser, a Steel Plate and redstone: it places blocks from its nine slots in front of it for 20 FE "
     "each.",
     [placed("block_placer")], rewards=[item("minecraft:dispenser", 1), xp(3)], items=["block_placer"])

ch.q("tree_cutter", "Tree Cutter", "tree_cutter",
     "Steel Plates, an iron axe, saplings, a Block Breaker and Wrought Alloy. It plants and fells a 5×5 tree farm for 200 FE "
     "a log; Range Upgrades (glass panes, spyglasses, redstone, Steel Plate) add a block each side per card.",
     [produced(category="tree_cutting", icon="tree_cutter", label="Fell a tree")], deps=["block_breaker"],
     rewards=[item("range_upgrade", 2), xp(5)], frame="goal", items=["tree_cutter", "range_upgrade"])

ch.q("vacuum_collector", "Vacuum Collector", "vacuum_collector",
     "Tempered Alloy, an ender pearl, hoppers, a Wrought Item Conduit and a Steel Plate. It pulls drops from a 1 to 9 block "
     "cube for 10 FE an item, takes a Conduit Filter, and collects experience orbs as Liquid Experience.",
     [placed("vacuum_collector")], deps=["block_breaker"], rewards=[item("conduit_filter", 1), xp(4)], items=["vacuum_collector"])

ch.q("assembler", "Assembler", "assembler",
     "Tempered Alloy, a crafting table, steel gears, redstone and steel plates. Set a 3×3 pattern, feed its 18-slot buffer "
     "through conduits, and it crafts for 400 FE a time. Automate plates into casings, dust into blocks, anything.",
     [placed("assembler")], deps=["block_placer"], rewards=[item("steel_gear", 8), xp(5)], frame="goal", items=["assembler"])

ch.q("quarry_parts", "Scanner and Drill Head", "quarry_scanner",
     "A Quarry Scanner is steel plates, a Silver Plate, glass panes, redstone and a comparator (also the core of the Vein "
     "Mining Module). A Tungsten Drill Head is four Tungsten Plates round an Arcite-Tungsten Composite.",
     [have("quarry_scanner"), have("tungsten_drill_head")], deps=["assembler"], rewards=[item("tungsten_plate", 8), xp(6)],
     items=["quarry_scanner", "tungsten_drill_head"])

ch.q("arc_quarry", "Arc Quarry", "arc_quarry",
     "Hardened Alloy, a Quarry Scanner, steel gears and a Tungsten Drill Head. A 3×3×3 digital miner: radius up to 32, Y 0 "
     "to 60 by default, a filter for what it wants, Silk Touch, and Replace to leave the ground whole. 200 FE a block, and "
     "a toast when it finishes.",
     [placed("arc_quarry")], deps=["quarry_parts"], rewards=[item("hardened_energy_cell", 1), xp(12)], frame="challenge",
     items=["arc_quarry"])

ch.q("experience_banking", "Experience Without Mobs", "liquid_experience_bucket",
     "The Induction Furnace (and its Array), the Arcforge Furnace and the Block Breaker bank the experience their work gives "
     "as Liquid Experience, 20 mB a point. Set a face or port to Experience and pipe it off; a full tank only stops the "
     "banking.",
     [have("liquid_experience_bucket", label="Bottle some banked experience")], deps=["block_breaker"],
     rewards=[item("minecraft:experience_bottle", 4), xp(4)])

ch.q("electric_enchanter", "Electric Enchanter", "electric_enchanter",
     "Tempered Alloy, a book, diamonds, an enchanting table and a Wrought Fluid Tank. Pick a target in its GUI and it "
     "enchants anything fed to it with FE, Lapis and Liquid Experience: each level costs Lapis equal to the level and that "
     "level's vanilla cost in experience. Top-ups pay only for what they gain.",
     [placed("electric_enchanter")], deps=["experience_banking", "vacuum_collector"],
     rewards=[item("minecraft:lapis_lazuli", 32), xp(10)], frame="goal", items=["electric_enchanter"])

ch.q("automated", "Automated", "assembler",
     "Breaker, placer, cutter, collector, assembler, quarry: a base that runs itself.",
     [placed("block_breaker"), placed("block_placer"), placed("vacuum_collector"), placed("assembler"), placed("arc_quarry"),
      produced(category="tree_cutting", icon="tree_cutter", label="Fell a tree")],
     deps=["arc_quarry", "tree_cutter", "vacuum_collector"], rewards=[item("arcforged_alloy", 4), xp(15)], frame="challenge")
