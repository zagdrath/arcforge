/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayPart;
import net.zagdrath.arcforge.block.multiblock.LithiumCellBlock;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Lithium Cell's share of its Battery Array's energy (FE, a long). In a formed array the controller holds the total and
// writes it back into the cells' shares, in proportion to their capacities, now and then, whenever the array breaks and
// just before a cell is taken out; a cell's share travels on its item (arcforge:stored_energy). On forming, the array
// starts from the sum of its cells' shares.
public class LithiumCellBlockEntity extends BlockEntity {
    private long stored;

    public LithiumCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.LITHIUM_CELL.get(), pos, state);
    }

    public long getCapacity() {
        return getBlockState().getBlock() instanceof LithiumCellBlock cell ? ArcforgeConfig.lithiumCellCapacity(cell.getTier()) : 0L;
    }

    public long getStored() {
        return stored;
    }

    // Sets its share (cut to its capacity).
    public void setStored(long amount) {
        long clamped = Math.clamp(amount, 0, getCapacity());
        if (clamped != stored) {
            stored = clamped;
            setChanged();
        }
    }

    // Loose: its windows show its own fill.
    public void showOwnCharge() {
        showCharge(EnergyCellBlockEntity.chargeLevel(stored, getCapacity()));
    }

    // Sets CHARGE (the windows), when it changes.
    public void showCharge(int charge) {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide() && state.hasProperty(LithiumCellBlock.CHARGE) && state.getValue(LithiumCellBlock.CHARGE) != charge
                && level.getBlockState(worldPosition).is(state.getBlock())) {
            level.setBlock(worldPosition, state.setValue(LithiumCellBlock.CHARGE, charge), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // Taken out of a formed array: the array writes every cell's share first (so this one leaves with its own, and the
    // rest keep theirs), then breaks.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel && state.hasProperty(BatteryArrayPart.FORMED) && state.getValue(BatteryArrayPart.FORMED)) {
            BatteryArrayBlockEntity array = BatteryArrayStructure.findController(serverLevel, pos);
            if (array != null) {
                array.breakAround(pos);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    // --- Item form: the share travels in a data component ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Long amount = components.get(ModDataComponents.STORED_ENERGY.get());
        if (amount != null) {
            stored = Math.clamp(amount, 0, getCapacity());
        }
    }

    // In a formed array, the share it holds right now (the array may not have written it back yet).
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        long share = stored;
        if (level != null && getBlockState().getValue(BatteryArrayPart.FORMED)) {
            BatteryArrayBlockEntity array = BatteryArrayStructure.findController(level, worldPosition);
            if (array != null) {
                share = array.shareOf(this);
            }
        }
        if (share > 0) {
            components.set(ModDataComponents.STORED_ENERGY.get(), share);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("stored");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stored = Math.max(0L, input.getLongOr("stored", 0L));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("stored", stored);
    }
}
