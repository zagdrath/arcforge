/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorControllerBlock;

// Where a steam array casing's face shows window: each 8x8 quadrant of the face does when formed Pressure
// Glass touches that corner (the vertical, horizontal or diagonal neighbour in the face's plane), so every
// window reaches half a block into the casings round it. Shared by ConnectedModel, which draws the window
// quadrants, and the arrays' renderers, which leave the lining out behind them. The Thermal Evaporator Array uses the
// same rule (see windowedEvaporator), so its windows reach half a block into its corner casings, its cap and its base.
public final class WindowQuadrants {
    private WindowQuadrants() {}

    // top / right: the quadrant, in the face's texture orientation.
    public static boolean windowed(BlockGetter level, BlockPos pos, Direction face, boolean top, boolean right) {
        Direction vertical = top ? up(face) : up(face).getOpposite();
        Direction horizontal = right ? right(face) : right(face).getOpposite();
        return isGlass(level, pos.relative(vertical)) || isGlass(level, pos.relative(horizontal))
                || isGlass(level, pos.relative(vertical).relative(horizontal));
    }

    // The Thermal Evaporator Array's casings: as windowed, except on the face the controller looks out of, where the
    // bottom layer (the controller and the casings either side of it) stays solid, so the window stops at the top of the
    // base there and never cuts into the controller's display.
    public static boolean windowedEvaporator(BlockGetter level, BlockPos pos, Direction face, boolean top, boolean right) {
        return !besideEvaporatorController(level, pos, face) && windowed(level, pos, face, top, right);
    }

    private static boolean besideEvaporatorController(BlockGetter level, BlockPos pos, Direction face) {
        if (!face.getAxis().isHorizontal()) {
            return false;
        }
        Direction across = right(face);
        for (BlockPos candidate : new BlockPos[] { pos, pos.relative(across), pos.relative(across.getOpposite()) }) {
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof ThermalEvaporatorControllerBlock
                    && state.getValue(ThermalEvaporatorControllerBlock.FACING) == face) {
                return true;
            }
        }
        return false;
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
