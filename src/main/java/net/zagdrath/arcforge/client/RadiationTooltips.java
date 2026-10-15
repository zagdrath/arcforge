/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationShielding;
import net.zagdrath.arcforge.nuclear.RadiationText;

// Tooltip lines from the radiation data maps, for any item: how radioactive the stack is (with what it holds) and, for
// a block, what one block of it lets through.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class RadiationTooltips {
    private RadiationTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        double rate = RadiationField.rate(stack);
        if (rate > 0.0) {
            tooltip.add(Component.translatable("tooltip.arcforge.radioactive", RadiationText.rate(rate)).withStyle(ChatFormatting.YELLOW));
        }
        if (stack.getItem() instanceof BlockItem block) {
            RadiationShielding shielding = RadiationField.shielding(block.getBlock());
            if (shielding != null) {
                tooltip.add(Component.translatable("tooltip.arcforge.radiation_shielding",
                        String.format(Locale.ROOT, "%.0f%%", shielding.transmission() * 100.0)).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
