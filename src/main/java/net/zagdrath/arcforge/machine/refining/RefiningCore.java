/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.refining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.CrackingMode;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.recipe.RefiningRecipe;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// What a refinery unit (see RefiningProcess) holds and does, shared by the single-block units
// (RefiningMachineBlockEntity) and the Cracking Unit (CrackingUnitBlockEntity), which give it their slots and settings:
// a feed tank (whatever the process's recipes take), one product tank per product (the recipe's fluids fill them in
// order), a heat buffer and an FE buffer when the process uses them, and a catalyst slot and an item output slot in the
// owner's items when it has them, and a second feed tank for the processes that take a second fluid (Hydrogen, Oxygen
// or Steam). Each tick it finds the recipe for the feed, checks the second feed, heat (at least the recipe's min_temp
// and a tick's heat), FE, the catalyst and room for every product, and runs the recipe's time; the catalyst
// item in the slot is used up after catalyst_uses operations. Speed upgrades shorten the time and draw FE and heat that
// much faster (an operation takes the same); Energy upgrades cut the FE. The Cracking Unit's core also holds its
// CrackingMode, which picks the recipes it runs and is saved with it.
public final class RefiningCore {
    // How the owner's items and settings are laid out.
    public record Layout(int catalystSlot, int itemSlot, int energyCapacity, int maxInput, int heatCapacity, int maxTemperature, int feedCapacity,
            int productCapacity, int secondFeedCapacity) {
        public Layout(int catalystSlot, int itemSlot, int energyCapacity, int maxInput, int heatCapacity, int maxTemperature, int feedCapacity,
                int productCapacity) {
            this(catalystSlot, itemSlot, energyCapacity, maxInput, heatCapacity, maxTemperature, feedCapacity, productCapacity, 0);
        }
    }

    private final RefiningProcess process;
    private final Layout layout;
    private final MachineItemHandler items;
    private final Runnable changed;
    private final @Nullable ConsumerEnergyHandler energy;
    private final @Nullable HeatBuffer heat;
    private final @Nullable HeatHandler heatInput;
    private final FilteredFluidTank feed;
    private final @Nullable FilteredFluidTank secondFeed;
    private final @Nullable ResourceHandler<FluidResource> secondFeedInput;
    private final FilteredFluidTank[] products;
    private final ResourceHandler<FluidResource>[] productOutputs;
    private final ResourceHandler<FluidResource> feedInput;
    private final ResourceHandler<FluidResource> liquidOutputs;
    private final ResourceHandler<FluidResource> gasOutputs;
    private final ResourceHandler<FluidResource> allOutputs;
    private final ResourceHandler<FluidResource> all;
    private final ResourceHandler<FluidResource> interaction;
    // Per fluid: what a port for that one product drains (see outputHolding).
    private final Map<Fluid, ResourceHandler<FluidResource>> holding = new HashMap<>();

    private int progress;
    private int total;
    private int usage;
    private int heatUsage;
    private int catalystUsed;
    private @Nullable Item catalystItem;
    private @Nullable Identifier current;
    private @Nullable RefiningRecipe recipe;
    private CrackingMode mode = CrackingMode.CATALYTIC;

