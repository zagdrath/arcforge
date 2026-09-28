/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A Vault: bulk storage for one stackable item. Its front is for players: right-click puts in what's held,
// right-clicking twice quickly puts in every matching item carried, left-click takes a stack (Shift: one; see
// MachineInteractionEvents). Sneaking with an empty hand on the front, or using any other face, opens the GUI;
// the Wrench in Configure mode on the front toggles the lock. LOCKED and VOID light the front's status lights.
public class VaultBlock extends StorageBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    public static final BooleanProperty VOID = BooleanProperty.create("void");

    public VaultBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties, tier);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOCKED, false).setValue(VOID, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCKED, VOID);
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
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    public static boolean isFront(BlockState state, Direction face) {
        return state.getBlock() instanceof VaultBlock && state.getValue(FACING) == face;
    }

    // --- The front ---

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || hand != InteractionHand.MAIN_HAND || !isFront(state, hit.getDirection())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) {
            return InteractionResult.SUCCESS;
        }
        if (vault.registerClick(player, level.getGameTime())) {
            insertAll(vault, player, level, pos);
        } else if (vault.getStorage().accepts(stack)) {
            int taken = vault.insert(stack);
            if (taken > 0) {
                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(taken);
                }
                playInsertSound(level, pos);
            }
        } else {
            // Consumed either way, so nothing gets placed against the front.
            player.sendOverlayMessage(stack.getMaxStackSize() <= 1 || !stack.canFitInsideContainerItems()
                    ? Component.translatable("message.arcforge.vault.not_stackable")
                    : Component.translatable("message.arcforge.vault.wrong_item", vault.getTemplate().getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    // An empty hand on the front counts toward a double-click; anywhere else (or sneaking) it opens the GUI.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isFront(state, hit.getDirection()) || player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VaultBlockEntity vault
                && vault.registerClick(player, level.getGameTime())) {
            insertAll(vault, player, level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Every matching stack in the player's hotbar and main inventory (not armour or the offhand).
    private static void insertAll(VaultBlockEntity vault, Player player, Level level, BlockPos pos) {
        var items = player.getInventory().getNonEquipmentItems();
        int total = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && vault.getStorage().accepts(stack)) {
                int taken = vault.insert(stack);
                stack.shrink(taken);
                total += taken;
            }
        }
        if (total > 0) {
            player.getInventory().setChanged();
            playInsertSound(level, pos);
        }
    }

    private static void playInsertSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    // --- Block entity ---

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.VAULT.get(),
                        (innerLevel, pos, blockState, vault) -> VaultBlockEntity.serverTick(serverLevel, pos, blockState, vault))
                : null;
    }
}
