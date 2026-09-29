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

Suggested version: **2.1.0** (new content and balance changes; worlds load as they are).

### Upgrading

- **Fuel Burner maximum temperature.** The default is now 1,400°C, so hydrogen burns at its full 1,400°C.
  - An existing `arcforge-common.toml` keeps its old value (1,200).
  - Set `power.fuelBurner.maxTemperature` to 1400 by hand, or hydrogen stops at 1,200°C.
- **Recipes need plastic now.** Conduit Filters, Storage Upgrades and crafted crate and vault upgrades need a
  Plastic Sheet. Plastic needs the Chemical Reactor (Hardened), Light Oil and an Electrolyzer, so these items
  now come later in progression.
- **Superheater cost.** The default `steam.superheaterArray.heatCostMultiplier` is now 0.6 (was 1.0).
  - An existing `arcforge-common.toml` keeps 1.0; set it to 0.6 by hand.

- **Geothermal passive heat.** The defaults are now 40 HU/t per lava source and 16 per magma block (were 10
  and 4).
  - An existing `arcforge-common.toml` keeps 10 and 4; set `power.geothermalPlant.lavaSourceHeat` to 40 and
    `magmaHeat` to 16 by hand.

- **Solar Thermal Array temperature.** The default `solarThermalArray.maxTemperature` is now 1,100°C (was 550).
  - An existing `arcforge-common.toml` keeps 550; set it to 1100 by hand.

- **Machine security.** Machines, storage blocks and multiblocks placed before this update have no owner, so
  anyone can use them, as before. Break and re-place one (or build a new one) to own it. Ops bypass security
  unless `security.opsBypass` is false; `security.enabled = false` turns it off.
- **Burner fuel data packs.** `arcforge:burner_fuels` entries take an optional `"gas_turbine": false` to keep
  a fuel out of the Gas Turbine Array. Entries without it are burned there, so a pack that adds a slow,
  dirty fuel may want to set it.

### Added

