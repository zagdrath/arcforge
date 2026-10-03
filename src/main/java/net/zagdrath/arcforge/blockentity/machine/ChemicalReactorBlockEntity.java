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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ChemicalReactorMenu;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.fluid.InputTankRouter;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.transfer.item.PairedItemInput;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Reacts up to two items and up to three fluids with FE (arcforge:chemical_reacting recipes) into an item, a fluid or
// both, plus a chance byproduct: Sulfuric Acid from sulfur and water, ore slurry from raw ore and acid, dust from
// slurry and water, NPK Fertilizer from Basic Slag, Wood Ash and Ammonia, Brine from Salt and water, Biodiesel from Seed
// Oil, Ethanol and Lye, Ethylene (and Water) from Ethanol, Plastic and PVC Sheet from Ethylene, Lithium Brine from Spodumene Dust and Sulfuric Acid,
// Lithium Hydroxide from Lithium Brine and Lye, LFP Cathodes from three items. Three input slots, sorted by
// PairedItemInput; three input tanks, sorted by InputTankRouter; one output tank and one by-product tank (for a recipe's
// fluid_byproduct, leaving by By-product faces), 8,000 mB each. The third input tank and the by-product tank came later:
// older saves load with them empty.
// 60 FE/t unless the recipe says otherwise. Speed upgrades make it faster (drawing FE just as much faster),
// Energy upgrades cut the FE per operation.
public class ChemicalReactorBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_BYPRODUCT = 2;
    // The second and third input slots, for recipes with two or three items (each added after the others, so saves keep
    // their slots).
    public static final int SLOT_INPUT_B = 3;
    public static final int SLOT_INPUT_C = 4;
    public static final int MACHINE_SLOTS = 5;
    private static final int[] INPUT_SLOTS = { SLOT_INPUT, SLOT_INPUT_B, SLOT_INPUT_C };
    // Saves from before the second input slot (layout 1) had the upgrade slots where it is now; saves from before the
    // third (layout 2) had them where that one is.
    private static final int SLOT_LAYOUT = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BYPRODUCT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank inputA;
    private final FilteredFluidTank inputB;
    private final FilteredFluidTank inputC;
    private final FilteredFluidTank output;
    private final FilteredFluidTank byproductTank;
    private final InputTankRouter router;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final ResourceHandler<FluidResource> fluidByproduct;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemByproduct;
    private final ResourceHandler<ItemResource> itemEject;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // The recipe running now (progress starts over when it changes), or null.
    private @Nullable Identifier current;
    // What it makes, for Jade.
    private ItemStack makingItem = ItemStack.EMPTY;
    private FluidStack makingFluid = FluidStack.EMPTY;

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHEMICAL_REACTOR.get(), pos, state, MACHINE_SLOTS, ChemicalReactorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.REACTOR_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.REACTOR_MAX_INPUT.getAsInt(),
                this::setChanged);
        int capacity = ArcforgeConfig.REACTOR_TANK_CAPACITY.getAsInt();
        this.inputA = new FilteredFluidTank(capacity, resource -> MachineRecipes.isReactorFluid(level, resource), this::setChanged);
        this.inputB = new FilteredFluidTank(capacity, resource -> MachineRecipes.isReactorFluid(level, resource), this::setChanged);
        this.inputC = new FilteredFluidTank(capacity, resource -> MachineRecipes.isReactorFluid(level, resource), this::setChanged);
        this.output = new FilteredFluidTank(capacity, resource -> true, this::setChanged);
        this.byproductTank = new FilteredFluidTank(capacity, resource -> true, this::setChanged);
        this.router = new InputTankRouter(inputA, inputB, inputC);
        this.fluidInput = new AutomationResourceHandler<>(router, index -> true, index -> false);
        this.fluidOutput = new AutomationResourceHandler<>(output, index -> false, index -> true);
        this.fluidByproduct = new AutomationResourceHandler<>(byproductTank, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(inputA, inputB, inputC, output, byproductTank);
        // Buckets pour into the inputs and fill from the output first, then the by-product tank, then the inputs.
        this.fluidInteraction = new CombinedResourceHandler<>(fluidOutput, fluidByproduct, router);
        this.itemInput = new PairedItemInput(items, INPUT_SLOTS, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemByproduct = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_BYPRODUCT);
        this.itemEject = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT || slot == SLOT_BYPRODUCT);
        this.itemAutomation = new PairedItemInput(items, INPUT_SLOTS, slot -> slot == SLOT_OUTPUT || slot == SLOT_BYPRODUCT);
        this.data = new WideIntContainerData(ChemicalReactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ChemicalReactorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ChemicalReactorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ChemicalReactorMenu.DATA_USAGE -> usage;
                    case ChemicalReactorMenu.DATA_PROGRESS -> progress;
                    case ChemicalReactorMenu.DATA_TOTAL -> total;
                    case ChemicalReactorMenu.DATA_FLUID_A -> fluidId(inputA);
                    case ChemicalReactorMenu.DATA_AMOUNT_A -> inputA.getAmount();
                    case ChemicalReactorMenu.DATA_FLUID_B -> fluidId(inputB);
                    case ChemicalReactorMenu.DATA_AMOUNT_B -> inputB.getAmount();
                    case ChemicalReactorMenu.DATA_FLUID_C -> fluidId(inputC);
                    case ChemicalReactorMenu.DATA_AMOUNT_C -> inputC.getAmount();
                    case ChemicalReactorMenu.DATA_FLUID_OUT -> fluidId(output);
                    case ChemicalReactorMenu.DATA_AMOUNT_OUT -> output.getAmount();
                    case ChemicalReactorMenu.DATA_TANK_CAPACITY -> output.getCapacity();
                    case ChemicalReactorMenu.DATA_STATUS -> status.ordinal();
                    case ChemicalReactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ChemicalReactorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case ChemicalReactorMenu.DATA_FLUID_BYPRODUCT -> fluidId(byproductTank);
                    case ChemicalReactorMenu.DATA_AMOUNT_BYPRODUCT -> byproductTank.getAmount();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // The input slots take anything some recipe reacts. The client passes a null level and checks against the
    // recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return (slot == SLOT_INPUT || slot == SLOT_INPUT_B || slot == SLOT_INPUT_C) && MachineRecipes.isReactorItem(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t for this recipe: Speed draws it faster, Energy cuts it, so an operation costs base x 0.8^n.
    public int energyPerTick(ChemicalReactingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // FE/t for a recipe at the configured rate, for the GUI and tests.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.REACTOR_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(ChemicalReactingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    private ChemicalReactorInput input() {
        return new ChemicalReactorInput(items.getStack(SLOT_INPUT), items.getStack(SLOT_INPUT_B), inputA.getResource(0), inputA.getAmount(),
                inputB.getResource(0), inputB.getAmount(), inputC.getResource(0), inputC.getAmount(), items.getStack(SLOT_INPUT_C));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        ChemicalReactorInput input = input();
        RecipeHolder<ChemicalReactingRecipe> holder = MachineRecipes.chemicalReacting(level, input).orElse(null);
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            current = id;
        }
        if (holder == null) {
            total = 0;
            makingItem = ItemStack.EMPTY;
            makingFluid = FluidStack.EMPTY;
        } else {
            ChemicalReactingRecipe recipe = holder.value();
            total = ticksFor(recipe);
            makingItem = recipe.itemOutput().map(template -> template.create()).orElse(ItemStack.EMPTY);
            makingFluid = recipe.fluidOutput().map(template -> template.create()).orElse(FluidStack.EMPTY);
        }

        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (holder == null) {
            // Something to react but not the fluids (or the other item) for it.
            boolean reactive = false;
            for (int slot : INPUT_SLOTS) {
                ItemStack item = items.getStack(slot);
                reactive |= !item.isEmpty() && MachineRecipes.isReactorItem(level, item);
            }
            status = reactive ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        } else if (!fits(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(holder.value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.REACTING;
            usage = energyPerTick(holder.value());
            if (++progress >= total) {
                progress = 0;
                finish(level, holder.value(), input);
            }
            setChanged();
        }
        setLit(status == MachineStatus.REACTING);

        autoEject(level, itemEject);
        if (isAutoEject()) {
            outputs.pushFluid(level, pos, getFacing(), sideConfig, fluidOutput, ArcforgeConfig.REACTOR_OUTPUT_RATE.getAsInt(), null);
            pushByproduct(level, pos);
        }
    }

    // The liquid by-product out of By-product faces, up to outputRate mB/t shared across them.
    private void pushByproduct(ServerLevel level, BlockPos pos) {
        int budget = ArcforgeConfig.REACTOR_OUTPUT_RATE.getAsInt();
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || byproductTank.getAmount() <= 0) {
                return;
            }
            if (sideConfig.get(getFacing(), direction) == SideMode.BYPRODUCT) {
                budget -= outputs.pushFluid(level, pos, direction, fluidByproduct, budget);
            }
        }
    }

    // Whether everything the recipe makes fits right now: the item in the output slot, the fluid in the output
    // tank, and the byproduct (if it could come) in its slot.
    private boolean fits(ChemicalReactingRecipe recipe) {
        if (recipe.itemOutput().isPresent() && !fits(items.getStack(SLOT_OUTPUT), recipe.itemOutput().get().create())) {
            return false;
        }
        if (recipe.byproduct().isPresent() && !fits(items.getStack(SLOT_BYPRODUCT), recipe.byproduct().get().create())) {
            return false;
        }
        if (recipe.fluidOutput().isPresent()) {
            FluidStack fluid = recipe.fluidOutput().get().create();
            try (Transaction tx = Transaction.openRoot()) {
                if (output.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx) != fluid.getAmount()) {
                    return false;
                }
            }
        }
        if (recipe.fluidByproduct().isPresent()) {
            FluidStack fluid = recipe.fluidByproduct().get().create();
            try (Transaction tx = Transaction.openRoot()) {
                return byproductTank.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx) == fluid.getAmount();
            }
        }
        return true;
    }

    private static boolean fits(ItemStack slot, ItemStack product) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, product) && slot.getCount() + product.getCount() <= slot.getMaxStackSize();
    }

    private void finish(ServerLevel level, ChemicalReactingRecipe recipe, ChemicalReactorInput input) {
        int[] tanks = recipe.tanksFor(input);
        int[] slots = recipe.slotsFor(input);
        if (tanks == null || slots == null) {
            return;
        }
        List<ChemicalReactingRecipe.ItemInput> needed = recipe.itemInputs();
        for (int i = 0; i < slots.length; i++) {
            int slot = INPUT_SLOTS[slots[i]];
            ItemStack stack = items.getStack(slot);
            items.setStack(slot, stack.copyWithCount(stack.getCount() - needed.get(i).count()));
        }
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < tanks.length; i++) {
                FilteredFluidTank tank = inputTank(tanks[i]);
                tank.extract(0, tank.getResource(0), recipe.fluidInputs().get(i).amount(), tx);
            }
            recipe.fluidOutput().ifPresent(template -> {
                FluidStack fluid = template.create();
                output.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
            });
            recipe.fluidByproduct().ifPresent(template -> {
                FluidStack fluid = template.create();
                byproductTank.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
            });
            tx.commit();
        }
        recipe.itemOutput().ifPresent(template -> add(SLOT_OUTPUT, template.create()));
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            add(SLOT_BYPRODUCT, recipe.byproduct().get().create());
        }
        ArcforgeAdvancements.produced(this, recipe.itemOutput().map(template -> template.create()).orElse(ItemStack.EMPTY),
                recipe.fluidOutput().map(template -> template.create().getFluid()).orElse(null), recipe.category());
    }

    private void add(int slot, ItemStack product) {
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? product : current.copyWithCount(current.getCount() + product.getCount()));
    }

    public FilteredFluidTank getInputA() {
        return inputA;
    }

    public FilteredFluidTank getInputB() {
        return inputB;
    }

    public FilteredFluidTank getInputC() {
        return inputC;
    }

    // Input tank 0, 1 or 2.
    public FilteredFluidTank inputTank(int tank) {
        return switch (tank) {
            case 0 -> inputA;
            case 1 -> inputB;
            default -> inputC;
        };
    }

    public FilteredFluidTank getOutputTank() {
        return output;
    }

    // The liquid by-product (the Water left from making Ethylene).
    public FilteredFluidTank getByproductTank() {
        return byproductTank;
    }

    // The input tanks as one: what input faces, buckets and the GUI fill.
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

    // What the running recipe makes (either may be empty), for Jade.
    public ItemStack getMakingItem() {
        return makingItem;
    }

    public FluidStack getMakingFluid() {
        return makingFluid;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            case BYPRODUCT -> itemByproduct;
            default -> null;
        };
    }

    // Input faces fill the input tanks (sorted by the router); output faces drain the output tank, by-product faces the
    // by-product tank. Unsided, all five tanks (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
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

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.OUTPUT || mode == SideMode.BYPRODUCT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.OUTPUT || mode == SideMode.BYPRODUCT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            // Gases too (Ammonia, Hydrogen), through Pressurized Conduits.
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        inputA.deserialize(input.childOrEmpty("input_a"));
        inputB.deserialize(input.childOrEmpty("input_b"));
        inputC.deserialize(input.childOrEmpty("input_c"));
        output.deserialize(input.childOrEmpty("output"));
        byproductTank.deserialize(input.childOrEmpty("byproduct_tank"));
        progress = input.getIntOr("progress", 0);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
        int layout = input.getIntOr("slot_layout", 1);
        if (layout < SLOT_LAYOUT) {
            // Layout 1 was input, output, byproduct, then the upgrade slots; layout 2 added the second input slot before the
            // upgrades. Move the upgrades up past each input slot added since.
            items.ensureSize(MACHINE_SLOTS + UPGRADE_SLOTS);
            int first = layout < 2 ? SLOT_INPUT_B : SLOT_INPUT_C;
            int shift = MACHINE_SLOTS - first;
            for (int slot = MACHINE_SLOTS + UPGRADE_SLOTS - 1; slot >= MACHINE_SLOTS; slot--) {
                items.setStack(slot, items.getStack(slot - shift));
            }
            for (int slot = first; slot < MACHINE_SLOTS; slot++) {
                items.setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        inputA.serialize(output.child("input_a"));
        inputB.serialize(output.child("input_b"));
        inputC.serialize(output.child("input_c"));
        this.output.serialize(output.child("output"));
        byproductTank.serialize(output.child("byproduct_tank"));
        output.putInt("progress", progress);
        if (current != null) {
            output.putString("recipe", current.toString());
        }
        output.putInt("slot_layout", SLOT_LAYOUT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.chemical_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChemicalReactorMenu(containerId, inventory, worldPosition, items, data);
    }
}
