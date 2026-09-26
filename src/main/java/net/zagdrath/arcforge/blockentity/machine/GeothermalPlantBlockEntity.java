/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.GeothermalPlantBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.GeothermalHeat;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Turns heat into FE. Heat comes from a combustion chamber (lava from the tank first, then coal/charcoal)
// plus passive heat from adjacent lava source blocks. Heat ramps toward its target, and FE/t = heat * fePerHeat.
public class GeothermalPlantBlockEntity extends BlockEntity implements MenuProvider {
    // The input slot takes lava buckets (drained into the tank) and coal/charcoal (burned directly).
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_UPGRADE_FIRST = 2;
    public static final int UPGRADE_SLOTS = 2;
    public static final int SLOT_COUNT = SLOT_UPGRADE_FIRST + UPGRADE_SLOTS;

    private static final int LAVA_SCAN_INTERVAL = 20;
    private static final FluidResource LAVA = FluidResource.of(Fluids.LAVA);

    private final GeneratorEnergyHandler energy;
    private final FilteredFluidTank lavaTank;
    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ResourceHandler<FluidResource> fluidInput;
    private final SideConfig sideConfig = new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.ENERGY, SideMode.ENERGY);
    private final ContainerData data;
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new EnumMap<>(Direction.class);

    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private MachineStatus status = MachineStatus.NO_FUEL;
    private int heat;
    private int burnTime;
    private int burnTotal;
    private int burnHeat;
    private int fePerTick;
    private int adjacentLavaSources = -1;

    public GeothermalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GEOTHERMAL_PLANT.get(), pos, state);
        this.energy = new GeneratorEnergyHandler(
                ArcforgeConfig.GEOTHERMAL_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.GEOTHERMAL_MAX_OUTPUT.getAsInt(),
                this::setChanged);
        this.lavaTank = new FilteredFluidTank(
                ArcforgeConfig.GEOTHERMAL_LAVA_TANK_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == Fluids.LAVA,
                this::setChanged);
        this.items = new FilteredItemHandler(SLOT_COUNT, GeothermalPlantBlockEntity::isItemValid, this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.fluidInput = new AutomationResourceHandler<>(lavaTank, index -> true, index -> false);
        this.data = new WideIntContainerData(GeothermalPlantMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case GeothermalPlantMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case GeothermalPlantMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case GeothermalPlantMenu.DATA_LAVA -> lavaTank.getAmount();
                    case GeothermalPlantMenu.DATA_LAVA_CAPACITY -> lavaTank.getCapacity();
                    case GeothermalPlantMenu.DATA_HEAT -> heat;
                    case GeothermalPlantMenu.DATA_MAX_HEAT -> GeothermalHeat.maxHeat();
                    case GeothermalPlantMenu.DATA_BURN_TIME -> burnTime;
                    case GeothermalPlantMenu.DATA_BURN_TOTAL -> burnTotal;
                    case GeothermalPlantMenu.DATA_FE_PER_TICK -> fePerTick;
                    case GeothermalPlantMenu.DATA_LAVA_SOURCES -> Math.max(0, adjacentLavaSources);
                    case GeothermalPlantMenu.DATA_STATUS -> status.ordinal();
                    case GeothermalPlantMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GeothermalPlantMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(int slot, ItemResource resource) {
        if (slot == SLOT_INPUT) {
            return resource.is(Items.LAVA_BUCKET) || GeothermalHeat.isSolidFuel(resource.toStack(1));
        }
        if (slot >= SLOT_UPGRADE_FIRST && slot < SLOT_COUNT) {
            return resource.toStack(1).is(ModItemTags.UPGRADES);
        }
        return false;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, GeothermalPlantBlockEntity plant) {
        plant.tick(level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (adjacentLavaSources < 0 || level.getGameTime() % LAVA_SCAN_INTERVAL == 0) {
            adjacentLavaSources = GeothermalHeat.countAdjacentLavaSources(level, pos);
        }

        drainLavaBucket();

        // A redstone-disabled plant cuts all heat sources and pauses any fuel mid-burn.
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        int targetHeat = 0;
        if (enabled) {
            // Don't consume new fuel while the buffer is full; passive lava heat is free and keeps running.
            if (burnTime <= 0 && !energy.isFull()) {
                startBurn();
            }
            int combustionHeat = 0;
            if (burnTime > 0) {
                burnTime--;
                combustionHeat = burnHeat;
            }
            targetHeat = Math.min(GeothermalHeat.maxHeat(), combustionHeat + GeothermalHeat.passiveHeat(adjacentLavaSources));
        }

        int rate = ArcforgeConfig.GEOTHERMAL_HEAT_RATE.getAsInt();
        int previousHeat = heat;
        heat = heat < targetHeat ? Math.min(targetHeat, heat + rate) : Math.max(targetHeat, heat - rate);

        fePerTick = energy.generate(GeothermalHeat.toFePerTick(heat));
        pushEnergy(level, pos, state.getValue(GeothermalPlantBlock.FACING));

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (energy.isFull()) {
            status = MachineStatus.FULL;
        } else if (fePerTick > 0) {
            status = MachineStatus.RUNNING;
        } else {
            status = MachineStatus.NO_FUEL;
        }

        boolean lit = heat > 0;
        if (state.getValue(GeothermalPlantBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(GeothermalPlantBlock.LIT, lit), 3);
        }

        if (heat != previousHeat || burnTime > 0) {
            setChanged();
        }
    }

    private void startBurn() {
        int lavaPerBurn = ArcforgeConfig.GEOTHERMAL_LAVA_PER_BURN.getAsInt();
        if (lavaTank.getAmount() >= lavaPerBurn) {
            try (Transaction tx = Transaction.openRoot()) {
                if (lavaTank.extract(0, LAVA, lavaPerBurn, tx) == lavaPerBurn) {
                    tx.commit();
                    beginBurn(ArcforgeConfig.GEOTHERMAL_LAVA_BURN_TICKS.getAsInt(), GeothermalHeat.lavaHeat());
                    return;
                }
            }
        }

        ItemStack fuel = items.getStack(SLOT_INPUT);
        int fuelHeat = GeothermalHeat.solidFuelHeat(fuel);
        if (fuelHeat > 0) {
            ItemStack remaining = fuel.copy();
            remaining.shrink(1);
            items.setStack(SLOT_INPUT, remaining);
            beginBurn(ArcforgeConfig.GEOTHERMAL_SOLID_FUEL_BURN_TICKS.getAsInt(), fuelHeat);
        }
    }

    private void beginBurn(int ticks, int heatValue) {
        burnTime = ticks;
        burnTotal = ticks;
        burnHeat = heatValue;
    }

    // Empties a lava bucket from the input slot into the tank, placing the empty bucket in the output slot.
    private void drainLavaBucket() {
        ItemStack input = items.getStack(SLOT_INPUT);
        if (!input.is(Items.LAVA_BUCKET) || lavaTank.getSpace() < FluidType.BUCKET_VOLUME) {
            return;
        }

        ItemStack output = items.getStack(SLOT_OUTPUT);
        if (!output.isEmpty() && (!output.is(Items.BUCKET) || output.getCount() >= output.getMaxStackSize())) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (lavaTank.insert(0, LAVA, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME) {
                return;
            }
            tx.commit();
        }

        ItemStack remainingInput = input.copy();
        remainingInput.shrink(1);
        items.setStack(SLOT_INPUT, remainingInput);
        items.setStack(SLOT_OUTPUT, output.isEmpty() ? new ItemStack(Items.BUCKET) : output.copyWithCount(output.getCount() + 1));
    }

    // Pushes FE out of every face configured as ENERGY.
    private void pushEnergy(ServerLevel level, BlockPos pos, Direction facing) {
        int budget = Math.min(energy.getAmountAsInt(), ArcforgeConfig.GEOTHERMAL_MAX_OUTPUT.getAsInt());
        for (Direction direction : Direction.values()) {
            if (budget <= 0) {
                return;
            }
            if (sideConfig.get(facing, direction) != SideMode.ENERGY) {
                continue;
            }
            EnergyHandler target = energyTargets
                    .computeIfAbsent(direction, dir -> BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(dir), dir.getOpposite()))
                    .getCapability();
            budget -= EnergyHandlerUtil.move(energy, target, budget, null);
        }
    }

    // --- Configuration, changed by players through the menu ---

    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    public void setSideMode(RelativeSide side, SideMode mode) {
        if (SideConfig.isLocked(side) || sideConfig.get(side) == mode) {
            return;
        }
        sideConfig.set(side, mode);
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public SideMode getSideMode(RelativeSide side) {
        return sideConfig.get(side);
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    private @Nullable SideMode modeFor(@Nullable Direction side) {
        return side == null ? null : sideConfig.get(getBlockState().getValue(GeothermalPlantBlock.FACING), side);
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) return itemAutomation;
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        lavaTank.deserialize(input.childOrEmpty("lava"));
        items.deserialize(input.childOrEmpty("items"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        heat = input.getIntOr("heat", 0);
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        burnHeat = input.getIntOr("burn_heat", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        lavaTank.serialize(output.child("lava"));
        items.serialize(output.child("items"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        output.putInt("heat", heat);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putInt("burn_heat", burnHeat);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.geothermal_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeothermalPlantMenu(containerId, inventory, worldPosition, items, data);
    }
}
