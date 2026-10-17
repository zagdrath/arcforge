/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
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
import net.zagdrath.arcforge.blockentity.nuclear.DecontaminationShowerBlockEntity;
import net.zagdrath.arcforge.registry.ModItems;

// A Decontamination Shower (see DecontaminationShowerBlockEntity): a shower head and its filter housing, hung from the
// ceiling. Using it with a HEPA Filter puts the filter in (giving back the old one); with an empty hand while sneaking
// takes the filter out; a Water bucket fills it.
public class DecontaminationShowerBlock extends NuclearEntityBlock {
    public DecontaminationShowerBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        super(type, Block.box(2, 6, 2, 14, 16, 14), properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.HEPA_FILTER.get()) && level.getBlockEntity(pos) instanceof DecontaminationShowerBlockEntity shower) {
            if (!level.isClientSide()) {
                ItemStack old = shower.swapFilter(stack);
                stack.consume(1, player);
                if (!old.isEmpty() && !player.getInventory().add(old)) {
                    player.drop(old, false, Prediction.SERVER_ONLY);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof DecontaminationShowerBlockEntity shower) || shower.getFilter().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = shower.takeFilter();
            if (!player.getInventory().add(out)) {
                player.drop(out, false, Prediction.SERVER_ONLY);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
