from dsl import *

ch = Chapter("reactor_safety", "Reactor Safety", "containment_concrete",
             "A reactor fails the way a real one does, and always warns first. Build a sealed containment round it, give it "
             "emergency cooling, relief valves, hydrogen recombiners and a core catcher, and learn to clean up fallout.")

ch.q("containment_materials", "Containment Concrete", "containment_concrete",
     "Eight Concrete Shielding round a Steel Rod make eight Containment Concrete; four Steel Plates make two Steel Liner. "
     "A containment is any sealed room round the reactor (and its steam generators): every inside face liner backed by "
     "concrete. The Containment Dome is the lined block for a domed roof.",
     [have("containment_concrete"), have("steel_liner")], rewards=[item("concrete_shielding", 16), xp(6)],
     items=["containment_concrete", "steel_liner", "containment_dome"])

ch.q("airlocks", "Airlocks and Hatches", "airlock",
     "Airlocks (Steel Liner round Reactor Glass) are doors for people. Build them in pairs: two near each other are "
     "interlocked, so only one opens at a time. The Equipment Hatch is a 3×3 bolted door for machines; while it's open "
     "the containment isn't sealed.",
     [have("airlock"), have("equipment_hatch")], deps=["containment_materials"], rewards=[item("steel_liner", 8), xp(6)],
     items=["airlock", "equipment_hatch"])

ch.q("penetrations", "Through the Wall", "penetration",
     "Penetrations (Steel Liner and Steel Pipe) carry pipes, conduits and cables through the containment wall without "
     "breaking the seal. Place a row of them through the wall's thickness, each facing along it.",
     [have("penetration")], deps=["containment_materials"], rewards=[item("steel_pipe", 8), xp(5)],
     items=["penetration"])

ch.q("sealed", "Sealed", "containment_dome",
     "Run the reactor inside a sealed containment. The reactor finds it by flood fill from its vessel; the Safety page "
     "of its screen shows whether it's sealed, and why not. Sealed walls absorb explosions up to their strength and keep "
     "fallout in.",
     [produced(category="containment", icon="containment_dome", label="Run a reactor inside a sealed containment")],
     deps=["airlocks", "penetrations"], rewards=[item("hardened_alloy", 4), xp(12)], frame="goal")

ch.q("eccs", "Emergency Core Cooling", "eccs_accumulator",
     "ECCS Accumulators (a Hardened Fluid Tank in green-painted steel, 64,000 mB of water) and ECCS Pumps (an Electric "
     "Pump and a Block of Redstone, powered, drawing from tanks beside them or the Suppression Pool) touching the vessel "
     "inject water when its coolant falls below 75%.",
     [have("eccs_accumulator"), have("eccs_pump")], rewards=[item("hardened_fluid_tank", 1), xp(8)],
     items=["eccs_accumulator", "eccs_pump"])

ch.q("relief_valve", "Let It Vent", "relief_valve",
     "A vessel ruptures past 130% of its design pressure. Each Relief Valve touching it lets more steam out first; "
     "inside a containment with a Suppression Pool (water in the containment) they vent into the pool.",
     [have("relief_valve")], rewards=[item("steel_pipe", 4), xp(5)], items=["relief_valve"])

ch.q("hydrogen", "Hydrogen Recombiners", "hydrogen_recombiner",
     "Above about 1,200°C Zircaloy cladding reacts with steam and makes Hydrogen. Above 4% in the containment's air, a "
     "spark ignites it. Hydrogen Recombiners (silver and a Cracking Catalyst) anywhere in the containment remove it "
     "without a flame.",
     [have("hydrogen_recombiner")], deps=["containment_materials"], rewards=[item("silver_ingot", 8), xp(6)],
     items=["hydrogen_recombiner"])

ch.q("core_catcher", "Core Catcher", "core_catcher",
     "Above about 2,800°C fuel melts into Corium, which melts down through blocks. A Core Catcher (Zircon Dust, furnace "
     "bricks and Containment Concrete) under the vessel stops it and cools it faster.",
     [have("core_catcher")], deps=["containment_materials"], rewards=[item("zircon_dust", 8), xp(6)],
     items=["core_catcher"])

ch.q("decontamination", "Decontamination", "decontamination_shower",
     "A failure without a sealed containment releases Fallout that settles like snow and spreads downwind. Mount a "
     "Decontamination Shower overhead: it washes players two blocks below it, and what they carry, using water and "
     "wearing out its HEPA Filter (steel rods, paper and wool). Used filters are low-level waste.",
     [have("decontamination_shower"), have("hepa_filter")], rewards=[item("hepa_filter", 4), xp(6)],
     items=["decontamination_shower", "hepa_filter", "used_hepa_filter"])

ch.q("what_remains", "What Remains", "solidified_corium",
     "Corium slowly cools, faster beside water, into Solidified Corium: still intensely radioactive. Hold a piece of "
     "it. Use a Lead Box.",
     [have("solidified_corium")], deps=["core_catcher"], rewards=[item("lead_block", 8), xp(20)], frame="challenge", items=["solidified_corium"])
