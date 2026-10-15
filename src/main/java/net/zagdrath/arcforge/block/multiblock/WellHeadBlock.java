/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;

// The Well Head: what an Oil Derrick leaves capping the hole when it strikes oil (a flanged casing head, a valve tree
// with a stuffing box on top, and a side outlet with a valve and handwheel). A Pumpjack is built round it. It can't be
// carried off: broken, it's gone, and so is the hole. Drilled into a deposit's gas cap, it's a gas well (GAS): a Gas
// Wellhead goes on top of it instead of a Pumpjack round it.
public class WellHeadBlock extends Block {
    public static final BooleanProperty GAS = BooleanProperty.create("gas");

    // Its shape follows the model (tools/WellHeadModelGen.java): the casing head, the valve tree and stuffing box, the
    // side outlet with its valve, and the handwheel.
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(4, 2, 4, 12, 6, 12), Block.box(3, 6, 3, 13, 7, 13),
            Block.box(5, 7, 5, 11, 16, 11), Block.box(11, 7, 6, 15, 12, 10), Block.box(11, 12, 6, 16, 15, 11));

    public WellHeadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GAS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GAS);
    }

    public static boolean isGasWell(BlockState state) {
        return state.getBlock() instanceof WellHeadBlock && state.getValue(GAS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
}
