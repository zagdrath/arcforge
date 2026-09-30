/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Glass Cloche, Grow Chamber and Hydroponic Cell (arcforge:cloche): a seed planted in a soil grows over `time` ticks
// (at the Glass Cloche's speed; the others are faster) and is harvested into `results`, each rolled with its chance,
// plus seed_output copies of the planted item (flowers). The seed stays planted and grows again.
//  - "soil": the soils it grows in (an item ingredient; see the arcforge:cloche_soils data map for how each soil looks and
//    how fast crops grow in it). The Hydroponic Cell has no soil and ignores it.
//  - "hydroponic_only": only the Hydroponic Cell grows it (saplings, flowers, Hops).
//  - "render": how it's drawn growing inside: a block (default: the seed's own block, if it's a block item), fixed
//    properties, "grow" ("age": step its age property with the growth, the default; "scale": grow it in size) and
//    "height" in blocks (2 for crops whose model reaches into the block above).
// Seeds of crops no recipe names still grow if their block is a vanilla-style crop (a CropBlock): see ClochePlants.
public record ClocheRecipe(Ingredient seed, Optional<Ingredient> soil, List<Output> results, int seedOutput, int time, boolean hydroponicOnly,
        Optional<Render> render) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 2_400;

    // One harvested item and the chance it comes (1 = always).
    public record Output(ItemStackTemplate item, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Output::item),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(Output::chance))
                .apply(i, Output::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Output::item,
                ByteBufCodecs.FLOAT, Output::chance,
                Output::new);
    }

    // How the plant is drawn inside the farm.
    public record Render(Optional<Block> block, Map<String, String> properties, String grow, int height) {
        public static final Codec<Render> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(Render::block),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(Render::properties),
                Codec.STRING.optionalFieldOf("grow", "age").forGetter(Render::grow),
                Codec.intRange(1, 3).optionalFieldOf("height", 1).forGetter(Render::height))
                .apply(i, Render::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Render> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.BLOCK)), Render::block,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), Render::properties,
                ByteBufCodecs.STRING_UTF8, Render::grow,
                ByteBufCodecs.VAR_INT, Render::height,
                Render::new);

        public static final Render DEFAULT = new Render(Optional.empty(), Map.of(), "age", 1);
    }

    public static final MapCodec<ClocheRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("seed").forGetter(ClocheRecipe::seed),
            Ingredient.CODEC.optionalFieldOf("soil").forGetter(ClocheRecipe::soil),
            Output.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(ClocheRecipe::results),
            Codec.intRange(0, 64).optionalFieldOf("seed_output", 0).forGetter(ClocheRecipe::seedOutput),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ClocheRecipe::time),
            Codec.BOOL.optionalFieldOf("hydroponic_only", false).forGetter(ClocheRecipe::hydroponicOnly),
            Render.CODEC.optionalFieldOf("render").forGetter(ClocheRecipe::render))
            .apply(i, ClocheRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClocheRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, ClocheRecipe::seed,
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), ClocheRecipe::soil,
            Output.STREAM_CODEC.apply(ByteBufCodecs.list()), ClocheRecipe::results,
            ByteBufCodecs.VAR_INT, ClocheRecipe::seedOutput,
            ByteBufCodecs.VAR_INT, ClocheRecipe::time,
            ByteBufCodecs.BOOL, ClocheRecipe::hydroponicOnly,
            ByteBufCodecs.optional(Render.STREAM_CODEC), ClocheRecipe::render,
            ClocheRecipe::new);

    // Whether this recipe grows this seed in this soil (the soil is ignored hydroponically, and a hydroponic-only recipe
    // needs the Hydroponic Cell).
    public boolean grows(ItemStack seed, ItemStack soil, boolean hydroponic) {
        if (!this.seed.test(seed)) {
            return false;
        }
        if (hydroponic) {
            return true;
        }
        return !hydroponicOnly && (this.soil.isEmpty() || !soil.isEmpty() && this.soil.get().test(soil));
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return seed.test(input.item());
    }

    // The harvest is rolled by the farm (see ClochePlants.harvest).
    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    // Machine recipes stay out of the recipe book.
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<ClocheRecipe> getSerializer() {
        return ModRecipes.CLOCHE_SERIALIZER.get();
    }

    @Override
    public RecipeType<ClocheRecipe> getType() {
        return ModRecipes.CLOCHE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.MACHINE_CATEGORY.get();
    }
}
