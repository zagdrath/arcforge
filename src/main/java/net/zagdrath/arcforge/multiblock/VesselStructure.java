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
import java.util.function.Predicate;

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
import net.zagdrath.arcforge.block.multiblock.VesselControllerBlock;
import net.zagdrath.arcforge.block.multiblock.VesselPart;
import net.zagdrath.arcforge.block.nuclear.VesselInteriorBlock;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;

// A walled box multiblock, a vessel (the Fission Reactor's vessel, the Steam Generator, the Heavy Water Tower; see
// VesselStructures): a box of the structure's casings, from minWidth to maxWidth across each way and minHeight to
// maxHeight tall, with one controller in a side face (not on an edge), facing out. Its twelve edges are casings; its
// faces are casings or its glass; the inside holds only what the structure allows (air counts when it allows it), and
// passes the structure's own check (a reactor needs a fuel channel). The controller's block entity runs it and decides
// whether it's formed; every controller is kept in a per-level registry, so the other parts find it without searching.
// FORMED is set on the casings, the controller and the glass of a formed box.
public final class VesselStructure {
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

        public Iterable<BlockPos> interior() {
            return BlockPos.betweenClosed(min.offset(1, 1, 1), max.offset(-1, -1, -1));
        }

        public int size(Direction.Axis axis) {
            return max.get(axis) - min.get(axis) + 1;
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

        // The walls: every block that isn't inside.
        public boolean isWall(BlockPos pos) {
            return contains(pos) && boundaries(pos) > 0;
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

        public BlockPos centre() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        }
    }

    // What a structure is made of and how big it may be. glass may be null (no windows); square: its footprint must
    // be square (the towers).
    public record Spec(String id, int minWidth, int maxWidth, int minHeight, int maxHeight, boolean square, Predicate<BlockState> casing,
            @Nullable Predicate<BlockState> glass, Predicate<BlockState> fill, InteriorCheck check) {}

    // The structure's own check of the inside (after every block fits).
    @FunctionalInterface
    public interface InteriorCheck {
        boolean test(Level level, Box box);
    }

    private final Spec spec;
    private final Map<Level, Set<VesselControllerBlockEntity>> server = new WeakHashMap<>();
    private final Map<Level, Set<VesselControllerBlockEntity>> client = new WeakHashMap<>();

    public VesselStructure(Spec spec) {
        this.spec = spec;
    }

    public Spec spec() {
        return spec;
    }

    public boolean allows(int x, int y, int z) {
        return x >= spec.minWidth() && x <= spec.maxWidth() && z >= spec.minWidth() && z <= spec.maxWidth() && y >= spec.minHeight()
                && y <= spec.maxHeight() && (!spec.square() || x == z);
    }

    // --- The registry of controllers ---

    private Map<Level, Set<VesselControllerBlockEntity>> registry(Level level) {
        return level.isClientSide() ? client : server;
    }

