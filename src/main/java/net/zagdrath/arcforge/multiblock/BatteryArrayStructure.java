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
import net.zagdrath.arcforge.block.multiblock.BatteryArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayPart;
import net.zagdrath.arcforge.block.multiblock.LithiumCellBlock;
import net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;

// The Battery Array: a box of Battery Array Casings 3 to 5 blocks along each axis (any mix, so it needn't be a cube) with
// one Battery Array Controller in a side face (not on an edge), facing out. Its twelve edges are casings; its faces are
// casings or Pressure Glass. The inside (1 to 27 blocks) is filled entirely with Lithium Cells and Power Regulators, in any
// mix, and holds at least one cell. The controller's block entity runs it and decides whether it's formed; every
// controller is kept in a per-level registry (as the Firebox Array's are), so the other parts find it without searching.
// FORMED is set on every block of a formed box: casings, controller, panes, cells and regulators.
public final class BatteryArrayStructure {
    public static final int MIN_SIZE = 3, MAX_SIZE = 5;

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

        // The inside: the cells and regulators.
        public Iterable<BlockPos> interior() {
            return BlockPos.betweenClosed(min.offset(1, 1, 1), max.offset(-1, -1, -1));
        }

        public int size(Direction.Axis axis) {
            return max.get(axis) - min.get(axis) + 1;
        }

        public int volume() {
            return size(Direction.Axis.X) * size(Direction.Axis.Y) * size(Direction.Axis.Z);
        }

