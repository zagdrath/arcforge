/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.recipe.ItemProcessingRecipe;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One item line for ItemProcessingRecipes: an input slot feeding a result slot and a bonus slot, like CrushingLane.
// The Mill has three, the Seed Extractor one. Each tick it draws its FE and advances; when an item is done it makes
// the result and rolls the bonus once. The Millstone uses the static fits/finish helpers with its own turns.
public class ItemLane {
    // How a machine runs its lanes, with its upgrades applied.
    public record Settings(double timeMultiplier, int energyPerTick) {}

    public enum State { IDLE, WORKING, NO_POWER, OUTPUT_FULL }

    private final int inputSlot;
    private final int outputSlot;
    private final int bonusSlot;
    private int progress;
    private int total;

    public ItemLane(int inputSlot, int outputSlot, int bonusSlot) {
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

    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, Settings settings,
            Function<ItemStack, Optional<? extends ItemProcessingRecipe>> lookup) {
        return tick(level, items, energy, settings, lookup, (produced, used) -> {});
    }

    // finished: told about each finished item, with what it made (fresh stacks) and how many items it used.
    public State tick(ServerLevel level, FilteredItemHandler items, ConsumerEnergyHandler energy, Settings settings,
            Function<ItemStack, Optional<? extends ItemProcessingRecipe>> lookup, ObjIntConsumer<List<ItemStack>> finished) {
        ItemStack input = items.getStack(inputSlot);
        ItemProcessingRecipe recipe = input.isEmpty() ? null : lookup.apply(input).orElse(null);
        if (recipe == null) {
            progress = 0;
            total = 0;
            return State.IDLE;
        }
        total = Math.max(1, (int) Math.round(recipe.time() * settings.timeMultiplier()));
        if (!fits(items, outputSlot, bonusSlot, recipe)) {
            return State.OUTPUT_FULL;
        }
        if (!energy.consume(settings.energyPerTick())) {
            return State.NO_POWER;
        }
        progress++;
        if (progress >= total) {
            finished.accept(finish(level, items, inputSlot, outputSlot, bonusSlot, recipe), 1);
            progress = 0;
        }
        return State.WORKING;
    }

    // Whether the result and a possible bonus fit in their slots right now.
    public static boolean fits(FilteredItemHandler items, int outputSlot, int bonusSlot, ItemProcessingRecipe recipe) {
        ItemStack result = recipe.result().create();
        if (!fits(items.getStack(outputSlot), result)) {
            return false;
        }
        return recipe.bonus().isEmpty() || recipe.bonusChance() <= 0 || fits(items.getStack(bonusSlot), recipe.bonus().get().create());
    }

    private static boolean fits(ItemStack slot, ItemStack product) {
        if (slot.isEmpty()) {
            return product.getCount() <= product.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(slot, product) && slot.getCount() + product.getCount() <= slot.getMaxStackSize();
    }

    // Uses one input and makes the result, rolling the bonus once. Returns copies of what it made.
    public static List<ItemStack> finish(ServerLevel level, FilteredItemHandler items, int inputSlot, int outputSlot, int bonusSlot, ItemProcessingRecipe recipe) {
        ItemStack input = items.getStack(inputSlot);
        items.setStack(inputSlot, input.copyWithCount(input.getCount() - 1));
        List<ItemStack> made = new ArrayList<>(2);
        ItemStack result = recipe.result().create();
        made.add(result.copy());
        add(items, outputSlot, result);
        if (recipe.bonus().isPresent() && level.getRandom().nextFloat() < recipe.bonusChance()) {
            ItemStack bonus = recipe.bonus().get().create();
            made.add(bonus.copy());
            add(items, bonusSlot, bonus);
        }
        return made;
    }

    private static void add(FilteredItemHandler items, int slot, ItemStack product) {
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? product : current.copyWithCount(current.getCount() + product.getCount()));
    }

    public void serialize(ValueOutput output) {
        output.putInt("progress", progress);
    }

    public void deserialize(ValueInput input) {
        progress = input.getIntOr("progress", 0);
    }
}
