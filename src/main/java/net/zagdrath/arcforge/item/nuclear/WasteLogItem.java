/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.nuclear;

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.nuclear.WasteLog;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Vitrified Waste Log (see WasteLog): its tooltip shows its radioactivity and warmth now, both falling with time.
public class WasteLogItem extends Item {
    public WasteLogItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        WasteLog log = stack.get(ModDataComponents.WASTE_LOG.get());
        if (log == null || context.level() == null) {
            return;
        }
        long now = context.level().getGameTime();
        builder.accept(Component.translatable("tooltip.arcforge.waste_log.activity", String.format(Locale.ROOT, "%,.1f", log.activityAt(now)))
                .withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("tooltip.arcforge.waste_log.heat", String.format(Locale.ROOT, "%.2f", log.heatAt(now)))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.waste_log.age", String.format(Locale.ROOT, "%.1f", (now - log.made()) / 24_000.0))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
