/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.SteamGrade;

// Distillation Array: a batch of one fluid (1,000 mB of creosote) split into fractions by the column's
// height. Each batch needs `heat` HU in all and the column at least min_temp °C. by_height lists what each
// height makes: the fluids, and how many of item_output (pitch). Optional steam stripping: each batch
// takes steam_per_batch mB of steam if there is that much, and raises the `boosts` fraction by the steam
// grade's bonus, taking the extra out of the `taken_from` fraction where the column makes it.
public record DistillingRecipe(Fluid input, int amount, int heat, int minTemp, Map<Integer, Fractions> byHeight, Item itemOutput,
        Optional<SteamStripping> steamStripping) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_MIN_TEMP = 350;

    private static final Codec<Map<Fluid, Integer>> FLUID_AMOUNTS = Codec.unboundedMap(BuiltInRegistries.FLUID.byNameCodec(), ExtraCodecs.POSITIVE_INT);

    // What a column of one height makes from a batch.
    public record Fractions(Map<Fluid, Integer> fluids, int items) {
        public static final Codec<Fractions> CODEC = RecordCodecBuilder.create(i -> i.group(
                FLUID_AMOUNTS.optionalFieldOf("fluids", Map.of()).forGetter(Fractions::fluids),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("pitch", 0).forGetter(Fractions::items))
                .apply(i, Fractions::new));
    }

    public record SteamStripping(int steamPerBatch, Fluid boosts, Optional<Fluid> takenFrom, Map<Fluid, Float> bonus) {
        public static final Codec<SteamStripping> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("steam_per_batch").forGetter(SteamStripping::steamPerBatch),
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("boosts").forGetter(SteamStripping::boosts),
                BuiltInRegistries.FLUID.byNameCodec().optionalFieldOf("taken_from").forGetter(SteamStripping::takenFrom),
                Codec.unboundedMap(BuiltInRegistries.FLUID.byNameCodec(), Codec.floatRange(0.0F, 10.0F)).fieldOf("bonus").forGetter(SteamStripping::bonus))
                .apply(i, SteamStripping::new));

        // The yield bonus (0.15 = +15%) for this steam, or 0.
        public float bonusFor(FluidResource steam) {
            return steam.isEmpty() ? 0.0F : bonus.getOrDefault(steam.getFluid(), 0.0F);
        }
    }

    private static final Codec<Map<Integer, Fractions>> BY_HEIGHT = Codec.unboundedMap(Codec.STRING.comapFlatMap(key -> {
        try {
            return DataResult.success(Integer.parseInt(key));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a column height: " + key);
        }
    }, String::valueOf), Fractions.CODEC);

    private record Input(Fluid fluid, int amount) {
        static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Input::fluid),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount", 1_000).forGetter(Input::amount))
                .apply(i, Input::new));
    }

    public static final MapCodec<DistillingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Input.CODEC.fieldOf("input").forGetter(recipe -> new Input(recipe.input, recipe.amount)),
            ExtraCodecs.POSITIVE_INT.fieldOf("heat").forGetter(DistillingRecipe::heat),
            Codec.INT.optionalFieldOf("min_temp", DEFAULT_MIN_TEMP).forGetter(DistillingRecipe::minTemp),
            BY_HEIGHT.fieldOf("by_height").forGetter(DistillingRecipe::byHeight),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item_output").forGetter(DistillingRecipe::itemOutput),
            SteamStripping.CODEC.optionalFieldOf("steam_stripping").forGetter(DistillingRecipe::steamStripping))
            .apply(i, (input, heat, minTemp, byHeight, item, stripping) ->
                    new DistillingRecipe(input.fluid(), input.amount(), heat, minTemp, byHeight, item, stripping)));

    public static final StreamCodec<RegistryFriendlyByteBuf, DistillingRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC.codec());

    public boolean usesFluid(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid() == input;
    }

    // What a column this tall makes: the entry for the tallest height it reaches, or null if it's too short.
    public @Nullable Fractions fractions(int height) {
        Fractions best = null;
        int bestHeight = 0;
        for (Map.Entry<Integer, Fractions> entry : byHeight.entrySet()) {
            if (entry.getKey() <= height && entry.getKey() > bestHeight) {
                bestHeight = entry.getKey();
                best = entry.getValue();
            }
        }
        return best;
    }

    // The fluids one batch makes at this height with this steam stripping bonus: the boosted fraction gains
    // bonus x its share, out of the taken-from fraction if the column makes it (a straight bonus if not).
    public Map<Fluid, Integer> outputs(int height, float bonus) {
        Fractions fractions = fractions(height);
        Map<Fluid, Integer> out = new LinkedHashMap<>(fractions != null ? fractions.fluids() : Map.of());
        if (bonus > 0 && steamStripping.isPresent()) {
            SteamStripping stripping = steamStripping.get();
            int base = out.getOrDefault(stripping.boosts(), 0);
            int extra = Math.round(base * bonus);
            Fluid from = stripping.takenFrom().orElse(null);
            if (from != null && out.containsKey(from)) {
                extra = Math.min(extra, out.get(from));
                out.put(from, out.get(from) - extra);
            }
            if (extra > 0) {
                out.put(stripping.boosts(), base + extra);
            }
        }
        out.values().removeIf(amount -> amount <= 0);
        return out;
    }

    public int items(int height) {
        Fractions fractions = fractions(height);
        return fractions != null ? fractions.items() : 0;
    }

    // The steam stripping bonus this steam gives, as a fraction (0 without stripping or with no steam).
    public float bonusFor(FluidResource steam) {
        return steamStripping.map(stripping -> stripping.bonusFor(steam)).orElse(0.0F);
    }

    public int steamPerBatch() {
        return steamStripping.map(SteamStripping::steamPerBatch).orElse(0);
    }

    // For display: the bonus for each steam grade, in grade order.
    public float bonusFor(SteamGrade grade) {
        return bonusFor(grade.resource());
    }

    // The column works on fluids, not items: nothing matches through the item input.
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return new ItemStack(itemOutput);
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
    public RecipeSerializer<DistillingRecipe> getSerializer() {
        return ModRecipes.DISTILLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<DistillingRecipe> getType() {
        return ModRecipes.DISTILLING.get();
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
