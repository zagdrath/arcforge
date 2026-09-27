/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// Conduit tiers in progression order. Different tiers of the same type connect,
// and a network runs at the rate of its lowest tier.
public enum ConduitTier implements StringRepresentable {
    //            FE/t    items/op  ticks/op  mB/t    HU/t   item speed (blocks/tick)
    WROUGHT("wrought", 256, 8, 20, 200, 50, 0.05F),
    TEMPERED("tempered", 1_024, 16, 10, 800, 200, 0.08F),
    HARDENED("hardened", 8_192, 32, 5, 3_200, 800, 0.12F),
    ARCFORGED("arcforged", 65_536, 64, 2, 12_800, 3_200, 0.2F);

    // Liquid each conduit block can hold.
    public static final int LIQUID_CAPACITY_PER_CONDUIT = 1_000;

    private final String name;
    private final int energyPerTick;
    private final int itemsPerOperation;
    private final int ticksPerItemOperation;
    private final int liquidPerTick;
    private final int heatPerTick;
    private final float itemSpeed;

    ConduitTier(String name, int energyPerTick, int itemsPerOperation, int ticksPerItemOperation, int liquidPerTick, int heatPerTick, float itemSpeed) {
        this.name = name;
        this.energyPerTick = energyPerTick;
        this.itemsPerOperation = itemsPerOperation;
        this.ticksPerItemOperation = ticksPerItemOperation;
        this.liquidPerTick = liquidPerTick;
        this.heatPerTick = heatPerTick;
        this.itemSpeed = itemSpeed;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public int itemsPerOperation() {
        return itemsPerOperation;
    }

    public int ticksPerItemOperation() {
        return ticksPerItemOperation;
    }

    public int liquidPerTick() {
        return liquidPerTick;
    }

    public int heatPerTick() {
        return heatPerTick;
    }

    public float itemSpeed() {
        return itemSpeed;
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
            case ENERGY -> Component.translatable("tooltip.arcforge.conduit.throughput.energy", format(energyPerTick));
            case LIQUID -> Component.translatable("tooltip.arcforge.conduit.throughput.liquid", format(liquidPerTick));
            case THERMAL -> Component.translatable("tooltip.arcforge.conduit.throughput.thermal", format(heatPerTick));
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
