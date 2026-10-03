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

### Changed

- **Runs on every NeoForge 26.3 beta,** from 26.3.0.0-beta up; it used to need 26.3.0.23-beta or later.

### Fixed

- **Arcforge failed to load on NeoForge 26.3.0.37-beta and later** (`NoSuchFieldError: ModConfig$Type COMMON`).
  NeoForge renamed the COMMON config type to LOCAL; Arcforge now picks whichever one the running NeoForge has. The
  config file is still `arcforge-common.toml`, so settings carry over.
- **Blurry blocks at a distance with Arcforge installed.** The Induction Furnace Array's chamber window (30×30) and the
  Arc Crushing Array's rollers and Metal Pressing Array's press window (24×24) cut mipmapping for the whole block
  atlas, every mod's blocks included. They're now 32×32, so the windows look exactly as before.
- **The Jetpack Mode key defaulted to Pause instead of H.** Minecraft 26.3 numbers keys differently from earlier
  versions. Controls you've already saved keep their binding, so if Jetpack Mode shows Pause, set it to H (or any key)
  in Options → Controls → Key Binds.
- **"Missing model" warnings for every liquid block** (`arcforge:creosote[level=0]` and so on): each liquid now has a
  blockstate.

## [2.3.0] - 2026-10-01

### Upgrading

- **Chemical Reactor third input tank** and **Electrolyzer liquid output tank.** Saved machines keep everything in their
  tanks and slots. A saved Electrolyzer keeps its side settings, so its bottom stays as it was; set it to Output with the
  Wrench (or in the Sides tab) to pipe Lye out of it. Newly placed ones have the bottom set to Output.
- **Halite generates only in newly explored chunks.**
- **New config sections and keys** `multiblocks.thermalEvaporator`, `machines.electricPump.seawaterBiomeTags`,
  `ores.halite`, `haliteBeds`, `machines.electrolyzer.liquidTankCapacity` and
  `farming.loamFarmland.legumeNutrientsPerStage` / `rotationMultiplier` get their defaults.
- **Leaching recipes** in data packs that name `arcforge:sulfuric_acid` still work; the built-in ones now take the tag
  `#arcforge:leaching_acids` (Sulfuric and Hydrochloric Acid).
- **Infuser additive slot** (for Pine Resin). Saved Infusers keep their wood, tank and upgrades: the upgrades move up past
  the new slot when the world loads.
- **Chemical Reactor by-product tank** and **Grain Dryer fluid tank** load empty in saved machines. The reactor's output
  and by-product slots now stack after the arrow (the second output tank sits where the by-product slot was) and its
  status line moved under the tanks; nothing in the slots moves.
- **Recipes that now need Rubber Gaskets or PVC Sheet** (see Changed): make some Rubber first, or a data pack can put the
  old recipes back.
- **Wild Rubber Dandelions generate only in newly explored chunks.**
- **New config sections** `farming.vulcanizer` and `farming.resinTap`, and `farming.grainDryer.tankCapacity`, get their
  defaults.
- **Data packs:** `arcforge:infusing` recipes may take an `additive` (`{"ingredient", "count"}`) instead of a `fluid`;
  `arcforge:drying` recipes may take a `fluid` (`{"fluid" or "tag", "amount"}`) instead of an `ingredient`;
  `arcforge:chemical_reacting` recipes may have a `fluid_byproduct`. Existing recipes load unchanged.
- **Vacuum Collector experience tank.** Saved collectors load with an empty Liquid Experience tank. Fluid conduits
  touching an Output face of one now connect to it (they find its experience there).
- **New config sections** `reservoir`, `experience`, `quantumTunnel` and `chunkLoader` get their defaults. Chunk Loaders
  load chunks for their owner only up to `chunkLoader.chunksPerPlayer` (25); raise it on servers that need more.

### Added

- **Reservoir:** a 32,000 mB glass tank in a thin steel frame. Reservoirs that touch merge into one tank of any shape and
  size (like OpenBlocks tanks), sharing one liquid that fills from the bottom up: drawn as one tank, with the frame only
  round its outer edges and the liquid as one body at its level (glowing for glowing fluids). Any block takes and gives
  the liquid (buckets, conduits, pipes), a comparator reads the whole tank, and a broken block keeps its share on the item.
  Tanks of different liquids stay apart. Separate from the tiered Fluid Tanks, which are unchanged.
