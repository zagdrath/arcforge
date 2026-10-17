from dsl import *

ch = Chapter("logistics", "Logistics & Storage", "arcforged_energy_conduit",
             "Moving and storing everything at every tier: conduits, filters and facades, tanks and cells, crates and "
             "vaults, portables, reservoirs, meters, and the endgame links and tunnels.")

ch.q("tempered_conduits", "Tempered Conduits", "tempered_energy_conduit",
     "Eight Wrought conduits round a Tempered Alloy. Energy 1,024 FE/t, Item 16 per half-second, Fluid 800 mB/t, "
     "Thermodynamic 200 HU/t. Dye any conduit to colour-code a run.",
     [have("tempered_energy_conduit"), have("tempered_item_conduit"), have("tempered_fluid_conduit"), have("tempered_thermal_conduit")],
     rewards=[item("tempered_energy_conduit", 8), xp(4)],
     items=["tempered_energy_conduit", "tempered_item_conduit", "tempered_fluid_conduit", "tempered_thermal_conduit"])

ch.q("hardened_conduits", "Hardened Conduits", "hardened_energy_conduit",
     "Eight Tempered round a Hardened Alloy; fluid adds a PVC Sheet (acid-proof), thermal adds Invar Plates. Energy 8,192 "
     "FE/t, Item 32 per quarter-second, Fluid 3,200 mB/t, Thermodynamic 800 HU/t.",
     [have("hardened_energy_conduit"), have("hardened_item_conduit"), have("hardened_fluid_conduit"), have("hardened_thermal_conduit")],
     deps=["tempered_conduits"], rewards=[item("hardened_energy_conduit", 8), xp(6)],
     items=["hardened_energy_conduit", "hardened_item_conduit", "hardened_fluid_conduit", "hardened_thermal_conduit"])

ch.q("arcforged_conduits", "Arcforged Conduits", "arcforged_energy_conduit",
     "Eight Hardened round Arcforged Alloy and Arcite. Energy 262,144 FE/t, Item 64 every 2 ticks, Fluid 12,800 mB/t, "
     "Thermodynamic 51,200 HU/t. Nothing in the mod out-runs them.",
     [have("arcforged_energy_conduit"), have("arcforged_item_conduit"), have("arcforged_fluid_conduit"), have("arcforged_thermal_conduit")],
     deps=["hardened_conduits"], rewards=[item("arcforged_energy_conduit", 8), xp(10)], frame="goal",
     items=["arcforged_energy_conduit", "arcforged_item_conduit", "arcforged_fluid_conduit", "arcforged_thermal_conduit"])

ch.q("filters_facades", "Filters and Facades", "conduit_filter",
     "A Conduit Filter (plastic, Wrought Alloy, iron bars) goes on one connection of an item, fluid or gas conduit: nine "
     "slots, tags, allow or deny, direction; the Vacuum Collector takes one too. A Conduit Facade (three iron nuggets and a "
     "Steel Plate make six) hides a conduit side behind any full block.",
     [have("conduit_filter"), have("conduit_facade")], all_of=False, deps=["tempered_conduits"],
     rewards=[item("conduit_facade", 12), xp(3)], items=["conduit_filter", "conduit_facade"])

ch.q("fluid_tanks", "Fluid Tanks", "tempered_fluid_tank",
     "Wrought 16,000 mB; Tempered 64,000; Hardened 256,000 (a Rubber Gasket; the Chemical Reactor and Distillation casings "
     "want one); Arcforged 1,024,000 (Arcite, PVC). Each upgrades the one before and keeps its fluid.",
     [have("tempered_fluid_tank", "hardened_fluid_tank", "arcforged_fluid_tank")], rewards=[item("tempered_fluid_tank", 1), xp(4)],
     items=["wrought_fluid_tank", "tempered_fluid_tank", "hardened_fluid_tank", "arcforged_fluid_tank"])

ch.q("energy_cells", "Energy Cells", "tempered_energy_cell",
     "Tempered: 4,000,000 FE at 2,048 FE/t. Hardened: 32,000,000 at 16,384 FE/t. Arcforged: 256,000,000 at 262,144 FE/t. "
     "Upgrade in place with the next alloy and copper plates; the charge stays.",
     [have("tempered_energy_cell", "hardened_energy_cell", "arcforged_energy_cell")], rewards=[item("tempered_energy_cell", 1), xp(4)],
     items=["wrought_energy_cell", "tempered_energy_cell", "hardened_energy_cell", "arcforged_energy_cell"])

