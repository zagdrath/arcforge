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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.SteamTurbineMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Turns steam into FE: up to 10 mB/t of any grade, at the grade's FE per mB (Steam 8, High-Pressure 14,
// Superheated 22), so 80 to 220 FE/t. The used steam vents as exhaust. It stops while its FE buffer is
// full, and pushes up to 400 FE/t out of its energy faces.
public class SteamTurbineBlockEntity extends MachineBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY);

    private final GeneratorEnergyHandler energy;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> steamInput;
    private final ContainerData data;
    private int flow;
    private int fePerTick;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_TURBINE.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new GeneratorEnergyHandler(
                ArcforgeConfig.TURBINE_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.TURBINE_MAX_OUTPUT.getAsInt(),
                this::setChanged);
        this.steam = new FilteredFluidTank(ArcforgeConfig.TURBINE_TANK_CAPACITY.getAsInt(), BoilerCore::isSteam, this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.data = new WideIntContainerData(SteamTurbineMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SteamTurbineMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case SteamTurbineMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case SteamTurbineMenu.DATA_STEAM -> steam.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(steam.getResource(0).getFluid()) : -1;
                    case SteamTurbineMenu.DATA_STEAM_AMOUNT -> steam.getAmount();
                    case SteamTurbineMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case SteamTurbineMenu.DATA_FLOW -> flow;
                    case SteamTurbineMenu.DATA_MAX_FLOW -> ArcforgeConfig.TURBINE_MAX_FLOW.getAsInt();
                    case SteamTurbineMenu.DATA_FE_PER_TICK -> fePerTick;
                    case SteamTurbineMenu.DATA_STATUS -> status.ordinal();
                    case SteamTurbineMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SteamTurbineMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        flow = 0;
        fePerTick = 0;
        SteamGrade grade = SteamGrade.of(steam.getResource(0));
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (grade == null || steam.getAmount() <= 0) {
            status = MachineStatus.NO_STEAM;
        } else if (energy.isFull()) {
            status = MachineStatus.FULL;
        } else {
            // Only as much steam as the FE buffer has room for.
            int room = energy.getCapacityAsInt() - energy.getAmountAsInt();
            flow = Math.min(Math.min(steam.getAmount(), ArcforgeConfig.TURBINE_MAX_FLOW.getAsInt()), Math.max(1, room / grade.fePerMb()));
            try (Transaction tx = Transaction.openRoot()) {
                flow = steam.extract(0, steam.getResource(0), flow, tx);
                tx.commit();
            }
            fePerTick = energy.generate(flow * grade.fePerMb());
            status = MachineStatus.GENERATING;
            setChanged();
        }
        setLit(status == MachineStatus.GENERATING);
        outputs.pushEnergy(level, pos, getFacing(), sideConfig, energy, ArcforgeConfig.TURBINE_MAX_OUTPUT.getAsInt());
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Steam goes in on input faces.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? steamInput : null;
    }

    // FE can be drawn out of energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM, FLUID, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        steam.deserialize(input.childOrEmpty("steam"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        steam.serialize(output.child("steam"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_turbine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SteamTurbineMenu(containerId, inventory, worldPosition, items, data);
    }
}
