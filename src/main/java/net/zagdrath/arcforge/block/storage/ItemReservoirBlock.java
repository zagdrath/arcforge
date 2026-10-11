/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirStore;
import net.zagdrath.arcforge.machine.interaction.WrenchableMachine;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Item Reservoir: bulk item storage, solid riveted steel. Blocks that together make a solid box (see
// ItemReservoirStore) form one store: FORMED, with AXIS along its length, which ItemReservoirModel draws as one container
// (rims round its outer edges only, bands round both ends). Any block opens the whole store's GUI, takes and gives items
// for it, and gives a comparator its fill. Broken, a block spills its share; the Wrench's Dismantle keeps it on the item.
public class ItemReservoirBlock extends BaseEntityBlock implements WrenchableMachine {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public ItemReservoirBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, AXIS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ItemReservoirBlockEntity reservoir) {
            player.openMenu(reservoir, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Placed (not just changed state): the blocks round it are worked out again on the next tick, once it's in place with
    // its block entity.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !level.isClientSide()) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ItemReservoirStore.update(level, pos, this);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConduitBlock.refreshAround(level, pos);
    }

    // Removed: what's left of its store is worked out again.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            ItemReservoirStore.update(level, pos, this);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    // The whole store's fill, from any of its blocks.
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ItemReservoirBlockEntity reservoir ? reservoir.getComparatorSignal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemReservoirBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ITEM_RESERVOIR.get(),
                        (innerLevel, pos, blockState, reservoir) -> ItemReservoirBlockEntity.serverTick(serverLevel, pos, blockState, reservoir))
                : null;
    }
}
