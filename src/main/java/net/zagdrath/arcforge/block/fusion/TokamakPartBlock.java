/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fusion;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.multiblock.VesselPart;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.PortHolder;

// A block of the Fusion Reactor (see TokamakStructure): the Cryostat Casing and Vessel Port (its wall and lids, which can
// hold ports), and the coils, wall, divertor and blanket inside. Placing, breaking or wrenching one has nearby reactors
// look again; formed, the controller's renderer draws the reactor and these draw nothing but their ports.
public class TokamakPartBlock extends Block implements MultiblockPart, PortHolder {
    // The same property the vessels use, so a pane of Reactor Glass formed into either is seen as taken.
    public static final BooleanProperty FORMED = VesselPart.FORMED;

    private final boolean ports;

    public TokamakPartBlock(boolean ports, BlockBehaviour.Properties properties) {
        super(properties);
        this.ports = ports;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    public boolean holdsPorts() {
        return ports;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return TokamakStructure.findController(level, pos);
    }

    @Override
    public InteractionResult useWrench(UseOnContext context) {
        return TokamakStructure.wrenchPart(context);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            TokamakStructure.notifyChanged(level, pos);
        }
    }

    // Something placed against a formed wall (where only the reactor's own blocks may go) has it looked at again.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (state.getValue(FORMED) && ports) {
            TokamakStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        TokamakStructure.notifyChanged(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return state.getValue(FORMED) ? TokamakStructure.useOnPart(level, pos, player) : InteractionResult.PASS;
    }

    // Formed, the reactor is drawn by the controller's renderer: its blocks hide nothing behind them and let light through.
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
