/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.recipe.CrushingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One crushing line: an input slot feeding a main output and a bonus output. The Arc Crusher has one,
// the Arc Crushing Array three. Each tick it draws its FE and advances; when an operation finishes it
// makes the result (times oreYield for ore recipes), if the recipe has one, and rolls the bonus once.
public class CrushingLane {
    // How a machine runs its lanes, with its upgrades applied.
    public record Settings(double timeMultiplier, int energyPerTick, int oreYield) {}

    public enum State { IDLE, WORKING, NO_POWER, OUTPUT_FULL }

    private final int inputSlot;
    private final int outputSlot;
    private final int bonusSlot;
    private int progress;
    private int total;

    public CrushingLane(int inputSlot, int outputSlot, int bonusSlot) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.bonusSlot = bonusSlot;
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

    // finished: told about each finished operation, with what it made (fresh stacks) and how many items it used.
    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, Settings settings,
            ObjIntConsumer<List<ItemStack>> finished) {
        ItemStack input = items.getStack(inputSlot);
        RecipeHolder<CrushingRecipe> holder = input.isEmpty() ? null : MachineRecipes.crushing(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return State.IDLE;
        }
        CrushingRecipe recipe = holder.value();
        total = Math.max(1, (int) Math.round(recipe.time() * settings.timeMultiplier()));
        int yield = recipe.ore() ? settings.oreYield() : 1;
        if (!fits(items, recipe, yield)) {
            return State.OUTPUT_FULL;
        }
        if (!energy.consume(settings.energyPerTick())) {
            return State.NO_POWER;
        }
        progress++;
        if (progress >= total) {
            finished.accept(finish(level, items, recipe, yield), 1);
            progress = 0;
        }
        return State.WORKING;
    }

    // Whether the result (and a possible bonus) would fit in the output slots right now.
    private boolean fits(FilteredItemHandler items, CrushingRecipe recipe, int yield) {
        if (recipe.result().isPresent()) {
            ItemStack result = recipe.result().get().create();
            if (!fits(items.getStack(outputSlot), result, result.getCount() * yield)) {
                return false;
            }
        }
        return recipe.bonus().isEmpty() || fits(items.getStack(bonusSlot), recipe.bonus().get().create(), recipe.bonus().get().create().getCount());
    }

    private static boolean fits(ItemStack slot, ItemStack product, int count) {
        if (slot.isEmpty()) {
            return count <= product.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(slot, product) && slot.getCount() + count <= slot.getMaxStackSize();
    }

    // Returns copies of what it made.
    private List<ItemStack> finish(ServerLevel level, FilteredItemHandler items, CrushingRecipe recipe, int yield) {
        ItemStack input = items.getStack(inputSlot);
        items.setStack(inputSlot, input.copyWithCount(input.getCount() - 1));
        List<ItemStack> made = new ArrayList<>(2);
        recipe.result().ifPresent(template -> {
            ItemStack result = template.create();
            made.add(result.copyWithCount(result.getCount() * yield));
            add(items, outputSlot, result, result.getCount() * yield);
        });
        if (recipe.bonus().isPresent() && level.getRandom().nextFloat() < recipe.bonusChance()) {
            ItemStack bonus = recipe.bonus().get().create();
            made.add(bonus.copy());
            add(items, bonusSlot, bonus, bonus.getCount());
        }
        return made;
    }

    private static void add(FilteredItemHandler items, int slot, ItemStack product, int count) {
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? product.copyWithCount(count) : current.copyWithCount(current.getCount() + count));
    }

    public void serialize(ValueOutput output) {
        output.putInt("progress", progress);
    }

    public void deserialize(ValueInput input) {
        progress = input.getIntOr("progress", 0);
    }
}
