/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.NeoForgeLootContextParams;
import net.zagdrath.arcforge.tag.ModItemTags;

// Fuel for the Combustion Generator and the Firebox: #arcforge:combustion_fuel, burning for its
// vanilla furnace time (coal and charcoal 1,600 ticks, a coal block 16,000).
public final class CombustionFuel {
    private CombustionFuel() {}

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModItemTags.COMBUSTION_FUEL);
    }

    // Vanilla furnace burn time in ticks, resolved the same way a furnace resolves it.
    public static int vanillaBurnTicks(ServerLevel level, BlockEntity machine, ItemStack fuel) {
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, machine.getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, machine)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(machine.getBlockPos()))
                .withParameter(LootContextParams.CONTAINER, new SimpleContainer(fuel.copyWithCount(1)))
                .withOptionalParameter(NeoForgeLootContextParams.QUERIED_STACK, fuel)
                .create(LootContextParamSets.CONTAINER_PROCESS))
                .create(Optional.empty());
        return ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    // Burn time at the given speed (2 = twice as fast as a furnace).
    public static int burnTicks(ServerLevel level, BlockEntity machine, ItemStack fuel, double speed) {
        int ticks = vanillaBurnTicks(level, machine, fuel);
        return ticks <= 0 ? 0 : Math.max(1, (int) Math.round(ticks / speed));
    }
}
