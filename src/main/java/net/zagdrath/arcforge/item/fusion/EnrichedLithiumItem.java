/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.fusion;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.fusion.Lithium6;

// Enriched Lithium Fluoride and Lithium-6 Fluoride: lithium fluoride with more of its lithium-6 than nature's 7.5%, shown in
// the tooltip (see Lithium6).
public class EnrichedLithiumItem extends Item {
    public EnrichedLithiumItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.li6_assay", Lithium6.percent(Lithium6.assay(stack))).withStyle(ChatFormatting.GRAY));
    }
}
