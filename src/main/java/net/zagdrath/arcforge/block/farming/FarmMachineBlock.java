/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.farming.FarmMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HarvesterBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.PlanterBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Planter and the Harvester: a treated-wood hopper box on four legs, facing the field it works (FACING is the
// front, toward the crops; it's placed facing away from the player, like a dispenser). No GUI:
//  - Planter: use it holding seeds to put the stack in; sneak-use with an empty hand to take the seeds back.
//  - Harvester: use it with an empty hand to take the harvest.
// See FarmMachineBlockEntity for when it runs.
public class FarmMachineBlock extends BaseEntityBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<FarmMachineBlock> codec() {
        return MapCodec.unit(this);
    }

    public enum Kind { PLANTER, HARVESTER }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private final Kind kind;

    public FarmMachineBlock(Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != Kind.PLANTER || !PlanterBlockEntity.isSeed(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FarmMachineBlockEntity machine) {
            int inserted = machine.insertByHand(stack);
            if (inserted == 0) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.planter.full"));
            } else if (!player.hasInfiniteMaterials()) {
                stack.shrink(inserted);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // The Planter only gives its seeds back on a sneak-use, so a stray click doesn't empty it.
        if (kind == Kind.PLANTER && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FarmMachineBlockEntity machine && !machine.giveAll(level, player)) {
            player.sendOverlayMessage(Component.translatable(kind == Kind.PLANTER ? "message.arcforge.planter.empty" : "message.arcforge.harvester.empty"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.PLANTER ? new PlanterBlockEntity(pos, state) : new HarvesterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return kind == Kind.PLANTER
                ? createTickerHelper(type, ModBlockEntityTypes.PLANTER.get(), (l, pos, s, be) -> be.serverTick(serverLevel, pos, s))
                : createTickerHelper(type, ModBlockEntityTypes.HARVESTER.get(), (l, pos, s, be) -> be.serverTick(serverLevel, pos, s));
    }
}
