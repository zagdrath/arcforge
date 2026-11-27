/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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
import net.zagdrath.arcforge.block.fusion.FusionReactorControllerBlock;
import net.zagdrath.arcforge.block.fusion.TokamakPartBlock;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.registry.ModBlocks;

// The Fusion Reactor's structure (see TokamakLayout for its shape): found from its controller, which sits in the
// cryostat's wall at the midplane, straight out from the axis (in the middle of a side), facing out. Every controller is
// kept in a per-level registry, so the other parts find it without searching. A tokamak has a Central Solenoid up its
// axis and Toroidal Field Coils round it; a stellarator has Stellarator Coils in both places (the block on the axis at the
// midplane says which). FORMED is set on every block of a formed reactor; the controller's renderer draws it then.
public final class TokamakStructure {
    // A formed or candidate reactor: its axis at the midplane, its major radius, whether it's a stellarator, and the way
    // its controller faces.
    public record Shape(BlockPos axis, int radius, boolean stellarator, Direction front) {
        public TokamakLayout layout() {
            return new TokamakLayout(radius);
        }

        public TokamakLayout.Part partAt(BlockPos pos) {
            return layout().at(pos.getX() - axis.getX(), pos.getY() - axis.getY(), pos.getZ() - axis.getZ());
        }

        public boolean contains(BlockPos pos) {
            return partAt(pos) != TokamakLayout.Part.OUTSIDE;
        }

        public BlockPos min() {
            TokamakLayout layout = layout();
            return axis.offset(-layout.outer(), -layout.top(), -layout.outer());
        }

        public BlockPos max() {
            TokamakLayout layout = layout();
            return axis.offset(layout.outer(), layout.top(), layout.outer());
        }

        public AABB aabb() {
            BlockPos min = min(), max = max();
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }

        // Every position of the reactor's bounding box.
        public Iterable<BlockPos> box() {
            return BlockPos.betweenClosed(min(), max());
        }

        // The horizontal side a cryostat wall block faces out of (the axis direction it's furthest along).
        public Direction outward(BlockPos pos) {
            int dx = pos.getX() - axis.getX(), dz = pos.getZ() - axis.getZ();
            if (Math.abs(dx) >= Math.abs(dz)) {
                return dx >= 0 ? Direction.EAST : Direction.WEST;
            }
            return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // "R7 tokamak" style size text: the major radius and footprint.
        public String sizeText() {
            TokamakLayout layout = layout();
            return layout.width() + "×" + layout.height() + "×" + layout.width();
        }
    }

    private static final Map<Level, Set<FusionReactorBlockEntity>> SERVER = new WeakHashMap<>();
    private static final Map<Level, Set<FusionReactorBlockEntity>> CLIENT = new WeakHashMap<>();

    private TokamakStructure() {}

    // --- The registry of controllers ---

