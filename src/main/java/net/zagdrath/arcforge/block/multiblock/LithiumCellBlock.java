/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.multiblock.LithiumCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;

// A Lithium Cell: the Battery Array's storage, one tier of capacity (see ArcforgeConfig.lithiumCellCapacity). It holds its
// share of the array's energy (LithiumCellBlockEntity), kept on the item when broken. CHARGE (0-4) lights its windows like
// an Energy Cell's: in a formed array, at the array's fill (all its cells alike); loose, at its own.
public class LithiumCellBlock extends BaseEntityBlock implements BatteryArrayPart {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    private static final int[] LIGHT_BY_CHARGE = { 0, 3, 5, 7, 9 };

    private final ConduitTier tier;

    public LithiumCellBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties.lightLevel(state -> LIGHT_BY_CHARGE[state.getValue(CHARGE)]));
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(CHARGE, 0));
    }

    public ConduitTier getTier() {
        return tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LithiumCellBlockEntity(pos, state);
    }

    // Placed from an item that carries energy: the windows show it straight away.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LithiumCellBlockEntity cell) {
            cell.showOwnCharge();
        }
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return BatteryArrayStructure.useOnPart(level, pos, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, CHARGE);
    }
}
