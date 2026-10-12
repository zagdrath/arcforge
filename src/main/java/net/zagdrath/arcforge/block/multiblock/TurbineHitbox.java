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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.TurbineLayout;
import net.zagdrath.arcforge.steam.TurbineShapes;

// The shapes of a formed Steam Turbine Array's blocks (casings and Pressure Glass alike): the pieces of its model in
// each (see TurbineShapes), turned into the world's frame, and on a block holding ports, each port's plate on its face
// and its collar in from it. Kept for each shape of array (size, front and axis), as both sides ask for them often.
// Only asked of a real level: anything else (a chunk being meshed, a pathfinding region, world generation) gets the
// full cube, as it can't be trusted to reach the array's master.
public final class TurbineHitbox {
    private static final int CACHE_LIMIT = 32;
    // A port: the plate on the face (as the block model draws it) and the collar in from it.
    private static final int PLATE = 1, COLLAR = 10;
    // How long a pane's answer (which array it's a window of, if any) is kept, in ticks.
    private static final long GLASS_TICKS = 20;
    private static final long NO_ARRAY = Long.MIN_VALUE;

    private record Key(int length, int width, int height, boolean high, boolean alongX) {}

    private static final class Built {
        final Key key;
        final TurbineShapes frame;
        final @Nullable VoxelShape[] shapes;
        // The same with a block's ports on it, by which faces have them (a bit per Direction).
        final Map<Long, VoxelShape> ported = new ConcurrentHashMap<>();

        Built(Key key, TurbineShapes frame, int count) {
            this.key = key;
            this.frame = frame;
            this.shapes = new VoxelShape[count];
        }
    }

    private static final Map<Key, Built> CACHE = new ConcurrentHashMap<>();
    // Per level, each pane's master position (or NO_ARRAY) and the game time it was found at.
    private static final Map<Level, Map<Long, long[]>> GLASS = new WeakHashMap<>();

    private TurbineHitbox() {}

    // The shape of the formed casing at pos, or null when it can't tell (its array isn't loaded, say): then the block
    // keeps a full cube.
    static @Nullable VoxelShape casing(BlockGetter getter, BlockPos pos, BlockState state) {
        if (!(getter instanceof Level level) || !(level.getBlockEntity(pos) instanceof SteamTurbineArrayBlockEntity part)) {
            return null;
        }
        ShellStructure.Shell shell = part.getShell();
        if (shell == null) {
            return null;
        }
        boolean ports = state.getBlock() instanceof ShellCasingBlock casing && casing.holdsPorts(state);
        return shape(level, pos, shell, ports ? portMask(level, pos, shell) : 0);
    }

