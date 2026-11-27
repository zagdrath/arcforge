/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.fusion;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.fusion.FusionExhaustPool;
import net.zagdrath.arcforge.fusion.Tritium;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.fusion.FusionPlantMenu;
import net.zagdrath.arcforge.multiblock.FusionPlants;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.recipe.CryogenicDistillingRecipe;
import net.zagdrath.arcforge.recipe.LiquefyingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The fusion tier's three plant vessels (see FusionPlants), one fluid in and one to four out, FE through Energy
// ports:
//  - the Cryogenic Plant liquefies the gas in its input tank (arcforge:liquefying: Nitrogen into Liquid Nitrogen, Helium
//    into Liquid Helium), cryoPlantRatePerBlock mB of gas a tick for every block inside it, at the recipe's FE;
//  - the Cryogenic Distillation Column runs its arcforge:cryogenic_distilling recipe columnOperationsPerStage times per stage (each block of its
//    inside's height) every recipe's time ticks: on its feed, or on the air round it when it has none;
//  - the Tritium Plant takes Fusion Exhaust, plantRatePerBlock mB a tick for every block inside, and splits it by
//    what it holds (FusionExhaustPool) into Deuterium, Tritium, Helium-3 and Helium. The Tritium it holds decays into
//    its Helium-3 tank, as stored tritium does.
// Products come out of the output ports, one product to a port mode (Output, Byproduct, Tails, Gas Output), pushed
// into what touches them.
public class FusionPlantBlockEntity extends VesselControllerBlockEntity {
    public enum Kind {
        CRYOGENIC_PLANT("cryogenic_plant", 1), CRYOGENIC_COLUMN("cryogenic_column", 3), TRITIUM_PLANT("tritium_plant", 4);

        private final String id;
        private final int outputs;

        Kind(String id, int outputs) {
            this.id = id;
            this.outputs = outputs;
        }

        public String id() {
            return id;
        }

        public int outputs() {
            return outputs;
        }

        public VesselStructure structure() {
            return switch (this) {
                case CRYOGENIC_PLANT -> FusionPlants.CRYOGENIC_PLANT;
                case CRYOGENIC_COLUMN -> FusionPlants.CRYOGENIC_COLUMN;
                case TRITIUM_PLANT -> FusionPlants.TRITIUM_PLANT;
            };
        }

        public Supplier<BlockEntityType<FusionPlantBlockEntity>> type() {
            return switch (this) {
                case CRYOGENIC_PLANT -> ModBlockEntityTypes.CRYOGENIC_PLANT;
                case CRYOGENIC_COLUMN -> ModBlockEntityTypes.CRYOGENIC_COLUMN;
                case TRITIUM_PLANT -> ModBlockEntityTypes.TRITIUM_PLANT;
            };
        }

        public Block controller() {
            return switch (this) {
                case CRYOGENIC_PLANT -> ModBlocks.CRYOGENIC_PLANT_CONTROLLER.get();
                case CRYOGENIC_COLUMN -> ModBlocks.CRYOGENIC_COLUMN_CONTROLLER.get();
                case TRITIUM_PLANT -> ModBlocks.TRITIUM_PLANT_CONTROLLER.get();
            };
        }

