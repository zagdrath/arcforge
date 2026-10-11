/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// The Item Reservoir item: its slots, and the stacks a wrenched block kept.
public class ItemReservoirItem extends BlockItem {
    public ItemReservoirItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    // A wrenched block holding items can't go inside other storage, so storage never nests.
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.crate.slots", ArcforgeConfig.ITEM_RESERVOIR_SLOTS.getAsInt())
                .withStyle(ChatFormatting.GRAY));
        long stacks = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
        if (stacks > 0) {
            builder.accept(Component.translatable("tooltip.arcforge.crate.contents", stacks).withStyle(ChatFormatting.GRAY));
        }
    }
}
