/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.machine.CombustionFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// A machine that burns #arcforge:combustion_fuel from one slot, making something (FE or heat) into a
// buffer each tick it burns. While the buffer is full it pauses, keeping what's left of the burning item.
public abstract class BurnerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_UPGRADE_FIRST = 1;
    public static final int SLOT_COUNT = SLOT_UPGRADE_FIRST + UPGRADE_SLOTS;

    private final ResourceHandler<ItemResource> fuelInput;
    private final ContainerData data;

    private int burnTime;
    private int burnTotal;
    private @Nullable Item burning;
    private int outputPerTick;

    protected BurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, SideConfig sideConfig, List<SideMode> allowedSideModes) {
        super(type, pos, state, SLOT_COUNT, BurnerBlockEntity::isItemValid, sideConfig, allowedSideModes);
        this.fuelInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FUEL, slot -> false);
        this.data = new WideIntContainerData(BurnerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BurnerMenu.DATA_STORED -> getStored();
                    case BurnerMenu.DATA_CAPACITY -> getCapacity();
                    case BurnerMenu.DATA_TEMPERATURE -> getTemperature();
                    case BurnerMenu.DATA_BURN_TIME -> burnTime;
                    case BurnerMenu.DATA_BURN_TOTAL -> burnTotal;
                    case BurnerMenu.DATA_OUTPUT_PER_TICK -> outputPerTick;
                    case BurnerMenu.DATA_BURNING_ITEM -> burning == null ? -1 : BuiltInRegistries.ITEM.getId(burning);
                    case BurnerMenu.DATA_STATUS -> status.ordinal();
                    case BurnerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BurnerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(int slot, ItemResource resource) {
        if (slot == SLOT_FUEL) {
            return CombustionFuel.isFuel(resource.toStack(1));
        }
        return isUpgradeSlot(slot, SLOT_UPGRADE_FIRST, resource);
    }

    // --- What the burner makes ---

    // How much faster than a vanilla furnace fuel burns.
    protected abstract double burnSpeed();

    // Makes one tick's worth of output into the buffer; returns how much fit.
    protected abstract int produce();

    protected abstract boolean isBufferFull();

    // Hands the buffer's contents to touching blocks on the output faces.
    protected abstract void pushOutput(ServerLevel level, BlockPos pos, Direction facing);

    public abstract int getStored();

    public abstract int getCapacity();

    // °C for heat, 0 for FE.
    protected int getTemperature() {
        return 0;
    }

    protected ContainerData getData() {
        return data;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // A redstone-disabled burner holds its fire where it is.
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        int produced = 0;
        if (enabled) {
            if (burnTime <= 0 && !isBufferFull()) {
                startBurn(level);
            }
            if (burnTime > 0) {
                produced = produce();
                if (produced > 0) {
                    burnTime--;
                    if (burnTime == 0) {
                        burning = null;
                    }
                }
            }
        }
        outputPerTick = produced;
        pushOutput(level, pos, getFacing());

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (produced > 0) {
            status = MachineStatus.RUNNING;
        } else if (isBufferFull()) {
            status = MachineStatus.FULL;
        } else {
            status = MachineStatus.NO_FUEL;
        }
        setLit(enabled && burnTime > 0);
        if (produced > 0) {
            setChanged();
        }
    }

    private void startBurn(ServerLevel level) {
        ItemStack fuel = items.getStack(SLOT_FUEL);
        if (!CombustionFuel.isFuel(fuel)) {
            return;
        }
        int ticks = CombustionFuel.burnTicks(level, this, fuel, burnSpeed());
        if (ticks <= 0) {
            return;
        }
        burning = fuel.getItem();
        items.setStack(SLOT_FUEL, fuel.copyWithCount(fuel.getCount() - 1));
        burnTime = ticks;
        burnTotal = ticks;
    }

    public int getBurnTime() {
        return burnTime;
    }

    // --- Capabilities ---

    // Fuel goes in through input faces (and unsided queries); nothing comes back out.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fuelInput : null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        burning = input.getString("burning")
                .map(Identifier::tryParse)
                .flatMap(id -> id == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(id))
                .orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        if (burning != null) {
            output.putString("burning", BuiltInRegistries.ITEM.getKey(burning).toString());
        }
    }
}