- **Liquid Experience,** a glowing pale-green fluid at 20 mB per experience point, in `c:experience` so other mods' liquid
  experience works with it, with a bucket.
  - **XP Drain:** a grate placed on top of any fluid container (a Reservoir, a Fluid Tank, another mod's tank). Experience
    orbs that land on it run into the container below; a player sneaking on it is drained a level at a time.
  - **XP Shower:** hung under a block, fed Liquid Experience by conduit or from a container above; it gives a player
    sneaking under it experience a level at a time. A redstone signal turns it off.
  - **Vacuum Collector:** also collects experience orbs into a 16,000 mB Liquid Experience tank that conduits drain from
    its Output faces (the GUI shows it at the right).
  - Rates in the new `experience` config section.
- **Quantum Tunnel** (late game: Arcforged Alloy, Arcite and Ender Pearls). Every tunnel on a frequency, anywhere and in
  any dimension, shares one buffer of FE, heat, liquid, gas and items (sizes in the `quantumTunnel` config).
  - Frequencies are named in its GUI, public (anyone) or private (the owner and the players they trust at their Security
    Terminal); the maker may delete one. Each face is set for each resource to none, input or output; output faces push
    into what they touch.
  - A thin dark frame cube with Arcforged-cyan corner caps, a socketed port on each face (a blank collar until set, then the
    ring of its mode) and a cyan core spinning in the middle.
- **Chunk Loader:** keeps its own chunk, or a radius of up to 2 (5×5 chunks), loaded with NeoForge chunk tickets for the
  player who placed it (security as usual). The GUI maps the chunks and outlines them in the world while it's open. A
  per-player chunk limit (25), an option to load only while the owner is online, and an optional FE cost per chunk (off)
  are in the `chunkLoader` config. It stops when broken, turned off with redstone, or out of FE. A globe in a copper
  cradle on a tall pedestal turns while it works.
- Handbook pages (Reservoir, Liquid Experience, Quantum Tunnel, Chunk Loader), JEI information, Jade tooltips, and four
  new advancements (Pooling Resources, Bottled Wisdom, Spooky Action, Never Sleeps).
- **Rubber.**
  - **Rubber Dandelion,** a new crop (one block tall): yellow flowers, then white seed clocks when ripe. It drops
    **Rubber Dandelion Roots** and **Rubber Dandelion Seeds**. **Wild Rubber Dandelions** grow in meadows, plains and taiga
    (never snowy ones; modded biomes through `#arcforge:wild_crops/rubber_dandelion`), and grass drops the seeds. It grows in the Glass Cloche, Grow Chamber, Hydroponic Cell and Greenhouse Array.
  - **Latex,** a fluid. The Oil Press presses one root into 100 mB.
  - **Resin Tap,** a small rustic spout and cup hung on the side of a log with natural leaves above it (a living tree).
    Every 10 seconds it drips: 25 mB of Latex into its 1,000 mB tank on a jungle log; a 50% chance of a **Pine Resin** on
    a spruce log; a 15% chance on any other log (up to 16). Buckets, an empty hand, hoppers and conduits empty it. Jade
    shows what it holds and whether the tree is alive; JEI shows what each log gives.
  - **Pine Resin:** the Infuser takes it in its new additive slot in place of Creosote (one per plank, four per log or
    wood), so treated wood needs no Carbonizer.
  - **Raw Rubber:** the Grain Dryer has a fluid tank now and dries 250 mB of Latex into a sheet.
  - **Vulcanizer,** a single-block heat machine (HU, no FE) that only works at 140°C or hotter: 2 Raw Rubber + 1 Sulfur
    Dust make 2 **Rubber** (data-driven, `arcforge:vulcanizing`, with a JEI category). Side configuration, Speed and Heat
    upgrades, Jade info.
  - **Rubber** (`#c:rubbers`), the **Block of Rubber** and the **Rubber Gasket** (4 Rubber make 8).
- **Bio-plastics.**
  - **Ethylene,** a gas: 100 mB Ethanol + 5 mB Sulfuric Acid (the catalyst) in the Chemical Reactor make 60 mB Ethylene
    and 40 mB Water. The reactor has a new by-product tank for liquid by-products; it empties through By-product faces.
  - **Plastic Sheet** from 120 mB Ethylene (bio-polyethylene), beside the Light Oil + Hydrogen recipe, which stays.
  - **PVC Sheet:** 60 mB Ethylene + 60 mB Chlorine make 2.
  - Tag `#arcforge:plastics` (Plastic Sheet and PVC Sheet); PVC Sheet joins `#c:plastics`.
- Advancements: Dandelion Roots, Tapped and Crepe (Farming); Goodyear, Bio-Plastics and Acid-Proof (Chemistry). Handbook
  entries Rubber Dandelion, Resin Tap, Vulcanizer and Bio-Plastics, and updates to the Infuser, Grain Dryer, Oil Press and
  Chemical Processing entries. Every rate is in the config.
- **Salt and chlor-alkali chemistry.**
  - **Halite,** a new ore in stone and deepslate. It generates in large, flat underground beds (Y -32 to 40), several
    layers thick under deserts and oceans. It drops **Rock Salt** (Fortune works; Silk Touch drops the ore); the Arc
    Crusher grinds one into 2 Salt and the Arc Crushing Array into 4, and a halite ore gives twice that. Rock Salt packs
    into a **Block of Rock Salt**. Tags `#c:ores/halite`, `#c:raw_materials/rock_salt`, `#c:storage_blocks/raw_rock_salt`.
  - **Seawater,** a new fluid (`#c:seawater`): an Electric Pump on water in an ocean or beach biome pumps Seawater instead
    of Water (the biome tags are in the config).
  - **Thermal Evaporator Array,** a fixed 3×3×9 tower like Mekanism's Thermal Evaporation Plant: a solid 3×3 bottom of
    **Thermal Evaporator Casings** with the **Thermal Evaporator Controller** in the middle of one side, facing out; seven
    layers of a ring of 8 round a hollow 1×1 core, where the middle of each side may be **Pressure Glass**, so a window can
    run up any side; and a solid 3×3 cap. Formed, it keeps its block look with connected textures (outer rims only).
    Like the steam and gas turbine arrays' windows, each window reaches half a block into the casings round it (the
    corners, the cap and the base; the controller's side of the base stays solid), and the windows show the inside of
    the tower, lined, with the liquid rising and falling with the input tank, in that fluid's colour, with a white
    bed of salt at the bottom while it makes Salt.
    - It runs on heat (HU), only at 100°C or hotter, and faster the hotter it is: 20% speed at 100°C up to full speed
      (25 mB/t) at 400°C. Heat buffer 200,000 HU, up to 1,000°C.
    - 1,000 mB Seawater → 250 mB Brine + 675 mB Water back (6,000 HU); 250 mB Brine → 1 Salt + 200 mB Water back
      (1,800 HU). Data-driven (`arcforge:evaporating`).
    - Ports, set with the Wrench like the other arrays: Input (Seawater or Brine), Heat, Brine, Salt and Water. New
      Side modes `brine`, `salt` and `water` (port-only).
    - GUI, JEI category, Jade info, a Handbook entry and a build in the multiblock viewer. Tank sizes, throughput,
      temperatures, the speed curve and the heat buffer are in `multiblocks.thermalEvaporator`.
  - **Salt** (`#c:dusts/salt`) and **Salt Block**.
  - **Brine:** 1 Salt + 250 mB water in the Chemical Reactor.
  - **Electrolyzer:** 100 mB Brine → 50 mB Hydrogen + 50 mB **Chlorine** (a gas) + 100 mB **Lye** (sodium hydroxide
    solution), 60,000 FE. Electrolyzing recipes can now have a `tertiary` liquid output.
  - **Hydrochloric Acid:** 50 mB Chlorine + 50 mB Hydrogen in the Chemical Reactor. Every leaching recipe takes it as
    well as Sulfuric Acid.
  - **Lye biodiesel:** 100 mB Seed Oil + 25 mB Ethanol + 10 mB Lye make 150 mB Biodiesel, half as much again as the
    plain recipe, which stays.
  - Lye and Hydrochloric Acid burn what wades into them, like Sulfuric Acid.
  - Advancements: Worth Its Salt (Processing, mine Rock Salt), and in the Chemistry branch Salt of the Earth (build a
    Thermal Evaporator Array), Pickled, Chlor-Alkali and Muriatic.
- **Soybeans and crop rotation.**
  - **Soybeans,** a crop that is its own seed. **Wild Soybeans** grow in plains and forests, and grass drops them.
    The Oil Press gets 150 mB of Seed Oil from one (more than from any other seed), and the Compost Bin takes them.
  - **Legumes** (`#arcforge:legumes`, holding Soybeans): on Loam Farmland each growth stage adds 1 nutrient instead of
    using one (up to 15), and they grow at the nutrient speed even on empty Loam.
  - **Crop rotation:** Loam Farmland remembers whether its last harvested crop was a legume. A non-legume planted after one
    grows 1.25× as fast until it's harvested. Harvests by hand, Sickle, Scythe or Harvester count. Jade shows it on the
    farmland and the crop.
  - In the Glass Cloche, Grow Chamber, Hydroponic Cell and Greenhouse Array, legumes use no fertilizer or Nutrient
    Solution and grow as fast as if they had it (the Hydroponic Cell grows them on FE alone).
  - Advancements in the Farming branch: Bean Counter and Rotation.
  - Every rate, the rotation bonus and the nutrient values are in the config.

### Changed

- **Vacuum Collector:** it gathers experience orbs as well as items (see Added), for the same FE each.
- **Rubber Gaskets** go into the recipes of:
  - Tempered, Hardened and Arcforged Pressurized Conduits (one gasket in the ring: 7, 7 and 6 conduits a craft);
  - Tempered, Hardened and Arcforged Pressurized Cylinders and Gas Cartridges (two, along the bottom);
  - the Tempered Jetpack (between its conduit straps) and the Hardened and Arcforged Jetpacks (one, at the top).
- **PVC Sheet** lines the Hardened and Arcforged Fluid Conduits (one in the ring: 7 and 6 a craft) and the Hardened and
  Arcforged Fluid Tanks (one, bottom middle).
- **Every recipe that took Plastic Sheet** (or `#c:plastics`) now takes `#arcforge:plastics`, so PVC Sheet works too:
  the Crates, Vaults and Storage Upgrades, the Jetpacks, Arc Drills and Arc Saws, the tool modules, the Conduit Filter,
  Settings Card, Security Terminal, Fermenter and Gas Turbine Array Casing.
- **Chemical Reactor:** a by-product tank beside the output tank; the output and by-product slots stack after the arrow.
- **Infuser:** an additive slot under the wood slot; the status moved under the arrow.
- **Grain Dryer:** a fluid tank (4,000 mB) that takes Latex through input faces, pipes and buckets.
- **New banner** in the README and on the mod list's details screen.
- **Wild crops spawn in more places, and more often.** Each has a biome tag (`#arcforge:wild_crops/flax`, `/rapeseed`,
  `/sorghum`, `/hops`) built on the common `c:` biome tags, so modded biomes such as Biomes O' Plenty's get them too;
  Biomes O' Plenty's grasslands, scrublands and woodlands are listed by name as well. Hops now also grow in taigas and
  Sorghum on wooded badlands, never in snowy biomes. Wild Soybeans get the same treatment as the others.
- **Wild crops are much rarer.** Wild Flax, Rapeseed, Sorghum, Hops and Soybeans patches now generate in about one chunk
  in 28 instead of one in 10, with 10 tries a patch instead of 24.
- **Planter, Harvester and Fertilizer Spreader textures:** their wood is now cut from the Treated Planks and Stripped
  Treated Log textures (real grain and boards) instead of flat brown.
- **Chemical Reactor: three input tanks.** Recipes can take up to three fluids, and when several recipes match, the one
  that uses the most inputs wins. The GUI is re-cut to fit the third tank.
- **Electrolyzer: a liquid output tank** (8,000 mB, for Lye) beside the gas tanks, leaving by Output faces; the bottom is
  now an Output face by default, with a port on its texture. The second gas face carries Chlorine as well as Oxygen.
- **Oil Press:** Soybeans are its best seed.
- **Textures:** plates are shaded softer (no bright centre); the Arcite-Tungsten Composite is a tungsten plate with
  arcite fibres; the Plate, Gear and Rod Dies engrave the new plate, gear and rod shapes; Digestate, the Sickles and
  the Steel Scythe use Cody's own textures.
- **Greenhouse Array FE:** the gauge's tooltip and the Handbook say that only the Grow Lamps use FE, so by day under the
  sky it grows without any.
- **Gas Turbine Array spool-up.** The rotor now speeds up by at most full speed / `steam.gasTurbineArray.spoolTime`
  a tick (200: about 10 seconds from standstill to full at full throttle, less with lubricant), so slamming the
  throttle from 0 to 15 winds it up gradually; smaller throttle changes still follow quickly.
- **Digester Casing** is now called **Biogas Digester Casing** (same block, same id).

### Fixed

- **Planter and Fertilizer Spreader tops:** the Planter's seed tray now uses the Compost Bin's compost texture and the
  Fertilizer Spreader's top uses Loam, the same textures as those blocks, in place of rough copies.
- **Treated Slabs made a vanilla Composter instead of a Compost Bin.** They count as wooden slabs, so vanilla's
  Composter recipe matched them first; it now takes any wooden slab but Treated Slabs.
- **Glass Cloche:** its corner bars now reach the planter floor, so there's no gap at the corners inside the tray.
- **Planting Bed:** the rim's corners were missing, so the rim looked set back from the sides.
- **Grow Lamp:** its housing and mount had no underside, so from below you saw into it.
- **Jade named the block, not the machine, on the Biogas Digester and Greenhouse Array.** Their controllers never told
  clients the structure's shape, so Jade couldn't find the machine a casing, bed, lamp or pane belongs to. They now
  sync it when they form or break up, and Jade shows "Biogas Digester" / "Greenhouse Array" on any of their blocks.

## [2.2.0] - 2026-09-30

### Upgrading

- **New config section** `farming` (`compostBin`, `fertilizers`, `loamFarmland`, `hops`, `rusticMachines`, `millstone`,
  `mill`, `oilPress`, `seedExtractor`, `grainDryer`, `airSeparator`, `haberReactor`, `biogasDigester`, `cloche`, `greenhouse`)
  gets its defaults, as do the Fermenter's new keys; nothing to redo.
- **Chemical Reactor second item slot.** Saved Chemical Reactors keep their items, tanks and upgrades: the upgrades move
  up past the new slot when the world loads.
- **Fermenter additive slot.** Saved Fermenters keep their crops, byproduct and upgrades: the upgrades move up past the
  new slot when the world loads.
- **Wild crops generate only in newly explored chunks.**
- **Firebox and Combustion Plant ash slot.** Both machines gain an ash slot. Saved machines keep their fuel and
  upgrades: the upgrades move up past the new slot when the world loads.

### Added

- **Farming,** the base of a new section, with its own creative tab (**Arcforge: Farming**), a Handbook
  chapter and a Farming branch in the advancement tab.
  - **Compost Bin.** A treated-wood bin that plant matter fills like a vanilla composter (seeds, crops, leaves,
    saplings, flowers and food scraps, at the vanilla chances). When full, it makes Compost after a short
    delay. Drop items in by hand, or feed it with hoppers and conduits; they can also pull the Compost out from
    underneath.
  - **Loam** (dirt + Compost). Till it into **Loam Farmland**, which:
    - stays moist twice as long;
    - can't be trampled;
    - holds nutrients (0 to 15, shown in Jade).
    While it has nutrients, crops on it grow 1.5 times as fast, and each growth stage uses 1 nutrient.
  - **Irrigated Loam Farmland** (four Loam round a Copper Plate): Loam Farmland with a copper water channel,
    always moist with no water nearby.
  - **Fertilizers.** Use one on Loam Farmland, or on the crop growing on it:
    - Compost: +2 nutrients.
    - Wood Ash: +3. The Firebox and Combustion Plant have a new ash slot, and charcoal leaves Wood Ash half the
      time. The ash comes out through an Output face.
    - Basic Slag: +3. The Arc Crusher crushes 1 Slag into 2.
    - Mixed Fertilizer: +8, crafted from one of each.
  - Every value is in the new `farming` config section.
- **Crops:** four new crops. All grow on vanilla farmland and Loam Farmland (and use its nutrients). Each has a wild
  version in the biomes that suit it, and their seeds sometimes drop from grass.
  - **Flax** (wild in plains) grows two blocks tall with blue flowers. It gives Flax Fibre and Flax Seeds. One fibre
    crafts into String, and four make Linen.
  - **Rapeseed** (wild in meadows and plains) grows two blocks tall and flowers yellow. It gives Rapeseeds, the oil
    crop, for the Oil Press to come.
  - **Sorghum** (wild in savannas) grows two blocks tall with rust-red seed heads. It gives Sorghum Stalks. In the Fermenter a stalk makes 80 mB of Ethanol, more than
    any vanilla crop.
  - **Hops** (wild in forests) are perennial. Stand a **Trellis** (treated wood, up to 2 high) on farmland and plant
    Hop Seeds in the bottom one. The vine climbs, then bears Hop Cones. Picking them leaves the vine to bear again.
  - Handbook pages, Jade shows a trellis's vine, and four new Farming advancements.
- **Rustic farming machines and tools:** unpowered, built from treated wood, copper and iron. All ranges, rates and
  timers are in the new `farming.rusticMachines` config section.
  - **Planter.** Plants seeds from its 9 slots on the empty farmland in the 3x3 in front of it, and Hop Seeds into a
    bare Trellis.
  - **Harvester.** Harvests the ripe crops in the 3x3 in front of it into its 18 slots and replants each with a seed
    from its own harvest. It picks Hop Cones and leaves the vine.
  - Both run once per redstone pulse, or every 5 seconds with no redstone attached. Hoppers and conduits fill the
    Planter and empty the Harvester. There's no GUI: use them by hand to add seeds or take the harvest.
  - **Fertilizer Spreader.** Holds fertilizer and tops up the Loam Farmland in the 5x5 around it when a block's
    nutrients drop below 4.
  - **Copper Sprinkler.** Fed water from a pipe, tank, conduit or bucket, it uses 1 mB/t. It keeps farmland within 4
    blocks moist and gives the crops on it a small growth bonus. It sprays while it runs.
  - **Scarecrow.** A treated-wood and linen figure, two blocks tall. Mobs can't trample farmland within 9 blocks of it.
  - **Iron Sickle** and **Steel Sickle** harvest and replant the ripe crops in a 3x3; the **Steel Scythe** does a 5x5.
  - Handbook pages, JEI info, Jade tooltips and three new Farming advancements (Hands Free, April Showers, Reaper).
- **Farm processing:** machines that turn the harvest into goods. Recipes are data-driven (`arcforge:milling`,
  `arcforge:oil_pressing`, `arcforge:seed_extracting` and `arcforge:drying`), each with its own JEI category, and every
  rate is in config.
  - **Millstone** (rustic, no power). Put the item in and turn the stone by hand, or with a redstone pulse: wheat into
    **Flour**, any seeds into **Seed Meal**, bones into 4 Bone Meal (crafting gives 3), sugar cane into 2 Sugar. Flour
    smelts or smokes into Bread; Seed Meal is a light fertilizer (+1 nutrient).
  - **Mill** (FE). The powered Millstone: three lanes, each twice as fast as a recipe's base time, fed evenly from its
    input faces. Takes Speed and Energy upgrades.
  - **Oil Press** (FE). Presses Rapeseeds (125 mB), Flax Seeds (80 mB) and other seeds (25 mB) into **Seed Oil** and
    **Press Cake**. Seed Oil is a new fluid with a bucket; Press Cake goes in the Compost Bin.
  - **Seed Extractor** (FE). Threshes crops into their seeds (wheat into 2 Wheat Seeds, Hop Cones into Hop Seeds, and
    so on), for replanting and pressing.
  - **Grain Dryer** (heat). Dries Hop Cones into **Dried Hops**, wheat into **Dried Grain** and Sorghum Stalks into
    **Dried Sorghum** with HU and no FE. It only runs above 60°C.
  - Handbook pages, Jade tooltips and four new Farming advancements (Daily Grind, Grist for the Mill, Cold Pressed,
    Hop to It).
- **Farm chemistry:** from air and crops to fertilizer and fuel. Recipes are data-driven (`arcforge:air_separating`,
  `arcforge:synthesizing` and `arcforge:digesting`, plus new `arcforge:chemical_reacting` recipes), each with a JEI
  category, and every rate is in config.
  - **Air Separator** (FE). Separates the air round it into **Nitrogen** and Oxygen (80 mB and 20 mB every 2 s, at
    80 FE/t) with no input, anywhere but the End. Oxygen goes out of Oxygen faces and Nitrogen out of Gas Output faces;
    when one tank is full that gas goes back into the air, so it keeps making the other. A second source of Oxygen
    besides the Electrolyzer.
  - **Haber Reactor** (FE and heat). 60 mB of Hydrogen and 20 mB of Nitrogen make 40 mB of **Ammonia** every second,
    at 60 FE/t and 10 HU/t, but only at 450°C or hotter.
  - **NPK Fertilizer:** Basic Slag, Wood Ash and 100 mB of Ammonia make two in the Chemical Reactor. It adds 15
    nutrients to Loam Farmland and enriches it: the crop on it grows twice as fast (instead of 1.5 times) until those
    nutrients run out.
  - **Nutrient Solution:** NPK Fertilizer and water in the Chemical Reactor. It feeds the Hydroponic Cell.
  - **Biodiesel:** 100 mB of Seed Oil and 25 mB of Ethanol make 100 mB in the Chemical Reactor. A Fuel Burner fuel:
    320 HU per mB at up to 1,000°C.
  - **Biogas Digester:** a solid 3x3x3 of **Digester Casings** with a **Biogas Digester Controller** in the middle of a
    side. It digests crops, seeds, leaves, Press Cake and Compost in water, four at a time, into **Biogas** (a gas that
    burns in the Fuel Burner, 45 HU per mB at up to 1,100°C, and in the Gas Turbine Array) and **Digestate**, a
    fertilizer worth 4 nutrients. It needs a little heat: it only works at 35°C or hotter, using 2 HU/t.
  - Handbook pages, Jade tooltips, a build page for the digester, and five new Farming advancements (Thin Air, Bread
    from Air, N, P, K, Fryer to Fuel, Waste Not).
- **Automated farms:** single blocks that grow a crop over and over, the plant growing inside where you can see it
  through the glass. Recipes are data-driven (`arcforge:cloche`: a seed, the soils it grows in, the harvest with
  chances, the time, and how it's drawn), with a JEI category; the soils are the `arcforge:cloche_soils` data map (how
  each looks, and how fast crops grow in it: Loam 1.25x). Any ordinary crop from another mod (a `CropBlock`) that no
  recipe names still grows in dirt or Loam, giving its own drops. Every speed and cost is in config (`farming.cloche`).
  - **Glass Cloche** (unpowered): a glass bell on a treated-wood planter. A soil (dirt, Loam, sand, or soul sand for
    nether wart), a seed and 100 mB of water a harvest; about 2 minutes a crop. The harvest drops out of the bottom
    into what's under it. Its faces are fixed, with no Sides tab.
  - **Grow Chamber** (FE and water): steel and Pressure Glass with a grow lamp, 3x as fast, 24 FE/t. Side
    configuration, Speed and Energy upgrades.
  - **Hydroponic Cell** (FE and Nutrient Solution): no soil, 6x as fast, 48 FE/t and 50 mB of Nutrient Solution a
    harvest. Carbon Dioxide, if it has some, makes it 1.25x faster again. Only it grows saplings (6 logs, and sometimes
    a sapling, sticks and apples), flowers (2 a harvest) and Hops (no trellis needed).
  - All three take fertilizer (and bone meal) in a fertilizer slot: each harvest uses a nutrient point and grows 1.5x
    as fast, or 2x with NPK Fertilizer. The seed and soil are never used up.
  - Handbook pages, Jade tooltips, and three new Farming advancements (Under Glass, Grow Lights, Soil Not Included).
- **Greenhouse Array:** the farming section's multiblock, a glass building 5x5 to 11x11 and 4 to 8 tall. Greenhouse
  Frame edges, Pressure Glass walls and roof, a Greenhouse Controller in a wall, **Planting Beds** in the floor and
  **Grow Lamps** hanging from the roof. It forms and takes ports like the other arrays.
  - Each bed holds a soil and a seed (put in by hand) and grows it over and over, harvesting into the output ports: the
    `arcforge:cloche` recipes (not the hydroponic-only ones) and any ordinary crop from another mod. Beds are drawn with
    their crop growing on them.
  - Water is all it needs (50 mB a harvest). Each system fed through its ports makes every bed faster: Nutrient Solution
    or fertilizer (x2, plain fertilizer x1.5), Carbon Dioxide (x1.3), heat (HU keeps it at 24°C), and Grow Lamps (FE:
    growth at night and without sky).
  - Climate: the air starts at the biome's temperature (a cold 5°C in the Nether and the End), 6°C warmer by day under
    the glass. Crops grow at full speed from 18 to 30°C and slower outside that.
  - The GUI shows its temperature, each system's state and the resulting growth speed. Every value is in config
    (`farming.greenhouse`). Handbook page with a build view, JEI category, Jade tooltips, and three new Farming
    advancements (Glasshouse, Night Shift, Climate Controlled).
- **Carbon Dioxide**, a new gas.
- **Gas Output**, a new side mode: a machine's own gas comes out of it. The Fermenter gives out Carbon Dioxide through it,
  the Air Separator Nitrogen, the Haber Reactor Ammonia and the Biogas Digester Biogas.
- **Silver, Nickel, Tungsten and Invar Gears and Rods.** Every metal with a plate now has a gear and a rod too, pressed
  in the Metal Press like steel's (4 ingots make a gear, 1 ingot makes 2 rods), and tagged `c:gears/<metal>` and
  `c:rods/<metal>` for other mods' recipes.

### Changed

- **Fermenter.**
  - A new additive slot: Dried Hops add 20% Ethanol, one lasting 4 operations (`#arcforge:fermenter_additives`).
  - Dried Grain and Dried Sorghum ferment in half the time of wheat and sorghum.
  - It gives off Carbon Dioxide (1 mB per mB of Ethanol) through Gas Output faces. With none set, or with its tank
    full, the gas goes into the air, so it never stops the Fermenter.
  - The GUI is rearranged to fit the additive slot and a Carbon Dioxide tank: the Ethanol tank is now on the right.
- **Plate and gear textures:** every plate, gear and rod now uses Cody's plate and gear style, recoloured per metal:
  rods are redrawn to match, with a tonal outline instead of black.
- **Chemical Reactor.** A second item slot, for recipes with two items (`second_item_input`): items sort into the two
  slots like fluids into its two tanks, one kind to a slot. The status moved under the arrow to make room.
- **Loam Farmland** can be enriched (NPK Fertilizer); Jade says so.
- **Turbine lubricants are data-driven:** any fluid in `#arcforge:lubricants` (Heavy Oil and Seed Oil). The Steam and
  Gas Turbine Arrays' lubricant gauge shows the oil in the tank.

### Fixed

- **Heat Meter between Thermodynamic Conduits passed no heat.** It couldn't tell how hot heat from a conduit was,
  so it passed it on at 20°C, which nothing takes: the conduits backed up and a Gas Turbine Array vented its
  exhaust. It now carries the conduit's temperature.
- **Thermodynamic Conduits could get stuck full of 20°C heat** (from the Heat Meter bug above) and never move
  anything again. They no longer take heat at 20°C, and drop any they already hold.
- **Gas Turbine Array end caps went dark** when a block sat right against the middle of the end, such as a
  conduit or Heat Meter on its port. The cap is now lit by the brightest block in front of it.
- **Gas Turbine Array combustor:** the slotted grille on its sides and underside was squashed sideways. It now
  looks the same all the way around, as it does on top.

## [2.1.0] - 2026-09-29

### Upgrading

- **New config keys** (`oxyFuel`, the Firebox's and Fuel Burner's oxygen tank, `meters`, `chargepad`) get their
  defaults; nothing to redo.
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
  anyone can use them, as before, until someone opens one: the first player to open its screen becomes its owner.
  On a shared server, open your own machines before someone else does. Ops bypass security
  unless `security.opsBypass` is false; `security.enabled = false` turns it off.
- **Burner fuel data packs.** `arcforge:burner_fuels` entries take an optional `"gas_turbine": false` to keep
  a fuel out of the Gas Turbine Array. Entries without it are burned there, so a pack that adds a slow,
  dirty fuel may want to set it.

### Added

- **Advancements.** An Arcforge tab, starting from the Engineer's Handbook, with six branches (Getting Started,
  Steel, Processing, Steam, Chemistry, Gear) and four challenges. Each one says what to do next.
