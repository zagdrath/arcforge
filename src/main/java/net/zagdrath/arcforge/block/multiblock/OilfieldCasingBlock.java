/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PortHolder;

// A casing of an Oil Derrick (its drill floor) or a Pumpjack (its skid and Samson post). The Derrick Casing is a plain
// block, formed or not, and any on the outside can be one of the rig's ports; a formed Pumpjack Casing draws nothing
// (the pumpjack's renderer draws the unit) but its port plates, and its shape follows the model (see PumpjackLayout).
// The Derrick Frame (holdsPorts false) is the rig's steel lattice.
public class OilfieldCasingBlock extends Block implements OilfieldPart, PortHolder {
    private final boolean pumpjack, ports;

    public OilfieldCasingBlock(BlockBehaviour.Properties properties, boolean pumpjack, boolean ports) {
        super(properties);
        this.pumpjack = pumpjack;
        this.ports = ports;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public boolean holdsPorts() {
        return ports;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            OilfieldStructures.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        OilfieldStructures.notifyChanged(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return OilfieldStructures.useOnPart(level, pos, player);
    }

    // A formed Pumpjack Casing: the parts of the pumpjack in it (see PumpjackHitbox).
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = pumpjack && state.getValue(FORMED) ? PumpjackHitbox.shape(level, pos) : null;
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = pumpjack && state.getValue(FORMED) ? PumpjackHitbox.shape(level, pos) : null;
        return shape != null ? shape : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return pumpjack && state.getValue(FORMED) ? Shapes.block() : super.getBlockSupportShape(state, level, pos);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return pumpjack && state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return pumpjack && state.getValue(FORMED);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return pumpjack && state.getValue(FORMED) ? 0 : super.getLightDampening(state);
    }
}
