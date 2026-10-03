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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.FlueGas;
import net.zagdrath.arcforge.heat.OxyFuel;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.FuelBurnerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Burns liquid fuel from its tank into heat (HU). What burns, how well and how hot comes from the
// arcforge:burner_fuels data map: creosote burns at 0.5 mB/t for 120 HU per mB, so 60 HU/t, and only
// up to 850°C; Naphtha makes 200 HU/t up to 1,200°C, and hydrogen 60 HU/t up to 1,400°C (the burner's maximum). It pauses while its heat
// buffer is full, or as hot as the fuel burns (after switching to a cooler fuel it cools down to it as
// its heat is drawn off). Speed upgrades burn fuel (and make heat) faster, Heat upgrades get more heat
// from each mB. Fed oxygen through an Oxygen face it burns on oxy-fuel (see OxyFuel): each fuel 300°C hotter, up
// to 1,600°C, with more heat per mB. The Oxygen face is separate from the fuel's Input face, which still takes
// hydrogen as a fuel. With a Flue Gas face (the Gas Output side mode) set, carbon fuels (#arcforge:carbon_fuels) give off
// Carbon Dioxide through it in proportion to the heat made (see FlueGas); Hydrogen gives none.
public class FuelBurnerBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_BUCKET_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HEAT, SideMode.OXYGEN, SideMode.GAS_OUTPUT);

    private final HeatBuffer heat;
    private final HeatHandler heatOutput;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final OxyFuel oxy;
    private final FlueGas flue;
    private final ContainerData data;

    // Fuel taken from the tank (in whole mB) but not burnt yet.
    private double fuelTaken;
    private double burnedPerTick;
    private int heatPerTick;

    public FuelBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FUEL_BURNER.get(), pos, state, MACHINE_SLOTS, FuelBurnerBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.HEAT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.INPUT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.FUEL_BURNER_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.FUEL_BURNER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatOutput = heat.output();
        this.tank = new FilteredFluidTank(
                ArcforgeConfig.FUEL_BURNER_TANK_CAPACITY.getAsInt(),
                resource -> BurnerFuel.of(resource) != null,
                this::setChanged);
        this.fluidInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        // With no output face mode, input faces also hand back the empty buckets.
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_BUCKET_IN, slot -> slot == SLOT_BUCKET_OUT);
        this.oxy = new OxyFuel(ArcforgeConfig.FUEL_BURNER_OXYGEN_TANK_CAPACITY.getAsInt(), ArcforgeConfig.FUEL_BURNER_OXYGEN_PER_TICK::getAsDouble,
                this::setChanged);
        this.flue = new FlueGas(ArcforgeConfig.FLUE_GAS_TANK_CAPACITY.getAsInt(), this::setChanged);
        this.data = new WideIntContainerData(FuelBurnerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FuelBurnerMenu.DATA_HEAT -> heat.getStored();
                    case FuelBurnerMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case FuelBurnerMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case FuelBurnerMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case FuelBurnerMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case FuelBurnerMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case FuelBurnerMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case FuelBurnerMenu.DATA_BURN_RATE -> (int) Math.round(burnedPerTick * 1_000.0);
                    case FuelBurnerMenu.DATA_STATUS -> status.ordinal();
                    case FuelBurnerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FuelBurnerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case FuelBurnerMenu.DATA_OXYGEN -> oxy.getTank().getAmount();
                    case FuelBurnerMenu.DATA_OXYGEN_CAPACITY -> oxy.getTank().getCapacity();
                    case FuelBurnerMenu.DATA_OXY_ACTIVE -> oxy.isActive() ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    // The bucket slot takes full buckets of a burner fuel.
    public static boolean isItemValid(int slot, ItemResource resource) {
        FluidResource fluid = slot == SLOT_BUCKET_IN ? BucketSlots.contents(resource.toStack(1)) : null;
        return fluid != null && BurnerFuel.of(fluid) != null;
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, FuelBurnerBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        BucketSlots.pour(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, tank);

        // A redstone-disabled burner makes nothing but still gives away the heat it holds.
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        heatPerTick = 0;
        burnedPerTick = 0;
        BurnerFuel fuel = BurnerFuel.of(tank.getResource(0));
        int burnCelsius = fuel != null ? fuel.burnTemperature(heat.getMaxCelsius()) : heat.getMaxCelsius();
        // With oxygen to hand it burns hotter, and the buffer may fill as far as that temperature.
        boolean oxyReady = fuel != null && oxy.available();
        if (oxyReady) {
            burnCelsius = OxyFuel.temperature(burnCelsius);
        }
        heat.setCeiling(oxyReady ? burnCelsius : heat.getMaxCelsius());
        boolean hotEnough = heat.getStored() >= heat.storedAt(burnCelsius);
        boolean carbon = FlueGas.isCarbonFuel(tank.getResource(0).getFluid());
        if (enabled && !heat.isFull() && !hotEnough && fuel != null) {
            boolean onOxy = oxyReady && oxy.burn();
            burn(fuel, heat.storedAt(burnCelsius) - heat.getStored(), onOxy ? OxyFuel.heatMultiplier() : 1.0);
        }
        if (heatPerTick > 0 && FlueGas.hasFlueFace(sideConfig, getFacing())) {
            flue.emit(heatPerTick, carbon);
        } else {
            flue.idle();
        }
        flue.push(level, pos, getFacing(), sideConfig, outputs);
        if (heatPerTick <= 0) {
            oxy.idle();
        }
        // While it's burning it is at the fuel's temperature, so the heat moves on straight away.
        if (heatPerTick > 0) {
            heat.setProducingAt(burnCelsius);
        } else {
            heat.setProducing(false);
        }
        outputs.pushHeat(level, pos, getFacing(), sideConfig, heat, ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt());

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (heat.isFull() || hotEnough && fuel != null) {
            status = MachineStatus.FULL;
        } else if (heatPerTick > 0) {
            status = oxy.isActive() ? MachineStatus.OXY_FUEL : MachineStatus.BURNING;
        } else {
            status = MachineStatus.NO_FUEL;
        }
        setLit(heatPerTick > 0);
        if (oxy.isActive() && level.getGameTime() % 20 == 0) {
            ArcforgeAdvancements.oxyFuel(this, heat.getTemperature());
        }
    }

    // Burns one tick's fuel (faster with Speed upgrades), taking whole mB from the tank as it needs them.
    // room: the most heat the buffer takes before it's as hot as the fuel burns; multiplier: oxy-fuel's heat (or 1).
    private void burn(BurnerFuel fuel, int room, double multiplier) {
        double wanted = fuel.mbPerTick() * speedMultiplier();
        if (fuelTaken < wanted) {
            int take = Math.min(tank.getAmount(), (int) Math.ceil(wanted - fuelTaken));
            try (Transaction tx = Transaction.openRoot()) {
                fuelTaken += tank.extract(0, tank.getResource(0), take, tx);
                tx.commit();
            }
        }
        burnedPerTick = Math.min(wanted, fuelTaken);
        fuelTaken -= burnedPerTick;
        heatPerTick = heat.add(Math.min(room, (int) Math.round(burnedPerTick * fuel.huPerMb() * UpgradeType.outputMultiplier(upgrades(UpgradeType.HEAT))
                * multiplier)));
        setChanged();
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public int getHeatPerTick() {
        return heatPerTick;
    }

    public OxyFuel getOxyFuel() {
        return oxy;
    }

    public FlueGas getFlue() {
        return flue;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take full buckets and give back the empty ones.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? itemAutomation : null;
    }

    // Input faces take fuel from pipes and fluid conduits; Oxygen faces take oxygen for oxy-fuel.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == SideMode.OXYGEN) {
            return oxy.getInput();
        }
        if (mode == SideMode.GAS_OUTPUT) {
            return flue.getOutput();
        }
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    // Heat can be drawn out of heat faces (and unsided queries); nothing can put heat in.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatOutput : null;
    }

    // Held buckets fill the tank from any face, regardless of the side configuration.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            // Pressurized Conduits too, for gas fuels such as hydrogen, and oxygen on Oxygen faces.
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.INPUT || mode == SideMode.OXYGEN ? ConnectionMode.INPUT
                    : mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        heat.deserialize(input);
        fuelTaken = input.getDoubleOr("fuel_taken", 0.0);
        oxy.load(input);
        flue.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        heat.serialize(output);
        output.putDouble("fuel_taken", fuelTaken);
        oxy.save(output);
        flue.save(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.fuel_burner");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FuelBurnerMenu(containerId, inventory, worldPosition, items, data);
    }
}
