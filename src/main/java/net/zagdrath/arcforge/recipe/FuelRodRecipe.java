/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModRecipes;

// A Fuel Rod: a shaped recipe (a Zircaloy Tube and Fuel Pellets) whose result is a fresh rod at the pellets' enrichment
// (see FuelRod). Every pellet in the grid must be of the same enrichment, so a rod is never a mix. Shown in JEI as the
// shaped recipe it is.
public class FuelRodRecipe extends ShapedRecipe {
    private record Json(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern.Data pattern,
            ItemStackTemplate result) {}

    private static final MapCodec<Json> JSON_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(Json::commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(Json::bookInfo),
            ShapedRecipePattern.Data.MAP_CODEC.forGetter(Json::pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(Json::result))
            .apply(i, Json::new));

    public static final MapCodec<FuelRodRecipe> MAP_CODEC = JSON_CODEC.flatXmap(json -> DataResult.success(new FuelRodRecipe(json.commonInfo(),
            json.bookInfo(), ShapedRecipePattern.of(json.pattern().key(), json.pattern().pattern()), json.result(), json)),
            recipe -> recipe.json != null ? DataResult.success(recipe.json) : DataResult.error(() -> "Cannot encode a recipe received from the network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelRodRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            (commonInfo, bookInfo, pattern, result) -> new FuelRodRecipe(commonInfo, bookInfo, pattern, result, null));

    private final Recipe.CommonInfo commonInfo;
    private final CraftingRecipe.CraftingBookInfo bookInfo;
    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;
    private final Json json;

    private FuelRodRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result,
            Json json) {
        super(commonInfo, bookInfo, pattern, result);
        this.commonInfo = commonInfo;
        this.bookInfo = bookInfo;
        this.pattern = pattern;
        this.result = result;
        this.json = json;
    }

    // The enrichment every assay-carrying item in the grid shares, or -1 if they differ (0 if there are none).
    private static int sharedAssay(CraftingInput input) {
        int shared = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty() || !stack.has(ModDataComponents.U235_ASSAY.get()) && !isPellet(stack)) {
                continue;
            }
            int assay = stack.getOrDefault(ModDataComponents.U235_ASSAY.get(), Enrichment.NATURAL_PCM);
            if (shared != 0 && assay != shared) {
                return -1;
            }
            shared = assay;
        }
        return shared;
    }

    private static boolean isPellet(ItemStack stack) {
        return stack.is(net.zagdrath.arcforge.registry.ModItems.FUEL_PELLET.get());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && sharedAssay(input) >= 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack rod = super.assemble(input);
        int assay = sharedAssay(input);
        rod.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fresh(assay > 0 ? assay : Enrichment.NATURAL_PCM));
        return rod;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipes.FUEL_ROD_SERIALIZER.get();
    }
}
