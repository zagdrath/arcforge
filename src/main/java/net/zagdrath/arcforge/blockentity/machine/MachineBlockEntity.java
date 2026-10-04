/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.item.tool.SettingsCardData;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// What every single-block machine shares: its item slots (the machine's own, then four upgrade slots),
// the side configuration and redstone mode set from its GUI, pushing output into touching blocks,
// and dropping its items when removed.
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider, Dismantleable, ConduitConnectable, ConfigurableMachine, Owned, SettingsCopyable {
    // Who placed it and its security override (see SecurityRules).
    protected final Ownership ownership = new Ownership(this::setChanged);

    public static final int UPGRADE_SLOTS = MachineItemHandler.UPGRADE_SLOTS;

    protected final MachineItemHandler items;
    protected final SideConfig sideConfig;
    protected final MachineOutputs outputs = new MachineOutputs();
    private final List<SideMode> allowedSideModes;

    protected RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    // PULSE: the signal last tick, and whether a rising edge is still waiting for its operation (at most one).
    private boolean lastPowered;
    // Set just before the wrench picks it up: its upgrades go with the item instead of dropping.
    private boolean dismantled;
    private int pendingPulses;
    protected MachineStatus status = MachineStatus.NO_FUEL;

    // A slot filter that needs the machine's level, e.g. to look up recipes (null on the client menu's copy).
    @FunctionalInterface
    public interface LevelSlotFilter {
        boolean test(@Nullable Level level, int slot, ItemResource resource);
    }

    // machineSlots: the machine's own slots; the upgrade slots follow them.
    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots,
            FilteredItemHandler.SlotFilter filter, Set<UpgradeType> upgrades, SideConfig sideConfig, List<SideMode> allowedSideModes) {
        this(type, pos, state, machineSlots, (level, slot, resource) -> filter.test(slot, resource), upgrades, sideConfig, allowedSideModes);
    }

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots,
            LevelSlotFilter filter, Set<UpgradeType> upgrades, SideConfig sideConfig, List<SideMode> allowedSideModes) {
        super(type, pos, state);
        this.items = new MachineItemHandler(machineSlots, (slot, resource) -> filter.test(level, slot, resource), upgrades, this::setChanged);
        this.sideConfig = sideConfig;
        this.allowedSideModes = allowedSideModes;
    }

    public MachineItemHandler getItems() {
        return items;
    }

    // How many upgrades of this type are installed (0-8).
    public int upgrades(UpgradeType type) {
        return items.count(type);
    }

    protected double speedMultiplier() {
        return UpgradeType.speedMultiplier(upgrades(UpgradeType.SPEED));
    }

    public Direction getFacing() {
        return MachineBlock.facing(getBlockState());
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
        if (mode != redstoneMode) {
            pendingPulses = 0;
        }
        redstoneMode = mode;
        setChanged();
    }

    // Whether the redstone setting lets it work this tick. Call it once a tick. In PULSE mode a rising edge
    // allows one operation, until consumePulse().
    protected boolean redstoneAllows(Level level) {
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (redstoneMode == RedstoneMode.PULSE) {
            if (powered && !lastPowered) {
                pendingPulses = Math.min(pendingPulses + 1, 1);
                setChanged();
            }
            lastPowered = powered;
            return pendingPulses > 0;
        }
        lastPowered = powered;
        return redstoneMode.canRun(powered);
    }

    // After the single operation a pulse allowed.
    protected void consumePulse() {
        if (redstoneMode == RedstoneMode.PULSE && pendingPulses > 0) {
            pendingPulses = 0;
            setChanged();
        }
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

    protected void onSideConfigChanged() {
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

    // Only machines with output faces have auto-eject.
    @Override
    public boolean isAutoEject() {
        return sideConfig.isAutoEject() && hasOutputs();
    }

    private boolean hasOutputs() {
        return allowedSideModes.stream().anyMatch(SideMode::isOutput);
    }

    @Override
    public void setAutoEject(boolean autoEject) {
        if (hasOutputs()) {
            sideConfig.setAutoEject(autoEject);
            setChanged();
        }
    }

    // With auto-eject on, pushes items out of Output faces every few ticks. output: what those faces give.
    protected void autoEject(ServerLevel level, ResourceHandler<ItemResource> output) {
        if (isAutoEject() && level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            outputs.pushItems(level, worldPosition, getFacing(), sideConfig, output);
        }
    }

    @Override
    public List<SideMode> getAllowedSideModes() {
        return allowedSideModes;
    }

    // --- Settings Card: redstone mode, auto-eject, and the side modes (a formed structure's ports instead) ---

    @Override
    public Identifier settingsKind() {
        return MachineSettings.kind(this);
    }

    @Override
    public Optional<SettingsCardData.StructureSize> settingsSize() {
        return this instanceof MultiblockController controller && controller.isFormed()
                ? Optional.of(MachineSettings.Frame.of(controller).size()) : Optional.empty();
    }

    @Override
    public void writeSettings(ValueOutput output) {
        MachineSettings.writeRedstone(output, redstoneMode);
        if (this instanceof MultiblockController controller) {
            if (level != null && controller.isFormed()) {
                MachineSettings.writePorts(output, level, controller);
            }
        } else {
            output.putInt("side_config", sideConfig.pack());
        }
        if (hasOutputs()) {
            output.putBoolean("auto_eject", sideConfig.isAutoEject());
        }
    }

    @Override
    public int readSettings(ValueInput input) {
        int skipped = MachineSettings.readRedstone(input, this);
        if (this instanceof MultiblockController controller) {
            if (level != null && controller.isFormed()) {
                skipped += MachineSettings.readPorts(input, level, controller);
            }
        } else {
            input.getInt("side_config").ifPresent(packed -> {
                sideConfig.load(packed);
                onSideConfigChanged();
            });
        }
        input.read("auto_eject", Codec.BOOL).ifPresent(this::setAutoEject);
        setChanged();
        return skipped;
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new ArrayList<>();
        input.getInt("side_config").ifPresent(packed -> {
            long configured = java.util.Arrays.stream(RelativeSide.values()).filter(side -> SideConfig.unpack(packed, side) != SideMode.NONE).count();
            lines.add(Component.translatable("settings.arcforge.sides", configured));
        });
        MachineSettings.describePorts(input, lines);
        MachineSettings.describeRedstone(input, lines);
        MachineSettings.describeAutoEject(input, lines);
        return lines;
    }

    // Slot items drop however the machine is removed, except that upgrades stay in a machine dismantled with the wrench
    // (saveKeptItems puts them in the dropped item).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < items.size(); slot++) {
                if (!(dismantled && items.isUpgradeSlot(slot))) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
                }
            }
        }
    }

    @Override
    public void markDismantled() {
        dismantled = true;
    }

    // The dismantled item's "items" list: the upgrade slots only, in place, so placing it back reinstalls them.
    @Override
    public void saveKeptItems(ValueOutput output) {
        FilteredItemHandler kept = new FilteredItemHandler(items.size(), (slot, resource) -> true, () -> {});
        for (int slot = items.getFirstUpgradeSlot(); slot < items.size(); slot++) {
            kept.setStack(slot, items.getStack(slot).copy());
        }
        kept.serialize(output.child("items"));
    }

    @Override
    public Ownership ownership() {
        return ownership;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        items.deserialize(input.childOrEmpty("items"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        lastPowered = input.getBooleanOr("last_powered", false);
        pendingPulses = input.getIntOr("pending_pulses", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        items.serialize(output.child("items"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        output.putBoolean("last_powered", lastPowered);
        output.putInt("pending_pulses", pendingPulses);
    }
}