    public void register(VesselControllerBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null) {
            registry(level).computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public void unregister(VesselControllerBlockEntity controller) {
        Level level = controller.getLevel();
        Set<VesselControllerBlockEntity> set = level != null ? registry(level).get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    private List<VesselControllerBlockEntity> controllers(BlockGetter level) {
        Set<VesselControllerBlockEntity> set = level instanceof Level key ? registry(key).get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<VesselControllerBlockEntity> live = new ArrayList<>();
        for (VesselControllerBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    public boolean isCasing(BlockState state) {
        return spec.casing().test(state);
    }

    private boolean isGlass(BlockState state) {
        return spec.glass() != null && spec.glass().test(state);
    }

    private static boolean isFormed(BlockState state) {
        return state.hasProperty(VesselPart.FORMED) && state.getValue(VesselPart.FORMED);
    }

    private boolean isController(BlockState state) {
        return state.getBlock() instanceof VesselControllerBlock<?> block && block.structure() == this;
    }

    // --- Finding the box round a controller ---

    // The valid box the controller at pos is in, or null: its face is the one the controller faces out of (the way it
    // faces first, then the other three). current: the box it has now (its blocks may still be formed).
    public @Nullable Box find(Level level, BlockPos controller, @Nullable Box current) {
        BlockState state = level.getBlockState(controller);
        if (!isController(state)) {
            return null;
        }
        Direction facing = state.getValue(VesselControllerBlock.FACING);
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
    private @Nullable Box find(Level level, BlockPos controller, Direction front, @Nullable Box current) {
        Direction along = front.getClockWise();
        Direction in = front.getOpposite();
        List<Integer> downs = casings(level, controller, Direction.DOWN, spec.maxHeight() - 2);
        List<Integer> ups = casings(level, controller, Direction.UP, spec.maxHeight() - 2);
        List<Integer> lefts = casings(level, controller, along.getOpposite(), spec.maxWidth() - 2);
        List<Integer> rights = casings(level, controller, along, spec.maxWidth() - 2);
        for (int down : downs) {
            for (int up : ups) {
                int height = down + up + 1;
                if (height < spec.minHeight() || height > spec.maxHeight()) {
                    continue;
                }
                for (int left : lefts) {
                    for (int right : rights) {
                        int width = left + right + 1;
                        if (width < spec.minWidth() || width > spec.maxWidth()) {
                            continue;
                        }
                        BlockPos a = controller.relative(along.getOpposite(), left).below(down);
                        BlockPos b = controller.relative(along, right).above(up);
                        for (int depth = spec.minWidth(); depth <= spec.maxWidth(); depth++) {
                            if (spec.square() && depth != width) {
                                continue;
                            }
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

    // Distances (1..limit) from pos along direction where a casing is.
    private List<Integer> casings(Level level, BlockPos pos, Direction direction, int limit) {
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
    public boolean isValid(Level level, Box box, BlockPos controller, @Nullable Box current) {
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
                fits = pos.equals(controller) ? isController(state) : (isCasing(state) || isGlass(state)) && free;
            } else {
                fits = spec.fill().test(state);
            }
            if (!fits) {
                return false;
            }
        }
        return spec.check().test(level, box);
    }

    // --- Forming and breaking ---

    public void form(ServerLevel level, Box box, BlockPos controller) {
        formInterior(level, box, true);
        for (BlockPos pos : box.positions()) {
            if (box.boundaries(pos) == 0) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state;
            if (state.hasProperty(VesselPart.FORMED)) {
                formed = state.setValue(VesselPart.FORMED, true);
                if (immutable.equals(controller) && formed.hasProperty(VesselControllerBlock.FACING)) {
                    formed = formed.setValue(VesselControllerBlock.FACING, box.front());
                }
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // Sets FORMED on the blocks inside the box (VesselInteriorBlock: they hide while the vessel is drawn round them),
    // or clears it.
    public void formInterior(ServerLevel level, Box box, boolean formed) {
        for (BlockPos pos : box.interior()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof VesselInteriorBlock && state.hasProperty(VesselPart.FORMED) && state.getValue(VesselPart.FORMED) != formed) {
                level.setBlock(pos.immutable(), state.setValue(VesselPart.FORMED, formed), Block.UPDATE_CLIENTS);
            }
        }
    }

    // Un-forms every wall block of the box but skip (a block being broken), and shows what's inside again.
    public void unform(ServerLevel level, Box box, @Nullable BlockPos skip) {
        formInterior(level, box, false);
        for (BlockPos pos : box.positions()) {
            if (box.boundaries(pos) == 0) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            if (isFormed(state) && (isCasing(state) || isGlass(state) || isController(state))) {
                BlockState loose = state.setValue(VesselPart.FORMED, false);
                if (loose.hasProperty(VesselControllerBlock.LIT)) {
                    loose = loose.setValue(VesselControllerBlock.LIT, false);
                }
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
    }

    // --- Finding the controller from any part ---

    // The formed controller whose box holds pos (a wall block or anything inside), or null.
    public @Nullable VesselControllerBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (VesselControllerBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            if (box != null && box.contains(pos)) {
                return controller;
            }
        }
        return null;
    }

    // Something near pos was placed, broken or wrenched: every controller whose box could include it rechecks next
    // tick.
    public void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, VesselControllerBlockEntity::requestCheck);
        }
    }

    private void forEachNearbyController(Level level, BlockPos pos, Consumer<VesselControllerBlockEntity> action) {
        int reach = Math.max(spec.maxWidth(), spec.maxHeight());
        for (VesselControllerBlockEntity controller : controllers(level)) {
            Box box = controller.getBox();
            BlockPos at = controller.getBlockPos();
            boolean near = box != null ? box.aabb().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < reach && Math.abs(pos.getZ() - at.getZ()) < reach && Math.abs(pos.getY() - at.getY()) < reach;
            if (near) {
                action.accept(controller);
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a wall block of a formed box opens it.
    public InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        VesselControllerBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a part: nearby controllers recheck, and the player hears whether it's formed.
    public InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        forEachNearbyController(level, pos, VesselControllerBlockEntity::checkNow);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge." + spec.id() + ".formed" : "message.arcforge." + spec.id() + ".incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