- **Settings Card.** Sneak-use it on a machine, conduit, Vault or multiblock to copy its setup (sides or ports,
  redstone mode, auto-eject, and extras such as the Electrolyzer's vents, the Arc Quarry's area and filter or
  a boiler's pressure); use it on another of the same kind to paste. Multiblocks must be the same size; ports
  paste relative to the structure, so a card from an east-west turbine fits a north-south one. Conduit filter
  settings paste onto sides that already have a filter (others are skipped and counted). It never copies
  items, fluids or upgrades. Sneak-use in the air to clear it.
- **Machine security.**
  - Every machine, storage block and multiblock records who placed it. Each player picks a default of
    **Public**, **Trusted** or **Private** at the new **Security Terminal**, and lists the players they trust.
  - A **Security** tab on every machine screen lets the owner override the mode for that machine.
  - Others can't open, break, wrench, paste onto or hand-fill a machine they can't access, and can't extend
    someone else's multiblock. Conduits, hoppers and other automation still work, so shared lines never
    break. The Block Breaker and Block Placer act as their owner.
  - Jade shows the owner and mode. Config `[security]`: `enabled`, `opsBypass`, `defaultMode`.
- **Foundry Suit.** Fire-resistant armour from Rock Wool, Slag Wool and Steel Plates. Each piece cuts fire,
  campfire and hot-floor damage by 25%; the full set makes you immune to them and gives an 8-second lava
  shield (a bar over the hotbar), which recharges a minute after you leave the lava. Repaired with Rock Wool.
- **Fermenter and Ethanol.** A Tempered machine that ferments crops (wheat, potatoes, carrots, beetroot, sugar
  cane, melon slices and sweet berries) with water and FE into **Ethanol**, sometimes leaving bone meal.
  Ethanol burns in the Fuel Burner (300 HU/mB at up to 900°C) and the Gas Turbine Array. Speed and Energy
  upgrades, JEI (`arcforge:fermenting` recipes), a Handbook page and a running sound.
- **Dyed conduits.** Craft 1 to 7 conduits of one kind with a Plastic Sheet and a dye to sheathe them in that
  colour; a sheathed conduit alone strips back to plain. Sheathed conduits only join their own colour (or
  plain ones), so parallel lines of the same type stay separate. They keep their colour when broken, and JEI
  lists every colour.
- **Gas Turbine Array** (Hardened). A 3x3 tube 5 to 9 long that burns Naphtha, Light Oil, Ethanol or Hydrogen
  straight to FE.
  - It burns up to 560 HU/t per block of length at 1.5 FE per HU (less for fuels burning under 1,200°C): a
    9-long array makes up to 7,560 FE/t on Naphtha, under the Superheated Steam Turbine Array's 10,080.
  - One end is the intake, which needs air in front of it; the other is the exhaust. A quarter of the fuel's
    heat leaves the exhaust at half its burn temperature, through Heat ports into a Steam Boiler Array (the
    combined cycle, about 1.94× the burner route), or vents as heat haze.
  - The new **Throttle** redstone mode sets the fuel burned by signal strength (1-15). Starting takes a
    second of ignition; running dry is a flameout, with a short wait before it relights.
  - The rotor spools up and coasts down, with compressor and turbine blades and a glowing combustor through
    its windows, and a whine that climbs with its speed. Heavy Oil lubricant adds 8%.
  - New parts: the **Turbine Blade Set** and **Combustor**. JEI lists its fuels; Jade, a Handbook page and a
    config section `[gasTurbineArray]`.
- **Jetpacks** (Tempered / Hardened / Arcforged). They burn Steam, High-Pressure Steam, Superheated Steam or
  Hydrogen (data map `arcforge:jetpack_fuels`), with Normal / Hover / Off modes on a key (H), a fuel gauge by
  the hotbar, steam puffs or a blue hydrogen flame, and a thrust sound. Fill them in a Pressurized Cylinder or
  from a Gas Cartridge. Smith a Steel Chestplate onto one for an Armored Jetpack.
- **Arc Drill and Arc Saw** (Tempered / Hardened / Arcforged): FE-powered tools that never break, with module
  slots for Area, Silk Touch, Fortune I-III, Vein Mining and Speed. The Arc Saw fells trees. Both play a running
  sound and animate in hand (the auger spins, the chain runs) while they cut.
- **Steel tools and armour.** Sword, pickaxe, axe, shovel and hoe (500 uses, 7.0 speed) and a full armour set
  (2 / 7 / 5 / 2, 1.0 toughness): between iron and diamond, mining at iron level, repaired with Steel Ingots.
- **Steel Hammer and Steel Excavator.** Break a 3×3 facing the side you hit, only blocks the tool is good
  against and no harder than the one you hit; chests and other block entities are skipped. 1 durability per
  block, 1,500 uses; sneak for a single block. An outline shows what will break, and each block fires its own
  break event for protection mods.
- **Electrolyzer.** A Tempered machine that splits water into Hydrogen and Oxygen with FE, 2:1.
  - 100 mB of water makes 200 mB of hydrogen and 100 mB of oxygen for 120,000 FE (1,200 FE per mB of water),
    at 400 FE/t.
  - Water goes in on Input faces. The gases leave through the new **Hydrogen** and **Oxygen** face modes and
    are pushed out every tick.
  - Speed and Energy upgrades, redstone control, a GUI showing the ratio and the FE per mB of hydrogen, Jade,
    JEI, a Handbook page, and a running sound.
  - Recipes are data-driven (`arcforge:electrolyzing`). The primary output fills the hydrogen tank and the
    secondary the oxygen tank.
  - A vent button under each gas tank. With it on, gas that doesn't fit is released, so a full tank doesn't stop
    it when you only want the other gas.
- **Hydrogen and Oxygen**, two new gases (Pressurized Conduits, Cylinders and Gas Cartridges only).
- **Hydrogen fuel.** Hydrogen burns in the Fuel Burner: 60 HU/t, up to 1,400°C.
  - It stores power but never makes it. The Electrolyzer never charges less than 1.25× the most FE the best
    heat-to-FE setup could get back from what it makes.
  - This floor is worked out from the live config and data maps, so packs can't make hydrogen net-positive by
    changing them.
  - By default the floor is 354 FE per mB of hydrogen, reached at the fourth Energy upgrade.
- **Oxygen port on the Arcforge Furnace.** A smelt that starts with 50 mB of oxygen in the furnace uses it and
  runs 1.5× as fast.
  - The furnace's GUI, Jade and JEI show it.
  - Config: `oxygenTankCapacity`, `oxygenPerSmelt` and `oxygenSpeedMultiplier` in `multiblocks.arcforgeFurnace`.
- **Plastic Sheet**, tagged `#c:plastics`. The Chemical Reactor makes 2 from 100 mB Light Oil and 50 mB
  hydrogen.
- Handbook pages: **Electrolyzer** and **Hydrogen & Oxygen**. The Fuel Burner, Arcforge Furnace and Chemical
  Processing pages have new sections.
- **Assembler** (Tempered). An automatic crafting table.
  - A 3×3 pattern of ghost items picks any crafting recipe. Click a cell with an item to set it, or use JEI's +.
  - Ingredients go into an 18-slot buffer that only takes pattern items. Each craft uses 400 FE over 1 second.
  - The result goes to the output slot. Leftovers such as empty buckets go to two slots beside it. A craft
    whose result or leftovers wouldn't fit waits and uses nothing.
- **Block Breaker** (Wrought). Breaks the block in front of it into its 9 slots with FE. It needs no tool.
  - It drops what the right tool would, unenchanted: stone gives cobblestone, iron ore gives raw iron.
  - Harder blocks take longer: stone 6 ticks, obsidian 200, at 40 FE/t.
  - It never breaks unbreakable blocks, fluids, multiblock parts or `#arcforge:breaker_blacklist`.
  - It breaks as a fake player, so protection mods can refuse.
- **Block Placer** (Wrought). Places the first block it can from its 9 slots, for 20 FE each. Blocks orient as
  if a player placed them facing out of its front.
- The Block Breaker and Block Placer can face any of the six directions.
- **Vacuum Collector** (Tempered). Pulls dropped items within its range into an 18-slot buffer, for 10 FE per item.
  - The range defaults to 5 blocks and can be set from 1 to 9.
  - A Conduit Filter in its filter slot limits what it takes. An unset filter takes everything.
  - The range shows as an outline while you hold a Vacuum Collector, or from a button in its GUI.
- **Pulse redstone mode** for the Block Breaker and Block Placer: one break or placement per redstone pulse.
- Running sounds, Jade lines, JEI (the Assembler is a crafting station) and a new **Automation** chapter in the
  Handbook for all four.
- **Arc Quarry** (Hardened). A 3×3×3 digital miner, placed as one item in a clear 3×3 space, 3 blocks tall.
  - It mines a square area from the top layer down: radius 10 by default (up to 32, config `maxRadius`), from Y 0
    to 60 by default.
  - A block filter works like a Conduit Filter's. Set blocks or block tags (click a cell's chip, or type a tag
    with suggestions), as an allowlist or a denylist. Scan counts what matches before you start.
  - 200 FE per block, one block a second. Speed and Energy upgrades help.
  - **Silk Touch** mode costs 5× the FE. **Replace** mode (on by default) fills each mined space from its replace
    slot, and pauses when the slot runs out.
  - It skips air, fluids, unbreakable blocks, anything with a block entity, multiblock parts and
    `#arcforge:breaker_blacklist`. It checks each block again before mining it, and mines as a fake player so
    protection mods can refuse.
  - Drops go to a 27-slot buffer and out of any Output face of the cube. It pauses when full, out of power or out
    of replace blocks, and says why in its GUI and Jade.
  - An arc beam runs from its emitter to each block it mines. Its area shows as an outline while you hold one,
    while its settings are open, or always with its eye button.
  - Optional chunk loading (config `chunkLoading`, off by default) keeps its own chunk and the one it's working in
    loaded.
  - The Wrench picks it up with its settings.
