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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.CopperSprinklerBlock;
import net.zagdrath.arcforge.block.farming.FarmMachineBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.ScarecrowBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.FarmMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The rustic farming machines and tools: the Planter plants and the Harvester harvests and replants the 3x3 in front
// (hops included), once per redstone pulse or on their timer; the Fertilizer Spreader tops up only low soil; the Copper
// Sprinkler moistens farmland only with water; a Scarecrow protects within its range; the Sickle harvests a 3x3 and
// wears; and the recipes exist. Machines stand at (1,2,0) facing south, so their area is x 0..2, z 1..3 at y 2 over
// farmland at y 1.
public final class RusticGameTests {
    private RusticGameTests() {}

    private static final BlockPos MACHINE = new BlockPos(1, 2, 0);

    private static List<BlockPos> area() {
        return List.of(new BlockPos(0, 2, 1), new BlockPos(1, 2, 1), new BlockPos(2, 2, 1),
                new BlockPos(0, 2, 2), new BlockPos(1, 2, 2), new BlockPos(2, 2, 2),
                new BlockPos(0, 2, 3), new BlockPos(1, 2, 3), new BlockPos(2, 2, 3));
    }

    private static void farmland(GameTestHelper helper) {
        for (BlockPos pos : area()) {
            helper.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        }
    }

    private static FarmMachineBlockEntity machine(GameTestHelper helper, FarmMachineBlock block) {
        helper.setBlock(MACHINE, block.defaultBlockState().setValue(FarmMachineBlock.FACING, Direction.SOUTH));
        return (FarmMachineBlockEntity) helper.getBlockEntity(MACHINE, FarmMachineBlockEntity.class);
    }

