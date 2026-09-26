# Arcforge

A technology mod for Minecraft 26.3 (NeoForge) by Zagdrath.

Arcforge machines use Forge Energy (FE) through NeoForge's standard capabilities
(`Capabilities.Energy`, `Capabilities.Fluid`, `Capabilities.Item`), so they interoperate
with any other NeoForge mod's cables, pipes and machines.

## Machines

### Geothermal Plant
Converts heat (HU) into FE. Output is `heat × fePerHeat` FE/t (default 2 FE/t per HU).

| Heat source | Heat | FE/t | Consumption | FE per unit |
|---|---|---|---|---|
| Lava (internal tank) | 40 HU | 80 | 50 mB per 100 ticks | 160,000 per bucket |
| Coal | 20 HU | 40 | 1 per 1600 ticks | 64,000 |
| Charcoal | 15 HU | 30 | 1 per 1600 ticks | 48,000 |
| Adjacent lava source block (each side) | 5 HU | 10 | never consumed | — |

- The combustion chamber burns one fuel at a time: lava from the tank first, then coal/charcoal.
- Passive heat from lava source blocks on any of the 6 sides stacks with combustion.
- Maximum heat is 40 + 6 × 5 = 70 HU (140 FE/t); the GUI heat gauge scales to this.
- Heat ramps toward its target, so the plant warms up and cools down gradually.
- The input slot accepts lava buckets (drained into the 8,000 mB tank) and coal/charcoal.
  Lava can also be piped in, or added by right-clicking the block with a lava bucket.
- Fuel is not consumed while the internal FE buffer is full.
- Pushes up to 1,000 FE/t into neighbours on faces configured for energy output.

### Machine GUI tabs
- **Energy**: stored FE and current output.
- **Redstone**: ignore redstone, run with signal, or run without signal.
- **Sides**: per-face IO relative to the front (left click cycles none → input → output →
  energy, right click reverses, shift-click clears). The front is locked. Defaults: top input,
  bottom output, left/right/back energy output.
- **Upgrades**: two slots accepting items tagged `#arcforge:upgrades` (empty for now).

All values are configurable in `config/arcforge-common.toml`.

## Development

- `gradlew runClient` / `gradlew runServer` to launch.
- `gradlew build` to produce the jar in `build/libs`.
- `gradlew --refresh-dependencies` if your IDE is missing libraries.

Minecraft uses the official Mojang mappings; see the
[license](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
NeoForge docs: https://docs.neoforged.net/
