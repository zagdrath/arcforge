/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
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

    // Left-clicking a Vault's front takes a stack (sneaking: one) instead of mining it; mine it from any other
    // face. Cancelled on both sides so it neither breaks (not even in creative) nor shows mining progress.
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (event.getFace() == null || !VaultBlock.isFront(state, event.getFace())) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getEntity();
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof VaultBlockEntity vault)
                || vault.getAmount() == 0 || !vault.tryTake(player, event.getLevel().getGameTime())) {
            return;
        }
        ItemStack taken = vault.extract(player.isShiftKeyDown() ? 1 : vault.getTemplate().getMaxStackSize());
        if (!taken.isEmpty()) {
            player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
            event.getLevel().playSound(null, event.getPos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F,
                    1.0F + event.getLevel().getRandom().nextFloat() * 0.4F);
        }
    }
}
