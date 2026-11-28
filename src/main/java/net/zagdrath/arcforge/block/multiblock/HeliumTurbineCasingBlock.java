/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.HeliumGasTurbineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.HeliumTurbineLayout;

// A casing of the Helium Gas Turbine (Arcforged tier): a tube 15 to 31 blocks long along either horizontal axis, its
// cross-section 5x5, all casings (it has no windows: Pressure Glass never forms part of it, see
// ShellStructure.casingsOnly). Formed, it's drawn as one closed-cycle turbine-generator set (see HeliumTurbineLayout),
// and each casing collides as the pieces of the model in it (see HeliumTurbineHitbox), so players walk its deck between
// the crossover ducts; it's picked as a full block, so the whole turbine is one target with one outline (see
// TurbineOutline). Loose, a full block.
public class HeliumTurbineCasingBlock extends ShellCasingBlock {
    public static final ShellStructure STRUCTURE = ShellStructure.casingsOnly(block -> block instanceof HeliumTurbineCasingBlock,
            EnumSet.of(Direction.Axis.X, Direction.Axis.Z), (axis, x, y, z) -> {
                int width = axis == Direction.Axis.X ? z : x, length = axis == Direction.Axis.X ? x : z;
                return width == HeliumTurbineLayout.WIDTH && y == HeliumTurbineLayout.HEIGHT && length >= HeliumTurbineLayout.MIN_LENGTH
                        && length <= HeliumTurbineLayout.MAX_LENGTH;
            });

    public HeliumTurbineCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(FORMED) ? HeliumTurbineHitbox.casing(level, pos, state) : null;
        return shape != null ? shape : super.getCollisionShape(state, level, pos, context);
    }

    // Still a whole block to whatever hangs on it (levers, buttons, torches, redstone), so the turbine keeps its controls.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? Shapes.block() : super.getBlockSupportShape(state, level, pos);
    }

    @Override
    public ShellStructure structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends ShellMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.HELIUM_GAS_TURBINE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeliumGasTurbineBlockEntity(pos, state);
    }

    // Clients tick the formed master too, to keep its running sound going (see HeliumGasTurbineBlockEntity.clientTick).
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            if (!state.getValue(FORMED) || type != blockEntityType()) {
                return null;
            }
            BlockEntityTicker<HeliumGasTurbineBlockEntity> ticker = (innerLevel, pos, blockState, turbine) -> turbine.clientTick();
            return (BlockEntityTicker<T>) (BlockEntityTicker<?>) ticker;
        }
        return super.getTicker(level, state, type);
    }
}
