<p align="center">
  <img src=".github/assets/banner.png" alt="Arcforge" width="1005">
</p>

<p align="center">
  A Forge Energy technology mod for Minecraft 26.3 on NeoForge.
</p>

## About

Arcforge adds machines that generate and use Forge Energy (FE). All machines expose energy, fluids
and items through NeoForge's standard capabilities, so they connect to cables, pipes and machines
from any other NeoForge mod that uses FE.

Every Arcforge machine, fluid tank and energy cell appears in the **Arcforge: Machines** creative tab.

## Machines

### Power and heat

```
coal / charcoal / coal block --[Combustion Plant]--> FE      (simple, least FE per coal)
coal / charcoal / coal block --[Firebox]--> heat (HU)            (up to 1,100°C)
lava (tank, pipes, nearby blocks) --[Geothermal Plant]--> heat   (up to 600°C)
heat --[Thermoelectric Plant]--> FE                              (hotter heat = more FE)
```

**Heat.** Heat machines store heat (HU) in a buffer. Their temperature rises from 20°C when the
buffer is empty to the machine's maximum when it's full. Heat leaves through faces set to **Heat**,
either into a touching machine's heat face (up to 100 HU/t) or through thermodynamic conduits.
It only ever flows from a hotter machine into a colder one.

| Machine | Makes | Buffer | Max temp | Notes |
|---|---|---|---|---|
| Combustion Plant | 40 FE/t | 50,000 FE | — | Fuel burns at 2× furnace speed: 32,000 FE per coal |
| Firebox | 80 HU/t | 40,000 HU | 1,100°C | Fuel burns at 2× furnace speed: 64,000 HU per coal |
| Geothermal Plant | 40 HU/t from lava, plus passive | 20,000 HU | 600°C | 1 mB/t of lava: 40,000 HU per bucket |
| Thermoelectric Plant | FE from heat | 20,000 HU + 50,000 FE | 1,100°C | Takes up to 80 HU/t |

- **Fuel** for the Combustion Plant and Firebox is `#arcforge:combustion_fuel` (coal,
  charcoal, blocks of coal). Both pause while their buffer is full, keeping the rest of the burning item.
- **Geothermal Plant:** fill its 8,000 mB lava tank with lava buckets in the slot, by right-clicking
  with a bucket, or by piping lava into an input face. Touching lava source blocks add 3 HU/t each and
  magma blocks 1 HU/t each, even with an empty tank; they're never used up. It makes no FE itself.
- **Thermoelectric Plant:** heat passes through it in proportion to how full its buffer is (up to
  80 HU/t when full), and becomes FE at an efficiency set by its temperature: 0% at 100°C,
  50% at 600°C and 100% at 1,100°C. It can't get hotter than what feeds it, so a Geothermal Plant
  drives it to about 50% at best, while a Firebox gets it close to 100% (about 64,000 FE per coal).
  It takes a few minutes to warm up.
- The Combustion and Thermoelectric Plants push up to 200 FE/t out of their energy faces.

**Solar Thermal Array** (Tempered tier). A 2x2 tower exactly 4 tall: three layers of Solar Thermal Array
Casings with one controller in the bottom layer, facing out of the tower, and four Solar Collectors on
top. Formed, it becomes one model: a tower base, a mast and a parabolic trough mirror whose receiver tube
glows from grey through dull red to orange as it heats. The trough follows the sun (and stows face down at
night and in thunderstorms), and the receiver takes in heat by it:

| Condition | Effect |
|---|---|
| Sun | sin(time of day): nothing at night, about 70% at 9:00, 100% at noon |
| Weather | rain or snow ×0.3, thunderstorm ×0.1 (stowed) |
| Tracking axis | the controller's facing: north or south ×1.0, east or west ×0.7 |
| Biome | hot (desert, badlands, savanna) ×1.2, cold ×0.85 |
| Collectors | each one that can't see the sky loses a quarter |

At clear noon on the north-south axis it makes 200 HU/t at 550°C (High-Pressure Steam from a boiler needs
500°C, so about 10:00 to 14:00 in clear weather). The temperature follows the same factors, up to 550°C.
It holds 16,000 HU and gives heat out of its heat ports (a new tower has one on the bottom block behind
the controller); a buffer hotter than the sun now allows cools off over a few seconds. No sky, no heat:
it does nothing in the Nether or the End. Everything is configurable (`solarThermalArray`).

