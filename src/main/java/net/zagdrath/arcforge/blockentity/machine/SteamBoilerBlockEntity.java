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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.SteamBoilerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Boiler;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Boils water into steam with heat (see BoilerCore): up to 80 HU/t, so 8 mB/t of Steam, 5.3 of
// High-Pressure or 4 of Superheated. Heat only flows in from hotter blocks on its heat faces; water
// comes in on input faces (or from buckets), steam goes out of output faces. No upgrades: it is limited
// by the heat it is fed.
public class SteamBoilerBlockEntity extends MachineBlockEntity implements FluidInteractable, Boiler {
    public static final int SLOT_BUCKET_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);
    // Steam pushed out of output faces per tick with auto-eject.
    private static final int STEAM_PUSH = 1_000;

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank water;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> steamOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final BoilerCore core;
    private final ContainerData data;

    public SteamBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_BOILER.get(), pos, state, MACHINE_SLOTS, SteamBoilerBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.OUTPUT, SideMode.NONE, SideMode.INPUT, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.BOILER_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.BOILER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        int tankCapacity = ArcforgeConfig.BOILER_TANK_CAPACITY.getAsInt();
        this.water = new FilteredFluidTank(tankCapacity, BoilerCore::isWater, this::setChanged);
        this.steam = new FilteredFluidTank(tankCapacity, BoilerCore::isSteam, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.steamOutput = new AutomationResourceHandler<>(steam, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(waterInput, steamOutput);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_BUCKET_IN, slot -> slot == SLOT_BUCKET_OUT);
        this.core = new BoilerCore(heat, water, steam);
        this.data = new WideIntContainerData(SteamBoilerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return SteamBoilerMenu.value(index, heat, water, steam, core, status, redstoneMode, sideConfig, 1);
            }
        };
    }

    // The bucket slot takes water buckets.
    public static boolean isItemValid(int slot, ItemResource resource) {
        FluidResource fluid = slot == SLOT_BUCKET_IN ? BucketSlots.contents(resource.toStack(1)) : null;
        return fluid != null && BoilerCore.isWater(fluid);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, SteamBoilerBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        BucketSlots.pour(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, water);
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
            setLit(false);
        } else {
            status = switch (core.tick(ArcforgeConfig.BOILER_MAX_HEAT_PER_TICK.getAsInt(), 1.0)) {
                case BOILING -> MachineStatus.BOILING;
                case HEATING -> MachineStatus.HEATING;
                case NO_WATER -> MachineStatus.NO_WATER;
                case STEAM_FULL -> MachineStatus.STEAM_FULL;
            };
            setLit(status == MachineStatus.BOILING);
        }
        if (isAutoEject()) {
            outputs.pushFluid(level, pos, getFacing(), sideConfig, steamOutput, STEAM_PUSH, null);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    @Override
    public BoilerCore getCore() {
        return core;
    }

    @Override
    public void setPressure(BoilerPressure pressure) {
        core.setPressure(pressure);
        setChanged();
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? itemAutomation : null;
    }

    // Water goes in on input faces, steam comes out of output faces.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> waterInput;
            case OUTPUT -> steamOutput;
            default -> null;
        };
    }

    // Heat goes in on heat faces; nothing can draw it back out.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    // Held water buckets fill the water tank from any face.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return waterInput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case FLUID, ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        steam.deserialize(input.childOrEmpty("steam"));
        core.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        water.serialize(output.child("water"));
        steam.serialize(output.child("steam"));
        core.serialize(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_boiler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return SteamBoilerMenu.single(containerId, inventory, worldPosition, items, data);
    }
}
