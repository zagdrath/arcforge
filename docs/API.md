# Arcforge machine control API

Other mods can use this API to monitor and control Arcforge machines and multiblocks: read their status, progress,
energy, items, fluids and heat; move items and fluids through the slots the machine allows; change their settings; read
their statistics; and listen for events instead of polling. They can also store and move Arcforge gases in machines,
Pressurized Cylinders and gas items. Encoded Logistics is the first consumer.

- **Artifact:** `net.zagdrath.arcforge:arcforge-api:1.1.0+26.1.2`: API version 1.1.0, built for Minecraft 26.1.2. Each
  Minecraft version Arcforge supports has its own build of the same API version (`1.1.0+26.3` for 26.3), so pick
  the one for your Minecraft version. Compile against it only; Arcforge provides it at runtime.
- **Packages** (all under `net.zagdrath.arcforge.api`):

  | Package | Contents |
  |---|---|
  | `api` | `ArcforgeApi`: version constants |
  | `api.machine` | `MachineCapabilities` (the entry point), `MachineControl`, `MachineOwner`, `StructureSize` |
  | `api.machine.status` | `MachineStatus`, `MachineStatistics`, `MachineListener`, `CompletedOperation` |
  | `api.machine.resource` | `MachineEnergy`, `MachineItems`, `MachineFluids`, `MachineHeat`, `SlotRole`, `EnergyRole`, `HeatRole` |
  | `api.machine.settings` | `MachineSettings`, `SettingResult`, `RedstoneMode`, `MachineSide`, `SideModes`, `MachinePort`, `MachineOption`, `OptionType`, `InstalledUpgrade` |
  | `api.gas` | `GasCapabilities` (the gas entry point), `GasHandler`, `GasTank`, `Gas`, `GasRegistry`, `GasProperties`, `SteamGrade` (since 1.1.0) |
- **Entry points:** the block capability `MachineCapabilities.MACHINE_CONTROL`; for gases, the block and item
  capabilities `GasCapabilities.BLOCK` and `GasCapabilities.ITEM`.
- **Requires:** Arcforge 2.5.0 or later, on NeoForge 26.1.2.

The API jar holds only interfaces, enums, records and constants. It never refers to Arcforge internals, and nothing in it
changes Arcforge's behaviour. Arcforge's own jar contains the same classes, so at runtime the API comes from Arcforge.

## Contents

