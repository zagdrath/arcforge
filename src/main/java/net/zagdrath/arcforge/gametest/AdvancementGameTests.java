/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModTriggers;

// The Arcforge advancement tab (every advancement loads, forming a structure and a long turbine count) and the Wrench's
// early recipe.
public final class AdvancementGameTests {
    private AdvancementGameTests() {}

    static final List<String> IDS = List.of("root", "start/wrench", "start/first_power", "start/energy_cell", "steel/carbonizer", "steel/coal_coke",
            "steel/arcforge_furnace", "steel/steel_ingot", "processing/dust", "processing/array", "processing/leached_dust", "steam/boiler",
            "steam/superheated_steam", "steam/condenser", "chemistry/naphtha", "chemistry/electrolyzer", "chemistry/plastic",
            "chemistry/thermal_evaporator", "chemistry/brine", "chemistry/chlorine", "chemistry/hydrochloric_acid", "gear/steel_tools",
            "gear/jetpack", "gear/arcforged_arc_drill", "challenge/nine_stage_spin", "challenge/full_throttle", "challenge/white_heat",
            "challenge/array_of_options", "farming/compost_bin", "farming/compost", "farming/loam_farmland", "farming/fertilize",
            "farming/mixed_fertilizer", "farming/irrigated", "farming/new_crop", "farming/linen", "farming/hop_harvest", "farming/every_crop",
            "farming/hands_free", "farming/sprinkler", "farming/scythe",
            "farming/millstone", "farming/mill", "farming/seed_oil", "farming/dried_hops",
            "farming/air_separator", "farming/ammonia", "farming/npk_fertilizer", "farming/biodiesel", "farming/biogas_digester",
            "farming/glass_cloche", "farming/grow_chamber", "farming/hydroponic_cell",
            "farming/greenhouse", "farming/greenhouse_night_shift", "farming/greenhouse_fully_fed", "farming/soybeans", "farming/crop_rotation", "processing/rock_salt",
            "farming/rubber_dandelion", "farming/resin_tap", "farming/raw_rubber", "chemistry/rubber", "chemistry/ethylene", "chemistry/pvc",
            "logistics/reservoir", "logistics/liquid_experience", "logistics/quantum_tunnel", "logistics/chunk_loader");

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos standRelative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(standRelative);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    private static AdvancementHolder advancement(GameTestHelper helper, String path) {
        return helper.getLevel().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(Arcforge.MODID, path));
    }

    private static boolean done(ServerPlayer player, AdvancementHolder advancement) {
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    // All 52 advancements of the tab load.
    static void treeLoads(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (String id : IDS) {
            if (advancement(helper, id) == null) {
                missing.add(id);
            }
        }
        helper.assertTrue(missing.isEmpty(), "Advancements that didn't load: " + missing);
        helper.succeed();
    }

    // Forming an Arc Crushing Array next to a player gives them "processing/array".
    static void multiblockFormedTrigger(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(4, 1, 1));
        AdvancementHolder array = advancement(helper, "processing/array");
        helper.assertTrue(array != null && !done(player, array), "The player starts with the array advancement");
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(done(player, array), "Forming an Arc Crushing Array didn't count"))
                .thenSucceed();
    }

    // "Nine-stage spin" wants a Steam Turbine Array at least 9 long: a 3-long one at full speed doesn't count, a 9-long does.
    static void turbineFullSpeedNeedsLength(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        AdvancementHolder spin = advancement(helper, "challenge/nine_stage_spin");
        Identifier turbine = Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine_array");
        ModTriggers.TURBINE_FULL_SPEED.get().trigger(player, turbine, 3, 15);
        helper.assertFalse(done(player, spin), "A 3-long turbine counted for nine_stage_spin");
        ModTriggers.TURBINE_FULL_SPEED.get().trigger(player, turbine, 9, 15);
        helper.assertTrue(done(player, spin), "A 9-long turbine didn't count for nine_stage_spin");
        helper.succeed();
    }

    // The Wrench is crafted from three iron ingots and a copper ingot (no steel), so it comes before the Carbonizer.
    static void wrenchRecipeIsEarly(GameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/wrench")));
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, "No Wrench recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        ItemStack iron = new ItemStack(Items.IRON_INGOT), copper = new ItemStack(Items.COPPER_INGOT);
        CraftingInput grid = CraftingInput.of(3, 3, List.of(iron, ItemStack.EMPTY, iron, ItemStack.EMPTY, copper, ItemStack.EMPTY,
                ItemStack.EMPTY, iron, ItemStack.EMPTY));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Iron and copper don't make a Wrench");
        helper.assertTrue(recipe.assemble(grid).is(ModItems.WRENCH.get()), "The recipe doesn't make a Wrench");
        ItemStack steel = new ItemStack(ModItems.STEEL_INGOT.get());
        CraftingInput steelGrid = CraftingInput.of(3, 3, List.of(steel, ItemStack.EMPTY, steel, ItemStack.EMPTY, copper, ItemStack.EMPTY,
                ItemStack.EMPTY, steel, ItemStack.EMPTY));
        helper.assertFalse(recipe.matches(steelGrid, helper.getLevel()), "The Wrench recipe takes steel");
        helper.succeed();
    }
}
