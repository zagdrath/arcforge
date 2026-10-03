/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.upgrade;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// A Speed, Energy, Heat, Insulation, Thermoelectric or Range upgrade card, placed in a machine's (or Heat Cell's) Upgrades tab.
public class UpgradeItem extends Item {
    private final UpgradeType type;

    public UpgradeItem(UpgradeType type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    public UpgradeType getType() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.upgrade." + type.getSerializedName()).withStyle(ChatFormatting.GRAY));
        String max = type == UpgradeType.INSULATION ? "tooltip.arcforge.upgrade.insulation.max" : "tooltip.arcforge.upgrade.max";
        builder.accept(Component.translatable(max).withStyle(ChatFormatting.DARK_GRAY));
    }
}