    @SuppressWarnings("unchecked")
    public RefiningCore(RefiningProcess process, Layout layout, MachineItemHandler items, java.util.function.Supplier<@Nullable Level> level,
            Runnable changed) {
        this.process = process;
        this.layout = layout;
        this.items = items;
        this.changed = changed;
        this.energy = process.powered() ? new ConsumerEnergyHandler(layout.energyCapacity(), layout.maxInput(), changed) : null;
        this.heat = process.heated() ? new HeatBuffer(layout.heatCapacity(), layout.maxTemperature(), changed) : null;
        this.heatInput = heat != null ? heat.input(Integer.MAX_VALUE) : null;
        this.feed = new FilteredFluidTank(layout.feedCapacity(), resource -> MachineRecipes.isRefiningInput(level.get(), process, resource), changed);
        this.secondFeed = process.secondFeed() != null ? new FilteredFluidTank(Math.max(1, layout.secondFeedCapacity()),
                resource -> MachineRecipes.isRefiningSecondInput(level.get(), process, resource), changed) : null;
        this.secondFeedInput = secondFeed != null ? new AutomationResourceHandler<>(secondFeed, index -> true, index -> false) : null;
        int tanks = process.tanks();
        this.products = new FilteredFluidTank[tanks];
        this.productOutputs = new ResourceHandler[tanks];
        List<ResourceHandler<FluidResource>> liquids = new ArrayList<>(), gases = new ArrayList<>();
        for (int i = 0; i < tanks; i++) {
            int product = i;
            FilteredFluidTank tank = new FilteredFluidTank(layout.productCapacity(), resource -> MachineRecipes.isRefiningProduct(level.get(), process, product, resource),
                    changed);
            products[i] = tank;
            productOutputs[i] = new AutomationResourceHandler<>(tank, index -> false, index -> true);
            // Output faces give the liquids, Gas Output faces the gases, whichever tanks they're in.
            liquids.add(new AutomationResourceHandler<>(tank, index -> false, index -> tank.getAmount() > 0 && !Gases.isGas(tank.getResource(0))));
            gases.add(new AutomationResourceHandler<>(tank, index -> false, index -> tank.getAmount() > 0 && Gases.isGas(tank.getResource(0))));
        }
        this.feedInput = new AutomationResourceHandler<>(feed, index -> true, index -> false);
        this.liquidOutputs = new CombinedResourceHandler<>(liquids.toArray(ResourceHandler[]::new));
        this.gasOutputs = new CombinedResourceHandler<>(gases.toArray(ResourceHandler[]::new));
        this.allOutputs = new CombinedResourceHandler<>(productOutputs);
        // With no side: the feeds take fills, the products only drain.
        List<ResourceHandler<FluidResource>> every = new ArrayList<>();
        every.add(feedInput);
        if (secondFeedInput != null) {
            every.add(secondFeedInput);
        }
        every.addAll(List.of(productOutputs));
        this.all = new CombinedResourceHandler<>(every.toArray(ResourceHandler[]::new));
        // Buckets and cartridges: fill from the products, empty the feeds in.
        this.interaction = secondFeedInput != null ? new CombinedResourceHandler<>(allOutputs, feedInput, secondFeedInput)
                : new CombinedResourceHandler<>(allOutputs, feedInput);
    }

    public RefiningProcess process() {
        return process;
    }

    public Layout layout() {
        return layout;
    }

    // The Cracking Unit's mode (catalytic for every other process).
    public CrackingMode mode() {
        return mode;
    }

    // Sets the mode; the batch under way starts over, as for any change of recipe.
    public void setMode(CrackingMode mode) {
        if (process == RefiningProcess.CRACKING && this.mode != mode) {
            this.mode = mode;
            changed.run();
        }
    }

    // The products its tanks are laid out for, in order: the process's, or for the Cracking Unit its mode's.
    public List<Fluid> layoutProducts() {
        return process == RefiningProcess.CRACKING ? mode.products() : process.products();
    }

    // --- Running ---

