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
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseControllerBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseFrameBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhousePart;
import net.zagdrath.arcforge.block.farming.greenhouse.GrowLampBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.PlantingBedBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;

// The Greenhouse Array: a box 5x5 to 11x11 across and 4 to 8 tall. Its twelve edges are Greenhouse Frame; its walls and
// roof are Pressure Glass or Greenhouse Frame; its floor is Greenhouse Frame or Planting Beds. Exactly one Greenhouse
// Controller sits in a wall (not on an edge), facing out. Inside is air, except Grow Lamps in the top layer, hanging from
// the roof. The controller's block entity runs it and decides whether it's formed; every controller is kept in a per-level
// registry, so the other parts (and anything placed or broken near them) find it without searching.
public final class GreenhouseStructure {
    public static final int MIN_WIDTH = 5, MAX_WIDTH = 11, MIN_HEIGHT = 4, MAX_HEIGHT = 8;

    // A formed or candidate greenhouse: its inclusive corners, and the side the controller faces out of.
    public record House(BlockPos min, BlockPos max, Direction front) {
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

        // The interior layer under the roof, where the Grow Lamps hang.
        public int lampLayer() {
            return max.getY() - 1;
        }

        // Blocks of walls and roof (what heat is lost through).
        public int surface() {
            int x = size(Direction.Axis.X), y = size(Direction.Axis.Y), z = size(Direction.Axis.Z);
            return x * z + 2 * (x + z) * (y - 1);
        }

        public AABB box() {
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
    }

    private static final Map<Level, Set<GreenhouseBlockEntity>> CONTROLLERS = new WeakHashMap<>();

    private GreenhouseStructure() {}

    // --- The registry of controllers ---

