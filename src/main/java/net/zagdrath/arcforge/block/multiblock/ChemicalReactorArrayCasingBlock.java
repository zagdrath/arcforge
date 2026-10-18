/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Chemical Reactor Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock). Formed, every
// casing is hidden and the centre's renderer draws the model (ChemicalReactorArrayRenderer).
public class ChemicalReactorArrayCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<ChemicalReactorArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(ChemicalReactorArrayCasingBlock.class, ChemicalReactorArrayBlockEntity.class);

    public ChemicalReactorArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.CHEMICAL_REACTOR_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalReactorArrayBlockEntity(pos, state);
    }

    // A wisp of vapour over a vessel's head now and then while it reacts.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextInt(3) - 1) * 0.94;
        double x = pos.getX() + 0.5 + facing.getClockWise().getStepX() * along;
        double z = pos.getZ() + 0.5 + facing.getClockWise().getStepZ() * along;
        level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.2, pos.getY() + 1.55, z + (random.nextDouble() - 0.5) * 0.2,
                0.0, 0.02, 0.0);
    }
}
