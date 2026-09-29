/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// Dyed (sheathed) conduits: joining by colour, and the dyeing recipe.
public final class DyedConduitGameTests {
    private DyedConduitGameTests() {}

    private static void conduit(GameTestHelper helper, BlockPos pos, @Nullable DyeColor color) {
        helper.setBlock(pos, ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get());
        helper.getBlockEntity(pos, ConduitBlockEntity.class).setColor(color);
    }

    private static boolean joined(GameTestHelper helper, BlockPos pos, Direction side) {
        return ConduitBlock.mode(helper.getBlockState(pos), side) == ConnectionMode.PIPE;
    }

    // Red–red–blue–plain–blue along x: red joins red, red and blue don't, and a plain conduit joins either. A red
    // row laid on top of a blue one stays apart.
    static void connectByColour(GameTestHelper helper) {
        DyeColor[] row = { DyeColor.RED, DyeColor.RED, DyeColor.BLUE, null, DyeColor.BLUE };
        for (int x = 0; x < row.length; x++) {
            conduit(helper, new BlockPos(x, 1, 1), row[x]);
        }
        for (int x = 0; x < 3; x++) {
            conduit(helper, new BlockPos(x, 1, 4), DyeColor.BLUE);
            conduit(helper, new BlockPos(x, 2, 4), DyeColor.RED);
        }
        for (int x = 0; x < row.length; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 1)));
        }
        for (int x = 0; x < 3; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 4)));
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 2, 4)));
        }
        helper.assertTrue(joined(helper, new BlockPos(0, 1, 1), Direction.EAST), "Red didn't join red");
        helper.assertFalse(joined(helper, new BlockPos(1, 1, 1), Direction.EAST), "Red joined blue");
        helper.assertTrue(joined(helper, new BlockPos(2, 1, 1), Direction.EAST), "Blue didn't join a plain conduit");
        helper.assertTrue(joined(helper, new BlockPos(3, 1, 1), Direction.EAST), "A plain conduit didn't join blue");
        for (int x = 0; x < 3; x++) {
            helper.assertFalse(joined(helper, new BlockPos(x, 1, 4), Direction.UP), "The red and blue rows joined at x " + x);
            helper.assertTrue(x == 2 || joined(helper, new BlockPos(x, 2, 4), Direction.EAST), "The red row broke at x " + x);
        }
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, List<ItemStack> items) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/conduit_dyeing")));
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, "No conduit dyeing recipe");
        List<ItemStack> grid = new ArrayList<>(items);
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        return recipe.matches(input, helper.getLevel()) ? recipe.assemble(input) : ItemStack.EMPTY;
    }

    private static ItemStack redDye() {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye")));
    }

    // Seven conduits (all a crafting grid has room for beside the plastic and dye), a Plastic Sheet and red dye make
    // seven red ones; a red one alone strips back to plain; mixed conduits don't dye.
    static void recipe(GameTestHelper helper) {
        ItemStack conduit = new ItemStack(ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get().asItem());
        List<ItemStack> withDye = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            withDye.add(conduit.copy());
        }
        withDye.add(new ItemStack(ModItems.PLASTIC_SHEET.get()));
        withDye.add(redDye());
        ItemStack red = craft(helper, withDye);
        helper.assertTrue(red.getCount() == 7 && red.get(ModDataComponents.CONDUIT_COLOR.get()) == DyeColor.RED, "Dyeing made " + red);

        ItemStack oneRed = conduit.copy();
        oneRed.set(ModDataComponents.CONDUIT_COLOR.get(), DyeColor.RED);
        ItemStack stripped = craft(helper, List.of(oneRed));
        helper.assertTrue(stripped.getCount() == 1 && !stripped.has(ModDataComponents.CONDUIT_COLOR.get()), "Stripping made " + stripped);

        ItemStack energy = new ItemStack(ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get().asItem());
        ItemStack mixed = craft(helper, List.of(conduit.copy(), energy, new ItemStack(ModItems.PLASTIC_SHEET.get()), redDye()));
        helper.assertTrue(mixed.isEmpty(), "Mixed conduits dyed: " + mixed);
        helper.succeed();
    }
}
