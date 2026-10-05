/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.List;
import java.util.function.ObjIntConsumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.PressingRecipe;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One pressing line: a die slot, an input slot and an output slot. The Metal Press has one, the Metal
// Pressing Array three, each with its own die. Each tick it draws its FE and advances; when an
// operation finishes it uses up the recipe's count of the input and makes the result. The die is never
// used up, and taking it out (or swapping it) starts the operation over.
public class PressingLane {
    // How a machine runs its lanes, with its upgrades applied.
    public record Settings(double timeMultiplier, int energyPerTick) {}

    public enum State { IDLE, NO_DIE, WORKING, NO_POWER, OUTPUT_FULL }

    private final int dieSlot;
    private final int inputSlot;
    private final int outputSlot;
    private int progress;
    private int total;
    // The die the progress was made with.
    private Item die = Items.AIR;

    public PressingLane(int dieSlot, int inputSlot, int outputSlot) {
        this.dieSlot = dieSlot;
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, Settings settings) {
        return tick(level, items, energy, settings, (produced, used) -> {});
    }

    // finished: told about each finished operation, with what it made (a fresh stack) and how many items it used.
    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, Settings settings,
            ObjIntConsumer<List<ItemStack>> finished) {
        ItemStack dieStack = items.getStack(dieSlot);
        if (!dieStack.is(die)) {
            die = dieStack.getItem();
            progress = 0;
        }
        if (dieStack.isEmpty()) {
            total = 0;
            return State.NO_DIE;
        }
        ItemStack input = items.getStack(inputSlot);
        RecipeHolder<PressingRecipe> holder = input.isEmpty() ? null : MachineRecipes.pressing(level, dieStack, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return State.IDLE;
        }
        PressingRecipe recipe = holder.value();
        total = Math.max(1, (int) Math.round(recipe.time() * settings.timeMultiplier()));
        ItemStack result = recipe.result().create();
        if (!fits(items.getStack(outputSlot), result)) {
            return State.OUTPUT_FULL;
        }
        if (!energy.consume(settings.energyPerTick())) {
            return State.NO_POWER;
        }
        progress++;
        if (progress >= total) {
            items.setStack(inputSlot, input.copyWithCount(input.getCount() - recipe.count()));
            ItemStack current = items.getStack(outputSlot);
            items.setStack(outputSlot, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            finished.accept(List.of(result), recipe.count());
            progress = 0;
        }
        return State.WORKING;
    }

    private static boolean fits(ItemStack slot, ItemStack product) {
        if (slot.isEmpty()) {
            return product.getCount() <= product.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(slot, product) && slot.getCount() + product.getCount() <= slot.getMaxStackSize();
    }

    public void serialize(ValueOutput output) {
        output.putInt("progress", progress);
    }

    // The die isn't saved: the one in the slot on load is the one the progress was made with.
    public void deserialize(ValueInput input, FilteredItemHandler items) {
        progress = input.getIntOr("progress", 0);
        die = items.getStack(dieSlot).getItem();
    }
}
