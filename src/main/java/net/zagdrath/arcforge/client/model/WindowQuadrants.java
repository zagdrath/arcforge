/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;

// Where a steam array casing's face shows window: each 8x8 quadrant of the face does when formed Pressure
// Glass touches that corner (the vertical, horizontal or diagonal neighbour in the face's plane), so every
// window reaches half a block into the casings round it. Shared by ConnectedModel, which draws the window
// quadrants, and the arrays' renderers, which leave the lining out behind them.
public final class WindowQuadrants {
    private WindowQuadrants() {}

    // top / right: the quadrant, in the face's texture orientation.
    public static boolean windowed(BlockGetter level, BlockPos pos, Direction face, boolean top, boolean right) {
        Direction vertical = top ? up(face) : up(face).getOpposite();
        Direction horizontal = right ? right(face) : right(face).getOpposite();
        return isGlass(level, pos.relative(vertical)) || isGlass(level, pos.relative(horizontal))
                || isGlass(level, pos.relative(vertical).relative(horizontal));
    }

    public static boolean isGlass(BlockGetter level, BlockPos pos) {
        return PressureGlassBlock.isFormed(level.getBlockState(pos));
    }

    // The face's texture-up and texture-right directions in the world.
    public static Direction up(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    public static Direction right(Direction face) {
        return switch (face) {
            case NORTH -> Direction.WEST;
            case SOUTH, UP, DOWN -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }
}