        public static Kind of(int ordinal) {
            Kind[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, ordinal))];
        }
    }

    // The port mode each product comes out of, in order.
    public static final List<SideMode> OUTPUT_MODES = List.of(SideMode.OUTPUT, SideMode.BYPRODUCT, SideMode.TAILS, SideMode.GAS_OUTPUT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT, SideMode.TAILS,
            SideMode.GAS_OUTPUT, SideMode.ENERGY);
    private static final int LIT_HOLD = 40;
    private static final int ENERGY_CAPACITY = 1_000_000;

    private final Kind kind;
    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank input;
    private final FilteredFluidTank[] outputs;
    private final List<ResourceHandler<FluidResource>> outputHandlers;
    private final ResourceHandler<FluidResource> inputHandler;
    private final ResourceHandler<FluidResource> all;
    private final ContainerData data;
    // Gas waiting to be liquefied or exhaust to be split, carried between ticks (mB, fractional for the exhaust's
    // shares).
    private int credit;
    private final double[] split = new double[4];
    private int progress;
    private int lastMade;
    private int lastUse;
    // Tritium decayed in the Tritium Plant's tank but not yet a whole mB of Helium-3.
    private double decayed;
    private long lastWorked = Long.MIN_VALUE / 2;

    public FusionPlantBlockEntity(Kind kind, BlockPos pos, BlockState state) {
        super(kind.type().get(), pos, state, 0, (slot, resource) -> false, SIDE_MODES);
        this.kind = kind;
        this.energy = new ConsumerEnergyHandler(ENERGY_CAPACITY, ENERGY_CAPACITY, this::setChanged);
        int tank = tankSize(kind);
        this.input = new FilteredFluidTank(tank, this::accepts, this::setChanged);
        this.outputs = new FilteredFluidTank[kind.outputs()];
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = new FilteredFluidTank(tank, resource -> true, this::setChanged);
        }
        this.outputHandlers = java.util.Arrays.stream(outputs).<ResourceHandler<FluidResource>>map(t -> new SidedResourceHandler<>(t, false, true,
                Integer.MAX_VALUE)).toList();
        this.inputHandler = new SidedResourceHandler<>(input, true, false, Integer.MAX_VALUE);
        @SuppressWarnings("unchecked")
        ResourceHandler<FluidResource>[] parts = new ResourceHandler[outputs.length + 1];
        parts[0] = inputHandler;
        for (int i = 0; i < outputs.length; i++) {
            parts[i + 1] = outputHandlers.get(i);
        }
        this.all = new CombinedResourceHandler<>(parts);
        this.data = new WideIntContainerData(FusionPlantMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= FusionPlantMenu.DATA_OUTPUT && index < FusionPlantMenu.DATA_OUTPUT + 4) {
                    int i = index - FusionPlantMenu.DATA_OUTPUT;
                    return i < outputs.length ? outputs[i].getAmount() : 0;
                }
                if (index >= FusionPlantMenu.DATA_OUTPUT_FLUID && index < FusionPlantMenu.DATA_OUTPUT_FLUID + 4) {
                    int i = index - FusionPlantMenu.DATA_OUTPUT_FLUID;
                    return i < outputs.length ? fluidId(outputs[i]) : -1;
                }
                return switch (index) {
                    case FusionPlantMenu.DATA_KIND -> kind.ordinal();
                    case FusionPlantMenu.DATA_STATUS -> status.ordinal();
                    case FusionPlantMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case FusionPlantMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FusionPlantMenu.DATA_SIDE_CONFIG -> packedSides();
                    case FusionPlantMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case FusionPlantMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case FusionPlantMenu.DATA_INPUT -> input.getAmount();
                    case FusionPlantMenu.DATA_INPUT_FLUID -> fluidId(input);
                    case FusionPlantMenu.DATA_CAPACITY -> input.getCapacity();
                    case FusionPlantMenu.DATA_SIZE -> size();
                    case FusionPlantMenu.DATA_PROGRESS -> progress;
                    case FusionPlantMenu.DATA_TIME -> currentTime();
                    case FusionPlantMenu.DATA_LAST_MADE -> lastMade;
                    case FusionPlantMenu.DATA_ENERGY_USE -> lastUse;
                    default -> 0;
                };
            }
        };
    }

    public static FusionPlantBlockEntity cryogenicPlant(BlockPos pos, BlockState state) {
        return new FusionPlantBlockEntity(Kind.CRYOGENIC_PLANT, pos, state);
    }

    public static FusionPlantBlockEntity cryogenicColumn(BlockPos pos, BlockState state) {
        return new FusionPlantBlockEntity(Kind.CRYOGENIC_COLUMN, pos, state);
    }

    public static FusionPlantBlockEntity tritiumPlant(BlockPos pos, BlockState state) {
        return new FusionPlantBlockEntity(Kind.TRITIUM_PLANT, pos, state);
    }

    private static int tankSize(Kind kind) {
        return switch (kind) {
            case CRYOGENIC_PLANT -> ArcforgeConfig.CRYO_PLANT_TANK.getAsInt();
            case CRYOGENIC_COLUMN -> ArcforgeConfig.CRYO_COLUMN_TANK.getAsInt();
            case TRITIUM_PLANT -> ArcforgeConfig.TRITIUM_PLANT_TANK.getAsInt();
        };
    }

    private boolean accepts(FluidResource resource) {
        return switch (kind) {
            case CRYOGENIC_PLANT -> MachineRecipes.isLiquefiable(level, resource);
            case CRYOGENIC_COLUMN -> MachineRecipes.isCryogenicFeed(level, resource);
            case TRITIUM_PLANT -> resource.is(ModFluids.FUSION_EXHAUST.get());
        };
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, Set.of(), () -> {});
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public VesselStructure structure() {
        return kind.structure();
    }

    // The column's stages (its inside's height); the plants' inside volume.
    public int size() {
        if (box == null) {
            return 0;
        }
        return kind == Kind.CRYOGENIC_COLUMN ? box.size(Direction.Axis.Y) - 2 : box.interiorVolume();
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // FE it used last tick.
    public int getEnergyUse() {
        return lastUse;
    }

    public FilteredFluidTank getInput() {
        return input;
    }

    public FilteredFluidTank getOutput(int index) {
        return outputs[index];
    }

    private int currentTime() {
        if (kind != Kind.CRYOGENIC_COLUMN) {
            return 0;
        }
        RecipeHolder<CryogenicDistillingRecipe> recipe = column();
        return recipe != null ? recipe.value().time() : 0;
    }

    private @Nullable RecipeHolder<CryogenicDistillingRecipe> column() {
        FluidResource feed = input.getAmount() > 0 ? input.getResource(0) : FluidResource.EMPTY;
        return MachineRecipes.cryogenicDistilling(level, feed).orElse(null);
    }

    @Override
    protected void work(ServerLevel level) {
        lastMade = 0;
        lastUse = 0;
        if (kind == Kind.TRITIUM_PLANT && level.getGameTime() % 20 == 0) {
            decay();
        }
        status = run(level);
        setLit(level.getGameTime() - lastWorked < LIT_HOLD);
        MultiblockAutomation.pushFluids(level, this, Set.copyOf(OUTPUT_MODES.subList(0, outputs.length)), 16_000);
    }

    private MachineStatus run(ServerLevel level) {
        if (!controlState.isEnabled() || !redstoneMode.canRun(redstone > 0)) {
            progress = 0;
            return stoppedStatus();
        }
        return switch (kind) {
            case CRYOGENIC_PLANT -> liquefy(level);
            case CRYOGENIC_COLUMN -> distil(level);
            case TRITIUM_PLANT -> separate(level);
        };
    }

    // Whether amount mB of fluid fits output i (empty, or holding the same fluid with room).
    private boolean fits(int i, FluidStack stack) {
        FilteredFluidTank tank = outputs[i];
        return (tank.getAmount() <= 0 || tank.getResource(0).equals(FluidResource.of(stack))) && tank.getSpace() >= stack.getAmount();
    }

    private int room(int i, FluidStack stack) {
        FilteredFluidTank tank = outputs[i];
        if (tank.getAmount() > 0 && !tank.getResource(0).equals(FluidResource.of(stack))) {
            return 0;
        }
        return tank.getSpace();
    }

    private void fill(int i, FluidResource resource, int amount) {
        if (amount > 0) {
            FilteredFluidTank tank = outputs[i];
            tank.set(0, resource, tank.getAmount() + amount);
        }
    }

    private void drain(FilteredFluidTank tank, int amount) {
        int left = tank.getAmount() - amount;
        tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
    }

    private MachineStatus liquefy(ServerLevel level) {
        if (input.getAmount() <= 0) {
            credit = 0;
            return MachineStatus.NO_FLUID;
        }
        LiquefyingRecipe recipe = MachineRecipes.liquefying(level, input.getResource(0)).map(RecipeHolder::value).orElse(null);
        if (recipe == null) {
            return MachineStatus.NO_FEED;
        }
        FluidStack made = recipe.result().create();
        int in = Math.max(1, recipe.input().amount());
        if (!fits(0, made)) {
            return MachineStatus.TANK_FULL;
        }
        int rate = size() * ArcforgeConfig.CRYO_PLANT_RATE.getAsInt();
        credit = Math.min(credit + rate, Math.max(rate, in));
        int operations = Math.min(credit / in, input.getAmount() / in);
        operations = Math.min(operations, room(0, made) / Math.max(1, made.getAmount()));
        if (recipe.energy() > 0) {
            operations = Math.min(operations, energy.getAmountAsInt() / recipe.energy());
            if (operations <= 0 && credit >= in) {
                return MachineStatus.NO_POWER;
            }
        }
        lastWorked = level.getGameTime();
        if (operations <= 0) {
            return MachineStatus.LIQUEFYING;
        }
        int use = operations * recipe.energy();
        energy.consume(use);
        lastUse = use;
        credit -= operations * in;
        drain(input, operations * in);
        fill(0, FluidResource.of(made), operations * made.getAmount());
        lastMade = operations * made.getAmount();
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.getFluid(), "cryogenics");
        return MachineStatus.LIQUEFYING;
    }

    private MachineStatus distil(ServerLevel level) {
        RecipeHolder<CryogenicDistillingRecipe> holder = column();
        if (holder == null) {
            progress = 0;
            return input.getAmount() > 0 ? MachineStatus.NO_FEED : MachineStatus.NO_FLUID;
        }
        CryogenicDistillingRecipe recipe = holder.value();
        List<FluidStack> made = recipe.outputs().stream().map(template -> template.create()).toList();
        for (int i = 0; i < made.size() && i < outputs.length; i++) {
            if (!fits(i, made.get(i))) {
                // Air is only drawn while the products have somewhere to go (a feed's products may still fill them).
                return recipe.isAir() ? MachineStatus.NO_FLUID : MachineStatus.TANK_FULL;
            }
        }
        int wanted = size() * ArcforgeConfig.CRYO_COLUMN_ENERGY.getAsInt();
        if (wanted > 0 && !energy.consume(wanted)) {
            return MachineStatus.NO_POWER;
        }
        lastUse = wanted;
        lastWorked = level.getGameTime();
        progress++;
        if (progress < recipe.time()) {
            return MachineStatus.DISTILLING;
        }
        progress = 0;
        int operations = size() * ArcforgeConfig.CRYO_COLUMN_OPERATIONS.getAsInt();
        int in = recipe.input().map(fluidInput -> fluidInput.amount()).orElse(0);
        if (in > 0) {
            operations = Math.min(operations, input.getAmount() / in);
        }
        for (int i = 0; i < made.size() && i < outputs.length; i++) {
            operations = Math.min(operations, room(i, made.get(i)) / Math.max(1, made.get(i).getAmount()));
        }
        if (operations <= 0) {
            return MachineStatus.MISSING_FLUID;
        }
        if (in > 0) {
            drain(input, in * operations);
        }
        for (int i = 0; i < made.size() && i < outputs.length; i++) {
            fill(i, FluidResource.of(made.get(i)), made.get(i).getAmount() * operations);
            lastMade += made.get(i).getAmount() * operations;
        }
        if (!made.isEmpty()) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.get(made.size() - 1).getFluid(), "cryogenics");
        }
        return MachineStatus.DISTILLING;
    }

    private static final Supplier<List<Fluid>> PRODUCTS = () -> List.of(ModFluids.DEUTERIUM.get(), ModFluids.TRITIUM.get(), ModFluids.HELIUM_3.get(),
            ModFluids.HELIUM.get());

    private MachineStatus separate(ServerLevel level) {
        if (input.getAmount() <= 0) {
            return MachineStatus.NO_FLUID;
        }
        FusionExhaustPool pool = FusionExhaustPool.get(level.getServer());
        FusionExhaustPool.Shares shares = pool.shares();
        double[] share = { shares.deuterium(), shares.tritium(), shares.helium3(), shares.helium() };
        List<Fluid> products = PRODUCTS.get();
        int amount = Math.min(input.getAmount(), size() * ArcforgeConfig.TRITIUM_PLANT_RATE.getAsInt());
        for (int i = 0; i < 4; i++) {
            if (share[i] > 1.0E-9) {
                int room = room(i, new FluidStack(products.get(i), 1));
                amount = (int) Math.min(amount, Math.max(0.0, room - split[i]) / share[i]);
            }
        }
        if (amount <= 0) {
            return MachineStatus.TANK_FULL;
        }
        int perMb = ArcforgeConfig.TRITIUM_PLANT_ENERGY.getAsInt();
        if (perMb > 0) {
            amount = Math.min(amount, energy.getAmountAsInt() / perMb);
            if (amount <= 0) {
                return MachineStatus.NO_POWER;
            }
            energy.consume(amount * perMb);
            lastUse = amount * perMb;
        }
        drain(input, amount);
        pool.take(amount);
        for (int i = 0; i < 4; i++) {
            split[i] += amount * share[i];
            int whole = (int) split[i];
            if (whole > 0) {
                fill(i, FluidResource.of(products.get(i)), whole);
                split[i] -= whole;
                lastMade += whole;
            }
        }
        lastWorked = level.getGameTime();
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.TRITIUM.get(), "tritium");
        return MachineStatus.SEPARATING;
    }

    // Once a second: the Tritium it holds decays into Helium-3 (what doesn't fit is lost as harmless gas).
    private void decay() {
        FilteredFluidTank tritium = outputs[1];
        if (tritium.getAmount() <= 0 || !Tritium.is(tritium.getResource(0))) {
            return;
        }
        decayed += tritium.getAmount() * Tritium.decayFraction(20);
        int whole = Math.min((int) decayed, tritium.getAmount());
        if (whole > 0) {
            decayed -= whole;
            drain(tritium, whole);
            fill(2, FluidResource.of(ModFluids.HELIUM_3.get()), Math.min(whole, room(2, new FluidStack(ModFluids.HELIUM_3.get(), 1))));
        }
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
        if (mode == SideMode.INPUT) {
            return inputHandler;
        }
        int index = OUTPUT_MODES.indexOf(mode);
        return index >= 0 && index < outputHandlers.size() ? outputHandlers.get(index) : null;
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
            case FLUID, GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : OUTPUT_MODES.indexOf(mode) >= 0 && OUTPUT_MODES.indexOf(mode) < outputs.length ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
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
        progress = in.getIntOr("progress", 0);
        credit = in.getIntOr("credit", 0);
        decayed = in.getDoubleOr("decayed", 0.0);
        energy.deserialize(in.childOrEmpty("energy"));
        input.deserialize(in.childOrEmpty("input"));
        for (int i = 0; i < outputs.length; i++) {
            outputs[i].deserialize(in.childOrEmpty("output_" + i));
            split[i] = in.getDoubleOr("split_" + i, 0.0);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("progress", progress);
        out.putInt("credit", credit);
        out.putDouble("decayed", decayed);
        energy.serialize(out.child("energy"));
        input.serialize(out.child("input"));
        for (int i = 0; i < outputs.length; i++) {
            outputs[i].serialize(out.child("output_" + i));
            out.putDouble("split_" + i, split[i]);
        }
    }

    @Override
    public Component getDisplayName() {
        String key = "container.arcforge." + kind.id();
        return box != null ? Component.translatable(key + ".sized", box.sizeText()) : Component.translatable(key);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FusionPlantMenu(kind, containerId, inventory, worldPosition, items, data);
    }
}
