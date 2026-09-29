/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;

// Smithing a Steel Chestplate onto a Jetpack (with a steel plate as the template) plates it: it keeps everything it
// had, gains the chestplate's armour and toughness, and is worn looking like steel armour (JETPACK_PLATING marks
// it, and the pack still shows over the plate). A plated jetpack can't be plated again.
public class JetpackPlatingRecipe extends SimpleSmithingRecipe {
    public static final MapCodec<JetpackPlatingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
            Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
            Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition))
            .apply(i, JetpackPlatingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackPlatingRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
            JetpackPlatingRecipe::new);

    public static final Identifier ARMOR_ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_plating_armor");
    public static final Identifier TOUGHNESS_ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_plating_toughness");

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;

    public JetpackPlatingRecipe(Recipe.CommonInfo commonInfo, Ingredient template, Ingredient base, Ingredient addition) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return super.matches(input, level) && !input.base().has(ModDataComponents.JETPACK_PLATING.get());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        return plate(input.base(), input.addition());
    }

    // The jetpack with the chestplate's armour and toughness, worn as steel.
    public static ItemStack plate(ItemStack jetpack, ItemStack chestplate) {
        ItemStack plated = jetpack.copyWithCount(1);
        double armor = 0.0;
        double toughness = 0.0;
        for (ItemAttributeModifiers.Entry entry : chestplate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR)) {
                armor += entry.modifier().amount();
            } else if (entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
                toughness += entry.modifier().amount();
            }
        }
        plated.set(ModDataComponents.JETPACK_PLATING.get(), Unit.INSTANCE);
        plated.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, armor, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, toughness, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .build());
        Equippable equippable = plated.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            plated.set(DataComponents.EQUIPPABLE, Equippable.builder(equippable.slot()).setEquipSound(equippable.equipSound())
                    .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "steel"))).build());
        }
        return plated;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(template);
    }

    @Override
    public Ingredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(addition);
    }

    @Override
    public RecipeSerializer<JetpackPlatingRecipe> getSerializer() {
        return ModRecipes.JETPACK_PLATING_SERIALIZER.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(Optional.of(template), Optional.of(base), Optional.of(addition)));
    }

    // Shown with a Tempered Jetpack as the result.
    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(template.display(), base.display(), addition.display(),
                new SlotDisplay.ItemSlotDisplay(ModItems.TEMPERED_JETPACK.get()), new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}
