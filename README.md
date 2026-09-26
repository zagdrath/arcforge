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

## Machine settings

Tabs on the right of each machine's screen:

- **Energy**: stored FE and current output.
- **Redstone**: ignore redstone, run only with a signal, or run only without one.
- **Sides**: choose what each face does: none, input, output or energy output. Left-click to
  cycle, right-click to cycle back, shift-click to clear. The front face is locked.
  - Defaults: top is input, bottom is output, left, right and back are energy output.
- **Upgrades**: upgrade slots, reserved for future upgrade items.

## Configuration

All balance values (heat per fuel, FE per heat, burn times, tank and buffer sizes, output rate)
can be changed in `config/arcforge-common.toml`.

## License

Arcforge is released under the [MIT License](LICENSE). Copyright (c) 2026 Zagdrath.
