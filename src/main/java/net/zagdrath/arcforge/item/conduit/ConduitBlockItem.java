/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.conduit;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;

// Conduit item with its tier's maximum throughput in the tooltip.
public class ConduitBlockItem extends BlockItem {
    private final ConduitType conduitType;
    private final ConduitTier tier;

    public ConduitBlockItem(ConduitBlock block, Item.Properties properties) {
        super(block, properties);
        this.conduitType = block.getConduitType();
        this.tier = block.getTier();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(tier.describeThroughput(conduitType).copy().withStyle(ChatFormatting.GRAY));
        Component buffer = switch (conduitType) {
            case FLUID -> Component.translatable("tooltip.arcforge.conduit.buffer", String.format("%,d", ConduitTier.FLUID_CAPACITY_PER_CONDUIT));
            case ENERGY -> Component.translatable("tooltip.arcforge.conduit.buffer.energy", String.format("%,d", tier.energyPerTick()));
            case THERMAL -> Component.translatable("tooltip.arcforge.conduit.buffer.thermal", String.format("%,d", tier.heatPerTick()));
            case ITEM -> Component.translatable("tooltip.arcforge.conduit.buffer.item", ConduitTier.ITEM_STORAGE_SLOTS);
        };
        builder.accept(buffer.copy().withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.conduit.mixed_tiers").withStyle(ChatFormatting.DARK_GRAY));
    }
}
