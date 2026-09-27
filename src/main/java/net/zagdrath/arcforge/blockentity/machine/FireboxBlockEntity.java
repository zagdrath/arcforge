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
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Burns coal, charcoal and coal blocks into heat (HU) at twice furnace speed. It runs up to 1,100°C, hot
// enough to drive a Thermoelectric Plant at full efficiency.
public class FireboxBlockEntity extends BurnerBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatOutput;

    public FireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FIREBOX.get(), pos, state, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.FIREBOX_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.FIREBOX_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatOutput = heat.output();
    }

    @Override
    protected double burnSpeed() {
        return ArcforgeConfig.FIREBOX_BURN_SPEED.getAsDouble() * speedMultiplier();
    }

    // HU/t while burning: faster with Speed upgrades, more per fuel item with Heat upgrades.
    @Override
    protected int produce() {
        return heat.add((int) Math.round(ArcforgeConfig.FIREBOX_HEAT_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.outputMultiplier(upgrades(UpgradeType.HEAT))));
    }

    @Override
    protected boolean isBufferFull() {
        return heat.isFull();
    }

    @Override
    protected void pushOutput(ServerLevel level, BlockPos pos, Direction facing) {
        // A burning fire is at full temperature, so its heat moves on straight away.
        heat.setProducing(producedThisTick() > 0);
        outputs.pushHeat(level, pos, facing, sideConfig, heat, ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt());
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

    // Heat can be drawn out of heat faces (and unsided queries); nothing can put heat in.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatOutput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY, FLUID, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
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
