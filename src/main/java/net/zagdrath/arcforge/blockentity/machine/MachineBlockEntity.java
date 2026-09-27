/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// What every single-block machine shares: its item slots (the machine's own, then two upgrade slots),
// the side configuration and redstone mode set from its GUI, pushing output into touching blocks,
// and dropping its items when removed.
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider, Dismantleable, ConduitConnectable, ConfigurableMachine {
    public static final int UPGRADE_SLOTS = 2;

    protected final FilteredItemHandler items;
    protected final SideConfig sideConfig;
    protected final MachineOutputs outputs = new MachineOutputs();
    private final List<SideMode> allowedSideModes;

    protected RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    protected MachineStatus status = MachineStatus.NO_FUEL;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slotCount,
            FilteredItemHandler.SlotFilter filter, SideConfig sideConfig, List<SideMode> allowedSideModes) {
        super(type, pos, state);
        this.items = new FilteredItemHandler(slotCount, filter, this::setChanged);
        this.sideConfig = sideConfig;
        this.allowedSideModes = allowedSideModes;
    }

    // Upgrade slots come after the machine's own; they take anything tagged #arcforge:upgrades.
    public static boolean isUpgradeSlot(int slot, int firstUpgradeSlot, ItemResource resource) {
        return slot >= firstUpgradeSlot && slot < firstUpgradeSlot + UPGRADE_SLOTS && resource.toStack(1).is(ModItemTags.UPGRADES);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public Direction getFacing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    protected void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(MachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.LIT, lit), 3);
        }
    }

    // The mode of the face on the given world side, or null for an internal (unsided) query.
    protected @Nullable SideMode modeFor(@Nullable Direction side) {
        return side == null ? null : sideConfig.get(getFacing(), side);
    }

    public MachineStatus getStatus() {
        return status;
    }

    // --- Configuration, changed by players through the menu ---

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    @Override
    public void setSideMode(RelativeSide side, SideMode mode) {
        if (sideConfig.get(side) == mode) {
            return;
        }
        sideConfig.set(side, mode);
        onSideConfigChanged();
    }

    @Override
    public void clearSideModes() {
        sideConfig.clear();
        onSideConfigChanged();
    }

    private void onSideConfigChanged() {
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            ConduitBlock.refreshAround(level, worldPosition);
        }
    }

    @Override
    public SideMode getSideMode(RelativeSide side) {
        return sideConfig.get(side);
    }

    @Override
    public List<SideMode> getAllowedSideModes() {
        return allowedSideModes;
    }

    // Slot items drop however the machine is removed, including with the wrench.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < items.size(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("items"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
    }
}