    // The shape of a formed pane at pos that is a window of a Steam Turbine Array, or null when it isn't one.
    static @Nullable VoxelShape glass(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) {
            return null;
        }
        long master = masterOfPane(level, pos);
        if (master == NO_ARRAY || !(level.getBlockEntity(BlockPos.of(master)) instanceof SteamTurbineArrayBlockEntity turbine)) {
            return null;
        }
        ShellStructure.Shell shell = turbine.getShell();
        return shell != null ? shape(level, pos, shell, 0) : null;
    }

    // The master of the Steam Turbine Array a pane is a window of, or NO_ARRAY: the first casing found walking out across
    // the panes beside it, kept a second (a pane is asked often, and the walk is up to a turbine's length each way).
    private static long masterOfPane(Level level, BlockPos pos) {
        long time = level.getGameTime();
        Map<Long, long[]> panes;
        synchronized (GLASS) {
            panes = GLASS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        }
        long[] known = panes.get(pos.asLong());
        if (known != null && Math.abs(time - known[1]) < GLASS_TICKS) {
            return known[0];
        }
        long master = findMaster(level, pos);
        if (panes.size() > 4096) {
            panes.clear();
        }
        panes.put(pos.asLong(), new long[] { master, time });
        return master;
    }

    private static long findMaster(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos.MutableBlockPos at = pos.mutable();
            for (int step = 0; step < SteamTurbineArrayCasingBlock.MAX_LENGTH; step++) {
                at.move(direction);
                if (level.getBlockEntity(at) instanceof ShellMultiblockBlockEntity part) {
                    ShellStructure.Shell shell = part.getShell();
                    if (shell != null && shell.contains(pos)) {
                        return part instanceof SteamTurbineArrayBlockEntity ? shell.min().asLong() : NO_ARRAY;
                    }
                    break;
                }
                if (!PressureGlassBlock.isFormed(level.getBlockState(at))) {
                    break;
                }
            }
        }
        return NO_ARRAY;
    }

    // The faces of the block at pos holding ports and facing out of the array, a bit per Direction.
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

    private static @Nullable VoxelShape shape(Level level, BlockPos pos, ShellStructure.Shell shell, int ports) {
        if (!shell.contains(pos) || shell.isCore(pos)
                || !(level.getBlockEntity(shell.min()) instanceof SteamTurbineArrayBlockEntity master) || !shell.equals(master.getShell())) {
            return null;
        }
        boolean alongX = shell.axis() == Direction.Axis.X;
        Built built = built(shell, master);
        Key key = built.key;
        int u = TurbineLayout.blockU(shell, pos), y = shell.offset(pos, Direction.Axis.Y), x = TurbineLayout.blockX(shell, pos);
        int index = (u * key.height() + y) * key.width() + x;
        VoxelShape shape = built.shapes[index];
        if (shape == null) {
            int[][] boxes = built.frame.at(u, y, x);
            shape = boxes != null ? toWorld(boxes, alongX) : Shapes.block();
            built.shapes[index] = shape;
        }
        if (ports == 0) {
            return shape;
        }
        VoxelShape base = shape;
        return built.ported.computeIfAbsent((long) index << 6 | ports, k -> withPorts(base, ports));
    }

    // The whole model's shape of a formed array, for picking what no block's shape reaches (see TurbinePick).
    public static TurbineShapes model(ShellStructure.Shell shell, SteamTurbineArrayBlockEntity master) {
        return built(shell, master).frame;
    }

    // The shapes of an array of this one's size, front and axis, built on first asking.
    private static Built built(ShellStructure.Shell shell, SteamTurbineArrayBlockEntity master) {
        boolean alongX = shell.axis() == Direction.Axis.X;
        Direction.Axis across = alongX ? Direction.Axis.Z : Direction.Axis.X;
        Key key = new Key(shell.length(), shell.size(across), shell.size(Direction.Axis.Y),
                TurbineLayout.frontIsHigh(shell.axis(), master.getFacing()), alongX);
        Built built = CACHE.get(key);
        if (built == null) {
            if (CACHE.size() >= CACHE_LIMIT) {
                CACHE.clear();
            }
            built = CACHE.computeIfAbsent(key, k -> new Built(k, TurbineShapes.of(TurbineLayout.of(k.length(), k.width(), k.height(), k.high())),
                    k.length() * k.width() * k.height()));
        }
        return built;
    }

    // Boxes in the turbine's frame (x across, y up, u along) to a shape in the block's: along Z, x is world X and u
    // world Z; along X, u is world X and x runs toward world -Z.
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

    private static VoxelShape withPorts(VoxelShape shape, int ports) {
        for (Direction face : Direction.values()) {
            if ((ports & 1 << face.ordinal()) != 0) {
                shape = Shapes.or(shape, port(face));
            }
        }
        return shape.optimize();
    }

    private static VoxelShape port(Direction face) {
        double a = (16 - COLLAR) / 2.0, b = a + COLLAR;
        double p0 = 1.0, p1 = 15.0;
        return switch (face) {
            case UP -> Shapes.or(Block.box(p0, 16 - PLATE, p0, p1, 16, p1), Block.box(a, 0, a, b, 16, b));
            case DOWN -> Shapes.or(Block.box(p0, 0, p0, p1, PLATE, p1), Block.box(a, 0, a, b, 16, b));
            case NORTH -> Shapes.or(Block.box(p0, p0, 0, p1, p1, PLATE), Block.box(a, a, 0, b, b, 16));
            case SOUTH -> Shapes.or(Block.box(p0, p0, 16 - PLATE, p1, p1, 16), Block.box(a, a, 0, b, b, 16));
            case WEST -> Shapes.or(Block.box(0, p0, p0, PLATE, p1, p1), Block.box(0, a, a, 16, b, b));
            case EAST -> Shapes.or(Block.box(16 - PLATE, p0, p0, 16, p1, p1), Block.box(0, a, a, 16, b, b));
        };
    }
}