### Steelmaking and alloys

```
coal        --[Carbonizer]--> coal coke + 250 mB creosote   (600 ticks)
carbon dust --[Carbonizer]--> coal coke                     (300 ticks, no creosote)
metal + additive + coal coke --[Arcforge Furnace]--> result + slag
```

**Arcforge Furnace.** Three inputs: the metal, an optional additive and coal coke, which is both its fuel
and its reagent (it keeps back what each smelt uses; a coke block counts as nine). Burning coke heats it
towards 1,600°C, and a smelt only progresses above its recipe's minimum. A recipe without an additive only
runs with the additive slot empty. Input ports route each item to its own slot. Recipes are data-driven
(`arcforge:arcforge_smelting`: `metal`, optional `additive`, `coke`, `result`, `byproduct`, `time`, `min_temp`).

| Makes | Metal | Additive | Coke | Time | From |
|---|---|---|---|---|---|
| 1 Steel Ingot | 1 iron | — | 1 | 400 | 1,200°C |
| 2 Wrought Alloy | 2 iron | 1 gold | 1 | 400 | 1,200°C |
| 2 Tempered Alloy | 2 steel | 2 copper | 1 | 500 | 1,300°C |
| 2 Hardened Alloy | 2 steel | 2 amethyst shards | 2 | 600 | 1,400°C |
| 2 Arcforged Alloy | 2 Hardened Alloy | 2 Carbon Fiber | 2 | 800 | 1,500°C |

Each also gives 1 slag. **Tier alloys** are the material of every tiered block and item: conduits, cells,
tanks, cylinders and the portables are crafted from their tier's alloy, and each tier upgrades into the next
(the `arcforge:tier_upgrade` recipe keeps what it holds). The machine array casings need Tempered Alloy.
Crushing coal ore (2 carbon dust + a chance of a third) and carbonizing the dust is the high-yield coke route.
Carbon Fiber comes from the Distillation Array's pitch, so the top tier needs distillation.

### Crushing

**Arc Crusher.** Crushes ores and materials into dusts with FE: 20 FE/t for 200 ticks (4,000 FE an
operation), with a bonus output rolled once per operation. It stores 20,000 FE and takes up to
200 FE/t. Raw iron gives 1 iron dust (plus a 25% bonus), iron ore 2, ingots 1, and raw-ore blocks 9.
Coal and charcoal give carbon dust, a furnace fuel (half a coal). Iron, copper, gold, ancient debris
and quartz dust smelt back (twice as fast in a blast furnace). Recipes are data-driven
(`arcforge:crushing`).

**Arc Crushing Array.** Place 27 Arc Crushing Array Casings as a solid 3x3x3 cube. It forms into one
machine facing the player who completed it (turn it with the wrench), and any casing opens its GUI.
Three lanes crush in parallel, twice as fast as the Arc Crusher and for 60% less FE an operation
(16 FE/t per working lane), and ore recipes give **double** the main output (raw iron gives 2 dust, iron
ore 4); ingots, gems and blocks are not doubled. It stores 100,000 FE and takes up to 1,000 FE/t.
It does IO through its ports (see Multiblock ports): a new one has an input in the middle of the top,
an output in the middle of the bottom and energy in the middle of the back; input ports feed the lane
holding the fewest items. Breaking any casing reverts it to loose casings; the contents stay in the centre casing.

### Pressing

**Dies.** A Plate Die, Gear Die or Rod Die goes in a press's die slot and decides what it makes. Dies
don't stack and are never used up, and only players can put them in or take them out: hoppers and
pipes never touch the die slot.

**Metal Press.** Presses steel ingots into parts with FE: 20 FE/t for 100 ticks (2,000 FE an
operation). A plate die makes 1 Steel Plate from 1 ingot, a gear die 1 Steel Gear from 4, a rod die 2
Steel Rods from 1. With no die it reads "No die"; taking the die out mid-operation starts it over. It
stores 20,000 FE and takes up to 200 FE/t. Automation only feeds it what its die presses. Recipes are
data-driven (`arcforge:pressing`), so other metals can add their own.

