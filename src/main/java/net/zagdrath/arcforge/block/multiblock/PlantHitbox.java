/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.OilfieldController;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.ShapedPlant;

// The shapes of a formed Oil Derrick's, Pumpjack's, Cracking Unit's, Steam Cracker's or Diesel Generator's blocks (see
// ShapedPlant): the still parts of its model in each (clipped to the block by ModelFrame), turned the way it faces.
// Only asked of a real level (see RefineryHitbox); a bottom block no part reaches keeps a low floor plate, so it can
// still be clicked, and one higher up has no shape (break another to take the structure down).
public final class PlantHitbox {
    private static final long KEEP_TICKS = 20;
    private static final long NONE = Long.MIN_VALUE;
    private static final VoxelShape FLOOR = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 2, 16);
    private static final Map<Level, Map<Long, long[]>> CONTROLLERS = new WeakHashMap<>();

    private PlantHitbox() {}

    static @Nullable VoxelShape shape(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) {
            return null;
        }
        long found = controllerOf(level, pos);
        if (found == NONE || !(level.getBlockEntity(BlockPos.of(found)) instanceof ShapedPlant plant)) {
            return null;
        }
        ModelFrame frame = plant.plantFrame();
        if (frame == null) {
            return null;
        }
        VoxelShape[] shapes = plant.plantShapes();
        BlockPos min = plant.getMinCorner();
        int dx = pos.getX() - min.getX(), dy = pos.getY() - min.getY(), dz = pos.getZ() - min.getZ();
        if (dx < 0 || dy < 0 || dz < 0 || dx >= frame.sizeX() || dy >= frame.height() || dz >= frame.sizeZ()) {
            return Shapes.block();
        }
        VoxelShape shape = shapes[frame.index(dx, dy, dz)];
        return shape != null ? shape : dy == 0 ? FLOOR : Shapes.empty();
    }

    // The formed structure the block at pos is part of, or null (kept a second, as for its shape).
    public static @Nullable ShapedPlant plant(Level level, BlockPos pos) {
        long found = controllerOf(level, pos);
        return found != NONE && level.getBlockEntity(BlockPos.of(found)) instanceof ShapedPlant plant ? plant : null;
    }

    // The controller of the structure the block at pos is part of, or NONE, kept a second.
    private static long controllerOf(Level level, BlockPos pos) {
        long time = level.getGameTime();
        Map<Long, long[]> known;
        synchronized (CONTROLLERS) {
            known = CONTROLLERS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        }
        long[] entry = known.get(pos.asLong());
        if (entry != null && Math.abs(time - entry[1]) < KEEP_TICKS) {
            return entry[0];
        }
        OilfieldController controller = OilfieldStructures.findController(level, pos);
        long found = controller instanceof ShapedPlant ? controller.getBlockPos().asLong() : NONE;
        if (known.size() > 4096) {
            known.clear();
        }
        known.put(pos.asLong(), new long[] { found, time });
        return found;
    }
}
