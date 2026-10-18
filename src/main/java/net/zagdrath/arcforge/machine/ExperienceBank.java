/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Experience a machine banks as Liquid Experience (LiquidExperience.MB_PER_POINT mB a point) in a tank of its own, which
// only holds Liquid Experience and is drained through Experience faces (or ports): the smelting experience of the
// Induction Furnace, its array and the Arcforge Furnace, and what the Block Breaker's blocks drop. Fractions of a point
// add up until they make a whole one. A full tank stops banking and the machine pays the rest out the old way (held for
// the player taking the output, or dropped as orbs), so it never stalls a machine that nothing drains.
public class ExperienceBank {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> output;
    private final Runnable onChanged;
    // Part of a point not banked yet.
    private float fraction;

    public ExperienceBank(int capacity, Runnable onChanged) {
        this.onChanged = onChanged;
        this.tank = new FilteredFluidTank(capacity, LiquidExperience::is, onChanged);
        this.output = new AutomationResourceHandler<>(tank, index -> false, index -> true);
    }

    // Banks these points (whole and fractional); returns what didn't fit, to pay out the old way.
    public float bank(float points) {
        if (points <= 0.0F) {
            return 0.0F;
        }
        float total = fraction + points;
        int whole = Mth.floor(total);
        int banked = Math.min(whole, tank.getSpace() / LiquidExperience.MB_PER_POINT);
        if (banked > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                tank.insert(0, LiquidExperience.resource(), banked * LiquidExperience.MB_PER_POINT, tx);
                tx.commit();
            }
        }
        if (banked < whole) {
            // Full: the rest goes out the old way, the fraction with it.
            fraction = 0.0F;
            onChanged.run();
            return total - banked;
        }
        fraction = total - whole;
        onChanged.run();
        return 0.0F;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // What Experience faces give: drain only.
    public ResourceHandler<FluidResource> getOutput() {
        return output;
    }

    public int getAmount() {
        return tank.getAmount();
    }

    public int getCapacity() {
        return tank.getCapacity();
    }

    // The part of a point banked toward the next one.
    public float getFraction() {
        return fraction;
    }

    public void serialize(ValueOutput output) {
        tank.serialize(output.child("experience_tank"));
        output.putFloat("experience_fraction", fraction);
    }

    public void deserialize(ValueInput input) {
        tank.deserialize(input.childOrEmpty("experience_tank"));
        fraction = input.getFloatOr("experience_fraction", 0.0F);
    }
}
