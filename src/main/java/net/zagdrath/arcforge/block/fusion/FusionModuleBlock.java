/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fusion;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.nuclear.NuclearEntityBlock;
import net.zagdrath.arcforge.fusion.TokamakStructure;

// A Fusion Reactor's port module (see FusionModuleBlockEntity): set against a Vessel Port in the cryostat wall, facing it
// (FACING, toward the reactor; placed against a block it faces that block). Placing or breaking one has the reactor look
// again at its modules.
public class FusionModuleBlock extends NuclearEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public enum Kind implements StringRepresentable {
        NEUTRAL_BEAM_INJECTOR("neutral_beam_injector"),
        GYROTRON("gyrotron"),
        PELLET_INJECTOR("pellet_injector"),
        GAS_PUFF_VALVE("gas_puff_valve"),
        CRYOPUMP("cryopump"),
        DIAGNOSTICS("diagnostics_port"),
        DIRECT_CONVERTER("direct_energy_converter");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        // Whether it heats the plasma.
        public boolean heats() {
            return this == NEUTRAL_BEAM_INJECTOR || this == GYROTRON;
        }
    }

    private final Kind kind;

    public FusionModuleBlock(Kind kind, Supplier<? extends BlockEntityType<?>> type, VoxelShape shape, BlockBehaviour.Properties properties) {
        super(type, shape, properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            TokamakStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        TokamakStructure.notifyChanged(level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
