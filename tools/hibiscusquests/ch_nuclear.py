from dsl import *

ch = Chapter("nuclear", "Nuclear Fuel", "yellowcake",
             "Civil nuclear chemistry, enrichment capped at 20%: uraninite to yellowcake to hexafluoride, the Gas Centrifuge "
             "Cascade, lead and depleted-uranium shielding, and the instruments that keep you alive.")

ch.q("uraninite", "Uraninite", "raw_uraninite",
     "Uraninite Ore is rare (two veins of four, Y -64..24) and radioactive: Raw Uraninite gives 0.1 mSv/h in your pocket, "
     "a block of nine 0.9. It never smelts; an Arc Crusher makes Uraninite Dust (two per ore, four in the array).",
     [have("raw_uraninite")], rewards=[item("lead_ingot", 8), xp(5)], frame="goal",
     items=["uraninite_ore", "deepslate_uraninite_ore", "raw_uraninite", "raw_uraninite_block", "uraninite_dust"])

ch.q("geiger", "Geiger Counter", "geiger_counter",
     "Steel plates, a glass pane, redstone and copper. Held, it shows the dose rate in mSv/h and clicks faster near sources. "
     "The Dosimeter (glass pane, redstone, Steel Plate) keeps a running total of your dose and warns at each threshold.",
     [have("geiger_counter"), have("dosimeter")], all_of=False, deps=["uraninite"], rewards=[item("dosimeter", 1), xp(4)],
     items=["geiger_counter", "dosimeter"])

ch.q("lead", "Lead", "lead_ingot",
     "Galena Ore (Y -16..64) drops Raw Galena, which smelts to Lead Ingots; crushing gives Lead Dust and a 10% chance of "
     "Silver Dust. Lead is the shielding metal: a Block of Lead lets 10% of radiation through, Lead Plates build the "
     "cascade casings.",
     [have("lead_ingot")], deps=["uraninite"], rewards=[item("lead_ingot", 16), xp(4)],
     items=["galena_ore", "deepslate_galena_ore", "raw_galena", "raw_galena_block", "lead_dust", "lead_ingot", "lead_block", "lead_plate"])

ch.q("shielding", "Shielding", "lead_bricks",
     "A Lead Box (eight shielding metal round a chest) is a nine-slot box no radiation leaves: carry ore in it. Lead Bricks "
     "(lead and bricks) let 10% through; Concrete Shielding (concrete and Steel Rods) 25%. Build walls around the hot room.",
     [have("lead_box"), have("lead_bricks", "concrete_shielding", label="Make a shielding block")], deps=["lead"],
     rewards=[item("lead_bricks", 16), xp(5)], items=["lead_box", "lead_bricks", "concrete_shielding"])

