/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

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
import net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock;
import net.zagdrath.arcforge.block.multiblock.DigesterCasingBlock;
import net.zagdrath.arcforge.block.multiblock.DigesterPart;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;

// The Biogas Digester: a solid 3x3x3 tank of Digester Casings with exactly one Biogas Digester Controller, in the middle
// of one of its four sides (the middle block of the middle layer), facing out. The controller's block entity runs it
// and decides whether it is formed; the other parts find the controller by searching around themselves.
public final class BiogasDigesterStructure {
    public static final int SIZE = 3;

    // A formed or candidate tank: its minimum corner, and the side the controller faces out of.
    public record Tank(BlockPos min, Direction front) {
        public BlockPos max() {
            return min.offset(SIZE - 1, SIZE - 1, SIZE - 1);
        }

        public BlockPos center() {
            return min.offset(1, 1, 1);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + SIZE
                    && pos.getY() >= min.getY() && pos.getY() < min.getY() + SIZE
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + SIZE;
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }
    }

    private BiogasDigesterStructure() {}

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof DigesterPart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(DigesterPart.FORMED);
    }

    // --- Finding the tank round a controller ---

    // The valid tank the controller at pos is in, or null: the 3x3x3 behind it, trying the way it faces first, then
    // the other three sides. current: the tank it has now (its blocks may still be formed).
    public static @Nullable Tank find(Level level, BlockPos controller, @Nullable Tank current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof BiogasDigesterControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(BiogasDigesterControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            BlockPos center = controller.relative(front.getOpposite());
            Tank tank = new Tank(center.offset(-1, -1, -1), front);
            if (isValid(level, tank, controller, current)) {
                return tank;
            }
        }
        return null;
    }

    private static boolean isValid(Level level, Tank tank, BlockPos controller, @Nullable Tank current) {
        for (BlockPos pos : tank.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean fits = pos.equals(controller) ? state.getBlock() instanceof BiogasDigesterControllerBlock
                    : state.getBlock() instanceof DigesterCasingBlock;
            if (!fits) {
                return false;
            }
            // Not already part of another tank.
            if (state.getValue(DigesterPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Tank tank, BlockPos controller) {
        for (BlockPos pos : tank.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state.setValue(DigesterPart.FORMED, true);
            if (immutable.equals(controller)) {
                formed = formed.setValue(BiogasDigesterControllerBlock.FACING, tank.front());
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, tank.min(), tank.max());
    }

    public static void unform(ServerLevel level, Tank tank) {
        unform(level, tank, null);
    }

    // As unform, leaving the block at skip (a controller being broken) alone.
    public static void unform(ServerLevel level, Tank tank, @Nullable BlockPos skip) {
        for (BlockPos pos : tank.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (!immutable.equals(skip) && isFormedPart(state)) {
                BlockState loose = state.setValue(DigesterPart.FORMED, false);
                if (loose.hasProperty(BiogasDigesterControllerBlock.LIT)) {
                    loose = loose.setValue(BiogasDigesterControllerBlock.LIT, false);
                }
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, tank.min(), tank.max());
    }

    // --- Finding the controller from any part ---

    public static @Nullable BiogasDigesterBlockEntity findController(BlockGetter level, BlockPos pos) {
        if (!isFormedPart(level.getBlockState(pos))) {
            return null;
        }
        BiogasDigesterBlockEntity[] found = new BiogasDigesterBlockEntity[1];
        forEachNearbyController(level, pos, controller -> {
            if (found[0] == null && controller.isFormed() && controller.isPart(pos)) {
                found[0] = controller;
            }
        });
        return found[0];
    }

    // A part near pos was placed, broken or wrenched: every controller whose tank could include it rechecks next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, BiogasDigesterBlockEntity::requestCheck);
        }
    }

    // Controllers of any tank pos could be part of: a controller sits in the middle layer of a side, so at most two
    // blocks away across and one up or down.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<BiogasDigesterBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidate.setWithOffset(pos, dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof BiogasDigesterControllerBlock
                            && level.getBlockEntity(candidate) instanceof BiogasDigesterBlockEntity controller) {
                        action.accept(controller);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed tank opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        BiogasDigesterBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a casing: nearby controllers recheck, and the player hears whether it's formed.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        forEachNearbyController(level, pos, BiogasDigesterBlockEntity::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.biogas_digester.formed" : "message.arcforge.biogas_digester.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
