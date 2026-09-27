/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.zagdrath.arcforge.registry.ModRecipes;

// A shaped recipe that upgrades something to the next tier without losing what it holds: the result gets
// every data component of the ingredient under the key named by "copy_components_from" (the previous
// tier: its stored FE, fluid, gas or heat, and anything else it carries). Used for Energy Cells, Fluid
// Tanks, Heat Cells, Pressurized Cylinders and the portable storage items.
public class TierUpgradeRecipe extends ShapedRecipe {
    // The JSON: a shaped recipe's fields plus the key to copy from.
    private record Json(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern.Data pattern,
            ItemStackTemplate result, String copyFrom) {}

    private static final MapCodec<Json> JSON_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(Json::commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(Json::bookInfo),
            ShapedRecipePattern.Data.MAP_CODEC.forGetter(Json::pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(Json::result),
            Codec.STRING.fieldOf("copy_components_from").forGetter(Json::copyFrom))
            .apply(i, Json::new));

    public static final MapCodec<TierUpgradeRecipe> MAP_CODEC = JSON_CODEC.flatXmap(TierUpgradeRecipe::fromJson,
            recipe -> recipe.json != null ? DataResult.success(recipe.json) : DataResult.error(() -> "Cannot encode a recipe received from the network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.copyFrom,
            (commonInfo, bookInfo, pattern, result, copyFrom) -> new TierUpgradeRecipe(commonInfo, bookInfo, pattern, result, copyFrom, null));

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;
    private final Ingredient copyFrom;
    // What it was read from, kept to write it back out (null for recipes received from the server).
    private final Json json;

    private TierUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
            ItemStackTemplate result, Ingredient copyFrom, Json json) {
        super(commonInfo, bookInfo, pattern, result);
        this.pattern = pattern;
        this.result = result;
        this.copyFrom = copyFrom;
        this.json = json;
    }

    private static DataResult<TierUpgradeRecipe> fromJson(Json json) {
        if (json.copyFrom().length() != 1) {
            return DataResult.error(() -> "copy_components_from must be a single key, not \"" + json.copyFrom() + "\"");
        }
        Ingredient copyFrom = json.pattern().key().get(json.copyFrom().charAt(0));
        if (copyFrom == null) {
            return DataResult.error(() -> "copy_components_from names key '" + json.copyFrom() + "', which the recipe doesn't have");
        }
        return DataResult.success(new TierUpgradeRecipe(json.commonInfo(), json.bookInfo(),
                ShapedRecipePattern.of(json.pattern().key(), json.pattern().pattern()), json.result(), copyFrom, json));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack upgraded = super.assemble(input);
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && copyFrom.test(stack)) {
                upgraded.applyComponents(stack.getComponentsPatch());
                break;
            }
        }
        return upgraded;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipes.TIER_UPGRADE_SERIALIZER.get();
    }
}
