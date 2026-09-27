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
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Metal Pressing Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock).
public class MetalPressingArrayCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<MetalPressingArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(MetalPressingArrayCasingBlock.class, MetalPressingArrayBlockEntity.class);

    public MetalPressingArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.METAL_PRESSING_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalPressingArrayBlockEntity(pos, state);
    }

    // Sparks behind the guard window and the thump of the rams while pressing.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextDouble() - 0.5) * 1.2;
        double x = pos.getX() + 0.5 + facing.getStepX() * 1.4 + facing.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.1 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 1.4 + facing.getClockWise().getStepZ() * along;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.15F, 0.6F, false);
        }
    }
}
