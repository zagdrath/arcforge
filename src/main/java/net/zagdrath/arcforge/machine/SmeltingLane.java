/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.List;
import java.util.function.ObjIntConsumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.RetortingRecipe;
import net.zagdrath.arcforge.recipe.SinteringRecipe;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One smelting line: an input slot feeding an output slot, using vanilla (and modded) furnace recipes, sintering
// (arcforge:sintering, which only these furnaces do) and, given somewhere for the Mercury to go, retorting
// (arcforge:retorting, the single Induction Furnace's).
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
        return tick(level, items, energy, experience, settings, null, finished);
    }

    // Where a retorting recipe's fluid goes (the Induction Furnace's by-product tank).
    public interface FluidOutput {
        // Whether all of it fits right now.
        boolean fits(FluidStack fluid);

        void add(FluidStack fluid);
    }

    // fluidOutput: where retorting's Mercury goes; a lane without one (null: the array's) doesn't retort.
    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, StoredExperience experience, Settings settings,
            @Nullable FluidOutput fluidOutput, ObjIntConsumer<List<ItemStack>> finished) {
        ItemStack input = items.getStack(inputSlot);
        // Sintering (green fuel pellets) and retorting (amalgams) first: only these furnaces do them, and only the single
        // furnace retorts.
        RecipeHolder<SinteringRecipe> sintering = input.isEmpty() ? null : MachineRecipes.sintering(level, input).orElse(null);
        RecipeHolder<RetortingRecipe> retorting = input.isEmpty() || sintering != null || fluidOutput == null ? null
                : MachineRecipes.retorting(level, input).orElse(null);
        RecipeHolder<SmeltingRecipe> holder = input.isEmpty() || sintering != null || retorting != null ? null : MachineRecipes.smelting(level, input).orElse(null);
        if (holder == null && sintering == null && retorting == null) {
            progress = 0;
            total = 0;
            return State.IDLE;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        int cookingTime = sintering != null ? sintering.value().time() : retorting != null ? retorting.value().time() : holder.value().cookingTime();
        float xp = sintering != null ? sintering.value().experience() : retorting != null ? retorting.value().experience() : holder.value().experience();
        total = Math.max(1, (int) Math.round(cookingTime * settings.timeMultiplier()));
        ItemStack result = sintering != null ? sintering.value().assemble(recipeInput)
                : retorting != null ? retorting.value().assemble(recipeInput) : holder.value().assemble(recipeInput);
        FluidStack fluid = retorting != null ? retorting.value().fluidOutput().create() : FluidStack.EMPTY;
        if (!fits(items.getStack(outputSlot), result) || (!fluid.isEmpty() && !fluidOutput.fits(fluid))) {
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
            if (!fluid.isEmpty()) {
                fluidOutput.add(fluid);
            }
            experience.add(xp);
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
