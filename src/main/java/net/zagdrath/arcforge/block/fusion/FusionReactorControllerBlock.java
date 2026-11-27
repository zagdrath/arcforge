/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fusion;

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
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Fusion Reactor's controller: in the cryostat's wall at the midplane, in the middle of a side, facing out (FACING);
// LIT while a plasma burns. Its block entity runs the reactor; it's ticked on the server, and on the client while formed.
public class FusionReactorControllerBlock extends BaseEntityBlock implements MultiblockPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = TokamakPartBlock.FORMED;
    // The vanilla property, since MachineBlockEntity.setLit sets MachineBlock.LIT.
    public static final BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    public FusionReactorControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FusionReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel serverLevel) {
            return createTickerHelper(type, ModBlockEntityTypes.FUSION_REACTOR.get(), (inner, pos, blockState, reactor) -> reactor.serverTick(serverLevel));
        }
        return state.getValue(FORMED) ? createTickerHelper(type, ModBlockEntityTypes.FUSION_REACTOR.get(), (inner, pos, blockState, reactor) -> reactor.clientTick())
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            TokamakStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        TokamakStructure.notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the reactor is incomplete.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FusionReactorBlockEntity reactor) {
            player.openMenu(reactor, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FusionReactorBlockEntity reactor && reactor.isFormed() ? reactor : null;
    }

    // Wrench (Configure mode): recheck, and say whether the reactor is formed.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof FusionReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        reactor.checkNow();
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(reactor.isFormed() ? "message.arcforge.fusion_reactor.formed"
                    : "message.arcforge.fusion_reactor.incomplete"));
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
