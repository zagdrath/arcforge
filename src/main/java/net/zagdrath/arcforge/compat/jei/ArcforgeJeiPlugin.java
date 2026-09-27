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
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataMaps;
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

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MachineCategories.Crushing(gui),
                new MachineCategories.Fiberizing(gui),
                new MachineCategories.Infusing(gui),
                new MachineCategories.Pressing(gui),
                new MachineCategories.Carbonizing(gui),
                new MachineCategories.ArcforgeSmelting(gui),
                new MachineCategories.Steam(gui),
                new MachineCategories.BurnerFuels(gui),
                new MultiblockCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MachineCategories.Crushing.TYPE, recipes(ModRecipes.CRUSHING.get()));
        registration.addRecipes(MachineCategories.Fiberizing.TYPE, recipes(ModRecipes.FIBERIZING.get()));
        registration.addRecipes(MachineCategories.Infusing.TYPE, recipes(ModRecipes.INFUSING.get()));
        registration.addRecipes(MachineCategories.Pressing.TYPE, recipes(ModRecipes.PRESSING.get()));
        registration.addRecipes(MachineCategories.Carbonizing.TYPE, recipes(ModRecipes.CARBONIZING.get()));
        registration.addRecipes(MachineCategories.ArcforgeSmelting.TYPE, recipes(ModRecipes.ARCFORGE_SMELTING.get()));
        registration.addRecipes(MachineCategories.Steam.TYPE, List.of(SteamGrade.values()).stream().map(MachineCategories.SteamRecipe::new).toList());

        List<MachineCategories.BurnerFuelRecipe> fuels = new ArrayList<>();
        BuiltInRegistries.FLUID.listElements().forEach(holder -> {
            BurnerFuel fuel = holder.getData(ModDataMaps.BURNER_FUELS);
            if (fuel != null && holder.value().isSource(holder.value().defaultFluidState())) {
                fuels.add(new MachineCategories.BurnerFuelRecipe(holder.value(), fuel));
            }
        });
        registration.addRecipes(MachineCategories.BurnerFuels.TYPE, fuels);

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

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(MachineCategories.Crushing.TYPE, ModBlocks.ARC_CRUSHER.get(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Fiberizing.TYPE, ModBlocks.FIBERIZER.get());
        registration.addCraftingStation(MachineCategories.Infusing.TYPE, ModBlocks.INFUSER.get());
        registration.addCraftingStation(MachineCategories.Pressing.TYPE, ModBlocks.METAL_PRESS.get(), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.Carbonizing.TYPE, ModBlocks.CARBONIZER.get());
        registration.addCraftingStation(MachineCategories.ArcforgeSmelting.TYPE, ModBlocks.ARCFORGE_FURNACE_PORT.get(),
                ModBlocks.ARCFORGE_FURNACE_BRICKS.get());
        registration.addCraftingStation(MachineCategories.Steam.TYPE, ModBlocks.STEAM_BOILER.get(), ModBlocks.STEAM_BOILER_ARRAY_CASING.get(),
                ModBlocks.STEAM_TURBINE.get(), ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        registration.addCraftingStation(MachineCategories.BurnerFuels.TYPE, ModBlocks.FUEL_BURNER.get());
        registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.INDUCTION_FURNACE.get(), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
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
