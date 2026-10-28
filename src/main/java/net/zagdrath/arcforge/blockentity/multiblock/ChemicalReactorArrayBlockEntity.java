/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.ChemicalReactorArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.ChemicalReactorArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.fluid.InputTankRouter;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.transfer.item.PairedItemInput;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Chemical Reactor Array (see CubeMultiblockBlockEntity): every arcforge:chemical_reacting recipe in three lanes at
// once, each lane CRA_TIME_MULTIPLIER of a Chemical Reactor's time (twice as fast by default). Each lane has its own three
// input slots, output slot and by-product slot; the lanes share three input tanks, an output tank and a by-product tank
// (CRA_TANK_CAPACITY each, four times a reactor's), the FE buffer and a heat buffer for heated recipes, which takes HU
// through Heat ports and runs those recipes only at their min_temperature. Items through Input ports go to the lane
// holding the fewest input items, sorted into its slots as a Chemical Reactor's are (PairedItemInput); fluids to the
// tanks by InputTankRouter. Lanes that run on the same fluids share them; a lane whose inputs another lane used up first
// waits at its last tick, keeping its place (and paying nothing more) until they're back. Speed and Energy upgrades as the reactor. Ports: Input, Output, By-product, Energy, Heat.
// The client gets each lane's fluid (what its recipe makes, else what it reacts) and whether it runs, for the vessels'
// sight glasses and agitators (ChemicalReactorArrayRenderer).
public class ChemicalReactorArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final int LANES = 3;
    public static final int SLOTS_PER_LANE = 5;
    public static final int MACHINE_SLOTS = LANES * SLOTS_PER_LANE;
    // A lane's slots, from its first: three inputs, the output, the by-product.
    public static final int INPUT_A = 0, INPUT_B = 1, INPUT_C = 2, OUTPUT = 3, BYPRODUCT = 4;
    public static final int TANKS = 5;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT, SideMode.ENERGY,
            SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank[] tanks = new FilteredFluidTank[TANKS];
    private final InputTankRouter router;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final ResourceHandler<FluidResource> fluidByproduct;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<ItemResource> laneInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemByproduct;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    private final Lane[] lanes = new Lane[LANES];
    private int heatUsage;
    // For the renderer (synced): each lane's fluid (registry id, -1 none) and the running lanes as bits.
    private final int[] laneFluids = { -1, -1, -1 };
    private int running;
    // On the client: each agitator's angle and when it was last turned (ChemicalReactorArrayRenderer).
    public final float[] agitatorAngles = new float[LANES];
    public float agitatorTime = -1.0F;

    // One lane: its progress on its recipe.
    private static final class Lane {
        int progress;
        int total;
        @Nullable Identifier current;
    }

    public ChemicalReactorArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHEMICAL_REACTOR_ARRAY.get(), pos, state, MACHINE_SLOTS, ChemicalReactorArrayBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE), MODES, true,
                ArcforgeConfig.CRA_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.CRA_MAX_INPUT.getAsInt(), allInputSlots(),
                slot -> slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE >= OUTPUT);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new Lane();
        }
        this.heat = new HeatBuffer(ArcforgeConfig.CRA_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.CRA_MAX_TEMPERATURE.getAsInt(), this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        int capacity = ArcforgeConfig.CRA_TANK_CAPACITY.getAsInt();
        for (int tank = 0; tank < 3; tank++) {
            tanks[tank] = new FilteredFluidTank(capacity, resource -> MachineRecipes.isReactorFluid(level, resource), this::setChanged);
        }
        tanks[3] = new FilteredFluidTank(capacity, resource -> true, this::setChanged);
        tanks[4] = new FilteredFluidTank(capacity, resource -> true, this::setChanged);
        gasVents.add(tanks[3], null);
        gasVents.add(tanks[4], null);
        this.router = new InputTankRouter(tanks[0], tanks[1], tanks[2]);
        this.fluidInput = new AutomationResourceHandler<>(router, index -> true, index -> false);
        this.fluidOutput = new AutomationResourceHandler<>(tanks[3], index -> false, index -> true);
        this.fluidByproduct = new AutomationResourceHandler<>(tanks[4], index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(fluidInput, fluidOutput, fluidByproduct);
        this.laneInput = new LaneInput(slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE == OUTPUT);
        this.itemByproduct = new AutomationResourceHandler<>(items, slot -> false, slot -> slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE == BYPRODUCT);
        this.itemAutomation = new LaneInput(slot -> slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE >= OUTPUT);
        this.data = new WideIntContainerData(ChemicalReactorArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= ChemicalReactorArrayMenu.DATA_PROGRESS_FIRST && index < ChemicalReactorArrayMenu.DATA_PROGRESS_FIRST + LANES) {
                    return lanes[index - ChemicalReactorArrayMenu.DATA_PROGRESS_FIRST].progress;
                }
                if (index >= ChemicalReactorArrayMenu.DATA_TOTAL_FIRST && index < ChemicalReactorArrayMenu.DATA_TOTAL_FIRST + LANES) {
                    return lanes[index - ChemicalReactorArrayMenu.DATA_TOTAL_FIRST].total;
                }
                if (index >= ChemicalReactorArrayMenu.DATA_FLUID_FIRST && index < ChemicalReactorArrayMenu.DATA_FLUID_FIRST + TANKS) {
                    return fluidId(tanks[index - ChemicalReactorArrayMenu.DATA_FLUID_FIRST]);
                }
                if (index >= ChemicalReactorArrayMenu.DATA_AMOUNT_FIRST && index < ChemicalReactorArrayMenu.DATA_AMOUNT_FIRST + TANKS) {
                    return tanks[index - ChemicalReactorArrayMenu.DATA_AMOUNT_FIRST].getAmount();
                }
                return switch (index) {
                    case ChemicalReactorArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ChemicalReactorArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ChemicalReactorArrayMenu.DATA_USAGE -> usage;
                    case ChemicalReactorArrayMenu.DATA_STATUS -> status.ordinal();
                    case ChemicalReactorArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ChemicalReactorArrayMenu.DATA_SIDE_CONFIG -> packedSides();
                    case ChemicalReactorArrayMenu.DATA_TANK_CAPACITY -> tanks[0].getCapacity();
                    case ChemicalReactorArrayMenu.DATA_HEAT -> heat.getStored();
                    case ChemicalReactorArrayMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case ChemicalReactorArrayMenu.DATA_HEAT_USAGE -> heatUsage;
                    case ChemicalReactorArrayMenu.DATA_RUNNING -> running;
                    default -> 0;
                };
            }
        };
    }

    public static int slot(int lane, int which) {
        return lane * SLOTS_PER_LANE + which;
    }

    private static int[] laneInputs(int lane) {
        return new int[] { slot(lane, INPUT_A), slot(lane, INPUT_B), slot(lane, INPUT_C) };
    }

    private static int[] allInputSlots() {
        int[] slots = new int[LANES * 3];
        for (int lane = 0; lane < LANES; lane++) {
            System.arraycopy(laneInputs(lane), 0, slots, lane * 3, 3);
        }
        return slots;
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // The lanes' input slots take anything some recipe reacts.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE <= INPUT_C && MachineRecipes.isReactorItem(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // Items in through Input ports: to the lane holding the fewest input items (each lane's slots as a reactor's).
    private class LaneInput extends AutomationResourceHandler<ItemResource> {
        private final PairedItemInput[] perLane = new PairedItemInput[LANES];

        LaneInput(java.util.function.IntPredicate canExtract) {
            super(items, slot -> slot < MACHINE_SLOTS && slot % SLOTS_PER_LANE <= INPUT_C, canExtract);
            for (int lane = 0; lane < LANES; lane++) {
                perLane[lane] = new PairedItemInput(items, laneInputs(lane), slot -> false);
            }
        }

        private int held(int lane) {
            int count = 0;
            for (int slot : laneInputs(lane)) {
                count += items.getStack(slot).getCount();
            }
            return count;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index < MACHINE_SLOTS ? perLane[index / SLOTS_PER_LANE].insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            Integer[] order = { 0, 1, 2 };
            Arrays.sort(order, Comparator.comparingInt(this::held));
            int inserted = 0;
            for (int lane : order) {
                if (inserted >= amount) {
                    break;
                }
                inserted += perLane[lane].insert(resource, amount - inserted, transaction);
            }
            return inserted;
        }
    }

    // --- Running ---

    // FE/t for one lane on this recipe: the recipe's times the array's multiplier; Speed draws it faster, Energy cuts it.
    public int energyPerTick(ChemicalReactingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * ArcforgeConfig.CRA_ENERGY_MULTIPLIER.getAsDouble() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(ChemicalReactingRecipe recipe) {
        return Math.max(1, (int) Math.round(UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED)) * ArcforgeConfig.CRA_TIME_MULTIPLIER.getAsDouble()));
    }

    // HU/t for one lane: an operation takes the heat a reactor's does, over its shorter time.
    public int heatPerTick(ChemicalReactingRecipe recipe) {
        return (int) Math.ceil(recipe.heatPerTick() * speedMultiplier() / ArcforgeConfig.CRA_TIME_MULTIPLIER.getAsDouble());
    }

    private ChemicalReactorInput input(int lane) {
        return new ChemicalReactorInput(items.getStack(slot(lane, INPUT_A)), items.getStack(slot(lane, INPUT_B)), tanks[0].getResource(0), tanks[0].getAmount(),
                tanks[1].getResource(0), tanks[1].getAmount(), tanks[2].getResource(0), tanks[2].getAmount(), items.getStack(slot(lane, INPUT_C)));
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return ChemicalReactorArrayCasingBlock.STRUCTURE;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickPorts(level);
        if (!isFormed()) {
            return;
        }
        usage = 0;
        heatUsage = 0;
        int[] fluids = { -1, -1, -1 };
        int nowRunning = 0;
        boolean anyRecipe = false, full = false, cold = false, noPower = false, missing = false;
        boolean enabled = canRun(level);
        for (int index = 0; index < LANES; index++) {
            Lane lane = lanes[index];
            ChemicalReactorInput input = input(index);
            RecipeHolder<ChemicalReactingRecipe> holder = MachineRecipes.chemicalReacting(level, input).orElse(null);
            Identifier id = holder != null ? holder.id().identifier() : null;
            // A lane done but waiting for inputs another lane took keeps its place until they're back.
            boolean waiting = id == null && lane.current != null && lane.total > 0 && lane.progress >= lane.total;
            if (!waiting && (id == null || !id.equals(lane.current))) {
                lane.progress = 0;
                lane.current = id;
            }
            if (holder == null) {
                if (waiting) {
                    missing = true;
                    continue;
                }
                lane.total = 0;
                for (int slot : laneInputs(index)) {
                    ItemStack item = items.getStack(slot);
                    missing |= !item.isEmpty() && MachineRecipes.isReactorItem(level, item);
                }
                continue;
            }
            anyRecipe = true;
            ChemicalReactingRecipe recipe = holder.value();
            lane.total = ticksFor(recipe);
            fluids[index] = shownFluid(recipe, input);
            if (!enabled) {
                continue;
            }
            if (!fits(index, recipe)) {
                full = true;
                continue;
            }
            if (lane.progress >= lane.total) {
                // Done, and its inputs are back: finish without paying again.
                if (finish(level, index, recipe)) {
                    lane.progress = 0;
                }
                continue;
            }
            if (recipe.isHeated() && (heat.getTemperature() < recipe.minTemperature() || heat.getStored() < heatPerTick(recipe))) {
                cold = true;
                continue;
            }
            int fe = energyPerTick(recipe);
            if (!energy.consume(fe)) {
                noPower = true;
                continue;
            }
            usage += fe;
            if (recipe.isHeated()) {
                heatUsage += heat.remove(heatPerTick(recipe));
            }
            nowRunning |= 1 << index;
            lane.progress++;
            if (lane.progress >= lane.total && finish(level, index, recipe)) {
                lane.progress = 0;
            }
        }
        if (!enabled) {
            status = stoppedStatus();
        } else if (nowRunning != 0) {
            status = MachineStatus.REACTING;
        } else if (full) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (cold) {
            status = MachineStatus.TOO_COLD;
        } else if (noPower) {
            status = MachineStatus.NO_POWER;
        } else {
            status = !anyRecipe && missing ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        }
        if (nowRunning != 0) {
            setChanged();
        }
        setLit(nowRunning != 0);
        if (nowRunning != running || !Arrays.equals(fluids, laneFluids)) {
            running = nowRunning;
            System.arraycopy(fluids, 0, laneFluids, 0, LANES);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, false, ArcforgeConfig.CRA_OUTPUT_RATE.getAsInt());
        }
    }

    // The fluid a lane's sight glass shows: what its recipe makes, else the first fluid it reacts.
    private int shownFluid(ChemicalReactingRecipe recipe, ChemicalReactorInput input) {
        if (recipe.fluidOutput().isPresent()) {
            return BuiltInRegistries.FLUID.getId(recipe.fluidOutput().get().create().getFluid());
        }
        int[] used = recipe.tanksFor(input);
        if (used != null && used.length > 0) {
            return fluidId(tanks[used[0]]);
        }
        return -1;
    }

    // Whether everything the recipe makes fits this lane's slots and the shared output tanks now.
    private boolean fits(int lane, ChemicalReactingRecipe recipe) {
        if (recipe.itemOutput().isPresent() && !fits(items.getStack(slot(lane, OUTPUT)), recipe.itemOutput().get().create())) {
            return false;
        }
        if (recipe.byproduct().isPresent() && !fits(items.getStack(slot(lane, BYPRODUCT)), recipe.byproduct().get().create())) {
            return false;
        }
        if (recipe.fluidOutput().isPresent() && !fitsTank(tanks[3], recipe.fluidOutput().get().create())) {
            return false;
        }
        return recipe.fluidByproduct().isEmpty() || fitsTank(tanks[4], recipe.fluidByproduct().get().create());
    }

    private static boolean fitsTank(FilteredFluidTank tank, FluidStack fluid) {
        try (Transaction tx = Transaction.openRoot()) {
            return tank.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx) == fluid.getAmount();
        }
    }

    private static boolean fits(ItemStack slot, ItemStack product) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, product) && slot.getCount() + product.getCount() <= slot.getMaxStackSize();
    }

    // Finishes an operation: takes the inputs (as they are now; false when another lane used them up first, so this
    // one waits) and gives the products.
    private boolean finish(ServerLevel level, int lane, ChemicalReactingRecipe recipe) {
        ChemicalReactorInput input = input(lane);
        if (!recipe.matches(input, level)) {
            return false;
        }
        int[] used = recipe.tanksFor(input);
        int[] slots = recipe.slotsFor(input);
        if (used == null || slots == null) {
            return false;
        }
        int[] laneSlots = laneInputs(lane);
        List<ChemicalReactingRecipe.ItemInput> needed = recipe.itemInputs();
        ItemStack product = recipe.itemOutput().map(template -> template.create()).orElse(ItemStack.EMPTY);
        int itemsUsed = 0;
        for (int i = 0; i < slots.length; i++) {
            ChemicalReactingRecipe.ItemInput wanted = needed.get(i);
            if (wanted.isCatalyst() && level.getRandom().nextFloat() >= wanted.consumeChance()) {
                continue;
            }
            int slot = laneSlots[slots[i]];
            ItemStack stack = items.getStack(slot);
            items.setStack(slot, stack.copyWithCount(stack.getCount() - wanted.count()));
            itemsUsed += wanted.count();
        }
        int fluidUsed = 0;
        List<FluidStack> madeFluids = new ArrayList<>();
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < used.length; i++) {
                FilteredFluidTank tank = tanks[used[i]];
                fluidUsed += tank.extract(0, tank.getResource(0), recipe.fluidInputs().get(i).amount(), tx);
            }
            recipe.fluidOutput().ifPresent(template -> {
                FluidStack fluid = template.create();
                tanks[3].insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
                madeFluids.add(fluid);
            });
            recipe.fluidByproduct().ifPresent(template -> {
                FluidStack fluid = template.create();
                tanks[4].insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
                madeFluids.add(fluid);
            });
            tx.commit();
        }
        List<ItemStack> madeItems = new ArrayList<>();
        if (!product.isEmpty()) {
            madeItems.add(product.copy());
            add(slot(lane, OUTPUT), product);
        }
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            ItemStack byproduct = recipe.byproduct().get().create();
            madeItems.add(byproduct.copy());
            add(slot(lane, BYPRODUCT), byproduct);
        }
        controlState.completed(madeItems, madeFluids, itemsUsed, fluidUsed);
        Fluid made = recipe.fluidOutput().map(template -> template.create().getFluid()).orElse(null);
        ArcforgeAdvancements.produced(this, product, made, recipe.category());
        return true;
    }

    private void add(int slot, ItemStack product) {
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? product : current.copyWithCount(current.getCount() + product.getCount()));
    }

    // --- For the renderer, Jade and tests ---

    // The fluid a lane's sight glass shows (registry id, -1 for none).
    public int laneFluid(int lane) {
        return laneFluids[lane];
    }

    public boolean isLaneRunning(int lane) {
        return (running & (1 << lane)) != 0;
    }

    public int getProgress(int lane) {
        return lanes[lane].progress;
    }

    public int getTotal(int lane) {
        return lanes[lane].total;
    }

    public FilteredFluidTank getTank(int tank) {
        return tanks[tank];
    }

    public InputTankRouter getRouter() {
        return router;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> laneInput;
            case OUTPUT -> itemOutput;
            case BYPRODUCT -> itemByproduct;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> fluidInput;
            case OUTPUT -> fluidOutput;
            case BYPRODUCT -> fluidByproduct;
            default -> null;
        };
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatInput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.OUTPUT || mode == SideMode.BYPRODUCT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        String[] names = { "input_a", "input_b", "input_c", "output_tank", "byproduct_tank" };
        for (int tank = 0; tank < TANKS; tank++) {
            tanks[tank].deserialize(input.childOrEmpty(names[tank]));
        }
        int lane = 0;
        for (ValueInput entry : input.childrenListOrEmpty("lanes")) {
            if (lane < LANES) {
                lanes[lane].progress = entry.getIntOr("progress", 0);
                lanes[lane].current = entry.getString("recipe").map(Identifier::tryParse).orElse(null);
                lane++;
            }
        }
        for (int i = 0; i < LANES; i++) {
            laneFluids[i] = input.getIntOr("lane_fluid_" + i, -1);
        }
        running = input.getIntOr("running", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        String[] names = { "input_a", "input_b", "input_c", "output_tank", "byproduct_tank" };
        for (int tank = 0; tank < TANKS; tank++) {
            tanks[tank].serialize(output.child(names[tank]));
        }
        ValueOutput.ValueOutputList list = output.childrenList("lanes");
        for (Lane lane : lanes) {
            ValueOutput entry = list.addChild();
            entry.putInt("progress", lane.progress);
            if (lane.current != null) {
                entry.putString("recipe", lane.current.toString());
            }
        }
        for (int i = 0; i < LANES; i++) {
            output.putInt("lane_fluid_" + i, laneFluids[i]);
        }
        output.putInt("running", running);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.chemical_reactor_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChemicalReactorArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
