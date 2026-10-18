from dsl import *

ch = Chapter("chemistry", "Chemistry & Plastics", "chemical_reactor",
             "The Electrolyzer and the Chemical Reactor: hydrogen and oxygen, acids and leaching, plastics from oil, carbon "
             "capture and synthetic fuel from Syngas.")

ch.q("electrolyzer", "Electrolyzer", "electrolyzer",
     "Tempered Alloy, glass, nickel plates and a Wrought Fluid Tank. Water splits into 2 Hydrogen and 1 Oxygen for at least "
     "1,416 FE per mB of water, so hydrogen is a battery, never free power. It also splits Brine (Hydrogen, Chlorine, Lye) "
     "and Hydrofluoric Acid (Hydrogen, Fluorine).",
     [produced(fluid="hydrogen", icon="electrolyzer", label="Make Hydrogen"), produced(fluid="oxygen", icon="electrolyzer", label="Make Oxygen")],
     rewards=[item("wrought_pressurized_cylinder", 2), xp(6)], frame="goal", items=["electrolyzer"])

ch.q("chemical_reactor", "Chemical Reactor", "chemical_reactor",
     "Hardened Alloy, glass, Hardened Fluid Tanks, nickel plates and a thermocouple: three item and three fluid inputs. "
     "Acids, leaching and precipitating, plastics, fertilizer, lithium and every step of uranium chemistry run through it. "
     "Its recipes are tagged general, leaching or precipitating.",
     [placed("chemical_reactor")], rewards=[item("hardened_fluid_tank", 1), xp(8)], frame="goal", items=["chemical_reactor"])

ch.q("sulfuric_acid", "Sulfuric Acid", "sulfuric_acid_bucket",
     "One Sulfur Dust and 500 mB of water make 500 mB of Sulfuric Acid. It leaches ore, dissolves spodumene into Lithium "
     "Brine, makes Hydrofluoric Acid with Fluorite Dust and catalyses Ethylene from Ethanol.",
     [produced(fluid="sulfuric_acid", icon="sulfuric_acid_bucket", label="Make Sulfuric Acid")], deps=["chemical_reactor"],
     rewards=[item("sulfur_dust", 16), xp(5)], items=["sulfuric_acid_bucket"])

ch.q("leaching", "Leaching", "iron_slurry_bucket",
     "A raw ore piece and 250 mB of acid (Sulfuric or Hydrochloric) give 300 mB of Slurry: Iron, Copper, Gold, Silver, "
     "Nickel, Tungsten, Bismuth or Lead. Precipitate 100 mB with 100 mB of water for one dust. Three dust per raw piece, "
     "the best yield there is.",
     [produced(category="leaching", icon="iron_slurry_bucket", label="Leach a raw ore"),
      produced(category="precipitating", icon="iron_dust", label="Precipitate a dust")],
     deps=["sulfuric_acid"], rewards=[item("sulfuric_acid_bucket", 2), xp(8)], frame="goal",
     items=["iron_slurry_bucket", "copper_slurry_bucket", "gold_slurry_bucket", "silver_slurry_bucket", "nickel_slurry_bucket",
            "tungsten_slurry_bucket", "bismuth_slurry_bucket", "lead_slurry_bucket"])

ch.q("plastic", "Plastic", "plastic_sheet",
     "100 Light Oil and 50 Hydrogen (or 120 Ethylene) give two Plastic Sheets. Plastic is in the Conduit Filter, Storage "
     "Upgrades, every tool module, the Settings Card, Fermenter, Security Terminal, Battery Array Casing and the Arcforged "
     "Arc tools.",
     [have("plastic_sheet")], deps=["chemical_reactor", "electrolyzer"], rewards=[item("plastic_sheet", 8), xp(6)], frame="goal",
     items=["plastic_sheet"])

ch.q("settings_and_security", "Cards and Keys", "settings_card",
     "Plastic unlocks two base tools. The Settings Card (plastic, redstone, paper) copies a machine's sides, ports, "
     "redstone and options onto another. The Security Terminal (Wrought Alloy, plastic, glass panes, comparator) sets your "
     "default Public, Trusted or Private mode and your trusted players.",
     [have("settings_card"), have("security_terminal")], all_of=False, deps=["plastic"],
     rewards=[item("settings_card", 2), xp(4)], items=["settings_card", "security_terminal"])