- **Tungsten Drill Head** and **Quarry Scanner**, the Arc Quarry's parts.
- **JEI from the progress arrow.** Clicking a machine's progress arrow (each lane's, on the arrays) opens JEI on
  everything that machine makes. The Fuel Burner's flame shows its fuels.

### Changed

- **GUI tabs are smaller and on both sides.** Readouts (Energy, Heat, Pressure) are on the left of a machine's
  screen; settings (Redstone, Sides or Ports, Upgrades, Security) stay on the right. One tab can be open on
  each side.
- **Heavy Oil and Creosote don't burn in the Gas Turbine Array** (`"gas_turbine": false` in
  `burner_fuels.json`). They still burn in the Fuel Burner.
- **Plastic Sheets** go into the Settings Card, Security Terminal, Fermenter, conduit dyeing and the Gas
  Turbine Array Casing.
- **The hydrogen balance also counts the Gas Turbine Array** (fuel straight to FE, plus its exhaust raising
  steam). With the defaults it gives back less than the upgraded burner route, so the floor is unchanged.
- **Fuel Burner JEI** notes the fuels the Gas Turbine Array won't burn.
- Pressurized Cylinder slots also fill and drain Jetpacks.
- The Steel Hammer's area rules are shared with the Arc tools' Area module.
- **Solar Thermal Array: up to 1,100°C** (was 550), so it can feed a Superheater Array. In clear weather on the
  north-south axis it stays above 900°C from about 9:40 to 14:20. Its HU/t is unchanged.
