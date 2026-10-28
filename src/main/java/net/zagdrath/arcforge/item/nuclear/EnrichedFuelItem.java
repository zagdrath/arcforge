/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.nuclear;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.nuclear.Enrichment;

// Uranium Dioxide and its pressed/sintered pellets carry the same assay through the fuel production line.
public class EnrichedFuelItem extends Item {
    public EnrichedFuelItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.fuel.enrichment", Enrichment.percent(Enrichment.assay(stack)))
                .withStyle(ChatFormatting.GRAY));
    }
}
