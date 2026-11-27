/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Lithium Isotope Separator's (arcforge:lithium_enriching): input lithium fluoride (an ingredient and how many a
// pass takes) raised one enrichment step (fusion.lithium.enrichmentStep) a pass, coming out as intermediate (Enriched
// Lithium Fluoride) until it reaches fusion.lithium.enrichedMinimum, then as result (Lithium-6 Fluoride); each carries
// its assay (see Lithium6). time in ticks, energy_per_tick FE/t (fusion.lithium.separatorEnergy if unset).
public record LithiumEnrichingRecipe(ChemicalReactingRecipe.ItemInput input, Item intermediate, Item result, int time, java.util.Optional<Integer> energyPerTick)
        implements Recipe<SingleRecipeInput> {
    public static final MapCodec<LithiumEnrichingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.ItemInput.CODEC.fieldOf("input").forGetter(LithiumEnrichingRecipe::input),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("intermediate").forGetter(LithiumEnrichingRecipe::intermediate),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(LithiumEnrichingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", 200).forGetter(LithiumEnrichingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(LithiumEnrichingRecipe::energyPerTick))
            .apply(i, LithiumEnrichingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LithiumEnrichingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC, LithiumEnrichingRecipe::input,
            ByteBufCodecs.registry(Registries.ITEM), LithiumEnrichingRecipe::intermediate,
            ByteBufCodecs.registry(Registries.ITEM), LithiumEnrichingRecipe::result,
            ByteBufCodecs.VAR_INT, LithiumEnrichingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), LithiumEnrichingRecipe::energyPerTick,
            LithiumEnrichingRecipe::new);

    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(net.zagdrath.arcforge.config.ArcforgeConfig.LITHIUM_SEPARATOR_ENERGY::getAsInt);
    }

    // What a pass makes from a feed of this assay (tenths of a percent).
    public ItemStack product(int assay) {
        int next = Math.min(net.zagdrath.arcforge.fusion.Lithium6.max(), assay + net.zagdrath.arcforge.fusion.Lithium6.step());
        ItemStack out = new ItemStack(next >= net.zagdrath.arcforge.fusion.Lithium6.minimum() ? result : intermediate);
        out.set(net.zagdrath.arcforge.registry.ModDataComponents.LI6_ASSAY.get(), next);
        return out;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.ingredient().test(input.item()) && net.zagdrath.arcforge.fusion.Lithium6.assay(input.item()) < net.zagdrath.arcforge.fusion.Lithium6.max();
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return product(net.zagdrath.arcforge.fusion.Lithium6.assay(input.item()));
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
    public RecipeSerializer<LithiumEnrichingRecipe> getSerializer() {
        return ModRecipes.LITHIUM_ENRICHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<LithiumEnrichingRecipe> getType() {
        return ModRecipes.LITHIUM_ENRICHING.get();
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