ch.q("crates", "Crates", "wrought_crate",
     "A Wrought Crate (Wrought Alloy, planks, chest) is a 54-slot chest with a Sides tab for conduits. Tempered 72 slots, "
     "Hardened 99, Arcforged 126. Craft the upgrade or use a Storage Upgrade on the placed block.",
     [have("wrought_crate", "tempered_crate", "hardened_crate", "arcforged_crate")], rewards=[item("wrought_crate", 1), xp(3)],
     items=["wrought_crate", "tempered_crate", "hardened_crate", "arcforged_crate"])

ch.q("vaults", "Vaults", "wrought_vault",
     "A Vault holds one stackable item in bulk: Wrought 4,096, Tempered 16,384, Hardened 65,536, Arcforged 262,144. Lock "
     "it to its item, void the overflow, click the front to put in and take out.",
     [have("wrought_vault", "tempered_vault", "hardened_vault", "arcforged_vault")], rewards=[item("wrought_vault", 1), xp(3)],
     items=["wrought_vault", "tempered_vault", "hardened_vault", "arcforged_vault"])

ch.q("storage_upgrades", "Storage Upgrades", "tempered_storage_upgrade",
     "Right-click a placed Crate or Vault with a Storage Upgrade of the next tier and it grows in place with everything "
     "still inside. Tempered and Hardened are alloy, plastic and chests; Arcforged adds Arcite and Polypropylene.",
     [have("tempered_storage_upgrade", "hardened_storage_upgrade", "arcforged_storage_upgrade")], deps=["crates", "vaults"],
     rewards=[item("tempered_storage_upgrade", 1), xp(4)],
     items=["tempered_storage_upgrade", "hardened_storage_upgrade", "arcforged_storage_upgrade"])

ch.q("reservoirs", "Reservoirs", "fluid_reservoir",
     "Touching reservoirs merge into one store. Fluid Reservoir (steel rods, glass, bucket): 32,000 mB a block, up to 4,096 "
     "blocks. Gas Reservoir: 64,000 mB a block, Pressurized Conduits only. Item Reservoir (steel, barrel): 20 slots a block in a "
     "solid box up to 3×3×3, with search.",
     [have("fluid_reservoir"), have("gas_reservoir"), have("item_reservoir")], all_of=False,
     rewards=[item("fluid_reservoir", 4), xp(3)], items=["fluid_reservoir", "gas_reservoir", "item_reservoir"])

ch.q("liquid_experience", "Liquid Experience", "liquid_experience_bucket",
     "An XP Drain (iron bars, steel rods, hopper) is a grate on any fluid container: orbs and sneaking players drain into "
     "Liquid Experience at 20 mB a point. An XP Shower (steel plates, copper rod, iron bars) hangs under a block; sneak "
     "under it to take it back. Redstone turns the shower off.",
     [have("xp_drain", "xp_shower", "liquid_experience_bucket")], deps=["reservoirs"], rewards=[item("minecraft:experience_bottle", 8), xp(4)],
     items=["xp_drain", "xp_shower", "liquid_experience_bucket"])

ch.q("batteries", "Batteries", "wrought_battery",
     "A copper rod, Wrought Alloy, a Silver Plate and redstone: 250,000 FE, filled in an Energy Cell's slots, charging Arc "
     "tools. Tempered 1,000,000 (the Chargepad and Tempered Arc tool ingredient), Hardened 4,000,000, Arcforged 16,000,000.",
     [have("wrought_battery", "tempered_battery", "hardened_battery", "arcforged_battery")], deps=["energy_cells"],
     rewards=[item("wrought_battery", 1), xp(3)], items=["wrought_battery", "tempered_battery", "hardened_battery", "arcforged_battery"])

