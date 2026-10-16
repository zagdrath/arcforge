/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A vessel's window (Reactor Glass: leaded, so it shields like a wall; see the radiation_shielding data map). It never
// does IO; clicking a formed pane opens the vessel. The Fission Reactor shows its Cherenkov glow through it while it
// runs.
public class VesselGlassBlock extends TransparentBlock implements VesselPart {
    private final Supplier<VesselStructure> structure;

    public VesselGlassBlock(Supplier<VesselStructure> structure, BlockBehaviour.Properties properties) {
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

    // A whole block to whatever hangs on it (levers, buttons), so the vessel keeps its controls.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
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
        return state.getValue(FORMED) ? structure().useOnPart(level, pos, player) : InteractionResult.PASS;
    }
}
