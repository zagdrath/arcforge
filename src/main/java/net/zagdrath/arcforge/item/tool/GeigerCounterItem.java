/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.nuclear.RadiationDose;
import net.zagdrath.arcforge.nuclear.RadiationText;
import net.zagdrath.arcforge.registry.ModItems;

// The Geiger Counter: held in either hand it shows the dose rate where the player stands on the action bar (mSv/h,
// see RadiationField) and clicks faster the higher it is (the clicks are played on the client from the synced rate,
// see GeigerClient). With a Dosimeter in the other hand it shows their dose too.
public class GeigerCounterItem extends Item {
    private static final int SHOW_INTERVAL = 10;

    public GeigerCounterItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof GeigerCounterItem || player.getOffhandItem().getItem() instanceof GeigerCounterItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player) || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)
                || player.tickCount % SHOW_INTERVAL != 0) {
            return;
        }
        // Held in both hands, show it once.
        if (slot == EquipmentSlot.OFFHAND && player.getMainHandItem().getItem() instanceof GeigerCounterItem) {
            return;
        }
        RadiationDose.Exposure exposure = RadiationDose.exposure(player);
        Component reading = Component.translatable("message.arcforge.geiger_counter.reading", RadiationText.rate(exposure.rate()))
                .withStyle(color(exposure.rate()));
        if (player.getMainHandItem().is(ModItems.DOSIMETER.get()) || player.getOffhandItem().is(ModItems.DOSIMETER.get())) {
            reading = Component.translatable("message.arcforge.geiger_counter.with_dose", reading,
                    Component.translatable("message.arcforge.dosimeter.reading", RadiationText.dose(exposure.dose())).withStyle(ChatFormatting.GRAY));
        }
        player.sendOverlayMessage(reading);
    }

    // Green under 0.1 mSv/h, yellow under 1, gold under 10, red above.
    public static ChatFormatting color(double rate) {
        return rate < 0.1 ? ChatFormatting.GREEN : rate < 1.0 ? ChatFormatting.YELLOW : rate < 10.0 ? ChatFormatting.GOLD : ChatFormatting.RED;
    }
}
