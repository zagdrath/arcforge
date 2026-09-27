/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// What the steam arrays share (see ShellStructure). Every casing has one of these and remembers the shell
// it is part of; the one at the shell's minimum corner is the master, which runs the machine, holds its
// contents and serves the GUI. Side configuration applies to the faces of the whole box, relative to the
// structure's facing; only casings do IO (glass never does). The master's contents stay in it when the
// structure breaks, so rebuilding it brings them back.
public abstract class ShellMultiblockBlockEntity extends MachineBlockEntity implements MultiblockController {
    // How often the master rechecks the redstone signal across its blocks.
    private static final int REDSTONE_CHECK_INTERVAL = 10;

    private ShellStructure.@Nullable Shell shell;
    private Direction facing = Direction.NORTH;
    private boolean powered;

    protected ShellMultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots,
            FilteredItemHandler.SlotFilter filter, Set<UpgradeType> upgrades, SideConfig sideConfig, List<SideMode> allowedSideModes) {
        super(type, pos, state, machineSlots, filter, upgrades, sideConfig, allowedSideModes);
    }

    protected abstract ShellStructure structure();

    // Runs the formed machine; called on the master only.
    protected abstract void tickMaster(ServerLevel level);

    public ShellStructure.@Nullable Shell getShell() {
        return shell;
    }

    public void setShell(ShellStructure.@Nullable Shell shell) {
        this.shell = shell;
        setChanged();
        sync();
    }

    public boolean isMaster() {
        return shell != null && shell.min().equals(worldPosition);
    }

    // The master of the structure this casing is part of.
    public @Nullable ShellMultiblockBlockEntity getMaster() {
        if (shell == null || level == null) {
            return null;
        }
        return level.getBlockEntity(shell.min()) instanceof ShellMultiblockBlockEntity master && shell.equals(master.shell) ? master : null;
    }

    // The structure has just formed around this master. facing: toward the player who completed it, if known.
    public void onFormed(@Nullable Direction facing) {
        if (facing != null && facing.getAxis().isHorizontal()) {
            this.facing = facing;
        }
        setChanged();
        sync();
    }

    // The structure is about to break apart.
    public void onUnformed() {
        setChanged();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ShellMultiblockBlockEntity part) {
        if (part.isMaster()) {
            if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
                part.powered = part.checkPowered(level);
            }
            part.tickMaster(level);
        }
    }

    protected boolean isPowered() {
        return powered;
    }

    private boolean checkPowered(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (isPart(pos) && level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    // Sends the block entity to clients (for the renderers).
    protected void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- MachineBlockEntity: casings have no facing or lit property of their own ---

    @Override
    public Direction getFacing() {
        return facing;
    }

    @Override
    protected void setLit(boolean lit) {}

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && shell != null) {
            MultiblockAutomation.refresh(level, shell.min(), shell.max());
        }
    }

    // --- Structure (MultiblockController) ---

    @Override
    public boolean isFormed() {
        return isMaster();
    }

    @Override
    public Direction getStructureFacing() {
        return facing;
    }

    @Override
    public BlockPos getMinCorner() {
        return shell != null ? shell.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return shell != null ? shell.max() : worldPosition;
    }

    // Only casings are parts that do IO; glass and the hollow core aren't.
    @Override
    public boolean isPart(BlockPos pos) {
        return level != null && isInside(pos) && structure().isCasing(level.getBlockState(pos));
    }

    // Conduits next to any casing ask it; it answers for the structure face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        ShellMultiblockBlockEntity master = getMaster();
        return master != null ? master.conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // Renderers draw the whole structure from the master.
    public AABB getRenderBox() {
        return shell != null ? AABB.encapsulatingFullBlocks(shell.min(), shell.max()) : new AABB(worldPosition);
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        shell = input.getInt("shell_length").map(length -> new ShellStructure.Shell(
                BlockPos.of(input.getLongOr("shell_min", 0L)),
                Direction.Axis.byName(input.getStringOr("shell_axis", "y")),
                length)).orElse(null);
        facing = Direction.from2DDataValue(input.getIntOr("facing", Direction.NORTH.get2DDataValue()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (shell != null) {
            output.putLong("shell_min", shell.min().asLong());
            output.putString("shell_axis", shell.axis().getSerializedName());
            output.putInt("shell_length", shell.length());
        }
        output.putInt("facing", facing.get2DDataValue());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
