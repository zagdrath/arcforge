/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A slim glass tank. The liquid inside is drawn by FluidTankRenderer; the block glows with the
// fluid's light level (LIGHT is kept in sync by the block entity).
public class FluidTankBlock extends StorageBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<FluidTankBlock> codec() {
        return MapCodec.unit(this);
    }

    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public FluidTankBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties.lightLevel(state -> state.getValue(LIGHT)), tier);
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // Right-click with a filled bucket pours it in; with an empty bucket takes a bucket out.
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.FLUID_TANK.get(),
                        (innerLevel, pos, blockState, tank) -> FluidTankBlockEntity.serverTick(serverLevel, pos, blockState, tank))
                : null;
    }
}
