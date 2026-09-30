/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.farming.LoamGrowth;
import net.zagdrath.arcforge.recipe.FermentingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The crops: Flax, Rapeseed and Sorghum plant on vanilla and Loam farmland, the tall ones need room, they use
// nutrients and drop their harvest; Hops climb a Trellis (2 high at most), bear cones and regrow after picking; and
// the crop recipes, the Fermenter's sorghum, and the wild plants' worldgen.
public final class CropGameTests {
    private CropGameTests() {}

    private static void use(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false)));
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> drops(GameTestHelper helper, BlockPos pos, BlockState state) {
        return Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null);
    }

    // Each seed plants its crop on vanilla farmland and on Loam Farmland.
    static void seedsPlant(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<Item> seeds = List.of(ModItems.FLAX_SEEDS.get(), ModItems.RAPESEEDS.get(), ModItems.SORGHUM_SEEDS.get());
        List<Block> crops = List.of(ModBlocks.FLAX.get(), ModBlocks.RAPESEED.get(), ModBlocks.SORGHUM.get());
        for (int i = 0; i < seeds.size(); i++) {
            BlockPos vanilla = new BlockPos(i * 2, 1, 0), loam = new BlockPos(i * 2, 1, 2);
            helper.setBlock(vanilla, Blocks.FARMLAND);
            helper.setBlock(loam, ModBlocks.LOAM_FARMLAND.get());
            use(helper, player, new ItemStack(seeds.get(i)), vanilla);
            use(helper, player, new ItemStack(seeds.get(i)), loam);
            helper.assertBlockPresent(crops.get(i), vanilla.above());
            helper.assertBlockPresent(crops.get(i), loam.above());
        }
        helper.succeed();
    }

    // Rapeseed and Sorghum grow into their two-block stages (age 4 up) only with air above; flax from age 2.
    static void tallCropsNeedRoom(GameTestHelper helper) {
        BlockPos rape = new BlockPos(0, 2, 0), flax = new BlockPos(2, 2, 0), sorghum = new BlockPos(4, 2, 0);
        for (BlockPos pos : List.of(rape, flax, sorghum)) {
            helper.setBlock(pos.below(), Blocks.FARMLAND);
            helper.setBlock(pos.above(), Blocks.STONE);
        }
        helper.setBlock(rape, ModBlocks.RAPESEED.get().getStateForAge(3));
        helper.setBlock(flax, ModBlocks.FLAX.get().getStateForAge(1));
        helper.setBlock(sorghum, ModBlocks.SORGHUM.get().getStateForAge(3));
        var level = helper.getLevel();
        helper.assertTrue(!ModBlocks.RAPESEED.get().hasRoomToGrow(level, helper.absolutePos(rape), helper.getBlockState(rape)), "Rapeseed grows into stone");
        helper.assertTrue(!ModBlocks.FLAX.get().hasRoomToGrow(level, helper.absolutePos(flax), helper.getBlockState(flax)), "Flax grows into stone");
        helper.assertTrue(!ModBlocks.SORGHUM.get().hasRoomToGrow(level, helper.absolutePos(sorghum), helper.getBlockState(sorghum)), "Sorghum grows into stone");
        helper.setBlock(rape.above(), Blocks.AIR);
        helper.assertTrue(ModBlocks.RAPESEED.get().hasRoomToGrow(level, helper.absolutePos(rape), helper.getBlockState(rape)), "Rapeseed has no room under air");
        helper.succeed();
    }

    // A crop's growth on fed Loam Farmland uses its nutrients.
    static void cropsUseNutrients(GameTestHelper helper) {
        BlockPos soil = new BlockPos(1, 1, 1);
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, 6));
        helper.setBlock(soil.above(), ModBlocks.SORGHUM.get());
        int stages = LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(soil.above()), RandomSource.create(3));
        helper.assertTrue(stages >= 1 && LoamFarmlandBlock.nutrients(helper.getBlockState(soil)) == 6 - stages, "Sorghum didn't use nutrients");
        helper.succeed();
    }

    // Grown crops give their harvest; young ones only their seed.
    static void harvests(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        List<ItemStack> flax = drops(helper, pos, ModBlocks.FLAX.get().getStateForAge(7));
        helper.assertTrue(count(flax, ModItems.FLAX_FIBRE.get()) >= 1 && count(flax, ModItems.FLAX_SEEDS.get()) >= 1, "Grown flax drops " + flax);
        List<ItemStack> young = drops(helper, pos, ModBlocks.FLAX.get().getStateForAge(3));
        helper.assertTrue(count(young, ModItems.FLAX_FIBRE.get()) == 0 && count(young, ModItems.FLAX_SEEDS.get()) == 1, "Young flax drops " + young);
        helper.assertTrue(count(drops(helper, pos, ModBlocks.RAPESEED.get().getStateForAge(7)), ModItems.RAPESEEDS.get()) >= 2, "Grown rapeseed drops too few");
        helper.assertTrue(count(drops(helper, pos, ModBlocks.SORGHUM.get().getStateForAge(7)), ModItems.SORGHUM_STALKS.get()) >= 1, "Grown sorghum drops no stalks");
        helper.succeed();
    }

    // Trellises stack two high on farmland, not three.
    static void trellisStacksTwo(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 1);
        helper.setBlock(base, Blocks.FARMLAND);
        BlockState trellis = ModBlocks.TRELLIS.get().defaultBlockState();
        var level = helper.getLevel();
        helper.assertTrue(trellis.canSurvive(level, helper.absolutePos(base.above())), "A trellis can't stand on farmland");
        helper.setBlock(base.above(), trellis);
        helper.assertTrue(helper.getBlockState(base.above()).getValue(TrellisBlock.ON_FARMLAND), "A trellis on farmland has no feet");
        helper.assertTrue(trellis.canSurvive(level, helper.absolutePos(base.above(2))), "A second trellis can't stack");
        helper.setBlock(base.above(2), trellis);
        helper.assertTrue(!helper.getBlockState(base.above(2)).getValue(TrellisBlock.ON_FARMLAND), "A trellis on a trellis has feet");
        helper.assertTrue(!trellis.canSurvive(level, helper.absolutePos(base.above(3))), "A third trellis stacks");
        helper.assertBlockPresent(Blocks.FARMLAND, base);
        helper.succeed();
    }

    // Hop Seeds plant in a bottom trellis on farmland (not on stone); the vine grows, climbs into the trellis above, bears
    // cones, and after picking stays at a full vine.
    static void hopsGrowAndRegrow(GameTestHelper helper) {
        BlockPos soil = new BlockPos(1, 1, 1), bottom = soil.above(), top = soil.above(2), stone = new BlockPos(3, 1, 1);
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get());
        helper.setBlock(bottom, ModBlocks.TRELLIS.get());
        helper.setBlock(top, ModBlocks.TRELLIS.get());
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(stone.above(), ModBlocks.TRELLIS.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack seeds = new ItemStack(ModItems.HOP_SEEDS.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
        var level = helper.getLevel();
        BlockHitResult onStone = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(stone.above())), Direction.NORTH, helper.absolutePos(stone.above()), false);
        helper.getBlockState(stone.above()).useItemOn(seeds, level, player, InteractionHand.MAIN_HAND, onStone);
        helper.assertTrue(helper.getBlockState(stone.above()).getValue(TrellisBlock.AGE) == TrellisBlock.BARE, "Hops planted on stone");
        BlockHitResult onBottom = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(bottom)), Direction.NORTH, helper.absolutePos(bottom), false);
        helper.getBlockState(bottom).useItemOn(seeds, level, player, InteractionHand.MAIN_HAND, onBottom);
        helper.assertTrue(helper.getBlockState(bottom).getValue(TrellisBlock.AGE) == TrellisBlock.SHOOT && seeds.getCount() == 1, "Hops didn't plant");
        TrellisBlock block = ModBlocks.TRELLIS.get();
        for (int i = 0; i < 3; i++) {
            block.performBonemeal(level, level.getRandom(), helper.absolutePos(bottom), helper.getBlockState(bottom), net.minecraft.world.level.block.BonemealSource.INTERACTION);
        }
        helper.assertTrue(helper.getBlockState(bottom).getValue(TrellisBlock.AGE) == TrellisBlock.BEARING, "The bottom vine isn't bearing");
        helper.assertTrue(helper.getBlockState(top).getValue(TrellisBlock.AGE) >= TrellisBlock.SHOOT, "The vine didn't climb");
        int picked = TrellisBlock.harvest(level, helper.absolutePos(bottom), helper.getBlockState(bottom));
        helper.assertTrue(picked >= 1, "No cones picked");
        helper.assertTrue(helper.getBlockState(bottom).getValue(TrellisBlock.AGE) == TrellisBlock.FULL_VINE, "The vine didn't stay after picking");
        helper.succeed();
    }

    // Fibre makes String and Linen; the Fermenter gets more Ethanol from Sorghum Stalks (or Dried Sorghum) than from
    // anything else.
    static void recipes(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().recipeMap();
        RecipeHolder<?> linen = recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/linen")));
        helper.assertTrue(linen != null && linen.value() instanceof CraftingRecipe, "No Linen recipe");
        ItemStack fibre = new ItemStack(ModItems.FLAX_FIBRE.get());
        CraftingInput grid = CraftingInput.of(2, 2, List.of(fibre, fibre, fibre, fibre));
        helper.assertTrue(((CraftingRecipe) linen.value()).assemble(grid).is(ModItems.LINEN.get()), "Four fibre don't make Linen");
        RecipeHolder<?> string = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/string_from_flax_fibre")));
        helper.assertTrue(string != null && ((CraftingRecipe) string.value()).assemble(CraftingInput.of(1, 1, List.of(fibre))).is(Items.STRING),
                "Fibre doesn't make String");
        int sorghum = 0, best = 0;
        for (RecipeHolder<?> holder : recipes.values()) {
            if (holder.value() instanceof FermentingRecipe fermenting) {
                int amount = fermenting.result().amount();
                if (fermenting.ingredient().test(new ItemStack(ModItems.SORGHUM_STALKS.get()))) {
                    sorghum = amount;
                } else if (!fermenting.ingredient().test(new ItemStack(ModItems.DRIED_SORGHUM.get()))) {
                    best = Math.max(best, amount);
                }
            }
        }
        helper.assertTrue(sorghum > best, "Sorghum makes " + sorghum + " mB, not more than " + best);
        helper.succeed();
    }

    // The wild plants' placed features are registered.
    static void wildPlantsGenerate(GameTestHelper helper) {
        var features = helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (String name : List.of("wild_flax", "wild_rapeseed", "wild_sorghum", "wild_hops")) {
            helper.assertTrue(features.get(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, name))).isPresent(),
                    "No placed feature " + name);
        }
        BlockPos grass = new BlockPos(1, 1, 1);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        for (Block wild : List.of(ModBlocks.WILD_FLAX.get(), ModBlocks.WILD_RAPESEED.get(), ModBlocks.WILD_SORGHUM.get(), ModBlocks.WILD_HOPS.get())) {
            helper.assertTrue(wild.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(grass.above())), wild + " can't grow on grass");
        }
        helper.succeed();
    }
}
