from dsl import *

ch = Chapter("fuel_cycle", "Fuel Cycle", "purex_controller",
             "What happens to a fuel rod after the reactor: the Spent Fuel Pool and dry casks, PUREX reprocessing with "
             "Nitric Acid, MOX fuel from recovered plutonium, thorium breeding and U-233, and storing the waste for good.")

ch.q("spent_fuel_pool", "Spent Fuel Pool", "spent_fuel_rack",
     "Spent Fuel Racks (steel rods and plates) hold four rods each and must sit underwater with at least 3 blocks of "
     "water above them: the water cools and shields the rods, which glow Cherenkov blue while they're hot. If the pool "
     "drains, they heat up and their cladding fails.",
     [have("spent_fuel_rack"), produced(category="spent_fuel_pool", icon="spent_fuel_rack", label="Cool spent rods in the pool")],
     rewards=[item("steel_plate", 16), xp(8)], items=["spent_fuel_rack"])

ch.q("dry_cask", "Dry Cask Storage", "dry_cask",
     "Once a rod's decay heat has fallen (its tooltip shows it), a Dry Cask (Containment Concrete round a Block of "
     "Steel) holds seven and shields them anywhere. Broken, it keeps its rods.",
     [have("dry_cask")], deps=["spent_fuel_pool", "reactor_safety/containment_materials"],
     rewards=[item("containment_concrete", 8), xp(6)], items=["dry_cask"])

ch.q("nitric_acid", "Nitric Acid", "nitric_acid_bucket",
     "The Ostwald process in the Chemical Reactor: 100 mB of Ammonia burnt in 200 mB of Oxygen gives Nitrogen Dioxide; "
     "150 mB of it in 50 mB of Water gives 100 mB of Nitric Acid.",
     [produced(fluid="nitric_acid", icon="nitric_acid_bucket", label="Make Nitric Acid")],
     rewards=[item("minecraft:bucket", 2), xp(6)], items=["nitric_acid_bucket"])

ch.q("hot_cell", "The Hot Cell", "hot_cell_casing",
     "Hot Cell Casing is steel plates, shielding metal and Containment Concrete; Hot Cell Windows are lead glass. Build "
     "an empty box 3 to 9 across and 3 to 6 tall, windows in its walls and the PUREX controller in one.",
     [have("hot_cell_casing"), have("hot_cell_window"), have("purex_controller")],
     deps=["reactor_safety/containment_materials"], rewards=[item("lead_plate", 8), xp(8)],
     items=["hot_cell_casing", "hot_cell_window", "purex_controller"])

ch.q("purex", "PUREX", "purex_controller",
     "Form the PUREX Plant and feed it spent rods, cooled until their decay heat has fallen. Each is dissolved in 500 mB "
     "of Nitric Acid and separated with 100 mB of Kerosene, using 200 FE/t, into Recovered Uranium (back to UF6 and the "
     "Cascade), Plutonium Oxide and High-Level Liquid Waste.",
     [formed("purex_plant", icon="purex_controller", label="Form the PUREX Plant"),
      produced(category="purex", icon="plutonium_oxide", label="Reprocess a spent rod")],
     deps=["hot_cell", "nitric_acid", "spent_fuel_pool"], rewards=[item("hardened_alloy", 8), xp(15)], frame="goal",
     items=["recovered_uranium", "plutonium_oxide"])

ch.q("mox", "MOX Fuel", "mox_fuel_rod",
     "Plutonium only ever becomes fuel. Depleted Uranium Dust and Oxygen make Depleted Uranium Dioxide; five Plutonium "
     "Oxide and three of it make two MOX Blend. Press it with the Pellet Die, sinter it and rod it in a Zircaloy Tube. "
     "MOX rods run like uranium ones, and in the fast reactor.",
     [have("mox_fuel_rod")], deps=["purex"], rewards=[item("zircaloy_tube", 4), xp(12)],
     items=["depleted_uranium_dioxide", "mox_blend", "green_mox_pellet", "mox_fuel_pellet", "mox_fuel_rod"])

