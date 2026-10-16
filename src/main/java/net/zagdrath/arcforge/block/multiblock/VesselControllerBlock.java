/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.function.BiFunction;
import java.util.function.Supplier;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A vessel's controller, in a side face, facing out (FACING); LIT while it works. Its block entity runs the vessel and
// is ticked on the server, and on the client while it's formed (for its sound and moving parts).
public class VesselControllerBlock<B extends VesselControllerBlockEntity> extends BaseEntityBlock implements VesselPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private final Supplier<VesselStructure> structure;
    private final Supplier<BlockEntityType<B>> type;
    private final BiFunction<BlockPos, BlockState, B> factory;

    public VesselControllerBlock(Supplier<VesselStructure> structure, Supplier<BlockEntityType<B>> type, BiFunction<BlockPos, BlockState, B> factory,
            BlockBehaviour.Properties properties) {
        super(properties);
        this.structure = structure;
        this.type = type;
        this.factory = factory;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false));
    }

    @Override
    public VesselStructure structure() {
        return structure.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level instanceof ServerLevel serverLevel) {
            return createTickerHelper(blockEntityType, type.get(), (innerLevel, pos, blockState, controller) -> controller.serverTick(serverLevel));
        }
        return state.getValue(FORMED) ? createTickerHelper(blockEntityType, type.get(), (innerLevel, pos, blockState, controller) -> controller.clientTick())
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            structure().notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        structure().notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the vessel is incomplete.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VesselControllerBlockEntity controller) {
            player.openMenu(controller, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VesselControllerBlockEntity controller && controller.isFormed() ? controller : null;
    }

    // Wrench (Configure mode): recheck, and say whether the vessel is formed.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof VesselControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        controller.checkNow();
        Player player = context.getPlayer();
        if (player != null) {
            String id = structure().spec().id();
            player.sendOverlayMessage(Component.translatable(controller.isFormed() ? "message.arcforge." + id + ".formed"
                    : "message.arcforge." + id + ".incomplete"));
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

    // Formed, the vessel is drawn by the controller's renderer: its walls hide nothing behind them and let light
    // through.
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
