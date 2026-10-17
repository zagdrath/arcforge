from dsl import *

ch = Chapter("fuel_power", "Turbines & Engines", "gas_turbine_array_casing",
             "Burning refined fuel straight to FE: the Gas Turbine Array with its throttle and exhaust heat, and the V8, "
             "V12 and V16 Diesel Generators.")

ch.q("turbine_parts", "Blades and Combustors", "turbine_blade_set",
     "A Turbine Blade Set is Carbon Fiber and Hardened Alloy on a steel rod. A Combustor is Arcforge Furnace Bricks, "
     "tungsten plates and a Tungsten Heating Coil. Both go into every Gas Turbine Array Casing with Hardened Alloy and "
     "plastic.",
     [have("turbine_blade_set"), have("combustor")], rewards=[item("carbon_fiber", 4), xp(5)],
     items=["turbine_blade_set", "combustor"])

ch.q("gas_turbine", "Gas Turbine Array", "gas_turbine_array_casing",
     "A hollow 3×3 tube 5 to 9 long (the middles of both ends are casings). It burns Naphtha, Light Oil, Kerosene, Diesel, "
     "Gasoline, Ethanol, Hydrogen, Refinery Gas, Natural Gas, Biogas or Syngas straight to FE, up to 33,750 FE/t at 9 "
     "long. Its exhaust heat comes out of a heat port for a combined cycle.",
     [formed("gas_turbine_array", icon="gas_turbine_array_casing")], deps=["turbine_parts"],
     rewards=[item("gas_turbine_array_casing", 8), xp(10)], frame="goal", items=["gas_turbine_array_casing"])

ch.q("throttle_lever", "Throttle", "throttle_lever",
     "A lever, redstone, a comparator and a Steel Plate. Scroll or use it for a signal of 0 to 15. In the Gas Turbine's "
     "Throttle redstone mode that signal sets how hard it runs, so you burn fuel only for the power you need.",
     [have("throttle_lever")], deps=["gas_turbine"], rewards=[item("minecraft:comparator", 4), xp(4)],
     items=["throttle_lever"])

ch.q("full_throttle", "Full Throttle", "throttle_lever",
     "A Gas Turbine Array at full speed on a throttle signal of 15.",
     [turbine("gas_turbine_array", min_signal=15, icon="gas_turbine_array_casing", label="Full speed at throttle 15")],
     deps=["throttle_lever"], rewards=[item("gasoline_bucket", 4), xp(15)], frame="challenge")

ch.q("diesel_parts", "Engine Block", "diesel_generator_casing",
     "Diesel Generator Casings are steel plates and gears, copper plates and a Wrought Energy Conduit; the Controller "
     "adds copper rods, glass and a comparator.",
     [have("diesel_generator_casing"), have("diesel_generator_controller")], rewards=[item("steel_gear", 8), xp(4)],
     items=["diesel_generator_casing", "diesel_generator_controller"])

ch.q("diesel_generator", "Diesel Generator", "diesel_generator_controller",
     "A 3×3 box 5, 7 or 9 long: a V8, V12 or V16 making 3,000, 8,000 or 15,000 FE/t on Diesel, Gasoline, Kerosene, "
     "Biodiesel or Refinery Gas. Loud, simple, and it starts instantly.",
     [formed("diesel_generator", icon="diesel_generator_controller")], deps=["diesel_parts"],
     rewards=[item("diesel_bucket", 4), xp(10)], frame="goal")

ch.q("v16", "V16", "diesel_generator_casing",
     "The 9-long Diesel Generator: 15,000 FE/t.",
     [formed("diesel_generator", min_length=9, icon="diesel_generator_controller")], deps=["diesel_generator"],
     rewards=[item("hardened_energy_cell", 1), xp(12)], frame="challenge")
