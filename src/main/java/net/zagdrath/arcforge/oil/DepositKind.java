/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// The kinds of oil deposit, smallest first: how much Crude Oil each holds (in buckets, before the config's multiplier),
// how far it spreads (its radius in chunks), and how likely each is in an ordinary region and in an oil-rich one.
public enum DepositKind implements StringRepresentable {
    SEEP("seep", 1_000, 3_000, 1, 2, 45, 25),
    POCKET("pocket", 4_000, 10_000, 2, 3, 30, 35),
    FIELD("field", 12_000, 30_000, 2, 3, 18, 27),
    GIANT("giant", 40_000, 100_000, 3, 4, 7, 13);

    private final String name;
    public final int minBuckets, maxBuckets;
    public final int minRadius, maxRadius;
    public final int weight, richWeight;

    DepositKind(String name, int minBuckets, int maxBuckets, int minRadius, int maxRadius, int weight, int richWeight) {
        this.name = name;
        this.minBuckets = minBuckets;
        this.maxBuckets = maxBuckets;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.weight = weight;
        this.richWeight = richWeight;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component getDescription() {
        return Component.translatable("oil.arcforge.deposit." + name);
    }

    // A kind picked by weight from roll (0 <= roll < 1).
    public static DepositKind pick(double roll, boolean rich) {
        int total = 0;
        for (DepositKind kind : values()) {
            total += rich ? kind.richWeight : kind.weight;
        }
        double at = roll * total;
        for (DepositKind kind : values()) {
            at -= rich ? kind.richWeight : kind.weight;
            if (at < 0) {
                return kind;
            }
        }
        return GIANT;
    }

    public static DepositKind byName(String name) {
        for (DepositKind kind : values()) {
            if (kind.name.equals(name)) {
                return kind;
            }
        }
        return SEEP;
    }
}
