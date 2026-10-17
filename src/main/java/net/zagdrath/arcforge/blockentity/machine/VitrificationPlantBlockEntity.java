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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.VitrificationPlantMenu;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.nuclear.WasteLog;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.VitrifyingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Vitrification Plant: High-Level Liquid Waste calcined, melted with glass and poured into steel canisters as
// Vitrified Waste Logs (arcforge:vitrifying recipes, FE/t as the recipe says). Each log carries the radioactivity of the
// waste poured into it (see WasteLog), decaying from then on. The waste comes in through input faces (pipes); the glass
// through its slot. Its tank's waste counts as a radiation source, a little shielded by the plant's lead walls.
public class VitrificationPlantBlockEntity extends MachineBlockEntity implements RadiationSource {
    public static final int SLOT_GLASS = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final double WALLS = 0.2;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public VitrificationPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.VITRIFICATION_PLANT.get(), pos, state, MACHINE_SLOTS, VitrificationPlantBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(80_000, 1_000, this::setChanged);
        this.tank = new FilteredFluidTank(16_000, resource -> MachineRecipes.isVitrifyingFluid(level, resource), this::setChanged);
        this.fluidInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_GLASS, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_GLASS, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(VitrificationPlantMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case VitrificationPlantMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case VitrificationPlantMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case VitrificationPlantMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case VitrificationPlantMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case VitrificationPlantMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case VitrificationPlantMenu.DATA_PROGRESS -> progress;
                    case VitrificationPlantMenu.DATA_TOTAL -> total;
                    case VitrificationPlantMenu.DATA_USAGE -> usage;
                    case VitrificationPlantMenu.DATA_STATUS -> status.ordinal();
                    case VitrificationPlantMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case VitrificationPlantMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_GLASS && MachineRecipes.isVitrifyingGlass(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public int energyPerTick(VitrifyingRecipe recipe) {
        return (int) Math.ceil(recipe.energy() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        status = canRun(level) ? work(level) : stoppedStatus();
        setLit(status == MachineStatus.VITRIFYING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        FluidResource fluid = tank.getResource(0);
        ItemStack glass = items.getStack(SLOT_GLASS);
        if (tank.getAmount() <= 0) {
            progress = 0;
            total = 0;
            return MachineStatus.NO_FLUID;
        }
        RecipeHolder<VitrifyingRecipe> holder = MachineRecipes.vitrifying(level, fluid, glass).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return glass.isEmpty() ? MachineStatus.NO_GLASS : MachineStatus.IDLE;
        }
        VitrifyingRecipe recipe = holder.value();
        total = UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
        if (!recipe.glass().test(glass)) {
            return MachineStatus.NO_GLASS;
        }
        if (tank.getAmount() < recipe.fluid().amount()) {
            return MachineStatus.NO_FLUID;
        }
        ItemStack result = recipe.result().create();
        // Each log is its own (poured at its own time), so one waits in the output slot at a time.
        if (!items.getStack(SLOT_OUTPUT).isEmpty()) {
            return MachineStatus.OUTPUT_FULL;
        }
        int fe = energyPerTick(recipe);
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            double activity = RadiationField.rate(fluid.toStack(recipe.fluid().amount()));
            result.set(ModDataComponents.WASTE_LOG.get(), new WasteLog((float) activity, level.getGameTime()));
            int left = tank.getAmount() - recipe.fluid().amount();
            tank.set(0, left > 0 ? fluid : FluidResource.EMPTY, left);
            items.setStack(SLOT_GLASS, glass.copyWithCount(glass.getCount() - recipe.glass().count()));
            items.setStack(SLOT_OUTPUT, result.copy());
            controlState.completed(List.of(result.copy()), List.of(), recipe.glass().count(), recipe.fluid().amount());
            ArcforgeAdvancements.produced(this, result, null, "vitrifying");
            progress = 0;
        }
        setChanged();
        return MachineStatus.VITRIFYING;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    @Override
    public double radiationRate() {
        double rate = tank.getAmount() > 0 ? RadiationField.rate(tank.getResource(0).toStack(tank.getAmount())) * WALLS : 0.0;
        for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
            rate += RadiationField.rate(items.getStack(slot));
        }
        return rate;
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
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

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.vitrification_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VitrificationPlantMenu(containerId, inventory, worldPosition, items, data);
    }
}
