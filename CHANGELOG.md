# Changelog

All notable changes to Arcforge are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.2.0+26.3`), set by `mod_version` and
`minecraft_version` in `gradle.properties`, and each is released as the tag `vMAJOR.MINOR.PATCH+MINECRAFT` (releases up
to 2.5.0 were tagged `vMAJOR.MINOR.PATCH`). Each Minecraft version has its own branch and its own changelog; this one
is Minecraft 26.3's (`main`). The same version number on two Minecraft versions has the same changes.

- **PATCH** (1.2.0 → 1.2.1): bug fixes and small tweaks. No new content, and worlds and configs carry
  over untouched.
- **MINOR** (1.2.0 → 1.3.0): new content or features, and balance changes. Worlds still load; anything a
  player has to redo (settings that reset, blocks that need re-placing) is listed under *Upgrading*.
- **MAJOR** (1.x → 2.0.0): changes that break existing worlds or remove content: registry names that
  change or go away, or items and blocks that disappear on load.

Changes land under **Unreleased** as they're made; when a version is released, that section gets its
number and date. How to cut a release is in [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

Suggested version: **2.8.0** (minor: oil caverns, sour crude, gas wells, four new refinery units, a scaling Diesel
Generator, hydrocracking and Synthetic Rubber, a retuned oil line, running sounds and model fixes for the oilfield, and
the nuclear tier up to the Fission Reactor; worlds load, but Diesel Generators and Oil Derricks need their new layers
and some refinery lines need a new port, see Upgrading).

### Added

- **The nuclear tier, part one: ores, radiation, uranium chemistry and enrichment.** Civil power only: nothing in it
  makes a weapon, and enrichment stops at 20% U-235. A new **Arcforge: Nuclear** creative tab, a Nuclear chapter in the
  Handbook (ten entries) and a Nuclear advancement branch (ten advancements).
  - **Ores.** Uraninite Ore (stone and deepslate: rare, small veins, mostly below Y 0) drops Raw Uraninite and crushes
    into Uraninite Dust. Galena Ore (common at mid depths) drops Raw Galena, which makes **Lead** like the other metals
    (raw block, dust, ingot, block, plate and the leach chain), with a 10% chance of Silver Dust when crushed. Heavy
    Mineral Sand (near the surface in beach, river and desert sand and gravel; shovel-mined) sifts into Monazite and
    Zircon, which crush into dust. All have their `c:` tags and `world.ores` toggles.
  - **Radiation.** Data-driven sources (`arcforge:radioactivity` on items, blocks and fluids, in mSv/h at a block) and
    shielding (`arcforge:radiation_shielding`: Lead 0.1, Depleted Uranium 0.05, concrete 0.3, iron and steel 0.4,
    water 0.5, stone 0.6, other solid blocks 0.8). The dose rate at a player sums every source within 16 blocks by the
    inverse square and the blocks between, counting containers, tanks, dropped items and what you carry; it's checked
    four times a second and each chunk's sources are cached. The accumulated dose recovers slowly and brings Nausea
    (1,000 mSv), Weakness and Hunger (2,000), Wither (4,000) and radiation sickness (8,000); dying resets it. Mobs are
    unaffected unless `affectMobs` is on.
  - **Geiger Counter**, **Dosimeter**, a four-piece **Hazmat Suit** (a full set lets 5% through), the nine-slot **Lead
    Box** (nothing gets out), and **Lead Blocks**, **Lead Bricks** and **Concrete Shielding**.
  - **Uranium chemistry.** Uraninite Dust is leached with Sulfuric Acid into Uranyl Sulfate Solution, precipitated with
    Ammonia into Ammonium Diuranate and calcined into Yellowcake; Hydrogen, Hydrofluoric Acid (from Fluorite Dust) and
    Fluorine (electrolysed from the acid) take it to UO2, UF4 and **Uranium Hexafluoride**, a corrosive gas only
    Pressurized Conduits and PVC-lined Pressurized Cylinders hold.
  - **Gas Centrifuge Cascade.** A hall of two-tall Gas Centrifuges on a 3x3 to 7x7 raised floor that enriches natural
    UF6 to the target set in its GUI (1% to 20%) with depleted tails (0.25% by default), by the real separative-work
    formula, at 0.02 SWU a tick per centrifuge. UF6 carries its enrichment as a component; tanks only mix the same
    enrichment, and tooltips and Jade show it. Its model: slim columns in rows, header pipework along each row, a
    control panel, a spinning band on each column while it runs and a faint hum. A floor bigger than 3x3 forms as one
    hall (the biggest its casings allow). Its GUI is wider than most, with the FE gauge as tall as the three tanks.
  - **Depleted Uranium.** Depleted UF6, Hydrogen and Steam give Depleted Uranium Dust, smelted into ingots and blocks:
    the best shield, a little radioactive, and a stand-in for lead in the shielding recipes.
  - JEI shows the enrichment (at 3%, 5% and 20%) and every new recipe; Jade shows the cascade's target, tails, SWU
    and status and a cylinder's lining and UF6 enrichment. New config: `ores.uraninite`, `ores.galena`,
    `ores.heavyMineralSand` and the `nuclear` section (radiation, the cascade and PVC lining).

- **The nuclear tier, part two: fuel fabrication and the Fission Reactor.** Civil power only. Eight more Handbook
  entries in the Nuclear chapter and eight more advancements.
  - **Fuel.** Enriched UF6, Steam and Hydrogen make Uranium Dioxide at the gas's enrichment; the Metal Press with a new
    **Pellet Die** presses it into Green Fuel Pellets, which the Induction Furnace sinters (`arcforge:sintering`) into
    **Fuel Pellets**, keeping the enrichment throughout. **Zirconium** by the Kroll route (Zircon Dust, Coal Coke and
    Chlorine to Zirconium Tetrachloride, reduced with Hydrogen in an electrically heated charge), alloyed with Nickel
    in the Arcforge Furnace into **Zircaloy** and drawn into **Zircaloy Tubes**. A **Fuel Rod** is a tube and eight
    pellets of one enrichment (`arcforge:fuel_rod`); it records its fuel type, enrichment, burnup, plutonium, fission
    products and decay heat. Fresh rods are mildly radioactive; spent ones intensely, and hot enough to burn.
  - **Control Rods** (silver), **Graphite Moderator**, **Steel Reflectors**, and **Heavy Water** from the new **Heavy Water
    Tower** (3x4x3 to 5x8x5) by the Girdler sulfide exchange (`arcforge:isotope_exchanging`: Water, Hydrogen Sulfide and
    Steam in each Exchange Tray).
  - **Fission Reactor.** A vessel of Reactor Casing (Reactor Glass allowed), 3 to 7 across and 3 to 9 tall, with a grid
    of Fuel Channels, Control Rod Channels in four banks, moderators, reflectors and coolant (Light or Heavy Water)
    inside. Each second a one-group neutron diffusion over the grid gives k-effective and each channel's power; point
    kinetics with delayed neutrons (a reactor period below prompt critical, seconds above), Doppler and void feedback
    (negative in a water-moderated core, positive in a graphite one), iodine and xenon, decay heat (6.5% fading over
    in-game hours) and burnup with fission products and plutonium. Fuel, cladding and coolant temperatures and vessel
    pressure are tracked; overheated cladding fails and melted fuel ruins the core.
  - **Heat.** A Hot Leg port makes a pressurized core: Hot Coolant goes to the new **Steam Generator** (a 3x4x3 to 7x12x7
    vertical heat exchanger) for High-Pressure Steam. Steam ports alone make a boiling core: **Contaminated Steam** straight
    to Steam Turbine Arrays, which run on it as High-Pressure Steam and turn slightly radioactive. Each channel gives
    4,000 HU/t at full power; a big core passes 1,000,000 FE/t through four to six full-size turbine arrays.
  - **Control and protection.** A three-page GUI (core readouts and rod banks; power, coolant and k-effective trends;
    trip points, pump and options), bank moves from the GUI, redstone (a stop signal SCRAMs; optionally a signal holds
    every bank in) and the machine control API (`bank_a`..`bank_d`, `scram`). Five player-set trips SCRAM the core;
    rods load and unload only while it's shut down. A running core is a strong radiation source through its vessel.
  - **Models.** The reactor: a heavy pressure vessel in a casing frame, a domed closure head with control-rod drive
    housings, coolant nozzles at its ports, and a viewport for each pane of Reactor Glass that glows Cherenkov blue
    (full-bright) while it's critical. The Steam Generator: a tall shell under a wider steam drum. The Heavy Water Tower:
    a squat tower of tray sections. Their blocks (inside too) hide when formed.
  - JEI: Sintering, Isotope Exchange, Fission (rods at 0.711%, 3%, 5% and 20%) and the Steam Generator; Jade: the
    reactor's power, k, temperatures, rods and trips, a channel's rod or bank, the Steam Generator and the tower. New
    config: `nuclear.fissionReactor`, `nuclear.steamGenerator` and `nuclear.heavyWaterTower`.

- **Oil caverns.** In newly generated chunks every oil deposit is a real cavern full of oil: a sealed, flattened,
  roughly round chamber (an ellipsoid about twice as wide as it is tall, its edge slightly uneven) under the middle of
  its patch, between the deposit heights, holding one source block of Crude Oil per bucket of the deposit. A Seep is
  about 1,000–3,000 blocks, a Giant up to 100,000 and about 70 blocks across. A 2-block shell of stone or deepslate,
  laid after caves are carved, keeps caves, aquifers and lava out and the oil in; ores and other features don't
  generate inside it.
  - **One amount.** The deposit's tracked amount is still the truth. Pumping takes from it, and so does anything that
    removes the oil's source blocks (buckets, other mods' pumps) at 1,000 mB a block. The oil in the cavern falls to
    match, a layer at a time from the top down; chunks that weren't loaded catch up when they load.
  - The **Oil Derrick** drills down to the top of the cavern under its bore, and a **Pumpjack** draws on the whole
    cavern. The Seismic Survey Tool gives the cavern's depth.
  - **Surface seeps**: above some Seeps and Pockets in deserts and badlands, a small tar pit of Crude Oil marks the
    deposit below.
  - Crude Oil and Sour Crude Oil source blocks don't burn or spread fire.
- **Sour crude.** Every deposit is sweet or sour: 30% sour, 60% in badlands and deserts (the biome tag
  `arcforge:sour_rich`). Sour deposits give **Sour Crude Oil** (with its bucket), which the Distillation Array refines
  like Crude Oil, but its Diesel and Kerosene come out as **Sour Diesel** and **Sour Kerosene**. The Fuel Burner and
  Firebox Array burn them at 75% of the heat and 100°C cooler; the Diesel Generator, Gas Turbine Array and Jetpack
  refuse them.
  - **Hydrotreater** (a single block, heat and FE): 100 mB Sour Diesel or Sour Kerosene and 30 mB Hydrogen make 100 mB
    Diesel or Kerosene and 30 mB **Hydrogen Sulfide** (a new gas) in 2 seconds, at 300°C or hotter, 20 HU/t and 20 FE/t.
  - **Sulfur Recovery Unit** (a single block, heat): the Claus process. 100 mB Hydrogen Sulfide and 50 mB Oxygen make
    a Sulfur Dust and 100 mB Steam in half a second, at 250°C or hotter and 15 HU/t. Sour crude is a real sulfur
    source: a bucket distilled 14 high, both sour cuts treated, gives about one Sulfur Dust.
- **Natural gas.** About a third of deposits have a gas cap: an empty dome at the top of the cavern (about its top
  fifth), holding 2,000 mB of **Natural Gas** (a new gas) per block. An Oil Derrick that drills into the dome makes a
  gas well.
  - **Gas Wellhead**, placed on a gas well: the gas flows under its own pressure with no FE, 200 mB/t from a full cap,
    slowing to 15% of that as it empties, then a trickle of 40 mB a second. Its flow line faces the way it was placed.
    Modelled on a real gas well's tree: a flanged casing head, master valve, swab cap, a wing with an adjustable choke
    (red handwheel), a pressure gauge and the flow line's flange.
  - Natural Gas burns in the Fuel Burner, the Firebox Array and the Gas Turbine Array: 55 HU/mB, up to 1,250°C
    (`arcforge:burner_fuels`).
  - **Steam Methane Reformer** (a single block, heat at 700°C or hotter): 100 mB Natural Gas and 100 mB Steam make 100
    mB Hydrogen and 25 mB Carbon Dioxide in a second at 40 HU/t: Hydrogen far cheaper than the Electrolyzer's. Its
    Carbon Dioxide leaves through a gas port, for the Carbon Reclaimer or the Greenhouse.
- **Survey Records.** Using the Seismic Survey Tool with Paper in your inventory uses a sheet and gives a Survey Record
  of the chunk: dimension, coordinates, the deposit's kind, the oil left at the time, sweet or sour, and whether it has
  a gas cap, in its tooltip. Right-click one onto a filled map in your inventory to mark the spot with a small oil
  derrick (one per deposit kind); the map's tooltip lists its marked deposits.
- Handbook pages (Sour Crude, Sulfur Recovery Unit, Natural Gas, Steam Methane Reformer, Survey Records), JEI categories
  (Hydrotreating, Sulfur Recovery, Steam Reforming, Gas Wells), Jade tooltips (the Gas Wellhead's flow and gas left,
  the deposit's sulfur and gas cap), the machine control API for the three units, GameTests, and the advancements
  Black Gold Below (swim in a cavern), For the Records, Rotten Eggs, Desulfurized, Claus and Effect (goal), Under
  Pressure (goal) and Hydrogen the Cheap Way.

- **Steam Cracker**, a 3×3×3 multiblock (Steam Cracker Casings round a controller in the middle of the bottom of one
  side): fed heat at 800°C or hotter and steam, it cracks 100 mB Naphtha into 50 mB Ethylene, 25 mB **Propylene** (a new
  gas), 10 mB Hydrogen and 5 mB Heavy Oil a second (Refinery Gas gives more Ethylene and Hydrogen), at 60 HU/t. Ethylene
  and Propylene each have their own port mode. Modelled as a cracking furnace: a refractory-lined box with buckstays and
  peep holes glowing with the fire inside (as bright as it's hot), radiant tubes rising into banded headers on the roof,
  a transfer line to a quench drum, and a short stack on a convection hood.
- **Polypropylene Sheet**: 120 mB Propylene makes 2 in the Chemical Reactor. It's in `arcforge:plastics`, so every
  plastic recipe takes it.
- Handbook page (Steam Cracker), JEI category (Steam Cracking), Jade tooltips, the machine control API, GameTests and
  the advancements Crack of Dawn (goal) and Plastic Fantastic.

- **Hydrocracking.** The Cracking Unit has two modes, picked in its GUI's new Mode tab, saved with it and shown in Jade
  (it never switches by itself, whatever is in its tanks). Catalytic cracking works as before, over the Cracking
  Catalyst. Hydrocracking takes Hydrogen instead of a catalyst and a little more heat (520°C, 36 HU/t): 100 mB Heavy Oil
  and 30 mB Hydrogen make 10 mB Naphtha, 50 mB Diesel, 30 mB Kerosene and 10 mB Light Oil a second. The unit has a
  Hydrogen tank and the new port modes Hydrogen, Kerosene and Light Oil; its third tank holds Refinery Gas or Kerosene by
  mode. Cracking recipes take a `mode` (`catalytic`, the default, or `hydrocracking`) and a `second_input`. JEI has a
  category for each mode.
- **Synthetic Rubber** (EPDM): 60 mB Ethylene and 40 mB Propylene over a Cracking Catalyst make one in the Chemical
  Reactor, the catalyst used up about one time in 50 (a Chemical Reactor item input can now set a `consume_chance`;
  other machines' recipes ignore it). It's in the new item tag `arcforge:raw_rubber`, which the Vulcanizer's recipe now
  reads, so it cures into Rubber exactly as Raw Rubber does; hoppers never put Raw and Synthetic Rubber in its two slots
  at once, so there's always room for the Sulfur. A second use for Propylene, with the advancement Rubber Without Roots.
- Jade warns when a Cracking Unit's tank still holds the other mode's product after a switch (it waits until that's
  drained).
- **Natural Gas in the Steam Cracker**: 100 mB with 50 mB Steam make 35 mB Ethylene, 5 mB Propylene and 30 mB Hydrogen
  (less Ethylene than Naphtha, more Hydrogen, hardly any Propylene).
- **Kerosene fuels the Diesel Generator**: 360 FE per mB (378 in the V12, 396 in the V16), on JEI's fuel page.
- GameTests for the cracking modes, Natural Gas cracking, Light Oil from crude, the sulfur sour crude gives and
  Synthetic Rubber in the Vulcanizer.
- **The Diesel Generator sounds and smokes.** A running loop (a V8's chug, its pitch and volume following the engine),
  exhaust smoke from the silencer's outlet (a few puffs at idle, a plume at full speed, heavier and darker below 90%
  speed), and the outlet's rain flap now lifts with the exhaust and flutters with the firing pulses.
- **Running sounds for the Oil Derrick, Pumpjack and Distillation Array**: the derrick's drive and grinding rotary
  table, the Pumpjack's motor hum and creaking beam, and the column's boiling and bubbling, while each runs.
- **The Pumpjack's ladder can be climbed**, from the ground up to its platform.
- **Fluid conduits show fluid moving through them**: while a fluid flows, each conduit shows at least a stream of it
  along the bottom of its bore, even when the network passes all of it straight through (as gas conduits already did).

### Changed

- **Light Oil from Crude Oil.** A Distillation Array 10 or more tall gives Light Oil from Crude and Sour Crude Oil, out
  of the Diesel and Heavy Oil, so each height makes as much as before: 10 high 80 mB Light Oil (190 Diesel, 130 Heavy
  Oil), 14 high 100 mB (155 Diesel, 65 Heavy Oil). Plastic Sheet (Light Oil and Hydrogen) is now on the main oil line.
- **Catalytic cracking** gives a little Light Oil: 100 mB Heavy Oil makes 40 mB Naphtha, 40 mB Diesel, 55 mB Refinery
  Gas and 10 mB Light Oil (was 40, 45 and 60). The Cracking Unit has a fourth product tank.
- **The Coker** also gives 50 mB Heavy Oil for every 250 mB of Bitumen, from a second product tank (its coke slot moves
  right to make room).
- **The top tier needs oil.** Up to Hardened, any plastic still does. The Arcforged crate, vault, Storage Upgrade and
  Jetpack now need Polypropylene Sheet, and the Arcforged Arc Drill and Arc Saw Plastic Sheet (the Arcforged Fluid
  Conduit and Fluid Tank already took PVC).
- **Bio-ethylene is the slow route**: 100 mB Ethanol now makes 40 mB Ethylene (and 60 mB Water) in 5 seconds (was 60 mB
  in 3 seconds). The Steam Cracker makes Ethylene in bulk.
- The Cracking Unit, Distillation Array, Sour Crude, Sulfur Recovery Unit, Coker, Steam Cracker, Diesel Generator,
  Refinery Fuels, Natural Gas, Vulcanizer, Rubber Dandelion, Bio-Plastics (with a new Plastics by tier page), Crates and
  Vaults, Hydrogen and Oxygen, Oils and Pitch and Chemical Reactor Handbook pages cover these changes.
- Balance check (a bucket of Crude Oil distilled 14 high, Heavy Oil cracked, Bitumen coked, Naphtha reformed, all burnt
  in a V16): 291,000 FE before, 322,000 now (+11%, mostly Kerosene now counting); a full V16 takes 1.55 full-rate
  Pumpjacks (was 1.72).

- Gas Reservoirs, Gas Cartridges, the Flare Stack and Quantum Tunnels refuse corrosive gases (the
  `arcforge:corrosive_gases` tag: UF6 and depleted UF6). A Pressurized Cylinder takes them once lined with 4 PVC
  Sheets, and keeps its lining when broken.

- The Crude Oil, Oil Derrick, Pumpjack, Fuel Burner, Oils and Pitch, Jetpack, Gas Turbine Array and Refinery Fuels
  Handbook pages cover caverns, sour fuels and Natural Gas. The Seismic Survey Tool also says whether a deposit is
  sour and has a gas cap.
- Crude Oil's still and flowing textures are thicker and slower, with a faint oily sheen.
- Refining recipes (`arcforge:hydrotreating`, `arcforge:sulfur_recovery`, `arcforge:steam_reforming`) can take a second
  fluid input (`second_input`).

- **The Diesel Generator scales**: 3 wide and 3 tall, and 5, 7 or 9 long for a V8, V12 or V16, making 3,000, 8,000 or
  15,000 FE/t; the V12 gets 5% and the V16 10% more FE from each mB of fuel. Its fuel tank grows with its length and its
  FE buffer with its output (all in config), keeping their size while it's broken. JEI's fuel page and Jade show each
  engine. It has a new model, a large genset: a sub-base fuel tank and base frame, the radiator with its fan behind a
  ring guard, the V engine with a rocker cover per cylinder, exhaust manifolds, turbochargers (four on the V16),
  aftercooler and silencer with a rain flap, the flywheel housing, and the alternator with louvres, its FE band,
  terminal box and a control panel with a lit screen. The Energy ports now give up to 16,384 FE/t each.
- **The Oil Derrick is a real derrick**: nine blocks tall (legs seven Derrick Frames high), and formed it hides its blocks
  and is drawn as a four-legged lattice tower tapering to a crown platform with a railing, sheaves and a gin pole, over
  a plated rig floor with a rotary table, stair and railing, the drawworks (its drum turning as the line pays out) and
  an engine house, and a ladder up the front left leg. Its hitbox follows the model: walk on the rig floor and climb the
  ladder to the crown.
- The Oil Derrick's and Diesel Generator's Handbook pages show the new builds.
- **The Oil Derrick drills and hoists**: the travelling block feeds slowly down over four seconds with the kelly
  turning, then the kelly stops and the drawworks hoists it back up in a second, easing in and out (it used to jump
  back to the top). Its ladder is nearly twice as wide (11 px), with thicker stiles and rungs.
- **The Flare Stack's flame burns only while it flares**, with no pilot flame, and is much bigger: from a block tall at
  the least up to three and a half at full burn.
- **The Diesel Generator's rain flap is round and flaps**: while it runs, each exhaust pulse throws it up and it drops
  back on the pipe, about three times a second at low speed and six flat out, higher the faster it runs.
- **The Distillation Array's GUI is wider** (like the Gas Centrifuge Cascade's), so its temperature, status and output
  lines have room; the inventory sits centred under it.

### Fixed

- The Flare Stack's flame drew as a black square: its texture now has a clear background.
- The Diesel Generator GUI's output (8,192 / 15,000 FE/t) ran into its label; a wide value now shows short (8.2k /
  15k FE/t), in full on hover.
- Flickering and see-through spots on the large models:
  - **Diesel Generator**: the terminal box's lid, the aftercooler's top, the intake manifold's top and the alternator's
    ends flickered; the turbo exhaust risers' elbows, the radiator core's top, the shroud's ends, the FE cable glands,
    the filler caps' undersides, a gap under the fuel tank's seam band and the open exhaust outlet could be seen into.
  - **Pumpjack**: the gearbox's half-round top could be seen through and its belt guard flickered on its side; the
    walking beam's flanges flickered against its web, the railings' posts against their rails, and a gap ran round
    the top of the plinth.
  - **Oil Derrick**: the railings flickered where their posts and corners met, as did the leg caps under the crown and
    the engine house's roof (the girts ran through it); the front girder hid the stair's steps, and the ladder's stiles
    were open at the top.
  - The round ends of horizontal pipes on large models (the Diesel Generator's header tanks, the Derrick's sheaves and
    drum bearings, the Pumpjack's gearbox) faced inward, so they couldn't be seen from outside.
  - The flat tops of upright round parts on large models (vessels, stacks, filler necks) sat half a facet round from
    their sides, leaving thin see-through wedges round the rim.
  - **Diesel Generator** (more): the turbochargers' feed and outlet pipes could be seen into where they met the
    turbos, the engine mounts flickered against the crankcase's pan rail, the exhaust outlet's collar stood clear of the
    round silencer at its sides, and the fan guard flickered where its rings and spokes cross.
  - **Flare Stack**: its ladder flickered where the rungs met the stiles (and the stiles were open at the top), the
    platform's railings overlapped at its corners, its struts' ends poked out of the stack and under the platform, and
    the round seat left the corners of the base block's square socket showing (it now stands on a bolted square flange).
  - **Distillation Array**: the railings round every platform and the top deck overlapped at their corners and their
    knee rails flickered against the posts; the caged ladder's rungs flickered against its stiles (open at the top), and
    the cage's hoops stopped in mid air with open ends: they're now joined at each bend and bracketed to the stiles.
- Conduits and other glass behind an opaque fluid (Crude Oil, Sour Crude, Heavy Oil) in a Fluid Reservoir, Fluid Tank
  or fluid conduit showed through it. Clear fluids still show what's behind them.

### Upgrading

- Caverns, sour deposits and gas caps only come in chunks generated after this update. The first time a world loads
  with it, the chunks it already has are noted (from its region files); a deposit reaching any of them gets no cavern
  and works exactly as before everywhere (invisible, always sweet, no gas cap), so no cavern is ever cut off against
  old ground. Turn caverns off with `world.oilDeposits.caverns`.
- **Diesel Generators** are now 3 tall: an existing 2-tall one won't form until a third layer of casings is added. New
  config values in `dieselGenerator`: `maxPowerOutputV12`, `maxPowerOutputV16`, `efficiencyV12`, `efficiencyV16`;
  `maxEnergyOutput` now defaults to 16,384 (an existing config keeps its value).
- **Oil Derricks** are now 9 tall: an existing rig won't form until its legs are built up to seven Derrick Frames (37
  frames in all); its old crown blocks are shown again when it first rechecks. A rig mid-way through a hole carries on
  from where it was once rebuilt.
- **Retuned recipes** (Crude and Sour Crude distilling at 10 and 14 high, `cracking/heavy_oil`, `coking/bitumen`,
  `chemical_reacting/ethylene_from_ethanol`, `vulcanizing/rubber`, the Arcforged crate, vault, Storage Upgrade, Jetpack,
  Arc Drill and Arc Saw): a data pack overriding them keeps its own numbers; one replacing `cracking/heavy_oil` should
  add `"mode": "catalytic"` only if it wants to be explicit (it's the default).
- **New products need somewhere to go.** A Distillation Array 10 or more tall on Crude Oil, a Cracking Unit and a Coker
  each make something they didn't before (Light Oil, Light Oil, Heavy Oil) and stop when its tank fills. A column with
  only per-product ports needs a Light Oil port; a Cracking Unit with only Naphtha and Diesel ports needs a Light Oil
  port (or an Output port); a Coker pushes its Heavy Oil out of its Output face (the left, by default), so give that
  face somewhere to put a liquid. Existing Cracking Units start in catalytic mode and run as before.
- New config value: `multiblocks.crackingUnit.secondFeedTankCapacity` (the Hydrogen tank, 16,000 mB).
- **Machine control API:** the Cracking Unit lists its Hydrogen tank after its feed, so its product tanks now start at
  index 2 (and there are four). The Coker has a second product tank (Heavy Oil). The mode isn't exposed through the API.
- New config values: `world.oilDeposits` (`caverns`, `sourChance`, `sourRichChance`, `gasCapChance`, `gasPerBlock`,
  `surfaceSeepChance`, `cavernCheckInterval`), `power.burners.refineryFuels` (Natural Gas and sour fuel values) and new
  sections for the Hydrotreater, Sulfur Recovery Unit, Steam Methane Reformer, Gas Wellhead and Steam Cracker.

## [2.7.0] - 2026-10-14

### Added

- **Crude oil.** Deep underground, the world is split into regions (8 chunks across), and about a third of them hold
  an oil deposit: a round patch of chunks between Y −48 and 8. Regions in deserts, oceans, badlands and plains (the
  biome tag `arcforge:oil_rich`) have one about twice as often, and bigger. There are four kinds: Oil Seep
  (1,000–3,000 buckets), Oil Pocket (4,000–10,000), Oil Field (12,000–30,000) and Giant Oil Field (40,000–100,000), the
  Giant the rarest. Deposits follow from the world seed alone and every well over one shares it. A drained deposit
  never runs dry: each Pumpjack on it still gets a trickle (40 mB a second).
  - **Crude Oil**, a thick black fluid, with its bucket.
  - **Seismic Survey Tool** (a comparator on a steel rod and plates). Use it on the ground to learn whether there's oil
    under the chunk: its kind, roughly how many buckets are left and how deep it lies, or that it's drained.
  - **Oil Derrick**, a 3×3 drilling rig six blocks tall over a deposit: a floor ring of Derrick Casings round an open
    bore with the Oil Derrick Controller in it, Derrick Frame legs on the corners and a 3×3 of frames on top. With FE
    (120 FE/t), water (10 mB/t of drilling mud) and Steel Pipes (one per 4 blocks of hole) it drills a block every 2
    seconds down to the deposit, then strikes oil: the rig comes down, handing back its blocks, and a Well Head caps
    the hole. A rig broken and rebuilt over the same hole carries on where it was. A drill string turns in the bore
    while it runs.
  - **Pumpjack**, a 3×5 skid of Pumpjack Casings round a Well Head, with a post three casings tall in the middle and a
    Pumpjack Controller at the far end. With FE (60 FE/t) it pumps Crude Oil out through Output ports or into
    buckets: 30 mB/t from a full deposit, slowing in a straight line to a quarter of that as it drains, then the
    trickle. It's modelled on a real beam pump: an A-frame post with a ladder and platform, a walking beam with a
    curved horse head over the well, and a gear reducer swinging two big crank counterweights. The beam nods slower as
    the pumping slows.
  - **Steel Pipe** (3 from two steel plates and a steel rod).
  - GUIs, Handbook pages (Crude Oil, Oil Derrick, Pumpjack), JEI categories (Oil Deposits; Oil Drilling), Jade
    tooltips (depth and pipes, pump rate and deposit left, the Well Head's deposit), the machine control API for both
    machines, GameTests, and the advancements Prospector, Black Gold, Nodding Donkey, Cut to Size and the challenge
    Fractionator. All values are in config (`world.oilDeposits`, `multiblocks.oilDerrick`, `multiblocks.pumpjack`).
- **Refinery products from Crude Oil** in the Distillation Array (1,000 mB a batch, 5,000 HU, at 380°C or hotter). What
  it gives depends on the column's height; a column under 6 tall can't distil it, and it makes no Pitch:

  | Height | Refinery Gas | Naphtha | Kerosene | Diesel | Heavy Oil | Bitumen |
  |---|---|---|---|---|---|---|
  | 6–9 | 100 | 200 | – | 350 | – | 250 |
  | 10–13 | 100 | 200 | 150 | 250 | 150 | 100 |
  | 14–16 | 130 | 270 | 190 | 230 | 90 | 60 |

  Steam stripping moves Bitumen into Naphtha, as it moves Heavy Oil on creosote.
  - New fluids: **Refinery Gas** (a gas, in the gas API), **Kerosene**, **Diesel** and **Bitumen**, with buckets for
    the liquids and a port mode each. Kerosene (190 HU/t at 1,150°C), Diesel (210 HU/t at 1,150°C) and Refinery Gas
    (150 HU/t at 1,200°C) are Fuel Burner, Firebox Array and Gas Turbine Array fuels, and Kerosene and Diesel catch
    fire in the world.
- **The refinery's downstream units, fuels and generators:**
  - **Cracking Unit**, a fixed 3×3×5 multiblock of Cracking Unit Casings with its controller in the middle of the
    bottom of one side. With heat (480°C or more, 30 HU/t) and a **Cracking Catalyst** in its slot (8 from clay, sand
    and Nickel Dust; one lasts 50 batches) it cracks 100 mB of Heavy Oil a second into 40 mB Naphtha, 45 mB Diesel and
    60 mB Refinery Gas. Formed, it draws as a fluid catalytic cracker (regenerator and flue stack with a sight port that
    glows as it heats, reactor, riser, standpipes and catalyst hopper), with a form-fitting hitbox.
  - **Catalytic Reformer** (Naphtha + heat at 450°C → Gasoline + Hydrogen, 100 mB → 85 + 60), **Coker** (Bitumen +
    heat at 420°C → Petroleum Coke + Refinery Gas, 250 mB → 1 coke + 50 mB) and **Lubricant Blender** (Heavy Oil + FE
    → Lubricant, 100 mB → 90 mB). Each process is a data-driven recipe type (`arcforge:cracking`, `reforming`, `coking`,
    `lubricant_blending`).
  - **Gasoline** and **Lubricant**, new fluids with buckets. **Lubricant** in a Steam or Gas Turbine Array's lubricant
    tank adds 12% (Heavy Oil and Seed Oil stay at 8%). **Petroleum Coke** works as Coal Coke everywhere (the Arcforge
    Furnace, Graphite, the Gasifier, the Firebox Array, furnace fuel) and is in `#c:coal_coke`. **Asphalt** from
    Bitumen: 8 gravel round a Bitumen bucket.
  - **Gasoline** burns in the Fuel Burner, Firebox Array and Gas Turbine Array (225 HU/t at 1,250°C). The refinery
    fuels' heat and burn temperatures are in config (`power.burners.refineryFuels`). **Kerosene fuels the Jetpack**:
    click a Kerosene bucket or Canister onto it in your inventory.
  - **Diesel Generator**, a fixed 3×2×5 multiblock: an engine-generator set on a skid whose fan and coupling turn as it
    runs. It burns Diesel, Gasoline, Biodiesel or Refinery Gas (420, 400, 340 and 240 FE per mB, the data map
    `arcforge:engine_fuels`) straight to FE, up to 3,000 FE/t after a 3-second spin-up, and throttles back when its
    buffer is full.
  - **Flare Stack**: burns off any gas pushed into it (1,000 mB/t), with a flame at the top of its stack as big as
    what it burns, so a refinery doesn't stall when a gas tank fills.
  - Handbook entries, JEI categories (the four processes and the Diesel Generator's fuels), Jade lines, the machine
    control API for the five machines, advancements and GameTests.

### Changed

- **Distillation Array, rebuilt as a refinery tower:**
  - **Sizes:** 2×2 or 3×3 across and any height from 4 to 16 (was 2×2 at 4, 6 or 8). Its feed rate, heat buffer and
    heat use scale with its cross-section (a 3×3 ×2.25) and height: a 2×2×8 still distils 20 mB/t, and a 3×3×16 90
    mB/t. Its tanks scale with its volume (the defaults are a 2×2×8's). Creosote keeps its recipe: a column 8 or more
    tall gives what an 8-tall one did.
  - **Seven product tanks** in its GUI, lightest on the left; a product the column doesn't make at its height is
    locked. The machine control API lists all seven.
  - **Formed model:** a round refinery column in flanged tray sections, with a sight glass on each tray showing its
    liquid and rising vapour, manways, platforms with railings on every other flange, a caged ladder, a draw-off pipe
    for each product, a vapour line to an air-cooled condenser bank whose fans turn while it runs, and a fired reboiler
    whose burner windows glow with the column's heat. Ports are nozzles from the tower out to the block face.
  - **Form-fitting hitbox:** once formed, each block's outline and collision follow the parts of the model in it.
- **A formed Steam Turbine Array is one target.** Looking at any part of it outlines the whole array as one box, and
  each of its blocks is clicked as a full block, instead of the outline following the model block by block. You still
  walk on and around the model itself.

### Fixed

- **Machine and multiblock sounds come back after they cut out.** When the game's sound engine dropped a running loop
  (on an audio device change, a resource reload or when it ran out of channels), the machine thought it was still
  playing and stayed silent until you rejoined. This hit every machine and array loop, the Steam and Gas Turbine
  Arrays, the jetpack and the Arc tools. They now start again on their own.
- **Breaking or finishing a big shell multiblock no longer stalls the game.** After a Steam Turbine, Steam Boiler or
  Gas Turbine Array broke, every one of its casings searched for a structure to form, which took seconds on a big
  turbine, so its model lingered and rebuilding it was slow.
- **Steam Turbine Array windows no longer fill with slats at a slant.** The stage rings now only line the lower half of
  the casing, so the upper panes show the blades from low and side angles.
- **A Steam Boiler Array set to Superheated makes Superheated Steam again.** A boiler that had made a lower grade
  (climbing from cold, or after a dip in heat) kept making that grade until its steam tank emptied, and a tank that
  turbines keep drawing from never empties, so it stayed on High-Pressure at 1,300°C until its redstone was toggled.
  Once hot enough for a higher grade, it now lifts the stored steam to it, paying the difference in heat per mB
  (it gives out what it holds meanwhile), then makes the new grade.
- **The Tree Cutter fells trees grown together into huge clumps.** Trees joined into more than `maxLogsPerTree` logs
  (256) were left standing. They now come down `maxLogsPerTree` logs at a time, the highest first, and the trunks
  left after their leaves are gone still count as part of the tree.
- **Glass conduits show through tanks and windows.** The gas or fluid in a Reservoir or Fluid Tank, the contents of a
  glass conduit, and what shows through the windows of a Steam Boiler, Firebox, Distillation or Thermal Evaporator
  Array or an Arcforge Furnace hid the glass of any conduit (and any glass or water) behind it, leaving only the
  conduit's contents floating there.
- **The Industrial Water Intake's pipe has a proper end cap.** Its ends showed a scrap of the pipe's side texture.

### Upgrading

- **Built Distillation Arrays keep forming** and keep their tanks and heat. Formed, they now draw as the refinery
  tower, so their casings and tray windows no longer show. A 2×2 column under 8 tall now has smaller tanks than before
  (half at 4 tall); fluid already over the new size stays until it's drawn off.
- **New config keys:** `world.oilDeposits`, `multiblocks.oilDerrick` and `multiblocks.pumpjack` get their defaults.
  The `multiblocks.distillationArray` values keep their meaning, now for a 2×2 column (4 tall for the feed rate and heat
  buffer, 8 tall for the tanks) and scaled for other sizes.
- **Oil deposits** follow from the world seed alone, so existing worlds have them, under old chunks and new.

## [2.6.1] - 2026-10-12

### Added

- **`/arcforge debug conduits`** (admins): the conduit networks in your dimension, busiest first, each with its
  sources, targets (input sides), calls into machines' insert per tick and contents updates sent to players per second.

### Changed

- **Conduits use less server time.** They move the same amounts, shared out the same way:
  - Energy conduits move each tick's pull and push in one transaction. A machine that takes nothing (full) is left out
    for up to 8 ticks, asked first with a simulated insert when its wait ends, and fed straight away once it has room
    again. Networks with nothing stored skip the push entirely.
  - The FE and heat a network holds are only split back into its conduits when they're saved or the network changes,
    not every tick.
  - Item conduits work out once per network which inputs each output can reach, and an output with nothing to give is
    tried less often (at most 8 ticks apart, so only Hardened and Arcforged conduits notice).
  - Fluid, pressurized and item conduits send what they hold in a small packet instead of a full block update, at most
    once a tick per conduit, and a fluid level only once it has changed by 1/32 of a conduit.
- **Cube multiblocks** (the Arc Crushing, Induction Furnace, Metal Pressing, Superheater and Condenser Arrays and the
  Industrial Water Intake) check for a redstone signal only when a casing's neighbour changes, not across every casing
  every tick, and skip it altogether on Ignore. An idle Condenser Array with no water doesn't look for somewhere to push
  it.

### Fixed

- **The Steam Turbine, Steam Boiler and Gas Turbine Arrays' models no longer vanish at some angles.** They were only
  drawn while the chunk section holding the structure's corner block was visible; now they're drawn whenever any part
  of the structure is in view, and their draw distance counts from the nearest part of the structure.
- **The Steam Turbine Array's hitbox matches its model.** It's now taken from the model itself, pixel for pixel,
  instead of being built separately in rough steps, so what you see is what you click and walk on.
- **Every part of a big Steam Turbine Array can be clicked.** Parts deep inside a turbine 5 or more blocks across
  (the middle of a big casing, the pedestals between casings) open its GUI too, where clicks used to go through them.
- **Steam Turbine Array windows show the blades from any angle.** The stage rings between the blade rows are now a
  shallow lip at the casing wall, so they no longer hide the rotor when you look in at a slant.

## [2.6.0] - 2026-10-11

### Added

- **API 1.2.0:** `SideModes.MACHINE_LINK` (`"machine_link"`), the mode the machine control API reads on a face a Link
  Conduit is plugged into (see Upgrading). The API jar is `arcforge-api-1.2.0+26.3.jar`.
- **Iolite.** A new ore, found only in the End, in End Stone: a little on the main island and more on the outer
  islands, Y 0 to 80. It needs an iron pickaxe and drops Iolite Crystals (Fortune works; Silk Touch drops the ore). There
  is a Block of Iolite (9 crystals) and Iolite Dust (the Arc Crusher gives 2 per ore, the Arc Crushing Array 4, and a
  crystal crushes to 1); dust and ore smelt back into a crystal. Tags `c:gems/iolite`, `c:ores/iolite`, `c:dusts/iolite`,
  `c:storage_blocks/iolite` and `c:ores_in_ground/end_stone`. Advancement "Edge of the World".
- **Link Conduit.** A thin cable with an Iolite core (Iolite Crystal, copper and Plastic Sheet, 8 at a time) that links
  one machine or multiblock to one Wired Machine Link, through up to 64 blocks of cable. It carries nothing itself,
  never branches and joins no other conduits; its core glows purple while the link is live.
  - The face it plugs into (any face of a single machine, any outer face of a formed multiblock) becomes a **Wired
    Machine Link port**: purple, locked, and shown as "Wired Machine Link" in the machine's Sides or Ports tab and in
    Jade. The GUI, the Wrench, Settings Cards and the machine control API leave it alone while the cable is there, and
    taking the cable off gives the face back the mode it had.
  - A machine takes one Wired Machine Link.
- **Wired Machine Link.** A casing with Iolite accents and a status screen (Hardened Alloy, Iolite Crystals, steel
  plates and a Link Conduit) that works as a remote set of the linked machine's ports.
  - Each face other than the cable's can be set to any port mode the machine has, and works exactly as that port would
    on the machine: items, fluids, gases, FE and heat go straight to and from the machine's own inventories, tanks and
    buffers, with no limit of its own. Conduits connect to it as they would to the machine. Output faces push with the
    machine's auto-eject, and Energy and Heat faces give out what the machine gives out.
  - Its GUI shows the machine it's linked to (for example "Steam Boiler Array 7×7×16"), the face the cable is on, the
    cable's length, and a face grid like the Sides tab. A broken or overlong cable, a cable to nothing, a machine that
    already has a link, or one whose owner hasn't let you use it shows "Not linked" and why.
  - Jade shows the linked machine and each face's mode, and on the machine, its link port. Handbook entry, JEI
    information, and the advancement "Long Arm" (new trigger `arcforge:machine_linked`).
- **Industrial Water Intake.** A 3×3×3 multiblock pump house that stands in a body of at least 400 connected water
  sources and pumps 8,000 mB/t of water (×1.25 in rivers and oceans) for 0.25 FE per mB, enough for a full-size Steam
  Boiler Array. Its Output ports push as fast as it pumps, so they can feed a boiler's water port directly. It has its
  own GUI, Jade tooltip, Handbook page and JEI build view. The water isn't taken from the world. Settings in
  `power.steam.waterIntake`.
- **Heat Pipe.** A late-game block for moving a lot of heat between multiblocks. Heat Pipes in a straight line form one
  pipe of up to 32 blocks that joins only the heat ports or heat faces at its two ends, and carries up to 262,144 HU/t
  from the hotter end to the colder one, losing 0.5% per block of length. It's one unbroken steel jacket with flanged
  caps at its ends, and a window band whose core glows with the heat it carries. Jade shows its length, loss, rate and
  temperature. Settings in `power.heat.heatPipe`.
- **"Limited by connections" warning.** The Firebox Array, Fuel Burner, Firebox, Geothermal Plant, Solar Thermal Array,
  Steam Boiler Array, Steam Turbine Array, Gas Turbine Array, Thermoelectric Plant and Combustion Plant show a yellow line
  in their GUI and in Jade ("Limited by connections: 3,200 / 50,400 HU/t", or FE/t, or mB/t of steam) when, for a few
  seconds, their buffer stays full while they give out less than they could. It isn't shown while the machine is idle
  or has nothing connected. A Steam Boiler Array shows it while it vents steam that nothing takes. Settings in
  `power.outputLimit`.
- **Creative storage.** A Creative Fluid Tank, Pressurized Cylinder, Energy Cell and Vault, trimmed in pink, in the
  creative tabs. They have no recipe, and broken they drop nothing (the Wrench still picks them up).
  - **Tank, cylinder and vault:** the first liquid, gas or item put in sets it for good. Taking from it never runs it
    down, more of the same is destroyed, and anything else is refused.
  - **Energy cell:** always full. It gives any FE it's asked for and destroys any FE put in.
  - They move things with no rate limit, and their GUIs read Infinite.
  - Sneak-use one with an empty hand, or press Clear in its GUI's new Creative tab, to empty it.
- **Gas Reservoir.** A connected 64,000 mB pressure vessel for gases: heavy steel corners and edge beams on the outside
  of the merged vessel, clear glass between, and the gas drawn as an even tint by fill. Pressurized Conduits only; Gas
  Cartridges fill and empty it; comparator; a broken block keeps its share.
- **Item Reservoir.** Bulk item storage that forms one store from blocks making a solid box (rims round the store's
  outside, riveted bands at both ends), 20 slots a block, a scrolling GUI with search, hoppers, conduits and item
  handlers on any block, and a comparator. Broken blocks spill their share; wrenched ones keep it.
- **`/arcforge config reset`** for server admins (permission level 3), in game or from the console. `reset all` lists the
  settings that differ from their defaults, `reset all confirm` resets them, and `reset <path>` resets one setting or a
  section (tab completes). The server's `arcforge-common.toml` is saved at once, so there's no file to edit.

### Changed

- **Conduit Covers are now Conduit Facades**, one panel per side as Encoded Logistics' Cable Facades. A facade covers
  one side of a conduit, connected or not, and the conduit keeps connecting and working through it; a connected side's
  arm comes through a hole in the panel, and an output's flange shows through like a port.
  - Right-click a conduit with a facade to put it on the side you're aiming at; a ghost panel shows where it will go.
  - Right-click a facade with a full, non-translucent block to dress it in that block's look. One block is used, and
    the block it had comes back. Sneak-right-click with an empty hand to take the block back, then again to take the
    facade off. Mining it, or the Wrench in Dismantle mode, takes it off too, and pick block gives that facade.
  - Facades dressed in the same block tile like the real block, tinted blocks (grass, leaves) keep their colours, and
    a solid facade hides the face of the block behind it. Six facades make the conduit a full block to walk on.
  - A blank facade is a riveted steel panel (the old cover was a clear pane). Crafted 6 at a time from 3 Iron Nuggets
    and a Steel Plate. Server admins can stop blocks being copied with `logistics.conduitFacades.blocklist`.
- **Sifter Meshes take Unbreaking.** Enchant one at an enchanting table or with a book on an anvil, and it wears as a tool
  does with Unbreaking: Unbreaking III makes a mesh last about four times as long (a Tungsten Mesh about 4,000 blocks).
- **The Arcforged Energy Conduit carries 262,144 FE/t** (was 65,536), and each conduit holds one tick's worth to match.
  The **Arcforged Energy Cell** moves 262,144 FE/t in and out (was 131,072). One line carries a 7×7×19 Steam Turbine
  Array on lubricant (250,253 FE/t); a full-size 7×7×27 needs two (see the turbine's new sizes below). The Quantum
  Tunnel's FE, which moves at the Arcforged conduit rate, follows it. Conduit and cell rates are now in config
  (`power.energyConduits`, `power.energyCells`).
- **Condenser Arrays condense faster.** 400 mB/t per cube in open air (was 120) and up to 800 (was 400): a 7×7×7
  condenses 5,081 mB/t in open air and up to 10,163 with water or ice around it, so with some water or ice against it
  one keeps up with a full-size Steam Turbine Array's 5,880 mB/t of exhaust. Its Output ports now push as fast as it
  condenses (they pushed 100 mB/t a face).
- **The Carbonizer takes Speed Upgrades, and the Distillation Array Speed and Heat Upgrades,** in a new Upgrades tab,
  with the usual rules (Speed bakes or feeds 2^(n/2) times as fast; Heat uses 0.8^n of the heat per batch), so fuel
  production can keep up with a maxed Firebox Array.
- **The Arcforged Thermodynamic Conduit carries 51,200 HU/t** (was 3,200), and each conduit holds one tick's worth to
  match, so one conduit line can carry a big multiblock's heat. Wrought, Tempered and Hardened stay at 50 / 200 / 800
  HU/t, and the Arcforged Heat Cell and Thermal Capsule keep 3,200 HU/t. The Quantum Tunnel's heat, which moves at the
  Arcforged conduit rate, follows it. All four rates are now in config (`power.heat.thermodynamicConduits`).
- **Bigger Steam Boiler Arrays make more steam from the same heat.** Heat per mB is multiplied by (sections ÷ 3)^-0.2:
  a 3×3×12 pays 76% of a 3×3×3's, a 7×7×16 51%. Before, every size made the same steam from the same heat.
  Configurable as `sizeEfficiency`.
- **Steam Boiler Arrays climb, and run cheaper hot.** From 100°C a boiler boils with half the heat coming in and
  heats up on the rest, to the top of its grade's band (499°C for Steam, 899°C for High-Pressure, 1,390°C for
  Superheated and Auto). At the top of a band each mB costs 20% less heat (`hotSaving`). A boiler as hot as its heat
  source settles there and boils nearly all its heat. One whose heat stops keeps boiling from what it holds as it
  cools, but never below its set grade's temperature.
- **Steam Boiler Arrays vent overpressure.** With a full steam tank a boiler vents what it boils instead of stopping.
  Heat that pushes it past the top of its band is vented too, for example after its pressure is turned down. It
  shows Venting, with a jet of steam and a hiss from its roof.
- **Steam's energy balance counts the new boilers.** The best heat-to-FE route is now the biggest boiler at the top
  of Superheated's band (a 7×7×16), 10.13 FE per HU (was 4.72). The Electrolyzer's floor rises with it: 100 mB of
  water costs at least 303,789 FE (was 141,600), so hydrogen still can't make free power. JEI's steam page shows each
  grade's heat per mB as a range.
- **No large multiblock is more than 7×7 across.**
  - **Steam Boiler Array:** a footprint 3–7 by 3–7 (was 3–7 by 3–9), 3 to 16 tall (was 12).
  - **Firebox Array:** 3–7 by 3–7 across (was 3–7 by 3–9), 3 to 7 tall (was 5).
  - **Steam Turbine Array:** a cross-section 3–7 by 3–7 (was 3–7 wide by 3–9 tall), 3 to 27 long (was 15).
  - The Superheater, Condenser, Battery and Distillation Arrays are unchanged.
- **Each large multiblock's largest size keeps the largest output.**
  - **Firebox Array:** the largest (7×7×7) makes 50,400 HU/t (`heatPerTickAtMaxSize`) and a smaller one its share by
    volume (was 160 HU/t per block): a 3×3×3 makes 3,967 HU/t (was 4,320).
  - **Steam Boiler Array:** the largest (7×7×16, 87.1 sections) boils with up to 50,400 HU/t (`maxHeatPerTickAtMaxSize`)
    and a smaller one its share by sections, about 579 HU/t each (was 600): a 3×3×3 boils with up to 1,736 HU/t (was
    1,800).
  - **Steam Turbine Array:** still 40 mB/t per section, so the largest (7×7×27, 147 sections) takes 5,880 mB/t (was
    4,200 for a 7×9×15) and on Superheated Steam makes 329,280 FE/t before its bonuses, 355,622 with lubricant and
    388,550 with the vacuum bonus as well. That's more than one Arcforged Energy Conduit carries (262,144), so give it
    a second energy port, or raise `power.energyConduits.arcforged` and `power.energyCells.arcforged`.
- **The Steam Turbine Array's model is rebuilt for the longer sizes.**
  - Each casing is as long as its diameter calls for. A long set gets up to three double-flow low-pressure casings,
    each fed by its own drop of the crossover pipe, a pair of bearing pedestals and a coupling between each two
    casings, and an exciter beyond the generator.
  - Bladed rotors: discs of angled blades in light steel with stage rings between them, more and longer toward low
    pressure, turning with the rotor.
  - Glazed windows along each casing's upper facets, just inside its outer surface, with the standard glints. Nothing
    shows through the glass or the casing: the blades stay inside the inner wall, and the steam chest, valve pipes and
    crossover end inside the casing's wall.
  - Re-textured to the house style: big plated faces have broad shading under their bevels, rivets, straps and seams,
    and long round parts are shaded facet by facet.
  - Jade names each of several low-pressure casings ("Low-pressure casing 2 of 3").
- **A formed Steam Turbine Array's blocks take the shape of its model.** Each casing's and pane's outline and collision
  follow the parts in it, so you walk on the plinth and walkway and round the casings. Loose casings stay full blocks,
  and formed ones still hold levers, buttons and redstone on any face; fluids don't wash them away. Parts in the hollow
  core are carried by the shell block beside them. A block with nothing of the model in it can't be aimed at; break
  another to take the array down. Deep inside a 5×5 or bigger cross-section, which no block's shape reaches, a lid and
  walls just inside the shell stand in for the parts there.

### Fixed

- **Item conduits empty every output slot, not just the first.** Each operation took items from one slot only, the first
  that had any. A machine that keeps topping up its first output slot, such as a Block Breaker, only ever had that
  slot's few new items taken, so its other slots stayed full. An operation now takes from slot after slot, up to the
  tier's items per operation (8 / 16 / 32 / 64).
- **A Steam Boiler Array no longer stops boiling while it heats toward its pressure setting.** Set to High-Pressure
  or Superheated below that grade's temperature, it now keeps making the grade it's hot enough for as it climbs,
  instead of making nothing until it got there.
- **A big Firebox Array's output no longer overlaps its label.** From 10,000 HU/t it reads like "+28.8k HU/t", and the
  Output label is left out when even that doesn't fit; hovering the line shows the exact figure.
- **The Tree Cutter no longer leaves bare trunks standing.** When a big tree's branch grew into a neighbouring trunk, it
  took the neighbour's leaves and the logs above the branch but left the trunk below, a leafless stump it never cut.
  Trees joined like that now come down together, whole.

### Upgrading

- **Large multiblocks over the new size limits un-form** (see Changed): a Steam Boiler Array or Firebox Array 8 or 9
  across, and a Steam Turbine Array 8 or 9 tall, un-form when the world loads (the Firebox Array within two seconds).
  Rebuild them within the limits, up to 7×7 across; every height and length they could have before still fits.
- **Config keys replaced:** `power.burners.fireboxArray.heatPerTickPerBlock` becomes `heatPerTickAtMaxSize` and
  `power.steam.steamBoilerArray.maxHeatPerTickPerSection` becomes `maxHeatPerTickAtMaxSize` (both 50,400: the largest
  array's HU/t, shared out by size). A value you had changed isn't carried over; set the new key again.
- **Conduit Covers become Conduit Facades.** A covered conduit loads with a facade on every side: dressed in the
  cover's look, or blank if it had none, so it still looks like a block (a plain cover is now steel rather than a clear
  pane). Conduit Cover items load as blank Conduit Facades (`arcforge:conduit_cover` is now `arcforge:conduit_facade`;
  recipes or scripts that name the old id should switch). The cover recipe is replaced, and the new config key
  `logistics.conduitFacades.blocklist` gets its default.
- **New config keys**: `world.ores.iolite`, `world.ores.ioliteOuter` and `logistics.machineLink.maxCableLength` get
  their defaults. Iolite generates only in End chunks generated after the update.
- **Machine control API:** a face a Link Conduit is plugged into reads `"machine_link"` through `sideMode` and
  `ports()`, and can't be set while the cable is there (`REJECTED`). See docs/API.md.
- **New config keys**: `power.energyConduits` and `power.energyCells` (per tier) and the `power.steam.waterIntake`
  section get their defaults. The Condenser Array's new defaults (`power.steam.condenserArray.baseRatePerCube` 400,
  `maxRatePerCube` 800) and the Energy Meter's `logistics.meters.energyRateCap` (now 262,144) only apply to new configs:
  an existing config keeps its values until you change them (or `/arcforge config reset` them).
- **Carbonizer upgrade slot.** Saved Carbonizers get an empty upgrade slot when the world loads; nothing moves.
- **New config keys**: `power.heat.thermodynamicConduits` (`wrought`, `tempered`, `hardened`, `arcforged`),
  `power.heat.heatPipe` (`ratePerTick`, `maxLength`, `lossPercentPerBlock`) and `power.outputLimit` (`bufferFullPercent`,
  `shortfallPercent`, `delayTicks`) get their defaults. The Heat Meter's `logistics.meters.heatRateCap` now defaults to 51,200; an
  existing config keeps the value it has.
- **Reservoir renamed to Fluid Reservoir** (`arcforge:reservoir` is now `arcforge:fluid_reservoir`). An alias keeps
  placed Reservoirs, their contents and Reservoir items working; they load as Fluid Reservoirs. Recipes or scripts that
  name `arcforge:reservoir` should switch to the new id. Its textures moved to `block/fluid_reservoir/`.
- **New config keys**: `logistics.reservoir.fluidCapacity`, `logistics.reservoir.gasCapacity`, and the
  `logistics.itemReservoir` section (`slotsPerBlock`, `maxWidth`, `lengthRatio`) get their defaults.

## [2.5.1] - 2026-10-11

### Changed

- **The API jar carries the Minecraft version.** The API is now published as `arcforge-api-1.1.0+26.3.jar` (Maven
  `net.zagdrath.arcforge:arcforge-api:1.1.0+26.3`) rather than `arcforge-api-1.1.0.jar`, since each Minecraft version
  Arcforge supports builds its own. The API itself is unchanged. Releases are now tagged `vX.Y.Z+26.3` and named
  "Arcforge X.Y.Z for Minecraft 26.3", and CurseForge files "Arcforge X.Y.Z+26.3".
- **The Tree Cutter takes any fertilizer.** Its fertilizer slot now accepts Compost, Wood Ash, Basic Slag, Mixed
  Fertilizer, Seed Meal, Digestate and NPK Fertilizer as well as Bone Meal. Each item grows one sapling, as one Bone
  Meal does.
- **Energy, thermal, fluid and pressurized conduits load-balance.** They now draw evenly from every machine they pull
  from, rather than emptying the first one before touching the next. What they push in is shared evenly among the
  machines they feed, taking turns. When one machine is full (or empty, when pulling), its share goes to the others
  instead of being lost for that tick. Item conduits already took turns and are unchanged.

### Fixed

- **Distillation Arrays built against each other no longer merge into one connected texture.** Each 2x2 column keeps
  its own edge beams, tray window frames and roof hatch.
- **Dyed conduits no longer join a differently coloured conduit for a moment when placed.** A new conduit took its
  colour only after it was in the world, so for an instant it joined any neighbour, and items or fluid briefly
  moved between the two colours before they split. This affected every conduit type.
- **Long statuses no longer run into the output slot's frame** ("Output full", for example) in the Gasifier, Hydrothermal
  Carbonizer, Diamond Press, Grain Dryer and Vulcanizer. The status line sits 2px lower, as it does in the Fiberizer.

## [2.5.0] - 2026-10-07

### Added

- **Machine control API for other mods** (first used by Encoded Logistics). A new block capability,
  `arcforge:machine_control`, is on every Arcforge machine and on every block of a formed multiblock (resolving to its
  controller). Through it, other mods can:
  - read a machine's status, progress, current outputs and energy, items, fluids and heat;
  - insert into its input, fuel and catalyst slots and extract from its outputs, always through the machine's own
    rules;
  - change its redstone mode, sides or ports, auto-eject and machine-specific options;
  - read its statistics and listen for status changes, finished operations and faults.

  The API is published separately as `net.zagdrath.arcforge:arcforge-api` (interfaces only, with its own semantic
  version; this release ships 1.1.0). See [docs/API.md](docs/API.md). GameTests check it on every machine and every
  multiblock.
- **Gas API for other mods** (API 1.1.0). Other mods can now store and move Arcforge gases without sorting them from
  liquids:
  - a gas identity (`Gas`), with its id, name, colour, temperature in kelvin and steam grade, and a lookup of every gas,
    other mods' gases included, for search and display;
  - a gas handler block capability, `arcforge:gas_handler`. It is on every machine with a gas tank, on every block of
    such a formed multiblock, and on Pressurized Cylinders. It lists the gas tanks with their gas, amount and capacity,
    and inserts and extracts by gas and amount through each tank's own rules;
  - the same capability on Gas Cartridges and Jetpacks, so another mod's GUI can fill and empty them.

  Amounts are in mB, as for fluids. Gas colours come from a new synced data map, `arcforge:gas_properties`, which packs
  and other mods can add their own gases' colours to. GameTests check the registry, a Pressurized Cylinder, the Haber
  Reactor and Electrolyzer, the gas items, and that every machine a pipe can feed gas into has a gas handler.
- **Machines can be switched off remotely.** A mod using the API can switch any machine or multiblock off. It then
  stops as if its redstone mode stopped it, keeps its contents, and its GUI shows the new status **Switched off
  remotely**. Machines are on unless switched off this way, and the switch is saved with the machine.
- **Machine statistics.** Every machine now counts the operations it completes, the items and fluid it produces and
  consumes, and how long it has spent running and loaded. The totals are saved with the machine. For now, other mods
  read them through the API; the GUIs don't show them.

### Changed

- **Steam Turbine Array: a real turbine-generator set.** Formed, the array hides its casings and glass and is drawn as
  one machine along its axis, from the steam end to the generator end: a steel plinth (a deck on stepped footings, or
  stepped columns when the structure is taller than the turbine needs); turbine casings on one shaft, each a round
  casing split along the middle by a bolted flange line, growing from high to low pressure (3 to 5 long one turbine
  casing; 6 to 9 high and low pressure; 10 to 15 high, intermediate and a double-flow low-pressure casing with a
  flanged crossover pipe from the intermediate casing's top into the middle of the low-pressure casing); bearing
  pedestals with the shaft and couplings between the casings; a steam chest, stop-valve housings and inlet pipes on the
  high-pressure casing; the low-pressure exhaust neck pointing down; and the generator (lighter steel, cooling ribs, a
  terminal box with FE-red bushings, an FE-red band). Casing diameters follow the cross-section's shorter side (the
  low-pressure casing nearly fills it, the high-pressure about half) and the sections share the length (about 15 / 20 /
  30 / 25%, the rest bearings and couplings); a cross-section wider than tall gets a walkway with railings along its
  front. Every casing has an inspection window on both sides with its bladed rotor behind it (more and longer blade rows
  as the pressure drops), turning with the rotor, so spin-up and coast-down show; the generator's coupling turns too.
  Ports get a nozzle from the machine out to their face; the fitted spots are on top over the steam chest (steam), the
  generator end (energy) and underneath the exhaust neck (Exhaust, where a Condenser Array can connect). Jade names the
  section you look at ("Section: Low-pressure casing") and the fitted spots. A new turbine now starts with a steam, an
  energy and an Exhaust port on those spots (move or clear them with the Wrench; turbines already built keep their
  ports). Unformed casings, the GUI and how the turbine runs are unchanged. The old see-through rotor, lining and steam view are
  gone.

### Fixed

- **Nether generation crashed with BetterNether** (with WorldWeaver) installed: `Feature order cycle found` between
  `minecraft:warped_forest` and `betternether:old_fungiwoods`. Nether Sulfur Ore was placed in the same worldgen step
  as BetterNether's ores, which come after it in vanilla biomes and before it in BetterNether's own. It now uses the
  underground ores step, which nothing else uses in the Nether. It generates the same as before. A GameTest checks
  the step in every Nether biome.

## [2.4.0] - 2026-10-04

### Upgrading

- **The config is regrouped into eight categories with subcategories** (Heat & Power, Machines, Multiblocks, Farming,
  Logistics, World Generation, Equipment, Security), so many paths in `arcforge-common.toml` changed: Steam is now
  under `power.steam`, the machines under `machines.processing` / `chemistry` / `automation`, the farming pages under
  `farming.growing` / `cropProcessing` / `farmChemistry` / `automatedFarms`, and `meters`, `chargepad`, `reservoir`,
  `experience`, `quantumTunnel` and `chunkLoader` under `logistics`, `ores` and `haliteBeds` under `world`, and `tools`
  and `foundrySuit` under `equipment`. **Your settings carry over**: the first time it loads, the file's old sections
  are moved to their new places before it's read. A server's config does the same.
- **New client config** `arcforge-client.toml` for each player's own settings.
- **Fermenter second input slot** (for slime cultures). Saved Fermenters keep their crops, additive, output and
  upgrades: the upgrades move up past the new slot when the world loads. Its input, second input and additive slots
  now stack on the left of the arrow.
- **Data packs:** new recipe type `arcforge:culturing` (Fermenter cultures).
- **Steam and power config values reset to their new defaults.** Keys whose meaning or default changed were renamed, so
  old values are not carried over; set them again if you had changed them:
  - `power.steam.steamBoilerArray`: `heatCapacityPerHeight`, `maxHeatPerTickPerHeight`, `tankCapacityPerHeight` become
    `heatCapacityPerSection`, `maxHeatPerTickPerSection`, `tankCapacityPerSection` (per 3×3 of footprint per block of
    height; same defaults).
  - `power.steam.steamTurbineArray`: `maxFlowPerLength`, `tankCapacityPerLength`, `exhaustTankCapacityPerLength` become
    `maxFlowPerSection`, `tankCapacityPerSection`, `exhaustTankCapacityPerSection` (per 3×3 of cross-section per block of
    length; same defaults); `energyCapacity` and `maxEnergyOutput` are replaced by `energyBufferTicks` (400).
  - `power.steam.gasTurbineArray`: `maxFuelHuPerLength` (560) becomes `maxFuelHeatPerLength` (2,500), `fuelTankPerLength`
    (8,000) becomes `fuelTankCapacityPerLength` (32,000); `energyCapacity` and `maxEnergyOutput` are replaced by
    `energyBufferTicks` (400).
  - `power.steam.superheaterArray`: `heatCapacity`, `maxHeatPerTick`, `tankCapacity`, `maxFlow` become
    `heatCapacityPerCube`, `maxHeatPerTickPerCube`, `tankCapacityPerCube`, `maxFlowPerCube` (per 27 blocks; same defaults).
  - `power.steam.condenserArray`: `tankCapacity`, `baseRate`, `maxRate` become `tankCapacityPerCube`, `baseRatePerCube`,
    `maxRatePerCube` (per 27 blocks; same defaults).
  - New: `power.generationMultiplier` (1.0) and the `power.burners.fireboxArray` section.
- **Built arrays keep working.** Saved 3×3 Steam Boiler, Steam Turbine and Gas Turbine Arrays and 3×3×3 Superheater and
  Condenser Arrays load as they were. A Steam or Gas Turbine Array's FE buffer is resized to its new size when the world
  loads (it keeps the FE it held).
- **Chemical Reactor third item slot.** Saved Chemical Reactors keep their items, tanks and upgrades: the upgrades move up
  past the new slot when the world loads. The three input slots are re-stacked in the GUI (y 17/35/53).
- **Thermal Evaporator Array by-product tank** loads empty in saved towers. Towers without a Lithium Brine port keep
  making Salt (the Lithium Brine that doesn't fit is lost).
- **Arcforge Furnace routing:** Coal Coke put in through a port now goes to the fuel slot unless the metal slot already
  holds Coal Coke (it's a Graphite "metal" now). Steel lines are unaffected.
- **Spodumene generates only in newly explored chunks.**
- **New config sections and keys** `power.batteryArray` (with `lithiumCellCapacity` and `powerRegulatorTransfer`),
  `ores.spodumene`, `ores.spodumenePegmatite` and `multiblocks.thermalEvaporator.byproductCapacity` get their defaults.
- **Data packs:** `arcforge:chemical_reacting` recipes may take a `third_item_input` (it needs a `second_item_input`);
  `arcforge:evaporating` recipes may have a `byproduct` fluid. Existing recipes load unchanged.
- **New config sections** `machines.automation.treeCutter`, `machines.chemistry.hydrothermalCarbonizer` and `farming.growing.loam` get their defaults.
- **New upgrade type, Range** (Tree Cutter only). Existing machines' upgrade slots are unchanged.
- **New config sections** `power.burners.flueGas`, `machines.chemistry.carbonReclaimer`, `machines.chemistry.gasifier` and
  `machines.chemistry.fischerTropschReactor` get their defaults.
- **Flue gas is opt-in.** The Combustion Plant, Firebox, Fuel Burner, Firebox Array and Gas Turbine Array gain the Gas
  Output side mode / port (named Flue Gas in their Sides and Ports tabs). Saved burners keep their sides and give off
  nothing until a face is set to it, so existing setups run exactly as before.
- **Data packs:** new recipe types `arcforge:carbon_reclaiming`, `arcforge:gasifying` and `arcforge:fischer_tropsch`, new
  tags `#arcforge:carbon_fuels` (items and fluids) and a new `arcforge:burner_fuels` entry for Syngas.
- **New config sections and keys** `machines.processing.sifter`, `machines.processing.diamondPress` and
  `farming.farmChemistry.biogasDigester.sulfurPerThousandMb` get their defaults.
- **Biogas Digester sulfur slot.** Saved digesters keep their items; the slot list grows by one on load (the digester
  has no upgrades, so nothing moves). New port mode Sulfur (set with the Wrench).
- **Data packs:** new recipe types `arcforge:sifting` and `arcforge:diamond_pressing`.

### Added

- **Arc Quarry finished toast:** when an Arc Quarry finishes, the player who placed it gets a toast (with the blocks
  mined and where), if they're online. Turn it off in the new client config (Arcforge Client → Notifications).
- **Three ways to make Slimeballs:**
  - Crafting: 2 Pine Resin + 1 Kelp (shapeless) make a Slimeball.
  - Chemical Reactor: 250 mB of Latex + 1 Lime Dye make 2 Slimeballs.
  - **Slime culture** in the Fermenter: a Slimeball in the additive slot as the starter culture (never used up), Sugar
    and Kelp in its two input slots (either way round) and 250 mB of water grow a new Slimeball into the output slot in
    60 s (Ethanol takes 10 s). No Ethanol or Carbon Dioxide comes of it, and Dried Hops don't change it. The Fermenter
    gains a second input slot for it. New recipe type `arcforge:culturing`; the amounts and time are in the recipe.
  - A JEI category (Slime Culture) and a Slimeball information page, Handbook lines under Pine Resin, Latex and the
    Fermenter, and GameTests.
- **JEI drag-and-drop onto ghost slots:** drag an item from JEI onto a Conduit Filter entry, an Arc Quarry filter cell
  or an Assembler pattern cell to set it, as clicking with the item does. Fluid and pressurized conduits' filter entries
  also take dragged fluids and gases (and items holding one). Only the slots that take what's being dragged light up.
- **Firebox Array,** a large heat source: a hollow box of Firebox Array Casings with one Firebox Array Controller, 3 to 7
  by 3 to 9 across (in any combination, so it needn't be square) and 3 to 5 tall. Its edges are casings; its walls, floor
  and roof may be Pressure Glass, through which you see the fire burning on its firebrick-lined floor.
  - It makes 160 HU/t per block of its volume (a 3×3×3 makes 4,320 HU/t, a 7×5×9 50,400 HU/t, all the biggest Steam
    Boiler Array can take), with a heat buffer, fuel tank and oxygen tank that scale with it.
  - It burns solid fuels (`#arcforge:combustion_fuel`, at a Firebox's heat per item and 1,100°C; Coal Coke at 1.5× a
    coal's heat and 1,300°C) and any liquid or gas in `arcforge:burner_fuels` (at the Fuel Burner's HU per mB and the
    fuel's own temperature). Oxy-fuel through an Oxygen port adds 300°C, as on the Firebox. It pauses while it's as hot
    as its fuel burns.
  - Ports like the other arrays: Input (fuel), Oxygen and Heat (out, as fast as it burns).
  - Its own GUI, Handbook page, JEI build view and information, Jade tooltip, GameTests, and two advancements (Stoke the
    Fires; the challenge Roaring Inferno for a 7×5×9). All its values are in the new `power.burners.fireboxArray` config.
- **Renewable coal:**
  - **Tree Cutter,** an FE machine that plants saplings from its three sapling slots in a 5×5 area in front of it (in 2×2
    patches with gaps, so spruce, jungle and dark oak grow into big trees) and fells every fully grown tree there: all its
    logs, its leaves and what they drop (saplings, sticks, apples), 2×2 trees included. Saplings refill its sapling slots
    so it replants; the rest goes to 9 output slots. Bone Meal in its fertilizer slot grows the saplings faster. 200 FE
    per log, 20 per sapling; a tree needs natural leaves (so log buildings are safe) and one over 256 logs is left
    standing. Side configuration; Speed, Energy and the new **Range Upgrade** (a block wider on each side per card).
  - **Bio-Coal** and the **Block of Bio-Coal.** Bio-Coal works as coal everywhere: it's `#arcforge:combustion_fuel`,
    burns as long as coal in vanilla furnaces too (the block as long as a coal block), bakes into Coal Coke and Creosote
    in the Carbonizer and crushes into Carbon Dust in the Arc Crusher.
  - **Hydrothermal Carbonizer,** a heat (HU) machine that only runs at 200°C or hotter: 8 biomass + 500 mB Water make
    1 Bio-Coal in 10 seconds for 6,000 HU, and 300 mB of the water comes back out of a second tank. Side configuration;
    Speed and Heat upgrades. A **Discard returned Water** toggle under the returned tank throws that water away
    instead (and keeps the tank empty) when you don't want to pipe it. New recipe type
    `arcforge:hydrothermal_carbonizing` (`ingredient`, optional `count`, `result`, `water`, `water_return`, optional
    `heat` and `time`; the counts, heat and time default to the config).
  - **New tag `#arcforge:biomass`:** crops, seeds, leaves, saplings, sticks, vines, glow lichen, kelp, Press Cake and
    Compost.
  - Handbook pages (Tree Cutter, Bio-Coal), JEI categories (Tree Cutting, Hydrothermal Carbonizing), Jade tooltips,
    GameTests and two advancements (Timber!, Coal That Grows). Values in the new `machines.automation.treeCutter` and
    `machines.chemistry.hydrothermalCarbonizer` config.
- **Carbon capture:**
  - **Flue gas:** set a face of a Combustion Plant, Firebox or Fuel Burner to Flue Gas (a Gas Output port on the Firebox
    Array and Gas Turbine Array) and, while it burns a carbon fuel (`#arcforge:carbon_fuels`: coal, charcoal, Bio-Coal,
    Coal Coke, Carbon Dust, the oils, Creosote, Ethanol, Biodiesel, Biogas, Syngas), it gives off Carbon Dioxide through it
    in proportion to the heat it makes (4 mB per 1,000 HU; the Combustion Plant counts 2 HU per FE). Hydrogen gives none;
    what isn't taken away is vented.
  - **Carbon Reclaimer,** an FE machine: 250 mB Carbon Dioxide + 500 mB Hydrogen → Carbon Dust + 250 mB Water. It never
    costs less than 1.25× the most FE its Carbon Dust could give back (burnt, baked into Coal Coke or gasified, in the best
    setup, worked out like the Electrolyzer's energy balance), so it renews carbon, never energy; by default 800,000 FE
    or that floor. Speed and Energy upgrades (never below the floor).
  - **Gasifier,** a heat (HU) machine that only runs at 800°C or hotter: coal, charcoal or Bio-Coal + 500 mB Steam →
    1,000 mB Syngas, Coal Coke → 1,500 mB, 4 biomass → 250 mB, for 16,000 HU each, with a 25% chance of Wood Ash. Side
    configuration (Syngas out of its Gas Output faces); Speed and Heat upgrades.
  - **Syngas,** a new gas and fuel: in `arcforge:burner_fuels` at 50 HU per mB, burning at 1,150°C (Fuel Burner, Firebox
    Array, Gas Turbine Array).
  - **Fischer-Tropsch Reactor,** with FE and heat, running only between 200°C and 350°C (with the thermostat on, as by
    default, its heat faces take heat only up to 350°C; overheated, it stops and cools): 1,000 mB Syngas → 30 mB Naphtha,
    50 mB Light Oil, 50 mB Heavy Oil and 200 mB Water. Iron Dust in its catalyst slot lasts 16 batches, Nickel Dust 32.
    Each output has its own side mode (Naphtha, Light Oil, Heavy Oil, By-product for Water) and Output faces give out all
    four. Speed and Energy upgrades.
  - Handbook pages (Carbon Capture, Syngas, and a line on each burner), JEI categories (Carbon Reclaiming, Gasifying,
    Fischer-Tropsch), Jade tooltips (the three machines and every burner's flue), GameTests and three advancements (Town
    Gas, Gas to Liquids, Carbon Neutral). All rates, ratios and temperatures are in the new config sections.
- **Sifter,** an FE machine that sieves gravel, sand, red sand and Deepslate Gravel for chances of ore dusts and minerals
  (`arcforge:sifting`, each output with its own chance; a block waits until all its finds would fit). Gravel: flint and
  iron, copper, gold, silver and nickel dust; sand and red sand: gold dust, Nether Quartz, fluorite and bismuth dust;
  Deepslate Gravel: tungsten dust and rarer iron, gold and silver. Arcite never sifts, and every block gives well under
  half a dust even with the best mesh. Side configuration; Speed and Energy upgrades.
  - **Sifter Meshes** (eight rods in a ring) go in its mesh slot; it won't run without one. Steel (base), Invar (chances
    x1.25, 1.5x as fast) and Tungsten (x1.5, 2x as fast); they wear a point a block (256 / 512 / 1,024).
  - **Deepslate Gravel**, a new falling block: the Arc Crusher crushes cobbled deepslate into it.
- **Diamond Press** (Hardened), with FE and heat, only at 1,400°C or hotter: 4 Graphite → a Diamond for 2,000,000 FE and
  240,000 HU over a minute (all in `machines.processing.diamondPress`, set high as a late-game sink). Built round a Metal Press with
  tungsten gears and plates for its anvils. Speed, Energy and Heat upgrades. (Graphite already exists: Coal Coke or
  Carbon Dust baked at 1,500°C in the Arcforge Furnace, and its block.)
- **Sulfur from biogas:** the Biogas Digester scrubs its Biogas, 0.5 Sulfur Dust per 1,000 mB by default
  (`sulfurPerThousandMb`), into a new sulfur slot, given out of the new **Sulfur** port mode.
- Handbook pages (Sifter, Diamond Press, a line on the Biogas Digester), JEI categories (Sifting with each find's chance,
  Diamond Pressing), Jade tooltips (mesh wear, temperature, the digester's sulfur), GameTests and three advancements
  (Panning for Ore, Pressure Makes Diamonds, Scrubbed).
- **Bigger, rectangular steam arrays:**
  - **Steam Boiler Array:** a footprint 3 to 7 by 3 to 9 in any combination, 3 to 12 tall.
  - **Steam Turbine Array:** a cross-section 3 to 7 wide and 3 to 9 tall in any combination, 3 to 15 long, along either
    horizontal axis. Its rotor and blades are sized to the cross-section's shorter side.
  - **Superheater and Condenser Arrays:** any solid box 3 to 7 blocks each way. A 3×3×3 is drawn as before; a bigger box
    is one connected casing with its own textures.
  - Beyond a 3×3 cross-section the twelve edges must be casings; the rest of the walls can be Pressure Glass. Existing 3×3
    structures keep working unchanged.
- **Global power multiplier,** `power.generationMultiplier` (default 1.0, 0.1 to 100): multiplies the FE made by every
  generator (Combustion Plant, Thermoelectric Plant, Steam Turbine Array, Gas Turbine Array), at the point of generation.
  Heat (HU) and steam aren't multiplied, so the heat → steam → FE chain is multiplied once. GUIs, Jade, JEI and the
  Handbook show the multiplied values; generators' output caps and FE buffers grow with it.
- **Lithium,** for batteries:
  - **Spodumene Ore** (and Deepslate), in stone and deepslate everywhere (Y -48 to 48), with richer pegmatite veins up to
    Y 160 under mountains and badlands; a stone pickaxe mines it. Raw Spodumene, a raw block and Spodumene Dust (2 per
    ore from the Arc Crusher, doubled in the Arc Crushing Array; it doesn't smelt). Generation in `ores.spodumene` and
    `ores.spodumenePegmatite`.
  - **Lithium Brine** (a fluid, with a bucket): Spodumene Dust + 250 mB Sulfuric Acid in the Chemical Reactor gives 250 mB;
    the Thermal Evaporator Array drying Brine into Salt also leaves 25 mB per Salt, out of a new Lithium Brine port.
  - **Lithium Hydroxide:** 250 mB Lithium Brine + 100 mB Lye in the Chemical Reactor, giving back 100 mB Brine.
  - **Graphite** (and its block): Coal Coke, or 2 Carbon Dust, baked at 1,500°C in the Arcforge Furnace.
  - Battery parts: the **LFP Cathode** (Lithium Hydroxide + iron dust + Basic Slag in the Chemical Reactor), the
    **Graphite Anode** (Graphite in the Metal Press with the Plate Die) and the **Cell Separator** (from Plastic Sheet).
- **Chemical Reactor third item slot,** for recipes with three items (the LFP Cathode). Items put in through an input
  face sort into the three slots one kind each.
- **Battery Array,** a multiblock energy store: a box of Battery Array Casings 3 to 5 blocks each way (it needn't be a
  cube) with one Battery Array Controller; the edges are casings, the faces may be Pressure Glass. The inside (1 to 27
  blocks) is filled with Lithium Cells and Power Regulators in any mix, at least one cell.
  - **Lithium Cells** set its capacity (Wrought 64M, Tempered 512M, Hardened 4G, Arcforged 32G FE), **Power Regulators**
    its transfer each way (65,536 / 262,144 / 1,048,576 / 4,194,304 FE/t); with no regulators it moves 16,384 FE/t. Both
    come in four tiers, made from the battery parts, Steel Plates and the tier's alloy, each upgrading into the next and
    keeping its charge.
  - The energy is held as a long (past the int limit). It takes in and gives out up to its transfer rate each tick
    through Energy Input and Energy Output ports (set with the Wrench); redstone control pauses the output.
  - The energy is shared between the cells; a broken cell keeps its share on the item, and an array starts from what its
    cells hold. Breaking any block of it un-forms it.
  - Through the glass the cells' windows light up level by level at the array's fill, the controller's display does the
    same and it glows brighter the fuller it is; a comparator on the controller reads it.
  - GUI (stored, capacity, live input and output, transfer limit, cells and regulators), Handbook pages (Lithium &
    Batteries, Battery Array), a JEI build view and a Battery Array Components category, Jade tooltips, GameTests, and
    advancements (Light Metal, Salt of the Earth, Battery Grade, Grid Scale, and the challenge Power Bank for a 5×5×5).
    All its values are in the new `power.batteryArray` config.

### Changed

- **Quantum Tunnel item tooltip** shows the items held as well as the slots used ("9 / 9 slots used", then
  "412 / 576 items"), so a full buffer can be told from one with a few items in every slot.
- **Machines keep their upgrades when dismantled with the Wrench.** The upgrades travel in the picked-up item and are
  back in their slots when it's placed; the machine's other slots still drop beside it. Broken any other way, a
  machine drops its upgrades as before.
- **Carbonizer bakes logs into charcoal:** any log that burns (`#minecraft:logs_that_burn`) gives a Charcoal and 100 mB of
  Creosote in 400 ticks, so a Tree Cutter's logs can go straight in.
- **Loam grows saplings twice as fast** (`farming.growing.loam.saplingGrowthMultiplier`, 2.0), and a tree grown on Loam leaves
  the Loam under its trunk instead of turning it to dirt, so a Tree Cutter's area can be laid in Loam.
- **Gas Turbine Array exhaust:** "Venting to air" now says how much is vented ("Venting 2,420 HU/t to air"), in the
  GUI and in Jade, so a partly undersized heat sink shows how far short it falls.
- **Steam Boiler Array in Jade:** shows the heat it boils with against its maximum ("Heat use 3.2k / 3.2k HU/t"), in
  gold when it is at its limit, so a boiler too small for the heat fed to it is easy to spot.
- **Steam Boiler Array:** boil rate, heat buffer and tanks scale by footprint area and height: 600 HU/t, 100,000 HU and
  16,000 mB per section (width × depth ÷ 9 × height), so a 7×9×12 boils up to 50,400 HU/t. A 3×3 tower is unchanged.
- **Steam Turbine Array:** FE per mB raised to 20 / 36 / 56 (Steam / High-Pressure / Superheated), from 10 / 18 / 28.
  Steam flow and tanks scale by cross-section and length: 40 mB/t and 32,000 mB per section (width × height ÷ 9 × length).
  Its maximum output is no longer a fixed 16,384 FE/t: it is a full flow of lubricated, exhausting Superheated Steam
  (7,930 FE/t for a 3×3×3, 277,536 FE/t for a 7×9×15), shared across its energy ports, with an FE buffer of 400 ticks of
  that (was 1,000,000 FE). Lubricant use scales by sections.
- **Gas Turbine Array:** fuel heat per block of length raised from 560 to 2,500 HU/t (a 9-long array burns 22,500 HU/t,
  about 34,000 FE/t on Naphtha). Its output cap (was 16,384 FE/t) is its full-throttle lubricated output, its FE buffer
  (was 1,000,000 FE) 400 ticks of that, and its fuel tank 32,000 mB per block of length (was 8,000).
- **Superheater Array:** flow (1,000 mB/t), heat use (2,000 HU/t), heat buffer (200,000 HU) and tanks (16,000 mB) are per 27
  blocks of its size; a 7×7×7 moves 12,700 mB/t.
- **Condenser Array:** open-air rate (120 mB/t), cap (400 mB/t) and tanks (16,000 mB) are per 27 blocks of its size; a
  7×7×7 condenses up to 5,081 mB/t.
- **Electrolyzer:** its energy-balance floor follows the turbine's higher FE per mB (and the power multiplier): splitting
  100 mB of water now costs at least 141,600 FE (was 70,800 with eight Energy upgrades), above the recipe's 120,000, so
  Energy upgrades no longer lower it. Brine still costs its recipe's 60,000 FE, down to a floor of 35,400.
- **Pressure Glass** windows also fit the Firebox Array.
- **Runs on every NeoForge 26.3 beta,** from 26.3.0.0-beta up; it used to need 26.3.0.23-beta or later.

### Fixed

- **Config screen names:** Oxy-Fuel, Gas Turbine Array, Fermenter, Security, Foundry Suit, Meters, Chargepad and Loam,
  and 33 settings in them, showed their raw translation keys (`arcforge.configuration.security`). They all have names.
- **Dismantling a Heat Cell duplicated its Insulation Upgrades:** they dropped beside it and also stayed in the
  picked-up item. They now only stay in the item.
- **Tree Cutter fells the whole crown.** It took at most 12 leaves per log and trusted the leaves' stored distance,
  which lags behind a freshly grown tree, so most of an oak's crown was left floating to decay into saplings, sticks
  and apples. It now takes the natural leaves within 6 steps of the tree's logs that no other log holds, as vanilla leaf
  decay counts (so in a forest it stops at the tree, not the whole joined canopy).
- **Tree Cutter stuck on "Output full" with empty outputs:** a tree whose drops couldn't fit even in empty output slots
  was retried for ever. It now keeps what the leaves drop without the leaf blocks when those don't fit, and if even that
  is too much for empty outputs, fells it anyway (saplings and logs first) and pops the rest on top of the machine.
- **Item conduits no longer pull out items their filters reject.** With nowhere to send an item, a network stored it
  in the conduit to wait for room, even when every destination's filter refused it, so a filtered line emptied the
  machine of everything else (a Tree Cutter's saplings, leaves and sticks). Those items now stay in the machine;
  items with no destination connected yet are still held as before.
- **Battery Array screen:** hovering the FE gauge shows the FE stored and the fill percentage again, like the Energy
  Cell, instead of JEI's "Show all recipes" (the battery components stay listed under the controller and casing in JEI),
  and the status LED next to the status line no longer shows a missing texture.
- **Arcforge failed to load on NeoForge 26.3.0.37-beta and later** (`NoSuchFieldError: ModConfig$Type COMMON`).
  NeoForge renamed the COMMON config type to LOCAL; Arcforge now picks whichever one the running NeoForge has. The
  config file is still `arcforge-common.toml`, so settings carry over.
- **Blurry blocks at a distance with Arcforge installed.** The Induction Furnace Array's chamber window (30×30) and the
  Arc Crushing Array's rollers and Metal Pressing Array's press window (24×24) cut mipmapping for the whole block
  atlas, every mod's blocks included. They're now 32×32, so the windows look exactly as before.
- **The Jetpack Mode key defaulted to Pause instead of H.** Minecraft 26.3 numbers keys differently from earlier
  versions. Controls you've already saved keep their binding, so if Jetpack Mode shows Pause, set it to H (or any key)
  in Options → Controls → Key Binds.
- **Conduits stopped feeding a multiblock until broken and replaced.** When a world loaded with an array (Steam Boiler,
  Superheater and the rest) spread across chunks, a conduit at one of its ports could check before the array's
  controller had loaded, find nothing there, and keep that answer; Thermodynamic Conduits then filled up and stopped.
  Conduits now look again until they find the machine. The same fix covers every conduit type.
- **Thermodynamic Conduits stuck full of lukewarm heat.** When a heat source cooled (its fuel ran out), the conduits
  filled with heat too cool for the machine they feed, such as a Steam Boiler Array at 100°C; being full, they then drew
  nothing once the source was hot again, until a conduit was broken. Heat no input can take is now let go as soon as a
  source has hotter heat an input could use. Conduits with nothing to feed keep what they hold.
- **"Missing model" warnings for every liquid block** (`arcforge:creosote[level=0]` and so on): each liquid now has a
  blockstate.
- **The Chemical Reactor and Distillation Array couldn't be built.** Both need a Hardened Fluid Tank, which needed a PVC
  Sheet, and only the Chemical Reactor makes PVC. The Hardened Fluid Tank now takes a Rubber Gasket in its place (from the
  Vulcanizer, no Chemical Reactor needed); the Arcforged Fluid Tank and the Hardened and Arcforged Fluid Conduits still
  take PVC. Every recipe in the mod has been checked to be reachable from a new world.
- **Firebox Array windows** didn't open its GUI when right-clicked; they do now, like its casings.
- **Ports tab icons for the Thermal Evaporator Array's Brine, Salt and Water ports** were missing (a missing-texture
  square); they now have their own.

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

[Unreleased]: https://github.com/zagdrath/arcforge/compare/v2.7.0+26.3...HEAD
[2.7.0]: https://github.com/zagdrath/arcforge/compare/v2.6.1+26.3...v2.7.0+26.3
[2.6.1]: https://github.com/zagdrath/arcforge/compare/v2.6.0+26.3...v2.6.1+26.3
[2.6.0]: https://github.com/zagdrath/arcforge/compare/v2.5.1+26.3...v2.6.0+26.3
[2.5.1]: https://github.com/zagdrath/arcforge/compare/v2.5.0...v2.5.1+26.3
[2.5.0]: https://github.com/zagdrath/arcforge/compare/v2.4.0...v2.5.0
[2.4.0]: https://github.com/zagdrath/arcforge/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/zagdrath/arcforge/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/zagdrath/arcforge/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/zagdrath/arcforge/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/zagdrath/arcforge/compare/v1.2.0...v2.0.0
[1.2.0]: https://github.com/zagdrath/arcforge/compare/f8e4679...7b3fbce
[1.1.0]: https://github.com/zagdrath/arcforge/compare/d39ded7...f8e4679
[1.0.0]: https://github.com/zagdrath/arcforge/commit/d39ded7