    // Server side only: clients never know a greenhouse's shape (and the two threads mustn't share this map).
    public static void register(GreenhouseBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null && !level.isClientSide()) {
            CONTROLLERS.computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public static void unregister(GreenhouseBlockEntity controller) {
        Level level = controller.getLevel();
        Set<GreenhouseBlockEntity> set = level != null && !level.isClientSide() ? CONTROLLERS.get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    private static List<GreenhouseBlockEntity> controllers(BlockGetter level) {
        Set<GreenhouseBlockEntity> set = level instanceof Level key && !key.isClientSide() ? CONTROLLERS.get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<GreenhouseBlockEntity> live = new ArrayList<>();
        for (GreenhouseBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    public static boolean isFrame(BlockState state) {
        return state.getBlock() instanceof GreenhouseFrameBlock;
    }

    // A frame or controller of a formed greenhouse.
    public static boolean isFormedPart(BlockState state) {
        return state.getBlock() instanceof GreenhousePart && state.hasProperty(GreenhousePart.FORMED) && state.getValue(GreenhousePart.FORMED);
    }

    // --- Finding the greenhouse round a controller ---

    // The valid greenhouse the controller at pos is in, or null: its wall is the one the controller faces out of (the way
    // it faces first, then the other three). current: the greenhouse it has now (its blocks may still be formed).
    public static @Nullable House find(Level level, BlockPos controller, @Nullable House current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof GreenhouseControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(GreenhouseControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            House house = find(level, controller, front, current);
            if (house != null) {
                return house;
            }
        }
        return null;
    }

    // The frame columns of the wall run along `along`; the floor and roof edges are straight below and above the
    // controller, and the side edges straight left and right of it, so only frames there are tried as edges.
    private static @Nullable House find(Level level, BlockPos controller, Direction front, @Nullable House current) {
        Direction along = front.getClockWise();
        Direction in = front.getOpposite();
        List<Integer> downs = frames(level, controller, Direction.DOWN, MAX_HEIGHT - 2);
        List<Integer> ups = frames(level, controller, Direction.UP, MAX_HEIGHT - 2);
        List<Integer> lefts = frames(level, controller, along.getOpposite(), MAX_WIDTH - 2);
        List<Integer> rights = frames(level, controller, along, MAX_WIDTH - 2);
        for (int down : downs) {
            for (int up : ups) {
                int height = down + up + 1;
                if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
                    continue;
                }
                for (int left : lefts) {
                    for (int right : rights) {
                        int width = left + right + 1;
                        if (width < MIN_WIDTH || width > MAX_WIDTH) {
                            continue;
                        }
                        BlockPos a = controller.relative(along.getOpposite(), left).below(down);
                        BlockPos b = controller.relative(along, right).above(up);
                        for (int depth = MIN_WIDTH; depth <= MAX_WIDTH; depth++) {
                            BlockPos c = b.relative(in, depth - 1);
                            // The far bottom corner first: most depths fail there.
                            if (!level.isLoaded(a.relative(in, depth - 1)) || !isFrame(level.getBlockState(a.relative(in, depth - 1)))) {
                                continue;
                            }
                            House house = new House(min(a, c), max(a, c), front);
                            if (isValid(level, house, controller, current)) {
                                return house;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // Distances (1..limit) from pos along direction where a Greenhouse Frame is.
    private static List<Integer> frames(Level level, BlockPos pos, Direction direction, int limit) {
        List<Integer> found = new ArrayList<>();
        for (int distance = 1; distance <= limit; distance++) {
            BlockPos at = pos.relative(direction, distance);
            if (!level.isLoaded(at)) {
                break;
            }
            if (isFrame(level.getBlockState(at))) {
                found.add(distance);
            }
        }
        return found;
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    // Whether every block of the box is what it should be (see the class comment).
    public static boolean isValid(Level level, House house, BlockPos controller, @Nullable House current) {
        for (BlockPos corner : new BlockPos[] { house.min(), house.max() }) {
            if (!level.isLoaded(corner) || !isFrame(level.getBlockState(corner))) {
                return false;
            }
        }
        for (BlockPos pos : house.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean claimed = current != null && current.contains(pos);
            int boundaries = house.boundaries(pos);
            boolean fits;
            if (boundaries >= 2) {
                fits = isFrame(state) && (claimed || !state.getValue(GreenhousePart.FORMED));
            } else if (boundaries == 1) {
                if (pos.getY() == house.min().getY()) {
                    fits = isFrame(state) && (claimed || !state.getValue(GreenhousePart.FORMED)) || state.getBlock() instanceof PlantingBedBlock;
                } else if (pos.equals(controller)) {
                    fits = state.getBlock() instanceof GreenhouseControllerBlock;
                } else {
                    fits = isFrame(state) && (claimed || !state.getValue(GreenhousePart.FORMED))
                            || state.getBlock() instanceof PressureGlassBlock && (claimed || !PressureGlassBlock.isFormed(state));
                }
            } else {
                fits = state.isAir() || pos.getY() == house.lampLayer() && state.getBlock() instanceof GrowLampBlock;
            }
            if (!fits) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, House house, BlockPos controller) {
        for (BlockPos pos : house.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state;
            if (state.getBlock() instanceof GreenhousePart && state.hasProperty(GreenhousePart.FORMED)) {
                formed = state.setValue(GreenhousePart.FORMED, true);
                if (immutable.equals(controller)) {
                    formed = formed.setValue(GreenhouseControllerBlock.FACING, house.front());
                }
            } else if (state.getBlock() instanceof PressureGlassBlock) {
                formed = state.setValue(PressureGlassBlock.FORMED, true);
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, house.min(), house.max());
    }

    // Un-forms every block of the greenhouse but skip (a controller being broken).
    public static void unform(ServerLevel level, House house, @Nullable BlockPos skip) {
        for (BlockPos pos : house.positions()) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            BlockState loose = state;
            if (isFormedPart(state)) {
                loose = state.setValue(GreenhousePart.FORMED, false);
                if (loose.hasProperty(GreenhouseControllerBlock.LIT)) {
                    loose = loose.setValue(GreenhouseControllerBlock.LIT, false);
                }
            } else if (PressureGlassBlock.isFormed(state) && house.boundaries(immutable) == 1) {
                loose = state.setValue(PressureGlassBlock.FORMED, false);
            } else if (state.getBlock() instanceof GrowLampBlock && state.getValue(GrowLampBlock.LIT)) {
                loose = state.setValue(GrowLampBlock.LIT, false);
            }
            if (loose != state) {
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, house.min(), house.max());
    }

    // --- Finding the controller from any part ---

    // The formed greenhouse controller whose box holds pos (a frame, glass, bed, lamp or the air inside), or null.
    public static @Nullable GreenhouseBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (GreenhouseBlockEntity controller : controllers(level)) {
            House house = controller.getHouse();
            if (house != null && house.contains(pos)) {
                return controller;
            }
        }
        return null;
    }

    // Something near pos was placed, broken or wrenched: every controller whose greenhouse could include it rechecks
    // next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, GreenhouseBlockEntity::requestCheck);
        }
    }

    // Controllers that pos could matter to: in their formed box (or just outside it), or, unformed, within the largest
    // box round them.
    private static void forEachNearbyController(Level level, BlockPos pos, Consumer<GreenhouseBlockEntity> action) {
        for (GreenhouseBlockEntity controller : controllers(level)) {
            House house = controller.getHouse();
            BlockPos at = controller.getBlockPos();
            boolean near = house != null ? house.box().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < MAX_WIDTH && Math.abs(pos.getZ() - at.getZ()) < MAX_WIDTH
                            && Math.abs(pos.getY() - at.getY()) < MAX_HEIGHT;
            if (near) {
                action.accept(controller);
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed greenhouse opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        GreenhouseBlockEntity controller = findController(level, pos);
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
        forEachNearbyController(level, pos, GreenhouseBlockEntity::checkNow);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.greenhouse.formed" : "message.arcforge.greenhouse.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
