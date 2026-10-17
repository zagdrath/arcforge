from dsl import *

ch = Chapter("nuclear_power", "Nuclear Power", "reactor_controller",
             "From enriched uranium to a running reactor: zircaloy cladding, fuel pellets and rods, control rods and "
             "moderators, the Fission Reactor, its Steam Generator and the Heavy Water Tower. Civil power only.")

ch.q("zirconium", "Zirconium", "zirconium_ingot",
     "Zircon Dust (from Heavy Mineral Sand), Chlorine and Coal Coke react into Zirconium Tetrachloride; reduce it with "
     "Hydrogen for Zirconium Dust, and smelt that to Zirconium Ingots. Zirconium barely absorbs neutrons, which is why "
     "it clads the fuel.",
     [have("zirconium_tetrachloride"), have("zirconium_ingot")], deps=["nuclear/heavy_minerals"],
     rewards=[item("zircon_dust", 8), xp(6)], items=["zirconium_tetrachloride", "zirconium_dust", "zirconium_ingot"])

ch.q("zircaloy", "Zircaloy", "zircaloy_tube",
     "Four Zirconium Ingots and a Nickel Ingot in the Arcforge Furnace at 1,400°C make Zircaloy. Press it with the Rod "
     "Die into Zircaloy Tubes: the cladding of every fuel rod and the lining of every Fuel Channel.",
     [have("zircaloy_ingot"), have("zircaloy_tube")], deps=["zirconium"], rewards=[item("nickel_ingot", 4), xp(6)],
     items=["zircaloy_ingot", "zircaloy_tube"])

ch.q("pellets", "Fuel Pellets", "fuel_pellet",
     "Make a Pellet Die (steel round a Steel Gear and a Block of Steel). The Metal Press turns Uranium Dioxide into green "
     "pellets, and sintering bakes them into Fuel Pellets. Each pellet keeps the enrichment of the uranium it came from.",
     [have("pellet_die"), have("fuel_pellet")], deps=["nuclear/enriched"], rewards=[item("steel_ingot", 8), xp(8)],
     items=["pellet_die", "green_fuel_pellet", "fuel_pellet"])

ch.q("fuel_rod", "Fuel Rods", "fuel_rod",
     "Fuel Pellets in a Zircaloy Tube. A rod tracks its enrichment and burnup; fresh rods are only mildly radioactive, "
     "spent ones are very hot, so handle them with shielding.",
     [have("fuel_rod")], deps=["pellets", "zircaloy"], rewards=[item("lead_ingot", 8), xp(10)], frame="goal",
     items=["fuel_rod"])

ch.q("core_parts", "Core Parts", "control_rod",
     "Control Rods are silver on steel rods: they soak up neutrons and set the power. Graphite Moderators slow neutrons "
     "down so the fuel catches more of them, and Steel Reflectors bounce strays back in at the edges of the core.",
     [have("control_rod"), have("graphite_moderator", "steel_reflector", label="Make a moderator or reflector")],
     deps=["steelmaking/graphite"], rewards=[item("silver_ingot", 8), xp(8)],
     items=["control_rod", "graphite_moderator", "steel_reflector"])

ch.q("channels", "Channels", "fuel_channel",
     "Steel plates and rods round a Zircaloy Tube make a Fuel Channel; with a Steel Gear instead, a Control Rod Channel. "
     "These fill the inside of the reactor. Each Control Rod Channel belongs to one of four banks you move together.",
     [have("fuel_channel"), have("control_rod_channel")], deps=["zircaloy", "core_parts"],
     rewards=[item("steel_plate", 16), xp(8)], items=["fuel_channel", "control_rod_channel"])

ch.q("vessel", "Pressure Vessel", "reactor_casing",
     "Reactor Casing is steel plates with shielding metal and Concrete Shielding; Reactor Glass is the shielded window. "
     "The controller takes a casing and a Control Rod. The vessel is 3 to 7 across and 3 to 9 tall.",
     [have("reactor_casing"), have("reactor_controller")], deps=["channels", "nuclear/shielding"],
     rewards=[item("concrete_shielding", 16), xp(10)], items=["reactor_casing", "reactor_glass", "reactor_controller"])

ch.q("fission_reactor", "Fission Reactor", "reactor_controller",
     "Build the vessel round a grid of channels, set its ports with the Wrench and form it. Load fuel and control rods, "
     "fill it with water, and pull the rods out slowly: watch k-effective and the period. The Protection System SCRAMs "
     "the core if you go too fast.",
     [formed("fission_reactor", icon="reactor_controller")], deps=["vessel"], rewards=[item("hardened_alloy", 8), xp(15)],
     frame="goal", items=["fuel_channel", "control_rod_channel"])

ch.q("steam_generator", "Steam Generator", "steam_generator_controller",
     "In a pressurized-water reactor the coolant never boils; it carries heat to a Steam Generator, a tall vessel of Steam "
     "Generator Tubes (nickel rods and steel plates) that boils feedwater into High-Pressure Steam for your turbines.",
     [formed("steam_generator", icon="steam_generator_controller")], deps=["vessel", "steam/turbine"],
     rewards=[item("steam_generator_tubes", 8), xp(12)],
     items=["steam_generator_casing", "steam_generator_controller", "steam_generator_tubes"])

ch.q("heavy_water", "Heavy Water", "heavy_water_bucket",
     "The Heavy Water Tower (casings with PVC, Exchange Trays inside) runs the Girdler sulfide process: water, a little "
     "Hydrogen Sulfide and steam give Heavy Water, a moderator that absorbs almost nothing.",
     [formed("heavy_water_tower", icon="heavy_water_tower_controller"), have("heavy_water_bucket")], deps=["vessel"],
     rewards=[item("pvc_sheet", 8), xp(12)],
     items=["heavy_water_tower_casing", "heavy_water_tower_controller", "exchange_tray", "heavy_water_bucket"])

ch.q("power_plant", "Power Plant", "steam_turbine_array_casing",
     "Fission Reactor, Steam Generator and a full-length Steam Turbine Array spinning at full speed: a nuclear power "
     "station. A large core can drive several turbines at once.",
     [formed("fission_reactor", icon="reactor_controller"), formed("steam_generator", icon="steam_generator_controller"),
      turbine("steam_turbine_array", min_length=15, icon="steam_turbine_array_casing")],
     deps=["fission_reactor", "steam_generator"], rewards=[item("arcforged_alloy", 8), xp(25)], frame="challenge",
     hidden=True)
