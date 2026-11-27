/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Smithing Table upgrade to Arcforged gear: the Arcforged Upgrade Template, the piece to upgrade (a netherite tool or
// armour piece, or the Steel Hammer or Excavator) and "addition_count" of the addition (Arcforged Alloy: one, or two for
// the Hammer and Excavator). The result keeps everything the base carried (enchantments, trim, name, its charge) as
// vanilla's smithing_transform does, and the same share of its durability used, rescaled to the Arcforged maximum.
// The template is used up. JSON: {"type": "arcforge:arcforged_upgrade", "template": ..., "base": ..., "addition": ...,
// "addition_count": 2, "result": {"id": ...}}.
public class ArcforgedUpgradeRecipe extends SimpleSmithingRecipe {
    public static final MapCodec<ArcforgedUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
            Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
            Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
            Codec.intRange(1, 64).optionalFieldOf("addition_count", 1).forGetter(recipe -> recipe.additionCount),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
            .apply(i, ArcforgedUpgradeRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArcforgedUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
            ByteBufCodecs.VAR_INT, recipe -> recipe.additionCount,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            ArcforgedUpgradeRecipe::new);

    // The smithing menu's slots: template, base, addition.
    private static final int TEMPLATE_SLOT = 0, BASE_SLOT = 1, ADDITION_SLOT = 2;

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final int additionCount;
    private final ItemStackTemplate result;

    public ArcforgedUpgradeRecipe(Recipe.CommonInfo commonInfo, Ingredient template, Ingredient base, Ingredient addition, int additionCount,
            ItemStackTemplate result) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.additionCount = additionCount;
        this.result = result;
    }

    public int additionCount() {
        return additionCount;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition())
                && input.addition().getCount() >= additionCount;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        return upgrade(input.base(), result.create());
    }

    // `into` with everything `from` carried, and the same share of its durability used.
    public static ItemStack upgrade(ItemStack from, ItemStack into) {
        ItemStack upgraded = into.copyWithCount(1);
        upgraded.applyComponents(from.getComponentsPatch());
        if (from.isDamageableItem() && upgraded.isDamageableItem()) {
            double share = (double) from.getDamageValue() / Math.max(1, from.getMaxDamage());
            upgraded.setDamageValue(Mth.clamp((int) Math.round(share * upgraded.getMaxDamage()), 0, upgraded.getMaxDamage() - 1));
        }
        return upgraded;
    }

    // The smithing menu takes one of each input; an upgrade that needs more of its addition (the Hammer and Excavator,
    // two Arcforged Alloy) takes the rest here, as the result is taken (ArcforgedAreaToolItem.onCraftedBy, which the
    // menu calls before it takes its own one). On the server only; the client's menu is sent the result.
    public static void takeExtraAdditions(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !(player.containerMenu instanceof SmithingMenu menu)) {
            return;
        }
        SmithingRecipeInput input = new SmithingRecipeInput(menu.getSlot(TEMPLATE_SLOT).getItem(), menu.getSlot(BASE_SLOT).getItem(),
                menu.getSlot(ADDITION_SLOT).getItem());
        int extra = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level)
                .map(holder -> holder.value() instanceof ArcforgedUpgradeRecipe upgrade ? upgrade.additionCount() - 1 : 0).orElse(0);
        Slot additionSlot = menu.getSlot(ADDITION_SLOT);
        ItemStack additions = additionSlot.getItem();
        // Leave the menu's own one.
        int take = Math.min(extra, additions.getCount() - 1);
        if (take > 0) {
            additions.shrink(take);
            additionSlot.setChanged();
        }
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
    public RecipeSerializer<ArcforgedUpgradeRecipe> getSerializer() {
        return ModRecipes.ARCFORGED_UPGRADE_SERIALIZER.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(Optional.of(template), Optional.of(base), Optional.of(addition)));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(template.display(), base.display(), addition.display(),
                new SlotDisplay.ItemSlotDisplay(result.create().getItem()), new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}