**Metal Pressing Array.** Place 27 Metal Pressing Array Casings as a solid 3x3x3 cube; it forms like
the Arc Crushing Array. Three lanes press in parallel, each with its own die, so plates, gears and rods
can run side by side. Each lane is twice as fast as the Metal Press and uses 60% less FE an operation
(16 FE/t per working lane). It stores 100,000 FE and takes up to 1,000 FE/t. Input faces feed a lane
whose die presses the item (the one holding the fewest first) and refuse anything no lane can use.

### Steam

**Gases.** Steam comes in three grades, each its own gas: Steam, High-Pressure Steam and Superheated
Steam. Gases are fluids lighter than air (or tagged `#arcforge:gases`). They travel only in Pressurized
Conduits and Pressurized Cylinders, which carry nothing else; Fluid Conduits and Fluid Tanks refuse
them. A tank or network holds one gas at a time.

**Pressurized Conduits** move 400 / 1,600 / 6,400 / 25,600 mB/t (twice a fluid conduit) and hold
2,000 mB each; they glow in the colour of the gas while they hold or move it. **Pressurized Cylinders** hold 64,000 / 256,000 / 1,024,000 /
4,096,000 mB, keep their gas when broken, and show their fill on a gauge (and to comparators). Their GUI's
top slot empties a Gas Cartridge into the cylinder and the bottom one fills it.

**Electric Pump.** Pumps the fluid source directly below it: a bucket every 20 ticks for 10 FE/t. The
source is removed, except water with two or more water sources beside it, which is infinite. It
pushes up to 1,000 mB/t out of its top. Takes Speed and Energy upgrades.

**Steam Boiler.** Boils water into steam with heat, using up to 80 HU/t, and only at 100°C or hotter.
The grade depends on its temperature: Steam from 100°C (10 HU/mB), High-Pressure from 500°C (15 HU/mB),
Superheated from 900°C (20 HU/mB). Fed more heat than it uses, it climbs toward its heat source's
temperature; underfed, it sits near 100°C making plain Steam. Cooling into a lower grade turns the
steam it holds into that grade.

**Steam Turbine.** Turns up to 10 mB/t of steam into FE: 8 / 14 / 22 FE per mB by grade (80 to
220 FE/t). The used steam vents. Heavy Oil in its 1,000 mB lubricant tank (through a **Lubricant**
face) adds 8% to its output, using 1 mB every 100 ticks while it generates.

**Steam Boiler Array.** A 3x3 tower 3 to 7 tall of Steam Boiler Array Casings and Pressure Glass,
hollow in the middle. Only the 8 corners must be casings, so whole walls can be windows, and the water
and steam show through them. Per block of height: 100,000 HU, up to 200 HU/t, 16,000 mB tanks; each mB
costs 80% of a Steam Boiler's heat.

**Steam Turbine Array.** A 3x3 tube 3 to 9 long along either horizontal axis, built the same way. It
takes up to 40 mB/t per block of length at 10 / 18 / 28 FE per mB (a 9-long array on Superheated makes
10,080 FE/t). Its rotor spins up over a few seconds when steam flows, and output rises with it; it
coasts down when the steam stops. A new one has an energy port on its generator end and a steam port on
its bearing end. Heavy Oil in its 4,000 mB lubricant tank adds 8% to its output and doubles its spin-up, using 1 mB every 20 ticks per 3
blocks of length.

### Distillation

```
creosote + heat (+ steam) --[Distillation Array]--> Naphtha, Light Oil, Heavy Oil, Pitch
pitch --[Fiberizer, 1,000°C]--> Carbon Fiber --> Arcforged Alloy
8 gravel + 1 pitch --> 8 Asphalt
```

**Distillation Array.** A solid 2x2 column exactly 4, 6 or 8 tall of Distillation Array Casings and Tray
Level Casings with one Distillation Array Controller (Hardened tier). The controller and the trays can't
be in the top or bottom layer; the controller's display is the front. Formed, the column reads as one
block, and its tray levels are glass: each shows its steel tray, the fraction pooled on it (as deep as its
tank is full) and vapour rising while it runs. It distils
1,000 mB batches of creosote with heat, at 4,000 HU a batch and only at 350°C or hotter:

| Height | Per 1,000 mB of creosote | Feed | Heat |
|---|---|---|---|
| 4 | 250 Naphtha, 2 Pitch | 10 mB/t | 40 HU/t |
| 6 | 250 Naphtha, 450 Heavy Oil, 1 Pitch | 15 mB/t | 60 HU/t |
| 8 | 250 Naphtha, 300 Light Oil, 300 Heavy Oil, 1 Pitch | 20 mB/t | 80 HU/t |

It holds 40,000 HU per 4 blocks of height (up to 1,400°C), 16,000 mB of creosote, 8,000 mB of steam and
8,000 mB of each product, and stops when a product it makes is full. **Steam stripping:** steam in its
steam tank raises each batch's Naphtha by the grade (Steam +15%, High-Pressure +30%, Superheated +50%), out
of the Heavy Oil (a straight bonus at 4 high), using 100 mB a batch. Its ports take the modes Input
(creosote), Steam, Heat, Naphtha, Light Oil, Heavy Oil and Pitch, with auto-eject; a new column has heat
underneath, feed on the left, Naphtha on top, Heavy Oil on the right and Pitch at the back. Recipes are data-driven
(`arcforge:distilling`: `input`, `heat`, `min_temp`, `by_height`, `item_output`, `steam_stripping`).

**Fuel Burner.** Burns liquid fuels into heat, but never hotter than the fuel's burn temperature (the
optional `burn_temperature` of the `arcforge:burner_fuels` data map; without it, the burner's 1,200°C):

| Fuel | HU/mB | mB/t | HU/t | Burns at |
|---|---|---|---|---|
| Creosote | 120 | 0.5 | 60 | 850°C |
| Naphtha | 400 | 0.5 | 200 | 1,200°C |
| Light Oil | 250 | 0.5 | 125 | 1,000°C |
| Heavy Oil | 200 | 0.25 | 50 | 750°C |

Superheated Steam needs a 900°C boiler, so Naphtha is the fuel that makes it without a Geothermal Plant.
Naphtha also catches fire in the world. **Pitch** is a long-burning furnace fuel (12 items) and spins into
Carbon Fiber. **Asphalt** (and its slab and stairs) speeds up walking, running and riding by about 30%.

### Upgrades

Speed, Energy and Heat upgrade cards go in a machine's Upgrades tab, up to 8 of each.

| Upgrade | Effect (n installed) | Machines |
|---|---|---|
| Speed | Works 2^(n/2) times as fast (16x at 8), using power or fuel just as fast, so the cost per operation doesn't change | All with an Upgrades tab |
| Energy | FE per operation x 0.8^n (17% at 8); Combustion Plant: FE per fuel x (1 + n/8) | Arc Crusher, Arc Crushing Array, Metal Press, Metal Pressing Array, Electric Pump, Combustion Plant |
| Heat | Heat per fuel or lava (and from nearby lava and magma) x (1 + n/8); Thermoelectric Plant: efficiency / 0.8^n, up to 100% | Firebox, Geothermal Plant, Thermoelectric Plant |

## Logistics

### Conduits

Thin pipes that connect automatically to other conduits of the same type and to machines. A side
touching a machine starts on **Auto**: it follows the machine's own side configuration, so an
Arcforge machine's output faces are pulled from and its input faces are pushed into. Other mods'
blocks are pushed into (energy and heat blocks that can only give are pulled from). Use the wrench
to force a side to **Input** (push into the machine) or **Output** (pull from it) regardless, or to
**Disabled**. Conduits of different tiers connect, and a network runs at the rate of its lowest tier.

| Type | Wrought | Tempered | Hardened | Arcforged |
|---|---|---|---|---|
| Energy (FE/t) | 256 | 1,024 | 8,192 | 65,536 |
| Item (items per transfer) | 8 every 1.0s | 16 every 0.5s | 32 every 0.25s | 64 every 0.1s |
| Fluid (mB/t) | 200 | 800 | 3,200 | 12,800 |
| Thermodynamic (HU/t) | 50 | 200 | 800 | 3,200 |

