/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// Arcforge tiers in progression order, shared by conduits and storage blocks (Superconducting is energy conduits only: see
// STANDARD). Different tiers of the
// same conduit type connect, and a network runs at the rate of its lowest tier. The HU/t column is the heat cell's and
// thermal capsule's rate; the Thermodynamic Conduit's rate is in config (power.heat.thermodynamicConduits), 50 / 200 /
// 800 / 51,200 by default, so an Arcforged conduit can carry a big multiblock's heat.
public enum ConduitTier implements StringRepresentable {
    //            FE/t (default; config)  items/op  ticks/op  mB/t    cell HU/t   item speed (blocks/tick)
    WROUGHT("wrought", 256, 8, 20, 200, 50, 0.05F),
    TEMPERED("tempered", 1_024, 16, 10, 800, 200, 0.08F),
    HARDENED("hardened", 8_192, 32, 5, 3_200, 800, 0.12F),
    ARCFORGED("arcforged", 262_144, 64, 2, 12_800, 3_200, 0.2F),
    // A power plant's trunk line: the Superconducting Energy Conduit, 16 times the Arcforged, so one line carries a reactor
    // plant's output. It has no other conduit, storage block or portable (its other columns are the Arcforged's).
    SUPERCONDUCTING("superconducting", 4_194_304, 64, 2, 12_800, 3_200, 0.2F);

    // The tiers every conduit type, storage block and portable comes in.
    public static final java.util.List<ConduitTier> STANDARD = java.util.List.of(WROUGHT, TEMPERED, HARDENED, ARCFORGED);

    // Fluid each conduit block can hold.
    public static final int FLUID_CAPACITY_PER_CONDUIT = 1_000;
    // Gas each pressurized conduit block can hold.
    public static final int GAS_CAPACITY_PER_CONDUIT = 2_000;
    // Item stacks each item conduit can store while nothing will take them.
    public static final int ITEM_STORAGE_SLOTS = 4;

    // Whether this tier comes as a conduit of that type (every standard tier does; Superconducting only as energy).
    public boolean hasConduit(ConduitType type) {
        return this != SUPERCONDUCTING || type == ConduitType.ENERGY;
    }

    private final String name;
    private final int energyPerTick;
    private final int itemsPerOperation;
    private final int ticksPerItemOperation;
    private final int fluidPerTick;
    private final int cellHeatPerTick;
    private final float itemSpeed;

    ConduitTier(String name, int energyPerTick, int itemsPerOperation, int ticksPerItemOperation, int fluidPerTick, int cellHeatPerTick, float itemSpeed) {
        this.name = name;
        this.energyPerTick = energyPerTick;
        this.itemsPerOperation = itemsPerOperation;
        this.ticksPerItemOperation = ticksPerItemOperation;
        this.fluidPerTick = fluidPerTick;
        this.cellHeatPerTick = cellHeatPerTick;
        this.itemSpeed = itemSpeed;
    }

    // The Energy Conduit's FE/t (and what each conduit holds: one tick's worth), from config (power.energyConduits).
    public int energyPerTick() {
        return net.zagdrath.arcforge.config.ArcforgeConfig.energyConduitRate(this);
    }

    // The default of that rate (256 / 1,024 / 8,192 / 262,144), for config defaults that follow it.
    public int defaultEnergyPerTick() {
        return energyPerTick;
    }

    public int itemsPerOperation() {
        return itemsPerOperation;
    }

    public int ticksPerItemOperation() {
        return ticksPerItemOperation;
    }

    public int fluidPerTick() {
        return fluidPerTick;
    }

    // Pressurized conduits move gas twice as fast as fluid conduits move fluid.
    public int gasPerTick() {
        return fluidPerTick * 2;
    }

    // What each fluid (or gas) conduit of this tier holds: the base buffer, or a tick of its rate where that's more. A
    // network pools its conduits' buffers, and each tick pulls what fits and pushes what it holds, so with less than a
    // tick of its rate in each conduit a short run moved only its own size a tick (a 3-block Arcforged line 3,000 mB/t).
    public int fluidBuffer() {
        return Math.max(FLUID_CAPACITY_PER_CONDUIT, fluidPerTick());
    }

