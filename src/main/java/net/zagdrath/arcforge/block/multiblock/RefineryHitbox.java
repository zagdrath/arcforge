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
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.RefineryLayout;

// The shapes of a formed Distillation Array's blocks: the parts of its refinery tower in each (RefineryLayout.solids,
// clipped to the block by ModelFrame), turned to face its front, and on a block holding ports each port's plate and
// collar (as the Steam Turbine Array's, see TurbineHitbox). Kept for each shape of column (size and front). Only asked
// of a real level; anything else (a chunk being meshed, a pathfinding region, world generation) gets the full cube.
final class RefineryHitbox {
    private static final int CACHE_LIMIT = 32;
    // How long a block's answer (which column it's part of) is kept, in ticks.
    private static final long KEEP_TICKS = 20;
    private static final long NO_COLUMN = Long.MIN_VALUE;

    private record Key(int width, int height, Direction front) {}

    private static final Map<Key, VoxelShape[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<Key, Map<Long, VoxelShape>> PORTED = new ConcurrentHashMap<>();
    // Per level, each block's controller position (or NO_COLUMN) and the game time it was found at.
    private static final Map<Level, Map<Long, long[]>> CONTROLLERS = new WeakHashMap<>();

    private RefineryHitbox() {}

    // The shape of the formed part at pos, or null when it can't tell (its controller isn't loaded, say): then the
    // block keeps a full cube.
    static @Nullable VoxelShape shape(BlockGetter getter, BlockPos pos, BlockState state) {
        if (!(getter instanceof Level level)) {
            return null;
        }
        long found = controllerOf(level, pos);
        if (found == NO_COLUMN || !(level.getBlockEntity(BlockPos.of(found)) instanceof DistillationArrayBlockEntity controller)) {
            return null;
        }
        DistillationStructure.Column column = controller.getColumn();
        if (column == null || !column.contains(pos)) {
            return null;
        }
        Direction front = controller.getFacing();
        Key key = new Key(column.width(), column.height(), front);
        VoxelShape[] shapes = CACHE.get(key);
        if (shapes == null) {
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
                PORTED.clear();
            }
            shapes = CACHE.computeIfAbsent(key, k -> frame(k).shapes(new RefineryLayout(k.width(), k.height()).solids()));
        }
        ModelFrame frame = frame(key);
        int index = frame.index(pos.getX() - column.min().getX(), pos.getY() - column.min().getY(), pos.getZ() - column.min().getZ());
        VoxelShape shape = index >= 0 && index < shapes.length && shapes[index] != null ? shapes[index] : Shapes.block();
        int ports = state.getBlock() instanceof PortHolder holder && holder.holdsPorts(state) ? portMask(level, pos, column) : 0;
        if (ports == 0) {
            return shape;
        }
        VoxelShape base = shape;
        return PORTED.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).computeIfAbsent((long) index << 6 | ports,
                k -> TurbineHitbox.withPorts(base, ports));
    }

    private static ModelFrame frame(Key key) {
        return ModelFrame.toward(key.front().getOpposite(), key.width(), key.height(), key.width());
    }

    // The controller of the column the block at pos is part of, or NO_COLUMN, kept a second (the search round a block
    // covers a few hundred positions).
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
        DistillationArrayBlockEntity controller = DistillationStructure.findController(level, pos);
        long found = controller != null ? controller.getBlockPos().asLong() : NO_COLUMN;
        if (known.size() > 4096) {
            known.clear();
        }
        known.put(pos.asLong(), new long[] { found, time });
        return found;
    }

    // The faces of the block at pos holding ports and facing out of the column, a bit per Direction.
    private static int portMask(Level level, BlockPos pos, DistillationStructure.Column column) {
        PortFaces faces = MultiblockPorts.faces(level, pos);
        if (faces.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (faces.get(face) != SideMode.NONE && !column.contains(pos.relative(face))) {
                mask |= 1 << face.ordinal();
            }
        }
        return mask;
    }
}
