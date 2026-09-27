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
Side configuration covers the faces of the whole cube; input faces feed the lane holding the fewest
items. Breaking any casing reverts it to loose casings; the contents stay in the centre casing.

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

### Upgrades

Speed, Energy and Heat upgrade cards go in a machine's Upgrades tab, up to 8 of each.

| Upgrade | Effect (n installed) | Machines |
|---|---|---|
| Speed | Works 2^(n/2) times as fast (16x at 8), using power or fuel just as fast, so the cost per operation doesn't change | All |
| Energy | FE per operation x 0.8^n (17% at 8); Combustion Plant: FE per fuel x (1 + n/8) | Arc Crusher, Arc Crushing Array, Metal Press, Metal Pressing Array, Combustion Plant |
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
  (e.g. 256 FE per wrought energy conduit), and glow while they are moving energy or heat.
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
- Pick the tank up with the wrench (sneak-use) to keep its fluid on the item; the item shows the
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
- GUI: the top slot drains a battery or any FE item into the cell; the bottom slot charges one from
  the cell. The screen shows live input and output rates.
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
- GUI: stored heat, temperature, live in/out rates, the current leak, and insulation pips for the tier.
- Recipes upgrade one tier into the next: bricks and a copper block, then Arcforge furnace bricks,
  then steel and wool, then obsidian and steel blocks, with copper at the corners of each.
- Breaking the cell keeps its energy on the item, which lights up to show its charge. A comparator
  reads how full it is.

### Wrench

- Use on a conduit side facing a machine: cycle Auto, Input, Output, Disabled (sneak to cycle
  backwards). On a joint between two conduits it disconnects or reconnects them.
- Sneak-use on a conduit's centre: pick it up.
- Use on a machine: rotate it. Sneak-use: pick it up with its fluid, energy and settings kept; items
  in its slots drop beside it.

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
  - Arc Crusher, Arc Crushing Array, Metal Press and Metal Pressing Array: none, input, output,
    energy. Top is input, bottom is output, back is energy.
  - Tanks and cells: none, input or output.
- **Upgrades**: four slots. Each holds up to 8 of one upgrade type, so a machine takes at most 8 of
  each (see Upgrades below).

## Configuration

All balance values (heat and FE rates, buffer sizes, temperatures, burn speeds, efficiency, output rates)
can be changed in `config/arcforge-common.toml`.

## License

Arcforge is released under the [MIT License](LICENSE). Copyright (c) 2026 Zagdrath.