    public int gasBuffer() {
        return Math.max(GAS_CAPACITY_PER_CONDUIT, gasPerTick());
    }

    // The Thermodynamic Conduit's HU/t (and the heat each conduit holds: one tick's worth), from config.
    public int heatPerTick() {
        return net.zagdrath.arcforge.config.ArcforgeConfig.thermodynamicConduitRate(this);
    }

    // The default of that rate, for config defaults that follow it (read before the config is).
    public int defaultHeatPerTick() {
        return switch (this) {
            case WROUGHT -> 50;
            case TEMPERED -> 200;
            case HARDENED -> 800;
            case ARCFORGED, SUPERCONDUCTING -> 51_200;
        };
    }

    public float itemSpeed() {
        return itemSpeed;
    }

    // --- Storage blocks ---

    // Fluid tank capacity in mB.
    public int tankCapacity() {
        return switch (this) {
            case WROUGHT -> 16_000;
            case TEMPERED -> 64_000;
            case HARDENED -> 256_000;
            case ARCFORGED, SUPERCONDUCTING -> 1_024_000;
        };
    }

    // Most mB a fluid tank moves in or out per operation.
    public int tankRate() {
        return switch (this) {
            case WROUGHT -> 500;
            case TEMPERED -> 2_000;
            case HARDENED -> 8_000;
            case ARCFORGED, SUPERCONDUCTING -> 32_000;
        };
    }

    // Energy cell capacity in FE.
    public int cellCapacity() {
        return switch (this) {
            case WROUGHT -> 500_000;
            case TEMPERED -> 4_000_000;
            case HARDENED -> 32_000_000;
            case ARCFORGED, SUPERCONDUCTING -> 256_000_000;
        };
    }

    // Most FE an energy cell takes in or gives out per tick, from config (power.energyCells).
    public int cellRate() {
        return net.zagdrath.arcforge.config.ArcforgeConfig.energyCellRate(this);
    }

    // Its default: 512 / 2,048 / 16,384 / 262,144 (the Arcforged cell matches the Arcforged Energy Conduit).
    public int defaultCellRate() {
        return switch (this) {
            case WROUGHT -> 512;
            case TEMPERED -> 2_048;
            case HARDENED -> 16_384;
            case ARCFORGED, SUPERCONDUCTING -> 262_144;
        };
    }

    // Pressurized Cylinder capacity in mB.
    public int cylinderCapacity() {
        return switch (this) {
            case WROUGHT -> 64_000;
            case TEMPERED -> 256_000;
            case HARDENED -> 1_024_000;
            case ARCFORGED, SUPERCONDUCTING -> 4_096_000;
        };
    }

    // Most mB a Pressurized Cylinder moves in or out per operation: twice a fluid tank, like the conduits.
    public int cylinderRate() {
        return tankRate() * 2;
    }

    // Heat cell capacity in HU.
    public int heatCellCapacity() {
        return switch (this) {
            case WROUGHT -> 50_000;
            case TEMPERED -> 200_000;
            case HARDENED -> 800_000;
            case ARCFORGED, SUPERCONDUCTING -> 3_200_000;
        };
    }

    // Temperature of a full heat cell, in °C. The rock-wool tiers hold hotter heat: a Firebox (1,100°C) can
    // only fill them part way, a Geothermal Plant (1,400°C) all the way.
    public int heatCellMaxTemperature() {
        return switch (this) {
            case WROUGHT, TEMPERED -> 1_100;
            case HARDENED -> 1_300;
            case ARCFORGED, SUPERCONDUCTING -> 1_400;
        };
    }

    // Most HU a heat cell takes in or gives out per tick: 50 / 200 / 800 / 3,200 (the Arcforged cell kept its rate when the
    // Arcforged conduit was raised).
    public int heatCellRate() {
        return cellHeatPerTick;
    }

