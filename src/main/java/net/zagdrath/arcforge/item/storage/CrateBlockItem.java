/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.block.storage.CrateBlock;

// A Crate as an item: its size, and what it holds when it was picked up with the Wrench. A filled crate can't go
// inside another crate, a vault, a shulker box or a bundle.
public class CrateBlockItem extends StorageBlockItem {
    private static final int LISTED_STACKS = 5;

    public CrateBlockItem(CrateBlock block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        CrateBlock crate = (CrateBlock) getBlock();
        builder.accept(Component.translatable("tooltip.arcforge.crate.slots", crate.getTier().crateSlots()).withStyle(ChatFormatting.GRAY));
        List<ItemStack> stacks = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
        if (stacks.isEmpty()) {
            return;
        }
        builder.accept(Component.translatable("tooltip.arcforge.crate.contents", stacks.size()).withStyle(ChatFormatting.GRAY));
        for (ItemStack held : stacks.subList(0, Math.min(LISTED_STACKS, stacks.size()))) {
            builder.accept(Component.translatable("tooltip.arcforge.vault.contents", held.getCount(), held.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (stacks.size() > LISTED_STACKS) {
            builder.accept(Component.translatable("tooltip.arcforge.crate.more", stacks.size() - LISTED_STACKS).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
