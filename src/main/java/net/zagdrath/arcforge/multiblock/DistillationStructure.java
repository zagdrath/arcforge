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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

// The Distillation Array: a solid column exactly 2x2 across and 4, 6 or 8 tall, of Distillation Array
// Casings and Tray Level Casings with exactly one Distillation Array Controller. The controller and the
// trays can't be in the top or bottom layer. The controller's block entity runs it and decides whether it
// is formed: the column is the full 2x2 layers above and below it (so stacking more casings on a formed
// column breaks it until it reaches the next height). Other parts find the controller by searching around
// themselves.
public final class DistillationStructure {
    public static final int WIDTH = 2;
    public static final int MAX_HEIGHT = 8;
    private static final int[] HEIGHTS = { 4, 6, 8 };

    public record Column(BlockPos min, int height) {
        public BlockPos max() {
            return min.offset(WIDTH - 1, height - 1, WIDTH - 1);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + WIDTH
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + WIDTH
                    && pos.getY() >= min.getY() && pos.getY() < min.getY() + height;
        }

        // Layer of pos, 0 at the bottom.
        public int layer(BlockPos pos) {
            return pos.getY() - min.getY();
        }

        public boolean isEndLayer(BlockPos pos) {
            return layer(pos) == 0 || layer(pos) == height - 1;
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }
    }

    private DistillationStructure() {}

    public static boolean isHeight(int height) {
        for (int allowed : HEIGHTS) {
            if (allowed == height) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof ColumnPart;
    }

    public static boolean isFormedPart(BlockState state) {
        return isPart(state) && state.getValue(ColumnPart.FORMED);
    }

    // --- Finding the column around a controller ---

    // The valid column the controller at pos is in, or null. current: the column it has now (its blocks
    // may still be formed).
    public static @Nullable Column find(Level level, BlockPos controller, @Nullable Column current) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int x = controller.getX() + dx;
                int z = controller.getZ() + dz;
                if (!isFullLayer(level, x, controller.getY(), z)) {
                    continue;
                }
                int bottom = controller.getY();
                while (bottom > controller.getY() - MAX_HEIGHT && isFullLayer(level, x, bottom - 1, z)) {
                    bottom--;
                }
                int top = controller.getY();
                while (top < controller.getY() + MAX_HEIGHT && isFullLayer(level, x, top + 1, z)) {
                    top++;
                }
                Column column = new Column(new BlockPos(x, bottom, z), top - bottom + 1);
                if (isHeight(column.height()) && isValid(level, column, controller, current)) {
                    return column;
                }
            }
        }
        return null;
    }

    private static boolean isFullLayer(Level level, int x, int y, int z) {
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < WIDTH; dz++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                if (!level.isLoaded(pos) || !isPart(level.getBlockState(pos))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValid(Level level, Column column, BlockPos controller, @Nullable Column current) {
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
                formed = formed.setValue(TrayLevelCasingBlock.FRACTION, TrayLevelCasingBlock.Fraction.at(column.layer(immutable), column.height()));
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
        for (BlockPos pos : column.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (isFormedPart(state)) {
                BlockState loose = state.setValue(ColumnPart.FORMED, false);
                if (loose.hasProperty(ColumnPart.LIT)) {
                    loose = loose.setValue(ColumnPart.LIT, false);
                }
                level.setBlock(immutable, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, column.min(), column.max());
    }

    // A facing that points out of the column: each block of a 2x2 has one outward side on each axis, so a
    // facing into the column turns round.
    public static Direction outward(Column column, BlockPos pos, Direction facing) {
        BlockPos next = pos.relative(facing);
        return column.contains(next) ? facing.getOpposite() : facing;
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

    // Controllers in any column pos could be part of: one block to each side, up to 7 above or below.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<DistillationArrayBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -(MAX_HEIGHT - 1); dy <= MAX_HEIGHT - 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
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

    // Wrench on a casing or tray: sneak picks it up, otherwise nearby controllers recheck and report.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            level.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }
        forEachNearbyController(level, pos, DistillationArrayBlockEntity::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.distillation_array.formed" : "message.arcforge.distillation_array.incomplete"));
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.6F);
        return InteractionResult.SUCCESS;
    }
}
