/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A vessel's casing (the Reactor Casing, the Steam Generator Casing, the Heavy Water Tower Casing): any of them can be
// a port on its outer faces. Placing, breaking or something changing beside a formed one has the vessel looked at
// again.
public class VesselCasingBlock extends Block implements VesselPart, PortHolder {
    private final Supplier<VesselStructure> structure;

    public VesselCasingBlock(Supplier<VesselStructure> structure, BlockBehaviour.Properties properties) {
        super(properties);
        this.structure = structure;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    public VesselStructure structure() {
        return structure.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            structure().notifyChanged(level, pos);
        }
    }

    // Something placed inside a formed vessel (where only its own blocks may go) has it looked at again straight away.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (state.getValue(FORMED)) {
            structure().notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        structure().notifyChanged(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return structure().useOnPart(level, pos, player);
    }

    // Formed, the vessel is drawn by the controller's renderer: its walls hide nothing behind them and let light through.
    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return state.getValue(FORMED) ? 0 : super.getLightDampening(state);
    }
}
