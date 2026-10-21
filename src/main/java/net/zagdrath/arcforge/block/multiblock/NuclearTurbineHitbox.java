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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.NuclearSteamTurbineBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.NuclearTurbineLayout;
import net.zagdrath.arcforge.steam.NuclearTurbineShapes;
import net.zagdrath.arcforge.steam.TurbineLayout;

// The collision shapes of a formed Nuclear Steam Turbine's casings, as TurbineHitbox's for the Steam Turbine Array: the
// pieces of its model in each (see NuclearTurbineShapes), turned into the world's frame, so players walk its deck between
// the crossover arches and round the casings, reheaters and generator; a block holding ports gets each port's plate and
// collar too. Kept for each shape of turbine (length, front and axis). Only asked of a real level: anything else gets the
// full cube, as it can't be trusted to reach the turbine's master.
public final class NuclearTurbineHitbox {
    private static final int CACHE_LIMIT = 16;

    private record Key(int length, boolean frontHigh, boolean alongX) {}

    private static final class Built {
        final NuclearTurbineShapes frame;
        final @Nullable VoxelShape[] shapes;
        final Map<Long, VoxelShape> ported = new ConcurrentHashMap<>();
        // The model's parts in each deep core block, in the world's frame (see overlapsDeep); empty where it holds none.
        final @Nullable VoxelShape[] deep;

        Built(NuclearTurbineShapes frame, int count) {
            this.frame = frame;
            this.shapes = new VoxelShape[count];
            this.deep = new VoxelShape[count];
        }
    }

    private static final Map<Key, Built> CACHE = new ConcurrentHashMap<>();

    private NuclearTurbineHitbox() {}

    // The shape of the formed casing at pos, or null when it can't tell (its turbine isn't loaded, say): then the block
    // keeps a full cube.
    static @Nullable VoxelShape casing(BlockGetter getter, BlockPos pos, BlockState state) {
        if (!(getter instanceof Level level) || !(level.getBlockEntity(pos) instanceof NuclearSteamTurbineBlockEntity part)) {
            return null;
        }
        ShellStructure.Shell shell = part.getShell();
        if (shell == null || !shell.contains(pos) || shell.isCore(pos)
                || !(level.getBlockEntity(shell.min()) instanceof NuclearSteamTurbineBlockEntity master) || !shell.equals(master.getShell())) {
            return null;
        }
        boolean alongX = shell.axis() == Direction.Axis.X;
        Built built = built(shell, master);
        int u = TurbineLayout.blockU(shell, pos), y = shell.offset(pos, Direction.Axis.Y), x = TurbineLayout.blockX(shell, pos);
        int index = (u * NuclearTurbineLayout.HEIGHT + y) * NuclearTurbineLayout.WIDTH + x;
        VoxelShape shape = built.shapes[index];
        if (shape == null) {
            int[][] boxes = built.frame.at(u, y, x);
            shape = boxes != null ? toWorld(boxes, alongX) : Shapes.block();
            built.shapes[index] = shape;
        }
        int ports = state.getBlock() instanceof ShellCasingBlock casing && casing.holdsPorts(state) ? portMask(level, pos, shell) : 0;
        if (ports == 0) {
            return shape;
        }
        VoxelShape base = shape;
        return built.ported.computeIfAbsent((long) index << 6 | ports, k -> TurbineHitbox.withPorts(base, ports));
    }

    private static Built built(ShellStructure.Shell shell, NuclearSteamTurbineBlockEntity master) {
        Key key = new Key(shell.length(), TurbineLayout.frontIsHigh(shell.axis(), master.getFacing()), shell.axis() == Direction.Axis.X);
        Built built = CACHE.get(key);
        if (built == null) {
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
            }
            built = CACHE.computeIfAbsent(key, k -> new Built(NuclearTurbineShapes.of(NuclearTurbineLayout.of(k.length(), k.frontHigh())),
                    k.length() * NuclearTurbineLayout.WIDTH * NuclearTurbineLayout.HEIGHT));
        }
        return built;
    }

    // Whether box (a player's) overlaps a part of a formed turbine's model deep in its core: the generator, its exciter,
    // the cylinders' middles. A collision shape reaches at most a block past its own and the core is air, so nothing
    // there can collide; TurbineDeepCollision keeps the player out of them instead.
    public static boolean overlapsDeep(Level level, AABB box) {
        VoxelShape player = Shapes.create(box);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            ShellStructure.Shell shell = null;
            NuclearSteamTurbineBlockEntity master = null;
            // The turbine's floor is under it, within its height.
            for (int down = 1; down < NuclearTurbineLayout.HEIGHT && shell == null; down++) {
                if (level.getBlockEntity(pos.below(down)) instanceof NuclearSteamTurbineBlockEntity part && part.getShell() != null
                        && part.getShell().contains(pos) && part.getShell().isCore(pos)
                        && level.getBlockEntity(part.getShell().min()) instanceof NuclearSteamTurbineBlockEntity m && part.getShell().equals(m.getShell())) {
                    shell = part.getShell();
                    master = m;
                }
            }
            if (shell == null) {
                continue;
            }
            Built built = built(shell, master);
            int u = TurbineLayout.blockU(shell, pos), y = shell.offset(pos, Direction.Axis.Y), x = TurbineLayout.blockX(shell, pos);
            int index = (u * NuclearTurbineLayout.HEIGHT + y) * NuclearTurbineLayout.WIDTH + x;
            VoxelShape deep = built.deep[index];
            if (deep == null) {
                int[][] boxes = built.frame.deepAt(u, y, x);
                deep = boxes != null ? toWorld(boxes, shell.axis() == Direction.Axis.X) : Shapes.empty();
                built.deep[index] = deep;
            }
            if (!deep.isEmpty() && Shapes.joinIsNotEmpty(deep.move(pos.getX(), pos.getY(), pos.getZ()), player, BooleanOp.AND)) {
                return true;
            }
        }
        return false;
    }

    // The faces of the block at pos holding ports and facing out of the turbine, a bit per Direction.
    private static int portMask(Level level, BlockPos pos, ShellStructure.Shell shell) {
        PortFaces faces = MultiblockPorts.faces(level, pos);
        if (faces.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (faces.get(face) != SideMode.NONE && !shell.contains(pos.relative(face))) {
                mask |= 1 << face.ordinal();
            }
        }
        return mask;
    }

    // Boxes in the turbine's frame (x across, y up, u along) to a shape in the block's, as TurbineHitbox does.
    private static VoxelShape toWorld(int[][] boxes, boolean alongX) {
        VoxelShape shape = Shapes.empty();
        for (int[] box : boxes) {
            VoxelShape piece = alongX
                    ? Block.box(box[2], box[1], 16 - box[3], box[5], box[4], 16 - box[0])
                    : Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
            shape = Shapes.or(shape, piece);
        }
        return shape.optimize();
    }
}
