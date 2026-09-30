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
import net.zagdrath.arcforge.recipe.AirSeparatingRecipe;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ConduitDyeingRecipe;
import net.zagdrath.arcforge.recipe.CrushingRecipe;
import net.zagdrath.arcforge.recipe.DigestingRecipe;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.FermentingRecipe;
import net.zagdrath.arcforge.recipe.FiberizingRecipe;
import net.zagdrath.arcforge.recipe.InfusingRecipe;
import net.zagdrath.arcforge.recipe.JetpackPlatingRecipe;
import net.zagdrath.arcforge.recipe.MeltingRecipe;
import net.zagdrath.arcforge.recipe.PressingRecipe;
import net.zagdrath.arcforge.recipe.DryingRecipe;
import net.zagdrath.arcforge.recipe.SeedExtractingRecipe;
import net.zagdrath.arcforge.recipe.OilPressingRecipe;
import net.zagdrath.arcforge.recipe.MillingRecipe;
import net.zagdrath.arcforge.recipe.SynthesizingRecipe;
import net.zagdrath.arcforge.recipe.TierUpgradeRecipe;
import net.zagdrath.arcforge.recipe.ClocheRecipe;

// Data-driven machine recipes: data/<namespace>/recipe/*.json with type arcforge:carbonizing,
// arcforge:arcforge_smelting, arcforge:chemical_reacting, arcforge:crushing, arcforge:distilling, arcforge:electrolyzing, arcforge:fiberizing, arcforge:infusing, arcforge:melting, arcforge:pressing, and the farm processing
// arcforge:milling, arcforge:oil_pressing, arcforge:seed_extracting and arcforge:drying, and the farm chemistry
// arcforge:air_separating, arcforge:synthesizing and arcforge:digesting. All are synced to clients so GUI slots know what they accept,
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
    public static final Supplier<RecipeType<FermentingRecipe>> FERMENTING = RECIPE_TYPES.register("fermenting",
            () -> RecipeType.simple(id("fermenting")));
    public static final Supplier<RecipeType<PressingRecipe>> PRESSING = RECIPE_TYPES.register("pressing",
            () -> RecipeType.simple(id("pressing")));
    // Farm processing: the Millstone and Mill, the Oil Press, the Seed Extractor and the Grain Dryer.
    public static final Supplier<RecipeType<MillingRecipe>> MILLING = RECIPE_TYPES.register("milling",
            () -> RecipeType.simple(id("milling")));
    public static final Supplier<RecipeType<OilPressingRecipe>> OIL_PRESSING = RECIPE_TYPES.register("oil_pressing",
            () -> RecipeType.simple(id("oil_pressing")));
    public static final Supplier<RecipeType<SeedExtractingRecipe>> SEED_EXTRACTING = RECIPE_TYPES.register("seed_extracting",
            () -> RecipeType.simple(id("seed_extracting")));
    public static final Supplier<RecipeType<DryingRecipe>> DRYING = RECIPE_TYPES.register("drying",
            () -> RecipeType.simple(id("drying")));
    // Farm chemistry: the Air Separator, the Haber Reactor and the Biogas Digester.
    public static final Supplier<RecipeType<AirSeparatingRecipe>> AIR_SEPARATING = RECIPE_TYPES.register("air_separating",
            () -> RecipeType.simple(id("air_separating")));
    public static final Supplier<RecipeType<SynthesizingRecipe>> SYNTHESIZING = RECIPE_TYPES.register("synthesizing",
            () -> RecipeType.simple(id("synthesizing")));
    public static final Supplier<RecipeType<ClocheRecipe>> CLOCHE = RECIPE_TYPES.register("cloche",
            () -> RecipeType.simple(id("cloche")));
    public static final Supplier<RecipeType<DigestingRecipe>> DIGESTING = RECIPE_TYPES.register("digesting",
            () -> RecipeType.simple(id("digesting")));

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
    public static final Supplier<RecipeSerializer<FermentingRecipe>> FERMENTING_SERIALIZER = RECIPE_SERIALIZERS.register("fermenting",
            () -> new RecipeSerializer<>(FermentingRecipe.MAP_CODEC, FermentingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<PressingRecipe>> PRESSING_SERIALIZER = RECIPE_SERIALIZERS.register("pressing",
            () -> new RecipeSerializer<>(PressingRecipe.MAP_CODEC, PressingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<MillingRecipe>> MILLING_SERIALIZER = RECIPE_SERIALIZERS.register("milling",
            () -> new RecipeSerializer<>(MillingRecipe.MAP_CODEC, MillingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<OilPressingRecipe>> OIL_PRESSING_SERIALIZER = RECIPE_SERIALIZERS.register("oil_pressing",
            () -> new RecipeSerializer<>(OilPressingRecipe.MAP_CODEC, OilPressingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<SeedExtractingRecipe>> SEED_EXTRACTING_SERIALIZER = RECIPE_SERIALIZERS.register("seed_extracting",
            () -> new RecipeSerializer<>(SeedExtractingRecipe.MAP_CODEC, SeedExtractingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<DryingRecipe>> DRYING_SERIALIZER = RECIPE_SERIALIZERS.register("drying",
            () -> new RecipeSerializer<>(DryingRecipe.MAP_CODEC, DryingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<AirSeparatingRecipe>> AIR_SEPARATING_SERIALIZER = RECIPE_SERIALIZERS.register("air_separating",
            () -> new RecipeSerializer<>(AirSeparatingRecipe.MAP_CODEC, AirSeparatingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<SynthesizingRecipe>> SYNTHESIZING_SERIALIZER = RECIPE_SERIALIZERS.register("synthesizing",
            () -> new RecipeSerializer<>(SynthesizingRecipe.MAP_CODEC, SynthesizingRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<ClocheRecipe>> CLOCHE_SERIALIZER = RECIPE_SERIALIZERS.register("cloche",
            () -> new RecipeSerializer<>(ClocheRecipe.MAP_CODEC, ClocheRecipe.STREAM_CODEC));
    public static final Supplier<RecipeSerializer<DigestingRecipe>> DIGESTING_SERIALIZER = RECIPE_SERIALIZERS.register("digesting",
            () -> new RecipeSerializer<>(DigestingRecipe.MAP_CODEC, DigestingRecipe.STREAM_CODEC));
    // A crafting recipe (vanilla's crafting type) that keeps the contents of what it upgrades.
    public static final Supplier<RecipeSerializer<TierUpgradeRecipe>> TIER_UPGRADE_SERIALIZER = RECIPE_SERIALIZERS.register("tier_upgrade",
            () -> new RecipeSerializer<>(TierUpgradeRecipe.MAP_CODEC, TierUpgradeRecipe.STREAM_CODEC));

    // Sheathing conduits in coloured plastic, and stripping it (a crafting recipe of vanilla's type).
    public static final Supplier<RecipeSerializer<ConduitDyeingRecipe>> CONDUIT_DYEING_SERIALIZER = RECIPE_SERIALIZERS.register("conduit_dyeing",
            () -> new RecipeSerializer<>(ConduitDyeingRecipe.MAP_CODEC, ConduitDyeingRecipe.STREAM_CODEC));

    // Smithing a Steel Chestplate onto a Jetpack (a smithing recipe, so it has no type of its own).
    public static final Supplier<RecipeSerializer<JetpackPlatingRecipe>> JETPACK_PLATING_SERIALIZER = RECIPE_SERIALIZERS.register("jetpack_plating",
            () -> new RecipeSerializer<>(JetpackPlatingRecipe.MAP_CODEC, JetpackPlatingRecipe.STREAM_CODEC));

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
        event.sendRecipes(CARBONIZING.get(), ARCFORGE_SMELTING.get(), CHEMICAL_REACTING.get(), CRUSHING.get(), DISTILLING.get(), ELECTROLYZING.get(), FERMENTING.get(), FIBERIZING.get(), INFUSING.get(), MELTING.get(), PRESSING.get(),
                MILLING.get(), OIL_PRESSING.get(), SEED_EXTRACTING.get(), DRYING.get(), AIR_SEPARATING.get(), SYNTHESIZING.get(), DIGESTING.get(), CLOCHE.get(),
                RecipeType.SMELTING);
    }
}