- **Oxy-fuel.** The Firebox and Fuel Burner take oxygen through an Oxygen face. With it, they burn 300°C hotter (up
  to 1,600°C) and make 25% more heat per fuel, for 0.25 mB of oxygen a tick. Without it, nothing changes. Values
  under `oxyFuel`, `firebox` and `fuelBurner` in the config.
- **Conduit Cover.** Right-click a conduit to hide it inside a clear, outlined pane; it keeps working and
  connecting as before. Right-click the cover with any full block to copy that block's look, sneak-right-click
  with an empty hand to clear it, and take it off with the Wrench (Dismantle). 8 from steel plates and glass.
- **Meters.** Energy, Heat, Fluid and Gas Meters pass flow from their left side to their right, at up to the
  Arcforged conduit rate, and show the live rate on their screen. Set a threshold and Above/Below in the GUI for a
  redstone signal; comparators read the rate.
- **Chargepad.** Stand on it to charge the FE items in your hand, hotbar and armour slots. Feed it through the
  energy port on its back.
- **Settings Card.** Sneak-use it on a machine, conduit, Vault or multiblock to copy its setup (sides or ports,
  redstone mode, auto-eject, and extras such as the Electrolyzer's vents, the Arc Quarry's area and filter or
  a boiler's pressure); use it on another of the same kind to paste. Multiblocks must be the same size; ports
  paste relative to the structure, so a card from an east-west turbine fits a north-south one. Conduit filter
  settings paste onto sides that already have a filter (others are skipped and counted). It never copies
  items, fluids or upgrades. Sneak-use in the air to clear it.
- **Machine security.**
  - Every machine, storage block and multiblock records who placed it. Each player picks a default of
    **Public**, **Trusted** or **Private** at the new **Security Terminal**, and lists the players they trust.
  - A **Security** tab on every machine screen shows the machine's mode (Public, Trusted or Private) and lets the
    owner pick another for that machine; picking their profile's mode makes it follow the profile again.
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
  lists every colour. The colour is in the conduit itself: an Energy or Thermodynamic Conduit's core stripe
  takes it (dim when idle, lit when carrying power; a hot thermal one brightens with its temperature), and
  the glass of Item, Fluid and Pressurized Conduits is tinted with it.
- **Gas Turbine Array** (Hardened). A 3x3 tube 5 to 9 long that burns Naphtha, Light Oil, Ethanol or Hydrogen
  straight to FE.
  - It burns up to 560 HU/t per block of length at 1.5 FE per HU (less for fuels burning under 1,200°C): a
    9-long array makes up to 7,560 FE/t on Naphtha, under the Superheated Steam Turbine Array's 10,080.
  - One end is the intake, which needs air in front of it; the other is the exhaust. A quarter of the fuel's
    heat leaves the exhaust at half its burn temperature, through Heat ports into a Steam Boiler Array (the
    combined cycle, about 1.94× the burner route), a Heat Cell or a Thermodynamic Conduit, or vents as heat haze.
  - The new **Throttle** redstone mode sets the fuel burned by signal strength (1-15). Starting takes a
    second of ignition; running dry is a flameout, with a short wait before it relights.
  - The rotor spools up and coasts down, and a whine climbs with its speed. Through its windows: a compressor of
    many thin blades in stages that narrow toward a glowing combustor, and turbine stages that widen toward the
    exhaust. Heavy Oil lubricant adds 8%.
  - New parts: the **Turbine Blade Set** and **Combustor**. JEI lists its fuels; Jade, a Handbook page and a
    config section `[gasTurbineArray]`.
- **Throttle Lever.** An aircraft-style throttle quadrant with a signal of 0 to 15. Look at it and scroll the mouse
  wheel to move it (a popup under the crosshair shows the signal), or use / sneak-use it to step it up or down.
  The arm pivots on its axle, swinging back at 0 and forward at 15, and the gauge beside the slot lights with the
  signal. It powers the block it's on like a lever, so it sets a Gas Turbine Array's Throttle mode directly.
  Crafted from a lever, a comparator, redstone and a Steel Plate.
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

- **Machine tabs.** Collapsed tabs now use the GUI panel's own grey. Open tabs fit their content: the Energy,
  Heat and Pressure tabs on the left no longer leave a blank strip. The Ports tab grows to fit its longest line
  and starts its rows under the header when there's no auto-eject button. The Redstone tab keeps an even margin
  round four buttons.
- **Energy tab.** An idle machine shows a grey "0 FE/t" rather than a red "-0 FE/t".
- **Meters.** The front screen is a bezel framing an LCD centred on the block, and the reading is centred on it.
- **Fermenter.** The model is redone to the texture guide, without the three loose protrusions. Its default
  ports are built in: input on top, output under the base, energy on the back.
- **Gas Turbine Array rotor.** The combustor is round, and a bearing on the inside of each end holds the shaft.
- **Status names.** "Intake shut" is now "Intake off", and "Spinning up" is now "Spin-up", so they fit the
  turbine screens.

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
- **New multiblocks start with no ports.** A newly built structure does no IO until you set its ports with the
  Wrench in Port mode. Players nearby get a chat hint when it forms, and the Ports tab says the same. This
  covers the Steam Turbine, Solar Thermal and Gas Turbine Arrays, which used to get theirs automatically.
  Structures you've already built keep their ports.
- **Pressurized Conduits are see-through.** They're pressure glass now, like Fluid Conduits, and show the gas
  inside in its colour, denser the fuller the conduit. Gas that passes straight through (a fast conduit may hold
  none between ticks) still shows while it's moving. Idle, empty ones are clear. They no longer glow.
- **Every port face on a single-block machine shows a port.** Each face that has a side mode by default now
  carries the same 8×8 port plate, centred, in that mode's colour. This includes the Chemical Reactor's and
  Electrolyzer's left and right faces, the Fiberizer's and Infuser's input side, and the Block Breaker's top
  and bottom. Hopper and intake tops keep their art behind the port.
- **Electric Pump:** an 8×8 energy socket replaces the small FE box, and the top shows the output port. The
  **Arc Quarry** shows its output and energy ports.
- **Texture clean-up.** The conduit palettes follow the tier colours. Filter LEDs are cyan (allow), red (deny) and
  steel (unset). Firebox, Induction Furnace and coil textures use flat tones, as do upgrade cards, tool modules,
  dies and gears, and Energy/Heat Cell level bars.

### Fixed

- **Text running off GUIs.**
  - Distillation Array: the screen is wider and its status is clipped, so "No steam" and "No feed" no longer run
    into the tanks.
  - Security Terminal: long messages wrap onto two lines.
  - Heat Cell: In and Out no longer overlap at an Arcforged cell's rate.
  - The Combustion Plant's fuel, the Electric Pump's source, the Arc Quarry's status, the Block Breaker's target
    and the turbine statuses are clipped with "…", with the full text in the tooltip where one exists.

- **The Wrench can be crafted.** Three iron ingots and a copper ingot, early enough to set ports on the
  Carbonizer and Arcforge Furnace before you have steel. It was creative-only before.
- **Wrong port marks on machine textures.** The Block Placer's back showed an input port instead of energy.
  The Electrolyzer and Geothermal Plant bottoms showed ports they don't have, and the Fuel Burner's sides had a
  stray red nub.
- **Conduits: the dark edge along the top flipped sides from block to block.** The top of every conduit now
  has its lit edge on the same side, whichever way each piece points.
- **Energy Conduits: the idle core is two red rows again**, not one red row over a dark one.
- **Energy and Heat Cells:** the window shows its bars at every charge level again (dim where it's empty), its
  lit bars fill the whole window, and the black pixel at its bottom-left corner is back.
- **Jade on a Steam Turbine Array's windows** shows the turbine's exhaust line, as its casings do.
- **Induction Furnace Array screen: the speed ran past its edge.** It now reads "x2/lane"; hover for the full
  line.

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

[Unreleased]: https://github.com/zagdrath/arcforge/compare/v2.3.0...HEAD
[2.3.0]: https://github.com/zagdrath/arcforge/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/zagdrath/arcforge/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/zagdrath/arcforge/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/zagdrath/arcforge/compare/v1.2.0...v2.0.0
[1.2.0]: https://github.com/zagdrath/arcforge/compare/f8e4679...7b3fbce
[1.1.0]: https://github.com/zagdrath/arcforge/compare/d39ded7...f8e4679
[1.0.0]: https://github.com/zagdrath/arcforge/commit/d39ded7
