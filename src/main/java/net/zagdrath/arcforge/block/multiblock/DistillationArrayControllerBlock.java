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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.sound.MachineSounds;

// The Distillation Array's controller: its display faces the column's front (the structure's facing),
// and its block entity runs the column and holds its contents.
public class DistillationArrayControllerBlock extends BaseEntityBlock implements ColumnPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public DistillationArrayControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillationArrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.DISTILLATION_ARRAY.get(),
                        (innerLevel, pos, blockState, column) -> column.serverTick(serverLevel))
                // Clients tick it for its running loop (see MachineLoopSound).
                : createTickerHelper(type, ModBlockEntityTypes.DISTILLATION_ARRAY.get(), MachineSounds.clientTicker());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            DistillationStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        DistillationStructure.notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the column is incomplete, so its contents stay reachable.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DistillationArrayBlockEntity column) {
            player.openMenu(column, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DistillationArrayBlockEntity column && column.isFormed() ? column : null;
    }

    // Wrench (Configure mode): turn it: a formed column's controller to its other outward side (which
    // turns the side configuration with it), a loose one clockwise. Then recheck.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(pos) instanceof DistillationArrayBlockEntity column)) {
            return InteractionResult.PASS;
        }
        column.checkNow();
        BlockState state = level.getBlockState(pos);
        DistillationStructure.Column shape = column.getColumn();
        Direction turned = state.getValue(FACING).getClockWise();
        if (shape != null) {
            turned = DistillationStructure.outward(shape, pos, turned);
        }
        level.setBlock(pos, state.setValue(FACING, turned), Block.UPDATE_ALL);
        column.onStructureTurned();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(column.isFormed()
                    ? "message.arcforge.distillation_array.formed" : "message.arcforge.distillation_array.incomplete"));
        }
        return InteractionResult.SUCCESS;
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
        builder.add(FACING, FORMED, LIT);
    }

    // Formed, the shape is the refinery tower's parts in this block (see RefineryHitbox); loose, a full block.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(FORMED) ? RefineryHitbox.shape(level, pos, state) : null;
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(FORMED) ? RefineryHitbox.shape(level, pos, state) : null;
        return shape != null ? shape : super.getCollisionShape(state, level, pos, context);
    }

    // Still a whole block to whatever hangs on it (levers, buttons, torches, redstone), so the column keeps its
    // controls.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? Shapes.block() : super.getBlockSupportShape(state, level, pos);
    }

    // Formed, the tower is drawn by the renderer: the blocks hide nothing behind them and let light through.
    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return state.getValue(FORMED) ? 0 : super.getLightDampening(state);
    }
}