    private static Map<Level, Set<FusionReactorBlockEntity>> registry(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    public static void register(FusionReactorBlockEntity controller) {
        Level level = controller.getLevel();
        if (level != null) {
            registry(level).computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        }
    }

    public static void unregister(FusionReactorBlockEntity controller) {
        Level level = controller.getLevel();
        Set<FusionReactorBlockEntity> set = level != null ? registry(level).get(level) : null;
        if (set != null) {
            set.remove(controller);
        }
    }

    public static List<FusionReactorBlockEntity> controllers(BlockGetter level) {
        Set<FusionReactorBlockEntity> set = level instanceof Level key ? registry(key).get(key) : null;
        if (set == null) {
            return List.of();
        }
        List<FusionReactorBlockEntity> live = new ArrayList<>();
        for (FusionReactorBlockEntity controller : set) {
            if (!controller.isRemoved()) {
                live.add(controller);
            }
        }
        return live;
    }

    // --- What goes where ---

    // Whether a block fits a part of a tokamak (or a stellarator).
    public static boolean fits(TokamakLayout.Part part, BlockState state, boolean stellarator) {
        return switch (part) {
            case OUTSIDE -> true;
            case AIR -> state.isAir();
            case CENTRAL_SOLENOID -> state.is(stellarator ? ModBlocks.STELLARATOR_COIL.get() : ModBlocks.CENTRAL_SOLENOID.get());
            case FIRST_WALL -> state.is(ModBlocks.FIRST_WALL.get());
            case DIVERTOR -> state.is(ModBlocks.DIVERTOR_CASSETTE.get());
            case BLANKET -> state.is(ModBlocks.BREEDING_BLANKET.get());
            case TOROIDAL_COIL -> state.is(stellarator ? ModBlocks.STELLARATOR_COIL.get() : ModBlocks.TOROIDAL_FIELD_COIL.get());
            case POLOIDAL_COIL -> state.is(ModBlocks.POLOIDAL_FIELD_COIL.get());
            case CRYOSTAT_WALL -> state.is(ModBlocks.CRYOSTAT_CASING.get()) || state.is(ModBlocks.VESSEL_PORT.get())
                    || state.is(ModBlocks.REACTOR_GLASS.get());
            case CRYOSTAT_LID -> state.is(ModBlocks.CRYOSTAT_CASING.get());
        };
    }

    private static boolean isFormed(BlockState state) {
        return state.hasProperty(TokamakPartBlock.FORMED) && state.getValue(TokamakPartBlock.FORMED);
    }

    // The block that's always part of a reactor once it's formed: those carry FORMED.
    private static boolean isOwnPart(BlockState state) {
        return state.getBlock() instanceof TokamakPartBlock || state.getBlock() instanceof FusionReactorControllerBlock;
    }

    // --- Finding the reactor round a controller ---

    // The valid reactor the controller at pos is in, or null: its axis lies straight in from it, at each radius in turn.
    // current: the reactor it has now (its blocks may still be formed).
    public static @Nullable Shape find(Level level, BlockPos controller, @Nullable Shape current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof FusionReactorControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(FusionReactorControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            for (int r = TokamakLayout.MIN_RADIUS; r <= TokamakLayout.MAX_RADIUS; r++) {
                BlockPos axis = controller.relative(front.getOpposite(), new TokamakLayout(r).outer());
                if (!level.isLoaded(axis)) {
                    continue;
                }
                BlockState core = level.getBlockState(axis);
                boolean stellarator = core.is(ModBlocks.STELLARATOR_COIL.get());
                if (!stellarator && !core.is(ModBlocks.CENTRAL_SOLENOID.get())) {
                    continue;
                }
                Shape shape = new Shape(axis.immutable(), r, stellarator, front);
                if (isValid(level, shape, controller, current)) {
                    return shape;
                }
            }
        }
        return null;
    }

    // Whether every block of the reactor is what it should be. A block another reactor has claimed (formed, outside
    // current) doesn't count.
    public static boolean isValid(Level level, Shape shape, BlockPos controller, @Nullable Shape current) {
        if (!level.isLoaded(shape.min()) || !level.isLoaded(shape.max())) {
            return false;
        }
        if (shape.partAt(controller) != TokamakLayout.Part.CRYOSTAT_WALL || controller.getY() != shape.axis().getY()) {
            return false;
        }
        TokamakLayout layout = shape.layout();
        int ro = layout.outer(), top = layout.top();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -ro; dx <= ro; dx++) {
            for (int dz = -ro; dz <= ro; dz++) {
                int d = TokamakLayout.distance(dx, dz);
                if (d > ro) {
                    continue;
                }
                for (int y = -top; y <= top; y++) {
                    TokamakLayout.Part part = layout.at(d, y);
                    pos.set(shape.axis().getX() + dx, shape.axis().getY() + y, shape.axis().getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (pos.equals(controller)) {
                        continue;
                    }
                    if (!fits(part, state, shape.stellarator())) {
                        return false;
                    }
                    if (isFormed(state) && (current == null || !current.contains(pos))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Shape shape, BlockPos controller) {
        setFormed(level, shape, controller, null, true);
    }

    // Un-forms every block of the reactor but skip (a block being broken).
    public static void unform(ServerLevel level, Shape shape, @Nullable BlockPos skip) {
        setFormed(level, shape, null, skip, false);
    }

    private static void setFormed(ServerLevel level, Shape shape, @Nullable BlockPos controller, @Nullable BlockPos skip, boolean formed) {
        for (BlockPos pos : shape.box()) {
            TokamakLayout.Part part = shape.partAt(pos);
            if (part == TokamakLayout.Part.OUTSIDE || part == TokamakLayout.Part.AIR || pos.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isOwnPart(state) || !state.hasProperty(TokamakPartBlock.FORMED) || state.getValue(TokamakPartBlock.FORMED) == formed) {
                continue;
            }
            BlockState next = state.setValue(TokamakPartBlock.FORMED, formed);
            if (formed && pos.equals(controller) && next.hasProperty(FusionReactorControllerBlock.FACING)) {
                next = next.setValue(FusionReactorControllerBlock.FACING, shape.front());
            }
            if (!formed && next.hasProperty(FusionReactorControllerBlock.LIT)) {
                next = next.setValue(FusionReactorControllerBlock.LIT, false);
            }
            // The walls tell their neighbours (conduits, the modules); the inside only needs redrawing.
            boolean wall = part == TokamakLayout.Part.CRYOSTAT_WALL || part == TokamakLayout.Part.CRYOSTAT_LID;
            level.setBlock(pos.immutable(), next, wall ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        }
        MultiblockAutomation.refresh(level, shape.min(), shape.max());
    }

    // --- Finding the controller from any part ---

    // The formed controller whose reactor holds pos (any of its blocks, or the air inside), or null.
    public static @Nullable FusionReactorBlockEntity findController(BlockGetter level, BlockPos pos) {
        for (FusionReactorBlockEntity controller : controllers(level)) {
            Shape shape = controller.getShape();
            if (shape != null && shape.contains(pos)) {
                return controller;
            }
        }
        return null;
    }

    // The formed controller whose cryostat wall a block outside it touches (a module, a Remote Handling Arm), or null.
    public static @Nullable FusionReactorBlockEntity findAdjacent(BlockGetter level, BlockPos pos) {
        for (FusionReactorBlockEntity controller : controllers(level)) {
            Shape shape = controller.getShape();
            if (shape == null || shape.contains(pos)) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (shape.partAt(pos.relative(side)) == TokamakLayout.Part.CRYOSTAT_WALL) {
                    return controller;
                }
            }
        }
        return null;
    }

    // Something near pos was placed, broken or wrenched: every controller whose reactor could include it rechecks.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        int reach = TokamakLayout.MAX_RADIUS * 2 + 8;
        for (FusionReactorBlockEntity controller : controllers(level)) {
            Shape shape = controller.getShape();
            BlockPos at = controller.getBlockPos();
            boolean near = shape != null ? shape.aabb().inflate(1.0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    : Math.abs(pos.getX() - at.getX()) < reach && Math.abs(pos.getZ() - at.getZ()) < reach && Math.abs(pos.getY() - at.getY()) < reach;
            if (near) {
                controller.requestCheck();
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a block of a formed reactor opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        FusionReactorBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a part: nearby controllers recheck now, and the player hears whether it's formed.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int reach = TokamakLayout.MAX_RADIUS * 2 + 8;
        for (FusionReactorBlockEntity controller : controllers(level)) {
            BlockPos at = controller.getBlockPos();
            if (Math.abs(pos.getX() - at.getX()) < reach && Math.abs(pos.getZ() - at.getZ()) < reach && Math.abs(pos.getY() - at.getY()) < reach) {
                controller.checkNow();
            }
        }
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null ? "message.arcforge.fusion_reactor.formed"
                    : "message.arcforge.fusion_reactor.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
