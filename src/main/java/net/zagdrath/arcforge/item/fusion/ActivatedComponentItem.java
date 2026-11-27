/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.fusion;

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

// An Activated Component: a worn First Wall tile, Divertor cassette or blanket module a Remote Handling Arm took out of a
// fusion reactor, made radioactive by its neutrons. Its activity (the arcforge:waste_log component, as a waste log's)
// falls with time; store it, or send it to a Geological Repository.
public class ActivatedComponentItem extends Item {
    public ActivatedComponentItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack of(double activity, long now) {
        ItemStack stack = new ItemStack(net.zagdrath.arcforge.registry.ModItems.ACTIVATED_COMPONENT.get());
        stack.set(ModDataComponents.WASTE_LOG.get(), new WasteLog((float) Math.max(0.0, activity), now));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        WasteLog log = stack.get(ModDataComponents.WASTE_LOG.get());
        if (log == null || context.level() == null) {
            return;
        }
        long now = context.level().getGameTime();
        builder.accept(Component.translatable("tooltip.arcforge.activated_component.activity", String.format(Locale.ROOT, "%,.1f", log.activityAt(now)))
                .withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("tooltip.arcforge.activated_component.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