ch.q("vitrification", "Vitrification", "vitrified_waste_log",
     "The Vitrification Plant melts 1,000 mB of High-Level Liquid Waste with four glass blocks into a Vitrified Waste "
     "Log in a steel canister. Logs are radioactive and warm; their activity halves every 30 in-game days.",
     [produced(category="vitrifying", icon="vitrification_plant", label="Vitrify High-Level Liquid Waste")],
     deps=["purex"], rewards=[item("minecraft:glass", 16), xp(10)], items=["vitrification_plant", "vitrified_waste_log"])

ch.q("repository", "Deep Storage", "geological_repository",
     "A Geological Repository is a vault below Y -40, set in deepslate. It takes Vitrified Waste Logs and Dry Casks and "
     "stores them safely, showing what it holds.",
     [produced(category="repository", icon="geological_repository", label="Store waste in a Geological Repository")],
     deps=["vitrification"], rewards=[item("lead_block", 4), xp(15)], frame="goal", items=["geological_repository"])

ch.q("compacting", "Low-Level Waste", "low_level_waste_drum",
     "Contaminated items, used hazmat pieces and used HEPA Filters go into the Waste Compactor with an empty Waste Drum "
     "(steel plates), which comes out a Low-Level Waste Drum. Store drums anywhere shielded.",
     [produced(category="compacting", icon="waste_compactor", label="Compact low-level waste into a drum")],
     rewards=[item("waste_drum", 4), xp(6)], items=["waste_compactor", "waste_drum", "low_level_waste_drum"])

ch.q("thorium", "Thorium", "thorium_dioxide",
     "Monazite Dust cracked in 250 mB of Lye in the Chemical Reactor gives Thorium Dioxide, and usually Rare Earth "
     "Dust.",
     [have("thorium_dioxide")], rewards=[item("monazite_dust", 8), xp(6)], items=["thorium_dioxide", "rare_earth_dust"])

ch.q("blanket", "Breeding Blanket", "thorium_blanket_rod",
     "Press Thorium Dioxide into pellets, sinter them and rod them in a Zircaloy Tube. Thorium Blanket Rods hardly "
     "fission themselves: in the core, thorium absorbs neutrons and breeds Uranium-233.",
     [have("thorium_blanket_rod")], deps=["thorium"], rewards=[item("zircaloy_tube", 4), xp(8)],
     items=["green_thorium_pellet", "thorium_pellet", "thorium_blanket_rod"])

ch.q("thorex", "THOREX", "uranium_233_oxide",
     "Switch the PUREX Plant to THOREX: spent blanket rods give Thorium Dioxide back, and Uranium-233 Oxide.",
     [produced(category="thorex", icon="uranium_233_oxide", label="Separate Uranium-233 from a blanket rod")],
     deps=["blanket", "purex"], rewards=[item("thorium_dioxide", 6), xp(10)], items=["uranium_233_oxide"])

ch.q("u233", "U-233 Fuel", "u233_fuel_rod",
     "One Uranium-233 Oxide and three Thorium Dioxide make four U-233 Fuel Blend: pressed, sintered and rodded into "
     "U-233 Fuel Rods, which breed more U-233 as they burn.",
     [have("u233_fuel_rod")], deps=["thorex"], rewards=[item("arcforged_alloy", 2), xp(15)], frame="goal",
     items=["u233_fuel_blend", "green_u233_pellet", "u233_fuel_pellet", "u233_fuel_rod"])

ch.q("closed_cycle", "Closing the Cycle", "geological_repository",
     "Reprocess, refuel with MOX and U-233, and put the waste away for good: nothing left lying about.",
     [have("mox_fuel_rod"), have("u233_fuel_rod"),
      produced(category="repository", icon="geological_repository", label="Store waste in a Geological Repository")],
     deps=["mox", "u233", "repository"], rewards=[item("arcforged_alloy", 8), xp(25)], frame="challenge")
