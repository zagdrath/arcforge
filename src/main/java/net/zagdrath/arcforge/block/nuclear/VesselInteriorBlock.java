/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.zagdrath.arcforge.block.multiblock.VesselPart;
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A block that goes inside a vessel (see VesselStructure): the Fission Reactor's Fuel Channels, Control Rod Channels,
// Graphite Moderator and Steel Reflector, the Steam Generator's Tubes, the Heavy Water Tower's Exchange Trays. Placing
// or breaking one has the vessel round it looked at again (a reactor redraws its core). FORMED (VesselPart's) is set
// while it's inside a formed vessel, and it then draws nothing: the controller's renderer draws the vessel round it.
public class VesselInteriorBlock extends Block {
    private final Supplier<VesselStructure> structure;

    public VesselInteriorBlock(Supplier<VesselStructure> structure, BlockBehaviour.Properties properties) {
        super(properties);
        this.structure = structure;
        registerDefaultState(defaultBlockState().setValue(VesselPart.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VesselPart.FORMED);
    }

    public VesselStructure structure() {
        return structure.get();
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
}
