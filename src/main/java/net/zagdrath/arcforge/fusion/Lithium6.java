/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import java.util.Locale;

import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// Lithium-6 enrichment (the arcforge:li6_assay component, in tenths of a percent). Natural lithium is 7.5% Li-6; the
// Lithium Isotope Separator raises it a step a pass (fusion.lithium.enrichmentStep) as Enriched Lithium Fluoride, until
// it reaches fusion.lithium.enrichedMinimum: then it comes out as Lithium-6 Fluoride, what Lead-Lithium and Lithium
// Target Rods are made from.
public final class Lithium6 {
    public static final int NATURAL = 75;

    private Lithium6() {}

    // A stack's Li-6 share (tenths of a percent): its component, or the item's own (natural Lithium Fluoride; Lithium-6
    // Fluoride at the minimum).
    public static int assay(ItemStack stack) {
        Integer assay = stack.get(ModDataComponents.LI6_ASSAY.get());
        if (assay != null) {
            return assay;
        }
        return stack.is(ModItems.LITHIUM_6_FLUORIDE.get()) ? minimum() : NATURAL;
    }

    public static int step() {
        return (int) Math.round(ArcforgeConfig.LITHIUM_ENRICHMENT_STEP.getAsDouble() * 10.0);
    }

    public static int max() {
        return (int) Math.round(ArcforgeConfig.LITHIUM_MAX_ENRICHMENT.getAsDouble() * 10.0);
    }

    public static int minimum() {
        return (int) Math.round(ArcforgeConfig.LITHIUM_ENRICHED_MINIMUM.getAsDouble() * 10.0);
    }

    // Whether a stack is something the separator takes: lithium fluoride at any enrichment below its top.
    public static boolean isFeed(ItemStack stack) {
        return (stack.is(ModItems.LITHIUM_FLUORIDE.get()) || stack.is(ModItems.ENRICHED_LITHIUM_FLUORIDE.get())
                || stack.is(ModItems.LITHIUM_6_FLUORIDE.get())) && assay(stack) < max();
    }

    // What one pass makes from a feed of this assay: Enriched Lithium Fluoride, or Lithium-6 Fluoride once it's high enough.
    public static ItemStack product(int assay) {
        int next = Math.min(max(), assay + step());
        ItemStack out = new ItemStack(next >= minimum() ? ModItems.LITHIUM_6_FLUORIDE.get() : ModItems.ENRICHED_LITHIUM_FLUORIDE.get());
        out.set(ModDataComponents.LI6_ASSAY.get(), next);
        return out;
    }

    public static String percent(int assay) {
        return String.format(Locale.ROOT, "%.1f%%", assay / 10.0);
    }
}