ch.q("portables", "Canisters and Cartridges", "wrought_canister",
     "A Canister (Steel Plate, Wrought Alloy, bucket) carries 16,000 mB of liquid, filled in a Fluid Tank's slot. A Gas "
     "Cartridge (steel rod and plates, Wrought Alloy, bucket) carries 32,000 mB of gas from a Pressurized Cylinder and "
     "refuels Jetpacks. Higher tiers hold 4× each step.",
     [have("wrought_canister", "tempered_canister", "hardened_canister", "arcforged_canister"),
      have("wrought_gas_cartridge", "tempered_gas_cartridge", "hardened_gas_cartridge", "arcforged_gas_cartridge")],
     deps=["fluid_tanks"], rewards=[item("wrought_gas_cartridge", 1), xp(3)],
     items=["wrought_canister", "tempered_canister", "hardened_canister", "arcforged_canister", "wrought_gas_cartridge",
            "tempered_gas_cartridge", "hardened_gas_cartridge", "arcforged_gas_cartridge"])

ch.q("meters", "Meters", "energy_meter",
     "An inline gauge for a conduit run (steel plates, glass panes, comparator, redstone and a Tempered conduit of its kind): "
     "Energy up to 262,144 FE/t, Heat 51,200 HU/t, Fluid 12,800 mB/t, Gas 25,600 mB/t, each giving a redstone signal above or "
     "below a threshold.",
     [have("energy_meter", "heat_meter", "fluid_meter", "gas_meter")], deps=["tempered_conduits"],
     rewards=[item("energy_meter", 1), xp(4)], items=["energy_meter", "heat_meter", "fluid_meter", "gas_meter"])

ch.q("chargepad", "Chargepad", "chargepad",
     "Steel plates and ingots round a Tempered Battery. Stand on it and everything held, in the hotbar or worn charges at "
     "2,048 FE/t each, 8,192 total.",
     [have("chargepad")], deps=["batteries"], rewards=[item("tempered_battery", 1), xp(4)], items=["chargepad"])

ch.q("chunk_loader", "Chunk Loader", "chunk_loader",
     "Steel plates, an eye of ender, redstone and a compass. It keeps a 1×1, 3×3 or 5×5 of chunks loaded so your refinery "
     "runs while you're away; 25 chunks per player.",
     [have("chunk_loader")], deps=["tempered_conduits"], rewards=[item("minecraft:ender_eye", 2), xp(5)], items=["chunk_loader"])

ch.q("link_conduit", "Link Conduit", "link_conduit",
     "Plastic Sheets, copper and an Iolite Crystal. A Link Conduit runs up to 64 blocks from a machine or multiblock face to "
     "a Wired Machine Link.",
     [have("link_conduit")], deps=["hardened_conduits"], rewards=[item("link_conduit", 8), xp(5)], items=["link_conduit"])

ch.q("wired_machine_link", "Wired Machine Link", "wired_machine_link",
     "Steel plates, Iolite Crystals, Hardened Alloy and a Link Conduit: a remote port block. Each of its faces can be set to "
     "any port mode of the machine at the other end of the cable. Link one up.",
     [linked(label="Link a machine")], deps=["link_conduit"], rewards=[item("wired_machine_link", 2), xp(8)], frame="goal",
     items=["wired_machine_link"])

ch.q("quantum_tunnel", "Quantum Tunnel", "quantum_tunnel",
     "Arcforged Alloy, Arcite and ender pearls. Two tunnels on the same frequency share one buffer across any distance "
     "and any dimension: 4,000,000 FE, 400,000 HU, 64,000 mB of liquid, 64,000 mB of gas and 9 item slots.",
     [have("quantum_tunnel")], deps=["arcforged_conduits"], rewards=[item("quantum_tunnel", 2), xp(15)], frame="challenge",
     items=["quantum_tunnel"])

ch.q("quartermaster", "Quartermaster", "arcforged_vault",
     "Every tier of everything that holds or moves, and a base you can wire from anywhere.",
     [have("arcforged_energy_conduit"), have("arcforged_fluid_tank"), have("arcforged_energy_cell"), have("arcforged_vault", "arcforged_crate"),
      have("quantum_tunnel")],
     deps=["quantum_tunnel", "wired_machine_link", "storage_upgrades", "portables", "meters", "liquid_experience", "chargepad", "chunk_loader", "filters_facades"],
     rewards=[item("arcforged_storage_upgrade", 2), xp(20)], frame="challenge")
