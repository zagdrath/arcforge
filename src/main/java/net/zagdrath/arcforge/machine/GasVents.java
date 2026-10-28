/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.machine;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Persisted gas-overflow controls, shared by machines and arrays. Registered outputs keep stable indices.
public final class GasVents {
    public static final int MAX_OUTPUTS = 8, VALUES = 2 + MAX_OUTPUTS, BUTTON_FIRST = 500;
    private record Output(FilteredFluidTank tank, @Nullable Fluid fallback) {}
    private final List<Output> outputs = new ArrayList<>();
    private final Runnable changed;
    private int enabled;
    private int defaults;

    public GasVents(Runnable changed) {
        this.changed = changed;
    }

    public void add(FilteredFluidTank tank, @Nullable Fluid fallback) {
        add(tank, fallback, false);
    }

    public void add(FilteredFluidTank tank, @Nullable Fluid fallback, boolean initiallyEnabled) {
        if (outputs.size() >= MAX_OUTPUTS) throw new IllegalStateException("Too many vent outputs");
        outputs.add(new Output(tank, fallback));
        if (initiallyEnabled) {
            defaults |= 1 << (size() - 1);
            apply(enabled | 1 << (size() - 1));
        }
    }

    public int size() { return outputs.size(); }

    public boolean isEnabled(int index) {
        return index >= 0 && index < size() && (enabled & 1 << index) != 0;
    }

    public void toggle(int index) {
        if (index >= 0 && index < size()) {
            apply(enabled ^ 1 << index);
            changed.run();
        }
    }

    private void apply(int mask) {
        enabled = mask & ((1 << size()) - 1);
        for (int index = 0; index < size(); index++) outputs.get(index).tank().setVentingGas(isEnabled(index));
    }

    public void load(ValueInput input) { apply(input.getIntOr("gas_vents", defaults)); }

    public void readSettings(ValueInput input) {
        input.getInt("gas_vents").ifPresent(this::apply);
        changed.run();
    }

    public void save(ValueOutput output) { output.putInt("gas_vents", enabled); }

    public ContainerData data() {
        return new WideIntContainerData(VALUES) {
            @Override
            protected int getValue(int index) {
                if (index == 0) return size();
                if (index == 1) return enabled;
                int slot = index - 2;
                if (slot < 0 || slot >= size()) return -1;
                Output output = outputs.get(slot);
                Fluid fluid = output.tank().getAmount() > 0 ? output.tank().getResource(0).getFluid() : output.fallback();
                return fluid == null ? -1 : BuiltInRegistries.FLUID.getId(fluid);
            }
        };
    }
}
