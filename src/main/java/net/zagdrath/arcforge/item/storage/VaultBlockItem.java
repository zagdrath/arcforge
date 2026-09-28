/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.storage.StorageCounts;
import net.zagdrath.arcforge.storage.VaultContents;

// A Vault as an item: its capacity and what it holds (kept whether it was broken or picked up), with its lock and
// void settings. A vault holding anything can't go inside a crate, another vault, a shulker box or a bundle.
public class VaultBlockItem extends StorageBlockItem {
    public VaultBlockItem(VaultBlock block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY).amount() == 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        VaultBlock vault = (VaultBlock) getBlock();
        builder.accept(Component.translatable("tooltip.arcforge.vault.capacity", StorageCounts.grouped(vault.getTier().vaultCapacity()))
                .withStyle(ChatFormatting.GRAY));
        VaultContents contents = stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY);
        ItemStack template = contents.template();
        if (contents.amount() > 0) {
            builder.accept(Component.translatable("tooltip.arcforge.vault.contents", StorageCounts.grouped(contents.amount()), template.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (contents.locked() && !template.isEmpty()) {
            builder.accept(Component.translatable("tooltip.arcforge.vault.locked", template.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        if (contents.voidMode()) {
            builder.accept(Component.translatable("tooltip.arcforge.vault.void").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
