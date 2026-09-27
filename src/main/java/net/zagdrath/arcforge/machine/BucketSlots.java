/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// A machine's bucket-in slot above a bucket-out slot: full buckets are poured into its tank (or, for
// machines that give fluid out, empty buckets are filled from it) and the
// empty buckets come out below.
public final class BucketSlots {
    private BucketSlots() {}

    // The fluid in a full bucket, or null for anything else.
    public static @Nullable FluidResource contents(ItemStack stack) {
        return stack.getItem() instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY ? FluidResource.of(bucket.getContent()) : null;
    }

    // Pours the bucket in the input slot into the tank if all of it fits (and the tank takes that fluid),
    // moving the empty bucket to the output slot.
    public static void pour(FilteredItemHandler items, int inputSlot, int outputSlot, FilteredFluidTank tank) {
        ItemStack input = items.getStack(inputSlot);
        FluidResource fluid = contents(input);
        if (fluid == null || tank.getSpace() < FluidType.BUCKET_VOLUME) {
            return;
        }
        ItemStack output = items.getStack(outputSlot);
        if (!output.isEmpty() && (!output.is(Items.BUCKET) || output.getCount() >= output.getMaxStackSize())) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (tank.insert(0, fluid, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME) {
                return;
            }
            tx.commit();
        }
        items.setStack(inputSlot, input.copyWithCount(input.getCount() - 1));
        items.setStack(outputSlot, output.isEmpty() ? new ItemStack(Items.BUCKET) : output.copyWithCount(output.getCount() + 1));
    }

    // The other way round: fills the empty bucket in the input slot from the tank (if it holds a bucket of
    // a fluid that has a bucket), moving the full bucket to the output slot.
    public static void fill(FilteredItemHandler items, int inputSlot, int outputSlot, FilteredFluidTank tank) {
        ItemStack input = items.getStack(inputSlot);
        FluidResource fluid = tank.getResource(0);
        if (!input.is(Items.BUCKET) || fluid.isEmpty() || tank.getAmount() < FluidType.BUCKET_VOLUME) {
            return;
        }
        ItemStack filled = new ItemStack(fluid.getFluid().getBucket());
        ItemStack output = items.getStack(outputSlot);
        if (filled.isEmpty() || !output.isEmpty()) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (tank.extract(0, fluid, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME) {
                return;
            }
            tx.commit();
        }
        items.setStack(inputSlot, input.copyWithCount(input.getCount() - 1));
        items.setStack(outputSlot, filled);
    }
}
