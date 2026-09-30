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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.HaberReactorMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SynthesizingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.fluid.InputTankRouter;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Combines Hydrogen and Nitrogen into Ammonia with FE and heat (arcforge:synthesizing recipes: 60 mB of Hydrogen and
// 20 mB of Nitrogen make 40 mB of Ammonia every second, at 60 FE/t and 10 HU/t). Like the real Haber process it needs
// a hot catalyst: it only works at haberReactor.minTemperature (450°C) or hotter, and pauses below that, keeping its
// progress. Heat comes in through heat faces from a hotter block (a Fuel Burner, a Firebox). Two input tanks, sorted by
// InputTankRouter, take the gases through input faces; the Ammonia leaves through Gas Output faces every tick. Speed
// upgrades make it faster (drawing FE and heat just as much faster); Energy upgrades cut the FE per operation.
public class HaberReactorBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.GAS_OUTPUT, SideMode.ENERGY, SideMode.HEAT);

    private final ConsumerEnergyHandler energy;
    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank inputA;
    private final FilteredFluidTank inputB;
    private final FilteredFluidTank output;
    private final InputTankRouter router;
    private final ResourceHandler<FluidResource> gasInput;
    private final ResourceHandler<FluidResource> gasOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    private int heatUsage;
    // The recipe running now (progress starts over when it changes), or null.
    private @Nullable Identifier current;
    // What it makes, for Jade; and the temperature the recipe needs (the config's while idle).
    private FluidStack making = FluidStack.EMPTY;
    private int needed = ArcforgeConfig.HABER_MIN_TEMPERATURE.getAsInt();

    public HaberReactorBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: gases in the top and left, Ammonia out of the right, FE into the back, heat into the bottom.
        super(ModBlockEntityTypes.HABER_REACTOR.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.INPUT, SideMode.GAS_OUTPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.HABER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.HABER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.heat = new HeatBuffer(ArcforgeConfig.HABER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.HABER_MAX_TEMPERATURE.getAsInt(), this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        int capacity = ArcforgeConfig.HABER_TANK_CAPACITY.getAsInt();
        this.inputA = new FilteredFluidTank(capacity, resource -> Gases.isGas(resource) && MachineRecipes.isSynthesizingInput(level, resource), this::setChanged);
        this.inputB = new FilteredFluidTank(capacity, resource -> Gases.isGas(resource) && MachineRecipes.isSynthesizingInput(level, resource), this::setChanged);
        this.output = new FilteredFluidTank(capacity, Gases::isGas, this::setChanged);
        this.router = new InputTankRouter(inputA, inputB);
        this.gasInput = new AutomationResourceHandler<>(router, index -> true, index -> false);
        this.gasOutput = new AutomationResourceHandler<>(output, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(inputA, inputB, output);
        // Gas Cartridges fill from the Ammonia, and empty into the input tanks.
        this.fluidInteraction = new CombinedResourceHandler<>(gasOutput, router);
        this.data = new WideIntContainerData(HaberReactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case HaberReactorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case HaberReactorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case HaberReactorMenu.DATA_USAGE -> usage;
                    case HaberReactorMenu.DATA_PROGRESS -> progress;
                    case HaberReactorMenu.DATA_TOTAL -> total;
                    case HaberReactorMenu.DATA_FLUID_A -> fluidId(inputA);
                    case HaberReactorMenu.DATA_AMOUNT_A -> inputA.getAmount();
                    case HaberReactorMenu.DATA_FLUID_B -> fluidId(inputB);
                    case HaberReactorMenu.DATA_AMOUNT_B -> inputB.getAmount();
                    case HaberReactorMenu.DATA_FLUID_OUT -> fluidId(output);
                    case HaberReactorMenu.DATA_AMOUNT_OUT -> output.getAmount();
                    case HaberReactorMenu.DATA_TANK_CAPACITY -> output.getCapacity();
                    case HaberReactorMenu.DATA_HEAT -> heat.getStored();
                    case HaberReactorMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case HaberReactorMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case HaberReactorMenu.DATA_MIN_TEMPERATURE -> needed;
                    case HaberReactorMenu.DATA_HEAT_USAGE -> heatUsage;
                    case HaberReactorMenu.DATA_STATUS -> status.ordinal();
                    case HaberReactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case HaberReactorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // A client-side copy of the (upgrade) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    // FE/t for this recipe: Speed draws it faster, Energy cuts it.
    public int energyPerTick(SynthesizingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // HU/t for this recipe: Speed draws it faster (an operation takes the same heat).
    public int heatPerTick(SynthesizingRecipe recipe) {
        return (int) Math.ceil(recipe.heatPerTick() * speedMultiplier());
    }

    public int ticksFor(SynthesizingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    private SynthesizingRecipe.Input input() {
        return new SynthesizingRecipe.Input(inputA.getResource(0), inputA.getAmount(), inputB.getResource(0), inputB.getAmount());
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        heatUsage = 0;
        SynthesizingRecipe.Input input = input();
        RecipeHolder<SynthesizingRecipe> holder = MachineRecipes.synthesizing(level, input).orElse(null);
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            current = id;
        }
        total = holder != null ? ticksFor(holder.value()) : 0;
        making = holder != null ? holder.value().output().create() : FluidStack.EMPTY;
        needed = holder != null ? holder.value().minTemperatureOrDefault() : ArcforgeConfig.HABER_MIN_TEMPERATURE.getAsInt();

        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (holder == null) {
            // Gas in but not the other one it needs (or not enough).
            status = inputA.getAmount() > 0 || inputB.getAmount() > 0 ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        } else if (!fits(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (heat.getTemperature() < needed || heat.getStored() < heatPerTick(holder.value())) {
            status = MachineStatus.TOO_COLD;
        } else if (!energy.consume(energyPerTick(holder.value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.SYNTHESIZING;
            usage = energyPerTick(holder.value());
            heatUsage = heat.remove(heatPerTick(holder.value()));
            if (++progress >= total) {
                progress = 0;
                finish(holder.value(), input);
            }
            setChanged();
        }
        setLit(status == MachineStatus.SYNTHESIZING);

        // The Ammonia's only way out, so this is always on.
        int budget = ArcforgeConfig.HABER_OUTPUT_RATE.getAsInt();
        for (Direction direction : Direction.values()) {
            if (budget <= 0) {
                break;
            }
            if (sideConfig.get(getFacing(), direction) == SideMode.GAS_OUTPUT) {
                budget -= outputs.pushFluid(level, pos, direction, gasOutput, budget);
            }
        }
    }

    private boolean fits(SynthesizingRecipe recipe) {
        FluidStack made = recipe.output().create();
        try (Transaction tx = Transaction.openRoot()) {
            return output.insert(0, FluidResource.of(made), made.getAmount(), tx) == made.getAmount();
        }
    }

    private void finish(SynthesizingRecipe recipe, SynthesizingRecipe.Input input) {
        int[] tanks = recipe.tanksFor(input);
        if (tanks == null) {
            return;
        }
        FluidStack made = recipe.output().create();
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < tanks.length; i++) {
                FilteredFluidTank tank = tanks[i] == 0 ? inputA : inputB;
                tank.extract(0, tank.getResource(0), recipe.inputs().get(i).amount(), tx);
            }
            output.insert(0, FluidResource.of(made), made.getAmount(), tx);
            tx.commit();
        }
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.getFluid(), null);
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getInputA() {
        return inputA;
    }

    public FilteredFluidTank getInputB() {
        return inputB;
    }

    public FilteredFluidTank getOutputTank() {
        return output;
    }

    // Both input tanks as one: what input faces fill.
    public InputTankRouter getRouter() {
        return router;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // What the running recipe makes (empty without one), for Jade.
    public FluidStack getMaking() {
        return making;
    }

    // The temperature the recipe needs, °C.
    public int getNeededTemperature() {
        return needed;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces fill the input tanks (sorted by the router); Gas Output faces drain the Ammonia. Unsided, all three
    // (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> gasInput;
            case GAS_OUTPUT -> gasOutput;
            default -> null;
        };
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // Heat goes in on heat faces; nothing can draw it back out.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        heat.deserialize(input);
        inputA.deserialize(input.childOrEmpty("input_a"));
        inputB.deserialize(input.childOrEmpty("input_b"));
        output.deserialize(input.childOrEmpty("output"));
        progress = input.getIntOr("progress", 0);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        heat.serialize(output);
        inputA.serialize(output.child("input_a"));
        inputB.serialize(output.child("input_b"));
        this.output.serialize(output.child("output"));
        output.putInt("progress", progress);
        if (current != null) {
            output.putString("recipe", current.toString());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.haber_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HaberReactorMenu(containerId, inventory, worldPosition, items, data);
    }
}
