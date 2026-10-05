/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// What the 3x3x3 cube machines share (the Arc Crushing Array, the Induction Furnace Array, the Superheater
// and Condenser Arrays). Every casing has one of these; only the centre's runs. Side configuration applies
// to the faces of the whole cube: for the FE lane machines (none / input / output / energy) input faces fill
// the lane holding the fewest items, output faces give the products, energy faces take FE; the fluid cubes
// choose their own modes and serve their own fluid and heat ports. Each casing drops only its own items, so
// the machine's contents drop when the centre is broken.
public abstract class CubeMultiblockBlockEntity extends MachineBlockEntity implements MultiblockController {
    protected static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    protected final ConsumerEnergyHandler energy;
    // Cubes without FE (the fluid cubes) have an empty buffer that isn't exposed.
    private final boolean hasEnergy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    // FE/t drawn last tick, for the GUI.
    protected int usage;
    // The larger box this casing is part of (see CubeMultiblockStructure), or null for a 3x3x3 or loose casing.
    private CubeMultiblockStructure.@Nullable Box box;

    // inputSlots: each lane's input slot, in lane order. productSlot: the slots output faces give from.
    protected CubeMultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots, LevelSlotFilter filter,
            Set<UpgradeType> upgrades, int energyCapacity, int maxInput, int[] inputSlots, IntPredicate productSlot) {
        this(type, pos, state, machineSlots, filter, upgrades,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES, true, energyCapacity, maxInput, inputSlots, productSlot);
    }

    // A cube with its own port modes and first ports (defaults, relative to the cube's facing). Without
    // energy it has no FE buffer to speak of and nothing is exposed on energy faces.
    protected CubeMultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots, LevelSlotFilter filter,
            Set<UpgradeType> upgrades, SideConfig defaults, List<SideMode> allowed, boolean hasEnergy, int energyCapacity, int maxInput,
            int[] inputSlots, IntPredicate productSlot) {
        super(type, pos, state, machineSlots, filter, upgrades, defaults, allowed);
        this.hasEnergy = hasEnergy;
        this.energy = new ConsumerEnergyHandler(hasEnergy ? energyCapacity : 0, hasEnergy ? maxInput : 0, this::setChanged);
        this.itemInput = new BalancedInput(items, inputSlots);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, productSlot::test);
        this.itemAutomation = new BalancedInput(items, inputSlots) {
            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return productSlot.test(index) ? items.extract(index, resource, amount, transaction) : 0;
            }

            @Override
            public int extract(ItemResource resource, int amount, TransactionContext transaction) {
                return itemOutput.extract(resource, amount, transaction);
            }
        };
    }

    // Whether automation may put this item into this lane input slot (the slot filter already passed).
    protected boolean acceptsLaneInput(int slot, ItemResource resource) {
        return true;
    }

    // Items put in through input faces go to the lane holding the fewest, of those that take them.
    private class BalancedInput extends AutomationResourceHandler<ItemResource> {
        private final MachineItemHandler items;
        private final int[] inputSlots;

        BalancedInput(MachineItemHandler items, int[] inputSlots) {
            super(items, slot -> Arrays.stream(inputSlots).anyMatch(input -> input == slot), slot -> false);
            this.items = items;
            this.inputSlots = inputSlots;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return super.isValid(index, resource) && acceptsLaneInput(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return acceptsLaneInput(index, resource) ? super.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            Integer[] order = Arrays.stream(inputSlots).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingInt(slot -> items.getStack(slot).getCount()));
            int inserted = 0;
            for (int slot : order) {
                if (inserted >= amount) {
                    break;
                }
                inserted += insert(slot, resource, amount - inserted, transaction);
            }
            return inserted;
        }
    }

    // The structure this machine's casings build, for finding the controller from any casing.
    protected abstract CubeMultiblockStructure<?> structure();

    // Runs the formed machine; called on the centre only.
    public abstract void serverTick(ServerLevel level, BlockPos pos, BlockState state);

    // Pushes products out of output faces, if auto-eject is on.
    protected void pushOutputs(ServerLevel level) {
        MultiblockAutomation.pushOutputs(level, this);
    }

    protected boolean isPowered(Level level) {
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    // A signal into any casing counts, not just the centre's.
    @Override
    protected boolean canRun(Level level) {
        return controlState.isEnabled() && redstoneMode.canRun(isPowered(level));
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // FE/t drawn last tick.
    public int getUsage() {
        return usage;
    }

    // --- Lane statistics (the machine control API) ---

    // The counts in a lane's slots (its input first, then its products), taken just before its tick.
    protected int[] laneCounts(int... slots) {
        int[] counts = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            counts[i] = items.getStack(slots[i]).getCount();
        }
        return counts;
    }

    // After the lane's tick: only a finished operation takes from its input slot, and nothing else touches its slots
    // during the tick, so a drop in the input is one operation and what the product slots gained is what it made.
    protected void recordLane(int[] slots, int[] before) {
        int used = before[0] - items.getStack(slots[0]).getCount();
        if (used <= 0) {
            return;
        }
        List<ItemStack> made = new ArrayList<>();
        for (int i = 1; i < slots.length; i++) {
            ItemStack now = items.getStack(slots[i]);
            if (now.getCount() > before[i]) {
                made.add(now.copyWithCount(now.getCount() - before[i]));
            }
        }
        controlState.completed(made, List.of(), used, 0);
    }

    // The facing turned or the side configuration changed: every face of the cube may be different now.
    public void onStructureChanged() {
        if (level != null) {
            MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
        }
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        onStructureChanged();
    }

    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    // Called first thing each tick by the arrays.
    protected void tickPorts(ServerLevel level) {
        portDefaults.tick(level, this);
    }

    // --- Structure (MultiblockController) ---

    public CubeMultiblockStructure.@Nullable Box getBox() {
        return box;
    }

    // Set on every casing when a larger box forms round it (null when it breaks or forms as a 3x3x3).
    public void setBox(CubeMultiblockStructure.@Nullable Box box) {
        if (!java.util.Objects.equals(this.box, box)) {
            this.box = box;
            setChanged();
            onSizeChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    // How many blocks the structure is (27 for the cube).
    public int volume() {
        return box != null ? box.volume() : 27;
    }

    // Its size in 3x3x3 cubes: volume / 27 (1 for the cube). Per-cube config values scale with it.
    public double cubes() {
        return volume() / 27.0;
    }

    // A per-cube value times the cubes, rounded and kept in range.
    protected int perCube(int value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value * cubes()));
    }

    // "W×H×D" for names and tooltips.
    public String sizeText() {
        return box == null ? "3×3×3"
                : box.size(Direction.Axis.X) + "×" + box.size(Direction.Axis.Y) + "×" + box.size(Direction.Axis.Z);
    }

    // The structure's size changed: tanks and buffers that scale with it resize.
    protected void onSizeChanged() {}

    // A cube grew into a box, so its master moved from the cube's centre (this) to the box's corner: hand over the
    // contents that would otherwise be stranded in a casing that no longer runs.
    public void moveContentsTo(CubeMultiblockBlockEntity master) {}

    @Override
    public boolean isFormed() {
        return getBlockState().getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.CENTER;
    }

    @Override
    public Direction getStructureFacing() {
        return getBlockState().getValue(CubeCasingBlock.FACING);
    }

    @Override
    public BlockPos getMinCorner() {
        return box != null ? box.min() : worldPosition.offset(-1, -1, -1);
    }

    @Override
    public BlockPos getMaxCorner() {
        return box != null ? box.max() : worldPosition.offset(1, 1, 1);
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return isInside(pos);
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return null;
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (!hasEnergy) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    // Conduits next to any casing ask it; it answers for the structure face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        CubeMultiblockBlockEntity controller = level != null ? structure().findController(level, worldPosition) : null;
        return controller != null ? controller.conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        portDefaults.load(input);
        CubeMultiblockStructure.Box loaded = input.getLong("box_min").flatMap(min -> input.getLong("box_max")
                .map(max -> new CubeMultiblockStructure.Box(BlockPos.of(min), BlockPos.of(max)))).orElse(null);
        if (!java.util.Objects.equals(box, loaded)) {
            box = loaded;
            onSizeChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        portDefaults.save(output);
        if (box != null) {
            output.putLong("box_min", box.min().asLong());
            output.putLong("box_max", box.max().asLong());
        }
    }

    // Clients get the saved state (with the box, so any casing of one finds its master, for GUIs and Jade) when the chunk
    // loads and when the box changes.
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.@Nullable Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
