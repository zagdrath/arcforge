/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.Locale;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;

// Uranium enrichment: the U-235 assay Uranium Hexafluoride carries (ModDataComponents.U235_ASSAY, in thousandths of a
// percent, "pcm"), and the separative work a Gas Centrifuge Cascade spends to split feed into product and tails.
//  - Natural UF6 (from the conversion line) carries no component: 0.711%. Depleted UF6 without one is at the default
//    tails assay, 0.25%. Anything else is set on the stack, so tanks and conduits keep different assays apart (they
//    only merge equal stacks).
//  - Civil fuel only: nothing goes above MAX_PCM (20%, the top of low-enriched uranium). The component's codec refuses
//    more, the cascade's target stops there, and withAssay holds every value to it.
//  - Separative work (the standard value function): V(x) = (2x - 1) ln(x / (1 - x)). For a product assay xp from feed
//    xf, leaving tails xt, each unit of product takes F = (xp - xt) / (xf - xt) units of feed, leaves T = F - 1 units of
//    tails, and costs V(xp) + T V(xt) - F V(xf) SWU. A unit is a mB of UF6 here. So 5% product from natural feed at
//    0.25% tails takes about 10.3 mB of feed and 7.9 SWU a mB; 20% takes 42.8 mB and 41.6 SWU; tails at 0.2% instead
//    take less feed and more SWU.
public final class Enrichment {
    public static final int NATURAL_PCM = 711;
    public static final int DEFAULT_TAILS_PCM = 250;
    public static final int MAX_PCM = 20_000;
    // The cascade's target range (1% to 20%) and tails range (0.1% to 0.5%), and their step.
    public static final int MIN_TARGET_PCM = 1_000;
    public static final int MIN_TAILS_PCM = 100, MAX_TAILS_PCM = 500;

    private Enrichment() {}

    // The assay a fluid has without a component: natural for UF6, the default tails for depleted UF6, 0 otherwise.
    public static int defaultAssay(Fluid fluid) {
        if (fluid.isSame(ModFluids.URANIUM_HEXAFLUORIDE.get())) {
            return NATURAL_PCM;
        }
        return fluid.isSame(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get()) ? DEFAULT_TAILS_PCM : 0;
    }

    // Whether the fluid carries an assay (UF6 or its tails).
    public static boolean hasAssay(Fluid fluid) {
        return defaultAssay(fluid) > 0;
    }

    public static int assay(FluidStack stack) {
        if (stack.isEmpty() || !hasAssay(stack.getFluid())) {
            return 0;
        }
        Integer set = stack.get(ModDataComponents.U235_ASSAY.get());
        return set != null ? set : defaultAssay(stack.getFluid());
    }

    public static int assay(FluidResource resource) {
        return resource.isEmpty() ? 0 : assay(resource.toStack(1));
    }

    // The stack at this assay (held to 0.001%..20%); the fluid's default assay drops the component, so it matches
    // the plain fluid from a recipe.
    public static FluidStack withAssay(FluidStack stack, int pcm) {
        FluidStack copy = stack.copy();
        int held = Math.max(1, Math.min(MAX_PCM, pcm));
        if (held == defaultAssay(copy.getFluid())) {
            copy.remove(ModDataComponents.U235_ASSAY.get());
        } else {
            copy.set(ModDataComponents.U235_ASSAY.get(), held);
        }
        return copy;
    }

    public static FluidResource withAssay(Fluid fluid, int pcm) {
        return FluidResource.of(withAssay(new FluidStack(fluid, 1), pcm));
    }

    // "0.711%", "4.5%", "20%": up to three decimals, no trailing zeros.
    // --- Items that carry an assay (enriched Uranium Dioxide, its pellets) ---

    // An item's assay: its U-235 assay component, or natural without one.
    public static int assay(net.minecraft.world.item.ItemStack stack) {
        return stack.getOrDefault(net.zagdrath.arcforge.registry.ModDataComponents.U235_ASSAY.get(), NATURAL_PCM);
    }

    // The result of working an item keeps its assay (the Metal Press, sintering, the Chemical Reactor making UO2 from
    // UF6): from's assay component, if it has one, onto to.
    public static net.minecraft.world.item.ItemStack carry(net.minecraft.world.item.ItemStack from, net.minecraft.world.item.ItemStack to) {
        Integer assay = from.get(net.zagdrath.arcforge.registry.ModDataComponents.U235_ASSAY.get());
        if (assay != null && !to.isEmpty()) {
            to.set(net.zagdrath.arcforge.registry.ModDataComponents.U235_ASSAY.get(), Math.max(1, Math.min(MAX_PCM, assay)));
        }
        return to;
    }

    // The same from a fluid (UF6 into UO2): only an enriched or depleted one marks the item; natural stays unmarked.
    public static net.minecraft.world.item.ItemStack carry(FluidResource from, net.minecraft.world.item.ItemStack to) {
        if (!from.isEmpty() && hasAssay(from.getFluid()) && !to.isEmpty()) {
            int assay = assay(from);
            if (assay != NATURAL_PCM) {
                to.set(net.zagdrath.arcforge.registry.ModDataComponents.U235_ASSAY.get(), Math.max(1, Math.min(MAX_PCM, assay)));
            }
        }
        return to;
    }

    public static String percent(int pcm) {
        String text = String.format(Locale.ROOT, "%.3f", pcm / 1000.0);
        text = text.replaceAll("0+$", "");
        return (text.endsWith(".") ? text.substring(0, text.length() - 1) : text) + "%";
    }

    // --- Separative work ---

    // The value function V(x), x a fraction (0 < x < 1).
    public static double value(double x) {
        return (2.0 * x - 1.0) * Math.log(x / (1.0 - x));
    }

    // Feed (mB) each mB of product takes, or +inf when the feed can't reach the product (feed not between tails and
    // product).
    public static double feedPerProduct(int productPcm, int feedPcm, int tailsPcm) {
        if (!(tailsPcm < feedPcm && feedPcm < productPcm)) {
            return Double.POSITIVE_INFINITY;
        }
        return (double) (productPcm - tailsPcm) / (feedPcm - tailsPcm);
    }

    // SWU each mB of product costs (+inf as above).
    public static double swuPerProduct(int productPcm, int feedPcm, int tailsPcm) {
        double feed = feedPerProduct(productPcm, feedPcm, tailsPcm);
        if (Double.isInfinite(feed)) {
            return feed;
        }
        double xp = productPcm / 100_000.0, xf = feedPcm / 100_000.0, xt = tailsPcm / 100_000.0;
        return value(xp) + (feed - 1.0) * value(xt) - feed * value(xf);
    }

    // How much more radioactive UF6 is than natural at this assay (U-234 comes up with U-235): (assay / natural)^0.75,
    // about 12 at 20% and 0.46 at 0.25%.
    public static double activityFactor(int pcm) {
        return pcm <= 0 ? 1.0 : Math.pow(pcm / (double) NATURAL_PCM, 0.75);
    }
}
