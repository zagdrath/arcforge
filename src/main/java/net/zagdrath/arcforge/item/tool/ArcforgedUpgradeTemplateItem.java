/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

// The Arcforged Upgrade Template: the smithing template for Arcforged gear (ArcforgedUpgradeRecipe), used up on each
// upgrade and duplicated at the crafting table like vanilla's. Its tooltip says what it upgrades and with what, laid out
// as vanilla's templates' are.
public class ArcforgedUpgradeTemplateItem extends Item {
    public ArcforgedUpgradeTemplateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.arcforged_template.upgrade").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.empty());
        builder.accept(Component.translatable("tooltip.arcforge.arcforged_template.applies_to").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.literal(" ").append(Component.translatable("tooltip.arcforge.arcforged_template.bases")).withStyle(ChatFormatting.BLUE));
        builder.accept(Component.translatable("tooltip.arcforge.arcforged_template.ingredients").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.literal(" ").append(Component.translatable("tooltip.arcforge.arcforged_template.addition")).withStyle(ChatFormatting.BLUE));
    }
}
