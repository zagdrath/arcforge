/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.client.screen.GhostSlotScreen;

// Dragging an item, fluid or gas from JEI onto a ghost slot of a GhostSlotScreen (Conduit Filter entries, Arc Quarry
// filter cells, Assembler pattern cells) sets it, as clicking with the item would. Only the slots that take what's
// being dragged are offered, so only they light up.
final class GhostSlotHandler<T extends Screen & GhostSlotScreen> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> item = ingredient.getIngredient(VanillaTypes.ITEM_STACK).filter(stack -> !stack.isEmpty());
        Optional<Fluid> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).filter(stack -> !stack.isEmpty()).map(FluidStack::getFluid);
        List<Target<I>> targets = new ArrayList<>();
        for (int slot = 0; slot < gui.ghostSlotCount(); slot++) {
            int index = slot;
            Rect2i area = gui.ghostSlotArea(slot);
            if (item.isPresent() && gui.acceptsGhostItem(slot, item.get())) {
                targets.add(target(area, () -> gui.setGhostItem(index, item.get())));
            } else if (fluid.isPresent() && gui.acceptsGhostFluid(slot, fluid.get())) {
                targets.add(target(area, () -> gui.setGhostFluid(index, fluid.get())));
            }
        }
        return targets;
    }

    private static <I> Target<I> target(Rect2i area, Runnable set) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ingredient) {
                set.run();
            }
        };
    }

    @Override
    public void onComplete() {}
}
