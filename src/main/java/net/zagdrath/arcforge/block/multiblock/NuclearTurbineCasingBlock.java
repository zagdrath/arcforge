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
import net.zagdrath.arcforge.blockentity.multiblock.NuclearSteamTurbineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.NuclearTurbineLayout;

// A casing of the Nuclear Steam Turbine (Hardened tier): a tube 21 to 45 blocks long along either horizontal axis, its
// cross-section 7x7, all casings (it has no windows). Formed, it's drawn as one turbine-generator set (see
// NuclearTurbineLayout), and each casing collides as the pieces of the model in it (see NuclearTurbineHitbox), so
// players walk its deck between the crossover arches; it's picked as a full block, so the whole turbine is one target
// with one outline (see TurbineOutline). Loose, a full block.
public class NuclearTurbineCasingBlock extends ShellCasingBlock {
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof NuclearTurbineCasingBlock,
            EnumSet.of(Direction.Axis.X, Direction.Axis.Z), (axis, x, y, z) -> {
                int width = axis == Direction.Axis.X ? z : x, length = axis == Direction.Axis.X ? x : z;
                return width == NuclearTurbineLayout.WIDTH && y == NuclearTurbineLayout.HEIGHT && length >= NuclearTurbineLayout.MIN_LENGTH
                        && length <= NuclearTurbineLayout.MAX_LENGTH;
            });

    public NuclearTurbineCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(FORMED) ? NuclearTurbineHitbox.casing(level, pos, state) : null;
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
        return ModBlockEntityTypes.NUCLEAR_STEAM_TURBINE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NuclearSteamTurbineBlockEntity(pos, state);
    }

    // Clients tick the formed master too, to keep its running sound going (see NuclearSteamTurbineBlockEntity.clientTick).
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            if (!state.getValue(FORMED) || type != blockEntityType()) {
                return null;
            }
            BlockEntityTicker<NuclearSteamTurbineBlockEntity> ticker = (innerLevel, pos, blockState, turbine) -> turbine.clientTick();
            return (BlockEntityTicker<T>) (BlockEntityTicker<?>) ticker;
        }
        return super.getTicker(level, state, type);
    }
}
