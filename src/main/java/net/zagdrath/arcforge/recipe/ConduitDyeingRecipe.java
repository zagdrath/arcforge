/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.zagdrath.arcforge.item.conduit.ConduitBlockItem;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;

// Sheathing conduits in coloured plastic (arcforge:conduit_dyeing): 1-8 of the same conduit, one Plastic Sheet and one
// dye make that many of the dye's colour (already-sheathed ones can be re-dyed). 1-8 sheathed conduits of the same
// kind alone strip the sheath off (the plastic is lost).
public class ConduitDyeingRecipe extends CustomRecipe {
    public static final ConduitDyeingRecipe INSTANCE = new ConduitDyeingRecipe();
    public static final MapCodec<ConduitDyeingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConduitDyeingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    private static final int MAX_CONDUITS = 8;

    // What the grid holds: the conduits (one item, how many), the dye and plastic seen, or null if it doesn't fit.
    private record Parts(ItemStack conduit, int count, @Nullable DyeColor dye, int plastic) {}

    private static @Nullable Parts parts(CraftingInput input) {
        ItemStack conduit = ItemStack.EMPTY;
        int count = 0;
        DyeColor dye = null;
        int dyes = 0;
        int plastic = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ConduitBlockItem) {
                if (!conduit.isEmpty() && !stack.is(conduit.getItem())) {
                    return null;
                }
                conduit = stack;
                count++;
            } else if (stack.is(ModItems.PLASTIC_SHEET.get())) {
                plastic++;
            } else if (stack.is(Tags.Items.DYES) && stack.has(DataComponents.DYE)) {
                dye = stack.get(DataComponents.DYE);
                dyes++;
            } else {
                return null;
            }
        }
        if (count == 0 || count > MAX_CONDUITS || dyes > 1 || plastic > 1) {
            return null;
        }
        return new Parts(conduit, count, dyes == 1 ? dye : null, plastic);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Parts parts = parts(input);
        if (parts == null) {
            return false;
        }
        boolean dyeing = parts.dye() != null && parts.plastic() == 1;
        boolean stripping = parts.dye() == null && parts.plastic() == 0 && sheathed(input);
        return dyeing || stripping;
    }

    // Stripping needs every conduit in the grid to be sheathed.
    private static boolean sheathed(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof ConduitBlockItem && !stack.has(ModDataComponents.CONDUIT_COLOR.get())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Parts parts = parts(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = parts.conduit().copyWithCount(parts.count());
        if (parts.dye() != null) {
            result.set(ModDataComponents.CONDUIT_COLOR.get(), parts.dye());
        } else {
            result.remove(ModDataComponents.CONDUIT_COLOR.get());
        }
        return result;
    }

    @Override
    public RecipeSerializer<ConduitDyeingRecipe> getSerializer() {
        return ModRecipes.CONDUIT_DYEING_SERIALIZER.get();
    }
}