- **Arcforge Furnace bricks redrawn** from Cody's brick texture in the furnace's fire-brick palette. The brick
  walls and the ports (idle and lit) use the new bricks; the port openings are unchanged.
- **Treated wood redrawn** from Cody's wood textures in the treated palette: planks (and so stairs, slabs,
  fences, gates, buttons and pressure plates), logs and wood, stripped logs, the door, trapdoor and door item.
- **Geothermal Plant: touching lava and magma give 4× the heat.** 40 HU/t per lava source (was 10) and 16 per
  magma block (was 4). A plant boxed in by lava makes 200 HU/t at 1,400°C with no fuel, so a field of them
  runs a Steam Turbine Array with no outside input: a self-sufficient plant. The hydrogen balance floor is
  unchanged.
- **Superheater Array: 3 HU per mB per grade step** (was 5). Boiling Steam and superheating it now costs 14 HU per
  mB of Superheated Steam, against 16 from a boiler alone, so the Superheater is the efficient way to make it.
  - The hydrogen balance floor follows automatically: 354 FE per mB of hydrogen (was 310).
- **Plastic in recipes.** Conduit Filters, Storage Upgrades and crafted crate and vault upgrades now need a
  Plastic Sheet in place of one alloy.
- **Fuel Burner.** Its default maximum temperature is 1,400°C (was 1,200). The other fuels still burn at their
  own lower temperatures.
- **Pressurized Conduits** connect to Fuel Burner input faces.

### Fixed

- **Slot placeholders match the items again.** Every empty-slot hint is now a silhouette of the item that
  goes there: the Arcforge Furnace shows an ingot, an additive crystal and Coal Coke (not dust piles and
  blobs); the coal, bucket, die, filter, battery, capsule and cartridge hints follow their current item art;
  the Infuser shows a block. They share one style across every GUI.
- **JEI text overlaps.** Distillation's steam-stripping bonus, which ran into the column line, and the Chemical
  Reactor's Leaching/Precipitating label, which could run into the cost, each get their own line. The steam
  line now names the fluid it boosts ("Steam stripping: up to +50% Naphtha").
- **Wrench on Energy faces.** It said "Energy output" for every machine. It now says which way the energy goes:
  "Energy input" on machines that use it, "Energy output" on generators (multiblock ports too).
- **Flickering and see-through edges on the formed arrays.** Parts of their models had faces in the same place,
  which flicker against each other:
  - the Superheater's side ribs, where they crossed its band;
  - the top of the Condenser under its band;
  - the Induction Furnace Array's crossed side blocks;
  - the Metal Pressing Array's base, where it ran into its columns.

  The Superheater's and Condenser's bands also had no top or bottom faces.
- **Steam Turbine Array GUI: overlapping text.**
  - The flow value ran into the dial. The unit is now in its label ("Flow (mB/t)").
  - Output of 10,000 FE/t or more is shortened (e.g. "+16.4k").
  - The exhaust line under the dial reads just "Vacuum" or "Venting", with the bonus in its tooltip.
  - Hovering the values shows them in full.
- **Pressurized Conduits: hard black lines along their edges.** The gas conduits' bottom bevel is now a normal
  1 px dark edge instead of a 2 px near-black band.
- **Fuel Burner:** its empty tank's tooltip said "Creosote"; it takes any burner fuel, so it now says "Fuel".

## [2.0.0] - 2026-09-28

### Upgrading

- **Breaking: Steam Boiler and Steam Turbine are gone.**
  - Placed ones turn into loose **Steam Boiler Array Casings** and **Steam Turbine Array Casings**.
    Items in inventories convert too.
  - The water and steam they held are lost.
  - Build the arrays instead. The casing recipes no longer need the old machines.
- **Config:** the `steam.steamBoiler` and `steam.steamTurbine` sections are removed.
  - The boiler array's maximum temperature moves to `steam.steamBoilerArray.maxTemperature`.
  - Re-apply it if you changed it.
- **Multiblock ports reset once.** Ports are now stored per face (see below). Each formed structure gets
  its default ports again the first time it loads; set any custom ports again with the Wrench.
- **Config values reset once.** The config is regrouped into categories, so `arcforge-common.toml` is
  rewritten with the defaults the first time the game starts. Re-apply any values you changed.
- **Ore changes apply to new chunks only.**

