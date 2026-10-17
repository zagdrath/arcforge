from dsl import *

ch = Chapter("oil_and_gas", "Oil & Gas", "pumpjack_controller",
             "Finding a deposit, drilling to it and pumping it: the Seismic Survey Tool, the Oil Derrick, the Pumpjack "
             "and the Gas Wellhead.")

ch.q("survey_tool", "Seismic Survey", "seismic_survey_tool",
     "A comparator on a steel rod with steel plates. Use it on the ground and it tells you what's under this chunk: the "
     "deposit kind (Seep, Pocket, Field, Giant), how many buckets are left, how deep, whether it's sour and whether it "
     "has a gas cap. About 30% of deposits are sour.",
     [used_on("seismic_survey_tool", label="Survey the ground")], rewards=[item("minecraft:paper", 8), xp(3)],
     items=["seismic_survey_tool"])

ch.q("survey_record", "Survey Record", "survey_record",
     "Survey with Paper in your inventory and the tool writes a Survey Record you can read later. Right-click it onto a "
     "filled map to mark the deposit.",
     [have("survey_record")], deps=["survey_tool"], rewards=[item("minecraft:map", 1), xp(2)], items=["survey_record"])

ch.q("oil_cavern", "Into the Cavern", "crude_oil_bucket",
     "Deposits are real caverns of Crude Oil deep underground (and tar pits on the surface). Find one and wade in. Oil "
     "is thick and slow; it can be bucketed straight out.",
     [enter("crude_oil", label="Swim in Crude Oil", icon="crude_oil_bucket")], deps=["survey_tool"],
     rewards=[item("crude_oil_bucket", 2), xp(4)])

ch.q("derrick_parts", "Rig Parts", "derrick_frame",
     "Five Steel Rods in an X make a Derrick Frame (the four legs and the crown). A ring of steel plates and rods makes a "
     "Derrick Casing (the floor). The Oil Derrick Controller is a casing with copper rods, glass and a comparator.",
     [have("derrick_frame"), have("derrick_casing"), have("oil_derrick_controller")], deps=["survey_record"],
     rewards=[item("steel_rod", 32), xp(3)], items=["derrick_frame", "derrick_casing", "oil_derrick_controller"])

ch.q("oil_derrick", "Oil Derrick", "oil_derrick_controller",
     "A 3×3 rig nine tall over a surveyed deposit: floor ring, four 7-tall legs, a 3×3 crown. Feed it 120 FE/t, 10 mB/t "
     "of water and Steel Pipes (one per four blocks) and it drills one block every two seconds down to the cavern. When "
     "it strikes, it leaves a Well Head behind that can't be moved.",
     [formed("oil_derrick", icon="oil_derrick_controller")], deps=["derrick_parts"], rewards=[item("steel_pipe", 16), xp(6)],
     frame="goal", items=["oil_derrick_controller", "steel_pipe"])

ch.q("struck_oil", "Struck Oil", "well_head",
     "The derrick hits the deposit and the Well Head starts to weep. Dismantle the rig: you don't need it any more.",
     [produced(fluid="crude_oil", category="struck_oil", icon="well_head", label="Strike oil")], deps=["oil_derrick"],
     rewards=[item("pumpjack_casing", 8), xp(8)], frame="goal", items=["well_head"])

ch.q("pumpjack", "Pumpjack", "pumpjack_controller",
     "Pumpjack Casings (steel plates and gears) make a 3×5 skid round the Well Head with a 3-tall Samson post; the "
     "Controller is a casing with copper rods and a comparator. 60 FE/t pumps Crude Oil out at up to 30 mB/t on a full "
     "deposit, through its Output port.",
     [formed("pumpjack", icon="pumpjack_controller")], deps=["struck_oil"], rewards=[item("wrought_fluid_tank", 2), xp(8)],
     frame="goal", items=["pumpjack_casing", "pumpjack_controller", "crude_oil_bucket"])

ch.q("sour_crude", "Sour Crude", "sour_crude_oil_bucket",
     "A sour deposit pumps Sour Crude Oil. It distils the same, but its Kerosene and Diesel come out sour and burn at 75% "
     "heat until a Hydrotreater cleans them with Hydrogen. The sulfur it gives up is worth having.",
     [produced(fluid="sour_crude_oil", icon="sour_crude_oil_bucket", label="Pump Sour Crude Oil")], deps=["pumpjack"],
     rewards=[item("sulfur_dust", 8), xp(5)], items=["sour_crude_oil_bucket"])

ch.q("gas_well", "Natural Gas", "gas_wellhead",
     "Drill a deposit with a gas cap (or a pure gas deposit) and the derrick strikes gas. A Gas Wellhead (comparator, Steel "
     "Pipes, steel gear and plate) on the Well Head gives 200 mB/t of Natural Gas for no FE at all, straight into "
     "Pressurized Conduits. It burns in the Gas Turbine and the Firebox Array, feeds the Steam Methane Reformer and the "
     "Steam Cracker.",
     [produced(category="gas_well", icon="gas_wellhead", label="Produce Natural Gas")], deps=["struck_oil"],
     rewards=[item("wrought_pressurized_cylinder", 1), xp(8)], frame="goal", items=["gas_wellhead"])

ch.q("flare_stack", "Flare Stack", "flare_stack",
     "Flint and steel on a Wrought Pressurized Cylinder with steel rods. It burns off any gas up to 1,000 mB/t so a refinery "
     "never stalls on a by-product it can't store (it refuses uranium hexafluoride). Wasteful, but better than a dead "
     "line.",
     [produced(category="flaring", icon="flare_stack", label="Flare a gas")], deps=["gas_well"],
     rewards=[item("minecraft:flint_and_steel", 1), xp(4)], items=["flare_stack"])

ch.q("oilfield", "Oilfield", "pumpjack_controller",
     "Surveyed, drilled, pumped, flared. Now refine it.",
     [formed("pumpjack", icon="pumpjack_controller"), produced(category="gas_well", icon="gas_wellhead", label="Produce Natural Gas"),
      have("crude_oil_bucket")],
     deps=["pumpjack", "gas_well", "flare_stack", "oil_cavern"], rewards=[item("hardened_alloy", 8), xp(12)], frame="challenge")
