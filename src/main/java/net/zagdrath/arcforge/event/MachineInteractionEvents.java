/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.util.TriState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;

// Runs on both client and server.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class MachineInteractionEvents {
    private MachineInteractionEvents() {}

    // Vanilla skips a block's use handler while sneaking with an item in hand, so a sneak right-click
    // with a bucket would pour the fluid into the world. Force the machine to handle it instead.
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (player.isSecondaryUseActive()
                && event.getLevel().getBlockEntity(event.getPos()) instanceof FluidInteractable
                && MachineInteractions.isHoldingFluidContainer(player, event.getHand())) {
            event.setUseBlock(TriState.TRUE);
        }
    }
}
