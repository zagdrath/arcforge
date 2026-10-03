/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;

// A Power Regulator: inside a Battery Array, it adds its tier's transfer rate (see ArcforgeConfig.powerRegulatorTransfer)
// to what the array takes in and gives out each tick. Its status lamp lights while it's part of a formed array.
public class PowerRegulatorBlock extends Block implements BatteryArrayPart {
    private final ConduitTier tier;

    public PowerRegulatorBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties.lightLevel(state -> state.getValue(FORMED) ? 3 : 0));
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    public ConduitTier getTier() {
        return tier;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BatteryArrayStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BatteryArrayStructure.notifyChanged(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return BatteryArrayStructure.useOnPart(level, pos, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }
}
