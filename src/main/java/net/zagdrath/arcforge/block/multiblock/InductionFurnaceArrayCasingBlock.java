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
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Induction Furnace Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock).
public class InductionFurnaceArrayCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<InductionFurnaceArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(InductionFurnaceArrayCasingBlock.class, InductionFurnaceArrayBlockEntity.class);

    public InductionFurnaceArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.INDUCTION_FURNACE_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InductionFurnaceArrayBlockEntity(pos, state);
    }

    // Sparks at the three lane pods and fumes from the stack while smelting.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextInt(3) - 1) * 0.9 + (random.nextDouble() - 0.5) * 0.3;
        double x = pos.getX() + 0.5 + facing.getStepX() * 1.4 + facing.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.1 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 1.4 + facing.getClockWise().getStepZ() * along;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(4) == 0) {
            double stackX = pos.getX() + 0.5 - facing.getStepX() * 0.6;
            double stackZ = pos.getZ() + 0.5 - facing.getStepZ() * 0.6;
            level.addParticle(ParticleTypes.SMOKE, stackX + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 2.1,
                    stackZ + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.05, 0.0);
        }
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.1F, false);
        }
    }
}