Conduits store what they pull, so they keep pulling from machines even when nothing will take it yet:

- **Energy** and **Thermodynamic** conduits each hold one tick's worth of their tier's rate
  (e.g. 256 FE per wrought energy conduit), and glow while they hold or move energy or heat (thermodynamic conduits from dull red to white-hot with the temperature).
- **Fluid** conduits are glass and show the fluid inside. Each holds 1,000 mB, and a network
  carries one fluid at a time.
- **Item** conduits are glass and show items travelling through. Each stores up to 4 stacks,
  shown resting in its centre, and sends them on once a destination has room. Items that can't
  be delivered are rerouted or stored; they are only dropped when the conduit is broken.

### Fluid Tanks

A slim glass tank that shows the liquid inside, and glows with lava or any other glowing fluid.

| | Wrought | Tempered | Hardened | Arcforged |
|---|---|---|---|---|
| Capacity (mB) | 16,000 | 64,000 | 256,000 | 1,024,000 |
| I/O per operation (mB) | 500 | 2,000 | 8,000 | 32,000 |

- Right-click with a filled bucket to pour it in, or with an empty bucket to take a bucket out.
- In the GUI, a filled bucket (or any fluid container) in the top slot empties into the tank and the
  empty container drops into the slot below. An empty bucket there is filled from the tank instead.
- A Canister in the top slot empties into the tank, and one in the bottom slot fills from it, at the
  canister's rate; either way it stays in its slot.
- Pick the tank up with the wrench (Dismantle mode, sneak-use) to keep its fluid on the item; the item shows the
  fluid inside. Breaking it any other way spills a source block of the fluid (if it held at least
  a bucket) and drops the tank empty. A comparator reads how full it is.
- Sides: fill from every side and drain from the bottom by default. Output faces push fluid into
  the block beside them, so stacked tanks drain downwards into the lowest one.

### Energy Cells

A full-block FE store. Its side windows light up segment by segment as it charges, and it gives
off more light the fuller it is.

| | Wrought | Tempered | Hardened | Arcforged |
|---|---|---|---|---|
| Capacity (FE) | 500,000 | 4,000,000 | 32,000,000 | 256,000,000 |
| I/O (FE/t) | 512 | 2,048 | 16,384 | 131,072 |

- Output faces push FE into neighbouring machines every tick. By default the front (the side
  facing you when you placed it) is the output and every other face is an input.
- GUI: the top slot drains a Battery (or any FE item) into the cell; the bottom slot charges one from
  the cell, at up to the item's rate. The screen shows live input and output rates.
- Redstone control pauses pushing and charging.

### Heat Cells

A full-block heat (HU) store, built like the Energy Cell in orange. Its window segments, glowing
core and light level rise with its temperature (20°C empty, 1,100°C full). Heat always leaks away,
a share of what it holds every second, so an idle cell cools back towards 20°C; better tiers are
better insulated.

| | Wrought | Tempered | Hardened | Arcforged |
|---|---|---|---|---|
| Capacity (HU) | 50,000 | 200,000 | 800,000 | 3,200,000 |
| I/O (HU/t) | 50 | 200 | 800 | 3,200 |
| Leak | 10% / min | 4% / min | 1.5% / min | 0.5% / min |
| Idle half-life | ~7 min | ~17 min | ~46 min | ~2.3 h |

- Faces work like the Energy Cell's: the front outputs, every other face is an input. Heat only
  flows from hotter to colder, so a Geothermal Plant (600°C) fills a cell to about half; a Firebox
  fills it completely.
- Output faces push heat into colder neighbours every tick; thermodynamic conduits can also pull
  from them. Redstone control pauses pushing, but not leaking.
- Breaking a cell keeps its heat on the item (it doesn't leak while it's an item).
- GUI: Thermal Capsule slots (the top one empties a capsule into the cell, the bottom one fills one, only
  ever from hotter to colder), stored heat, temperature, live in/out rates, the current leak, and
  insulation pips for the tier.
- Breaking the cell keeps its energy on the item, which lights up to show its charge. A comparator
  reads how full it is.

