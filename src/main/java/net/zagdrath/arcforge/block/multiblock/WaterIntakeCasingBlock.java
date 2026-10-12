/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.WaterIntakeBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Industrial Water Intake; 27 in a 3x3x3 cube, standing in water, form the machine (see CubeCasingBlock
// and WaterIntakeBlockEntity).
public class WaterIntakeCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<WaterIntakeBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(WaterIntakeCasingBlock.class, WaterIntakeBlockEntity.class);

    public WaterIntakeCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.WATER_INTAKE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterIntakeBlockEntity(pos, state);
    }

    // Bubbles rising along the bottom edges while it pumps.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT) || random.nextInt(2) != 0) {
            return;
        }
        double x = pos.getX() - 1 + random.nextDouble() * 3;
        double z = pos.getZ() - 1 + random.nextDouble() * 3;
        double y = pos.getY() - 1.6 + random.nextDouble() * 0.4;
        level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.08, 0.0);
    }
}
