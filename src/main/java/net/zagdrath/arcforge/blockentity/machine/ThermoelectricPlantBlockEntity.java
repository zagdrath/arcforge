/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ThermoelectricPlantMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Turns heat into FE. Heat arriving on heat faces fills its buffer, and heat passes through the
// thermocouples in proportion to how full (how hot) the buffer is: up to heatThroughput HU/t when full.
// The FE made from that heat depends on the temperature: 0% at 100°C, 100% at 1,100°C. So it needs a
// hot source: a Geothermal Plant (600°C at most) can only drive it to about half efficiency.
public class ThermoelectricPlantBlockEntity extends MachineBlockEntity {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.HEAT, SideMode.ENERGY);

    private final HeatBuffer heat;
    private final GeneratorEnergyHandler energy;
    private final ContainerData data;
    // Heat faces together take at most heatThroughput HU in a tick.
    private final HeatHandler heatInput;

    private int receivedThisTick;
    private long receiveTick = -1;
    private int heatPerTick;
    private int fePerTick;

    public ThermoelectricPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(), pos, state, MACHINE_SLOTS, ThermoelectricPlantBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.HEAT, SideMode.HEAT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.THERMOELECTRIC_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.THERMOELECTRIC_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.energy = new GeneratorEnergyHandler(
                ArcforgeConfig.THERMOELECTRIC_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.THERMOELECTRIC_MAX_OUTPUT.getAsInt(),
                this::setChanged);
        HeatHandler buffered = heat.input(Integer.MAX_VALUE);
        this.heatInput = new HeatHandler() {
            @Override
            public int getHeat() {
                return heat.getStored();
            }

            @Override
            public int getMaxHeat() {
                return heat.getCapacity();
            }

            @Override
            public int getTemperature() {
                return heat.getTemperature();
            }

            @Override
            public int receiveHeat(int amount, boolean simulate) {
                // Sources push whenever they tick, before or after this plant, so count by game tick.
                long now = level != null ? level.getGameTime() : 0;
                if (now != receiveTick) {
                    receiveTick = now;
                    receivedThisTick = 0;
                }
                int allowed = Math.max(0, throughput() - receivedThisTick);
                int accepted = buffered.receiveHeat(Math.min(amount, allowed), simulate);
                if (!simulate) {
                    receivedThisTick += accepted;
                }
                return accepted;
            }

            @Override
            public int extractHeat(int amount, boolean simulate) {
                return 0;
            }
        };
        this.data = new WideIntContainerData(ThermoelectricPlantMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ThermoelectricPlantMenu.DATA_HEAT -> heat.getStored();
                    case ThermoelectricPlantMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case ThermoelectricPlantMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case ThermoelectricPlantMenu.DATA_EFFICIENCY -> Math.round(upgradedEfficiency() * 100.0F);
                    case ThermoelectricPlantMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case ThermoelectricPlantMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ThermoelectricPlantMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case ThermoelectricPlantMenu.DATA_FE_PER_TICK -> fePerTick;
                    case ThermoelectricPlantMenu.DATA_STATUS -> status.ordinal();
                    case ThermoelectricPlantMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ThermoelectricPlantMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // No slots of its own; the upgrade slots are checked by the handler.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, ThermoelectricPlantBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // Fraction of the heat passing through that becomes FE, from the temperature.
    public static float efficiency(int celsius) {
        int zero = ArcforgeConfig.THERMOELECTRIC_MIN_TEMPERATURE.getAsInt();
        int full = Math.max(zero + 1, ArcforgeConfig.THERMOELECTRIC_FULL_TEMPERATURE.getAsInt());
        return Mth.clamp((float) (celsius - zero) / (full - zero), 0.0F, 1.0F);
    }

    // HU/t it takes in and converts when full: faster with Speed upgrades.
    private int throughput() {
        return (int) Math.round(ArcforgeConfig.THERMOELECTRIC_HEAT_THROUGHPUT.getAsInt() * speedMultiplier());
    }

    // Heat upgrades lift the efficiency of cooler heat (never past 100%).
    public float upgradedEfficiency() {
        return (float) Math.min(1.0, efficiency(heat.getTemperature()) / UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        heatPerTick = 0;
        fePerTick = 0;
        if (enabled && !energy.isFull() && heat.getStored() > 0) {
            float efficiency = upgradedEfficiency();
            int throughput = throughput();
            // Heat flows through in proportion to the temperature above ambient (the cold side).
            int wanted = (int) Math.ceil((double) throughput * heat.getStored() / heat.getCapacity());
            int wantedFe = (int) (wanted * efficiency);
            // Don't burn heat the FE buffer has no room for.
            int room = energy.getCapacityAsInt() - energy.getAmountAsInt();
            if (wantedFe > room) {
                wanted = efficiency > 0 ? (int) (room / efficiency) : wanted;
                wantedFe = room;
            }
            heatPerTick = heat.remove(wanted);
            fePerTick = energy.generate(Math.min(wantedFe, (int) (heatPerTick * efficiency)));
        }
        outputs.pushEnergy(level, pos, getFacing(), sideConfig, energy, ArcforgeConfig.THERMOELECTRIC_MAX_OUTPUT.getAsInt());

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (energy.isFull()) {
            status = MachineStatus.FULL;
        } else if (heatPerTick > 0) {
            status = MachineStatus.RUNNING;
        } else {
            status = MachineStatus.NO_HEAT;
        }
        setLit(fePerTick > 0);
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    // --- Capabilities ---

    // Heat goes in on heat faces (and unsided queries), up to heatThroughput HU/t in total.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    // FE comes out of energy faces (and unsided queries); nothing can put FE in.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        energy.serialize(output.child("energy"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.thermoelectric_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ThermoelectricPlantMenu(containerId, inventory, worldPosition, items, data);
    }
}
