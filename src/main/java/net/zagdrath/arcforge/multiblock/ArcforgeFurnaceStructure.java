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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.registry.ModBlocks;

// The Arcforge Furnace: a 3x3 cross-shaped column, 6 tall. Every layer has bricks on the four edge
// middles and brick walls on the four corners. The bottom layer is solid, with the port on its front
// edge (the port's facing is the furnace's front) and a brick hearth in the centre; the layers above
// have an empty centre (the stack, open at the top):
//
//     W B W        W B W
//     B B B        B . B      24 bricks + 1 port + 24 walls
//     W P W        W B W
//    bottom      layers 2-6
//
// The port's block entity runs the furnace; bricks and walls find it by searching around themselves.
public final class ArcforgeFurnaceStructure {
    public static final int HEIGHT = 6;
    private static final int SEARCH_RADIUS = 2;

    private ArcforgeFurnaceStructure() {}

    // The bottom-centre hearth brick, just behind the port; the hollow stack starts above it.
    public static BlockPos center(BlockPos port, Direction facing) {
        return port.relative(facing.getOpposite());
    }

    public static BlockPos minCorner(BlockPos port, Direction facing) {
        return center(port, facing).offset(-1, 0, -1);
    }

    public static BlockPos maxCorner(BlockPos port, Direction facing) {
        return center(port, facing).offset(1, HEIGHT - 1, 1);
    }

    // Whether pos is one of the structure's blocks (inside the box, not in the hollow stack).
    public static boolean isPart(BlockPos port, Direction facing, BlockPos pos) {
        BlockPos center = center(port, facing);
        int dx = pos.getX() - center.getX();
        int dy = pos.getY() - center.getY();
        int dz = pos.getZ() - center.getZ();
        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 0 && dy < HEIGHT && (dx != 0 || dz != 0 || dy == 0);
    }

    public static boolean isValid(Level level, BlockPos port, Direction facing) {
        return firstMismatch(level, port, facing) == null;
    }

    // The first position (bottom up) that doesn't hold what the pattern needs, or null if the furnace is complete.
    public static @Nullable BlockPos firstMismatch(Level level, BlockPos port, Direction facing) {
        BlockPos center = center(port, facing);
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos) || !matches(level.getBlockState(pos), dx, dy, dz, facing)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matches(BlockState state, int dx, int dy, int dz, Direction facing) {
        if (dx == 0 && dz == 0) {
            return dy == 0 ? state.is(ModBlocks.ARCFORGE_FURNACE_BRICKS.get()) : state.canBeReplaced();
        }
        if (dx != 0 && dz != 0) {
            return state.is(ModBlocks.ARCFORGE_FURNACE_BRICK_WALL.get());
        }
        if (dy == 0 && dx == facing.getStepX() && dz == facing.getStepZ()) {
            return state.is(ModBlocks.ARCFORGE_FURNACE_PORT.get()) && state.getValue(ArcforgeFurnacePortBlock.FACING) == facing;
        }
        return state.is(ModBlocks.ARCFORGE_FURNACE_BRICKS.get());
    }

    // The port of the formed furnace that the block at pos belongs to, if any.
    public static @Nullable ArcforgeFurnaceBlockEntity findFormedPort(Level level, BlockPos pos) {
        ArcforgeFurnaceBlockEntity[] found = new ArcforgeFurnaceBlockEntity[1];
        forEachNearbyPort(level, pos, port -> {
            if (found[0] == null && port.isFormed() && port.isPart(pos)) {
                found[0] = port;
            }
        });
        return found[0];
    }

    // A brick, wall or port near pos was placed, broken or wrenched: every port whose furnace could
    // include it rechecks its structure.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyPort(level, pos, ArcforgeFurnaceBlockEntity::requestCheck);
        }
    }

    // Ports in the bottom layer of any furnace pos could be part of: up to 5 blocks below, 2 to each side.
    private static void forEachNearbyPort(Level level, BlockPos pos, Consumer<ArcforgeFurnaceBlockEntity> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    candidate.setWithOffset(pos, dx, -dy, dz);
                    if (level.isLoaded(candidate) && level.getBlockState(candidate).is(ModBlocks.ARCFORGE_FURNACE_PORT.get())
                            && level.getBlockEntity(candidate) instanceof ArcforgeFurnaceBlockEntity port) {
                        action.accept(port);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of bricks and walls ---

    // Right-clicking a brick or wall of a formed furnace opens the furnace; otherwise the click falls through.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        ArcforgeFurnaceBlockEntity port = findFormedPort(level, pos);
        if (port == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(port, port.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench on a brick or wall: sneak picks it up, otherwise nearby furnaces recheck and report.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            level.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }
        forEachNearbyPort(level, pos, ArcforgeFurnaceBlockEntity::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findFormedPort(level, pos) != null
                    ? "message.arcforge.arcforge_furnace.formed" : "message.arcforge.arcforge_furnace.incomplete"));
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.6F);
        return InteractionResult.SUCCESS;
    }
}
