/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import java.util.function.DoubleSupplier;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Oxy-fuel for a Firebox or Fuel Burner: an oxygen tank, filled through an Oxygen face, and the oxygen it burns.
// Each tick the machine burns with enough oxygen for it, it takes oxygenPerTick (whole mB at a time, as the
// fraction adds up) and burns temperatureBonus hotter (up to maxTemperature), making heatMultiplier times the heat.
// Without oxygen nothing about the machine changes.
public final class OxyFuel {
    private final FilteredFluidTank oxygen;
    private final ResourceHandler<FluidResource> input;
    private final DoubleSupplier perTick;
    // Oxygen burnt (in mB) but not yet taken from the tank.
    private double owed;
    private boolean active;

    public OxyFuel(int capacity, DoubleSupplier perTick, Runnable onChanged) {
        this.oxygen = new FilteredFluidTank(capacity, resource -> resource.is(ModFluids.OXYGEN.get()), onChanged);
        this.input = new AutomationResourceHandler<>(oxygen, index -> true, index -> false);
        this.perTick = perTick;
    }

    // The burn temperature on oxy-fuel for a normal one.
    public static int temperature(int normalCelsius) {
        return Math.min(normalCelsius + ArcforgeConfig.OXY_FUEL_TEMPERATURE_BONUS.getAsInt(), ArcforgeConfig.OXY_FUEL_MAX_TEMPERATURE.getAsInt());
    }

    public static double heatMultiplier() {
        return ArcforgeConfig.OXY_FUEL_HEAT_MULTIPLIER.getAsDouble();
    }

    // Whether the tank holds enough for a tick of oxy-fuel: the whole mB that tick would take.
    public boolean available() {
        int amount = oxygen.getAmount();
        return amount > 0 && Math.floor(owed + perTick.getAsDouble()) <= amount;
    }

    // A tick of burning: on oxy-fuel if there's the oxygen for it (which it then uses). Returns whether it is.
    public boolean burn() {
        if (!available()) {
            active = false;
            return false;
        }
        owed += perTick.getAsDouble();
        int take = Math.min(oxygen.getAmount(), (int) Math.floor(owed));
        if (take > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                owed -= oxygen.extract(0, oxygen.getResource(0), take, tx);
                tx.commit();
            }
        }
        active = true;
        return true;
    }

    // A tick without burning.
    public void idle() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public FilteredFluidTank getTank() {
        return oxygen;
    }

    // What an Oxygen face takes: oxygen only, nothing back out.
    public ResourceHandler<FluidResource> getInput() {
        return input;
    }

    public void load(ValueInput input) {
        oxygen.deserialize(input.childOrEmpty("oxygen"));
        owed = input.getDoubleOr("oxygen_owed", 0.0);
    }

    public void save(ValueOutput output) {
        oxygen.serialize(output.child("oxygen"));
        output.putDouble("oxygen_owed", owed);
    }
}
