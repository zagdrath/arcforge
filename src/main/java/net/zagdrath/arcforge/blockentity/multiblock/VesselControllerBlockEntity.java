/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.VesselControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.TickSpread;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The block entity of a vessel's controller (see VesselStructure): it finds and forms the vessel round it, keeps it in
// its structure's registry, ticks its ports and runs the machine (work) while it's formed. Clients get the box when the
// chunk loads and whenever it changes, for the renderer. Ports go on the walls (casings and the controller).
public abstract class VesselControllerBlockEntity extends MachineBlockEntity implements MultiblockController {
    private static final int STRUCTURE_CHECK_INTERVAL = 40;
    private static final int REDSTONE_CHECK_INTERVAL = 10;

    protected VesselStructure.@Nullable Box box;
    private boolean checkRequested = true;
    // The strongest redstone signal into any wall block (0 to 15), checked every half second.
    protected int redstone;
    protected final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    protected VesselControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots, FilteredItemHandler.SlotFilter filter,
            List<SideMode> sideModes) {
        this(type, pos, state, machineSlots, filter, Set.<UpgradeType>of(), sideModes);
    }

    // A vessel that takes upgrades (in its Upgrades tab).
    protected VesselControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots, FilteredItemHandler.SlotFilter filter,
            Set<UpgradeType> upgrades, List<SideMode> sideModes) {
        super(type, pos, state, machineSlots, filter, upgrades,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), sideModes);
    }

    public abstract VesselStructure structure();

    // One tick of the formed vessel's work.
    protected abstract void work(ServerLevel level);

    // The vessel formed, changed or broke (box is the new one, or null).
    protected void onShapeChanged(ServerLevel level) {}

    // The client's tick while formed (sounds, moving parts).
    public void clientTick() {}

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        structure().register(this);
    }

    @Override
    public void setRemoved() {
        structure().unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return box != null;
    }

    public VesselStructure.@Nullable Box getBox() {
        return box;
    }

    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            boolean requested = checkRequested;
            checkRequested = false;
            updateFormed(serverLevel, requested);
        }
    }

    // Breaking the controller un-forms the rest of the vessel.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && box != null) {
            structure().unform(serverLevel, box, pos);
            box = null;
            onShapeChanged(serverLevel);
        }
    }

    protected void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // requested: something in or round the box changed (a block placed inside hides with the rest).
    private void updateFormed(ServerLevel level, boolean requested) {
        if (box != null && !(level.isLoaded(box.min()) && level.isLoaded(box.max()))) {
            return;
        }
        VesselStructure.Box found = structure().find(level, worldPosition, box);
        if (Objects.equals(found, box)) {
            if (requested && box != null) {
                structure().formInterior(level, box, true);
            }
            return;
        }
        if (box != null) {
            structure().unform(level, box, null);
        }
        box = found;
        if (found != null) {
            structure().form(level, found, worldPosition);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        setLit(false);
        onShapeChanged(level);
        setChanged();
        sync();
    }

    public void serverTick(ServerLevel level) {
        if (checkRequested || TickSpread.due(level, worldPosition, STRUCTURE_CHECK_INTERVAL)) {
            checkNow();
        }
        if (box == null) {
            idle(level);
            return;
        }
        portDefaults.tick(level, this);
        if (TickSpread.due(level, worldPosition, REDSTONE_CHECK_INTERVAL)) {
            redstone = redstone(level);
        }
        work(level);
    }

    // A tick while not formed.
    protected void idle(ServerLevel level) {
        setLit(false);
    }

    private int redstone(ServerLevel level) {
        int best = 0;
        for (BlockPos pos : box.positions()) {
            if (box.boundaries(pos) > 0) {
                best = Math.max(best, level.getBestNeighborSignal(pos));
                if (best >= 15) {
                    break;
                }
            }
        }
        return best;
    }

    public int getRedstone() {
        return redstone;
    }

    // How bright the running glow is, 0..1 (full while lit, unless the vessel says otherwise).
    public float glowLevel() {
        return 1.0F;
    }

    // How full of water the vessel is, 0..1, for its renderer (none unless the vessel says otherwise).
    public float waterLevel() {
        return 0.0F;
    }

    // The still texture its windows show that coolant with (null: water) and its colour (ARGB, multiplied over it).
    public @Nullable Identifier coolantTexture() {
        return null;
    }

    public int coolantColor() {
        return 0xA03F76E4;
    }

    protected void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(VesselControllerBlock.LIT) && state.getValue(VesselControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(VesselControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isLit() {
        BlockState state = getBlockState();
        return state.hasProperty(VesselControllerBlock.LIT) && state.getValue(VesselControllerBlock.LIT);
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return box != null ? box.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return box != null ? box.max() : worldPosition;
    }

    // The walls (casings, the controller and the glass): what can hold ports.
    @Override
    public boolean isPart(BlockPos pos) {
        return box != null && box.isWall(pos);
    }

    public AABB getRenderBox() {
        return box != null ? box.aabb().inflate(1.0) : new AABB(worldPosition);
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && box != null) {
            MultiblockAutomation.refresh(level, box.min(), box.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        box = in.getLong("box_min").flatMap(min -> in.getLong("box_max").map(max -> new VesselStructure.Box(BlockPos.of(min), BlockPos.of(max),
                Direction.from2DDataValue(in.getIntOr("box_front", 0))))).orElse(null);
        portDefaults.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (box != null) {
            out.putLong("box_min", box.min().asLong());
            out.putLong("box_max", box.max().asLong());
            out.putInt("box_front", box.front().get2DDataValue());
        }
        portDefaults.save(out);
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
