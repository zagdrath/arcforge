/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Arc Crushing Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock).
public class ArcCrushingArrayCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<ArcCrushingArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(ArcCrushingArrayCasingBlock.class, ArcCrushingArrayBlockEntity.class);

    public ArcCrushingArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcCrushingArrayBlockEntity(pos, state);
    }

    // Arc sparks at the rollers while crushing.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextDouble() - 0.5) * 1.2;
        double x = pos.getX() + 0.5 + facing.getStepX() * 1.4 + facing.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 1.4 + facing.getClockWise().getStepZ() * along;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, 0.5F, false);
        }
    }
}
