/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Firebox Array's controller, in a side wall of the box (not on an edge): its fire-door panel faces out of the box
// (FACING), and its block entity runs the array and holds its contents. LIT while it burns.
public class FireboxArrayControllerBlock extends BaseEntityBlock implements FireboxArrayPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FireboxArrayControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false)
                .setValue(INWARD, Inward.NONE));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FireboxArrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.FIREBOX_ARRAY.get(), (innerLevel, pos, blockState, array) -> array.serverTick(serverLevel))
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            FireboxArrayStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        FireboxArrayStructure.notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the box is incomplete, so its contents stay reachable.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FireboxArrayBlockEntity array) {
            player.openMenu(array, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FireboxArrayBlockEntity array && array.isFormed() ? array : null;
    }

    // Wrench (Configure mode): recheck, and say whether the box is formed.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof FireboxArrayBlockEntity array)) {
            return InteractionResult.PASS;
        }
        array.checkNow();
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(array.isFormed()
                    ? "message.arcforge.firebox_array.formed" : "message.arcforge.firebox_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }

    // Smoke and the odd flame from the fire door while it burns.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || !state.getValue(FORMED)) {
            return;
        }
        Direction out = state.getValue(FACING);
        double x = pos.getX() + 0.5 + out.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(out.getStepZ());
        double z = pos.getZ() + 0.5 + out.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(out.getStepX());
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.3;
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, LIT, INWARD);
    }
}
