/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;

// Data-driven machine recipes: data/<namespace>/recipe/*.json with type arcforge:carbonizing or
// arcforge:arcforge_smelting. Both types are synced to clients so GUI slots know what they accept.
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Arcforge.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Arcforge.MODID);
    public static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, Arcforge.MODID);

    public static final Supplier<RecipeType<CarbonizingRecipe>> CARBONIZING = RECIPE_TYPES.register("carbonizing",
            () -> RecipeType.simple(id("carbonizing")));
    public static final Supplier<RecipeType<ArcforgeSmeltingRecipe>> ARCFORGE_SMELTING = RECIPE_TYPES.register("arcforge_smelting",
            () -> RecipeType.simple(id("arcforge_smelting")));

    public static final Supplier<RecipeSerializer<CarbonizingRecipe>> CARBONIZING_SERIALIZER = RECIPE_SERIALIZERS.register("carbonizing",
            () -> new RecipeSerializer<>(CarbonizingRecipe.MAP_CODEC, CarbonizingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<ArcforgeSmeltingRecipe>> ARCFORGE_SMELTING_SERIALIZER = RECIPE_SERIALIZERS.register("arcforge_smelting",
            () -> new RecipeSerializer<>(ArcforgeSmeltingRecipe.MAP_CODEC, ArcforgeSmeltingRecipe.STREAM_CODEC));

    // Machine recipes never show in the recipe book, but every recipe must name a category.
    public static final Supplier<RecipeBookCategory> MACHINE_CATEGORY = RECIPE_BOOK_CATEGORIES.register("machine", RecipeBookCategory::new);

    private ModRecipes() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, path);
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        RECIPE_BOOK_CATEGORIES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(ModRecipes::syncToClients);
    }

    private static void syncToClients(OnDatapackSyncEvent event) {
        event.sendRecipes(CARBONIZING.get(), ARCFORGE_SMELTING.get());
    }
}
