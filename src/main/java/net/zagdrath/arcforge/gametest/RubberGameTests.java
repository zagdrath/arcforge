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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.ResinTapBlock;
import net.zagdrath.arcforge.blockentity.farming.ResinTapBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Rubber and bio-plastics: the Rubber Dandelion (drops, farms, Latex from the Oil Press), the Resin Tap (Latex on a living
// jungle tree, Pine Resin on spruce, nothing on a dead log, and it drops with its log), Pine Resin in the Infuser, Latex
// dried into Raw Rubber, the Vulcanizer (needs 140°C), Ethylene with its Water by-product, bio Plastic and PVC Sheet, and
// the plastics tag and the gasket and PVC recipe changes.
public final class RubberGameTests {
    private RubberGameTests() {}

    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // A ripe Rubber Dandelion drops roots and seeds; the farms grow it; a root presses into 100 mB of Latex.
    static void dandelion(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockState ripe = ModBlocks.RUBBER_DANDELION.get().getStateForAge(ModBlocks.RUBBER_DANDELION.get().getMaxAge());
        List<ItemStack> drops = Block.getDrops(ripe, level, helper.absolutePos(POS), null);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.RUBBER_DANDELION_ROOTS.get())), "A ripe plant drops " + drops);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.RUBBER_DANDELION_SEEDS.get())), "No seeds from a ripe plant");
        helper.assertTrue(MachineRecipes.isClocheSeed(level, new ItemStack(ModItems.RUBBER_DANDELION_SEEDS.get())), "The farms don't grow it");
        var latex = MachineRecipes.oilPressing(level, new ItemStack(ModItems.RUBBER_DANDELION_ROOTS.get()));
        helper.assertTrue(latex.isPresent() && latex.get().value().result().fluid().value() == ModFluids.LATEX.get()
                && latex.get().value().result().amount() == 100, "Roots don't press into 100 mB of Latex");
        helper.succeed();
    }

    // A trunk of `log` from y 1 to 4 at (x, z), with natural (non-persistent) leaves on top if leaves is set.
    private static BlockPos tree(GameTestHelper helper, int x, int z, Block log, Block leaves) {
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(x, y, z), log);
        }
        if (leaves != null) {
            BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(new BlockPos(x + dx, 5, z + dz), leaf);
                }
            }
        }
        return new BlockPos(x, 2, z);
    }

    private static ResinTapBlockEntity tap(GameTestHelper helper, BlockPos log) {
        BlockPos pos = log.north();
        helper.setBlock(pos, ModBlocks.RESIN_TAP.get().defaultBlockState().setValue(ResinTapBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, ResinTapBlockEntity.class);
    }

    // On a living jungle tree each drip is Latex; on a spruce tree Pine Resin comes (up to its cap); a trunk with no
    // leaves gives nothing; the tap drops when its log goes.
    static void resinTap(GameTestHelper helper) {
        var level = helper.getLevel();
        ResinTapBlockEntity jungle = tap(helper, tree(helper, 1, 2, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES));
        ResinTapBlockEntity spruce = tap(helper, tree(helper, 5, 2, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES));
        BlockPos deadLog = tree(helper, 9, 2, Blocks.OAK_LOG, null);
        ResinTapBlockEntity dead = tap(helper, deadLog);
        helper.assertTrue(jungle.drip(level, jungle.getBlockState()), "A jungle tap gained nothing");
        helper.assertTrue(jungle.getTank().contains(ModFluids.LATEX.get())
                && jungle.getTank().getAmount() == ArcforgeConfig.RESIN_TAP_LATEX_PER_DRIP.getAsInt(), "Jungle tap holds " + jungle.getTank().getAmount());
        for (int i = 0; i < 200; i++) {
            spruce.drip(level, spruce.getBlockState());
            dead.drip(level, dead.getBlockState());
        }
        int resin = spruce.getResin().getCount();
        helper.assertTrue(spruce.getResin().is(ModItems.PINE_RESIN.get()) && resin > 0 && resin <= ArcforgeConfig.RESIN_TAP_MAX_RESIN.getAsInt(),
                "Spruce tap holds " + spruce.getResin());
        helper.assertTrue(dead.getResin().isEmpty() && !dead.isAlive(), "A tap on a dead log gathered " + dead.getResin());
        helper.assertTrue(!ResinTapBlock.isLog(Blocks.STONE.defaultBlockState()), "Stone counts as a log");
        // A bucket's worth comes out of any face, as a hopper or conduit would take it.
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(jungle.getFluidHandler(Direction.DOWN).extract(FluidResource.of(ModFluids.LATEX.get()), 1_000, tx) > 0,
                    "The Latex can't be drawn out");
        }
        helper.setBlock(deadLog, Blocks.AIR);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockState(deadLog.north()).is(ModBlocks.RESIN_TAP.get()), "The tap stayed without its log"))
                .thenSucceed();
    }

    // Planks with Pine Resin in the additive slot become treated planks with no Creosote; with neither it says so.
    static void infuserPineResin(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.INFUSER.get());
        InfuserBlockEntity infuser = helper.getBlockEntity(POS, InfuserBlockEntity.class);
        FiberGameTests.charge(infuser.getEnergy(), infuser.getEnergy().getCapacityAsInt());
        infuser.getItems().setStack(InfuserBlockEntity.SLOT_INPUT, new ItemStack(Items.OAK_PLANKS, 2));
        helper.assertTrue(infuser.getItems().isValid(InfuserBlockEntity.SLOT_ADDITIVE, net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.PINE_RESIN.get())),
                "The additive slot refuses Pine Resin");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(infuser.getStatus() == MachineStatus.NO_ADDITIVE || infuser.getStatus() == MachineStatus.NO_FLUID,
                            "With neither it's " + infuser.getStatus());
                    infuser.getItems().setStack(InfuserBlockEntity.SLOT_ADDITIVE, new ItemStack(ModItems.PINE_RESIN.get(), 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_OUTPUT).getCount() == 2, "Not two treated planks yet"))
                .thenExecute(() -> {
                    helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_OUTPUT).is(ModBlocks.TREATED_PLANKS.get().asItem()), "Not treated planks");
                    helper.assertTrue(infuser.getItems().getStack(InfuserBlockEntity.SLOT_ADDITIVE).isEmpty(), "The resin wasn't used");
                    helper.assertTrue(infuser.getTank().getAmount() == 0, "Creosote appeared");
                })
                .thenSucceed();
    }

    // 250 mB of Latex in the Grain Dryer's tank dries into Raw Rubber.
    static void dryerLatex(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.GRAIN_DRYER.get());
        GrainDryerBlockEntity dryer = helper.getBlockEntity(POS, GrainDryerBlockEntity.class);
        helper.assertTrue(fill(dryer.getFluidHandler(Direction.UP), ModFluids.LATEX.get(), 500) == 500, "The dryer refused Latex");
        helper.assertTrue(fill(dryer.getFluidHandler(Direction.UP), Fluids.WATER, 100) == 0, "The dryer took water");
        dryer.getHeat().add(dryer.getHeat().getCapacity() / 2);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(dryer.getItems().getStack(GrainDryerBlockEntity.SLOT_OUTPUT).is(ModItems.RAW_RUBBER.get()), "No Raw Rubber yet"))
                .thenExecute(() -> helper.assertTrue(dryer.getTank().getAmount() == 250, "The tank holds " + dryer.getTank().getAmount() + " mB"))
                .thenSucceed();
    }

    // Cold it waits; at 140°C or more, 2 Raw Rubber and 1 Sulfur Dust (either slot) cure into 2 Rubber.
    static void vulcanizer(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.VULCANIZER.get());
        VulcanizerBlockEntity machine = helper.getBlockEntity(POS, VulcanizerBlockEntity.class);
        helper.assertTrue(CrushingGameTests.insert(machine.getItemHandler(Direction.UP), ModItems.SULFUR_DUST.get(), 1) == 1, "It refused Sulfur");
        helper.assertTrue(CrushingGameTests.insert(machine.getItemHandler(Direction.UP), ModItems.RAW_RUBBER.get(), 2) == 2, "It refused Raw Rubber");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(machine.getStatus() == MachineStatus.TOO_COLD, "A cold vulcanizer is " + machine.getStatus());
                    machine.getHeat().add(machine.getHeat().getCapacity());
                    helper.assertTrue(machine.getHeat().getTemperature() >= VulcanizerBlockEntity.minTemperature(), "A full buffer isn't 140°C");
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.getItems().getStack(VulcanizerBlockEntity.SLOT_OUTPUT).getCount() == 2
                        && machine.getItems().getStack(VulcanizerBlockEntity.SLOT_OUTPUT).is(ModItems.RUBBER.get()), "No Rubber yet"))
                .thenSucceed();
    }

    private static ChemicalReactorBlockEntity reactor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        return reactor;
    }

    // Ethanol with a little Sulfuric Acid becomes Ethylene, its Water in the by-product tank; Ethylene polymerises into
    // Plastic Sheet; Ethylene and Chlorine make PVC Sheet.
    static void bioPlastics(GameTestHelper helper) {
        ChemicalReactorBlockEntity ethylene = reactor(helper, new BlockPos(0, 1, 0));
        ChemicalReactorBlockEntity plastic = reactor(helper, new BlockPos(2, 1, 0));
        ChemicalReactorBlockEntity pvc = reactor(helper, new BlockPos(4, 1, 0));
        fill(ethylene.getRouter(), ModFluids.ETHANOL.get(), 100);
        fill(ethylene.getRouter(), ModFluids.SULFURIC_ACID.get(), 5);
        fill(plastic.getRouter(), ModFluids.ETHYLENE.get(), 120);
        fill(pvc.getRouter(), ModFluids.ETHYLENE.get(), 60);
        fill(pvc.getRouter(), ModFluids.CHLORINE.get(), 60);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ethylene.getOutputTank().contains(ModFluids.ETHYLENE.get()) && ethylene.getOutputTank().getAmount() == 60,
                        "No Ethylene yet"))
                .thenExecute(() -> helper.assertTrue(ethylene.getByproductTank().contains(Fluids.WATER) && ethylene.getByproductTank().getAmount() == 40,
                        "By-product tank holds " + ethylene.getByproductTank().getAmount() + " mB of " + ethylene.getByproductTank().getResource(0)))
                .thenWaitUntil(() -> helper.assertTrue(plastic.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).is(ModItems.PLASTIC_SHEET.get()),
                        "No bio Plastic Sheet yet"))
                .thenWaitUntil(() -> helper.assertTrue(pvc.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).is(ModItems.PVC_SHEET.get()), "No PVC yet"))
                .thenSucceed();
    }

    // Both sheets are #arcforge:plastics, and the Light Oil recipe still makes Plastic Sheet.
    static void plastics(GameTestHelper helper) {
        TagKey<Item> plastics = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "plastics"));
        helper.assertTrue(new ItemStack(ModItems.PLASTIC_SHEET.get()).is(plastics) && new ItemStack(ModItems.PVC_SHEET.get()).is(plastics),
                "The sheets aren't #arcforge:plastics");
        TagKey<Item> rubbers = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "rubbers"));
        helper.assertTrue(new ItemStack(ModItems.RUBBER.get()).is(rubbers), "Rubber isn't #c:rubbers");
        helper.assertTrue(MachineRecipes.isReactorFluid(helper.getLevel(), FluidResource.of(ModFluids.LIGHT_OIL.get())), "The Light Oil plastic recipe is gone");
        // Gaskets in the Pressurized Cylinder, PVC in the Fluid Tank (both keep their contents as tier upgrades).
        ItemStack hardenedAlloy = new ItemStack(ModItems.HARDENED_ALLOY.get());
        ItemStack cylinder = AlloyGameTests.craft(helper, "crafting/hardened_pressurized_cylinder", new String[] { "PHP", "HXH", "GHG" }, java.util.Map.of(
                'P', new ItemStack(ModItems.STEEL_PLATE.get()), 'H', hardenedAlloy, 'G', new ItemStack(ModItems.RUBBER_GASKET.get()),
                'X', new ItemStack(ModBlocks.pressurizedCylinder(net.zagdrath.arcforge.conduit.ConduitTier.TEMPERED).get())));
        helper.assertTrue(cylinder.is(ModBlocks.pressurizedCylinder(net.zagdrath.arcforge.conduit.ConduitTier.HARDENED).get().asItem()), "Made " + cylinder);
        ItemStack tank = AlloyGameTests.craft(helper, "crafting/hardened_fluid_tank", new String[] { "HLH", "LXL", "HVH" }, java.util.Map.of(
                'H', hardenedAlloy, 'L', new ItemStack(Items.GLASS), 'V', new ItemStack(ModItems.PVC_SHEET.get()),
                'X', new ItemStack(ModBlocks.fluidTank(net.zagdrath.arcforge.conduit.ConduitTier.TEMPERED).get())));
        helper.assertTrue(tank.is(ModBlocks.fluidTank(net.zagdrath.arcforge.conduit.ConduitTier.HARDENED).get().asItem()), "Made " + tank);
        helper.succeed();
    }
}