    // One tick; runs is whether redstone lets it; the Speed and Energy upgrades installed. Gives the status.
    public MachineStatus tick(ServerLevel level, boolean runs, int speedUpgrades, int energyUpgrades) {
        usage = 0;
        heatUsage = 0;
        if (layout.catalystSlot() >= 0) {
            ItemStack catalyst = items.getStack(layout.catalystSlot());
            Item inSlot = catalyst.isEmpty() ? null : catalyst.getItem();
            // A different catalyst starts fresh; taking the stack out and putting it back doesn't.
            if (inSlot != null && inSlot != catalystItem) {
                catalystItem = inSlot;
                catalystUsed = 0;
                changed.run();
            }
        }
        RecipeHolder<RefiningRecipe> holder = feed.getAmount() > 0
                ? MachineRecipes.refining(level, process, new RefiningRecipe.Input(feed.getResource(0), feed.getAmount()), mode).orElse(null)
                : null;
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (progress != 0) {
                progress = 0;
                changed.run();
            }
            current = id;
        }
        recipe = holder != null ? holder.value() : null;
        total = recipe != null ? UpgradeType.time(recipe.ticks(), speedUpgrades) : 0;
        if (!runs) {
            return MachineStatus.DISABLED;
        }
        if (recipe == null) {
            return feed.getAmount() > 0 ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        }
        if (recipe.secondInput().isPresent()
                && (secondFeed == null || secondFeed.getAmount() <= 0 || !recipe.hasSecond(secondFeed.getResource(0), secondFeed.getAmount()))) {
            return process.missingSecond();
        }
        if (recipe.usesCatalyst() && !recipe.isCatalyst(catalystStack())) {
            return MachineStatus.NO_CATALYST;
        }
        if (!fits(recipe)) {
            return MachineStatus.OUTPUT_FULL;
        }
        double speed = UpgradeType.speedMultiplier(speedUpgrades);
        int heatPerTick = (int) Math.ceil(recipe.heatPerTick() * speed);
        if (heat != null && (heat.getTemperature() < recipe.minTemp() || heat.getStored() < heatPerTick)) {
            return MachineStatus.TOO_COLD;
        }
        int fe = (int) Math.ceil(recipe.energyPerTick() * speed * UpgradeType.energyCostMultiplier(energyUpgrades));
        if (fe > 0 && (energy == null || !energy.consume(fe))) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        if (heat != null && heatPerTick > 0) {
            heatUsage = heat.remove(heatPerTick);
        }
        if (++progress >= total) {
            progress = 0;
            finish(recipe);
        }
        changed.run();
        return process.runningStatus();
    }

    private ItemStack catalystStack() {
        return layout.catalystSlot() >= 0 ? items.getStack(layout.catalystSlot()) : ItemStack.EMPTY;
    }

    private boolean fits(RefiningRecipe recipe) {
        List<FluidStackTemplate> made = recipe.fluids();
        for (int i = 0; i < made.size(); i++) {
            FluidStackTemplate out = made.get(i);
            if (i >= products.length) {
                return false;
            }
            FilteredFluidTank tank = products[i];
            if (tank.getAmount() > 0 && tank.getResource(0).getFluid() != out.fluid().value() || tank.getSpace() < out.amount()) {
                return false;
            }
        }
        ItemStack item = recipe.itemResult();
        if (!item.isEmpty()) {
            if (layout.itemSlot() < 0) {
                return false;
            }
            ItemStack in = items.getStack(layout.itemSlot());
            return in.isEmpty() || ItemStack.isSameItemSameComponents(in, item) && in.getCount() + item.getCount() <= in.getMaxStackSize();
        }
        return true;
    }

    private void finish(RefiningRecipe recipe) {
        try (Transaction tx = Transaction.openRoot()) {
            feed.extract(0, feed.getResource(0), recipe.input().amount(), tx);
            if (secondFeed != null && recipe.secondInput().isPresent()) {
                secondFeed.extract(0, secondFeed.getResource(0), recipe.secondInput().get().amount(), tx);
            }
            List<FluidStackTemplate> made = recipe.fluids();
            for (int i = 0; i < made.size(); i++) {
                products[i].insert(0, FluidResource.of(made.get(i).fluid().value()), made.get(i).amount(), tx);
            }
            tx.commit();
        }
        ItemStack item = recipe.itemResult();
        if (!item.isEmpty()) {
            ItemStack in = items.getStack(layout.itemSlot());
            items.setStack(layout.itemSlot(), in.isEmpty() ? item : in.copyWithCount(in.getCount() + item.getCount()));
        }
        // The catalyst wears: one item is used up every catalyst_uses operations.
        if (recipe.usesCatalyst()) {
            ItemStack catalyst = catalystStack();
            if (++catalystUsed >= recipe.catalystUses()) {
                catalystUsed = 0;
                items.setStack(layout.catalystSlot(), catalyst.copyWithCount(catalyst.getCount() - 1));
                if (catalyst.getCount() <= 1) {
                    catalystItem = null;
                }
            }
        }
    }

    // The fluids it made in its last operation, for advancements: the first product, and the item.
    public @Nullable Fluid mainProduct() {
        return recipe != null && !recipe.fluids().isEmpty() ? recipe.fluids().get(0).fluid().value() : null;
    }

    // --- What the menu and the faces see ---

    public @Nullable ConsumerEnergyHandler energy() {
        return energy;
    }

    public @Nullable HeatBuffer heat() {
        return heat;
    }

    public @Nullable HeatHandler heatInput() {
        return heatInput;
    }

    public FilteredFluidTank feed() {
        return feed;
    }

    // The second feed tank (Hydrogen, Oxygen or Steam), or null for a process that takes one fluid.
    public @Nullable FilteredFluidTank secondFeed() {
        return secondFeed;
    }

    public @Nullable ResourceHandler<FluidResource> secondFeedInput() {
        return secondFeedInput;
    }

    public FilteredFluidTank product(int index) {
        return products[index];
    }

    // What a product tank gives out (it takes nothing in).
    public ResourceHandler<FluidResource> productOutput(int index) {
        return productOutputs[index];
    }

    public int productCount() {
        return products.length;
    }

    public ResourceHandler<FluidResource> feedInput() {
        return feedInput;
    }

    public ResourceHandler<FluidResource> liquidOutputs() {
        return liquidOutputs;
    }

    public ResourceHandler<FluidResource> gasOutputs() {
        return gasOutputs;
    }

    public ResourceHandler<FluidResource> allOutputs() {
        return allOutputs;
    }

    // The product tank laid out for this fluid (process.products()), or null.
    public @Nullable ResourceHandler<FluidResource> outputFor(Fluid fluid) {
        int index = process.products().indexOf(fluid);
        return index >= 0 && index < productOutputs.length ? productOutputs[index] : null;
    }

    // What a port for one product drains: whichever product tank holds that fluid (the Cracking Unit's third tank holds
    // Refinery Gas or Kerosene by its mode).
    @SuppressWarnings("unchecked")
    public ResourceHandler<FluidResource> outputHolding(Fluid fluid) {
        return holding.computeIfAbsent(fluid, key -> {
            List<ResourceHandler<FluidResource>> tanks = new ArrayList<>();
            for (FilteredFluidTank tank : products) {
                tanks.add(new AutomationResourceHandler<>(tank, index -> false, index -> tank.getAmount() > 0 && tank.getResource(0).getFluid() == key));
            }
            return new CombinedResourceHandler<>(tanks.toArray(ResourceHandler[]::new));
        });
    }

    public ResourceHandler<FluidResource> all() {
        return all;
    }

    public ResourceHandler<FluidResource> interaction() {
        return interaction;
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return total;
    }

    public int usage() {
        return usage;
    }

    public int heatUsage() {
        return heatUsage;
    }

    // The least temperature the current recipe needs (0 without one).
    public int minTemperature() {
        return recipe != null ? recipe.minTemp() : 0;
    }

    // Operations the catalyst item in the slot has left (0 without a catalyst recipe or item).
    public int catalystLeft() {
        return recipe != null && recipe.usesCatalyst() && recipe.isCatalyst(catalystStack()) ? Math.max(0, recipe.catalystUses() - catalystUsed) : 0;
    }

    public int catalystLife() {
        return recipe != null && recipe.usesCatalyst() ? recipe.catalystUses() : 0;
    }

    // A fluid's id for the menu, or -1 for an empty tank.
    public static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // --- Saving ---

    public void load(ValueInput input) {
        if (energy != null) {
            energy.deserialize(input.childOrEmpty("energy"));
        }
        if (heat != null) {
            heat.deserialize(input);
        }
        feed.deserialize(input.childOrEmpty("feed"));
        if (secondFeed != null) {
            secondFeed.deserialize(input.childOrEmpty("second_feed"));
        }
        for (int i = 0; i < products.length; i++) {
            products[i].deserialize(input.childOrEmpty("product_" + i));
        }
        progress = input.getIntOr("progress", 0);
        catalystUsed = input.getIntOr("catalyst_used", 0);
        catalystItem = input.getString("catalyst").map(Identifier::tryParse)
                .flatMap(id -> id == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(id)).orElse(null);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
        mode = process == RefiningProcess.CRACKING ? CrackingMode.byName(input.getStringOr("mode", CrackingMode.CATALYTIC.getSerializedName()))
                : CrackingMode.CATALYTIC;
    }

    public void save(ValueOutput output) {
        if (energy != null) {
            energy.serialize(output.child("energy"));
        }
        if (heat != null) {
            heat.serialize(output);
        }
        feed.serialize(output.child("feed"));
        if (secondFeed != null) {
            secondFeed.serialize(output.child("second_feed"));
        }
        for (int i = 0; i < products.length; i++) {
            products[i].serialize(output.child("product_" + i));
        }
        output.putInt("progress", progress);
        output.putInt("catalyst_used", catalystUsed);
        if (catalystItem != null) {
            output.putString("catalyst", BuiltInRegistries.ITEM.getKey(catalystItem).toString());
        }
        if (current != null) {
            output.putString("recipe", current.toString());
        }
        if (process == RefiningProcess.CRACKING) {
            output.putString("mode", mode.getSerializedName());
        }
    }

    // A product tank's fluid for display: what's in it, or what it's laid out for.
    public Fluid shownProduct(int index) {
        FilteredFluidTank tank = products[index];
        List<Fluid> laidOut = layoutProducts();
        return tank.getAmount() > 0 ? tank.getResource(0).getFluid() : index < laidOut.size() ? laidOut.get(index) : Fluids.EMPTY;
    }

    // Whether this item can go in the catalyst slot.
    public static boolean isCatalyst(@Nullable Level level, RefiningProcess process, ItemResource resource) {
        return process.catalysed() && MachineRecipes.isRefiningCatalyst(level, process, resource.toStack(1));
    }
}
