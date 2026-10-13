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
import net.zagdrath.arcforge.block.multiblock.ColumnPart;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;

// The Distillation Array: a solid column 2x2 or 3x3 across and 4 to 16 tall, of Distillation Array Casings and Tray
// Level Casings with exactly one Distillation Array Controller. The controller and the trays can't be in the top or
// bottom layer, and the controller needs a side on the outside (not the middle of a 3x3). The controller's block entity
// runs it and decides whether it is formed: the column is the full layers above and below it (so stacking more casings
// on a formed column makes it taller, up to 16). A 3x3 of parts round the controller is always read as a 3x3, never as
// a 2x2 inside it. Other parts find the controller by searching around themselves. Formed, the column draws as a round
// refinery tower (see DistillationArrayRenderer) and its blocks' shapes follow it (see RefineryLayout).
public final class DistillationStructure {
    public static final int MIN_WIDTH = 2, MAX_WIDTH = 3;
    public static final int MIN_HEIGHT = 4, MAX_HEIGHT = 16;

    public record Column(BlockPos min, int width, int height) {
        public BlockPos max() {
            return min.offset(width - 1, height - 1, width - 1);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + width
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + width
                    && pos.getY() >= min.getY() && pos.getY() < min.getY() + height;
        }

        // Layer of pos, 0 at the bottom.
        public int layer(BlockPos pos) {
            return pos.getY() - min.getY();
        }

        public boolean isEndLayer(BlockPos pos) {
            return layer(pos) == 0 || layer(pos) == height - 1;
        }

        // Whether pos has a side on the outside of the column (every block of a 2x2 does; the middle of a 3x3 doesn't).
        public boolean isOuter(BlockPos pos) {
            int x = pos.getX() - min.getX(), z = pos.getZ() - min.getZ();
            return x == 0 || z == 0 || x == width - 1 || z == width - 1;
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }
    }

    private DistillationStructure() {}

    public static boolean isHeight(int height) {
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof ColumnPart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(ColumnPart.FORMED);
    }

    // --- Finding the column around a controller ---

    // The valid column the controller at pos is in, or null. current: the column it has now (its blocks may still be
    // formed). A 3x3 is tried first; where a full 3x3 layer of parts surrounds the controller (with no other controller
    // in it, and none of another column's formed blocks), only a 3x3 will do, so a 3x3 being built doesn't form as a
    // 2x2. 2x2 columns packed side by side still form, each round its own controller.
    public static @Nullable Column find(Level level, BlockPos controller, @Nullable Column current) {
        boolean wide = false;
        for (int width = MAX_WIDTH; width >= MIN_WIDTH; width--) {
            for (int dx = -(width - 1); dx <= 0; dx++) {
                for (int dz = -(width - 1); dz <= 0; dz++) {
                    int x = controller.getX() + dx;
                    int z = controller.getZ() + dz;
                    if (!isFullLayer(level, x, controller.getY(), z, width)) {
                        continue;
                    }
                    wide |= width == MAX_WIDTH && isOwnLayer(level, x, controller.getY(), z, width, controller, current);
                    int bottom = controller.getY();
                    while (bottom > controller.getY() - MAX_HEIGHT && isFullLayer(level, x, bottom - 1, z, width)) {
                        bottom--;
                    }
                    int top = controller.getY();
                    while (top < controller.getY() + MAX_HEIGHT && isFullLayer(level, x, top + 1, z, width)) {
                        top++;
                    }
                    Column column = new Column(new BlockPos(x, bottom, z), width, top - bottom + 1);
                    if (isHeight(column.height()) && isValid(level, column, controller, current)) {
                        return column;
                    }
                }
            }
            if (wide) {
                return null;
            }
        }
        return null;
    }

