/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.List;
import java.util.function.ObjIntConsumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One smelting line: an input slot feeding an output slot, using vanilla (and modded) furnace recipes.
// The Induction Furnace has one, the Induction Furnace Array three. Each tick it draws its FE and
// advances; each finished item adds the recipe's XP to the machine's store.
public class SmeltingLane {
    // How a machine runs its lanes, with its upgrades applied. timeMultiplier scales the recipe's cooking time.
    public record Settings(double timeMultiplier, int energyPerTick) {}

    public enum State { IDLE, WORKING, NO_POWER, OUTPUT_FULL }

    private final int inputSlot;
    private final int outputSlot;
    private int progress;
    private int total;

    public SmeltingLane(int inputSlot, int outputSlot) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, StoredExperience experience, Settings settings) {
        return tick(level, items, energy, experience, settings, (produced, used) -> {});
    }

    // finished: told about each finished item, with what it made (a fresh stack) and how many items it used.
    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, StoredExperience experience, Settings settings,
            ObjIntConsumer<List<ItemStack>> finished) {
        ItemStack input = items.getStack(inputSlot);
        RecipeHolder<SmeltingRecipe> holder = input.isEmpty() ? null : MachineRecipes.smelting(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return State.IDLE;
        }
        SmeltingRecipe recipe = holder.value();
        total = Math.max(1, (int) Math.round(recipe.cookingTime() * settings.timeMultiplier()));
        ItemStack result = recipe.assemble(new SingleRecipeInput(input));
        if (!fits(items.getStack(outputSlot), result)) {
            return State.OUTPUT_FULL;
        }
        if (!energy.consume(settings.energyPerTick())) {
            return State.NO_POWER;
        }
        progress++;
        if (progress >= total) {
            items.setStack(inputSlot, input.copyWithCount(input.getCount() - 1));
            ItemStack current = items.getStack(outputSlot);
            items.setStack(outputSlot, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            experience.add(recipe.experience());
            finished.accept(List.of(result), 1);
            progress = 0;
        }
        return State.WORKING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public void serialize(ValueOutput output) {
        output.putInt("progress", progress);
    }

    public void deserialize(ValueInput input) {
        progress = input.getIntOr("progress", 0);
    }
}
