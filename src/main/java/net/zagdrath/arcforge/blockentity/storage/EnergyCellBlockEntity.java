/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.EnergyCellMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.energy.SidedEnergyHandler;
import net.zagdrath.arcforge.transfer.energy.TrackingEnergyHandler;

// Stores FE. Input faces accept energy, output faces give it and push it into neighbours each tick.
// The GUI's discharge slot drains an energy item into the cell; the charge slot fills one from it.
// Redstone control pauses everything the cell does on its own (pushing and charging).
public class EnergyCellBlockEntity extends StorageBlockEntity {
    private final TrackingEnergyHandler energy;
    private final EnergyHandler inputView;
    private final EnergyHandler outputView;
    private final ContainerData data;
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new EnumMap<>(Direction.class);

    // FE that came in and went out during the last tick, shown in the GUI.
    private int lastReceived;
    private int lastExtracted;
    private int comparatorSignal = -1;

    public EnergyCellBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: charge from every side, discharge from the front.
        super(ModBlockEntityTypes.ENERGY_CELL.get(), pos, state, ((EnergyCellBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.OUTPUT));
        this.energy = new TrackingEnergyHandler(tier.cellCapacity(), tier.cellRate(), this::setChanged);
        this.inputView = new SidedEnergyHandler(energy, true, false);
        this.outputView = new SidedEnergyHandler(energy, false, true);
        this.data = new WideIntContainerData(EnergyCellMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case EnergyCellMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case EnergyCellMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case EnergyCellMenu.DATA_RECEIVED -> lastReceived;
                    case EnergyCellMenu.DATA_EXTRACTED -> lastExtracted;
                    case EnergyCellMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case EnergyCellMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isEnergyItem(ItemResource resource) {
        return !resource.isEmpty() && ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Energy.ITEM) != null;
    }

    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return isEnergyItem(resource);
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.ENERGY;
    }

    public int getEnergy() {
        return energy.getAmountAsInt();
    }

    public int getCapacity() {
        return energy.getCapacityAsInt();
    }

    // 0 when empty (everything dark), otherwise 1-4 lit segments by fill level.
    public static int chargeLevel(long stored, long capacity) {
        if (stored <= 0 || capacity <= 0) {
            return 0;
        }
        return Mth.clamp((int) Math.ceil(4.0 * stored / capacity), 1, 4);
    }

    public int getComparatorSignal() {
        return Mth.floor(15.0 * energy.getAmountAsLong() / energy.getCapacityAsLong());
    }

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, EnergyCellBlockEntity cell) {
        cell.tick(level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        int rate = tier.cellRate();
        EnergyHandler discharging = itemEnergy(SLOT_IN);
        if (discharging != null) {
            EnergyHandlerUtil.move(discharging, energy, rate, null);
        }
        if (isRedstoneEnabled()) {
            EnergyHandler charging = itemEnergy(SLOT_OUT);
            if (charging != null) {
                EnergyHandlerUtil.move(energy, charging, rate, null);
            }
            pushEnergy(level, pos, rate);
        }

        lastReceived = energy.getReceived();
        lastExtracted = energy.getExtracted();
        energy.resetTotals();

        int charge = chargeLevel(energy.getAmountAsLong(), energy.getCapacityAsLong());
        if (state.getValue(EnergyCellBlock.CHARGE) != charge) {
            level.setBlock(pos, state.setValue(EnergyCellBlock.CHARGE, charge), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        int signal = getComparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private @Nullable EnergyHandler itemEnergy(int slot) {
        return items.getStack(slot).isEmpty() ? null : ItemAccess.forHandlerIndexStrict(items, slot).getCapability(Capabilities.Energy.ITEM);
    }

    // Pushes FE out of every output face, up to the I/O rate in total.
    private void pushEnergy(ServerLevel level, BlockPos pos, int budget) {
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || energy.getAmountAsInt() <= 0) {
                return;
            }
            if (modeFor(direction) != SideMode.OUTPUT) {
                continue;
            }
            EnergyHandler target = energyTargets
                    .computeIfAbsent(direction, dir -> BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(dir), dir.getOpposite()))
                    .getCapability();
            budget -= EnergyHandlerUtil.move(energy, target, budget, null);
        }
    }

    // --- Capabilities ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return energy;
        }
        return switch (mode) {
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            default -> null;
        };
    }

    // --- Item form: the energy travels in a data component ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(ModDataComponents.ENERGY.get());
        if (stored != null) {
            energy.load(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getAmountAsInt() > 0) {
            components.set(ModDataComponents.ENERGY.get(), energy.getAmountAsInt());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("energy");
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

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new EnergyCellMenu(containerId, inventory, worldPosition, items, data);
    }
}
