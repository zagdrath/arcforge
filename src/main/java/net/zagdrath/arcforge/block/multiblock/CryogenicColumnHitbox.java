/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.multiblock.FusionPlants;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.CryogenicColumnMesh;

// The shapes of a formed Cryogenic Distillation Column's casing blocks: the parts of its model a player stands on and
// bumps into (CryogenicColumnMesh.solids: the plinth, the column and condenser, the decks and their railings), so the
// well in front of the ladder is open to step into; and the blocks the ladder runs through are climbable. Only asked
// of a real level (a chunk being meshed gets the full block); a block no part reaches has no shape.
public final class CryogenicColumnHitbox {
    // Each block's shape by the way the column faces and its height in blocks.
    private static final Map<Long, VoxelShape[]> SHAPES = new ConcurrentHashMap<>();

    private CryogenicColumnHitbox() {}

    private static VesselStructure.@Nullable Box box(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) {
            return null;
        }
        VesselControllerBlockEntity controller = FusionPlants.CRYOGENIC_COLUMN.findController(level, pos);
        return controller != null ? controller.getBox() : null;
    }

    // The block's shape on a formed column, or null when it isn't one's (use the block's own).
    static @Nullable VoxelShape shape(BlockGetter getter, BlockPos pos) {
        VesselStructure.Box box = box(getter, pos);
        if (box == null) {
            return null;
        }
        ModelFrame frame = box.frame();
        int height = frame.height();
        Direction front = box.front();
        VoxelShape[] shapes = SHAPES.computeIfAbsent((long) height << 8 | front.ordinal(),
                key -> frame.shapes(CryogenicColumnMesh.solids(height * 16.0F)));
        BlockPos min = box.min();
        int dx = pos.getX() - min.getX(), dy = pos.getY() - min.getY(), dz = pos.getZ() - min.getZ();
        if (dx < 0 || dy < 0 || dz < 0 || dx >= frame.sizeX() || dy >= frame.height() || dz >= frame.sizeZ()) {
            return null;
        }
        VoxelShape shape = shapes[frame.index(dx, dy, dz)];
        return shape != null ? shape : Shapes.empty();
    }

    // Whether the block at pos is on a formed column's ladder.
    static boolean isLadder(BlockGetter getter, BlockPos pos) {
        VesselStructure.Box box = box(getter, pos);
        if (box == null) {
            return false;
        }
        ModelFrame frame = box.frame();
        BlockPos min = box.min();
        int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
        return CryogenicColumnMesh.isLadderBlock(block[0], block[1], block[2], frame.height() * 16.0F);
    }
}
