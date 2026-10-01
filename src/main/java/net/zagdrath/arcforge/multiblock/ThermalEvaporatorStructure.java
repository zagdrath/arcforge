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
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorCasingBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorControllerBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorPart;
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;

// The Thermal Evaporator Array: a fixed 3x3x9 tower, like Mekanism's Thermal Evaporation Plant.
//  - Bottom layer: a solid 3x3 of Thermal Evaporator Casings, with the Thermal Evaporator Controller in the middle of one
//    side, facing out.
//  - Layers 2 to 8: a ring of 8 blocks round a hollow 1x1 core of air. The corners are casings; the middle block of each
//    side is a casing or Pressure Glass, so a window can run up any side.
//  - Top layer: a solid 3x3 cap of casings.
// The controller's block entity runs it and decides whether it is formed; casings and panes find the controller by
// searching around themselves. FORMED is set on every casing, the controller and every pane of a formed tower.
public final class ThermalEvaporatorStructure {
    public static final int WIDTH = 3;
    public static final int HEIGHT = 9;

    // A formed or candidate tower: its minimum corner, and the side the controller faces out of.
    public record Tower(BlockPos min, Direction front) {
        public BlockPos max() {
            return min.offset(WIDTH - 1, HEIGHT - 1, WIDTH - 1);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + WIDTH
                    && pos.getY() >= min.getY() && pos.getY() < min.getY() + HEIGHT
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + WIDTH;
        }

        // The hollow core: the middle column of layers 2 to 8.
        public boolean isCore(BlockPos pos) {
            int dy = pos.getY() - min.getY();
            return pos.getX() == min.getX() + 1 && pos.getZ() == min.getZ() + 1 && dy >= 1 && dy <= HEIGHT - 2;
        }

        // Where a window may go: the middle block of a side in layers 2 to 8.
        public boolean isWindowSlot(BlockPos pos) {
            int dx = pos.getX() - min.getX(), dy = pos.getY() - min.getY(), dz = pos.getZ() - min.getZ();
            return dy >= 1 && dy <= HEIGHT - 2 && (dx == 1) != (dz == 1);
        }

        // The bottom of the core, where the salt bed lies, and its top.
        public BlockPos coreBottom() {
            return min.offset(1, 1, 1);
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }
    }

    private ThermalEvaporatorStructure() {}

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof ThermalEvaporatorPart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(ThermalEvaporatorPart.FORMED);
    }

    // --- Finding the tower round a controller ---

    // The valid tower the controller at pos heads, or null: the tower behind it, trying the way it faces first, then the
    // other three sides. current: the tower it has now (its blocks may still be formed).
    public static @Nullable Tower find(Level level, BlockPos controller, @Nullable Tower current) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof ThermalEvaporatorControllerBlock)) {
            return null;
        }
        Direction facing = state.getValue(ThermalEvaporatorControllerBlock.FACING);
        for (Direction front : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
            BlockPos centre = controller.relative(front.getOpposite());
            Tower tower = new Tower(centre.offset(-1, 0, -1), front);
            if (isValid(level, tower, controller, current)) {
                return tower;
            }
        }
        return null;
    }

    private static boolean isValid(Level level, Tower tower, BlockPos controller, @Nullable Tower current) {
        for (BlockPos pos : tower.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (tower.isCore(pos)) {
                if (!state.isAir()) {
                    return false;
                }
                continue;
            }
            boolean fits;
            if (pos.equals(controller)) {
                fits = state.getBlock() instanceof ThermalEvaporatorControllerBlock;
            } else if (tower.isWindowSlot(pos)) {
                fits = state.getBlock() instanceof ThermalEvaporatorCasingBlock || state.getBlock() instanceof PressureGlassBlock;
            } else {
                fits = state.getBlock() instanceof ThermalEvaporatorCasingBlock;
            }
            if (!fits) {
                return false;
            }
            // Not already part of another structure.
            boolean formed = isFormedPart(state) || PressureGlassBlock.isFormed(state);
            if (formed && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Tower tower, BlockPos controller) {
        for (BlockPos pos : tower.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state;
            if (isPart(state)) {
                formed = state.setValue(ThermalEvaporatorPart.FORMED, true);
                if (immutable.equals(controller)) {
                    formed = formed.setValue(ThermalEvaporatorControllerBlock.FACING, tower.front());
                }
            } else if (state.getBlock() instanceof PressureGlassBlock) {
                formed = state.setValue(PressureGlassBlock.FORMED, true);
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, tower.min(), tower.max());
    }

    public static void unform(ServerLevel level, Tower tower) {
        unform(level, tower, null);
    }

    // As unform, leaving the block at skip (a controller being broken) alone.
    public static void unform(ServerLevel level, Tower tower, @Nullable BlockPos skip) {
        for (BlockPos pos : tower.positions()) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(skip)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            BlockState loose = state;
            if (isFormedPart(state)) {
                loose = state.setValue(ThermalEvaporatorPart.FORMED, false);
                if (loose.hasProperty(ThermalEvaporatorControllerBlock.LIT)) {
                    loose = loose.setValue(ThermalEvaporatorControllerBlock.LIT, false);
                }
            } else if (PressureGlassBlock.isFormed(state) && tower.isWindowSlot(immutable)) {
                loose = state.setValue(PressureGlassBlock.FORMED, false);
            }
            if (loose != state) {
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, tower.min(), tower.max());
    }

    // --- Finding the controller from any block ---

    // The formed tower's controller that the casing or pane at pos belongs to, or null.
    public static @Nullable ThermalEvaporatorBlockEntity findController(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isFormedPart(state) && !PressureGlassBlock.isFormed(state)) {
            return null;
        }
        ThermalEvaporatorBlockEntity[] found = new ThermalEvaporatorBlockEntity[1];
        forEachNearbyController(level, pos, controller -> {
            if (found[0] == null && controller.getTower() != null && controller.getTower().contains(pos)) {
                found[0] = controller;
            }
        });
        return found[0];
    }

    // A block near pos was placed, broken or wrenched: every controller whose tower could include it rechecks next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, ThermalEvaporatorBlockEntity::requestCheck);
        }
    }

    // Controllers of any tower pos could be in: a controller sits in the bottom layer, so at most two blocks away across
    // and from level with pos to eight blocks below it.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<ThermalEvaporatorBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -(HEIGHT - 1); dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidate.setWithOffset(pos, dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof ThermalEvaporatorControllerBlock
                            && level.getBlockEntity(candidate) instanceof ThermalEvaporatorBlockEntity controller) {
                        action.accept(controller);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a casing or pane of a formed tower opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        ThermalEvaporatorBlockEntity controller = findController(level, pos);
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
        forEachNearbyController(level, pos, ThermalEvaporatorBlockEntity::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.thermal_evaporator.formed" : "message.arcforge.thermal_evaporator.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