ch.q("steam_cracker", "Steam Cracker", "steam_cracker_controller",
     "A 3×3×3 of Steam Cracker Casings (steel plates, bricks, Wrought Fluid Tank) with its Controller. Naphtha, Refinery "
     "Gas or Natural Gas plus Steam at 800°C come out as Ethylene, Propylene and Hydrogen (and some Heavy Oil): the "
     "olefins every plastic is built from.",
     [produced(category="steam_cracking", icon="steam_cracker_controller", label="Crack with steam")], deps=["plastic"],
     rewards=[item("steam_cracker_casing", 8), xp(8)], frame="goal", items=["steam_cracker_casing", "steam_cracker_controller"])

ch.q("ethylene", "Ethylene", "steam_cracker_casing",
     "Ethylene also comes from Ethanol over Sulfuric Acid. 120 mB is two Plastic Sheets, and with Chlorine it's PVC.",
     [produced(fluid="ethylene", icon="plastic_sheet", label="Make Ethylene")], deps=["steam_cracker"],
     rewards=[item("plastic_sheet", 8), xp(5)])

ch.q("pvc", "PVC", "pvc_sheet",
     "60 Ethylene and 60 Chlorine give two PVC Sheets. PVC is acid-proof: it lines the Hardened and Arcforged Fluid "
     "Conduits and Fluid Tank, the Gas Centrifuge, and four sheets line a cylinder for uranium hexafluoride.",
     [have("pvc_sheet")], deps=["ethylene"], rewards=[item("pvc_sheet", 8), xp(6)], items=["pvc_sheet"])

ch.q("polypropylene", "Polypropylene", "polypropylene_sheet",
     "120 Propylene gives two Polypropylene Sheets: the plastic in the Arcforged Crate, Vault, Storage Upgrade and "
     "Jetpack, and a stand-in anywhere a recipe asks for plastics.",
     [produced(item="polypropylene_sheet", label="Make Polypropylene")], deps=["steam_cracker"],
     rewards=[item("polypropylene_sheet", 8), xp(6)], items=["polypropylene_sheet"])

ch.q("synthetic_rubber", "Synthetic Rubber", "synthetic_rubber",
     "60 Ethylene and 40 Propylene over a Cracking Catalyst (used one time in fifty). It cures in the Vulcanizer like Raw "
     "Rubber, so you never have to farm dandelions again.",
     [produced(item="synthetic_rubber", label="Make Synthetic Rubber")], deps=["polypropylene"],
     rewards=[item("rubber", 16), xp(6)], items=["synthetic_rubber"])

ch.q("carbon_capture", "Carbon Capture", "carbon_reclaimer",
     "Set a Flue Gas face on a Firebox, Combustion Plant or Fuel Burner and it gives off Carbon Dioxide while it burns "
     "carbon. The Carbon Reclaimer (Tempered Alloy, heating coil, Wrought Pressurized Cylinder) turns 250 CO2 and 500 "
     "Hydrogen back into Carbon Dust and water for 800,000 FE. It renews carbon, not energy.",
     [produced(category="carbon_reclaiming", icon="carbon_dust", label="Reclaim carbon")], deps=["electrolyzer"],
     rewards=[item("carbon_dust", 16), xp(8)], items=["carbon_reclaimer"])

ch.q("gasifier", "Syngas", "gasifier",
     "The Gasifier (steel plates, Rubber Gaskets, thermocouples, firebox, blast furnace) takes coal, charcoal, coke, "
     "Bio-Coal or biomass and Steam at 800°C and makes Syngas, with a chance of Wood Ash. Syngas burns in the Gas Turbine "
     "and feeds the Fischer-Tropsch Reactor.",
     [produced(fluid="syngas", category="gasifying", icon="gasifier", label="Make Syngas")], deps=["chemical_reactor"],
     rewards=[item("coal_coke", 16), xp(6)], items=["gasifier"])

