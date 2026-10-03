/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.block.multiblock.FireboxArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.FireboxArrayPart;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;

// The Firebox Array: a hollow box of Firebox Array Casings with one Firebox Array Controller, 3 to 7 by 3 to 9 across
// (either way round, so it needn't be square) and 3 to 5 tall. Its twelve edges are casings; its walls, floor and roof
// are casings or Pressure Glass, so the fire shows through a window; exactly one controller sits in a side wall (not on
// an edge), facing out. Inside is air. The controller's block entity runs it and decides whether it's formed; every
// controller is kept in a per-level registry (as the Greenhouse Array's are), so the other parts find it without
// searching. FORMED is set on every casing, the controller and every pane of a formed box, and INWARD on each wall block
// says which way the inside is (its model leaves that face out; the renderer lines the inside with firebrick).
public final class FireboxArrayStructure {
    public static final int MIN_SIZE = 3, MAX_WIDTH = 7, MAX_DEPTH = 9, MAX_HEIGHT = 5;

    // A formed or candidate box: its inclusive corners, and the side the controller faces out of.
    public record Box(BlockPos min, BlockPos max, Direction front) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max);
        }

        public int size(Direction.Axis axis) {
            return max.get(axis) - min.get(axis) + 1;
        }

        // Blocks in the box, walls and inside: what its heat output and buffer scale with.
        public int volume() {
            return size(Direction.Axis.X) * size(Direction.Axis.Y) * size(Direction.Axis.Z);
        }

        // On how many axes pos lies on the box's boundary: 0 inside, 1 on a face, 2 or 3 on an edge or corner.
        public int boundaries(BlockPos pos) {
            int count = 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (pos.get(axis) == min.get(axis) || pos.get(axis) == max.get(axis)) {
                    count++;
                }
            }
            return count;
        }

        public boolean isInterior(BlockPos pos) {
            return contains(pos) && boundaries(pos) == 0;
        }

        // The way into the box from a wall block (one on exactly one face), or null for an edge, a corner or the inside.
        public @Nullable Direction inward(BlockPos pos) {
            if (!contains(pos) || boundaries(pos) != 1) {
                return null;
            }
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (pos.get(axis) == min.get(axis)) {
                    return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                }
                if (pos.get(axis) == max.get(axis)) {
                    return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
                }
            }
            return null;
        }

        // "W×H×D" (x, height, z).
        public String sizeText() {
            return size(Direction.Axis.X) + "×" + size(Direction.Axis.Y) + "×" + size(Direction.Axis.Z);
        }

        public BlockPos centre() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        }

        public AABB aabb() {
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
    }

    // Whether a box of these sizes is allowed: 3 to 7 by 3 to 9 across (either way round), 3 to 5 tall.
    public static boolean allows(int x, int y, int z) {
        return Math.min(x, z) >= MIN_SIZE && Math.min(x, z) <= MAX_WIDTH && Math.max(x, z) <= MAX_DEPTH && y >= MIN_SIZE && y <= MAX_HEIGHT;
    }

    // One registry per side: the server's for forming and GUIs, the client's so Jade and the model can name the box any of
    // its blocks belongs to. Each is only touched from its own thread.
    private static final Map<Level, Set<FireboxArrayBlockEntity>> SERVER_CONTROLLERS = new WeakHashMap<>();
    private static final Map<Level, Set<FireboxArrayBlockEntity>> CLIENT_CONTROLLERS = new WeakHashMap<>();

    private FireboxArrayStructure() {}

    // --- The registry of controllers ---

    private static Map<Level, Set<FireboxArrayBlockEntity>> registry(Level level) {
        return level.isClientSide() ? CLIENT_CONTROLLERS : SERVER_CONTROLLERS;
    }

    public static void register(FireboxArrayBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null) {
            registry(level).computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public static void unregister(FireboxArrayBlockEntity controller) {
        Level level = controller.getLevel();
        Set<FireboxArrayBlockEntity> set = level != null ? registry(level).get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    private static List<FireboxArrayBlockEntity> controllers(BlockGetter level) {
        Set<FireboxArrayBlockEntity> set = level instanceof Level key ? registry(key).get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<FireboxArrayBlockEntity> live = new ArrayList<>();
        for (FireboxArrayBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    public static boolean isCasing(BlockState state) {
        return state.getBlock() instanceof FireboxArrayCasingBlock;
    }

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof FireboxArrayPart;
    }

    // A casing or controller of a formed box.
    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(FireboxArrayPart.FORMED);
    }

    // --- Finding the box round a controller ---

    // The valid box the controller at pos is in, or null: its wall is the one the controller faces out of (the way it
    // faces first, then the other three). current: the box it has now (its blocks may still be formed).
    public static @Nullable Box find(Level level, BlockPos controller, @Nullable Box current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof FireboxArrayControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(FireboxArrayControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            Box box = find(level, controller, front, current);
            if (box != null) {
                return box;
            }
        }
        return null;
    }

    // The edges of the controller's wall are straight below and above it and straight left and right of it, so only
    // casings there are tried as edges; then each depth into the box.
    private static @Nullable Box find(Level level, BlockPos controller, Direction front, @Nullable Box current) {
        Direction along = front.getClockWise();
        Direction in = front.getOpposite();
        List<Integer> downs = casings(level, controller, Direction.DOWN, MAX_HEIGHT - 2);
        List<Integer> ups = casings(level, controller, Direction.UP, MAX_HEIGHT - 2);
        List<Integer> lefts = casings(level, controller, along.getOpposite(), MAX_DEPTH - 2);
        List<Integer> rights = casings(level, controller, along, MAX_DEPTH - 2);
        for (int down : downs) {
            for (int up : ups) {
                int height = down + up + 1;
                if (height < MIN_SIZE || height > MAX_HEIGHT) {
                    continue;
                }
                for (int left : lefts) {
                    for (int right : rights) {
                        int width = left + right + 1;
                        if (width < MIN_SIZE || width > MAX_DEPTH) {
                            continue;
                        }
                        BlockPos a = controller.relative(along.getOpposite(), left).below(down);
                        BlockPos b = controller.relative(along, right).above(up);
                        for (int depth = MIN_SIZE; depth <= MAX_DEPTH; depth++) {
                            if (!allows(width, height, depth)) {
                                continue;
                            }
                            BlockPos far = a.relative(in, depth - 1);
                            // The far bottom corner first: most depths fail there.
                            if (!level.isLoaded(far) || !isCasing(level.getBlockState(far))) {
                                continue;
                            }
                            BlockPos c = b.relative(in, depth - 1);
                            Box box = new Box(BlockPos.min(a, c), BlockPos.max(a, c), front);
                            if (isValid(level, box, controller, current)) {
                                return box;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // Distances (1..limit) from pos along direction where a Firebox Array Casing is.
    private static List<Integer> casings(Level level, BlockPos pos, Direction direction, int limit) {
        List<Integer> found = new ArrayList<>();
        for (int distance = 1; distance <= limit; distance++) {
            BlockPos at = pos.relative(direction, distance);
            if (!level.isLoaded(at)) {
                break;
            }
            if (isCasing(level.getBlockState(at))) {
                found.add(distance);
            }
        }
        return found;
    }

    // Whether every block of the box is what it should be (see the class comment).
    public static boolean isValid(Level level, Box box, BlockPos controller, @Nullable Box current) {
        for (BlockPos corner : new BlockPos[] { box.min(), box.max() }) {
            if (!level.isLoaded(corner) || !isCasing(level.getBlockState(corner))) {
                return false;
            }
        }
        if (box.boundaries(controller) != 1 || box.inward(controller) == null || !box.inward(controller).getAxis().isHorizontal()) {
            return false;
        }
        for (BlockPos pos : box.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean claimed = current != null && current.contains(pos);
            int boundaries = box.boundaries(pos);
            boolean fits;
            if (boundaries >= 2) {
                fits = isCasing(state) && (claimed || !state.getValue(FireboxArrayPart.FORMED));
            } else if (boundaries == 1) {
                if (pos.equals(controller)) {
                    fits = state.getBlock() instanceof FireboxArrayControllerBlock;
                } else {
                    fits = isCasing(state) && (claimed || !state.getValue(FireboxArrayPart.FORMED))
                            || state.getBlock() instanceof PressureGlassBlock && (claimed || !PressureGlassBlock.isFormed(state));
                }
            } else {
                fits = state.isAir();
            }
            if (!fits) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Box box, BlockPos controller) {
        for (BlockPos pos : box.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state;
            if (isPart(state)) {
                formed = state.setValue(FireboxArrayPart.FORMED, true).setValue(FireboxArrayPart.INWARD, FireboxArrayPart.Inward.of(box.inward(immutable)));
                if (immutable.equals(controller)) {
                    formed = formed.setValue(FireboxArrayControllerBlock.FACING, box.front());
                }
            } else if (state.getBlock() instanceof PressureGlassBlock) {
                formed = state.setValue(PressureGlassBlock.FORMED, true);
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // Un-forms every block of the box but skip (a controller being broken).
    public static void unform(ServerLevel level, Box box, @Nullable BlockPos skip) {
        for (BlockPos pos : box.positions()) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            BlockState loose = state;
            if (isFormedPart(state)) {
                loose = state.setValue(FireboxArrayPart.FORMED, false).setValue(FireboxArrayPart.INWARD, FireboxArrayPart.Inward.NONE);
                if (loose.hasProperty(FireboxArrayControllerBlock.LIT)) {
                    loose = loose.setValue(FireboxArrayControllerBlock.LIT, false);
                }
            } else if (PressureGlassBlock.isFormed(state) && box.boundaries(immutable) == 1) {
                loose = state.setValue(PressureGlassBlock.FORMED, false);
            }
            if (loose != state) {
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // --- Finding the controller from any part ---

    // The formed Firebox Array controller whose box holds pos (a casing, a pane or the air inside), or null.
    public static @Nullable FireboxArrayBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (FireboxArrayBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            if (box != null && box.contains(pos)) {
                return controller;
            }
        }
        return null;
    }

    // Something near pos was placed, broken or wrenched: every controller whose box could include it rechecks next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, FireboxArrayBlockEntity::requestCheck);
        }
    }

    // Controllers that pos could matter to: in their formed box (or just outside it), or, unformed, within the largest box
    // round them.
    private static void forEachNearbyController(Level level, BlockPos pos, Consumer<FireboxArrayBlockEntity> action) {
        for (FireboxArrayBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            BlockPos at = controller.getBlockPos();
            boolean near = box != null ? box.aabb().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < MAX_DEPTH && Math.abs(pos.getZ() - at.getZ()) < MAX_DEPTH
                            && Math.abs(pos.getY() - at.getY()) < MAX_HEIGHT;
            if (near) {
                action.accept(controller);
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed box opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        FireboxArrayBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a part: nearby controllers recheck, and the player hears whether it's formed.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        forEachNearbyController(level, pos, FireboxArrayBlockEntity::checkNow);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.firebox_array.formed" : "message.arcforge.firebox_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
