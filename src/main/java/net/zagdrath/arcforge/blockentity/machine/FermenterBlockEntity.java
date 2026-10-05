/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.FermenterMenu;
import net.zagdrath.arcforge.recipe.CulturingRecipe;
import net.zagdrath.arcforge.recipe.FermentingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Ferments crops and water into Ethanol with FE (arcforge:fermenting recipes): a crop and 100 mB of water become
// 20-80 mB of Ethanol over 200 ticks (Dried Grain and Dried Sorghum in half that), now and then with some Bone Meal.
// Crops, the additive and water come in through input faces; Ethanol and the byproduct leave through output faces; a
// full tank or byproduct slot pauses it.
// Additive: while the additive slot holds Dried Hops (#arcforge:fermenter_additives), each operation makes additiveBonus
// more Ethanol (+20%), and one lasts additiveOperations operations.
// Carbon Dioxide: each operation gives off carbonDioxidePerEthanol mB per mB of Ethanol into its gas tank, pushed out of
// Gas Output faces; what doesn't fit goes into the air, so it never stops the Fermenter.
// Cultures (arcforge:culturing): with a starter culture in the additive slot (a Slimeball), the items in its two input
// slots (Sugar and Kelp, either way round) and water grow the result (a Slimeball) into the output slot, slowly. The
// starter is never used up; there's no Ethanol or Carbon Dioxide, and Dried Hops don't come into it. A culture that
// matches goes before fermenting.
// Speed upgrades make it faster (drawing FE just as much faster), Energy upgrades cut the FE per operation.
public class FermenterBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BYPRODUCT = 1;
    public static final int SLOT_ADDITIVE = 2;
    // The second input, for cultures (crops for Ethanol only go in the first).
    public static final int SLOT_INPUT_2 = 3;
    public static final int MACHINE_SLOTS = 4;
    // Saves before the additive slot (layout 1) had the upgrade slots straight after the byproduct slot, and saves before
    // the second input slot (layout 2) straight after the additive slot.
    private static final int SLOT_LAYOUT = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.GAS_OUTPUT);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank water;
    private final FilteredFluidTank ethanol;
    private final FilteredFluidTank carbonDioxide;
    private final ResourceHandler<FluidResource> carbonDioxideOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> ethanolOutput;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // Operations the Dried Hops already taken from the additive slot still boost.
    private int additiveLeft;
    // Whether the batch in progress is a culture (switching to or from one starts the batch over).
    private boolean culturing;

    public FermenterBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input, bottom output, back energy.
        super(ModBlockEntityTypes.FERMENTER.get(), pos, state, MACHINE_SLOTS, FermenterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.FERMENTER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.FERMENTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.water = new FilteredFluidTank(ArcforgeConfig.FERMENTER_WATER_TANK.getAsInt(), resource -> resource.is(Fluids.WATER), this::setChanged);
        this.ethanol = new FilteredFluidTank(ArcforgeConfig.FERMENTER_ETHANOL_TANK.getAsInt(), resource -> true, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.ethanolOutput = new AutomationResourceHandler<>(ethanol, index -> false, index -> true);
        this.fluidInteraction = new CombinedResourceHandler<>(ethanolOutput, waterInput);
        this.carbonDioxide = new FilteredFluidTank(ArcforgeConfig.FERMENTER_CO2_TANK.getAsInt(), resource -> resource.is(ModFluids.CARBON_DIOXIDE.get()),
                this::setChanged);
        this.carbonDioxideOutput = new AutomationResourceHandler<>(carbonDioxide, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(ethanolOutput, waterInput, carbonDioxideOutput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_INPUT_2 || slot == SLOT_ADDITIVE,
                slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_BYPRODUCT);
        this.data = new WideIntContainerData(FermenterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FermenterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case FermenterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case FermenterMenu.DATA_USAGE -> usage;
                    case FermenterMenu.DATA_PROGRESS -> progress;
                    case FermenterMenu.DATA_TOTAL -> total;
                    case FermenterMenu.DATA_WATER -> water.getAmount();
                    case FermenterMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case FermenterMenu.DATA_ETHANOL_FLUID -> ethanol.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(ethanol.getResource(0).getFluid()) : -1;
                    case FermenterMenu.DATA_ETHANOL -> ethanol.getAmount();
                    case FermenterMenu.DATA_ETHANOL_CAPACITY -> ethanol.getCapacity();
                    case FermenterMenu.DATA_STATUS -> status.ordinal();
                    case FermenterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FermenterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case FermenterMenu.DATA_CO2 -> carbonDioxide.getAmount();
                    case FermenterMenu.DATA_CO2_CAPACITY -> carbonDioxide.getCapacity();
                    case FermenterMenu.DATA_ADDITIVE_LEFT -> additiveLeft;
                    default -> 0;
                };
            }
        };
    }

    // The first input slot takes any fermenting or culture ingredient, the second culture ingredients only, and the
    // additive slot Dried Hops or a starter culture; nothing goes into the output slot. The client passes a null level
    // and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_INPUT -> MachineRecipes.isFermenterInput(level, stack) || MachineRecipes.isCultureIngredient(level, stack);
            case SLOT_INPUT_2 -> MachineRecipes.isCultureIngredient(level, stack);
            case SLOT_ADDITIVE -> isAdditive(stack) || MachineRecipes.isCultureStarter(level, stack);
            default -> false;
        };
    }

    public static boolean isAdditive(ItemStack stack) {
        return stack.is(ModItemTags.FERMENTER_ADDITIVES);
    }

    // Whether this operation gets the additive's bonus: Dried Hops already taken, or one in the slot to take.
    private boolean additiveReady() {
        return additiveLeft > 0 || isAdditive(items.getStack(SLOT_ADDITIVE));
    }

    // The Ethanol this recipe makes now, with the additive's bonus if there is one.
    public int ethanolFor(FermentingRecipe recipe) {
        int base = recipe.result().amount();
        return additiveReady() ? (int) Math.round(base * (1.0 + ArcforgeConfig.FERMENTER_ADDITIVE_BONUS.getAsDouble())) : base;
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while fermenting: Speed draws it faster, Energy cuts it.
    public int energyPerTick(FermentingRecipe recipe) {
        double base = (double) recipe.totalEnergy() / recipe.time();
        return (int) Math.ceil(base * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(FermentingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    public int energyPerTick(CulturingRecipe recipe) {
        double base = (double) recipe.totalEnergy() / recipe.time();
        return (int) Math.ceil(base * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(CulturingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    // The culture its slots make now, if any.
    public Optional<RecipeHolder<CulturingRecipe>> culture(@Nullable Level level) {
        return MachineRecipes.culturing(level, items.getStack(SLOT_INPUT), items.getStack(SLOT_INPUT_2), items.getStack(SLOT_ADDITIVE));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        Optional<RecipeHolder<CulturingRecipe>> culture = culture(level);
        if (culture.isPresent()) {
            cultureTick(level, pos, culture.get().value());
        } else {
            fermentTick(level, pos);
        }
        setLit(status == MachineStatus.FERMENTING);

        outputs.pushFluid(level, pos, getFacing(), sideConfig, ethanolOutput, ArcforgeConfig.MELTER_OUTPUT_RATE.getAsInt(), null);
        pushGas(level, pos);
        autoEject(level, itemOutput);
    }

    private void cultureTick(ServerLevel level, BlockPos pos, CulturingRecipe recipe) {
        // Switching between cultures and fermenting starts the batch over.
        if (!culturing) {
            culturing = true;
            progress = 0;
        }
        total = ticksFor(recipe);
        ItemStack result = recipe.result().create();
        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (water.getAmount() < recipe.fluidInput().amount()) {
            status = MachineStatus.NO_WATER;
        } else if (!fitsOutput(result)) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(recipe))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.FERMENTING;
            usage = energyPerTick(recipe);
            if (++progress >= total) {
                progress = 0;
                grow(recipe, result);
            }
            setChanged();
        }
    }

    // Takes the ingredients and the water, keeps the starter, and puts the grown result in the output slot.
    private void grow(CulturingRecipe recipe, ItemStack result) {
        int[] slots = recipe.slotsFor(items.getStack(SLOT_INPUT), items.getStack(SLOT_INPUT_2));
        if (slots == null) {
            return;
        }
        int itemsUsed = 0;
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i] == 0 ? SLOT_INPUT : SLOT_INPUT_2;
            ItemStack held = items.getStack(slot);
            items.setStack(slot, held.copyWithCount(held.getCount() - recipe.ingredients().get(i).count()));
            itemsUsed += recipe.ingredients().get(i).count();
        }
        int waterUsed;
        try (Transaction tx = Transaction.openRoot()) {
            waterUsed = water.extract(0, water.getResource(0), recipe.fluidInput().amount(), tx);
            tx.commit();
        }
        ItemStack held = items.getStack(SLOT_BYPRODUCT);
        items.setStack(SLOT_BYPRODUCT, held.isEmpty() ? result.copy() : held.copyWithCount(held.getCount() + result.getCount()));
        controlState.completed(List.of(result), List.of(), itemsUsed, waterUsed);
    }

    private boolean fitsOutput(ItemStack stack) {
        ItemStack held = items.getStack(SLOT_BYPRODUCT);
        return held.isEmpty() || (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() + stack.getCount() <= held.getMaxStackSize());
    }

    private void fermentTick(ServerLevel level, BlockPos pos) {
        if (culturing) {
            culturing = false;
            progress = 0;
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        Optional<RecipeHolder<FermentingRecipe>> recipe = input.isEmpty() ? Optional.empty() : MachineRecipes.fermenting(level, input);
        total = recipe.map(holder -> ticksFor(holder.value())).orElse(0);
        if (recipe.isEmpty() && progress != 0) {
            progress = 0;
            setChanged();
        }

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (recipe.isEmpty()) {
            status = MachineStatus.IDLE;
        } else if (water.getAmount() < recipe.get().value().fluidInput().amount()) {
            status = MachineStatus.NO_WATER;
        } else if (!fits(recipe.get().value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(recipe.get().value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.FERMENTING;
            usage = energyPerTick(recipe.get().value());
            if (++progress >= total) {
                progress = 0;
                ferment(level, recipe.get().value());
            }
            setChanged();
        }
    }

    // Carbon Dioxide out of Gas Output faces, up to gasOutputRate mB/t shared across them.
    private void pushGas(ServerLevel level, BlockPos pos) {
        int budget = ArcforgeConfig.FERMENTER_GAS_OUTPUT_RATE.getAsInt();
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || carbonDioxide.getAmount() <= 0) {
                return;
            }
            if (sideConfig.get(getFacing(), direction) == SideMode.GAS_OUTPUT) {
                budget -= outputs.pushFluid(level, pos, direction, carbonDioxideOutput, budget);
            }
        }
    }

    // Whether the ethanol and (if it might come) the byproduct fit.
    private boolean fits(FermentingRecipe recipe) {
        FluidStack result = recipe.result().create();
        int amount = ethanolFor(recipe);
        try (Transaction tx = Transaction.openRoot()) {
            if (ethanol.insert(0, FluidResource.of(result), amount, tx) != amount) {
                return false;
            }
        }
        return recipe.byproduct().isEmpty() || recipe.byproductChance() <= 0 || fitsOutput(recipe.byproduct().get().create());
    }

    private void ferment(ServerLevel level, FermentingRecipe recipe) {
        FluidStack result = recipe.result().create();
        int amount = ethanolFor(recipe);
        int itemsUsed = 1;
        if (additiveLeft <= 0 && isAdditive(items.getStack(SLOT_ADDITIVE))) {
            ItemStack additive = items.getStack(SLOT_ADDITIVE);
            items.setStack(SLOT_ADDITIVE, additive.copyWithCount(additive.getCount() - 1));
            additiveLeft = ArcforgeConfig.FERMENTER_ADDITIVE_OPERATIONS.getAsInt();
            itemsUsed++;
        }
        if (additiveLeft > 0) {
            additiveLeft--;
        }
        int waterUsed;
        List<FluidStack> made = new java.util.ArrayList<>();
        try (Transaction tx = Transaction.openRoot()) {
            waterUsed = water.extract(0, water.getResource(0), recipe.fluidInput().amount(), tx);
            made.add(result.copyWithAmount(ethanol.insert(0, FluidResource.of(result), amount, tx)));
            // The gas that doesn't fit goes into the air.
            int gas = carbonDioxideFor(amount);
            if (gas > 0) {
                made.add(new FluidStack(ModFluids.CARBON_DIOXIDE.get(),
                        carbonDioxide.insert(0, FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), gas, tx)));
            }
            tx.commit();
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        List<ItemStack> madeItems = List.of();
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            ItemStack byproduct = recipe.byproduct().get().create();
            madeItems = List.of(byproduct.copy());
            ItemStack held = items.getStack(SLOT_BYPRODUCT);
            items.setStack(SLOT_BYPRODUCT, held.isEmpty() ? byproduct : held.copyWithCount(held.getCount() + byproduct.getCount()));
        }
        controlState.completed(madeItems, made, itemsUsed, waterUsed);
    }

    // The Carbon Dioxide given off with this much Ethanol.
    public static int carbonDioxideFor(int ethanol) {
        return (int) Math.round(ethanol * ArcforgeConfig.FERMENTER_CO2_PER_ETHANOL.getAsDouble());
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getEthanol() {
        return ethanol;
    }

    public FilteredFluidTank getCarbonDioxide() {
        return carbonDioxide;
    }

    public int getAdditiveLeft() {
        return additiveLeft;
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

    // FE used last tick.
    public int getUsage() {
        return usage;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees everything. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return new CombinedResourceHandler<>(itemInput, itemOutput);
        }
        return mode == SideMode.INPUT ? itemInput : mode == SideMode.OUTPUT ? itemOutput : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> waterInput;
            case OUTPUT -> ethanolOutput;
            case GAS_OUTPUT -> carbonDioxideOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // A water bucket fills the water tank; an empty bucket takes Ethanol.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        water.deserialize(input.childOrEmpty("water"));
        ethanol.deserialize(input.childOrEmpty("ethanol"));
        carbonDioxide.deserialize(input.childOrEmpty("carbon_dioxide"));
        progress = input.getIntOr("progress", 0);
        additiveLeft = input.getIntOr("additive_left", 0);
        culturing = input.getBooleanOr("culturing", false);
        int layout = input.getIntOr("slot_layout", 1);
        if (layout < 2) {
            // Layout 1 was input, byproduct, then the upgrade slots: move the upgrades up past the new additive slot.
            insertSlot(SLOT_ADDITIVE);
        }
        if (layout < 3) {
            // Layout 2 was input, byproduct, additive, then the upgrade slots: move them up past the second input slot.
            insertSlot(SLOT_INPUT_2);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        water.serialize(output.child("water"));
        ethanol.serialize(output.child("ethanol"));
        carbonDioxide.serialize(output.child("carbon_dioxide"));
        output.putInt("progress", progress);
        output.putInt("additive_left", additiveLeft);
        output.putInt("slot_layout", SLOT_LAYOUT);
        output.putBoolean("culturing", culturing);
    }

    // An old save's slots from `slot` on move up one, leaving it empty.
    private void insertSlot(int slot) {
        items.ensureSize(MACHINE_SLOTS + UPGRADE_SLOTS);
        for (int i = MACHINE_SLOTS + UPGRADE_SLOTS - 1; i > slot; i--) {
            items.setStack(i, items.getStack(i - 1));
        }
        items.setStack(slot, ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.fermenter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FermenterMenu(containerId, inventory, worldPosition, items, data);
    }
}
