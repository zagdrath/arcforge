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
import net.zagdrath.arcforge.block.multiblock.SolarCollectorBlock;
import net.zagdrath.arcforge.block.multiblock.SolarPart;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;

// The Solar Thermal Array: a 2x2 tower exactly 4 tall. Its first three layers are Solar Thermal Array Casings
// with exactly one controller, in the bottom layer and facing out of the tower; the top layer is four Solar
// Collectors. The controller's block entity runs it and decides whether it's formed; the controller's
// facing axis is the trough's tracking axis (north-south or east-west), fixed when it forms.
public final class SolarThermalStructure {
    public static final int WIDTH = 2;
    public static final int HEIGHT = 4;

    // min: the bottom layer's minimum corner.
    public record Tower(BlockPos min, Direction.Axis axis) {
        public BlockPos max() {
            return min.offset(WIDTH - 1, HEIGHT - 1, WIDTH - 1);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + WIDTH
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + WIDTH
                    && pos.getY() >= min.getY() && pos.getY() < min.getY() + HEIGHT;
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }

        // The collectors, west to east then north to south: north-west, north-east, south-west, south-east.
        public BlockPos collector(int index) {
            return min.offset(index % 2, HEIGHT - 1, index / 2);
        }

        // Where the mast and yoke are drawn from.
        public BlockPos upper() {
            return min.above(2);
        }

        public boolean northSouth() {
            return axis == Direction.Axis.Z;
        }
    }

    // Why a controller's tower isn't formed.
    public enum Problem {
        NONE("formed"), INCOMPLETE("incomplete"), FACING_IN("facing_in");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        public Component message() {
            return Component.translatable("message.arcforge.solar_thermal_array." + key);
        }
    }

    public record Found(@Nullable Tower tower, Problem problem) {}

    private SolarThermalStructure() {}

    public static boolean isFormedPart(BlockState state) {
        return SolarPart.isFormed(state);
    }

    // --- Finding the tower round a controller ---

    // The valid tower the controller at pos (in its bottom layer) is in. current: the tower it has now.
    public static Found find(Level level, BlockPos controller, @Nullable Tower current) {
        BlockState controllerState = level.getBlockState(controller);
        if (!(controllerState.getBlock() instanceof SolarThermalArrayControllerBlock)) {
            return new Found(null, Problem.INCOMPLETE);
        }
        Direction facing = controllerState.getValue(SolarThermalArrayControllerBlock.FACING);
        boolean facingIn = false;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                Tower tower = new Tower(controller.offset(dx, 0, dz), facing.getAxis());
                if (!isValid(level, tower, controller, current)) {
                    continue;
                }
                if (tower.contains(controller.relative(facing))) {
                    facingIn = true;
                    continue;
                }
                return new Found(tower, Problem.NONE);
            }
        }
        return new Found(null, facingIn ? Problem.FACING_IN : Problem.INCOMPLETE);
    }

    private static boolean isValid(Level level, Tower tower, BlockPos controller, @Nullable Tower current) {
        for (BlockPos pos : tower.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            int layer = pos.getY() - tower.min().getY();
            boolean fits = layer == HEIGHT - 1 ? state.getBlock() instanceof SolarCollectorBlock
                    : pos.equals(controller) ? state.getBlock() instanceof SolarThermalArrayControllerBlock
                    : state.getBlock() instanceof SolarThermalArrayCasingBlock;
            if (!fits) {
                return false;
            }
            // Not already part of another tower.
            if (state.getValue(SolarPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Tower tower) {
        for (BlockPos pos : tower.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state.setValue(SolarPart.FORMED, true);
            if (state.getBlock() instanceof SolarThermalArrayCasingBlock) {
                formed = formed.setValue(SolarThermalArrayCasingBlock.PART, immutable.equals(tower.min()) ? SolarThermalArrayCasingBlock.Part.BASE
                        : immutable.equals(tower.upper()) ? (tower.northSouth() ? SolarThermalArrayCasingBlock.Part.UPPER_NS : SolarThermalArrayCasingBlock.Part.UPPER_EW)
                        : SolarThermalArrayCasingBlock.Part.OTHER);
            }
            if (state.getBlock() instanceof SolarThermalArrayControllerBlock) {
                formed = formed.setValue(SolarThermalArrayControllerBlock.BASE, immutable.equals(tower.min()));
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, tower.min(), tower.max());
    }

    public static void unform(ServerLevel level, Tower tower) {
        for (BlockPos pos : tower.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (!isFormedPart(state)) {
                continue;
            }
            BlockState loose = state.setValue(SolarPart.FORMED, false);
            if (loose.hasProperty(SolarThermalArrayCasingBlock.PART)) {
                loose = loose.setValue(SolarThermalArrayCasingBlock.PART, SolarThermalArrayCasingBlock.Part.NONE);
            }
            if (loose.hasProperty(SolarThermalArrayControllerBlock.BASE)) {
                loose = loose.setValue(SolarThermalArrayControllerBlock.BASE, false).setValue(SolarThermalArrayControllerBlock.LIT, false);
            }
            level.setBlock(immutable, loose, Block.UPDATE_ALL);
        }
        MultiblockAutomation.refresh(level, tower.min(), tower.max());
    }

    // --- Finding the controller from any part ---

    public static @Nullable SolarThermalArrayBlockEntity findController(BlockGetter level, BlockPos pos) {
        if (!isFormedPart(level.getBlockState(pos))) {
            return null;
        }
        SolarThermalArrayBlockEntity[] found = new SolarThermalArrayBlockEntity[1];
        forEachNearbyController(level, pos, controller -> {
            if (found[0] == null && controller.isFormed() && controller.isPart(pos)) {
                found[0] = controller;
            }
        });
        return found[0];
    }

    // A part near pos was placed, broken or wrenched: every controller whose tower could include it
    // rechecks next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, SolarThermalArrayBlockEntity::requestCheck);
        }
    }

    // Controllers of any tower pos could be part of: one block to each side, up to three below.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<SolarThermalArrayBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -(HEIGHT - 1); dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidate.setWithOffset(pos, dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof SolarThermalArrayControllerBlock
                            && level.getBlockEntity(candidate) instanceof SolarThermalArrayBlockEntity controller) {
                        action.accept(controller);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed tower opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        SolarThermalArrayBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a casing or collector: nearby controllers recheck and report.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        SolarThermalArrayBlockEntity[] reported = new SolarThermalArrayBlockEntity[1];
        forEachNearbyController(level, pos, controller -> {
            controller.checkNow();
            if (reported[0] == null || controller.isFormed()) {
                reported[0] = controller;
            }
        });
        if (reported[0] != null) {
            report(context.getPlayer(), reported[0]);
        } else if (context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(Problem.INCOMPLETE.message());
        }
        return InteractionResult.SUCCESS;
    }

    // Tells the player whether the controller's tower is formed, or why not.
    public static void report(@Nullable Player player, SolarThermalArrayBlockEntity controller) {
        if (player != null) {
            player.sendOverlayMessage(controller.getProblem().message());
        }
    }
}