    private static void insert(FarmMachineBlockEntity machine, Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            machine.getItems().insert(ItemResource.of(item), count, tx);
            tx.commit();
        }
    }

    private static int count(FarmMachineBlockEntity machine, Item item) {
        int total = 0;
        for (int slot = 0; slot < machine.getItems().size(); slot++) {
            ItemStack stack = machine.getItems().getStack(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int age(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : -1;
    }

    // A run plants wheat on every empty farmland in front, and Hop Seeds into the bare Trellis.
    static void planterPlants(GameTestHelper helper) {
        farmland(helper);
        BlockPos trellis = new BlockPos(2, 2, 3);
        helper.setBlock(trellis, ModBlocks.TRELLIS.get());
        FarmMachineBlockEntity planter = machine(helper, ModBlocks.PLANTER.get());
        insert(planter, Items.WHEAT_SEEDS, 16);
        insert(planter, ModItems.HOP_SEEDS.get(), 2);
        int planted = planter.run(helper.getLevel());
        helper.assertTrue(planted == 9, "Planted " + planted + ", expected 9");
        for (BlockPos pos : area()) {
            if (!pos.equals(trellis)) {
                helper.assertBlockPresent(Blocks.WHEAT, pos);
            }
        }
        helper.assertTrue(helper.getBlockState(trellis).getValue(TrellisBlock.AGE) == TrellisBlock.SHOOT, "Hops weren't planted in the Trellis");
        helper.assertTrue(count(planter, Items.WHEAT_SEEDS) == 8 && count(planter, ModItems.HOP_SEEDS.get()) == 1, "Wrong seeds used");
        helper.assertTrue(planter.run(helper.getLevel()) == 0, "Planted over crops");
        helper.succeed();
    }

    // A run harvests the ripe wheat into the machine and replants it at age 0, leaves unripe wheat alone, and picks a
    // bearing Trellis's cones leaving its full vine.
    static void harvesterHarvests(GameTestHelper helper) {
        farmland(helper);
        BlockPos young = new BlockPos(0, 2, 1), trellis = new BlockPos(2, 2, 3);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        for (BlockPos pos : area()) {
            helper.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()));
        }
        helper.setBlock(young, wheat.getStateForAge(3));
        helper.setBlock(trellis, ModBlocks.TRELLIS.get().defaultBlockState().setValue(TrellisBlock.AGE, TrellisBlock.BEARING));
        FarmMachineBlockEntity harvester = machine(helper, ModBlocks.HARVESTER.get());
        int harvested = harvester.run(helper.getLevel());
        helper.assertTrue(harvested == 8, "Harvested " + harvested + ", expected 8");
        for (BlockPos pos : area()) {
            if (!pos.equals(young) && !pos.equals(trellis)) {
                helper.assertTrue(age(helper, pos) == 0, "Wheat at " + pos + " wasn't replanted");
            }
        }
        helper.assertTrue(age(helper, young) == 3, "Unripe wheat was harvested");
        helper.assertTrue(helper.getBlockState(trellis).getValue(TrellisBlock.AGE) == TrellisBlock.FULL_VINE, "The hop vine didn't stay");
        helper.assertTrue(count(harvester, Items.WHEAT) >= 7, "Only " + count(harvester, Items.WHEAT) + " wheat stored");
        helper.assertTrue(count(harvester, ModItems.HOP_CONES.get()) >= ArcforgeConfig.HOP_CONES_MIN.getAsInt(), "No cones stored");
        helper.succeed();
    }

    // With redstone attached it runs once per rising pulse and never on the timer; without, on the timer.
    static void pulseAndTimer(GameTestHelper helper) {
        farmland(helper);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockPos crop = new BlockPos(1, 2, 1), redstone = new BlockPos(0, 2, 0);
        FarmMachineBlockEntity harvester = machine(helper, ModBlocks.HARVESTER.get());
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(MACHINE);
        helper.setBlock(crop, wheat.getStateForAge(wheat.getMaxAge()));
        helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
        harvester.serverTick(level, abs, helper.getBlockState(MACHINE));
        helper.assertTrue(age(helper, crop) == 0, "The pulse didn't run it");
        helper.setBlock(crop, wheat.getStateForAge(wheat.getMaxAge()));
        for (int i = 0; i < ArcforgeConfig.MACHINE_TIMER_TICKS.getAsInt() + 5; i++) {
            harvester.serverTick(level, abs, helper.getBlockState(MACHINE));
        }
        helper.assertTrue(age(helper, crop) == wheat.getMaxAge(), "It ran again without a new pulse");
        helper.setBlock(redstone, Blocks.AIR);
        harvester.serverTick(level, abs, helper.getBlockState(MACHINE));
        helper.assertTrue(age(helper, crop) == wheat.getMaxAge(), "Losing the signal ran it");
        for (int i = 0; i < ArcforgeConfig.MACHINE_TIMER_TICKS.getAsInt(); i++) {
            harvester.serverTick(level, abs, helper.getBlockState(MACHINE));
        }
        helper.assertTrue(age(helper, crop) == 0, "The timer didn't run it");
        helper.succeed();
    }

    // Only Loam Farmland below the threshold gets fertilizer, one item each.
    static void spreaderTopsUp(GameTestHelper helper) {
        BlockPos low = new BlockPos(0, 1, 0), high = new BlockPos(2, 1, 2), spreaderPos = new BlockPos(1, 2, 1);
        BlockState loam = ModBlocks.LOAM_FARMLAND.get().defaultBlockState();
        helper.setBlock(low, loam.setValue(LoamFarmlandBlock.NUTRIENTS, 1));
        helper.setBlock(high, loam.setValue(LoamFarmlandBlock.NUTRIENTS, 12));
        helper.setBlock(spreaderPos, ModBlocks.FERTILIZER_SPREADER.get());
        FertilizerSpreaderBlockEntity spreader = (FertilizerSpreaderBlockEntity) helper.getBlockEntity(spreaderPos, FertilizerSpreaderBlockEntity.class);
        helper.assertTrue(spreader.insertByHand(new ItemStack(Items.DIRT)) == 0, "The spreader took dirt");
        spreader.insertByHand(new ItemStack(ModItems.COMPOST.get(), 4));
        int fed = spreader.spread(helper.getLevel());
        helper.assertTrue(fed == 1, "Fed " + fed + " blocks, expected 1");
        helper.assertTrue(LoamFarmlandBlock.nutrients(helper.getBlockState(low)) > 1, "The low soil wasn't topped up");
        helper.assertTrue(LoamFarmlandBlock.nutrients(helper.getBlockState(high)) == 12, "The rich soil was fertilized");
        helper.assertTrue(spreader.fertilizerCount() == 3, "Used " + (4 - spreader.fertilizerCount()) + " compost, expected 1");
        helper.succeed();
    }

    // Dry, it does nothing; with water it runs, uses water and moistens the farmland in range.
    static void sprinklerWaters(GameTestHelper helper) {
        BlockPos soil = new BlockPos(0, 1, 0), sprinklerPos = new BlockPos(2, 2, 2);
        helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 0));
        helper.setBlock(sprinklerPos, ModBlocks.COPPER_SPRINKLER.get());
        CopperSprinklerBlockEntity sprinkler = (CopperSprinklerBlockEntity) helper.getBlockEntity(sprinklerPos, CopperSprinklerBlockEntity.class);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sprinklerPos);
        for (int i = 0; i < ArcforgeConfig.SPRINKLER_INTERVAL.getAsInt() + 2; i++) {
            sprinkler.serverTick(level, abs, helper.getBlockState(sprinklerPos));
        }
        helper.assertTrue(helper.getBlockState(soil).getValue(FarmlandBlock.MOISTURE) == 0, "A dry sprinkler moistened the farmland");
        helper.assertTrue(!helper.getBlockState(sprinklerPos).getValue(CopperSprinklerBlock.RUNNING), "A dry sprinkler runs");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(sprinkler.getFluidHandler(Direction.DOWN).insert(CopperSprinklerBlockEntity.WATER, 500, tx) == 500,
                    "The sprinkler refused water");
            tx.commit();
        }
        for (int i = 0; i < ArcforgeConfig.SPRINKLER_INTERVAL.getAsInt(); i++) {
            sprinkler.serverTick(level, abs, helper.getBlockState(sprinklerPos));
        }
        helper.assertTrue(helper.getBlockState(sprinklerPos).getValue(CopperSprinklerBlock.RUNNING), "A watered sprinkler isn't running");
        helper.assertTrue(helper.getBlockState(soil).getValue(FarmlandBlock.MOISTURE) == FarmlandBlock.MAX_MOISTURE, "The farmland wasn't moistened");
        int used = 500 - sprinkler.getTank().getAmount();
        helper.assertTrue(used == ArcforgeConfig.SPRINKLER_INTERVAL.getAsInt() * ArcforgeConfig.SPRINKLER_WATER_PER_TICK.getAsInt(), "Used " + used + " mB");
        helper.succeed();
    }

    // A Scarecrow protects farmland within its radius and not beyond.
    static void scarecrowProtects(GameTestHelper helper) {
        BlockPos post = new BlockPos(1, 2, 1);
        BlockState lower = ModBlocks.SCARECROW.get().defaultBlockState();
        helper.setBlock(post.below(), Blocks.STONE);
        helper.setBlock(post, lower);
        helper.setBlock(post.above(), lower.setValue(ScarecrowBlock.HALF, DoubleBlockHalf.UPPER));
        int radius = ArcforgeConfig.SCARECROW_RADIUS.getAsInt();
        BlockPos abs = helper.absolutePos(post);
        helper.assertTrue(ScarecrowBlock.protects(helper.getLevel(), abs.offset(radius, -1, 0)), "Farmland at the edge isn't protected");
        helper.assertTrue(!ScarecrowBlock.protects(helper.getLevel(), abs.offset(radius + 1, -1, 0)), "Farmland beyond the edge is protected");
        helper.succeed();
    }

    // The Steel Sickle harvests and replants the ripe crops in a 3x3, 1 durability each.
    static void sickleReaps(GameTestHelper helper) {
        farmland(helper);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockPos young = new BlockPos(0, 2, 1), centre = new BlockPos(1, 2, 2);
        for (BlockPos pos : area()) {
            helper.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()));
        }
        helper.setBlock(young, wheat.getStateForAge(2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sickle = new ItemStack(ModItems.STEEL_SICKLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, sickle);
        BlockPos abs = helper.absolutePos(centre);
        sickle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        for (BlockPos pos : area()) {
            helper.assertTrue(age(helper, pos) == (pos.equals(young) ? 2 : 0), "Wrong age at " + pos + " after the sickle");
        }
        helper.assertTrue(sickle.getDamageValue() == 8, "Sickle took " + sickle.getDamageValue() + " damage, expected 8");
        helper.assertTrue(ModItems.STEEL_SCYTHE.get().radius() == ArcforgeConfig.SCYTHE_RADIUS.getAsInt(), "The scythe's radius isn't its config");
        helper.succeed();
    }

    static void recipes(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().recipeMap();
        for (String name : List.of("planter", "harvester", "fertilizer_spreader", "copper_sprinkler", "scarecrow", "iron_sickle", "steel_sickle", "steel_scythe")) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/" + name))) != null,
                    "No recipe for " + name);
        }
        helper.succeed();
    }
}
