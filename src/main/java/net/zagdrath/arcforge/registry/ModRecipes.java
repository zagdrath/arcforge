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
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.CrushingRecipe;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.FiberizingRecipe;
import net.zagdrath.arcforge.recipe.InfusingRecipe;
import net.zagdrath.arcforge.recipe.MeltingRecipe;
import net.zagdrath.arcforge.recipe.PressingRecipe;
import net.zagdrath.arcforge.recipe.TierUpgradeRecipe;

// Data-driven machine recipes: data/<namespace>/recipe/*.json with type arcforge:carbonizing,
// arcforge:arcforge_smelting, arcforge:chemical_reacting, arcforge:crushing, arcforge:distilling, arcforge:electrolyzing, arcforge:fiberizing, arcforge:infusing, arcforge:melting or arcforge:pressing. All are synced to clients so GUI slots know what they accept,
// as are vanilla smelting recipes (for the Induction Furnaces).
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Arcforge.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Arcforge.MODID);
    public static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, Arcforge.MODID);

    public static final Supplier<RecipeType<CarbonizingRecipe>> CARBONIZING = RECIPE_TYPES.register("carbonizing",
            () -> RecipeType.simple(id("carbonizing")));
    public static final Supplier<RecipeType<ArcforgeSmeltingRecipe>> ARCFORGE_SMELTING = RECIPE_TYPES.register("arcforge_smelting",
            () -> RecipeType.simple(id("arcforge_smelting")));
    public static final Supplier<RecipeType<ChemicalReactingRecipe>> CHEMICAL_REACTING = RECIPE_TYPES.register("chemical_reacting",
            () -> RecipeType.simple(id("chemical_reacting")));
    public static final Supplier<RecipeType<CrushingRecipe>> CRUSHING = RECIPE_TYPES.register("crushing",
            () -> RecipeType.simple(id("crushing")));
    public static final Supplier<RecipeType<DistillingRecipe>> DISTILLING = RECIPE_TYPES.register("distilling",
            () -> RecipeType.simple(id("distilling")));
    public static final Supplier<RecipeType<ElectrolyzingRecipe>> ELECTROLYZING = RECIPE_TYPES.register("electrolyzing",
            () -> RecipeType.simple(id("electrolyzing")));
    public static final Supplier<RecipeType<FiberizingRecipe>> FIBERIZING = RECIPE_TYPES.register("fiberizing",
            () -> RecipeType.simple(id("fiberizing")));
    public static final Supplier<RecipeType<InfusingRecipe>> INFUSING = RECIPE_TYPES.register("infusing",
            () -> RecipeType.simple(id("infusing")));
    public static final Supplier<RecipeType<MeltingRecipe>> MELTING = RECIPE_TYPES.register("melting",
            () -> RecipeType.simple(id("melting")));
    public static final Supplier<RecipeType<PressingRecipe>> PRESSING = RECIPE_TYPES.register("pressing",
            () -> RecipeType.simple(id("pressing")));

    public static final Supplier<RecipeSerializer<CarbonizingRecipe>> CARBONIZING_SERIALIZER = RECIPE_SERIALIZERS.register("carbonizing",
            () -> new RecipeSerializer<>(CarbonizingRecipe.MAP_CODEC, CarbonizingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<ArcforgeSmeltingRecipe>> ARCFORGE_SMELTING_SERIALIZER = RECIPE_SERIALIZERS.register("arcforge_smelting",
            () -> new RecipeSerializer<>(ArcforgeSmeltingRecipe.MAP_CODEC, ArcforgeSmeltingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<ChemicalReactingRecipe>> CHEMICAL_REACTING_SERIALIZER = RECIPE_SERIALIZERS.register("chemical_reacting",
            () -> new RecipeSerializer<>(ChemicalReactingRecipe.MAP_CODEC, ChemicalReactingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<CrushingRecipe>> CRUSHING_SERIALIZER = RECIPE_SERIALIZERS.register("crushing",
            () -> new RecipeSerializer<>(CrushingRecipe.MAP_CODEC, CrushingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<DistillingRecipe>> DISTILLING_SERIALIZER = RECIPE_SERIALIZERS.register("distilling",
            () -> new RecipeSerializer<>(DistillingRecipe.MAP_CODEC, DistillingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<ElectrolyzingRecipe>> ELECTROLYZING_SERIALIZER = RECIPE_SERIALIZERS.register("electrolyzing",
            () -> new RecipeSerializer<>(ElectrolyzingRecipe.MAP_CODEC, ElectrolyzingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<FiberizingRecipe>> FIBERIZING_SERIALIZER = RECIPE_SERIALIZERS.register("fiberizing",
            () -> new RecipeSerializer<>(FiberizingRecipe.MAP_CODEC, FiberizingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<InfusingRecipe>> INFUSING_SERIALIZER = RECIPE_SERIALIZERS.register("infusing",
            () -> new RecipeSerializer<>(InfusingRecipe.MAP_CODEC, InfusingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<MeltingRecipe>> MELTING_SERIALIZER = RECIPE_SERIALIZERS.register("melting",
            () -> new RecipeSerializer<>(MeltingRecipe.MAP_CODEC, MeltingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<PressingRecipe>> PRESSING_SERIALIZER = RECIPE_SERIALIZERS.register("pressing",
            () -> new RecipeSerializer<>(PressingRecipe.MAP_CODEC, PressingRecipe.STREAM_CODEC));
    // A crafting recipe (vanilla's crafting type) that keeps the contents of what it upgrades.
    public static final Supplier<RecipeSerializer<TierUpgradeRecipe>> TIER_UPGRADE_SERIALIZER = RECIPE_SERIALIZERS.register("tier_upgrade",
            () -> new RecipeSerializer<>(TierUpgradeRecipe.MAP_CODEC, TierUpgradeRecipe.STREAM_CODEC));

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
        event.sendRecipes(CARBONIZING.get(), ARCFORGE_SMELTING.get(), CHEMICAL_REACTING.get(), CRUSHING.get(), DISTILLING.get(), ELECTROLYZING.get(), FIBERIZING.get(), INFUSING.get(), MELTING.get(), PRESSING.get(), RecipeType.SMELTING);
    }
}
