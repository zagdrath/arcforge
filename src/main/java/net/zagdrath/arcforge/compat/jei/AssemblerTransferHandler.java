/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;
import net.zagdrath.arcforge.network.AssemblerPatternPayload;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// JEI's + on a crafting recipe with an Assembler open: sets its pattern to the recipe's shown ingredients, as
// ghosts. No real items move, so it always works (there's nothing to be missing).
public final class AssemblerTransferHandler implements IRecipeTransferHandler<AssemblerMenu, RecipeHolder<CraftingRecipe>> {
    @Override
    public Class<? extends AssemblerMenu> getContainerClass() {
        return AssemblerMenu.class;
    }

    @Override
    public Optional<MenuType<AssemblerMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.ASSEMBLER.get());
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(AssemblerMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            ClientPacketDistributor.sendToServer(new AssemblerPatternPayload(menu.containerId, cells(slots)));
        }
        return null;
    }

    // The first shown ingredient of each input slot, in grid order (JEI lays crafting recipes out on a 3x3 grid;
    // shapeless ones fill it in order).
    static List<ItemStack> cells(IRecipeSlotsView slots) {
        List<ItemStack> cells = new ArrayList<>(AssemblerBlockEntity.PATTERN_SIZE);
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            if (cells.size() >= AssemblerBlockEntity.PATTERN_SIZE) {
                break;
            }
            cells.add(slot.getDisplayedItemStack().map(stack -> stack.copyWithCount(1)).orElse(ItemStack.EMPTY));
        }
        while (cells.size() < AssemblerBlockEntity.PATTERN_SIZE) {
            cells.add(ItemStack.EMPTY);
        }
        return cells;
    }
}