ch.q("fischer_tropsch", "Fischer-Tropsch", "fischer_tropsch_reactor",
     "1,000 Syngas over an iron or nickel dust catalyst at 200–350°C gives 30 Naphtha, 50 Light Oil, 50 Heavy Oil and "
     "200 water. Oil from coal, or from wood: a refinery that never needs a well.",
     [produced(category="fischer_tropsch", icon="fischer_tropsch_reactor", label="Synthesise oil")], deps=["gasifier"],
     rewards=[item("light_oil_bucket", 4), xp(10)], frame="goal", items=["fischer_tropsch_reactor"])

ch.q("silica", "Silica", "silica_dust",
     "Flint finally has a use: the Arc Crusher makes 2 Silica Dust from it, and 4 from Quartz. Silica smelts into Glass and "
     "starts the whole silicon line.",
     [have("silica_dust")], deps=["chemical_reactor"], rewards=[item("minecraft:flint", 16), xp(3)], items=["silica_dust"])

ch.q("ferrosilicon", "Ferrosilicon", "ferrosilicon",
     "Iron, 2 Silica and Coal Coke in the Arcforge Furnace at 1,300°C. A steel flux: the four-ingot steel smelt takes it "
     "as it takes a Fluorite Crystal.",
     [have("ferrosilicon")], deps=["silica"], rewards=[item("coal_coke", 8), xp(4)], items=["ferrosilicon"])

ch.q("silica_gel", "Silica Gel", "silica_gel",
     "Silica in Lye gives Sodium Silicate; Sulfuric Acid sets it into Silica Gel, which the Cracking Catalyst takes in place "
     "of Sand.",
     [produced(fluid="sodium_silicate", icon="sodium_silicate_bucket", label="Make Sodium Silicate"),
      produced(item="silica_gel", label="Set Silica Gel")], deps=["silica", "sulfuric_acid"],
     rewards=[item("silica_dust", 16), xp(5)], items=["sodium_silicate_bucket", "silica_gel"])

ch.q("silicon_carbide", "Silicon Carbide", "silicon_carbide_mesh",
     "Two Silica and Coal Coke craft into Silicon Carbide Blend; the Induction Furnace fires it into Silicon Carbide. Ringed "
     "with Tungsten Rods it is the best Sifter Mesh there is, and it takes enchantments.",
     [have("silicon_carbide_mesh")], deps=["silica"], rewards=[item("silicon_carbide", 4), xp(8)], frame="goal",
     items=["silicon_carbide_blend", "silicon_carbide", "silicon_carbide_mesh"])

ch.q("polysilicon", "Polysilicon", "polysilicon",
     "Silica reduced by coke at 1,500°C gives Metallurgical Silicon. Dissolve it in Hydrochloric Acid for Trichlorosilane, "
     "then deposit it with Hydrogen and heat (1,000°C through a Heat face) as Polysilicon, getting the acid back.",
     [have("metallurgical_silicon"), produced(item="polysilicon", label="Deposit Polysilicon")], deps=["silica", "electrolyzer"],
     rewards=[item("hydrochloric_acid_bucket", 2), xp(10)], frame="goal",
     items=["metallurgical_silicon", "trichlorosilane_bucket", "polysilicon"])

ch.q("chemical_reactor_array", "Chemical Reactor Array", "chemical_reactor_array_casing",
     "Hardened Alloy, nickel plates, glass and a Chemical Reactor make four casings; a 3×3×3 of them runs every reactor "
     "recipe in three lanes, each twice as fast, with 32,000 mB tanks and Heat ports. Set its ports with the Wrench.",
     [formed("chemical_reactor_array", icon="chemical_reactor_array_casing")], deps=["chemical_reactor"],
     rewards=[item("hardened_alloy", 4), xp(15)], frame="challenge", items=["chemical_reactor_array_casing"])

ch.q("petrochemist", "Petrochemist", "chemical_reactor",
     "Plastic, PVC, Polypropylene and Synthetic Rubber from your own olefins, and carbon that goes round in a circle.",
     [have("plastic_sheet"), have("pvc_sheet"), have("polypropylene_sheet"), have("synthetic_rubber")],
     deps=["pvc", "synthetic_rubber", "carbon_capture", "fischer_tropsch", "leaching"],
     rewards=[item("arcforged_alloy", 4), xp(20)], frame="challenge")