    private static boolean isFullLayer(Level level, int x, int y, int z, int width) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                if (!level.isLoaded(pos) || !isPart(level.getBlockState(pos))) {
                    return false;
                }
            }
        }
        return true;
    }

    // Whether a full layer could only be this controller's column: no other controller and no other column's formed
    // block.
    private static boolean isOwnLayer(Level level, int x, int y, int z, int width, BlockPos controller, @Nullable Column current) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DistillationArrayControllerBlock && !pos.equals(controller)) {
                    return false;
                }
                if (state.getValue(ColumnPart.FORMED) && (current == null || !current.contains(pos))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValid(Level level, Column column, BlockPos controller, @Nullable Column current) {
        if (!column.isOuter(controller)) {
            return false;
        }
        for (BlockPos pos : column.positions()) {
            BlockState state = level.getBlockState(pos);
            boolean isController = state.getBlock() instanceof DistillationArrayControllerBlock;
            if (isController && !pos.equals(controller)) {
                return false;
            }
            if ((isController || state.getBlock() instanceof TrayLevelCasingBlock) && column.isEndLayer(pos)) {
                return false;
            }
            // Not already part of another column.
            if (state.getValue(ColumnPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // --- Forming and breaking ---

    public static void form(ServerLevel level, Column column) {
        for (BlockPos pos : column.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            BlockState formed = state.setValue(ColumnPart.FORMED, true);
            if (state.getBlock() instanceof TrayLevelCasingBlock) {
                formed = formed.setValue(TrayLevelCasingBlock.FRACTION, TrayLevelCasingBlock.Fraction.at(column.layer(immutable),
                        Math.min(column.height(), 8)));
            }
            if (state.getBlock() instanceof DistillationArrayControllerBlock) {
                formed = formed.setValue(DistillationArrayControllerBlock.FACING, outward(column, immutable, state.getValue(DistillationArrayControllerBlock.FACING)));
            }
            if (formed != state) {
                level.setBlock(immutable, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, column.min(), column.max());
    }

    public static void unform(ServerLevel level, Column column) {
        unform(level, column, null);
    }

    // As unform, leaving the block at skip (a controller being broken) alone.
    public static void unform(ServerLevel level, Column column, @Nullable BlockPos skip) {
        for (BlockPos pos : column.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (!immutable.equals(skip) && isFormedPart(state)) {
                BlockState loose = state.setValue(ColumnPart.FORMED, false);
                if (loose.hasProperty(ColumnPart.LIT)) {
                    loose = loose.setValue(ColumnPart.LIT, false);
                }
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, column.min(), column.max());
    }

    // A facing that points out of the column: a facing into it turns round, and on a 3x3's side (with one outward side)
    // to that side.
    public static Direction outward(Column column, BlockPos pos, Direction facing) {
        for (Direction candidate : new Direction[] { facing, facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise() }) {
            if (!column.contains(pos.relative(candidate))) {
                return candidate;
            }
        }
        return facing;
    }

    // Tray and controller lights follow whether the column is running.
    public static void setLit(ServerLevel level, Column column, boolean lit) {
        for (BlockPos pos : column.positions()) {
            BlockState state = level.getBlockState(pos);
            if (isFormedPart(state) && state.hasProperty(ColumnPart.LIT) && state.getValue(ColumnPart.LIT) != lit) {
                level.setBlock(pos.immutable(), state.setValue(ColumnPart.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    // --- Finding the controller from any part ---

    public static @Nullable DistillationArrayBlockEntity findController(BlockGetter level, BlockPos pos) {
        if (!isFormedPart(level.getBlockState(pos))) {
            return null;
        }
        DistillationArrayBlockEntity[] found = new DistillationArrayBlockEntity[1];
        forEachNearbyController(level, pos, controller -> {
            if (found[0] == null && controller.isFormed() && controller.isPart(pos)) {
                found[0] = controller;
            }
        });
        return found[0];
    }

    // A part near pos was placed, broken or wrenched: every controller whose column could include it
    // rechecks next tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, DistillationArrayBlockEntity::requestCheck);
        }
    }

    // Controllers in any column pos could be part of: up to two blocks to each side, up to 15 above or below.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<DistillationArrayBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -(MAX_HEIGHT - 1); dy <= MAX_HEIGHT - 1; dy++) {
            for (int dx = -(MAX_WIDTH - 1); dx <= MAX_WIDTH - 1; dx++) {
                for (int dz = -(MAX_WIDTH - 1); dz <= MAX_WIDTH - 1; dz++) {
                    candidate.setWithOffset(pos, dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof DistillationArrayControllerBlock
                            && level.getBlockEntity(candidate) instanceof DistillationArrayBlockEntity controller) {
                        action.accept(controller);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed column opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        DistillationArrayBlockEntity controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(controller, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a casing or tray: nearby controllers recheck and report.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        forEachNearbyController(level, pos, DistillationArrayBlockEntity::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.distillation_array.formed" : "message.arcforge.distillation_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