        public int interiorVolume() {
            return (size(Direction.Axis.X) - 2) * (size(Direction.Axis.Y) - 2) * (size(Direction.Axis.Z) - 2);
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

        // The way into the box from a face block (one on exactly one face), or null.
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

        public AABB aabb() {
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
    }

    public static boolean allows(int x, int y, int z) {
        return x >= MIN_SIZE && x <= MAX_SIZE && y >= MIN_SIZE && y <= MAX_SIZE && z >= MIN_SIZE && z <= MAX_SIZE;
    }

    private static final Map<Level, Set<BatteryArrayBlockEntity>> SERVER_CONTROLLERS = new WeakHashMap<>();
    private static final Map<Level, Set<BatteryArrayBlockEntity>> CLIENT_CONTROLLERS = new WeakHashMap<>();

    private BatteryArrayStructure() {}

    // --- The registry of controllers ---

    private static Map<Level, Set<BatteryArrayBlockEntity>> registry(Level level) {
        return level.isClientSide() ? CLIENT_CONTROLLERS : SERVER_CONTROLLERS;
    }

    public static void register(BatteryArrayBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null) {
            registry(level).computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public static void unregister(BatteryArrayBlockEntity controller) {
        Level level = controller.getLevel();
        Set<BatteryArrayBlockEntity> set = level != null ? registry(level).get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    private static List<BatteryArrayBlockEntity> controllers(BlockGetter level) {
        Set<BatteryArrayBlockEntity> set = level instanceof Level key ? registry(key).get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<BatteryArrayBlockEntity> live = new ArrayList<>();
        for (BatteryArrayBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    public static boolean isCasing(BlockState state) {
        return state.getBlock() instanceof BatteryArrayCasingBlock;
    }

    // A casing or the controller.
    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof BatteryArrayPart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(BatteryArrayPart.FORMED);
    }

    // A Lithium Cell or a Power Regulator: what fills the inside.
    public static boolean isFill(BlockState state) {
        return state.getBlock() instanceof LithiumCellBlock || state.getBlock() instanceof PowerRegulatorBlock;
    }

    private static boolean isFormed(BlockState state) {
        return state.hasProperty(BatteryArrayPart.FORMED) && state.getValue(BatteryArrayPart.FORMED);
    }

    // --- Finding the box round a controller ---

    // The valid box the controller at pos is in, or null: its face is the one the controller faces out of (the way it
    // faces first, then the other three). current: the box it has now (its blocks may still be formed).
    public static @Nullable Box find(Level level, BlockPos controller, @Nullable Box current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof BatteryArrayControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(BatteryArrayControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            Box box = find(level, controller, front, current);
            if (box != null) {
                return box;
            }
        }
        return null;
    }

    // The edges of the controller's face are straight below, above, left and right of it, so only casings there are
    // tried as edges; then each depth into the box.
    private static @Nullable Box find(Level level, BlockPos controller, Direction front, @Nullable Box current) {
        Direction along = front.getClockWise();
        Direction in = front.getOpposite();
        List<Integer> downs = casings(level, controller, Direction.DOWN, MAX_SIZE - 2);
        List<Integer> ups = casings(level, controller, Direction.UP, MAX_SIZE - 2);
        List<Integer> lefts = casings(level, controller, along.getOpposite(), MAX_SIZE - 2);
        List<Integer> rights = casings(level, controller, along, MAX_SIZE - 2);
        for (int down : downs) {
            for (int up : ups) {
                int height = down + up + 1;
                if (height < MIN_SIZE || height > MAX_SIZE) {
                    continue;
                }
                for (int left : lefts) {
                    for (int right : rights) {
                        int width = left + right + 1;
                        if (width < MIN_SIZE || width > MAX_SIZE) {
                            continue;
                        }
                        BlockPos a = controller.relative(along.getOpposite(), left).below(down);
                        BlockPos b = controller.relative(along, right).above(up);
                        for (int depth = MIN_SIZE; depth <= MAX_SIZE; depth++) {
                            BlockPos far = a.relative(in, depth - 1);
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

    // Distances (1..limit) from pos along direction where a Battery Array Casing is.
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

    // Whether every block of the box is what it should be (see the class comment). A block another box has claimed
    // (formed, outside current) doesn't count.
    public static boolean isValid(Level level, Box box, BlockPos controller, @Nullable Box current) {
        for (BlockPos corner : new BlockPos[] { box.min(), box.max() }) {
            if (!level.isLoaded(corner) || !isCasing(level.getBlockState(corner))) {
                return false;
            }
        }
        if (!allows(box.size(Direction.Axis.X), box.size(Direction.Axis.Y), box.size(Direction.Axis.Z))) {
            return false;
        }
        Direction inward = box.inward(controller);
        if (box.boundaries(controller) != 1 || inward == null || !inward.getAxis().isHorizontal()) {
            return false;
        }
        boolean cell = false;
        for (BlockPos pos : box.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean free = current != null && current.contains(pos) || !isFormed(state);
            int boundaries = box.boundaries(pos);
            boolean fits;
            if (boundaries >= 2) {
                fits = isCasing(state) && free;
            } else if (boundaries == 1) {
                if (pos.equals(controller)) {
                    fits = state.getBlock() instanceof BatteryArrayControllerBlock;
                } else {
                    fits = (isCasing(state) || state.getBlock() instanceof PressureGlassBlock) && free;
                }
            } else {
                fits = isFill(state) && free;
                cell |= state.getBlock() instanceof LithiumCellBlock;
            }
            if (!fits) {
                return false;
            }
        }
        return cell;
    }

    // Whether every cell and regulator inside is formed: one swapped in place (without the old one going to air first)
    // isn't, and the box must form again to count it.
    public static boolean isFullyFormed(Level level, Box box) {
        for (BlockPos pos : box.interior()) {
            if (!isFormed(level.getBlockState(pos))) {
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
            if (state.hasProperty(BatteryArrayPart.FORMED) && (isPart(state) || isFill(state))) {
                formed = state.setValue(BatteryArrayPart.FORMED, true);
                if (immutable.equals(controller)) {
                    formed = formed.setValue(BatteryArrayControllerBlock.FACING, box.front());
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

    // Un-forms every block of the box but skip (a block being broken). The cells' charge then shows their own fill
    // (see LithiumCellBlockEntity), and the controller goes dark.
    public static void unform(ServerLevel level, Box box, @Nullable BlockPos skip) {
        for (BlockPos pos : box.positions()) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            BlockState loose = state;
            if ((isPart(state) || isFill(state)) && isFormed(state)) {
                loose = state.setValue(BatteryArrayPart.FORMED, false);
                if (loose.hasProperty(BatteryArrayControllerBlock.CHARGE)) {
                    loose = loose.setValue(BatteryArrayControllerBlock.CHARGE, 0);
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

    // The formed Battery Array controller whose box holds pos (a casing, a pane, a cell or a regulator), or null.
    public static @Nullable BatteryArrayBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (BatteryArrayBlockEntity controller : controllers(level)) {
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
            forEachNearbyController(level, pos, BatteryArrayBlockEntity::requestCheck);
        }
    }

    private static void forEachNearbyController(Level level, BlockPos pos, Consumer<BatteryArrayBlockEntity> action) {
        for (BatteryArrayBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            BlockPos at = controller.getBlockPos();
            boolean near = box != null ? box.aabb().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < MAX_SIZE && Math.abs(pos.getZ() - at.getZ()) < MAX_SIZE
                            && Math.abs(pos.getY() - at.getY()) < MAX_SIZE;
            if (near) {
                action.accept(controller);
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a block of a formed box opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        BatteryArrayBlockEntity controller = findController(level, pos);
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
        forEachNearbyController(level, pos, BatteryArrayBlockEntity::checkNow);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.battery_array.formed" : "message.arcforge.battery_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
