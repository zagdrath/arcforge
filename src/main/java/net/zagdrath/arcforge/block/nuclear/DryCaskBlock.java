/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.nuclear.DryCaskBlockEntity;
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;

// A Dry Cask (see DryCaskBlockEntity): a squat concrete overpack. Using it with a cool enough fuel rod loads it; with
// an empty hand while sneaking takes the last rod out, and with an empty hand shows how many it holds.
public class DryCaskBlock extends NuclearEntityBlock {
    public DryCaskBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        super(type, Block.box(1, 0, 1, 15, 16, 15), properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!FuelRodItem.isFuelRod(stack) || !(level.getBlockEntity(pos) instanceof DryCaskBlockEntity cask)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            if (!DryCaskBlockEntity.accepts(stack, level.getGameTime())) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.dry_cask.too_hot"));
            } else if (cask.insert(stack, level.getGameTime())) {
                stack.consume(1, player);
            } else {
                player.sendOverlayMessage(Component.translatable("message.arcforge.dry_cask.full"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof DryCaskBlockEntity cask)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown() && cask.count() > 0) {
                ItemStack out = cask.extract();
                if (!player.getInventory().add(out)) {
                    player.drop(out, false, Prediction.SERVER_ONLY);
                }
            } else {
                player.sendOverlayMessage(Component.translatable("message.arcforge.dry_cask.holds", cask.count(), DryCaskBlockEntity.SLOTS));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