    // Share of its stored heat a heat cell leaks away per minute, in percent. Better tiers are better insulated.
    public double heatCellLeakPercentPerMinute() {
        return switch (this) {
            case WROUGHT -> 10.0;
            case TEMPERED -> 4.0;
            case HARDENED -> 1.5;
            case ARCFORGED, SUPERCONDUCTING -> 0.5;
        };
    }

    // --- Portable storage items (Batteries, Canisters, Gas Cartridges, Thermal Capsules) ---

    // How much the next tier holds or moves: four times as much.
    private int quadrupled(int wrought) {
        return wrought << (2 * ordinal());
    }

    // Battery capacity in FE: half an energy cell of the first tier, four times more per tier.
    public int batteryCapacity() {
        return quadrupled(250_000);
    }

    // Most FE a battery takes in or gives out per tick.
    public int batteryRate() {
        return quadrupled(1_000);
    }

    // Canister capacity in mB: a fluid tank of the same tier.
    public int canisterCapacity() {
        return tankCapacity();
    }

    // Most mB a canister takes in or gives out per tick.
    public int canisterRate() {
        return quadrupled(1_000);
    }

    // Gas cartridge capacity in mB: twice a canister, like the cylinders.
    public int gasCartridgeCapacity() {
        return quadrupled(32_000);
    }

    // Most mB a gas cartridge takes in or gives out per tick.
    public int gasCartridgeRate() {
        return quadrupled(2_000);
    }

    // A thermal capsule holds, moves, leaks and gets as hot as the heat cell of the same tier.
    public int thermalCapsuleCapacity() {
        return heatCellCapacity();
    }

    public int thermalCapsuleRate() {
        return heatCellRate();
    }

    public int thermalCapsuleMaxTemperature() {
        return heatCellMaxTemperature();
    }

    // --- Crates and Vaults ---

    // Rows of 9 slots in a crate: 54 / 72 / 99 / 126 slots.
    public int crateRows() {
        return switch (this) {
            case WROUGHT -> 6;
            case TEMPERED -> 8;
            case HARDENED -> 11;
            case ARCFORGED, SUPERCONDUCTING -> 14;
        };
    }

    public int crateSlots() {
        return crateRows() * 9;
    }

    // Items a vault holds: 4,096 / 16,384 / 65,536 / 262,144.
    public int vaultCapacity() {
        return quadrupled(4_096);
    }

    // The tier before and after this one among the standard tiers (null at either end), for Storage Upgrades.
    public @Nullable ConduitTier previous() {
        int i = STANDARD.indexOf(this);
        return i <= 0 ? null : STANDARD.get(i - 1);
    }

    public @Nullable ConduitTier next() {
        int i = STANDARD.indexOf(this);
        return i < 0 || i == STANDARD.size() - 1 ? null : STANDARD.get(i + 1);
    }

    public static ConduitTier lowest(ConduitTier a, ConduitTier b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    public Component getDisplayName() {
        return Component.translatable("conduit_tier.arcforge." + name);
    }

    // Tooltip line describing this tier's maximum throughput for a conduit type.
    public Component describeThroughput(ConduitType type) {
        return switch (type) {
            case ENERGY -> Component.translatable("tooltip.arcforge.conduit.throughput.energy", format(energyPerTick()));
            case FLUID -> Component.translatable("tooltip.arcforge.conduit.throughput.fluid", format(fluidPerTick));
            case GAS -> Component.translatable("tooltip.arcforge.conduit.throughput.fluid", format(gasPerTick()));
            case THERMAL -> Component.translatable("tooltip.arcforge.conduit.throughput.thermal", format(heatPerTick()));
            case ITEM -> Component.translatable("tooltip.arcforge.conduit.throughput.item", itemsPerOperation,
                    String.format("%.1f", ticksPerItemOperation / 20.0));
        };
    }

    private static String format(int value) {
        return String.format("%,d", value);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