### Portable storage

Carry FE, liquids, gases and heat. Each is filled and emptied in the item slots of its storage block
(top slot: emptied into the block; bottom slot: filled from it), shows a fill bar, and keeps its contents
when upgraded to the next tier. Automation can put them in through input faces and take full ones out
through output faces.

| | Wrought | Tempered | Hardened | Arcforged | Rate per tick | Filled at |
|---|---|---|---|---|---|---|
| Battery (FE) | 250,000 | 1,000,000 | 4,000,000 | 16,000,000 | 1k / 4k / 16k / 64k | Energy Cell |
| Canister (mB, liquids) | 16,000 | 64,000 | 256,000 | 1,024,000 | 1k / 4k / 16k / 64k | Fluid Tank |
| Gas Cartridge (mB, gases) | 32,000 | 128,000 | 512,000 | 2,048,000 | 2k / 8k / 32k / 128k | Pressurized Cylinder |
| Thermal Capsule (HU) | 50,000 | 200,000 | 800,000 | 3,200,000 | 50 / 200 / 800 / 3,200 | Heat Cell |

Thermal Capsules get as hot as the Heat Cell of their tier (1,100 / 1,100 / 1,300 / 1,400°C), only give
heat to something colder, and leak like that Heat Cell while carried.

### Wrench

Shift + mouse wheel with the wrench in hand changes its mode (shown in the action bar, on its tooltip and
by the colour of its grip). Where a mode does nothing with a block, the click goes through to it.

- **Configure**: use on a conduit side facing a machine to cycle Auto, Input, Output, Disabled (sneak to
  cycle backwards); on a joint between two conduits it disconnects or reconnects them. Use on a
  multiblock to check its structure (sneaking does nothing).
- **Rotate**: use on a machine to turn it clockwise, sneak-use to turn it back.
- **Port**: use on a multiblock's block or a machine's face to see its port or side mode, sneak-use to
  cycle it. Conduits work as in Configure.
- **Dismantle**: sneak-use to pick up a machine (its fluid, energy and settings kept; items in its
  slots drop beside it), a conduit, or a block of a multiblock.

### Multiblock ports

Multiblocks do IO only through their **ports**: blocks on the outside of the structure set to a mode,
marked with a plate (a blue ring takes in, an orange one gives out, red is energy, hot orange is heat).
A port works on every outer face of its block. On the cube arrays and the Solar Thermal Array, whose
formed model isn't a cube, a port is a steel nozzle from the model out to the block face. Set them with
the wrench in Port mode; each structure cycles only through its own modes. A new structure gets a few in
the middle of its sides (as listed with each machine), and ports stay on their blocks when the structure
breaks and forms again. Worlds from before ports get ports where their side configuration had faces.

## Machine settings

Tabs on the right of each machine's screen:

- **Energy** / **Heat**: stored FE or HU and the current rate (the Geothermal Plant also shows the
  lava and magma touching it).
- **Redstone**: ignore redstone, run only with a signal, or run only without one.
- **Sides**: choose what each face does, including the front. Left-click to cycle, right-click to
  cycle back, shift-click to clear one face, or use the clear button to reset every face to none.
  - Combustion Plant: none, input, energy. Top is input, back is energy.
  - Firebox and Geothermal Plant: none, input, heat. Top is input, back is heat.
  - Thermoelectric Plant: none, heat, energy. Top and bottom are heat, back is energy.
  - Arc Crusher and Metal Press: none, input, output, energy. Top is input, bottom is output, back is
    energy.
  - Tanks and cells: none, input or output.
- **Ports** (multiblocks, in place of Sides): the structure's ports, with where each one is, and the
  auto-eject toggle. Set ports with the wrench in Port mode.
- **Upgrades**: four slots. Each holds up to 8 of one upgrade type, so a machine takes at most 8 of
  each (see Upgrades below).

## Configuration

All balance values (heat and FE rates, buffer sizes, temperatures, burn speeds, efficiency, output rates)
can be changed in `config/arcforge-common.toml`.

## License

Arcforge is released under the [MIT License](LICENSE). Copyright (c) 2026 Zagdrath.
