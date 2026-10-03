/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;

// The Battery Array's controller, in a side face of the box (not on an edge): its charge display faces out (FACING), and
// its block entity runs the array. CHARGE (0-4) is the array's fill, as the cells show it: the display lights that many
// bars and the block glows brighter the fuller it is. A comparator reads how full the array is.
public class BatteryArrayControllerBlock extends BaseEntityBlock implements BatteryArrayPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    // Light by CHARGE while formed: dark when empty, brightest when full.
    private static final int[] LIGHT_BY_CHARGE = { 0, 4, 7, 10, 13 };

    public BatteryArrayControllerBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(FORMED) ? LIGHT_BY_CHARGE[state.getValue(CHARGE)] : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(CHARGE, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryArrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.BATTERY_ARRAY.get(), (innerLevel, pos, blockState, array) -> array.serverTick(serverLevel))
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BatteryArrayStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BatteryArrayStructure.notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the box is incomplete.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BatteryArrayBlockEntity array) {
            player.openMenu(array, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BatteryArrayBlockEntity array && array.isFormed() ? array : null;
    }

    // Wrench (Configure mode): recheck, and say whether the box is formed.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof BatteryArrayBlockEntity array)) {
            return InteractionResult.PASS;
        }
        array.checkNow();
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(array.isFormed()
                    ? "message.arcforge.battery_array.formed" : "message.arcforge.battery_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }

    // A comparator reads how full the array is (0-15).
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BatteryArrayBlockEntity array ? array.getComparatorSignal() : 0;
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
        builder.add(FACING, FORMED, CHARGE);
    }
}
