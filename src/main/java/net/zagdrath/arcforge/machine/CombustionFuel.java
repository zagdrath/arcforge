/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.tag.ModItemTags;

// Fuel for the Combustion Plant and the Firebox: #arcforge:combustion_fuel, burning for its
// vanilla furnace time (coal and charcoal 1,600 ticks, a coal block 16,000).
public final class CombustionFuel {
    private CombustionFuel() {}

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModItemTags.COMBUSTION_FUEL);
    }

    // Vanilla furnace burn time in ticks, as a furnace reads it: on 26.1 from the fuel values (vanilla's list and
    // NeoForge's furnace_fuels data map, which has Arcforge's fuels).
    public static int vanillaBurnTicks(ServerLevel level, BlockEntity machine, ItemStack fuel) {
        return fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
    }

    // Burn time at the given speed (2 = twice as fast as a furnace).
    public static int burnTicks(ServerLevel level, BlockEntity machine, ItemStack fuel, double speed) {
        int ticks = vanillaBurnTicks(level, machine, fuel);
        return ticks <= 0 ? 0 : Math.max(1, (int) Math.round(ticks / speed));
    }
}
