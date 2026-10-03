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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.heat.OxyFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Burns coal, charcoal and coal blocks into heat (HU) at twice furnace speed. It runs up to 1,100°C, hot
// enough to drive a Thermoelectric Plant at full efficiency. Fed oxygen through an Oxygen face it burns on
// oxy-fuel (see OxyFuel): hotter, up to 1,400°C, and with more heat per fuel.
public class FireboxBlockEntity extends BurnerBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT, SideMode.OXYGEN, SideMode.GAS_OUTPUT);

    private final HeatBuffer heat;
    private final HeatHandler heatOutput;
    private final OxyFuel oxy;

    public FireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FIREBOX.get(), pos, state, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.FIREBOX_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.FIREBOX_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatOutput = heat.output();
        this.oxy = new OxyFuel(ArcforgeConfig.FIREBOX_OXYGEN_TANK_CAPACITY.getAsInt(), ArcforgeConfig.FIREBOX_OXYGEN_PER_TICK::getAsDouble,
                this::setChanged);
    }

    // How hot it burns on oxy-fuel.
    private int oxyCelsius() {
        return OxyFuel.temperature(heat.getMaxCelsius());
    }

    // With oxygen to hand the buffer may fill to the oxy-fuel temperature; without, to its usual maximum.
    private void updateCeiling() {
        heat.setCeiling(oxy.available() ? oxyCelsius() : heat.getMaxCelsius());
    }

    @Override
    protected double burnSpeed() {
        return ArcforgeConfig.FIREBOX_BURN_SPEED.getAsDouble() * speedMultiplier();
    }

    // HU/t while burning: faster with Speed upgrades, more per fuel item with Heat upgrades, and more again on
    // oxy-fuel. Oxygen is only used while the heat has somewhere to go.
    @Override
    protected int produce() {
        updateCeiling();
        if (heat.isFull()) {
            oxy.idle();
            return 0;
        }
        double made = ArcforgeConfig.FIREBOX_HEAT_PER_TICK.getAsInt() * speedMultiplier() * UpgradeType.outputMultiplier(upgrades(UpgradeType.HEAT));
        if (oxy.burn()) {
            made *= OxyFuel.heatMultiplier();
        }
        return heat.add((int) Math.round(made));
    }

    @Override
    protected boolean isBufferFull() {
        updateCeiling();
        return heat.isFull();
    }

    @Override
    protected void pushOutput(ServerLevel level, BlockPos pos, Direction facing) {
        // A burning fire is at full temperature (or the oxy-fuel one), so its heat moves on straight away.
        if (producedThisTick() > 0) {
            heat.setProducingAt(oxy.isActive() ? oxyCelsius() : heat.getMaxCelsius());
        } else {
            heat.setProducing(false);
            oxy.idle();
        }
        outputs.pushHeat(level, pos, facing, sideConfig, heat, ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt());
        if (oxy.isActive() && level.getGameTime() % 20 == 0) {
            ArcforgeAdvancements.oxyFuel(this, heat.getTemperature());
        }
    }

    @Override
    public int getStored() {
        return heat.getStored();
    }

    @Override
    public int getCapacity() {
        return heat.getCapacity();
    }

    @Override
    protected int getTemperature() {
        return heat.getTemperature();
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public OxyFuel getOxyFuel() {
        return oxy;
    }

    @Override
    protected int oxygenAmount() {
        return oxy.getTank().getAmount();
    }

    @Override
    protected int oxygenCapacity() {
        return oxy.getTank().getCapacity();
    }

    @Override
    public boolean isOxyActive() {
        return oxy.isActive();
    }

    @Override
    protected MachineStatus runningStatus() {
        return oxy.isActive() ? MachineStatus.OXY_FUEL : MachineStatus.RUNNING;
    }

    // Oxygen faces (and unsided queries) take oxygen; Flue Gas faces give out the flue gas.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OXYGEN ? oxy.getInput() : flueHandler(mode);
    }

    // Heat can be drawn out of heat faces (and unsided queries); nothing can put heat in.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatOutput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            // Pressurized Conduits bring oxygen to Oxygen faces.
            case GAS -> mode == SideMode.OXYGEN ? ConnectionMode.INPUT : mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        oxy.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        oxy.save(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.firebox");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BurnerMenu(ModMenuTypes.FIREBOX.get(), containerId, inventory, worldPosition, items, getData());
    }
}
