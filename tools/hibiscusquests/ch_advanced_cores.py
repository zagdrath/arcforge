from dsl import *

ch = Chapter("advanced_cores", "Advanced Cores", "lead_bismuth_ingot",
             "Two new core types in the same vessel: the lead-bismuth cooled fast reactor, with no moderator, and the molten "
             "salt reactor, whose fuel is dissolved in its coolant and drains away through a Freeze Plug when it overheats.")

ch.q("lead_bismuth", "Lead-Bismuth", "lead_bismuth_ingot",
     "Two Lead and two Bismuth Ingots smelted together in the Arcforge Furnace make four Lead-Bismuth Ingots; melt them "
     "in the Arc Melter for Lead-Bismuth Eutectic. It freezes below 125°C, so keep the core warm.",
     [have("lead_bismuth_ingot")], rewards=[item("bismuth_ingot", 8), xp(6)], items=["lead_bismuth_ingot"])

ch.q("fast_reactor", "Fast Reactor", "lead_bismuth_ingot",
     "Fill the vessel with Lead-Bismuth Eutectic instead of water and leave out the graphite, so the neutrons stay fast. "
     "Fuel it with 15 to 20% enriched rods or MOX, with Thorium Blanket Rods round the edge to breed. It runs at 900°C "
     "and more at low pressure; hot lead-bismuth goes to a Steam Generator for Superheated Steam.",
     [produced(category="fast_reactor", icon="reactor_controller", label="Run a lead-bismuth fast reactor at power")],
     deps=["lead_bismuth"], rewards=[item("arcforged_alloy", 4), xp(20)], frame="goal")

ch.q("fluoride_salts", "Fluoride Salts", "lithium_fluoride",
     "Lithium Hydroxide and Hydrofluoric Acid make Lithium Fluoride; Thorium Dioxide and Hydrofluoric Acid make Thorium "
     "Tetrafluoride. Both in the Chemical Reactor.",
     [have("lithium_fluoride"), have("thorium_tetrafluoride")], deps=["fuel_cycle/thorium"],
     rewards=[item("lithium_hydroxide", 8), xp(8)], items=["lithium_fluoride", "thorium_tetrafluoride"])

ch.q("fuel_salt", "Fuel Salt", "thorium_tetrafluoride",
     "One Uranium Tetrafluoride (its enrichment carried in), four Lithium Fluoride and two Thorium Tetrafluoride give "
     "1,000 mB of Fuel Salt in the Chemical Reactor.",
     [produced(fluid="fuel_salt", icon="thorium_tetrafluoride", label="Make Fuel Salt")], deps=["fluoride_salts"],
     rewards=[item("lithium_fluoride", 8), xp(8)])

ch.q("freeze_plug", "Freeze Plug", "freeze_plug",
     "A Freeze Plug (nickel plates, Steel Pipe and packed ice) under the vessel's floor, a Drain Tank under it. Powered "
     "and cool, the plug stays frozen; if the salt overheats or the power goes, it melts and the salt drains into the "
     "shielded tank, and the core shuts down by itself.",
     [have("freeze_plug"), have("drain_tank")], deps=["fuel_salt"], rewards=[item("minecraft:packed_ice", 8), xp(8)],
     items=["freeze_plug", "drain_tank"])

ch.q("salt_processing", "Online Cleanup", "salt_processing_unit",
     "Fission products build up in the salt. Salt Processing Units (a Chemical Reactor in steel and pipe) touching the "
     "vessel take them out while it runs, using FE, as High-Level Liquid Waste.",
     [have("salt_processing_unit")], deps=["fuel_salt"], rewards=[item("steel_pipe", 8), xp(8)],
     items=["salt_processing_unit"])

ch.q("molten_salt_reactor", "Molten Salt Reactor", "salt_processing_unit",
     "Only Control Rod Channels go in a salt core: build enough of them, in graphite, to hold it subcritical with every "
     "rod in before you fill it (about one channel in three). It runs at about 700°C; hot Fuel Salt goes to a Steam "
     "Generator for High-Pressure Steam.",
     [produced(category="molten_salt_reactor", icon="reactor_controller", label="Run a molten salt reactor at power")],
     deps=["freeze_plug"], rewards=[item("arcforged_alloy", 4), xp(20)], frame="goal")

ch.q("walk_away", "Walk-Away Safe", "freeze_plug",
     "Let a salt core overheat and watch the Freeze Plug do its job: the salt drains into the tank and the core shuts "
     "itself down. It refreezes once it's powered and cool again.",
     [produced(category="freeze_plug", icon="freeze_plug", label="Have a Freeze Plug drain an overheating core")],
     deps=["molten_salt_reactor"], rewards=[item("arcforged_alloy", 4), xp(20)], frame="challenge", hidden=True)
