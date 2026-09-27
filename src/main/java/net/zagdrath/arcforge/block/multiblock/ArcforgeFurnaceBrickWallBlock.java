/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A vanilla wall of Arcforge Furnace bricks. On the furnace's corners it connects to the bricks on both sides.
public class ArcforgeFurnaceBrickWallBlock extends WallBlock implements MultiblockPart {
    public ArcforgeFurnaceBrickWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // Walls change state as their neighbours change; only a new wall matters to the furnace.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            ArcforgeFurnaceStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ArcforgeFurnaceStructure.notifyChanged(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return ArcforgeFurnaceStructure.useOnPart(level, pos, player);
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return ArcforgeFurnaceStructure.findFormedPort(level, pos);
    }

    @Override
    public InteractionResult useWrench(UseOnContext context) {
        return ArcforgeFurnaceStructure.wrenchPart(context);
    }
}
