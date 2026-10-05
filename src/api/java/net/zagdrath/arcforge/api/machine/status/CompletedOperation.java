/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.status;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One operation a machine finished, as {@link MachineListener#onOperationCompleted} reports it.
 *
 * @param itemsProduced the items it made (copies; empty if none)
 * @param fluidProduced the fluids it made (copies; empty if none)
 * @param itemsConsumed how many items it used up
 * @param fluidConsumed how much fluid it used up, in mB
 */
public record CompletedOperation(List<ItemStack> itemsProduced, List<FluidStack> fluidProduced, long itemsConsumed, long fluidConsumed) {
    /**
     * Copies the lists so the record cannot change.
     *
     * @param itemsProduced the items made
     * @param fluidProduced the fluids made
     * @param itemsConsumed how many items were used up
     * @param fluidConsumed how much fluid was used up, in mB
     */
    public CompletedOperation {
        itemsProduced = List.copyOf(itemsProduced);
        fluidProduced = List.copyOf(fluidProduced);
    }
}
