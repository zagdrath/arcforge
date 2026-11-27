from dsl import *

ch = Chapter("fusion", "Fusion", "fusion_reactor_controller",
             "The tier above fission: deuterium and bred tritium, lithium-6, cryogenics and superconducting magnets, the "
             "tokamak Fusion Reactor and the Stellarator, the Tritium Plant and remote maintenance.")

# --- Fuels ---
ch.q("deuterium", "Deuterium", "electrolyzer",
     "Electrolyse Heavy Water: 100 mB gives 200 mB of Deuterium and 100 mB of Oxygen. Deuterium is half of a fusion "
     "reactor's fuel, and the Neutral Beam Injectors' beam.",
     [produced(fluid="deuterium", icon="electrolyzer", label="Electrolyse Heavy Water into Deuterium")],
     deps=["nuclear_power/heavy_water", "chemistry/electrolyzer"], rewards=[item("heavy_water_bucket", 2), xp(8)])

ch.q("lithium_6", "Lithium-6", "lithium_6_fluoride",
     "Natural lithium is 7.5% lithium-6, the isotope that breeds tritium. The Lithium Isotope Separator takes two "
     "Lithium Fluoride and gives one 20 points richer; feed it its own Enriched Lithium Fluoride until it gives "
     "Lithium-6 Fluoride (60% and up).",
     [produced(category="lithium_enriching", icon="lithium_isotope_separator", label="Enrich lithium"), have("lithium_6_fluoride")],
     deps=["salt_and_lithium/lithium_hydroxide"], rewards=[item("lithium_fluoride", 16), xp(10)],
     items=["lithium_isotope_separator", "enriched_lithium_fluoride", "lithium_6_fluoride"])

ch.q("target_rods", "Breeding Tritium", "lithium_target_rod",
     "A Zircaloy Tube between two Lithium-6 Fluoride makes a Lithium Target Rod. Load it into a Fission Reactor's Fuel "
     "Channel: its neutrons turn the lithium into tritium (the rod is a mild poison to the core).",
     [have("lithium_target_rod")], deps=["lithium_6"], rewards=[item("zircaloy_tube", 4), xp(10)], items=["lithium_target_rod"])

ch.q("tritium", "Tritium", "tritium_canister",
     "The Tritium Extraction Unit bakes a bred rod at 600°C: up to 1,000 mB of Tritium and 250 mB of Helium, and the "
     "tube back. Tritium seeps out of ordinary tanks and cartridges; the double-walled Tritium Canister keeps it, and keeps "
     "the Helium-3 it decays to apart.",
     [produced(category="tritium_extraction", icon="tritium_extraction_unit", label="Extract tritium"), have("tritium_canister")],
     deps=["target_rods"], rewards=[item("nickel_plate", 8), xp(12)], frame="goal",
     items=["tritium_extraction_unit", "tritium_canister"])

ch.q("lead_lithium", "Lead-Lithium", "lead_lithium_ingot",
     "Four Lead Ingots, a Lithium-6 Fluoride and Hydrogen in the Chemical Reactor make Lead-Lithium: the Breeding "
     "Blanket's breeder and coolant. The Arc Melter melts it for the reactor's tanks.",
     [have("lead_lithium_ingot")], deps=["lithium_6"], rewards=[item("lead_ingot", 16), xp(8)], items=["lead_lithium_ingot"])

# --- Cryogenics ---
ch.q("cryogenic_plant", "Cold Box", "cryogenic_plant_controller",
     "A box of Cryogenic Plant Casing, 3 to 7 across, empty inside: the Cryogenic Plant liquefies Nitrogen and Helium, "
     "faster the bigger it is. Ordinary tanks boil 5% of a cryogenic liquid away every minute; the Cryogenic Tank keeps it.",
     [formed("cryogenic_plant", icon="cryogenic_plant_controller"), produced(fluid="liquid_nitrogen", category="cryogenics",
      icon="cryogenic_plant_controller", label="Liquefy Nitrogen")],
     deps=["bio_industry/air_separator"], rewards=[item("invar_plate", 8), xp(10)],
     items=["cryogenic_plant_casing", "cryogenic_plant_controller", "cryogenic_tank"])