### Removed

- **Steam Boiler** and **Steam Turbine** (single blocks). This covers their blocks, items, GUIs, JEI
  entries, Handbook pages and config sections. The Steam Boiler Array and Steam Turbine Array replace
  them.

### Added

- **Per-face multiblock ports.** Each outer face of a block is its own port, so one corner can take things
  in on one side and give them out on another. Ports stay where they were set, even if the block is
  broken and put back or the structure breaks and forms again.
- **Boiler pressure setting.** A Pressure tab on the Steam Boiler Array: Auto, or hold
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
- **Crates:** tiered chests with 54 / 72 / 99 / 126 slots (the Tempered Crate and up scroll), with face
  settings, conduit and hopper support and a comparator reading. Broken, a crate spills like a chest; picked up
  with the Wrench it keeps its items. A filled crate can't go inside another crate, a vault or a shulker box.
- **Vaults:** bulk storage for one stackable item, 4,096 / 16,384 / 65,536 / 262,144 by tier. The front shows the
  item and its count. Right-click the front to put in what you hold (twice quickly: everything matching you
  carry), left-click to take a stack (Shift: one); mine it from any other face. Lock (the Wrench on the front, or
  the GUI) keeps the item type when it empties; Void destroys what arrives when it's full. Conduits and hoppers move
  as much as they like in and out, and a vault keeps its contents and settings when broken or picked up.
- **Storage Upgrades** (Tempered, Hardened, Arcforged): right-click a placed Crate or Vault of the tier below to
  upgrade it where it stands, contents and settings included. Crafting the block with the next tier's alloy
  also works.
- **Arc Melter** (Tempered tier): melts cobblestone, stone, netherrack and magma blocks into lava with FE
  (50 FE per mB by default, so a bucket costs 50,000 FE). Its 4,000 mB tank feeds conduits, tanks or a
  Geothermal Plant set right under it, with no setup. Takes Speed and Energy upgrades, has a running sound,
  and shows in JEI (Melting) and Jade. Recipes are data-driven (`arcforge:melting`), and the costs are in the
  config under `machines.arcMelter`.
- **Sulfur:** Sulfur Dust, crushed from gunpowder (2 each), netherrack (a 15% chance) or the new Nether Sulfur
  Ore, which drops 2–4 Sulfur Dust and generates through the Nether (configurable under `ores.sulfur`).
- **Chemical Reactor** (Hardened tier): an item and up to two fluids react into an item, a fluid or both, with a
  byproduct slot for chance outputs. Its two input tanks sort fluids by themselves, so water and acid each keep
  a tank. Takes Speed and Energy upgrades, has a running sound, and shows in JEI (Chemical Reacting) and Jade.
  Recipes are data-driven (`arcforge:chemical_reacting`).
- **Sulfuric Acid** (hurts whatever wades in) and an **Ore Slurry** for iron, copper, gold, silver, nickel,
  tungsten and bismuth, with buckets.
- **3x ore processing:** leach raw ore in Sulfuric Acid into slurry, then precipitate the slurry with water
  into dust: 3 dust per raw ore and 6 per ore block, against 2 and 4 from the Arc Crushing Array.
- Crushing recipes may now have only a chance output (no main result).
- **Superheater Array.** A 3x3x3 Hardened-tier multiblock. It upgrades steam one or two grades with
  heat: 5 HU per mB per step, gated by its temperature (500°C / 900°C). It has a Pressure tab (Auto /
  High-Pressure / Superheated), takes up to 2,000 HU/t, and has 16,000 mB tanks.
- **Condenser Array.** A 3x3x3 Tempered-tier multiblock. It turns Exhaust Steam back into water, 1:1.
  - 120 mB/t base, plus water, ice, packed ice and blue ice touching it.
  - Cold biomes ×1.25, the Nether ×0.5, capped at 400 mB/t.
- **Turbine exhaust.** A new **Exhaust** port mode on the Steam Turbine Array. Spent steam leaves as
  **Exhaust Steam** (a new gas).
  - It gives +10% FE while the exhaust drains, stacking with lubricant.
  - With no Exhaust port the turbine vents as before.
  - A boiler → turbine → condenser loop closes without a pump.

### Changed

- **Casing recipes.** Neither array casing recipe needs the old machine any more:
  - Steam Boiler Array Casing: Tempered alloy, copper plates and a bucket.
  - Steam Turbine Array Casing: Tempered alloy, nickel plates and a steel gear.
  - Both still make 4.
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