- [Adding the dependency](#adding-the-dependency)
- [Rules](#rules)
- [Looking a machine up](#looking-a-machine-up)
- [The interfaces](#the-interfaces)
- [Gases](#gases)
- [Version policy](#version-policy)
- [Example consumer](#example-consumer)
- [Coverage](#coverage)
- [Building and publishing the API jar](#building-and-publishing-the-api-jar)

## Adding the dependency

Publish the API to your local Maven repository from an Arcforge checkout (see
[Building and publishing](#building-and-publishing-the-api-jar)), then in your mod's `build.gradle`:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    // The API only, at compile time. Players install Arcforge itself, which contains the API classes.
    compileOnly "net.zagdrath.arcforge:arcforge-api:1.1.0+26.1.2"
    // Optional: run Arcforge in your dev client to test against it.
    // localRuntime files("libs/arcforge-2.5.0+26.1.2.jar")
}
```

If Arcforge is optional for your mod, declare it as an optional dependency in your `neoforge.mods.toml`. Touch API classes
only when `ModList.get().isLoaded("arcforge")` is true, for example from a class that is loaded only in that case. A
capability lookup with no Arcforge installed needs `MachineCapabilities` loaded, and that class isn't there without
Arcforge.

```toml
[[dependencies.yourmod]]
    modId = "arcforge"
    type = "optional"
    versionRange = "[2.5.0,)"
    ordering = "NONE"
    side = "BOTH"
```

## Rules

- **Server side only.** Call every method on the logical server thread, for example from a block entity's server tick
  or a server tick event. The capability is never provided on the client. Calling from another thread has undefined
  results.
- **Every write goes through the machine's own rules.** Insertions pass the machine's slot and recipe filters. Settings
  are checked exactly as the machine's GUI or the Wrench checks them. The API can do nothing a player or a pipe couldn't
  do, and nothing bypasses recipe, slot or tier rules.
- **Missing features are empty, never exceptions.** A machine without energy returns `Optional.empty()` from `energy()`.
  A machine without a setting returns `SettingResult.UNSUPPORTED`. Lists come back empty, and numbers come back as 0.
- **Permissions are the consumer's job.** The API never checks who is asking. If your mod acts for players, check that
  the player may use the machine. `MachineControl.owner()` gives the owner Arcforge tracks. Arcforge's own automation
  (pipes, hoppers) also ignores machine security.
- **Instances belong to one loaded block entity.** Once a machine is unloaded or broken, `isValid()` returns false.
  Drop the instance and look the machine up again. NeoForge's `BlockCapabilityCache` handles this for you.

## Looking a machine up

The capability has no context, so always pass `null`:

```java
import net.zagdrath.arcforge.api.machine.MachineCapabilities;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

MachineControl machine = serverLevel.getCapability(MachineCapabilities.MACHINE_CONTROL, pos, null);
if (machine != null) {
    MachineStatus status = machine.status();
    Component why = machine.statusReason();
}
```

Which blocks provide it:

- every Arcforge single-block machine;
- every block of a formed multiblock: casings, ports, glass, the controller, and interior blocks such as a Battery
  Array's cells. All of them return the same `MachineControl`, the controller's;
- the controller block of a multiblock that has a dedicated controller block (Biogas Digester, Firebox Array, Battery
  Array, Thermal Evaporator, Solar Thermal Array, Distillation Array, Greenhouse Array), even while the structure isn't
  formed. It then reports `isFormed() == false` and `MachineStatus.NOT_FORMED`;
- every block of the Arc Quarry's 3x3x3 footprint, which resolves to the quarry.

Casings of an unformed cube or shell array (the Arc Crushing, Induction Furnace, Metal Pressing, Superheater, Condenser,
Steam Boiler, Steam Turbine and Gas Turbine Arrays) provide nothing, because they don't belong to a machine until the
structure forms.

To keep a machine across ticks, use a capability cache. It drops the instance when the machine changes:

```java
BlockCapabilityCache<MachineControl, @Nullable Void> cache =
        BlockCapabilityCache.create(MachineCapabilities.MACHINE_CONTROL, serverLevel, pos, null);
MachineControl machine = cache.getCapability(); // null if there is no machine there now
```

## The interfaces

Each heading names the type's package (under `net.zagdrath.arcforge.api`), and each type and method has Javadoc. The API jar's
`-javadoc` artifact has the full reference.

### `ArcforgeApi` (`api`)

| Constant | Meaning |
|---|---|
| `MOD_ID` | `"arcforge"` |
| `API_VERSION` | `"1.1.0"`; the published jar's version is this plus the Minecraft version, e.g. `1.1.0+26.1.2` |
| `API_VERSION_MAJOR`, `_MINOR`, `_PATCH` | The parts of `API_VERSION`, as ints |

### `MachineCapabilities` (`api.machine`)

| Member | Meaning |
|---|---|
| `MACHINE_CONTROL` | `BlockCapability<MachineControl, @Nullable Void>`. Query it with a `null` context. |
| `MACHINE_CONTROL_ID` | `arcforge:machine_control` |

### `MachineControl` (`api.machine`): one machine

| Method | Returns |
|---|---|
| **Identity** | |
| `machineType()` | The block entity type id, e.g. `arcforge:arc_crusher` or `arcforge:steam_turbine_array` |
| `displayName()` | Its display name |
| `tier()` | `OptionalInt`; always empty, because Arcforge machines have no tiers yet |
| `isMultiblock()` / `isFormed()` | Whether it's a multiblock, and whether it's formed (single blocks always are) |
| `structureSize()` | `Optional<StructureSize>` (width, height, depth) of a formed multiblock |
| `position()` | The machine's block, or the multiblock's controller block |
| `owner()` | `Optional<MachineOwner>` (UUID and name) |
| `isValid()` | Whether this instance still refers to a loaded machine |
| **Status** | |
| `status()` | A `MachineStatus` (see below) |
| `statusReason()` | The machine's own words for it, as its GUI shows them, e.g. "No fluid" or "Too cold" |
| `progress()` | `OptionalDouble`: from 0 to 1 through the current operation, empty when not in one |
| `ticksRemaining()` | `OptionalInt`: an estimate of the time left in the current operation |
| `currentOutputs()` / `currentFluidOutputs()` | What the current operation will make, where the machine knows |
| **Resources** | |
| `energy()` | `Optional<MachineEnergy>` |
| `items()` | `Optional<MachineItems>` |
| `fluids()` | `Optional<MachineFluids>` (gases included) |
| `heat()` | `Optional<MachineHeat>` |
| **Settings, statistics, events** | |
| `settings()` | `MachineSettings` |
| `statistics()` | `MachineStatistics` |
| `addListener(l)` / `removeListener(l)` | `MachineListener` registration |

### `MachineStatus` (`api.machine.status`)

| Value | Meaning | Examples of `statusReason()` |
|---|---|---|
| `IDLE` | Ready, nothing to do | Idle, Waiting for pulse, Finished, Night |
| `RUNNING` | Working | Crushing, Generating, Distilling |
| `NO_POWER` | Short of FE, or of heat for a heat machine | No power, No heat, Too cold |
| `NO_INPUT` | Has a job but lacks an input | No fuel, No fluid, Missing items, No die |
| `OUTPUT_BLOCKED` | Outputs, tanks or buffer full | Output full, Steam full, Front blocked |
| `DISABLED` | Stopped by redstone, or switched off through the API | Idle (redstone), Switched off remotely |
| `NOT_FORMED` | An unformed multiblock | Not formed |
| `FAULT` | A problem needing attention | Too hot, No sky, Can't break, Flameout |

### `MachineEnergy` (`api.machine.resource`): FE, read only

`role()` (`EnergyRole.CONSUMER`, `GENERATOR` or `STORAGE`), `stored()`, `capacity()`, and `perTick()`. `perTick()` is the
FE used by a consumer, generated by a generator, or the net flow of storage (positive while charging) in the last tick.
Energy itself moves through NeoForge's normal energy capability.

### `MachineItems` (`api.machine.resource`)

| Method | Meaning |
|---|---|
| `slotCount()`, `role(slot)`, `stack(slot)`, `capacity(slot)` | Read any slot |
| `insert(slot, stack, simulate)` | Insert into one slot; returns what didn't fit |
| `insert(stack, simulate)` | Insert wherever the machine takes it, as a pipe would |
| `extract(slot, amount, simulate)` | Extract from one slot; returns what was taken |
| `handler()` | A NeoForge `ResourceHandler<ItemResource>` with the same rules, for transactions |

`SlotRole` decides what is allowed:

| Role | API access | Examples |
|---|---|---|
| `INPUT` | Insert | Ingredients, full buckets |
| `FUEL` | Insert | Coal in a Combustion Plant |
| `CATALYST` | Insert | A Metal Press die, a Sifter mesh, Fischer-Tropsch catalyst |
| `OUTPUT` | Extract | Products, by-products, ash, emptied buckets |
| `UPGRADE` | Read only | The four upgrade slots |
| `OTHER` | Read only | Patterns, filters |

An insert must also pass the machine's own filter for that slot. An Arc Crusher's input takes only crushable items, for
example. The stack methods each run in their own transaction (nested in yours, if one is open). Inside your own
transaction you can also use `handler()`.

### `MachineFluids` (`api.machine.resource`): mB, gases included

`tankCount()`, `role(tank)`, `fluid(tank)`, `capacity(tank)`, `insert(tank, fluid, simulate)`, `insert(fluid, simulate)`,
`extract(tank, amount, simulate)` and `handler()`. These follow the same role rules as items. Each tank accepts only the
fluids its machine uses. A fill without a tank index is routed as a pipe's would be. To work with a machine's gases
alone, by `Gas` rather than `FluidStack`, use its [gas handler](#gases).

### `MachineHeat` (`api.machine.resource`): HU and °C

`role()` (`HeatRole.CONSUMER` or `PRODUCER`), `stored()`, `capacity()`, `temperature()`, `maxTemperature()`, `perTick()`
(the HU used or produced last tick), `insert(amount, simulate)` (consumers only) and `extract(amount, simulate)`
(producers only). Moving heat this way has the same limits a heat conduit has. Heat is Arcforge's own resource and is
exposed only where a machine has a heat buffer.

### `MachineSettings` (`api.machine.settings`)

Every setter returns a `SettingResult`: `APPLIED`, `UNCHANGED`, `UNSUPPORTED`, `INVALID` (a value the machine refuses)
or `REJECTED` (it can't be changed right now). `succeeded()` is true for the first two.

| Setting | Read | Write |
|---|---|---|
| On/off | `isEnabled()` | `setEnabled(boolean)` |
| Redstone mode | `redstoneMode()`, `allowedRedstoneModes()` | `setRedstoneMode(RedstoneMode)` |
| Side configuration (single blocks) | `hasSideConfiguration()`, `sideMode(MachineSide)`, `allowedSideModes()` | `setSideMode(side, mode)`, `clearSideModes()` |
| Ports (multiblocks) | `ports()` (a list of `MachinePort`: pos, face, mode), `allowedPortModes()` | `setPort(pos, face, mode)` |
| Auto-eject | `supportsAutoEject()`, `isAutoEject()` | `setAutoEject(boolean)` |
| Machine-specific | `options()` (a list of `MachineOption`) | `setOption(id, value)` |
| Upgrades | `upgrades()` (a list of `InstalledUpgrade`), `acceptedUpgrades()` | read only |

- **On/off.** Every machine is on unless switched off through the API. Arcforge has no on/off switch of its own. A
  machine switched off stops as if its redstone mode stopped it, keeps its contents, and reports `DISABLED`; its GUI
  says "Switched off remotely". The switch is saved with the machine. A multiblock keeps it, and its statistics, in
  its controller block. A cube array that grows into a bigger box carries them over, but a structure broken and
  rebuilt around a different controller block starts afresh. As with a redstone stop, an Arc Quarry switched off
  pauses mining but finishes a scan it has started.
- **Redstone modes** are `RedstoneMode.IGNORE`, `HIGH`, `LOW`, `PULSE` and `THROTTLE`. Each machine allows only the
  modes its Redstone tab offers.
- **Side and port modes** are the string ids in `SideModes`, for example `"input"`, `"output"`, `"energy"`, `"heat"` and
  `"gas_output"`. They are strings so Arcforge can add modes without a major API change. Sides are relative to the
  machine's front (`MachineSide.TOP`, `BOTTOM`, `LEFT`, `RIGHT`, `BACK`, `FRONT`). A port can only be set on a block of
  the structure that can hold ports, and only on a face that points out of the structure, as with the Wrench.
- **Machine-specific options** have an `id`, a `name`, an `OptionType` (`BOOLEAN` as `"true"`/`"false"`, `INTEGER`
  with `min`/`max`, `CHOICE` from `choices`) and the current `value`:

| Machine | Option id | Type |
|---|---|---|
| Electrolyzer | `vent_hydrogen`, `vent_oxygen` | Boolean |
| Hydrothermal Carbonizer | `discard_water` | Boolean |
| Vacuum Collector | `range` | Integer, 1 to 16; values above the server's configured maximum are `REJECTED` |
| Arc Quarry | `running` | Boolean: start/resume or stop, like its Start/Stop button |
| Superheater Array | `pressure` | Choice: `auto`, `high_pressure`, `superheated` |
| Steam Boiler Array | `pressure` | Choice: `auto`, `steam`, `high_pressure`, `superheated` |

### `MachineStatistics` (`api.machine.status`)

`operationsCompleted()`, `itemsProduced()`, `itemsConsumed()`, `fluidProduced()` and `fluidConsumed()` (mB),
`uptimeTicks()` (time spent `RUNNING`), `loadedTicks()` and `operationsPerMinute()`.

- **Totals** are saved with the machine. They survive unloading and restarts, and are lost when the machine is broken.
- **`operationsPerMinute()`** is a rolling rate over the last minute the machine was loaded.
- **An operation** is one unit of work: a recipe, a craft, a fuel item burned, a block broken or placed, a batch.
  Machines that work continuously, such as pumps, boilers, turbines and solar collectors, count amounts and may leave
  operations at 0.

### `MachineListener` and `CompletedOperation` (`api.machine.status`)

```java
public interface MachineListener {
    default void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {}
    default void onOperationCompleted(MachineControl machine, CompletedOperation operation) {}
    default void onFault(MachineControl machine, Component reason) {}
}
```

- **Status changes** are checked at the end of every level tick. `onFault` follows `onStatusChanged` when the status
  becomes `FAULT`.
- **`onOperationCompleted`** fires as the machine finishes each operation, with copies of what it produced and the
  amounts it consumed.
- **Delivery.** Events arrive on the server thread, so keep listeners quick, and don't change the machine from inside
  one. An exception from a listener is logged and never reaches the machine.
- **Listeners aren't saved.** When the machine unloads (`isValid()` becomes false), look it up again and add your
  listener to the new instance.

## Gases

Since API 1.1.0, other mods can store and move Arcforge gases. They can read a block's or an item's gas tanks, fill
and empty them by gas and amount, and look up every gas for search and display. The types are in `api.gas`.

### How Arcforge stores gas

Arcforge has no separate gas system. **A gas is a NeoForge fluid** that is lighter than air (its `FluidType` has a
negative density) or is tagged `#arcforge:gases`. Gases are kept in ordinary fluid tanks. Pressurized Conduits and
Pressurized Cylinders carry only gases, and Fluid Conduits and Fluid Tanks carry only liquids. Other mods' gases count
too, if they are lighter than air or tagged.

- **Unit:** gas amounts are in **millibuckets (mB)**, the NeoForge fluid unit, as `int`. So 1,000 mB of Hydrogen in a
  gas tank is 1,000 mB of the fluid `arcforge:hydrogen`.
- **Temperature** is in kelvin, from the fluid type: 293 K for most gases, 308 K for Biogas, 373 K for Steam and Exhaust
  Steam, 773 K for High-Pressure Steam and 1,173 K for Superheated Steam.
- **Pressure:** Arcforge has no pressure value. The three steam grades stand for it, and the hotter grades are at
  higher pressure. `Gas.steamGrade()` says which grade a steam is.

The gas API is a gas-only view of those fluid tanks. Because gases are fluids, anything the gas API does can also be
done through NeoForge's fluid capabilities or `MachineFluids`. The gas API adds three things: a gas identity, tank
lists without the liquid tanks, and an item capability. With them, a mod with its own gas storage doesn't have to sort
gases from liquids.

### `Gas` (`api.gas`): one gas

`Gas` is a record that wraps the gas's source fluid. Two `Gas` values for the same gas are equal, so a `Gas` can be a
map key. `new Gas(fluid)` also takes a gas's flowing form. It throws `IllegalArgumentException` for a fluid that isn't
a gas.

| Member | Returns |
|---|---|
| `fluid()` | The source `Fluid` |
| `id()` | Its fluid id, e.g. `arcforge:hydrogen`. Store this, or use `CODEC` |
| `displayName()` | The translatable name Arcforge's GUIs show, e.g. "Hydrogen" |
| `color()` | `0xAARRGGBB`, the colour Arcforge draws the gas in. A gas with no colour gets `DEFAULT_COLOR` (opaque white) |
| `temperature()` | Kelvin |
| `steamGrade()` | `Optional<SteamGrade>`: `STEAM`, `HIGH_PRESSURE` or `SUPERHEATED`. Empty for every other gas, including Exhaust Steam |
| `toResource()`, `toStack(amount)` | The gas as a NeoForge `FluidResource` or `FluidStack`, for NeoForge's fluid capabilities |
| `CODEC` | Saves a gas as its id string. Decoding fails for an id that isn't a gas |
| `STREAM_CODEC` | Sends a gas to the client, for example to draw your GUI's gauges |

`Gas` and `GasRegistry` read the fluid registry, its tags and a synced data map. They work on **both sides** once a
world is loaded, so a client GUI can show gas names and colours.

### `GasRegistry` (`api.gas`): every gas

| Member | Meaning |
|---|---|
| `all()` | Every gas, Arcforge's and other mods', sorted by id. Each call builds a new list, so keep the list rather than calling `all()` every tick. Filter it by `displayName()` or `id()` to search |
| `get(id)` | `Optional<Gas>` by fluid id |
| `of(fluid)` | `Optional<Gas>` for a fluid, source or flowing |
| `isGas(fluid)` | Whether a fluid is a gas |
| `TAG` | `#arcforge:gases` |
| `PROPERTIES` | The data map `arcforge:gas_properties` (see below) |

Arcforge's gases are Steam, High-Pressure Steam, Superheated Steam, Exhaust Steam, Hydrogen, Oxygen, Carbon Dioxide,
Nitrogen, Ammonia, Biogas, Chlorine, Ethylene and Syngas. Use `all()` rather than hard-coding them.

**Colours** come from the data map `arcforge:gas_properties`, keyed by fluid and synced to clients. Arcforge ships an
entry for each of its gases. A datapack or another mod can add one for its own gas, in
`data/<namespace>/data_maps/fluid/gas_properties.json`:

```json
{ "values": { "examplemod:argon": { "color": "#C8A2E8" } } }
```

### `GasCapabilities` (`api.gas`): the entry points

| Member | Meaning |
|---|---|
| `BLOCK` | `BlockCapability<GasHandler, @Nullable Void>`. Query it with a `null` context |
| `ITEM` | `ItemCapability<GasHandler, ItemAccess>`. Query it through an `ItemAccess` |
| `ID` | `arcforge:gas_handler`, the id of both |

```java
GasHandler tanks = serverLevel.getCapability(GasCapabilities.BLOCK, pos, null);
GasHandler cartridge = ItemAccess.forPlayerSlot(player, slot).getCapability(GasCapabilities.ITEM);
```

**Which blocks provide `BLOCK`.** It is provided on the server only, and only by blocks with at least one tank that can
hold a gas:

- **Single-block machines:** the Air Separator, Carbon Reclaimer, Chemical Reactor, Combustion Plant, Electrolyzer,
  Fermenter, Firebox, Fischer-Tropsch Reactor, Fuel Burner, Gasifier, Haber Reactor and Hydroponic Cell.
- **Multiblocks:** the Arcforge Furnace, Biogas Digester, Condenser Array, Distillation Array, Firebox Array, Gas
  Turbine Array, Greenhouse, Steam Boiler Array, Steam Turbine Array and Superheater Array. Every block of a formed
  multiblock gives its controller's tanks, as `MACHINE_CONTROL` does.
- **Storage:** Pressurized Cylinders of every tier.

Machines that hold only liquids don't provide it, nor do Fluid Tanks or Reservoirs. Neither do conduits, Meters or the
Quantum Tunnel: they move gas but hold none of their own. Reach them on a face through NeoForge's
`Capabilities.Fluid.BLOCK`, as a pipe would.

**The block capability has no side.** Like `MACHINE_CONTROL` and `MachineFluids`, it is the whole block's view, and it
ignores side and port configuration. For what one face offers a pipe, query `Capabilities.Fluid.BLOCK` on that face,
which carries gases as fluids.

**Which items provide `ITEM`:** Gas Cartridges of every tier, and Jetpacks. Jetpacks take only jetpack fuels: the three
steams and Hydrogen. Canisters hold liquids and don't provide it. Neither does a Pressurized Cylinder held as an item;
place it to fill or empty it. The handler writes the item's new contents back through the `ItemAccess`. Make changes on
the server, for example in your menu's server-side logic, and let the slot sync to the client.

### `GasHandler` and `GasTank` (`api.gas`)

| Method | Meaning |
|---|---|
| `tankCount()` | The number of gas tanks (at least 1) |
| `tank(i)` | A `GasTank` snapshot, or `GasTank.NONE` for an index out of range |
| `tanks()` | A snapshot of every tank, in index order |
| `accepts(i, gas)` | Whether the tank takes that gas from outside at all, whatever it holds now |
| `insert(i, gas, amount, simulate)` | Fill one tank. Returns the mB filled |
| `insert(gas, amount, simulate)` | Fill whichever tanks take the gas, routed as a pipe's fill would be |
| `extract(i, gas, amount, simulate)` | Drain that gas from one tank. Returns the mB drained |
| `extract(gas, amount, simulate)` | Drain that gas from whichever tanks give it, in index order |

`GasTank` is a record with `gas()` (an `Optional<Gas>`), `amount()`, `capacity()`, `canInsert()` and `canExtract()`,
plus `isEmpty()` and `space()`. A snapshot doesn't change with the tank, so read the tank again for fresh values.

The rules:

- **Only gas tanks are listed.** A machine's water, oil and other liquid tanks are left out, so gas tank indices are
  **not** `MachineFluids` indices. The list of tanks is fixed for the life of the handler.
- **Each tank's own rules apply, as for a pipe.** Nothing is forced, and a refused transfer returns 0.
  - A machine's input tanks (`canInsert`) take only the gases its recipes use, and never give them back.
  - A machine's output tanks (`canExtract`) only give.
  - A Haber Reactor keeps one gas to a tank, so Hydrogen already in one input tank is refused by the other.
  - A Pressurized Cylinder and a Gas Cartridge hold one gas at a time, and move at most their tier's rate per call.
  - A Jetpack takes only jetpack fuels.
- **A tank that takes liquids as well as gases** (a Chemical Reactor's) reads as empty while it holds a liquid, and
  takes no gas.
- **`simulate`** only reports what would move. Otherwise each call runs in its own NeoForge transfer transaction and
  commits it. If you have a transaction open, the call nests in yours and is undone if you abort it, so several
  transfers can be made all-or-nothing.
- **Server thread only**, as for the rest of the API. A handler belongs to one block entity. Keep it in a
  `BlockCapabilityCache`, as for `MACHINE_CONTROL`, which drops the handler when the block changes.

### Example: moving gas between a machine and your storage

```java
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.zagdrath.arcforge.api.gas.Gas;
import net.zagdrath.arcforge.api.gas.GasCapabilities;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.gas.GasTank;

// Loaded only when Arcforge is installed.
public final class ArcforgeGasLink {
    private final BlockCapabilityCache<GasHandler, @Nullable Void> cache;

    public ArcforgeGasLink(ServerLevel level, BlockPos pos) {
        this.cache = BlockCapabilityCache.create(GasCapabilities.BLOCK, level, pos, null);
    }

    // Called from your storage's server tick. GasStorage is your own: how much of each gas it holds and has room for.
    public void tick(GasStorage storage) {
        GasHandler tanks = cache.getCapability();
        if (tanks == null) {
            return;
        }
        for (int i = 0; i < tanks.tankCount(); i++) {
            GasTank tank = tanks.tank(i);
            if (tank.canExtract() && !tank.isEmpty()) {
                // Pull products (an Electrolyzer's Hydrogen, a Haber Reactor's Ammonia) into storage.
                Gas gas = tank.gas().orElseThrow();
                int moved = tanks.extract(i, gas, Math.min(storage.space(gas), tank.amount()), false);
                storage.add(gas, moved);
            } else if (tank.canInsert()) {
                // Feed inputs: offer each stored gas the tank takes. It keeps what fits and refuses the rest.
                for (Gas gas : storage.gases()) {
                    if (tanks.accepts(i, gas)) {
                        int moved = tanks.insert(i, gas, storage.amount(gas), false);
                        storage.remove(gas, moved);
                    }
                }
            }
        }
    }
}
```

## Version policy

The API follows [semantic versioning](https://semver.org/), separately from the mod's version:

- **Patch** (1.0.x): documentation or behaviour fixes; no type or method changes.
- **Minor** (1.x.0): additions that keep old consumers working, such as new types, new methods, new enum constants or new
  `SideModes` ids. Consumers should handle enum values they don't know, for example with a `default` branch.
- **Major** (x.0.0): changing or removing a type or method.

History: **1.0.0** added machine control, and **1.1.0** added gases (`api.gas`).

`ArcforgeApi.API_VERSION` holds the current version, and the published jar has the same version followed by the
Minecraft version it's built for (`+26.1.2`). To check at runtime,
compare `ArcforgeApi.API_VERSION_MAJOR` with the major version you built against, and `API_VERSION_MINOR` with the minor
version that added what you use. Arcforge's CHANGELOG records each API change under the release that ships it.

## Example consumer

A storage controller in the style of Encoded Logistics. It keeps a machine fed with an ingredient, pulls finished items
into storage, switches the machine off when storage is full, and reacts to events instead of polling.

```java
package com.example.encodedlogistics.integration.arcforge;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.zagdrath.arcforge.api.ArcforgeApi;
import net.zagdrath.arcforge.api.machine.MachineCapabilities;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.resource.MachineItems;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.api.machine.status.CompletedOperation;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

// Loaded only when Arcforge is installed.
public final class ArcforgeMachineLink implements MachineListener {
    private static final int BUILT_AGAINST_MAJOR = 1;

    private final BlockCapabilityCache<MachineControl, @Nullable Void> cache;
    private @Nullable MachineControl listeningTo;

    public ArcforgeMachineLink(ServerLevel level, BlockPos machinePos) {
        if (ArcforgeApi.API_VERSION_MAJOR != BUILT_AGAINST_MAJOR) {
            throw new IllegalStateException("Built for Arcforge API " + BUILT_AGAINST_MAJOR + ".x, found " + ArcforgeApi.API_VERSION);
        }
        this.cache = BlockCapabilityCache.create(MachineCapabilities.MACHINE_CONTROL, level, machinePos, null);
    }

    // Your permission check: Arcforge leaves it to you.
    public boolean mayControl(ServerPlayer player) {
        MachineControl machine = cache.getCapability();
        return machine != null && machine.owner().map(owner -> owner.id().equals(player.getUUID())).orElse(true);
    }

    // Called from the storage controller's server tick.
    public void tick(Predicate<ItemStack> takeIntoStorage, ItemSource ingredients) {
        MachineControl machine = cache.getCapability();
        if (machine == null || !machine.isFormed()) {
            return;
        }
        if (listeningTo != machine) {
            // A new instance after an unload or re-form: listen to it instead.
            if (listeningTo != null) {
                listeningTo.removeListener(this);
            }
            machine.addListener(this);
            listeningTo = machine;
        }
        machine.items().ifPresent(items -> {
            for (int slot = 0; slot < items.slotCount(); slot++) {
                if (items.role(slot) == SlotRole.OUTPUT) {
                    pullOutput(items, slot, takeIntoStorage);
                } else if (items.role(slot) == SlotRole.INPUT && items.stack(slot).isEmpty()) {
                    ItemStack offer = ingredients.peek();
                    // Simulate first: the machine refuses anything its recipes can't use.
                    if (!offer.isEmpty() && items.insert(slot, offer, true).getCount() < offer.getCount()) {
                        ItemStack rest = items.insert(slot, offer, false);
                        ingredients.take(offer.getCount() - rest.getCount());
                    }
                }
            }
        });
    }

    private void pullOutput(MachineItems items, int slot, Predicate<ItemStack> takeIntoStorage) {
        ItemStack available = items.extract(slot, 64, true);
        if (!available.isEmpty() && takeIntoStorage.test(available)) {
            items.extract(slot, available.getCount(), false);
        }
    }

    // Storage full: stop the machine rather than let it block.
    public void setStorageFull(boolean full) {
        MachineControl machine = cache.getCapability();
        if (machine != null) {
            machine.settings().setEnabled(!full);
        }
    }

    @Override
    public void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {
        if (current == MachineStatus.NO_INPUT) {
            // e.g. request more ingredients from the network
        }
    }

    @Override
    public void onOperationCompleted(MachineControl machine, CompletedOperation operation) {
        // e.g. update a crafting job's progress with operation.itemsProduced()
    }

    @Override
    public void onFault(MachineControl machine, Component reason) {
        // e.g. alert the network's owner: machine.displayName() + ": " + reason
    }

    public interface ItemSource {
        ItemStack peek();

        void take(int count);
    }
}
```

## Coverage

These machines provide the capability: every single-block machine with a GUI, the Arc Quarry, and all seventeen
multiblocks. That includes the Geothermal Plant, the Hydrothermal Carbonizer, the Arcforge Furnace and the Carbonizer.
Storage blocks (cells, tanks, crates, vaults), conduits, the Meter, the Chunk Loader, the Quantum Tunnel, the Chargepad
and the simple farm blocks (Planter, Harvester, Compost Bin, Millstone, Resin Tap, sprinklers) aren't machines in this
sense and don't provide it.

What each machine supports follows from its features. A heat-only machine has no `energy()`, and a passive machine
reports no `progress()`. Check the `Optional`s and `SettingResult`s rather than assuming.

## Building and publishing the API jar

The API lives in the `api` source set at `src/api/java`. Its classes are packed into the Arcforge mod jar as well.

| Task | Result |
|---|---|
| `./gradlew apiJar` | `build/libs/arcforge-api-1.1.0+26.1.2.jar` (also built by `assemble` and `build`) |
| `./gradlew apiJavadoc` | The API's Javadoc. A missing or broken doc comment fails it, and it runs as part of `check`. |
| `./gradlew publishApiPublicationToMavenLocal` | The API jar with `-sources` and `-javadoc` jars, in `~/.m2/repository/net/zagdrath/arcforge/arcforge-api/1.1.0+26.1.2/` |
| `./gradlew publishToMavenLocal` | The same, plus the mod jar's own publication |

The jar's version is `ArcforgeApi.API_VERSION` plus `+` and `minecraft_version` from `gradle.properties`. Bump that constant, and its `MAJOR`/`MINOR`/`PATCH` parts, to
release a new API version.
