/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The PUREX Plant's: a spent rod chopped, dissolved in an acid and separated with a solvent. What comes out depends on
// the rod (see PurexBlockEntity): its fuel left as heavy_metal (Recovered Uranium carrying the rod's remaining
// assay, or Thorium Dioxide), its bred fissile (Plutonium Oxide, or U-233 Oxide) as fissile: fissile_per_fraction items
// for each whole of the rod's fissile fraction (left out: nuclear.reprocessing.plutoniumPerFraction, or
// uranium233PerFraction in THOREX), and waste: High-Level Liquid Waste, the amount for a fully spent rod (a fresher one
// leaves less). mode is the plant's setting it needs: purex (uranium and MOX fuel) or thorex (thorium).
public record ReprocessingRecipe(Mode mode, Ingredient ingredient, ChemicalReactingRecipe.FluidInput acid, ChemicalReactingRecipe.FluidInput solvent,
        ItemStackTemplate heavyMetal, ItemStackTemplate fissile, Optional<Double> fissilePerFraction, FluidStackTemplate waste, int time)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 400;

    public enum Mode implements StringRepresentable {
        PUREX("purex"), THOREX("thorex");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<ReprocessingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(ReprocessingRecipe::mode),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(ReprocessingRecipe::ingredient),
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("acid").forGetter(ReprocessingRecipe::acid),
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("solvent").forGetter(ReprocessingRecipe::solvent),
            ItemStackTemplate.CODEC.fieldOf("heavy_metal").forGetter(ReprocessingRecipe::heavyMetal),
            ItemStackTemplate.CODEC.fieldOf("fissile").forGetter(ReprocessingRecipe::fissile),
            Codec.DOUBLE.optionalFieldOf("fissile_per_fraction").forGetter(ReprocessingRecipe::fissilePerFraction),
            FluidStackTemplate.CODEC.fieldOf("waste").forGetter(ReprocessingRecipe::waste),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ReprocessingRecipe::time))
            .apply(i, ReprocessingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReprocessingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                buf.writeEnum(recipe.mode);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient);
                ChemicalReactingRecipe.FluidInput.STREAM_CODEC.encode(buf, recipe.acid);
                ChemicalReactingRecipe.FluidInput.STREAM_CODEC.encode(buf, recipe.solvent);
                ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.heavyMetal);
                ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.fissile);
                ByteBufCodecs.optional(ByteBufCodecs.DOUBLE).encode(buf, recipe.fissilePerFraction);
                FluidStackTemplate.STREAM_CODEC.encode(buf, recipe.waste);
                buf.writeVarInt(recipe.time);
            },
            buf -> new ReprocessingRecipe(buf.readEnum(Mode.class), Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                    ChemicalReactingRecipe.FluidInput.STREAM_CODEC.decode(buf), ChemicalReactingRecipe.FluidInput.STREAM_CODEC.decode(buf),
                    ItemStackTemplate.STREAM_CODEC.decode(buf), ItemStackTemplate.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.optional(ByteBufCodecs.DOUBLE).decode(buf), FluidStackTemplate.STREAM_CODEC.decode(buf), buf.readVarInt()));

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    // The fissile items for each whole fraction of fissile in a rod.
    public double perFraction() {
        return fissilePerFraction.orElseGet(() -> mode == Mode.THOREX ? net.zagdrath.arcforge.config.ArcforgeConfig.THOREX_URANIUM_233.getAsDouble()
                : net.zagdrath.arcforge.config.ArcforgeConfig.PUREX_PLUTONIUM.getAsDouble());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return heavyMetal.create();
    }

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
    public RecipeSerializer<ReprocessingRecipe> getSerializer() {
        return ModRecipes.REPROCESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<ReprocessingRecipe> getType() {
        return ModRecipes.REPROCESSING.get();
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
