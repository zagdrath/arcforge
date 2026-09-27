/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.item;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.registry.ModDataComponents;

// An energy cell item's charge level (0-4), the same value the placed block shows, so the item
// model can light the matching segments (see items/<tier>_energy_cell.json).
public record CellChargeProperty() implements RangeSelectItemModelProperty {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "cell_charge");
    public static final MapCodec<CellChargeProperty> MAP_CODEC = MapCodec.unit(new CellChargeProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof StorageBlock block)) {
            return 0;
        }
        return EnergyCellBlockEntity.chargeLevel(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), block.getTier().cellCapacity());
    }

    @Override
    public MapCodec<CellChargeProperty> type() {
        return MAP_CODEC;
    }
}