ch.q("hazmat", "Hazmat Suit", "hazmat_chestplate",
     "Rubber and shielding metal (glass pane for the hood): Hood, Suit, Trousers and Boots. The full set blocks 95% of "
     "radiation.",
     [have("hazmat_helmet"), have("hazmat_chestplate"), have("hazmat_leggings"), have("hazmat_boots")], deps=["geiger"],
     rewards=[item("rubber", 16), xp(8)], frame="goal", items=["hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "hazmat_boots"])

ch.q("heavy_minerals", "Heavy Mineral Sand", "heavy_mineral_sand",
     "Patches in beach, river and desert sand and gravel (Y 40..90), dug with a shovel. The Sifter gives sand back plus "
     "Monazite (30%, slightly radioactive), Zircon (40%) and Iron Dust (15%). Crushed, Monazite Dust and Zircon Dust are "
     "set aside for the reactors to come.",
     [have("monazite"), have("zircon")], deps=["uraninite"], rewards=[item("heavy_mineral_sand", 16), xp(5)],
     items=["heavy_mineral_sand", "monazite", "monazite_dust", "zircon", "zircon_dust"])

ch.q("uranyl", "Uranyl Sulfate", "uranyl_sulfate_solution_bucket",
     "Uraninite Dust and 250 mB of Sulfuric Acid leach into 250 mB of Uranyl Sulfate Solution.",
     [produced(fluid="uranyl_sulfate_solution", icon="uranyl_sulfate_solution_bucket", label="Leach uranium")], deps=["uraninite"],
     rewards=[item("sulfuric_acid_bucket", 2), xp(6)], items=["uranyl_sulfate_solution_bucket"])

ch.q("yellowcake", "Yellowcake", "yellowcake",
     "250 mB of the solution and 50 mB of Ammonia precipitate Ammonium Diuranate; calcine it in an Induction Furnace (or "
     "any furnace) to Yellowcake.",
     [have("ammonium_diuranate"), have("yellowcake")], deps=["uranyl"], rewards=[item("yellowcake", 4), xp(8)], frame="goal",
     items=["ammonium_diuranate", "yellowcake"])

ch.q("hydrofluoric_acid", "Hydrofluoric Acid", "hydrofluoric_acid_bucket",
     "Fluorite Dust and 500 mB of Sulfuric Acid give 500 mB of Hydrofluoric Acid. It fluorinates uranium, and the Electrolyzer "
     "splits it into Hydrogen and Fluorine.",
     [produced(fluid="hydrofluoric_acid", icon="hydrofluoric_acid_bucket", label="Make Hydrofluoric Acid")], deps=["uraninite"],
     rewards=[item("fluorite_dust", 8), xp(6)], items=["hydrofluoric_acid_bucket"])

ch.q("conversion", "Conversion", "uranium_tetrafluoride",
     "Yellowcake and 100 mB of Hydrogen make Uranium Dioxide. UO2 and 400 mB of Hydrofluoric Acid make Uranium "
     "Tetrafluoride. UF4 and 100 mB of Fluorine make 100 mB of Uranium Hexafluoride at natural 0.711% U-235, a gas that only a "
     "PVC-lined cylinder will hold.",
     [have("uranium_dioxide"), have("uranium_tetrafluoride"),
      produced(fluid="uranium_hexafluoride", icon="uranium_tetrafluoride", label="Make Uranium Hexafluoride")],
     deps=["yellowcake", "hydrofluoric_acid"], rewards=[item("pvc_sheet", 8), xp(12)], frame="goal",
     items=["uranium_dioxide", "uranium_tetrafluoride"])

ch.q("gas_centrifuge", "Gas Centrifuge", "gas_centrifuge",
     "Steel plates, Carbon Fiber, tungsten rods, PVC Sheets and a copper gear: a two-block-tall centrifuge doing 0.02 SWU/t "
     "for 40 FE/t. Centrifuge Cascade Casings (steel plates, Lead Plates, steel gear) make the floor it stands on; the "
     "Controller is a casing with a glass pane and redstone.",
     [have("gas_centrifuge"), have("centrifuge_cascade_casing"), have("centrifuge_cascade_controller")], deps=["conversion"],
     rewards=[item("carbon_fiber", 8), xp(8)], items=["gas_centrifuge", "centrifuge_cascade_casing", "centrifuge_cascade_controller"])

ch.q("cascade", "Centrifuge Cascade", "centrifuge_cascade_controller",
     "A floor 3×3 to 7×7 of casings with Gas Centrifuges standing on it and the Controller in the edge. Input, Output, Tails "
     "and Energy ports on the outer faces. It enriches UF6 to 1–20% U-235 and leaves tails at 0.1–0.5%.",
     [formed("centrifuge_cascade", icon="centrifuge_cascade_controller")], deps=["gas_centrifuge"],
     rewards=[item("centrifuge_cascade_casing", 8), xp(10)], frame="goal")

ch.q("enriched", "Enriched", "centrifuge_cascade_controller",
     "Draw enriched Uranium Hexafluoride from the cascade's Output port.",
     [produced(category="enrichment", icon="centrifuge_cascade_controller", label="Enrich UF6")], deps=["cascade"],
     rewards=[item("gas_centrifuge", 2), xp(12)], frame="goal")

ch.q("full_hall", "Full Hall", "gas_centrifuge",
     "A 7×7 cascade floor carrying 24 Gas Centrifuges.",
     [formed("centrifuge_cascade", min_length=24, icon="centrifuge_cascade_controller")], deps=["cascade"],
     rewards=[item("arcforged_alloy", 8), xp(20)], frame="challenge", hidden=True)

ch.q("depleted_uranium", "Depleted Uranium", "depleted_uranium_ingot",
     "100 mB of depleted hexafluoride, 100 Hydrogen and 100 Steam give Depleted Uranium Dust (and 200 mB of Hydrofluoric Acid "
     "back). Blast it to Depleted Uranium Ingots, which stand in for lead in every shielding recipe; a Block of Depleted "
     "Uranium is the best shield there is, letting 5% through, and glows very faintly.",
     [have("depleted_uranium_ingot")], deps=["enriched"], rewards=[item("depleted_uranium_ingot", 8), xp(12)], frame="goal",
     items=["depleted_uranium_dust", "depleted_uranium_ingot", "depleted_uranium_block"])

ch.q("nuclear_engineer", "Nuclear Engineer", "yellowcake",
     "Dressed, shielded, enriched. The reactors come next.",
     [have("hazmat_chestplate"), have("depleted_uranium_block"), produced(category="enrichment", icon="centrifuge_cascade_controller", label="Enrich UF6")],
     deps=["depleted_uranium", "hazmat", "shielding", "heavy_minerals"], rewards=[item("arcforged_energy_cell", 1), xp(25)], frame="challenge")
