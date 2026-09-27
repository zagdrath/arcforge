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

### Geothermal Plant

Turns heat into FE. Output is **heat × 2 FE/t**, and the heat level rises and falls gradually as
heat sources are added or run out.

| Heat source | Heat | Output | Energy per unit |
|---|---|---|---|
| Lava (internal tank) | 40 HU | 80 FE/t | 160,000 FE per bucket |
| Coal | 20 HU | 40 FE/t | 64,000 FE |
| Block of coal | 20 HU | 40 FE/t | 608,000 FE (9.5× coal) |
| Charcoal | 15 HU | 30 FE/t | 48,000 FE |
| Adjacent lava source block | 5 HU each | 10 FE/t each | Free, never consumed |

- Burns one fuel at a time, lava first and then coal, blocks of coal or charcoal. A block of coal
  burns at coal heat for 9.5× as long as one coal, so it is a little more efficient than 9 coal.
- Lava source blocks touching any of its six sides add passive heat on top. The plant can sit on
  or beside a lava pool.
- Peaks at 70 HU (140 FE/t) with lava burning and lava on all six sides.
- Fill the 8,000 mB lava tank by piping lava in, placing lava buckets in the input slot, or
  right-clicking the plant with a lava bucket. Coal, blocks of coal and charcoal also go in the input slot.
- Stores 100,000 FE and pushes up to 1,000 FE/t into neighbouring machines and cables.
- Stops taking new fuel while its energy buffer is full.

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
| Liquid (mB/t) | 200 | 800 | 3,200 | 12,800 |
| Thermodynamic (HU/t) | 50 | 200 | 800 | 3,200 |

Conduits store what they pull, so they keep pulling from machines even when nothing will take it yet:

- **Energy** and **Thermodynamic** conduits each hold one tick's worth of their tier's rate
  (e.g. 256 FE per wrought energy conduit), and glow while they are moving energy or heat.
- **Liquid** conduits are glass and show the fluid inside. Each holds 1,000 mB, and a network
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
- Breaking the tank keeps its fluid on the item. A comparator reads how full it is.
- Sides: fill from every side and drain from the bottom by default.

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
- Breaking the cell keeps its energy on the item. A comparator reads how full it is.

### Wrench

- Use on a conduit side facing a machine: cycle Auto, Input, Output, Disabled (sneak to cycle
  backwards). On a joint between two conduits it disconnects or reconnects them.
- Sneak-use on a conduit's centre: pick it up.
- Use on a machine: rotate it. Sneak-use: pick it up with its contents, energy and settings kept.

## Machine settings

Tabs on the right of each machine's screen:

- **Energy**: stored FE and current output.
- **Redstone**: ignore redstone, run only with a signal, or run only without one.
- **Sides**: choose what each face does, including the front: none, input, output or energy
  output (tanks and cells: none, input or output). Left-click to cycle, right-click to cycle back, shift-click to clear one face, or use
  the clear button to reset every face to none.
  - Defaults: top is input, bottom is output, left, right and back are energy output, front is none.
- **Upgrades**: upgrade slots, reserved for future upgrade items.

## Configuration

All balance values (heat per fuel, FE per heat, burn times, tank and buffer sizes, output rate)
can be changed in `config/arcforge-common.toml`.

## License

Arcforge is released under the [MIT License](LICENSE). Copyright (c) 2026 Zagdrath.
