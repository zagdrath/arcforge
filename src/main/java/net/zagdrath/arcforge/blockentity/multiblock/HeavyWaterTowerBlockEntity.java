/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.HeavyWaterTowerMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.multiblock.VesselStructures;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.IsotopeExchangingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Heavy Water Tower (see VesselStructures.HEAVY_WATER_TOWER): the Girdler sulfide process. Its inputs (Water,
// Hydrogen Sulfide and Steam, as its arcforge:isotope_exchanging recipes ask) come in through Input ports into three
// input tanks (each takes one fluid; a fluid goes into the tank already holding it, else the first empty one), FE
// through Energy ports. Each Exchange Tray does one operation of the recipe every recipe's time ticks, for
// energyPerTray FE/t; Heavy Water comes out of Output ports, pushed into what touches them.
public class HeavyWaterTowerBlockEntity extends VesselControllerBlockEntity {
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final int LIT_HOLD = 40;

    private int trays;
    private int progress;
    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank[] inputs = new FilteredFluidTank[3];
    private final FilteredFluidTank output;
    private final ResourceHandler<FluidResource> router;
    private final ResourceHandler<FluidResource> outputHandler;
    private final ResourceHandler<FluidResource> all;
    private final ContainerData data;
    private long lastWorked = Long.MIN_VALUE / 2;
    private int lastMade;

    public HeavyWaterTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HEAVY_WATER_TOWER.get(), pos, state, 0, (slot, resource) -> false, SIDE_MODES);
        int capacity = 400_000;
        this.energy = new ConsumerEnergyHandler(capacity, capacity, this::setChanged);
        int tank = ArcforgeConfig.HEAVY_WATER_TANK_PER_BLOCK.getAsInt();
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = new FilteredFluidTank(tank, resource -> MachineRecipes.isIsotopeExchangeInput(level, resource), this::setChanged);
        }
        this.output = new FilteredFluidTank(tank, resource -> true, this::setChanged);
        this.router = new Router();
        this.outputHandler = new SidedResourceHandler<>(output, false, true, Integer.MAX_VALUE);
        this.all = new CombinedResourceHandler<>(router, outputHandler);
        this.data = new WideIntContainerData(HeavyWaterTowerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= HeavyWaterTowerMenu.DATA_INPUT && index < HeavyWaterTowerMenu.DATA_INPUT + 3) {
                    return inputs[index - HeavyWaterTowerMenu.DATA_INPUT].getAmount();
                }
                if (index >= HeavyWaterTowerMenu.DATA_INPUT_FLUID && index < HeavyWaterTowerMenu.DATA_INPUT_FLUID + 3) {
                    return fluidId(inputs[index - HeavyWaterTowerMenu.DATA_INPUT_FLUID]);
                }
                return switch (index) {
                    case HeavyWaterTowerMenu.DATA_STATUS -> status.ordinal();
                    case HeavyWaterTowerMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case HeavyWaterTowerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case HeavyWaterTowerMenu.DATA_SIDE_CONFIG -> packedSides();
                    case HeavyWaterTowerMenu.DATA_TRAYS -> box != null ? trays : 0;
                    case HeavyWaterTowerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case HeavyWaterTowerMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case HeavyWaterTowerMenu.DATA_OUTPUT -> output.getAmount();
                    case HeavyWaterTowerMenu.DATA_OUTPUT_FLUID -> fluidId(output);
                    case HeavyWaterTowerMenu.DATA_CAPACITY -> output.getCapacity();
                    case HeavyWaterTowerMenu.DATA_PROGRESS -> progress;
                    case HeavyWaterTowerMenu.DATA_TIME -> currentTime();
                    case HeavyWaterTowerMenu.DATA_LAST_MADE -> lastMade;
                    case HeavyWaterTowerMenu.DATA_ENERGY_USE -> energyPerTick();
                    default -> 0;
                };
            }
        };
    }

    // Fluids in through Input ports: into the tank already holding that fluid, else the first empty one.
    private final class Router implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return inputs.length;
        }

        @Override
        public FluidResource getResource(int index) {
            return inputs[index].getResource(0);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inputs[index].getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return inputs[index].getCapacity();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return inputs[index].isValid(0, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return target(resource) == index ? inputs[index].insert(0, resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            int index = target(resource);
            return index >= 0 ? inputs[index].insert(0, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        private int target(FluidResource resource) {
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i].getAmount() > 0 && inputs[i].getResource(0).equals(resource)) {
                    return i;
                }
            }
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i].getAmount() <= 0 && inputs[i].isValid(0, resource)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, Set.of(), () -> {});
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    @Override
    public VesselStructure structure() {
        return VesselStructures.HEAVY_WATER_TOWER;
    }

    @Override
    protected void onShapeChanged(ServerLevel level) {
        trays = box != null ? VesselStructures.count(level, box, ModBlocks.EXCHANGE_TRAY.get()) : 0;
        sizeTanks();
    }

    @Override
    public void requestCheck() {
        super.requestCheck();
        if (level instanceof ServerLevel serverLevel && box != null) {
            onShapeChanged(serverLevel);
        }
    }

    private void sizeTanks() {
        int tank = ArcforgeConfig.HEAVY_WATER_TANK_PER_BLOCK.getAsInt() * Math.max(1, box != null ? box.interiorVolume() : 1);
        for (FilteredFluidTank input : inputs) {
            input.setCapacity(tank);
        }
        output.setCapacity(tank);
    }

    public int getTrays() {
        return box != null ? trays : 0;
    }

    public int energyPerTick() {
        return getTrays() * ArcforgeConfig.HEAVY_WATER_ENERGY_PER_TRAY.getAsInt();
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getInput(int index) {
        return inputs[index];
    }

    public FilteredFluidTank getOutput() {
        return output;
    }

    private int currentTime() {
        RecipeHolder<IsotopeExchangingRecipe> recipe = recipe();
        return recipe != null ? recipe.value().time() : 0;
    }

    private @Nullable RecipeHolder<IsotopeExchangingRecipe> recipe() {
        List<FluidResource> held = new ArrayList<>();
        for (FilteredFluidTank input : inputs) {
            if (input.getAmount() > 0) {
                held.add(input.getResource(0));
            }
        }
        return held.isEmpty() ? null : MachineRecipes.isotopeExchanging(level, new IsotopeExchangingRecipe.Input(held)).orElse(null);
    }

    @Override
    protected void work(ServerLevel level) {
        lastMade = 0;
        status = run(level);
        setLit(level.getGameTime() - lastWorked < LIT_HOLD);
        MultiblockAutomation.pushFluids(level, this, Set.of(SideMode.OUTPUT), 16_000);
    }

    private MachineStatus run(ServerLevel level) {
        if (!controlState.isEnabled() || !redstoneMode.canRun(redstone > 0)) {
            progress = 0;
            return stoppedStatus();
        }
        RecipeHolder<IsotopeExchangingRecipe> holder = recipe();
        if (holder == null) {
            progress = 0;
            return MachineStatus.NO_FLUID;
        }
        IsotopeExchangingRecipe recipe = holder.value();
        FluidStack made = recipe.output().create();
        if (output.getAmount() > 0 && !output.getResource(0).equals(FluidResource.of(made)) || output.getSpace() < made.getAmount()) {
            return MachineStatus.TANK_FULL;
        }
        int wanted = energyPerTick();
        if (wanted > 0 && !energy.consume(wanted)) {
            return MachineStatus.NO_POWER;
        }
        progress++;
        lastWorked = level.getGameTime();
        if (progress < recipe.time()) {
            return MachineStatus.EXCHANGING;
        }
        progress = 0;
        // Every tray does one operation, as far as the inputs and the output tank go.
        int operations = trays;
        for (ChemicalReactingRecipe.FluidInput input : recipe.inputs()) {
            FilteredFluidTank tank = tankFor(input);
            operations = tank == null ? 0 : Math.min(operations, tank.getAmount() / input.amount());
        }
        operations = Math.min(operations, output.getSpace() / Math.max(1, made.getAmount()));
        if (operations <= 0) {
            return MachineStatus.MISSING_FLUID;
        }
        for (ChemicalReactingRecipe.FluidInput input : recipe.inputs()) {
            FilteredFluidTank tank = tankFor(input);
            int left = tank.getAmount() - input.amount() * operations;
            tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
        }
        output.set(0, FluidResource.of(made), output.getAmount() + made.getAmount() * operations);
        lastMade = made.getAmount() * operations;
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.getFluid(), "heavy_water");
        return MachineStatus.EXCHANGING;
    }

    private @Nullable FilteredFluidTank tankFor(ChemicalReactingRecipe.FluidInput input) {
        for (FilteredFluidTank tank : inputs) {
            if (tank.getAmount() > 0 && input.test(tank.getResource(0))) {
                return tank;
            }
        }
        return null;
    }

    // --- Capabilities ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return all;
        }
        return switch (mode) {
            case INPUT -> router;
            case OUTPUT -> outputHandler;
            default -> null;
        };
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (box == null) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID, GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        trays = in.getIntOr("trays", 0);
        progress = in.getIntOr("progress", 0);
        sizeTanks();
        energy.deserialize(in.childOrEmpty("energy"));
        for (int i = 0; i < inputs.length; i++) {
            inputs[i].deserialize(in.childOrEmpty("input_" + i));
        }
        output.deserialize(in.childOrEmpty("output"));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("trays", trays);
        out.putInt("progress", progress);
        energy.serialize(out.child("energy"));
        for (int i = 0; i < inputs.length; i++) {
            inputs[i].serialize(out.child("input_" + i));
        }
        output.serialize(out.child("output"));
    }

    @Override
    public Component getDisplayName() {
        return box != null ? Component.translatable("container.arcforge.heavy_water_tower.sized", box.sizeText())
                : Component.translatable("container.arcforge.heavy_water_tower");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HeavyWaterTowerMenu(containerId, inventory, worldPosition, items, data);
    }
}
