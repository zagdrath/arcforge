/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.ItemInfoTooltips;
import net.zagdrath.arcforge.client.screen.machine.FermenterScreen;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModDataMaps;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.SteamGrade;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.zagdrath.arcforge.client.handbook.RecipeLinks;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.zagdrath.arcforge.client.screen.multiblock.MetalPressingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.InductionFurnaceArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcCrushingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.CarbonizerScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcforgeFurnaceScreen;
import net.zagdrath.arcforge.client.screen.machine.FuelBurnerScreen;
import net.zagdrath.arcforge.client.screen.machine.MetalPressScreen;
import net.zagdrath.arcforge.client.screen.machine.InfuserScreen;
import net.zagdrath.arcforge.client.screen.machine.InductionFurnaceScreen;
import net.zagdrath.arcforge.client.screen.machine.FiberizerScreen;
import net.zagdrath.arcforge.client.screen.machine.ElectrolyzerScreen;
import net.zagdrath.arcforge.client.screen.machine.ChemicalReactorScreen;
import net.zagdrath.arcforge.client.screen.machine.AssemblerScreen;
import net.zagdrath.arcforge.client.screen.machine.ArcMelterScreen;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;

// JEI support (only loaded when JEI is installed): a category for each Arcforge machine's recipes, steam
// grades and burner fuels, a step-by-step build viewer for every multiblock, and each item's description
// as its JEI information page.
@JeiPlugin
public class ArcforgeJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    // Sheathed conduits are their own ingredients, one per colour, listed after the plain ones.
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        ModItems.allConduits().forEach(conduit -> registration.registerFromDataComponentTypes(conduit.get(), ModDataComponents.CONDUIT_COLOR.get()));
    }

    @Override
    public void registerExtraIngredients(mezz.jei.api.registration.IExtraIngredientRegistration registration) {
        List<ItemStack> sheathed = new ArrayList<>();
        for (var conduit : ModItems.allConduits()) {
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                ItemStack stack = new ItemStack(conduit.get());
                stack.set(ModDataComponents.CONDUIT_COLOR.get(), color);
                sheathed.add(stack);
            }
        }
        registration.addExtraItemStacks(sheathed);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MachineCategories.Crushing(gui),
                new MachineCategories.Fiberizing(gui),
                new MachineCategories.Infusing(gui),
                new MachineCategories.Pressing(gui),
                new MachineCategories.Carbonizing(gui),
                new MachineCategories.Melting(gui),
                new MachineCategories.Fermenting(gui),
                new MachineCategories.Milling(gui),
                new MachineCategories.OilPressing(gui),
                new MachineCategories.SeedExtracting(gui),
                new MachineCategories.Drying(gui),
                new MachineCategories.HydrothermalCarbonizing(gui),
                new MachineCategories.TreeCutting(gui),
                new MachineCategories.Sifting(gui),
                new MachineCategories.DiamondPressing(gui),
                new MachineCategories.CarbonReclaiming(gui),
                new MachineCategories.Gasifying(gui),
                new MachineCategories.FischerTropsch(gui),
                new MachineCategories.Vulcanizing(gui),
                new MachineCategories.ResinTap(gui),
                new MachineCategories.AirSeparating(gui),
                new MachineCategories.Synthesizing(gui),
                new MachineCategories.Digesting(gui),
                new MachineCategories.Cloche(gui),
                new MachineCategories.Greenhouse(gui),
                new MachineCategories.ConduitDyeingCategory(gui),
                new MachineCategories.ChemicalReacting(gui),
                new MachineCategories.Electrolyzing(gui),
                new MachineCategories.Evaporating(gui),
                new MachineCategories.ArcforgeSmelting(gui),
                new MachineCategories.Distilling(gui),
                new MachineCategories.Steam(gui),
                new MachineCategories.Superheating(gui),
                new MachineCategories.Condensing(gui),
                new MachineCategories.BurnerFuels(gui),
                new MachineCategories.GasTurbineFuels(gui),
                new MachineCategories.BatteryComponents(gui),
                new MultiblockCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MachineCategories.Crushing.TYPE, recipes(ModRecipes.CRUSHING.get()));
        registration.addRecipes(MachineCategories.Fiberizing.TYPE, recipes(ModRecipes.FIBERIZING.get()));
        registration.addRecipes(MachineCategories.Infusing.TYPE, recipes(ModRecipes.INFUSING.get()));
        registration.addRecipes(MachineCategories.Pressing.TYPE, recipes(ModRecipes.PRESSING.get()));
        registration.addRecipes(MachineCategories.Carbonizing.TYPE, recipes(ModRecipes.CARBONIZING.get()));
        registration.addRecipes(MachineCategories.Melting.TYPE, recipes(ModRecipes.MELTING.get()));
        registration.addRecipes(MachineCategories.Fermenting.TYPE, recipes(ModRecipes.FERMENTING.get()));
        registration.addRecipes(MachineCategories.Milling.TYPE, recipes(ModRecipes.MILLING.get()));
        registration.addRecipes(MachineCategories.OilPressing.TYPE, recipes(ModRecipes.OIL_PRESSING.get()));
        registration.addRecipes(MachineCategories.SeedExtracting.TYPE, recipes(ModRecipes.SEED_EXTRACTING.get()));
        registration.addRecipes(MachineCategories.Drying.TYPE, recipes(ModRecipes.DRYING.get()));
        registration.addRecipes(MachineCategories.HydrothermalCarbonizing.TYPE, recipes(ModRecipes.HYDROTHERMAL_CARBONIZING.get()));
        registration.addRecipes(MachineCategories.Sifting.TYPE, recipes(ModRecipes.SIFTING.get()));
        registration.addRecipes(MachineCategories.DiamondPressing.TYPE, recipes(ModRecipes.DIAMOND_PRESSING.get()));
        registration.addRecipes(MachineCategories.CarbonReclaiming.TYPE, recipes(ModRecipes.CARBON_RECLAIMING.get()));
        registration.addRecipes(MachineCategories.Gasifying.TYPE, recipes(ModRecipes.GASIFYING.get()));
        registration.addRecipes(MachineCategories.FischerTropsch.TYPE, recipes(ModRecipes.FISCHER_TROPSCH.get()));
        // Every sapling; its log and leaves found by name (oak_sapling: oak_log, oak_leaves), where they exist.
        List<MachineCategories.TreeCuttingRecipe> trees = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            Item sapling = holder.value();
            if (!new ItemStack(sapling).is(net.minecraft.tags.ItemTags.SAPLINGS)) {
                return;
            }
            Identifier id = holder.key().identifier();
            String stem = id.getPath().endsWith("_sapling") ? id.getPath().substring(0, id.getPath().length() - "_sapling".length()) : id.getPath();
            trees.add(new MachineCategories.TreeCuttingRecipe(sapling, named(id.getNamespace(), stem + "_log"), named(id.getNamespace(), stem + "_leaves")));
        });
        registration.addRecipes(MachineCategories.TreeCutting.TYPE, trees);
        registration.addRecipes(MachineCategories.Vulcanizing.TYPE, recipes(ModRecipes.VULCANIZING.get()));
        registration.addRecipes(MachineCategories.ResinTap.TYPE, MachineCategories.ResinTapping.all());
        registration.addRecipes(MachineCategories.AirSeparating.TYPE, recipes(ModRecipes.AIR_SEPARATING.get()));
        registration.addRecipes(MachineCategories.Synthesizing.TYPE, recipes(ModRecipes.SYNTHESIZING.get()));
        registration.addRecipes(MachineCategories.Digesting.TYPE, recipes(ModRecipes.DIGESTING.get()));
        registration.addRecipes(MachineCategories.Cloche.TYPE, recipes(ModRecipes.CLOCHE.get()));
        registration.addRecipes(MachineCategories.Greenhouse.TYPE, MachineCategories.Greenhouse.plantings(recipes(ModRecipes.CLOCHE.get())));
        registration.addRecipes(MachineCategories.ConduitDyeingCategory.TYPE,
                ModItems.allConduits().stream().map(conduit -> new MachineCategories.ConduitDyeing(conduit.get())).toList());
        registration.addRecipes(MachineCategories.ChemicalReacting.TYPE, recipes(ModRecipes.CHEMICAL_REACTING.get()));
        registration.addRecipes(MachineCategories.Electrolyzing.TYPE, recipes(ModRecipes.ELECTROLYZING.get()));
        registration.addRecipes(MachineCategories.Evaporating.TYPE, recipes(ModRecipes.EVAPORATING.get()));
        registration.addRecipes(MachineCategories.ArcforgeSmelting.TYPE, recipes(ModRecipes.ARCFORGE_SMELTING.get()));
        registration.addRecipes(MachineCategories.Distilling.TYPE, recipes(ModRecipes.DISTILLING.get()).stream()
                .flatMap(holder -> MachineCategories.DistillingPage.of(holder).stream()).toList());
        registration.addRecipes(MachineCategories.Steam.TYPE, List.of(SteamGrade.values()).stream().map(MachineCategories.SteamRecipe::new).toList());
        registration.addRecipes(MachineCategories.Superheating.TYPE, MachineCategories.SuperheatingRecipe.all());
        registration.addRecipes(MachineCategories.Condensing.TYPE, List.of(new MachineCategories.CondensingRecipe()));

        List<MachineCategories.BurnerFuelRecipe> fuels = new ArrayList<>();
        BuiltInRegistries.FLUID.listElements().forEach(holder -> {
            BurnerFuel fuel = holder.getData(ModDataMaps.BURNER_FUELS);
            if (fuel != null && holder.value().isSource(holder.value().defaultFluidState())) {
                fuels.add(new MachineCategories.BurnerFuelRecipe(holder.value(), fuel));
            }
        });
        registration.addRecipes(MachineCategories.BurnerFuels.TYPE, fuels);
        registration.addRecipes(MachineCategories.GasTurbineFuels.TYPE, fuels.stream().filter(fuel -> fuel.fuel().gasTurbine()).toList());
        List<MachineCategories.BatteryComponentRecipe> batteryParts = new ArrayList<>();
        for (net.zagdrath.arcforge.conduit.ConduitTier tier : net.zagdrath.arcforge.conduit.ConduitTier.values()) {
            batteryParts.add(new MachineCategories.BatteryComponentRecipe(ModBlocks.lithiumCell(tier).get().asItem(), tier, true));
        }
        for (net.zagdrath.arcforge.conduit.ConduitTier tier : net.zagdrath.arcforge.conduit.ConduitTier.values()) {
            batteryParts.add(new MachineCategories.BatteryComponentRecipe(ModBlocks.powerRegulator(tier).get().asItem(), tier, false));
        }
        registration.addRecipes(MachineCategories.BatteryComponents.TYPE, batteryParts);

        registration.addRecipes(MultiblockCategory.TYPE, MultiblockBlueprints.all().stream().map(MultiblockCategory.Build::new).toList());

        // Every item's description as its information page.
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String key = Arcforge.MODID.equals(id.getNamespace()) ? ItemInfoTooltips.descriptionKey(id.getPath()) : null;
            if (key != null) {
                registration.addItemStackInfo(new ItemStack(item), Component.translatable(key));
            }
        }
    }

    // The item with this id, or EMPTY.
    private static ItemStack named(String namespace, String path) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace, path)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // The recipes of a type the server sent.
    private static <I extends RecipeInput, R extends Recipe<I>> List<RecipeHolder<R>> recipes(RecipeType<R> type) {
        return List.copyOf(MachineRecipes.clientRecipes().byType(type));
    }

    // The Engineer's Handbook opens an item's recipes here.
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeLinks.setViewer(stack -> runtime.getRecipesGui().show(
                runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack)));
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeLinks.setViewer(null);
    }

    // Clicking a machine's progress arrow shows everything it makes (positions from each screen's layout; arrows
    // are 21x15). The three-lane arrays get one per lane, and the Fuel Burner's flame shows its fuels.
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Drag-and-drop from JEI onto ghost slots.
        registration.addGhostIngredientHandler(net.zagdrath.arcforge.client.screen.conduit.ConduitFilterScreen.class, new GhostSlotHandler<>());
        registration.addGhostIngredientHandler(net.zagdrath.arcforge.client.screen.machine.ArcQuarryConfigScreen.class, new GhostSlotHandler<>());
        registration.addGhostIngredientHandler(AssemblerScreen.class, new GhostSlotHandler<>());
        registration.addRecipeClickArea(ArcCrusherScreen.class, 67, 35, ARROW_W, ARROW_H, MachineCategories.Crushing.TYPE);
        registration.addRecipeClickArea(ArcMelterScreen.class, 67, 35, ARROW_W, ARROW_H, MachineCategories.Melting.TYPE);
        registration.addRecipeClickArea(FermenterScreen.class, 66, 35, ARROW_W, ARROW_H, MachineCategories.Fermenting.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.OilPressScreen.class, 67, 35, ARROW_W, ARROW_H, MachineCategories.OilPressing.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.SeedExtractorScreen.class, 67, 35, ARROW_W, ARROW_H,
                MachineCategories.SeedExtracting.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.GrainDryerScreen.class, 68, 35, ARROW_W, ARROW_H, MachineCategories.Drying.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.HydrothermalCarbonizerScreen.class, 68, 35, ARROW_W, ARROW_H,
                MachineCategories.HydrothermalCarbonizing.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.VulcanizerScreen.class, 68, 35, ARROW_W, ARROW_H,
                MachineCategories.Vulcanizing.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.SifterScreen.class, 78, 35, ARROW_W, ARROW_H,
                MachineCategories.Sifting.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.DiamondPressScreen.class, 68, 35, ARROW_W, ARROW_H,
                MachineCategories.DiamondPressing.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.CarbonReclaimerScreen.class, 68, 35, ARROW_W, ARROW_H,
                MachineCategories.CarbonReclaiming.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.GasifierScreen.class, 68, 35, ARROW_W, ARROW_H,
                MachineCategories.Gasifying.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.FischerTropschReactorScreen.class, 64, 35, ARROW_W, ARROW_H,
                MachineCategories.FischerTropsch.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.AirSeparatorScreen.class, 114, 35, ARROW_W, ARROW_H,
                MachineCategories.AirSeparating.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.HaberReactorScreen.class, 81, 35, ARROW_W, ARROW_H,
                MachineCategories.Synthesizing.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.multiblock.ThermalEvaporatorScreen.class, 28, 27, ARROW_W, ARROW_H,
                MachineCategories.Evaporating.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.multiblock.BiogasDigesterScreen.class, 52, 27, ARROW_W, ARROW_H,
                MachineCategories.Digesting.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.ClocheScreen.class, 72, 30, ARROW_W, ARROW_H, MachineCategories.Cloche.TYPE);
        registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.multiblock.GreenhouseScreen.class, 76, 50, 36, 10, MachineCategories.Greenhouse.TYPE);
        registration.addRecipeClickArea(AssemblerScreen.class, 88, 35, ARROW_W, ARROW_H, RecipeTypes.CRAFTING);
        registration.addRecipeClickArea(ChemicalReactorScreen.class, 90, 35, ARROW_W, ARROW_H, MachineCategories.ChemicalReacting.TYPE);
        registration.addRecipeClickArea(ElectrolyzerScreen.class, 98, 35, ARROW_W, ARROW_H, MachineCategories.Electrolyzing.TYPE);
        registration.addRecipeClickArea(FiberizerScreen.class, 68, 35, ARROW_W, ARROW_H, MachineCategories.Fiberizing.TYPE);
        registration.addRecipeClickArea(InductionFurnaceScreen.class, 78, 35, ARROW_W, ARROW_H, RecipeTypes.SMELTING);
        registration.addRecipeClickArea(InfuserScreen.class, 96, 35, ARROW_W, ARROW_H, MachineCategories.Infusing.TYPE);
        registration.addRecipeClickArea(MetalPressScreen.class, 78, 35, ARROW_W, ARROW_H, MachineCategories.Pressing.TYPE);
        registration.addRecipeClickArea(ArcforgeFurnaceScreen.class, 84, 24, ARROW_W, ARROW_H, MachineCategories.ArcforgeSmelting.TYPE);
        registration.addRecipeClickArea(CarbonizerScreen.class, 52, 24, ARROW_W, ARROW_H, MachineCategories.Carbonizing.TYPE);
        registration.addRecipeClickArea(FuelBurnerScreen.class, 127, 52, 14, 14, MachineCategories.BurnerFuels.TYPE);
        for (int lane = 0; lane < 3; lane++) {
            int y = 20 + lane * 18;
            registration.addRecipeClickArea(ArcCrushingArrayScreen.class, 52, y, ARROW_W, ARROW_H, MachineCategories.Crushing.TYPE);
            registration.addRecipeClickArea(InductionFurnaceArrayScreen.class, 54, y, ARROW_W, ARROW_H, RecipeTypes.SMELTING);
            registration.addRecipeClickArea(MetalPressingArrayScreen.class, 68, y, ARROW_W, ARROW_H, MachineCategories.Pressing.TYPE);
            registration.addRecipeClickArea(net.zagdrath.arcforge.client.screen.machine.MillScreen.class, 52, y, ARROW_W, ARROW_H, MachineCategories.Milling.TYPE);
        }
    }

    private static final int ARROW_W = 21, ARROW_H = 15;

    // JEI's + on a crafting recipe fills an open Assembler's pattern.
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new AssemblerTransferHandler(), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(RecipeTypes.CRAFTING, ModBlocks.ASSEMBLER.get());
        registration.addCraftingStation(MachineCategories.Crushing.TYPE, ModBlocks.ARC_CRUSHER.get(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Fiberizing.TYPE, ModBlocks.FIBERIZER.get());
        registration.addCraftingStation(MachineCategories.Infusing.TYPE, ModBlocks.INFUSER.get());
        registration.addCraftingStation(MachineCategories.Pressing.TYPE, ModBlocks.METAL_PRESS.get(), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Carbonizing.TYPE, ModBlocks.CARBONIZER.get());
        registration.addCraftingStation(MachineCategories.Melting.TYPE, ModBlocks.ARC_MELTER.get());
        registration.addCraftingStation(MachineCategories.Fermenting.TYPE, ModBlocks.FERMENTER.get());
        registration.addCraftingStation(MachineCategories.Milling.TYPE, ModBlocks.MILLSTONE.get(), ModBlocks.MILL.get());
        registration.addCraftingStation(MachineCategories.OilPressing.TYPE, ModBlocks.OIL_PRESS.get());
        registration.addCraftingStation(MachineCategories.SeedExtracting.TYPE, ModBlocks.SEED_EXTRACTOR.get());
        registration.addCraftingStation(MachineCategories.Drying.TYPE, ModBlocks.GRAIN_DRYER.get());
        registration.addCraftingStation(MachineCategories.HydrothermalCarbonizing.TYPE, ModBlocks.HYDROTHERMAL_CARBONIZER.get());
        registration.addCraftingStation(MachineCategories.TreeCutting.TYPE, ModBlocks.TREE_CUTTER.get());
        registration.addCraftingStation(MachineCategories.Sifting.TYPE, ModBlocks.SIFTER.get());
        registration.addCraftingStation(MachineCategories.DiamondPressing.TYPE, ModBlocks.DIAMOND_PRESS.get());
        registration.addCraftingStation(MachineCategories.CarbonReclaiming.TYPE, ModBlocks.CARBON_RECLAIMER.get());
        registration.addCraftingStation(MachineCategories.Gasifying.TYPE, ModBlocks.GASIFIER.get());
        registration.addCraftingStation(MachineCategories.FischerTropsch.TYPE, ModBlocks.FISCHER_TROPSCH_REACTOR.get());
        registration.addCraftingStation(MachineCategories.Vulcanizing.TYPE, ModBlocks.VULCANIZER.get());
        registration.addCraftingStation(MachineCategories.ResinTap.TYPE, ModBlocks.RESIN_TAP.get());
        registration.addCraftingStation(MachineCategories.AirSeparating.TYPE, ModBlocks.AIR_SEPARATOR.get());
        registration.addCraftingStation(MachineCategories.Synthesizing.TYPE, ModBlocks.HABER_REACTOR.get());
        registration.addCraftingStation(MachineCategories.Digesting.TYPE, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get(), ModBlocks.DIGESTER_CASING.get());
        registration.addCraftingStation(MachineCategories.Cloche.TYPE, ModBlocks.GLASS_CLOCHE.get(), ModBlocks.GROW_CHAMBER.get(), ModBlocks.HYDROPONIC_CELL.get());
        registration.addCraftingStation(MachineCategories.Greenhouse.TYPE, ModBlocks.GREENHOUSE_CONTROLLER.get(), ModBlocks.PLANTING_BED.get());
        registration.addCraftingStation(MachineCategories.ConduitDyeingCategory.TYPE, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        registration.addCraftingStation(MachineCategories.ChemicalReacting.TYPE, ModBlocks.CHEMICAL_REACTOR.get());
        registration.addCraftingStation(MachineCategories.Electrolyzing.TYPE, ModBlocks.ELECTROLYZER.get());
        registration.addCraftingStation(MachineCategories.Evaporating.TYPE, ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get(),
                ModBlocks.THERMAL_EVAPORATOR_CASING.get());
        registration.addCraftingStation(MachineCategories.ArcforgeSmelting.TYPE, ModBlocks.ARCFORGE_FURNACE_PORT.get(),
                ModBlocks.ARCFORGE_FURNACE_BRICKS.get());
        registration.addCraftingStation(MachineCategories.Steam.TYPE, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Superheating.TYPE, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        // The turbine makes the Exhaust Steam the condenser takes.
        registration.addCraftingStation(MachineCategories.Condensing.TYPE, ModBlocks.CONDENSER_ARRAY_CASING.get(), ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.BurnerFuels.TYPE, ModBlocks.FUEL_BURNER.get(), ModBlocks.FIREBOX_ARRAY_CONTROLLER.get());
        registration.addCraftingStation(MachineCategories.GasTurbineFuels.TYPE, ModBlocks.GAS_TURBINE_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.BatteryComponents.TYPE, ModBlocks.BATTERY_ARRAY_CONTROLLER.get(), ModBlocks.BATTERY_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Distilling.TYPE, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get(),
                ModBlocks.DISTILLATION_ARRAY_CASING.get(), ModBlocks.TRAY_LEVEL_CASING.get());
        registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.INDUCTION_FURNACE.get(), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        // The Compost Bin takes what a composter takes, at the same chances (the minecraft:compostable component).
        registration.addCraftingStation(RecipeTypes.COMPOSTING, ModBlocks.COMPOST_BIN.get());
        List<ItemLike> multiblockBlocks = new ArrayList<>();
        for (MultiblockBlueprints.Blueprint blueprint : MultiblockBlueprints.all()) {
            for (Item item : blueprint.bill().keySet()) {
                if (!multiblockBlocks.contains(item)) {
                    multiblockBlocks.add(item);
                }
            }
        }
        registration.addCraftingStation(MultiblockCategory.TYPE, multiblockBlocks.toArray(ItemLike[]::new));
    }
}
