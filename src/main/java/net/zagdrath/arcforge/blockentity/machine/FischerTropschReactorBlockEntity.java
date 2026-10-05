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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
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
import net.zagdrath.arcforge.menu.machine.FischerTropschReactorMenu;
import net.zagdrath.arcforge.recipe.FischerTropschRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Builds Syngas up into Naphtha, Light Oil and Heavy Oil, plus Water (arcforge:fischer_tropsch recipes), with FE and heat,
// over a catalyst. It only works between fischerTropschReactor.minTemperature (200°C) and maxOperatingTemperature (350°C):
// colder it waits for heat, hotter it stops and sheds coolingPerTick HU/t until it's back in its window (with the
// thermostat on, as by default, its heat faces take heat only up to the top of the window, so it can't be overheated).
// The catalyst slot takes Iron Dust or Nickel Dust: one dust lasts ironCatalystOperations (16) or
// nickelCatalystOperations (32) operations. Syngas goes in through input faces (with the catalyst); each product has its
// own tank and its own face mode (Naphtha, Light Oil, Heavy Oil, and the By-product mode for Water), and Output faces give
// out all four. A product whose tank can't take the next batch stops it. Speed upgrades make it faster (drawing FE and
// heat just as much faster); Energy upgrades cut the FE.
public class FischerTropschReactorBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_CATALYST = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.NAPHTHA,
            SideMode.LIGHT_OIL, SideMode.HEAVY_OIL, SideMode.BYPRODUCT, SideMode.ENERGY, SideMode.HEAT);
    // The product tanks, in recipe order (FischerTropschRecipe.products).
    public static final int TANK_NAPHTHA = 0, TANK_LIGHT_OIL = 1, TANK_HEAVY_OIL = 2, TANK_WATER = 3;
    private static final SideMode[] PRODUCT_MODES = { SideMode.NAPHTHA, SideMode.LIGHT_OIL, SideMode.HEAVY_OIL, SideMode.BYPRODUCT };

    private final ConsumerEnergyHandler energy;
    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank syngas;
    private final FilteredFluidTank[] products = new FilteredFluidTank[4];
    private final ResourceHandler<FluidResource>[] productOutputs;
    private final ResourceHandler<FluidResource> syngasInput;
    private final ResourceHandler<FluidResource> allOutputs;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ResourceHandler<ItemResource> catalystInput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    private int heatUsage;
    // Operations the catalyst dust in the slot has done so far, and which dust that is (the wear starts over when it changes).
    private int catalystUsed;
    private @Nullable Item catalystItem;
    private @Nullable Identifier current;

    @SuppressWarnings("unchecked")
    public FischerTropschReactorBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: Syngas and catalyst in the top, heat into the bottom, every product out of the left, FE into the back.
        super(ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(), pos, state, MACHINE_SLOTS, FischerTropschReactorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.OUTPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.FT_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.FT_MAX_INPUT.getAsInt(), this::setChanged);
        this.heat = new HeatBuffer(ArcforgeConfig.FT_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.FT_BUFFER_MAX_TEMPERATURE.getAsInt(), this::setChanged);
        HeatHandler raw = heat.input(Integer.MAX_VALUE);
        // The thermostat: heat faces take heat only up to the top of the operating window.
        this.heatInput = new HeatHandler() {
            @Override
            public int getHeat() {
                return raw.getHeat();
            }

            @Override
            public int getMaxHeat() {
                return raw.getMaxHeat();
            }

            @Override
            public int getTemperature() {
                return raw.getTemperature();
            }

            @Override
            public int receiveHeat(int amount, boolean simulate) {
                if (ArcforgeConfig.FT_THERMOSTAT.getAsBoolean()) {
                    amount = Math.min(amount, Math.max(0, heat.storedAt(maxTemperature()) - heat.getStored()));
                }
                return raw.receiveHeat(amount, simulate);
            }

            @Override
            public int extractHeat(int amount, boolean simulate) {
                return 0;
            }
        };
        this.syngas = new FilteredFluidTank(ArcforgeConfig.FT_SYNGAS_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isFischerTropschInput(level, resource), this::setChanged);
        int capacity = ArcforgeConfig.FT_PRODUCT_CAPACITY.getAsInt();
        Fluid[] fluids = { ModFluids.NAPHTHA.get(), ModFluids.LIGHT_OIL.get(), ModFluids.HEAVY_OIL.get(), Fluids.WATER };
        this.productOutputs = new ResourceHandler[4];
        for (int i = 0; i < 4; i++) {
            Fluid fluid = fluids[i];
            products[i] = new FilteredFluidTank(capacity, resource -> resource.getFluid() == fluid, this::setChanged);
            productOutputs[i] = new AutomationResourceHandler<>(products[i], index -> false, index -> true);
        }
        this.syngasInput = new AutomationResourceHandler<>(syngas, index -> true, index -> false);
        this.allOutputs = new CombinedResourceHandler<>(productOutputs);
        this.fluidAll = new CombinedResourceHandler<>(syngas, products[0], products[1], products[2], products[3]);
        // Buckets and Gas Cartridges: fill from the products, empty Syngas in.
        this.fluidInteraction = new CombinedResourceHandler<>(allOutputs, syngasInput);
        this.catalystInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_CATALYST, slot -> false);
        this.data = new WideIntContainerData(FischerTropschReactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FischerTropschReactorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case FischerTropschReactorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case FischerTropschReactorMenu.DATA_USAGE -> usage;
                    case FischerTropschReactorMenu.DATA_PROGRESS -> progress;
                    case FischerTropschReactorMenu.DATA_TOTAL -> total;
                    case FischerTropschReactorMenu.DATA_HEAT -> heat.getStored();
                    case FischerTropschReactorMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case FischerTropschReactorMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case FischerTropschReactorMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case FischerTropschReactorMenu.DATA_MAX_TEMPERATURE -> maxTemperature();
                    case FischerTropschReactorMenu.DATA_HEAT_USAGE -> heatUsage;
                    case FischerTropschReactorMenu.DATA_SYNGAS_FLUID -> syngas.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(syngas.getResource(0).getFluid()) : -1;
                    case FischerTropschReactorMenu.DATA_SYNGAS -> syngas.getAmount();
                    case FischerTropschReactorMenu.DATA_SYNGAS_CAPACITY -> syngas.getCapacity();
                    case FischerTropschReactorMenu.DATA_NAPHTHA -> products[TANK_NAPHTHA].getAmount();
                    case FischerTropschReactorMenu.DATA_LIGHT_OIL -> products[TANK_LIGHT_OIL].getAmount();
                    case FischerTropschReactorMenu.DATA_HEAVY_OIL -> products[TANK_HEAVY_OIL].getAmount();
                    case FischerTropschReactorMenu.DATA_WATER -> products[TANK_WATER].getAmount();
                    case FischerTropschReactorMenu.DATA_PRODUCT_CAPACITY -> products[TANK_NAPHTHA].getCapacity();
                    case FischerTropschReactorMenu.DATA_CATALYST_LEFT -> catalystLeft();
                    case FischerTropschReactorMenu.DATA_CATALYST_LIFE -> catalystLife(items.getStack(SLOT_CATALYST));
                    case FischerTropschReactorMenu.DATA_STATUS -> status.ordinal();
                    case FischerTropschReactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FischerTropschReactorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The catalyst slot takes Iron Dust and Nickel Dust.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_CATALYST && catalystLife(resource.toStack(1)) > 0;
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // Operations one of this dust lasts as the catalyst, or 0 if it isn't one.
    public static int catalystLife(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ModItemTags.NICKEL_DUSTS)) {
            return ArcforgeConfig.FT_NICKEL_CATALYST_OPERATIONS.getAsInt();
        }
        return stack.is(ModItemTags.IRON_DUSTS) ? ArcforgeConfig.FT_IRON_CATALYST_OPERATIONS.getAsInt() : 0;
    }

    // Operations the dust in the slot has left.
    public int catalystLeft() {
        int life = catalystLife(items.getStack(SLOT_CATALYST));
        return life > 0 ? Math.max(0, life - catalystUsed) : 0;
    }

    public static int minTemperature() {
        return ArcforgeConfig.FT_MIN_TEMPERATURE.getAsInt();
    }

    public static int maxTemperature() {
        return ArcforgeConfig.FT_MAX_TEMPERATURE.getAsInt();
    }

    // FE/t for this recipe: Speed draws it faster, Energy cuts it.
    public int energyPerTick(FischerTropschRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // HU/t for this recipe: Speed draws it faster (an operation takes the same heat).
    public int heatPerTick(FischerTropschRecipe recipe) {
        return (int) Math.ceil(recipe.baseHeatPerTick() * speedMultiplier());
    }

    public int ticksFor(FischerTropschRecipe recipe) {
        return UpgradeType.time(recipe.ticks(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        heatUsage = 0;
        ItemStack catalyst = items.getStack(SLOT_CATALYST);
        Item inSlot = catalyst.isEmpty() ? null : catalyst.getItem();
        if (inSlot != catalystItem) {
            catalystItem = inSlot;
            catalystUsed = 0;
            setChanged();
        }
        // Past the top of its window it sheds heat until it's back in it.
        if (heat.getTemperature() > maxTemperature()) {
            int over = heat.getStored() - heat.storedAt(maxTemperature());
            heat.remove(Math.min(over, ArcforgeConfig.FT_COOLING_PER_TICK.getAsInt()));
        }
        RecipeHolder<FischerTropschRecipe> holder = syngas.getAmount() > 0
                ? MachineRecipes.fischerTropsch(level, new FischerTropschRecipe.Input(syngas.getResource(0), syngas.getAmount())).orElse(null)
                : null;
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            current = id;
        }
        total = holder != null ? ticksFor(holder.value()) : 0;

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (holder == null) {
            status = syngas.getAmount() > 0 ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        } else if (catalystLeft() <= 0) {
            status = MachineStatus.NO_CATALYST;
        } else if (!fits(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (heat.getTemperature() > maxTemperature()) {
            status = MachineStatus.TOO_HOT;
        } else if (heat.getTemperature() < minTemperature() || heat.getStored() < heatPerTick(holder.value())) {
            status = MachineStatus.TOO_COLD;
        } else if (!energy.consume(energyPerTick(holder.value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.SYNTHESIZING;
            usage = energyPerTick(holder.value());
            heatUsage = heat.remove(heatPerTick(holder.value()));
            if (++progress >= total) {
                progress = 0;
                finish(holder.value());
            }
            setChanged();
        }
        setLit(status == MachineStatus.SYNTHESIZING);
        pushProducts(level, pos);
    }

    private boolean fits(FischerTropschRecipe recipe) {
        int[] made = recipe.products();
        for (int i = 0; i < 4; i++) {
            if (made[i] > 0 && products[i].getSpace() < made[i]) {
                return false;
            }
        }
        return true;
    }

    private void finish(FischerTropschRecipe recipe) {
        int[] made = recipe.products();
        int used;
        List<FluidStack> madeFluids = new java.util.ArrayList<>();
        try (Transaction tx = Transaction.openRoot()) {
            used = syngas.extract(0, syngas.getResource(0), recipe.input().amount(), tx);
            for (int i = 0; i < 4; i++) {
                if (made[i] > 0) {
                    products[i].insert(0, FluidResource.of(fluidOf(i)), made[i], tx);
                    madeFluids.add(new FluidStack(fluidOf(i), made[i]));
                }
            }
            tx.commit();
        }
        // The catalyst wears: one dust is used up every so many operations.
        ItemStack catalyst = items.getStack(SLOT_CATALYST);
        int itemsUsed = 0;
        if (++catalystUsed >= catalystLife(catalyst)) {
            catalystUsed = 0;
            items.setStack(SLOT_CATALYST, catalyst.copyWithCount(catalyst.getCount() - 1));
            itemsUsed = 1;
            if (catalyst.getCount() <= 1) {
                catalystItem = null;
            }
        }
        controlState.completed(List.of(), madeFluids, itemsUsed, used);
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.LIGHT_OIL.get(), "fischer_tropsch");
    }

    // The fluid of product tank TANK_NAPHTHA, TANK_LIGHT_OIL, TANK_HEAVY_OIL or TANK_WATER.
    public static Fluid fluidOf(int product) {
        return switch (product) {
            case TANK_NAPHTHA -> ModFluids.NAPHTHA.get();
            case TANK_LIGHT_OIL -> ModFluids.LIGHT_OIL.get();
            case TANK_HEAVY_OIL -> ModFluids.HEAVY_OIL.get();
            default -> Fluids.WATER;
        };
    }

    // Each product out of its own faces and of Output faces, up to fluidOutputRate mB/t each.
    private void pushProducts(ServerLevel level, BlockPos pos) {
        int rate = ArcforgeConfig.FT_OUTPUT_RATE.getAsInt();
        for (int i = 0; i < 4; i++) {
            if (products[i].getAmount() <= 0) {
                continue;
            }
            int budget = rate;
            for (Direction direction : Direction.values()) {
                if (budget <= 0) {
                    break;
                }
                SideMode mode = sideConfig.get(getFacing(), direction);
                if (mode == PRODUCT_MODES[i] || mode == SideMode.OUTPUT) {
                    budget -= outputs.pushFluid(level, pos, direction, productOutputs[i], budget);
                }
            }
        }
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getSyngas() {
        return syngas;
    }

    // TANK_NAPHTHA, TANK_LIGHT_OIL, TANK_HEAVY_OIL or TANK_WATER.
    public FilteredFluidTank getProduct(int product) {
        return products[product];
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // FE and HU used last tick.
    public int getUsage() {
        return usage;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? catalystInput : null;
    }

    // Input faces fill the Syngas tank; each product's own faces drain it, and Output faces drain all four.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> syngasInput;
            case OUTPUT -> allOutputs;
            case NAPHTHA -> productOutputs[TANK_NAPHTHA];
            case LIGHT_OIL -> productOutputs[TANK_LIGHT_OIL];
            case HEAVY_OIL -> productOutputs[TANK_HEAVY_OIL];
            case BYPRODUCT -> productOutputs[TANK_WATER];
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

    // Heat goes in on heat faces (up to the top of the window, with the thermostat on); nothing can draw it back out.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        boolean product = mode == SideMode.OUTPUT || mode == SideMode.NAPHTHA || mode == SideMode.LIGHT_OIL || mode == SideMode.HEAVY_OIL
                || mode == SideMode.BYPRODUCT;
        return switch (type) {
            // Syngas rides Pressurized Conduits in; the liquids leave through Fluid Conduits.
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> product ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        heat.deserialize(input);
        syngas.deserialize(input.childOrEmpty("syngas"));
        String[] names = { "naphtha", "light_oil", "heavy_oil", "water" };
        for (int i = 0; i < 4; i++) {
            products[i].deserialize(input.childOrEmpty(names[i]));
        }
        progress = input.getIntOr("progress", 0);
        catalystUsed = input.getIntOr("catalyst_used", 0);
        catalystItem = input.getString("catalyst").map(Identifier::tryParse)
                .flatMap(id -> id == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(id)).orElse(null);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        heat.serialize(output);
        syngas.serialize(output.child("syngas"));
        String[] names = { "naphtha", "light_oil", "heavy_oil", "water" };
        for (int i = 0; i < 4; i++) {
            products[i].serialize(output.child(names[i]));
        }
        output.putInt("progress", progress);
        output.putInt("catalyst_used", catalystUsed);
        if (catalystItem != null) {
            output.putString("catalyst", BuiltInRegistries.ITEM.getKey(catalystItem).toString());
        }
        if (current != null) {
            output.putString("recipe", current.toString());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.fischer_tropsch_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FischerTropschReactorMenu(containerId, inventory, worldPosition, items, data);
    }
}
