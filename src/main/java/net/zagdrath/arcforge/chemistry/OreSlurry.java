/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.chemistry;

import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The metals the Chemical Reactor leaches into slurry (with sulfuric acid) and precipitates back into dust,
// three per raw ore. Each slurry is its own fluid (<metal>_slurry), drawn with one greyscale texture in its
// metal's tint. Fluorite and arcite are gems and aren't leached.
// The dusts are looked up lazily: ModItems builds the slurry buckets from this enum.
public enum OreSlurry {
    IRON("iron", 0xFFB08A6E, () -> ModItems.IRON_DUST.get()),
    COPPER("copper", 0xFFC0683E, () -> ModItems.COPPER_DUST.get()),
    GOLD("gold", 0xFFD6A93A, () -> ModItems.GOLD_DUST.get()),
    SILVER("silver", 0xFFA8B6C8, () -> ModItems.SILVER_DUST.get()),
    NICKEL("nickel", 0xFFB8AC72, () -> ModItems.NICKEL_DUST.get()),
    TUNGSTEN("tungsten", 0xFF6E7680, () -> ModItems.TUNGSTEN_DUST.get()),
    BISMUTH("bismuth", 0xFFB89CB8, () -> ModItems.BISMUTH_DUST.get());

    private final String metal;
    private final int tint;
    private final Supplier<? extends Item> dust;

    OreSlurry(String metal, int tint, Supplier<? extends Item> dust) {
        this.metal = metal;
        this.tint = tint;
        this.dust = dust;
    }

    public String metal() {
        return metal;
    }

    // The fluid's registry name, e.g. iron_slurry.
    public String fluidName() {
        return metal + "_slurry";
    }

    // ARGB, for the fluid in the world, buckets, tanks and GUIs.
    public int tint() {
        return tint;
    }

    public Item dust() {
        return dust.get();
    }

    public Fluid fluid() {
        return ModFluids.slurry(this).source().get();
    }
}