ch.q("helium", "Helium", "cryogenic_column_controller",
     "A quarter of gas caps are helium-rich. The Cryogenic Distillation Column (a 3×3 tower 6 to 16 tall) splits their "
     "gas into Natural Gas and Helium, a stage per block of its height; with no feed it splits air into Nitrogen, Oxygen "
     "and Argon.",
     [formed("cryogenic_column", icon="cryogenic_column_controller"), produced(fluid="helium", category="cryogenics",
      icon="cryogenic_column_controller", label="Distil Helium")],
     deps=["cryogenic_plant", "oil_and_gas/gas_well"], rewards=[item("nickel_plate", 8), xp(12)],
     items=["cryogenic_column_casing", "cryogenic_column_controller"])

ch.q("liquid_helium", "4 Kelvin", "cryogenic_plant_controller",
     "Liquefy Helium in the Cryogenic Plant. Liquid Helium takes superconducting magnets to their full field, freezes the "
     "fuel pellets and runs the Cryopumps.",
     [produced(fluid="liquid_helium", category="cryogenics", icon="cryogenic_plant_controller", label="Liquefy Helium")],
     deps=["helium"], rewards=[item("cryogenic_tank", 1), xp(12)], frame="goal")

# --- Magnets and the vessel ---
ch.q("superconductor", "Superconductor", "superconducting_coil",
     "Press Arcite-Tungsten Composite with the Rod Die into wire, sinter the wire into Superconducting Tape, and wind "
     "eight tapes round a Steel Rod: a Superconducting Coil. It carries current with no loss, but only when cold.",
     [have("arcite_tungsten_wire"), have("superconducting_tape"), have("superconducting_coil")],
     deps=["processing/composite"], rewards=[item("arcite_tungsten_composite", 4), xp(10)],
     items=["arcite_tungsten_wire", "superconducting_tape", "superconducting_coil"])

ch.q("magnets", "Field Coils", "toroidal_field_coil",
     "Toroidal Field Coils confine the plasma, the Central Solenoid drives its current, Poloidal Field Coils hold it in "
     "place. A Quench Protection Unit against the cryostat takes a quenching magnet's energy so the coil survives.",
     [have("toroidal_field_coil"), have("poloidal_field_coil"), have("central_solenoid"), have("quench_protection_unit")],
     deps=["superconductor"], rewards=[item("superconducting_tape", 8), xp(12)],
     items=["toroidal_field_coil", "poloidal_field_coil", "central_solenoid", "quench_protection_unit"])

ch.q("vessel_parts", "In-Vessel Parts", "first_wall",
     "Tungsten-faced First Wall and Divertor Cassettes face the plasma; the Lead-Lithium Breeding Blanket behind them "
     "takes its neutrons as heat and breeds tritium. Cryostat Casing closes it all in, with Vessel Ports for modules.",
     [have("first_wall"), have("divertor_cassette"), have("breeding_blanket"), have("cryostat_casing"), have("vessel_port")],
     deps=["lead_lithium"], rewards=[item("tungsten_plate", 16), xp(12)],
     items=["first_wall", "divertor_cassette", "breeding_blanket", "cryostat_casing", "vessel_port", "fusion_reactor_controller"])

ch.q("modules", "Port Modules", "gyrotron",
     "Against the Vessel Ports: Gyrotrons and Neutral Beam Injectors heat the plasma, Pellet Injectors and Gas Puff Valves "
     "fuel it, Cryopumps pump the vessel down and take the exhaust, and a Diagnostics Port shows the plasma in full.",
     [have("gyrotron", "neutral_beam_injector", label="Make a heater"), have("pellet_injector", "gas_puff_valve", label="Make a fueller"),
      have("cryopump")],
     deps=["vessel_parts"], rewards=[item("steel_pipe", 16), xp(12)],
     items=["neutral_beam_injector", "gyrotron", "pellet_injector", "gas_puff_valve", "cryopump", "diagnostics_port"])

ch.q("tokamak", "Tokamak", "fusion_reactor_controller",
     "Build the ring: Central Solenoid up the axis, the plasma chamber in First Wall over a Divertor floor, the Breeding "
     "Blanket, the Toroidal Field Coils over and under with two rings of Poloidal Field Coils in them, and the cryostat "
     "round it all, 2R + 3 blocks across for a major radius R of 3 to 7. The controller goes in the cryostat's wall at the midplane.",
     [formed("fusion_reactor", icon="fusion_reactor_controller")], deps=["magnets", "vessel_parts", "modules", "liquid_helium"],
     rewards=[item("superconducting_coil", 4), xp(20)], frame="goal")

