/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming.greenhouse;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.farming.PlantingBedBlockEntity;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;

// A Planting Bed, in the Greenhouse Array's floor: a steel planter that holds one soil and one crop, which the greenhouse
// grows, harvests and replants (the plant is drawn by PlantingBedRenderer, over the bed). Right-click it with a soil (dirt,
// Loam, sand, soul sand: the arcforge:cloche_soils), then a seed; sneak-right-click with an empty hand to take the seed
// back, then the soil.
public class PlantingBedBlock extends BaseEntityBlock implements GreenhousePart {
    private static final MapCodec<PlantingBedBlock> CODEC = simpleCodec(PlantingBedBlock::new);

    @Override
    protected MapCodec<PlantingBedBlock> codec() {
        return CODEC;
    }

    public PlantingBedBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantingBedBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlantingBedBlockEntity bed)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        boolean soil = bed.getSoil().isEmpty() && ClocheSoil.of(stack) != null;
        boolean seed = !soil && !bed.getSoil().isEmpty() && bed.getSeed().isEmpty() && ClochePlants.isSeed(level, stack);
        if (!soil && !seed) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            if (soil) {
                bed.setSoil(stack);
                level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                bed.setSeed(stack);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Sneaking, an empty hand takes the seed back, then the soil; otherwise it opens the greenhouse, if formed.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof PlantingBedBlockEntity bed
                && !(bed.getSeed().isEmpty() && bed.getSoil().isEmpty())) {
            if (!level.isClientSide()) {
                boolean seed = !bed.getSeed().isEmpty();
                ItemStack taken = (seed ? bed.getSeed() : bed.getSoil()).copy();
                if (seed) {
                    bed.setSeed(ItemStack.EMPTY);
                } else {
                    bed.setSoil(ItemStack.EMPTY);
                }
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return GreenhouseStructure.useOnPart(level, pos, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            GreenhouseStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        GreenhouseStructure.notifyChanged(level, pos);
    }
}
