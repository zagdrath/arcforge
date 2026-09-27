/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

// Shared player interactions for machine blocks.
public final class MachineInteractions {
    private MachineInteractions() {}

    public static boolean isHoldingFluidContainer(Player player, InteractionHand hand) {
        return !player.getItemInHand(hand).isEmpty()
                && ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    // Call from Block#useItemOn. Moves fluid between the held container and the machine.
    // Returns null when the click should fall through to the normal behaviour (opening the GUI).
    public static @Nullable InteractionResult useFluidContainer(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof FluidInteractable machine) || !isHoldingFluidContainer(player, hand)) {
            return null;
        }
        // The server decides; the client just swings and waits, so it never places the fluid in the world.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        var handler = machine.getInteractionFluidHandler();
        if (handler != null && FluidUtil.interactWithFluidHandler(player, hand, pos, handler, null)) {
            return InteractionResult.SUCCESS;
        }
        // A sneak-click aimed at the machine on purpose: don't open the GUI or spill the fluid next to it.
        return player.isSecondaryUseActive() ? InteractionResult.CONSUME : null;
    }
}