# --- Running it ---
ch.q("first_plasma", "First Plasma", "fusion_reactor_controller",
     "Pump the vessel down, cool and energise the magnets, charge the Central Solenoid, set the heating and density, and "
     "press Run. Watch the Greenwald density, beta and q95: cross a limit and it disrupts.",
     [produced(category="fusion_plasma", icon="fusion_reactor_controller", label="Make a fusion plasma")],
     deps=["tokamak", "deuterium", "tritium"], rewards=[item("first_wall", 8), xp(20)])

ch.q("breakeven", "Breakeven", "fusion_reactor_controller",
     "Q above 1: the plasma makes more fusion power than the heating puts in. Hot Lead-Lithium and Hot Coolant go to "
     "Steam Generators and turbines.",
     [produced(category="fusion_breakeven", icon="fusion_reactor_controller", label="Reach Q = 1")], deps=["first_plasma"],
     rewards=[item("divertor_cassette", 4), xp(25)], frame="goal")

ch.q("ignition", "Ignition", "fusion_reactor_controller",
     "The alpha particles alone hold the plasma's temperature: the fire keeps itself lit. A big reactor gets there; it "
     "makes tens of millions of FE/t.",
     [produced(category="fusion_ignition", icon="fusion_reactor_controller", label="Ignite the plasma")], deps=["breakeven"],
     rewards=[item("arcforged_alloy", 16), xp(40)], frame="challenge")

ch.q("tritium_plant", "Tritium Plant", "tritium_plant_controller",
     "The Cryopumps' Fusion Exhaust is unburnt fuel, helium ash and the blanket's bred tritium. The Tritium Plant (a box of "
     "Tritium Plant Casing) splits it back into Deuterium, Tritium, Helium-3 and Helium: close the fuel cycle.",
     [formed("tritium_plant", icon="tritium_plant_controller"), produced(category="tritium", icon="tritium_plant_controller",
      label="Separate exhaust")],
     deps=["first_plasma"], rewards=[item("lead_plate", 16), xp(20)], items=["tritium_plant_casing", "tritium_plant_controller"])

ch.q("remote_handling", "Remote Handling", "remote_handling_arm",
     "The First Wall and Divertor wear, and neutrons make them radioactive. A Remote Handling Arm on a Vessel Port swaps "
     "them while the reactor is shut down; what it takes out are Activated Components for the Geological Repository.",
     [produced(category="remote_handling", icon="remote_handling_arm", label="Replace a worn part")], deps=["first_plasma"],
     rewards=[item("lead_plate", 8), xp(15)], items=["remote_handling_arm", "activated_component"])

ch.q("dhe3", "Aneutronic", "tritium_canister",
     "Switch the fuel to D-He3, with Helium-3 from decayed tritium in the Pellet Injectors: almost no neutrons, almost no "
     "activation. Only a large, hot plasma burns it.",
     [produced(category="fusion_dhe3", icon="fusion_reactor_controller", label="Burn D-He3")], deps=["tritium_plant", "breakeven"],
     rewards=[item("arcforged_alloy", 8), xp(30)], frame="challenge")

ch.q("direct_conversion", "Direct Conversion", "direct_energy_converter",
     "A Direct Energy Converter on a Vessel Port catches the charged particles streaming out of the plasma and turns them "
     "straight into FE, half again what their heat would make through turbines. D-He3's power leaves almost all that way.",
     [have("direct_energy_converter")], deps=["modules", "breakeven"], rewards=[item("superconducting_coil", 4), xp(20)],
     items=["direct_energy_converter"])

ch.q("helium_turbine", "Helium Loop", "helium_turbine_casing",
     "Fill the reactor's coolant tank with Helium instead of Water: it comes out as Hot Helium. A Helium Gas Turbine (a "
     "hollow 5x5 tube of Helium Turbine Casings, 15 to 31 long) runs on it straight, no boiler between, and sends the "
     "Helium back. A full-size one makes about 8M FE/t.",
     [formed("helium_gas_turbine", icon="helium_turbine_casing")], deps=["breakeven"],
     rewards=[item("arcforged_alloy", 8), xp(20)], items=["helium_turbine_casing"])

ch.q("stellarator", "Stellarator", "stellarator_coil",
     "Build the ring with Stellarator Coils where the Central Solenoid and Toroidal Field Coils go: twisted coils that need "
     "no plasma current. No pulses and no disruptions, at about two thirds of a tokamak's power.",
     [have("stellarator_coil"), formed("fusion_reactor", icon="fusion_reactor_controller")], deps=["tokamak"],
     rewards=[item("superconducting_coil", 8), xp(25)], frame="goal", items=["stellarator_coil"])
