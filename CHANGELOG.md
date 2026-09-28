# Changelog

All notable changes to Arcforge are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.2.0+26.3`), set by `mod_version` in
`gradle.properties`; the Minecraft version after the `+` changes only when the mod moves to a new one.

- **PATCH** (1.2.0 → 1.2.1): bug fixes and small tweaks. No new content, and worlds and configs carry
  over untouched.
- **MINOR** (1.2.0 → 1.3.0): new content or features, and balance changes. Worlds still load; anything a
  player has to redo (settings that reset, blocks that need re-placing) is listed under *Upgrading*.
- **MAJOR** (1.x → 2.0.0): changes that break existing worlds or remove content: registry names that
  change or go away, or items and blocks that disappear on load.

Changes land under **Unreleased** as they're made; when a version is released, that section gets its
number and date.

## [Unreleased]

Suggested version: **1.3.0** (new features and balance changes; ports and config values reset once).

### Upgrading

- **Multiblock ports reset once.** Ports are now stored per face (see below). Each formed structure gets
  its default ports again the first time it loads; set any custom ports again with the Wrench.
- **Config values reset once.** The config is regrouped into categories, so `arcforge-common.toml` is
  rewritten with the defaults the first time the game starts. Re-apply any values you changed.
- **Ore changes apply to new chunks only.**

### Added

- **Per-face multiblock ports.** Each outer face of a block is its own port, so one corner can take things
  in on one side and give them out on another. Ports stay where they were set, even if the block is
  broken and put back or the structure breaks and forms again.
- **Boiler pressure setting.** A Pressure tab on the Steam Boiler and Steam Boiler Array: Auto, or hold
  at Steam, High-Pressure or Superheated. A held boiler heats without boiling until it reaches that grade's
  temperature, then makes that grade at whatever rate its heat comes in.
- **Running sounds** for the Steam Turbine Array (pitch and volume follow the rotor), the Arc Crusher, Arc
  Crushing Array, Induction Furnace, Induction Furnace Array, Metal Press and Metal Pressing Array: grinding
  and arc crackle for the crushers, a deep electrical hum for the induction furnaces, and heavy machinery for
  the presses. Each fades in while the machine runs and out when it stops, with subtitles. They replace the
  occasional vanilla sounds these machines played.
- **Config screen:** values grouped into Heat & Power, Steam, Machines, Multiblocks and Ore Generation,
  with a page per machine; every value has a name; **Reset All** puts every value back to its default.
- Jade shows the Solar Thermal Array's name and heat on every block of it, not just the controller.
- **More Arc Crusher recipes.** Ores: redstone (6, 25% chance of 2 more), lapis (8, 25% chance of 2
  more), diamond (2) and emerald (2), all doubled by the Arc Crushing Array. Utility: cobblestone → gravel,
  gravel → sand (10% chance of flint), bone → 5 bone meal, blaze rod → 4 blaze powder, any wool → 4
  string, glowstone → 4 glowstone dust, sandstone → 2 sand.
- **Conduit Filter.** Goes on a connection of an item, fluid or pressurized conduit (the arm facing a
  machine) and decides what passes there: nine entries, allowlist or denylist, each entry matched exactly or
  by one of its tags, an option to ignore item components (enchantments, damage, names), and whether it
  applies when inserting, extracting or both. Right-click the connection with an empty hand to set it up;
  fluid and pressurized conduits take a bucket, Canister or Gas Cartridge as an entry. The Wrench's Dismantle
  takes it off with its settings. A sleeve on the arm shows it, its LED grey until set, then green (allowlist)
  or red (denylist), like the item. Jade lists a conduit's filters, and the Engineer's Handbook has an entry.

### Changed

- Solar Thermal Array peak output 200 → **600 HU/t**, pushed in full into a touching machine.
- Steam Boiler Array boils up to **600 HU/t per block of height** (was 200): a full 3×3×7 can feed a
  full-length Steam Turbine Array on High-Pressure Steam.
- A Steam Turbine Array's rotor speed follows the power in the steam, so higher grades spin it faster,
  and the rotor visibly turns faster at part speed.
- On Auto, boilers hold at 100°C instead of flickering between Boiling and Heating when heat arrives
  unevenly; the GUI's rates are smoothed.
- Fluorite, bismuth, tungsten and arcite generate more often and show on cave walls far more (tungsten no
  longer hides from air; arcite mostly doesn't).
- Plates, the Tungsten Heating Coil, Thermocouple and Arcite-Tungsten Composite moved to the Components tab.
- New textures for the Trough Mirror, Tungsten Heating Coil and Thermocouple.
- The Steam Turbine Array's end caps are plain casing; only ports you set show plates.
- The solar tooltip now explains that a boiler needs its pressure set to reach High-Pressure.
- Nether gold ore has its own crushing recipe: 1 gold dust with a 25% chance of another (was 2, as gold
  ore), still doubled by the Arc Crushing Array.
- Item conduits take turns between destinations more evenly: one that's full (or whose filter refuses the
  items) no longer hands its turn to the next destination as well.
- Multiblock port nozzles (on the cube arrays and the Solar Thermal Array) have their own shaded steel collar
  instead of a flat patch of the casing texture.

### Fixed

- Conduits connect to multiblock ports reliably, and ports appear and clear as soon as they're set.
- Breaking a Solar Thermal Array or Distillation Array controller un-forms the structure, so it can be
  rebuilt.
- Multiblock GUIs stay open from anywhere along a big structure (they closed from the far end of a
  9-long turbine).
- Port nozzles on the cube arrays and the solar tower are closed at the back.
- The solar trough no longer flickers where it meets the mast.
- Ports no longer show on two faces of an edge or corner block.
- The Distillation Array's vapour no longer flickers (strobes) as you move around it.
- The Distillation Array's controller sits in the tray band on every side face, not just its front: its other
  faces show a louvred panel lined up with the trays.
- Softer, cleaner frames round the Distillation Array's tray windows: no dark line along the bottom, the sides
  stop at the sill, and no notch where a tray layer meets a casing layer.

## [1.2.0] - 2026-09-27

### Added

- Six ores with config-driven generation: silver, nickel, tungsten (as wolframite), fluorite, bismuth and
  arcite (needs a diamond pickaxe, and glows), with raw items, raw blocks, dusts and storage blocks.
- Invar, plates for the new metals, and the Tungsten Heating Coil, Thermocouple and Arcite-Tungsten
  Composite.
- A second additive slot in the Arcforge Furnace.
- The Thermoelectric Efficiency Upgrade.

## [1.1.0] - 2026-09-27

### Added

- Jade and JEI support, the Engineer's Handbook with a 3D multiblock viewer, and Shift-for-info tooltips.
- Tier alloys, the Arcforge Furnace additive slot, carbon dust coking, and a full recipe rework with
  content-keeping tier upgrades.
- Batteries, Canisters, Gas Cartridges and Thermal Capsules.
- The Distillation Array: oil fractions, pitch, carbon fiber and asphalt; fuel burn temperatures; Heavy
  Oil as turbine lubricant.
- Wrench modes, multiblock ports and the Solar Thermal Array.

### Changed

- Unified texture shading across the mod (the texture audit).
- Portable containers moved to the Tools & Upgrades tab; the pre-filled ones were removed.

### Fixed

- The handbook closing on click, Jade on array windows, the boiler water line and the turbine front.
- Steam array edges showing through, and interior clipping; rebuilt multiblocks keep their facing.

## [1.0.0] - 2026-09-26

First version.

### Added

- Conduits (energy, item, fluid, gas, thermal) in four tiers, and the Wrench.
- Heat and power: Geothermal Plant, Combustion Plant, Firebox, Thermoelectric Plant, Fuel Burner, Energy
  Cells, Fluid Tanks and insulated Heat Cells.
- Processing: Arc Crusher, Induction Furnace, Metal Press with dies, Fiberizer and Infuser, and Speed,
  Energy, Heat and Insulation upgrades.
- Multiblocks: Carbonizer, Arcforge Furnace, and the Arc Crushing, Induction Furnace and Metal Pressing
  Arrays.
- Steam: three grades of steam, Pressurized Conduits and Cylinders, the Electric Pump, Steam Boiler and
  Steam Turbine, and the Steam Boiler and Steam Turbine Arrays.
- Materials: steel, coal coke, slag and rock wool, dusts, copper and steel parts, and treated wood.

[Unreleased]: https://github.com/zagdrath/arcforge/compare/7b3fbce...HEAD
[1.2.0]: https://github.com/zagdrath/arcforge/compare/f8e4679...7b3fbce
[1.1.0]: https://github.com/zagdrath/arcforge/compare/d39ded7...f8e4679
[1.0.0]: https://github.com/zagdrath/arcforge/commit/d39ded7
