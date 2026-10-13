/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PlantKind;
import net.zagdrath.arcforge.multiblock.ShapedPlant;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The controller of the refinery's fixed boxes (see OilfieldStructures.PlantBox): the Cracking Unit and the Diesel
// Generator. It forms and breaks the box, keeps its ports, tells clients about it (they draw it, see PlantRenderer),
// and gives its blocks their shapes (ShapedPlant, from the model's still parts). Subclasses run the machine.
public abstract class PlantControllerBlockEntity extends MachineBlockEntity implements ShapedPlant {
    private static final int REDSTONE_CHECK_INTERVAL = 10;

    private final PlantKind kind;
    protected OilfieldStructures.@Nullable PlantBox box;
    private boolean checkRequested = true;
    protected boolean powered;
    protected boolean running;
    protected final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    protected PlantControllerBlockEntity(BlockEntityType<?> type, PlantKind kind, BlockPos pos, BlockState state, int machineSlots, LevelSlotFilter filter,
            Set<UpgradeType> upgrades, List<SideMode> sideModes) {
        super(type, pos, state, machineSlots, filter, upgrades,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), sideModes);
        this.kind = kind;
    }

    // The still parts' boxes, in the model's frame (its layout's solids).
    protected abstract List<float[]> solids();

    // Called once the box has formed or broken.
    protected void onStructureChanged() {}

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return box != null;
    }

    @Override
    public void requestCheck() {
        checkRequested = true;
    }

    @Override
    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && box != null) {
            OilfieldStructures.unform(serverLevel, box.parts(), pos, box.min(), box.max());
            box = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        OilfieldStructures.PlantBox found = OilfieldStructures.findPlant(level, worldPosition, kind, box);
        if (Objects.equals(found, box)) {
            return;
        }
        if (box != null) {
            OilfieldStructures.unform(level, box.parts(), null, box.min(), box.max());
        }
        box = found;
        if (found != null) {
            OilfieldStructures.form(level, found.parts(), worldPosition, found.facing(), found.min(), found.max());
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        setRunning(false);
        onStructureChanged();
        setChanged();
        sync();
    }

    // Runs the checks every controller needs each tick: the structure, its ports and redstone. Gives whether it's
    // formed.
    protected boolean tickStructure(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        if (box == null) {
            status = MachineStatus.NOT_FORMED;
            setRunning(false);
            return false;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = false;
            for (BlockPos pos : box.parts()) {
                if (level.hasNeighborSignal(pos)) {
                    powered = true;
                    break;
                }
            }
        }
        return true;
    }

    public OilfieldStructures.@Nullable PlantBox getBox() {
        return box;
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

    @Override
    public boolean isPart(BlockPos pos) {
        return box != null && box.contains(pos);
    }

    protected void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    protected void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
            sync();
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (box != null || !lit) {
            super.setLit(lit);
        }
    }

    public boolean isRunning() {
        return running;
    }

    // --- The model's frame and shapes ---

    public static ModelFrame frame(OilfieldStructures.PlantBox box) {
        return ModelFrame.toward(box.back(), box.width(), box.height(), box.length());
    }

    @Override
    public @Nullable ModelFrame plantFrame() {
        return box != null ? frame(box) : null;
    }

    // Each kind's shapes, by the way the box runs.
    private static final Map<PlantKind, Map<Direction, VoxelShape[]>> SHAPES = new EnumMap<>(PlantKind.class);

    @Override
    public VoxelShape[] plantShapes() {
        OilfieldStructures.PlantBox shown = box;
        if (shown == null) {
            return new VoxelShape[0];
        }
        synchronized (SHAPES) {
            return SHAPES.computeIfAbsent(kind, k -> new EnumMap<>(Direction.class)).computeIfAbsent(shown.back(), back -> frame(shown).shapes(solids()));
        }
    }

    // The structure (and a little round it), so the renderer isn't culled when the controller is off screen.
    public AABB getRenderBox() {
        return box != null ? AABB.encapsulatingFullBlocks(box.min(), box.max()).inflate(0.5) : new AABB(worldPosition);
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        box = input.getLong("front").map(front -> OilfieldStructures.PlantBox.of(kind, BlockPos.of(front),
                Direction.from2DDataValue(input.getIntOr("back", 0)))).orElse(null);
        running = input.getBooleanOr("running", false);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (box != null) {
            output.putLong("front", box.front().asLong());
            output.putInt("back", box.back().get2DDataValue());
        }
        output.putBoolean("running", running);
        portDefaults.save(output);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // A client-side copy of the slots, for menus.
    protected static MachineItemHandler clientItems(int slots, Set<UpgradeType> upgrades) {
        return new MachineItemHandler(slots, (slot, resource) -> true, upgrades, () -> {});
    }
}
