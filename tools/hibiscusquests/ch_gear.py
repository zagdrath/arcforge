from dsl import *

ch = Chapter("gear", "Tools & Gear", "arcforged_arc_drill",
             "What you wear and carry: steel tools and armour, the Foundry Suit, three tiers of Jetpack, Arc Drill and Arc "
             "Saw, and the modules that tune them.")

ch.q("steel_tools", "Steel Tools", "steel_pickaxe",
     "Steel crafts like iron: Sword, Pickaxe (500 uses, speed 7), Axe, Shovel and Hoe, repaired with steel. Make one.",
     [have("steel_sword", "steel_pickaxe", "steel_axe", "steel_shovel", "steel_hoe")], rewards=[item("steel_ingot", 8), xp(3)],
     items=["steel_sword", "steel_pickaxe", "steel_axe", "steel_shovel", "steel_hoe"])

ch.q("steel_armour", "Steel Armour", "steel_chestplate",
     "Helmet 2, Chestplate 7, Leggings 5, Boots 2, with 1.0 toughness each. The Chestplate can be smithed onto a Jetpack "
     "for an Armored Jetpack.",
     [have("steel_helmet"), have("steel_chestplate"), have("steel_leggings"), have("steel_boots")], deps=["steel_tools"],
     rewards=[item("steel_ingot", 16), xp(5)], frame="goal", items=["steel_helmet", "steel_chestplate", "steel_leggings", "steel_boots"])

ch.q("hammer", "Hammer and Excavator", "steel_hammer",
     "Three Steel Blocks on two Steel Rods make a Steel Hammer: a 3×3 pickaxe, 1,500 uses at 70% speed, and the core of "
     "the Area Module. One block on two rods is the Steel Excavator, a 3×3 shovel.",
     [have("steel_hammer", "steel_excavator")], deps=["steel_tools"], rewards=[item("steel_block", 2), xp(4)],
     items=["steel_hammer", "steel_excavator"])

ch.q("foundry_suit", "Foundry Suit", "foundry_chestplate",
     "Rock Wool, Slag Wool and Steel Plates. Each piece cuts fire and magma damage 25%; the full set makes you fire immune "
     "with an 8-second lava shield, and Rock Wool repairs it. Built for walking on Geothermal lava.",
     [have("foundry_helmet"), have("foundry_chestplate"), have("foundry_leggings"), have("foundry_boots")], deps=["steel_armour"],
     rewards=[item("rock_wool", 8), xp(8)], frame="goal", items=["foundry_helmet", "foundry_chestplate", "foundry_leggings", "foundry_boots"])

ch.q("jetpack", "Tempered Jetpack", "tempered_jetpack",
     "Plastic, Tempered Alloy, two Tempered Gas Cartridges, Wrought Pressurized Conduits and a Rubber Gasket. 16,000 mB of "
     "Steam (any grade), Hydrogen or Kerosene, in Normal, Hover or Off. Fill it from a Pressurized Cylinder or a Gas "
     "Cartridge.",
     [have("tempered_jetpack")], deps=["steel_armour"], rewards=[item("tempered_gas_cartridge", 1), xp(8)], frame="goal",
     items=["tempered_jetpack"])

ch.q("better_jetpacks", "Higher, Faster", "arcforged_jetpack",
     "Upgrade the Tempered Jetpack with Hardened Alloy, plastic and Hardened Gas Cartridges for 64,000 mB and a faster climb, "
     "then with Arcforged Alloy, Arcite, Polypropylene and Arcforged Gas Cartridges for 256,000 mB.",
     [have("hardened_jetpack", "arcforged_jetpack")], deps=["jetpack"], rewards=[item("kerosene_bucket", 4), xp(10)],
     items=["hardened_jetpack", "arcforged_jetpack"])

ch.q("arc_tools", "Arc Drill and Arc Saw", "tempered_arc_drill",
     "The Tempered Arc Drill (tungsten, Tempered Alloy, steel gears, plastic, a Tempered Battery) is an FE pickaxe and "
     "shovel: 100,000 FE, diamond level, two module slots. The Tempered Arc Saw is the axe, with a Felling toggle for whole "
     "trunks.",
     [have("tempered_arc_drill", "tempered_arc_saw")], deps=["hammer"], rewards=[item("tempered_battery", 1), xp(8)], frame="goal",
     items=["tempered_arc_drill", "tempered_arc_saw"])

ch.q("modules", "Tool Modules", "area_module",
     "Plastic and redstone around a core, slotted into an Arc tool. Area (Steel Hammer): 3×3, +25% FE. Silk Touch (emerald): "
     "+100%. Fortune I (lapis block) +50%, II (diamonds) +100%, III (Arcite) +150%. Vein Mining (Quarry Scanner): 64 ores or "
     "a whole tree. Speed (Speed Upgrade): digs 50% faster.",
     [have("area_module", "silk_touch_module", "fortune_i_module", "fortune_ii_module", "fortune_iii_module", "vein_module", "speed_module")],
     deps=["arc_tools"], rewards=[item("speed_module", 1), xp(5)],
     items=["area_module", "silk_touch_module", "fortune_i_module", "fortune_ii_module", "fortune_iii_module", "vein_module", "speed_module"])

ch.q("hardened_tools", "Hardened Tools", "hardened_arc_drill",
     "Upgrade with Hardened Alloy, plastic and a Hardened Battery: 400,000 FE, netherite level, three module slots.",
     [have("hardened_arc_drill", "hardened_arc_saw")], deps=["arc_tools"], rewards=[item("hardened_battery", 1), xp(8)],
     items=["hardened_arc_drill", "hardened_arc_saw"])

ch.q("arcforged_tools", "Arcforged Tools", "arcforged_arc_drill",
     "Arcforged Alloy, Arcite, a Plastic Sheet and an Arcforged Battery: 1,600,000 FE, speed 14, four slots, and the Area "
     "Module digs 5×5.",
     [have("arcforged_arc_drill", "arcforged_arc_saw")], deps=["hardened_tools"], rewards=[item("fortune_iii_module", 1), xp(15)],
     frame="challenge", items=["arcforged_arc_drill", "arcforged_arc_saw"])

ch.q("fully_equipped", "Fully Equipped", "arcforged_jetpack",
     "Foundry Suit, Arcforged Jetpack, Arcforged Drill and Saw with a full rack of modules.",
     [have("foundry_chestplate"), have("arcforged_jetpack"), have("arcforged_arc_drill"), have("arcforged_arc_saw"), have("fortune_iii_module")],
     deps=["foundry_suit", "better_jetpacks", "arcforged_tools", "modules"], rewards=[item("arcforged_battery", 1), xp(20)], frame="challenge")
