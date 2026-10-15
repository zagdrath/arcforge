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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.nuclear.RadiationDose;
import net.zagdrath.arcforge.nuclear.RadiationText;

// The Dosimeter: held, it shows the player's accumulated dose on the action bar (see RadiationDose); carried anywhere
// on them, it warns them in chat each time the dose passes a threshold on the way up (1,000, 2,000, 4,000 and 8,000 mSv
// by default). With a Geiger Counter in the other hand the counter shows both.
public class DosimeterItem extends Item {
    private static final int SHOW_INTERVAL = 10;

    public DosimeterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player) || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)
                || player.tickCount % SHOW_INTERVAL != 0 || GeigerCounterItem.isHeld(player)) {
            return;
        }
        double dose = RadiationDose.exposure(player).dose();
        int reached = RadiationDose.level(dose);
        player.sendOverlayMessage(Component.translatable("message.arcforge.dosimeter.reading", RadiationText.dose(dose))
                .withStyle(reached == 0 ? ChatFormatting.GREEN : reached <= 2 ? ChatFormatting.GOLD : ChatFormatting.RED));
    }
}
