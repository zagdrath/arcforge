/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.upgrade;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;

// The Chunk Loading Upgrade: right-click a Quantum Tunnel with it to fit it (one per tunnel), and the tunnel keeps its
// own chunk loaded as a Chunk Loader at radius 0 would (see QuantumTunnelBlockEntity). Sneak-right-click the tunnel with
// an empty hand to take it out; breaking the tunnel drops it.
public class ChunkLoadingUpgradeItem extends Item {
    public ChunkLoadingUpgradeItem(Item.Properties properties) {
        super(properties);
    }

    // Sneaking skips the block, so a sneak-click with the upgrade lands here; a plain click goes through
    // QuantumTunnelBlock.useItemOn.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof QuantumTunnelBlockEntity tunnel)) {
            return InteractionResult.PASS;
        }
        return install(context.getLevel(), context.getClickedPos(), tunnel, context.getPlayer(), context.getItemInHand());
    }

    // Fits the upgrade from stack into the tunnel, or says it already has one.
    public static InteractionResult install(Level level, BlockPos pos, QuantumTunnelBlockEntity tunnel, @Nullable Player player, ItemStack stack) {
        if (tunnel.hasChunkLoading()) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.chunk_loading_upgrade.already"));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        tunnel.setChunkLoading(true);
        if (player == null || !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.chunk_loading_upgrade.installed"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.chunk_loading_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
