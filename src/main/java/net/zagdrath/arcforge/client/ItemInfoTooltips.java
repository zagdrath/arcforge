/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.zagdrath.arcforge.Arcforge;

// A short description of what each Arcforge item does, under its name: "Hold [Shift] for info", and the
// description while Shift is held. Descriptions live in the lang file as tooltip.arcforge.info.<item>;
// tiered items (conduits, tanks, cells) share one without the tier, e.g. tooltip.arcforge.info.energy_conduit.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ItemInfoTooltips {
    private static final String PREFIX = "tooltip.arcforge.info.";
    private static final String[] TIERS = { "wrought_", "tempered_", "hardened_", "arcforged_" };
    // Descriptions wrap at this width (the vanilla tooltip font is 9 px tall; ~40 characters a line).
    private static final int WRAP_WIDTH = 200;

    private ItemInfoTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!Arcforge.MODID.equals(id.getNamespace())) {
            return;
        }
        String key = descriptionKey(id.getPath());
        if (key == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        // Right under the name, before the item's own lines.
        int at = Math.min(1, tooltip.size());
        if (!Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(at, Component.translatable("tooltip.arcforge.hold_shift",
                    Component.translatable("tooltip.arcforge.shift").withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter()
                .splitLines(Component.translatable(key), WRAP_WIDTH, Style.EMPTY);
        for (int i = 0; i < lines.size(); i++) {
            tooltip.add(at + i, Component.literal(lines.get(i).getString()).withStyle(ChatFormatting.GRAY));
        }
    }

    // The item's description key, or the shared one for its tier, or null if it has none.
    public static @Nullable String descriptionKey(String path) {
        Language language = Language.getInstance();
        if (language.has(PREFIX + path)) {
            return PREFIX + path;
        }
        for (String tier : TIERS) {
            if (path.startsWith(tier) && language.has(PREFIX + path.substring(tier.length()))) {
                return PREFIX + path.substring(tier.length());
            }
        }
        return null;
    }
}
