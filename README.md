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

Every Arcforge machine appears in the **Arcforge: Machines** creative tab.

## Machines

### Geothermal Plant

Turns heat into FE. Output is **heat × 2 FE/t**, and the heat level rises and falls gradually as
heat sources are added or run out.

| Heat source | Heat | Output | Energy per unit |
|---|---|---|---|
| Lava (internal tank) | 40 HU | 80 FE/t | 160,000 FE per bucket |
| Coal | 20 HU | 40 FE/t | 64,000 FE |
| Charcoal | 15 HU | 30 FE/t | 48,000 FE |
| Adjacent lava source block | 5 HU each | 10 FE/t each | Free, never consumed |

- Burns one fuel at a time, lava first and then coal or charcoal.
- Lava source blocks touching any of its six sides add passive heat on top. The plant can sit on
  or beside a lava pool.
- Peaks at 70 HU (140 FE/t) with lava burning and lava on all six sides.
- Fill the 8,000 mB lava tank by piping lava in, placing lava buckets in the input slot, or
  right-clicking the plant with a lava bucket. Coal and charcoal also go in the input slot.
- Stores 100,000 FE and pushes up to 1,000 FE/t into neighbouring machines and cables.
- Stops taking new fuel while its energy buffer is full.

## Logistics

### Conduits

Thin pipes that connect automatically to other conduits of the same type. Use the wrench on a side
touching a machine to set it to **Input** (the conduit pushes into the machine) or **Output**
(the conduit pulls from it). Conduits of different tiers connect, and a network runs at the rate
of its lowest tier.

| Type | Wrought | Tempered | Hardened | Arcforged |
|---|---|---|---|---|
| Energy (FE/t) | 256 | 1,024 | 8,192 | 65,536 |
| Item (items per transfer) | 8 every 1.0s | 16 every 0.5s | 32 every 0.25s | 64 every 0.1s |
| Liquid (mB/t) | 200 | 800 | 3,200 | 12,800 |
| Thermodynamic (HU/t) | 50 | 200 | 800 | 3,200 |

- **Energy** and **Thermodynamic** conduits glow while they are moving energy or heat.
- **Liquid** conduits are glass and show the fluid inside. Each holds 1,000 mB, and a network
  carries one fluid at a time.
- **Item** conduits are glass and show items travelling through. Items that can't be delivered
  are rerouted to another destination, and are only dropped if nothing can take them.

### Wrench

- Use on a conduit side: cycle Input, Output, Off (sneak to cycle backwards). On a joint between
  two conduits it disconnects or reconnects them.
- Sneak-use on a conduit's centre: pick it up.
- Use on a machine: rotate it. Sneak-use: pick it up with its contents, energy and settings kept.

## Machine settings

Tabs on the right of each machine's screen:

- **Energy**: stored FE and current output.
- **Redstone**: ignore redstone, run only with a signal, or run only without one.
- **Sides**: choose what each face does, including the front: none, input, output or energy
  output. Left-click to cycle, right-click to cycle back, shift-click to clear one face, or use
  the clear button to reset every face to none.
  - Defaults: top is input, bottom is output, left, right and back are energy output, front is none.
- **Upgrades**: upgrade slots, reserved for future upgrade items.

## Configuration

All balance values (heat per fuel, FE per heat, burn times, tank and buffer sizes, output rate)
can be changed in `config/arcforge-common.toml`.

## License

Arcforge is released under the [MIT License](LICENSE). Copyright (c) 2026 Zagdrath.
