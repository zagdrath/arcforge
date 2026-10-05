/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.LithiumCellBlock;
import net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.BatteryArrayMenu;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.energy.LongEnergyStore;
import net.zagdrath.arcforge.transfer.energy.SidedEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Battery Array (see BatteryArrayStructure), run by its controller: a big FE store.
//  - Its capacity is the sum of its Lithium Cells' (ArcforgeConfig.lithiumCellCapacity), held as a long; its transfer
//    rate the sum of its Power Regulators' (powerRegulatorTransfer), or baseTransfer (16,384 FE/t) with none.
//  - Each tick it takes in up to its transfer rate through its Energy Input ports and gives out up to its transfer rate
//    through its Energy Output ports, pushing into what touches them. Redstone control pauses the output only.
//  - The controller holds the total while it's formed. The cells hold the energy as shares in proportion to their
//    capacity: written back every shareInterval ticks while it changes, whenever the array breaks and just before a cell
//    is taken out, so a broken cell keeps its share on the item. Forming starts from the sum of the cells' shares.
//  - Its fill shows on the cells' windows and the controller's display (CHARGE, 0-4, all alike), the controller glows
//    brighter the fuller it is, and a comparator on the controller reads it.
// Breaking any block of it un-forms it.
public class BatteryArrayBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.ENERGY_INPUT, SideMode.ENERGY_OUTPUT);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    // How often it rechecks its box anyway (something swapped inside it breaks it).
    private static final int STRUCTURE_CHECK_INTERVAL = 40;

    private BatteryArrayStructure.@Nullable Box box;
    private boolean checkRequested = true;
    private boolean powered;
    // The box's contents, found again when it forms or loads.
    private final List<BlockPos> cells = new ArrayList<>();
    private int regulators;
    private long transfer;
    private boolean scanned;
    // The total loaded from a save, applied once the box has been scanned (its capacity is known then); -1: none.
    private long loadedAmount = -1;

    private final LongEnergyStore energy;
    private final EnergyHandler inputView;
    private final EnergyHandler outputView;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private boolean targetsDirty = true;

    // FE that came in and went out during the last tick, for the GUI and Jade.
    private long lastInput;
    private long lastOutput;
    private int comparatorSignal = -1;
    // The cells' shares are behind the total.
    private boolean sharesDirty;
    private long lastShare;
    // Gives the structure its first ports (none for a new one; see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public BatteryArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.BATTERY_ARRAY.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        this.energy = new LongEnergyStore(this::onEnergyChanged);
        this.inputView = new SidedEnergyHandler(energy, true, false);
        this.outputView = new SidedEnergyHandler(energy, false, true);
        this.data = new WideIntContainerData(BatteryArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BatteryArrayMenu.DATA_STORED_LOW -> low(energy.getAmountAsLong());
                    case BatteryArrayMenu.DATA_STORED_HIGH -> high(energy.getAmountAsLong());
                    case BatteryArrayMenu.DATA_CAPACITY_LOW -> low(energy.getCapacityAsLong());
                    case BatteryArrayMenu.DATA_CAPACITY_HIGH -> high(energy.getCapacityAsLong());
                    case BatteryArrayMenu.DATA_INPUT_LOW -> low(lastInput);
                    case BatteryArrayMenu.DATA_INPUT_HIGH -> high(lastInput);
                    case BatteryArrayMenu.DATA_OUTPUT_LOW -> low(lastOutput);
                    case BatteryArrayMenu.DATA_OUTPUT_HIGH -> high(lastOutput);
                    case BatteryArrayMenu.DATA_TRANSFER_LOW -> low(transfer());
                    case BatteryArrayMenu.DATA_TRANSFER_HIGH -> high(transfer());
                    case BatteryArrayMenu.DATA_CELLS -> box != null ? cells.size() : 0;
                    case BatteryArrayMenu.DATA_REGULATORS -> box != null ? regulators : 0;
                    case BatteryArrayMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case BatteryArrayMenu.DATA_STATUS -> status.ordinal();
                    case BatteryArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BatteryArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static int low(long value) {
        return (int) value;
    }

    private static int high(long value) {
        return (int) (value >>> 32);
    }

    // A client-side copy of the (no) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    private void onEnergyChanged() {
        sharesDirty = true;
        setChanged();
    }

    // --- The numbers (see the class comment) ---

    // FE/t it takes in, and gives out, each tick (0 while it isn't formed).
    public long transfer() {
        return box != null ? transfer : 0L;
    }

    public long getStored() {
        return energy.getAmountAsLong();
    }

    public long getCapacity() {
        return energy.getCapacityAsLong();
    }

    public long getLastInput() {
        return lastInput;
    }

    public long getLastOutput() {
        return lastOutput;
    }

    public int getCellCount() {
        return box != null ? cells.size() : 0;
    }

    public int getRegulatorCount() {
        return box != null ? regulators : 0;
    }

    public LongEnergyStore getEnergy() {
        return energy;
    }

    // 0 when empty, otherwise 1-4 by how full it is (as an Energy Cell's).
    public int chargeLevel() {
        return EnergyCellBlockEntity.chargeLevel(energy.getAmountAsLong(), energy.getCapacityAsLong());
    }

    // 0 when empty, otherwise 1-15 by how full it is.
    public int getComparatorSignal() {
        long capacity = energy.getCapacityAsLong();
        long stored = energy.getAmountAsLong();
        if (box == null || capacity <= 0 || stored <= 0) {
            return 0;
        }
        return 1 + Mth.floor(14.0 * stored / capacity);
    }

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        BatteryArrayStructure.register(this);
    }

    @Override
    public void setRemoved() {
        BatteryArrayStructure.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return box != null;
    }

    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller writes the cells' shares and un-forms the rest of the box.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        breakAround(pos);
    }

    // A block of the box at pos is going (a cell, or this controller): the cells get their shares and the box un-forms
    // now, leaving pos alone.
    public void breakAround(BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && box != null) {
            BatteryArrayStructure.Box old = box;
            release(serverLevel);
            BatteryArrayStructure.unform(serverLevel, old, pos);
            showLooseCharges(serverLevel, pos);
            checkRequested = true;
            setChanged();
            sync(serverLevel);
        }
    }

    // Clients get the box (Jade and the windows name the array from it) when it forms or breaks.
    private void sync(Level level) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void updateFormed(ServerLevel level) {
        // Don't decide while part of the box is out of loaded chunks: it isn't broken, just not there to see.
        if (box != null && !isLoaded(level, box)) {
            return;
        }
        BatteryArrayStructure.Box found = BatteryArrayStructure.find(level, worldPosition, box);
        if (Objects.equals(found, box) && (box == null || BatteryArrayStructure.isFullyFormed(level, box))) {
            return;
        }
        if (box != null) {
            BatteryArrayStructure.Box old = box;
            release(level);
            BatteryArrayStructure.unform(level, old, null);
            showLooseCharges(level, null);
        }
        box = found;
        if (found != null) {
            BatteryArrayStructure.form(level, found, worldPosition);
            scan(level);
            // It starts from what its cells hold.
            long total = 0;
            for (BlockPos pos : cells) {
                if (level.getBlockEntity(pos) instanceof LithiumCellBlockEntity cell) {
                    total += cell.getStored();
                }
            }
            loadedAmount = -1;
            energy.set(total);
            sharesDirty = false;
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        targetsDirty = true;
        updateDisplay(level);
        setChanged();
        sync(level);
    }

    private static boolean isLoaded(Level level, BatteryArrayStructure.Box box) {
        return level.isLoaded(box.min()) && level.isLoaded(box.max())
                && level.isLoaded(new BlockPos(box.min().getX(), box.min().getY(), box.max().getZ()))
                && level.isLoaded(new BlockPos(box.max().getX(), box.min().getY(), box.min().getZ()));
    }

    // Finds the cells and regulators inside: the capacity and the transfer rate.
    private void scan(ServerLevel level) {
        cells.clear();
        regulators = 0;
        long capacity = 0;
        long regulated = 0;
        if (box != null) {
            for (BlockPos pos : box.interior()) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof LithiumCellBlock cell) {
                    cells.add(pos.immutable());
                    capacity += ArcforgeConfig.lithiumCellCapacity(cell.getTier());
                } else if (state.getBlock() instanceof PowerRegulatorBlock regulator) {
                    regulators++;
                    regulated += ArcforgeConfig.powerRegulatorTransfer(regulator.getTier());
                }
            }
        }
        transfer = regulators > 0 ? regulated : ArcforgeConfig.BATTERY_BASE_TRANSFER.getAsInt();
        energy.setCapacity(capacity);
        scanned = true;
    }

    // Writes the total back into the cells' shares (in proportion to their capacities) and gives it up: the cells hold
    // it now.
    private void release(ServerLevel level) {
        if (!scanned) {
            scan(level);
            if (loadedAmount >= 0) {
                energy.set(loadedAmount);
                loadedAmount = -1;
            }
        }
        distribute(level);
        energy.set(0);
        energy.setCapacity(0);
        cells.clear();
        regulators = 0;
        scanned = false;
        sharesDirty = false;
        box = null;
    }

    // The cells' shares: each its capacity's part of the total, the odd FE left by rounding going to the first with room.
    private void distribute(ServerLevel level) {
        long total = energy.getAmountAsLong();
        long capacity = energy.getCapacityAsLong();
        List<LithiumCellBlockEntity> found = new ArrayList<>();
        long given = 0;
        for (BlockPos pos : cells) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LithiumCellBlockEntity cell) {
                long share = capacity > 0 ? (long) Math.floor((double) total * cell.getCapacity() / capacity) : 0L;
                share = Math.min(share, cell.getCapacity());
                cell.setStored(share);
                given += cell.getStored();
                found.add(cell);
            }
        }
        long left = total - given;
        for (LithiumCellBlockEntity cell : found) {
            if (left <= 0) {
                break;
            }
            long room = cell.getCapacity() - cell.getStored();
            long add = Math.min(room, left);
            if (add > 0) {
                cell.setStored(cell.getStored() + add);
                left -= add;
            }
        }
        sharesDirty = false;
        lastShare = level.getGameTime();
    }

    // A cell's share of the total now (its capacity's part of it), for its item when it's broken: some ways of breaking a
    // block (an explosion, Level.destroyBlock) take its drops before the array can write the shares back.
    public long shareOf(LithiumCellBlockEntity cell) {
        long capacity = energy.getCapacityAsLong();
        if (box == null || !scanned || capacity <= 0) {
            return cell.getStored();
        }
        return Math.min(cell.getCapacity(), (long) Math.floor((double) energy.getAmountAsLong() * cell.getCapacity() / capacity));
    }

    // Loose cells show their own fill; skip is a block being broken.
    private void showLooseCharges(ServerLevel level, @Nullable BlockPos skip) {
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-BatteryArrayStructure.MAX_SIZE, -BatteryArrayStructure.MAX_SIZE,
                -BatteryArrayStructure.MAX_SIZE), worldPosition.offset(BatteryArrayStructure.MAX_SIZE, BatteryArrayStructure.MAX_SIZE,
                        BatteryArrayStructure.MAX_SIZE))) {
            if (!pos.equals(skip) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LithiumCellBlockEntity cell
                    && !cell.getBlockState().getValue(LithiumCellBlock.FORMED)) {
                cell.showOwnCharge();
            }
        }
    }

    public BatteryArrayStructure.@Nullable Box getBox() {
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

    // The box's casings, controller and panes (what can hold ports), not the cells and regulators inside.
    @Override
    public boolean isPart(BlockPos pos) {
        return box != null && box.contains(pos) && !box.isInterior(pos);
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
    }

    // For advancements (arcforge:multiblock_formed's min_length): its volume, in blocks.
    @Override
    public int length() {
        return box != null ? box.volume() : 0;
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            checkNow();
        }
        // What came in and went out since the last tick.
        lastInput = energy.getReceived();
        lastOutput = energy.getExtracted();
        energy.resetTotals();
        if (box == null) {
            status = MachineStatus.NOT_FORMED;
            energy.setBudgets(0, 0);
            updateDisplay(level);
            return;
        }
        if (!scanned) {
            if (!isLoaded(level, box)) {
                energy.setBudgets(0, 0);
                return;
            }
            scan(level);
            if (loadedAmount >= 0) {
                energy.set(loadedAmount);
                loadedAmount = -1;
            }
            targetsDirty = true;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        // Redstone pauses the output only; switched off through the machine control API it neither charges nor discharges.
        boolean enabled = controlState.isEnabled();
        boolean output = enabled && redstoneMode.canRun(powered);
        energy.setBudgets(enabled ? transfer : 0, output ? transfer : 0);
        if (output) {
            pushEnergy(level);
        }
        if (!output) {
            status = stoppedStatus();
        } else if (lastInput > 0 && lastInput >= lastOutput) {
            status = MachineStatus.CHARGING;
        } else if (lastOutput > 0) {
            status = MachineStatus.DISCHARGING;
        } else {
            status = energy.getAmountAsLong() >= energy.getCapacityAsLong() ? MachineStatus.FULL : MachineStatus.IDLE;
        }
        if (sharesDirty && level.getGameTime() - lastShare >= ArcforgeConfig.BATTERY_SHARE_INTERVAL.getAsInt()) {
            distribute(level);
        }
        updateDisplay(level);
    }

    // Pushes FE out of every Energy Output port face, up to the transfer rate in total (the output budget).
    private void pushEnergy(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        for (BlockCapabilityCache<EnergyHandler, @Nullable Direction> target : energyTargets) {
            if (energy.getAmountAsLong() <= 0) {
                break;
            }
            EnergyHandler handler = target.getCapability();
            if (handler != null) {
                // The store's output budget caps the total across every port.
                EnergyHandlerUtil.move(energy, handler, (int) Math.min(Integer.MAX_VALUE, Math.min(transfer, energy.getAmountAsLong())), null);
            }
        }
    }

    // What touches the Energy Output port faces, found again when the ports or the box change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        energyTargets.clear();
        if (box == null) {
            return;
        }
        for (BlockPos pos : box.positions()) {
            if (!isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (faceMode(pos, side) == SideMode.ENERGY_OUTPUT) {
                    energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                }
            }
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : box.positions()) {
            if (isPart(pos) && level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    // The controller's display and glow, the cells' windows and the comparator, when they change.
    private void updateDisplay(ServerLevel level) {
        int charge = box != null ? chargeLevel() : 0;
        BlockState state = getBlockState();
        if (state.hasProperty(BatteryArrayControllerBlock.CHARGE) && state.getValue(BatteryArrayControllerBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, state.setValue(BatteryArrayControllerBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
        }
        if (box != null) {
            for (BlockPos pos : cells) {
                if (level.getBlockEntity(pos) instanceof LithiumCellBlockEntity cell) {
                    cell.showCharge(charge);
                }
            }
        }
        int signal = getComparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return null;
    }

    // Energy Input faces take FE in, Energy Output faces give it out; unsided, both.
    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (box == null) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        SideMode mode = faceMode(pos, side);
        if (mode == SideMode.ENERGY_INPUT) {
            return inputView;
        }
        return mode == SideMode.ENERGY_OUTPUT ? outputView : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        if (type != ConduitType.ENERGY) {
            return ConnectionMode.NONE;
        }
        return mode == SideMode.ENERGY_INPUT ? ConnectionMode.INPUT : mode == SideMode.ENERGY_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
    }

    // Conduits next to the controller ask it about the box face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
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
        box = in.getLong("box_min").flatMap(min -> in.getLong("box_max").map(max -> new BatteryArrayStructure.Box(BlockPos.of(min), BlockPos.of(max),
                Direction.from2DDataValue(in.getIntOr("box_front", 0))))).orElse(null);
        scanned = false;
        loadedAmount = box != null ? Math.max(0L, in.getLongOr("energy", 0L)) : -1L;
        sharesDirty = in.getBooleanOr("shares_dirty", false);
        portDefaults.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (box != null) {
            out.putLong("box_min", box.min().asLong());
            out.putLong("box_max", box.max().asLong());
            out.putInt("box_front", box.front().get2DDataValue());
            out.putLong("energy", loadedAmount >= 0 ? loadedAmount : energy.getAmountAsLong());
        }
        out.putBoolean("shares_dirty", sharesDirty);
        portDefaults.save(out);
    }

    // Clients get the saved state when the chunk loads and whenever the box changes.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return box != null ? Component.translatable("container.arcforge.battery_array.sized", box.sizeText())
                : Component.translatable("container.arcforge.battery_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BatteryArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
