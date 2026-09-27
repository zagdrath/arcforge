/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Treated wood, the Infuser that makes it from vanilla wood and creosote, and the Fuel Burner that burns
// creosote into heat. Machines are placed facing north.
public final class InfusionGameTests {
    private static final FluidResource CREOSOTE = FluidResource.of(ModFluids.CREOSOTE.get());

    private InfusionGameTests() {}

    // Planks, logs, wood and their stripped forms of every vanilla wood go in (and come out as the matching
    // treated block); treated wood and anything else is refused, and so is a bucket of a fluid no recipe uses.
    static void infuserAcceptsVanillaWood(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.INFUSER.get());
        InfuserBlockEntity infuser = helper.getBlockEntity(pos, InfuserBlockEntity.class);
        Item[][] cases = {
                { Items.OAK_PLANKS, ModBlocks.TREATED_PLANKS.get().asItem() },
                { Items.POPLAR_PLANKS, ModBlocks.TREATED_PLANKS.get().asItem() },
                { Items.CRIMSON_STEM, ModBlocks.TREATED_LOG.get().asItem() },
                { Items.BAMBOO_BLOCK, ModBlocks.TREATED_LOG.get().asItem() },
                { Items.BIRCH_WOOD, ModBlocks.TREATED_WOOD.get().asItem() },
                { Items.STRIPPED_SPRUCE_LOG, ModBlocks.STRIPPED_TREATED_LOG.get().asItem() },
                { Items.STRIPPED_WARPED_HYPHAE, ModBlocks.STRIPPED_TREATED_WOOD.get().asItem() },
        };
        for (Item[] io : cases) {
            ItemStack input = new ItemStack(io[0]);
            helper.assertTrue(infuser.getItems().isValid(InfuserBlockEntity.SLOT_INPUT, ItemResource.of(io[0])), "Infuser refuses " + io[0]);
            var recipe = MachineRecipes.infusing(helper.getLevel(), input, CREOSOTE).orElseThrow();
            helper.assertTrue(recipe.value().result().create().is(io[1]), io[0] + " infuses into " + recipe.value().result().create());
        }
        for (Item refused : new Item[] { ModBlocks.TREATED_PLANKS.get().asItem(), Items.STICK, Items.STONE }) {
            helper.assertTrue(!infuser.getItems().isValid(InfuserBlockEntity.SLOT_INPUT, ItemResource.of(refused)), "Infuser takes " + refused);
        }
        helper.assertTrue(infuser.getItems().isValid(InfuserBlockEntity.SLOT_BUCKET_IN, ItemResource.of(ModItems.CREOSOTE_BUCKET.get())), "Bucket slot refuses creosote");
        helper.assertTrue(!infuser.getItems().isValid(InfuserBlockEntity.SLOT_BUCKET_IN, ItemResource.of(Items.LAVA_BUCKET)), "Bucket slot takes lava");
        helper.succeed();
    }

    // Without creosote it waits; a creosote bucket fills the tank (the empty bucket comes out), and two oak
    // planks become two treated planks for 50 mB each, lit while it works.
    static void infuserTreatsPlanks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.INFUSER.get());
        InfuserBlockEntity infuser = helper.getBlockEntity(pos, InfuserBlockEntity.class);
        FiberGameTests.charge(infuser.getEnergy(), infuser.getEnergy().getCapacityAsInt());
        FiberGameTests.insert(infuser.getItemHandler(null), Items.OAK_PLANKS, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(infuser.getStatus() == MachineStatus.NO_FLUID, "Dry infuser is " + infuser.getStatus());
                    FiberGameTests.insert(infuser.getItemHandler(null), ModItems.CREOSOTE_BUCKET.get(), 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(infuser.getStatus() == MachineStatus.INFUSING, "Infuser never started"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(pos).getValue(MachineBlock.LIT), "Infuser is not lit while working");
                    helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_BUCKET_OUT).is(Items.BUCKET), "Empty bucket did not come out");
                })
                .thenWaitUntil(() -> helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_OUTPUT).getCount() == 2, "Not two treated planks yet"))
                .thenExecute(() -> {
                    helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_OUTPUT).is(ModBlocks.TREATED_PLANKS.get().asItem()), "Output is not treated planks");
                    helper.assertTrue(infuser.getTank().getAmount() == 1_000 - 2 * 50, "Tank holds " + infuser.getTank().getAmount() + " mB");
                })
                .thenSucceed();
    }

    // Creosote burns at 0.5 mB/t into +60 HU/t (up to 850°C); a full buffer pauses it without using fuel.
    static void fuelBurnerBurnsCreosote(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        BlockPos fullPos = new BlockPos(3, 1, 0);
        helper.setBlock(pos, ModBlocks.FUEL_BURNER.get());
        helper.setBlock(fullPos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity burner = helper.getBlockEntity(pos, FuelBurnerBlockEntity.class);
        FuelBurnerBlockEntity full = helper.getBlockEntity(fullPos, FuelBurnerBlockEntity.class);
        helper.assertTrue(FiberGameTests.insert(burner.getItemHandler(null), Items.LAVA_BUCKET, 1) == 0, "Fuel Burner took a lava bucket");
        FiberGameTests.insert(burner.getItemHandler(null), ModItems.CREOSOTE_BUCKET.get(), 1);
        try (Transaction tx = Transaction.openRoot()) {
            full.getInteractionFluidHandler().insert(CREOSOTE, 1_000, tx);
            tx.commit();
        }
        full.getHeat().add(full.getHeat().getCapacity());
        helper.assertTrue(full.getHeat().getTemperature() == 1_200, "Full burner is at " + full.getHeat().getTemperature() + "°C");
        helper.startSequence()
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(burner.getStatus() == MachineStatus.BURNING, "Burner is " + burner.getStatus());
                    helper.assertTrue(burner.getHeatPerTick() == 60, "Burner makes " + burner.getHeatPerTick() + " HU/t");
                    helper.assertTrue(helper.getBlockState(pos).getValue(MachineBlock.LIT), "Burner is not lit while burning");
                    // 20 ticks at 0.5 mB/t, give or take the tick the test runs on.
                    int used = 1_000 - burner.getTank().getAmount();
                    helper.assertTrue(used >= 9 && used <= 12, "Burner used " + used + " mB in ~20 ticks");
                    helper.assertTrue(full.getStatus() == MachineStatus.FULL, "Full burner is " + full.getStatus());
                    helper.assertTrue(full.getTank().getAmount() == 1_000, "Full burner burned fuel anyway");
                })
                .thenSucceed();
    }

    // An axe strips treated logs and wood, and nothing about treated wood burns.
    static void treatedWoodStripsAndResistsFire(GameTestHelper helper) {
        BlockPos logPos = new BlockPos(0, 1, 0);
        BlockPos woodPos = new BlockPos(2, 1, 0);
        helper.setBlock(logPos, ModBlocks.TREATED_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.setBlock(woodPos, ModBlocks.TREATED_WOOD.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (BlockPos pos : new BlockPos[] { logPos, woodPos }) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            helper.useBlock(pos, player);
        }
        helper.assertBlockPresent(ModBlocks.STRIPPED_TREATED_LOG.get(), logPos);
        helper.assertTrue(helper.getBlockState(logPos).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "Stripping turned the log");
        helper.assertBlockPresent(ModBlocks.STRIPPED_TREATED_WOOD.get(), woodPos);

        BlockPos planksPos = new BlockPos(4, 1, 0);
        helper.setBlock(planksPos, ModBlocks.TREATED_PLANKS.get());
        for (var block : ModBlocks.treatedWoodSet()) {
            var state = block.get().defaultBlockState();
            helper.assertTrue(!state.isFlammable(helper.getLevel(), helper.absolutePos(planksPos), Direction.UP), block.getId() + " is flammable");
            helper.assertTrue(new ItemStack(block.get()).is(ItemTags.NON_FLAMMABLE_WOOD), block.getId() + " is not #non_flammable_wood");
        }
        helper.assertTrue(new ItemStack(ModBlocks.TREATED_PLANKS.get()).is(ItemTags.PLANKS), "Treated planks are not #planks");
        helper.assertTrue(new ItemStack(ModBlocks.STRIPPED_TREATED_LOG.get()).is(ItemTags.LOGS), "Stripped treated logs are not #logs");
        helper.succeed();
    }
}
