/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// What the 3x3x3 FE lane machines share (the Arc Crushing Array, the Induction Furnace Array). Every
// casing has one of these; only the centre's runs. Side configuration applies to the faces of the whole
// cube (none / input / output / energy): input faces fill the lane holding the fewest items, output
// faces give the products, energy faces take FE. Each casing drops only its own items, so the machine's
// contents drop when the centre is broken.
public abstract class CubeMultiblockBlockEntity extends MachineBlockEntity implements MultiblockController {
    protected static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    protected final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    // FE/t drawn last tick, for the GUI.
    protected int usage;

    // inputSlots: each lane's input slot, in lane order. productSlot: the slots output faces give from.
    protected CubeMultiblockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots, LevelSlotFilter filter,
            Set<UpgradeType> upgrades, int energyCapacity, int maxInput, int[] inputSlots, IntPredicate productSlot) {
        super(type, pos, state, machineSlots, filter, upgrades,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(energyCapacity, maxInput, this::setChanged);
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

    // Items put in through input faces go to the lane holding the fewest.
    private static class BalancedInput extends AutomationResourceHandler<ItemResource> {
        private final MachineItemHandler items;
        private final int[] inputSlots;

        BalancedInput(MachineItemHandler items, int[] inputSlots) {
            super(items, slot -> Arrays.stream(inputSlots).anyMatch(input -> input == slot), slot -> false);
            this.items = items;
            this.inputSlots = inputSlots;
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

    protected boolean isPowered(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
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

    // --- Structure (MultiblockController) ---

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
        return worldPosition.offset(-1, -1, -1);
    }

    @Override
    public BlockPos getMaxCorner() {
        return worldPosition.offset(1, 1, 1);
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
            case FLUID, THERMAL -> ConnectionMode.NONE;
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }
}
