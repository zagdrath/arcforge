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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadeCasingBlock;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadeControllerBlock;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadePart;
import net.zagdrath.arcforge.block.multiblock.GasCentrifugeBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;

// The Gas Centrifuge Cascade: a hall on a raised floor of Centrifuge Cascade Casings, 3 to 7 blocks each way, with the
// Centrifuge Cascade Controller in an edge of the floor (not a corner), facing out. On the floor stand the Gas
// Centrifuges, each a column two blocks tall, anywhere but over the controller (its control panel stands there): at
// least one. The rest of the two layers over the floor is open (air, or anything you can walk through: a torch, a
// sign). The box is the floor and those two layers. The controller's block entity runs it; every controller is kept in
// a per-level registry, so the other parts find it without searching. FORMED is set on the floor, the controller and
// the centrifuges; formed, they draw as one model (see CentrifugeCascadeRenderer) and the floor's ports show.
public final class CentrifugeCascadeStructure {
    public static final int MIN_SIZE = 3, MAX_SIZE = 7, HEIGHT = 3;

    // A formed or candidate hall: its inclusive corners (the floor at min's y, two layers over it), and the side the
    // controller faces out of.
    public record Box(BlockPos min, BlockPos max, Direction front) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max);
        }

        public Iterable<BlockPos> floor() {
            return BlockPos.betweenClosed(min, new BlockPos(max.getX(), min.getY(), max.getZ()));
        }

        public boolean isFloor(BlockPos pos) {
            return contains(pos) && pos.getY() == min.getY();
        }

        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }

        // Across the front (the controller's side) and back from it.
        public int width() {
            return front.getAxis() == Direction.Axis.Z ? sizeX() : sizeZ();
        }

        public int depth() {
            return front.getAxis() == Direction.Axis.Z ? sizeZ() : sizeX();
        }

        // "W×D" across the front and back from it.
        public String sizeText() {
            return width() + "×" + depth();
        }

        public AABB aabb() {
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
    }

    private static final Map<Level, Set<CentrifugeCascadeBlockEntity>> SERVER_CONTROLLERS = new WeakHashMap<>();
    private static final Map<Level, Set<CentrifugeCascadeBlockEntity>> CLIENT_CONTROLLERS = new WeakHashMap<>();

    private CentrifugeCascadeStructure() {}

    // --- The registry of controllers ---

    private static Map<Level, Set<CentrifugeCascadeBlockEntity>> registry(Level level) {
        return level.isClientSide() ? CLIENT_CONTROLLERS : SERVER_CONTROLLERS;
    }

    public static void register(CentrifugeCascadeBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null) {
            registry(level).computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public static void unregister(CentrifugeCascadeBlockEntity controller) {
        Level level = controller.getLevel();
        Set<CentrifugeCascadeBlockEntity> set = level != null ? registry(level).get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    private static List<CentrifugeCascadeBlockEntity> controllers(BlockGetter level) {
        Set<CentrifugeCascadeBlockEntity> set = level instanceof Level key ? registry(key).get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<CentrifugeCascadeBlockEntity> live = new ArrayList<>();
        for (CentrifugeCascadeBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    public static boolean isCasing(BlockState state) {
        return state.getBlock() instanceof CentrifugeCascadeCasingBlock;
    }

    // A casing, the controller or a centrifuge.
    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof CentrifugeCascadePart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(CentrifugeCascadePart.FORMED);
    }

    public static boolean isCentrifuge(BlockState state, DoubleBlockHalf half) {
        return state.getBlock() instanceof GasCentrifugeBlock && state.getValue(GasCentrifugeBlock.HALF) == half;
    }

    private static boolean isFormed(BlockState state) {
        return state.hasProperty(CentrifugeCascadePart.FORMED) && state.getValue(CentrifugeCascadePart.FORMED);
    }

    // Open space over the floor: air, or something you can walk through.
    private static boolean isOpen(BlockState state) {
        return state.isAir() || !state.isSolid() && !(state.getBlock() instanceof CentrifugeCascadePart);
    }

    // --- Finding the hall round a controller ---

    // The valid hall the controller at pos is in, or null: the way it faces first, then the other three.
    public static @Nullable Box find(Level level, BlockPos controller, @Nullable Box current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof CentrifugeCascadeControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(CentrifugeCascadeControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            Box box = find(level, controller, front, current);
            if (box != null) {
                return box;
            }
        }
        return null;
    }

    // The floor's front edge runs left and right of the controller, so only casings there are tried as its ends; then
    // each depth back from it. The biggest first: a smaller hall inside the floor is valid too, and would leave the
    // centrifuges round it loose.
    private static @Nullable Box find(Level level, BlockPos controller, Direction front, @Nullable Box current) {
        Direction along = front.getClockWise();
        Direction in = front.getOpposite();
        List<Integer> lefts = casings(level, controller, along.getOpposite(), MAX_SIZE - 2).reversed();
        List<Integer> rights = casings(level, controller, along, MAX_SIZE - 2).reversed();
        for (int left : lefts) {
            for (int right : rights) {
                int width = left + right + 1;
                if (width < MIN_SIZE || width > MAX_SIZE) {
                    continue;
                }
                BlockPos a = controller.relative(along.getOpposite(), left);
                BlockPos b = controller.relative(along, right).above(HEIGHT - 1);
                for (int depth = MAX_SIZE; depth >= MIN_SIZE; depth--) {
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
        return null;
    }

    // Distances (1..limit) from pos along direction where a casing is.
    private static List<Integer> casings(Level level, BlockPos pos, Direction direction, int limit) {
        List<Integer> found = new ArrayList<>();
        for (int distance = 1; distance <= limit; distance++) {
            BlockPos at = pos.relative(direction, distance);
            if (!level.isLoaded(at) || !isCasing(level.getBlockState(at))) {
                break;
            }
            found.add(distance);
        }
        return found;
    }

    // Whether every block of the hall is what it should be (see the class comment). A block another hall has claimed
    // (formed, outside current) doesn't count.
    public static boolean isValid(Level level, Box box, BlockPos controller, @Nullable Box current) {
        if (box.sizeX() < MIN_SIZE || box.sizeX() > MAX_SIZE || box.sizeZ() < MIN_SIZE || box.sizeZ() > MAX_SIZE
                || box.max().getY() - box.min().getY() != HEIGHT - 1 || controller.getY() != box.min().getY()) {
            return false;
        }
        // The controller is in the front edge, not a corner.
        if (box.contains(controller.relative(box.front())) || !box.contains(controller.relative(box.front().getClockWise()))
                || !box.contains(controller.relative(box.front().getCounterClockWise()))) {
            return false;
        }
        boolean centrifuge = false;
        for (BlockPos pos : box.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean free = current != null && current.contains(pos) || !isFormed(state);
            int up = pos.getY() - box.min().getY();
            boolean overController = pos.getX() == controller.getX() && pos.getZ() == controller.getZ();
            boolean fits;
            if (up == 0) {
                fits = pos.equals(controller) ? state.getBlock() instanceof CentrifugeCascadeControllerBlock : isCasing(state) && free;
            } else if (up == 1) {
                if (isCentrifuge(state, DoubleBlockHalf.LOWER)) {
                    fits = !overController && free && isCentrifuge(level.getBlockState(pos.above()), DoubleBlockHalf.UPPER);
                    centrifuge = true;
                } else {
                    fits = isOpen(state);
                }
            } else {
                fits = isCentrifuge(state, DoubleBlockHalf.UPPER) ? isCentrifuge(level.getBlockState(pos.below()), DoubleBlockHalf.LOWER) && free
                        : isOpen(state);
            }
            if (!fits) {
                return false;
            }
        }
        return centrifuge;
    }

    // Whether every centrifuge in the hall is formed: one placed after it formed isn't, and the hall must form again to
    // count it.
    public static boolean isFullyFormed(Level level, Box box) {
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof GasCentrifugeBlock && !isFormed(state)) {
                return false;
            }
        }
        return true;
    }

    // The centrifuges' lower blocks.
    public static List<BlockPos> centrifuges(BlockGetter level, Box box) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.min().above(), new BlockPos(box.max().getX(), box.min().getY() + 1, box.max().getZ()))) {
            if (isCentrifuge(level.getBlockState(pos), DoubleBlockHalf.LOWER)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Box box, BlockPos controller) {
        for (BlockPos pos : box.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (!isPart(state)) {
                continue;
            }
            BlockState formed = state.setValue(CentrifugeCascadePart.FORMED, true);
            if (immutable.equals(controller)) {
                formed = formed.setValue(CentrifugeCascadeControllerBlock.FACING, box.front());
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // Un-forms every block of the hall but skip (a block being broken).
    public static void unform(ServerLevel level, Box box, @Nullable BlockPos skip) {
        for (BlockPos pos : box.positions()) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            if (isPart(state) && isFormed(state)) {
                BlockState loose = state.setValue(CentrifugeCascadePart.FORMED, false);
                if (loose.hasProperty(CentrifugeCascadeControllerBlock.LIT)) {
                    loose = loose.setValue(CentrifugeCascadeControllerBlock.LIT, false);
                }
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // --- Finding the controller from any part ---

    // The formed controller whose hall holds pos, or null.
    public static @Nullable CentrifugeCascadeBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (CentrifugeCascadeBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            if (box != null && box.contains(pos)) {
                return controller;
            }
        }
        return null;
    }

    // Something near pos was placed, broken or wrenched: every controller whose hall could include it rechecks.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, CentrifugeCascadeBlockEntity::requestCheck);
        }
    }

    private static void forEachNearbyController(Level level, BlockPos pos, Consumer<CentrifugeCascadeBlockEntity> action) {
        for (CentrifugeCascadeBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            BlockPos at = controller.getBlockPos();
            boolean near = box != null ? box.aabb().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < MAX_SIZE && Math.abs(pos.getZ() - at.getZ()) < MAX_SIZE
                            && pos.getY() - at.getY() >= -1 && pos.getY() - at.getY() <= HEIGHT;
            if (near) {
                action.accept(controller);
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a block of a formed hall opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        CentrifugeCascadeBlockEntity controller = findController(level, pos);
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
        forEachNearbyController(level, pos, CentrifugeCascadeBlockEntity::checkNow);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.centrifuge_cascade.formed" : "message.arcforge.centrifuge_cascade.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
