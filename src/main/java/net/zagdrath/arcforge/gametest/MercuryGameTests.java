/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.logistics.MercuryVapourLampBlock;
import net.zagdrath.arcforge.blockentity.logistics.MercuryVapourLampBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.chemistry.Containment;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The mercury line: Cinnabar crushed to dust, roasted to Mercury and Sulfur Dioxide, the contact process, amalgams
// retorted in the Induction Furnace, Mercury's containment and poison, and the Mercury Vapour Lamp.
public final class MercuryGameTests {
    private MercuryGameTests() {}

    private static ChemicalReactorBlockEntity reactor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        return reactor;
    }

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int amount(FilteredFluidTank tank, Fluid fluid) {
        return tank.contains(fluid) ? tank.getAmount() : 0;
    }

    // Keeps a reactor's heat at 800°C, as a Heat face fed from a firebox would.
    private static void keepHot(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.onEachTick(() -> {
            HeatBuffer heat = reactor.getHeat();
            if (heat.getTemperature() < 800) {
                heat.add(heat.storedAt(800) - heat.getStored());
            }
        });
    }

    // A block of Cinnabar crushes into 2 Cinnabar Dust, Polished Cinnabar into 1.
    static void crushCinnabar(GameTestHelper helper) {
        BlockPos block = new BlockPos(0, 1, 0);
        BlockPos polished = new BlockPos(2, 1, 0);
        ArcCrusherBlockEntity[] crushers = new ArcCrusherBlockEntity[2];
        BlockPos[] positions = { block, polished };
        ItemStack[] inputs = { new ItemStack(Items.CINNABAR), new ItemStack(Items.POLISHED_CINNABAR) };
        for (int i = 0; i < 2; i++) {
            helper.setBlock(positions[i], ModBlocks.ARC_CRUSHER.get());
            crushers[i] = helper.getBlockEntity(positions[i], ArcCrusherBlockEntity.class);
            CrushingGameTests.charge(crushers[i].getEnergy(), 20_000);
            crushers[i].getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, inputs[i]);
        }
        helper.startSequence()
                .thenIdle(205)
                .thenExecute(() -> {
                    ItemStack fromBlock = crushers[0].getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                    ItemStack fromPolished = crushers[1].getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(fromBlock.is(ModItems.CINNABAR_DUST.get()) && fromBlock.getCount() == 2, "Cinnabar gave " + fromBlock);
                    helper.assertTrue(fromPolished.is(ModItems.CINNABAR_DUST.get()) && fromPolished.getCount() == 1, "Polished Cinnabar gave " + fromPolished);
                })
                .thenSucceed();
    }

    // Cinnabar Dust and 100 mB Oxygen roast at 600°C+ into 100 mB Mercury and 100 mB Sulfur Dioxide; cold, nothing happens.
    static void roastCinnabar(GameTestHelper helper) {
        ChemicalReactorBlockEntity hot = reactor(helper, new BlockPos(0, 1, 0));
        ChemicalReactorBlockEntity cold = reactor(helper, new BlockPos(2, 1, 0));
        for (ChemicalReactorBlockEntity reactor : new ChemicalReactorBlockEntity[] { hot, cold }) {
            reactor.getItems().setStack(ChemicalReactorBlockEntity.SLOT_INPUT, new ItemStack(ModItems.CINNABAR_DUST.get()));
            helper.assertTrue(fill(reactor.getRouter(), ModFluids.OXYGEN.get(), 100) == 100, "Router refused Oxygen");
        }
        keepHot(helper, hot);
        helper.startSequence()
                .thenIdle(130)
                .thenExecute(() -> {
                    helper.assertTrue(amount(hot.getOutputTank(), ModFluids.MERCURY.get()) == 100,
                            "Output holds " + hot.getOutputTank().getAmount() + " mB, expected 100 mB of Mercury");
                    helper.assertTrue(amount(hot.getByproductTank(), ModFluids.SULFUR_DIOXIDE.get()) == 100,
                            "By-product holds " + hot.getByproductTank().getAmount() + " mB, expected 100 mB of Sulfur Dioxide");
                    helper.assertTrue(!cold.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT).isEmpty()
                            && cold.getOutputTank().getAmount() == 0, "A cold reactor roasted Cinnabar Dust");
                })
                .thenSucceed();
    }

    // 200 mB Sulfur Dioxide, 100 mB Oxygen and 500 mB Water make 500 mB Sulfuric Acid.
    static void contactProcess(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, new BlockPos(0, 1, 0));
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.SULFUR_DIOXIDE.get(), 200) == 200, "Router refused Sulfur Dioxide");
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.OXYGEN.get(), 100) == 100, "Router refused Oxygen");
        helper.assertTrue(fill(reactor.getRouter(), Fluids.WATER, 500) == 500, "Router refused Water");
        helper.startSequence()
                .thenIdle(105)
                .thenExecute(() -> helper.assertTrue(amount(reactor.getOutputTank(), ModFluids.SULFURIC_ACID.get()) == 500,
                        "Output holds " + reactor.getOutputTank().getAmount() + " mB, expected 500 mB of Sulfuric Acid"))
                .thenSucceed();
    }

    // Raw Gold in 100 mB Mercury makes Gold Amalgam; the Induction Furnace retorts it into 4 Gold Dust and 90 mB of
    // Mercury in its by-product tank, which a By-product face gives out. The array won't take an amalgam.
    static void amalgamAndRetort(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, new BlockPos(0, 1, 0));
        reactor.getItems().setStack(ChemicalReactorBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_GOLD));
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.MERCURY.get(), 100) == 100, "Router refused Mercury");
        BlockPos furnacePos = new BlockPos(2, 1, 0);
        helper.setBlock(furnacePos, ModBlocks.INDUCTION_FURNACE.get());
        InductionFurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos, InductionFurnaceBlockEntity.class);
        CrushingGameTests.charge(furnace.getEnergy(), 20_000);
        helper.startSequence()
                .thenIdle(105)
                .thenExecute(() -> {
                    ItemStack amalgam = reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(amalgam.is(ModItems.GOLD_AMALGAM.get()) && amalgam.getCount() == 1, "The reactor made " + amalgam);
                    helper.assertTrue(InductionFurnaceBlockEntity.isItemValid(helper.getLevel(), InductionFurnaceBlockEntity.SLOT_INPUT,
                            net.neoforged.neoforge.transfer.item.ItemResource.of(amalgam)), "The Induction Furnace refuses Gold Amalgam");
                    furnace.getItems().setStack(InductionFurnaceBlockEntity.SLOT_INPUT, amalgam.copy());
                    reactor.getItems().setStack(ChemicalReactorBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                })
                .thenIdle(115)
                .thenExecute(() -> {
                    ItemStack dust = furnace.getItems().getStack(InductionFurnaceBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(dust.is(ModItems.GOLD_DUST.get()) && dust.getCount() == 4, "Retorting made " + dust + ", expected 4 Gold Dust");
                    helper.assertTrue(amount(furnace.getMercuryTank(), ModFluids.MERCURY.get()) == 90,
                            "The furnace holds " + furnace.getMercuryTank().getAmount() + " mB, expected 90 mB of Mercury back");
                    helper.assertTrue(furnace.getFluidHandler(null) != null, "No unsided fluid view for Jade");
                })
                .thenSucceed();
    }

    // Mercury is a liquid that only Tempered storage and up holds: a Wrought Fluid Tank refuses it, a Tempered one takes
    // it. Sulfur Dioxide is a gas.
    static void containment(GameTestHelper helper) {
        FluidResource mercury = FluidResource.of(ModFluids.MERCURY.get());
        helper.assertTrue(!Gases.isGas(ModFluids.MERCURY.get()) && Gases.isGas(ModFluids.SULFUR_DIOXIDE.get()), "Mercury or Sulfur Dioxide in the wrong state");
        helper.assertTrue(!Containment.holds(ConduitTier.WROUGHT, mercury) && Containment.holds(ConduitTier.TEMPERED, mercury)
                && Containment.holds(ConduitTier.WROUGHT, FluidResource.of(Fluids.WATER)), "The containment tag is wrong");
        BlockPos wrought = new BlockPos(0, 1, 0);
        BlockPos tempered = new BlockPos(2, 1, 0);
        helper.setBlock(wrought, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        helper.setBlock(tempered, ModBlocks.fluidTank(ConduitTier.TEMPERED).get());
        ResourceHandler<FluidResource> wroughtTank = helper.getBlockEntity(wrought, FluidTankBlockEntity.class).getFluidHandler(Direction.UP);
        ResourceHandler<FluidResource> temperedTank = helper.getBlockEntity(tempered, FluidTankBlockEntity.class).getFluidHandler(Direction.UP);
        helper.assertTrue(wroughtTank != null && fill(wroughtTank, ModFluids.MERCURY.get(), 1_000) == 0, "A Wrought Fluid Tank took Mercury");
        helper.assertTrue(temperedTank != null && fill(temperedTank, ModFluids.MERCURY.get(), 1_000) == 1_000, "A Tempered Fluid Tank refused Mercury");
        helper.succeed();
    }

    // A pig wading in Mercury is poisoned; one in a full Hazmat Suit isn't.
    static void mercuryPoisons(GameTestHelper helper) {
        BlockPos bare = new BlockPos(0, 1, 0);
        BlockPos suited = new BlockPos(3, 1, 0);
        helper.setBlock(bare, ModBlocks.MERCURY.get());
        helper.setBlock(suited, ModBlocks.MERCURY.get());
        LivingEntity bareBody = helper.spawn(EntityTypes.PIG, bare);
        LivingEntity suitedBody = helper.spawn(EntityTypes.PIG, suited);
        suitedBody.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HAZMAT_HELMET.get()));
        suitedBody.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_CHESTPLATE.get()));
        suitedBody.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_LEGGINGS.get()));
        suitedBody.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.HAZMAT_BOOTS.get()));
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(bareBody.hasEffect(MobEffects.POISON), "Mercury didn't poison a pig");
                    helper.assertTrue(!suitedBody.hasEffect(MobEffects.POISON), "Mercury poisoned a pig in a full Hazmat Suit");
                })
                .thenSucceed();
    }

    // A lamp on a floor lights (15) with FE, goes dark under a redstone signal, and stays dark with no FE.
    static void vapourLamp(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos pos = floor.above();
        BlockPos unpowered = new BlockPos(4, 2, 1);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(unpowered.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.MERCURY_VAPOUR_LAMP.get().defaultBlockState().setValue(MercuryVapourLampBlock.FACING, Direction.UP));
        helper.setBlock(unpowered, ModBlocks.MERCURY_VAPOUR_LAMP.get().defaultBlockState().setValue(MercuryVapourLampBlock.FACING, Direction.UP));
        MercuryVapourLampBlockEntity lamp = helper.getBlockEntity(pos, MercuryVapourLampBlockEntity.class);
        CrushingGameTests.charge(lamp.getEnergy(), 2_000);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(pos).getValue(MercuryVapourLampBlock.LIT), "A charged lamp isn't lit");
                    helper.assertTrue(helper.getBlockState(pos).getLightEmission() == 15,
                            "A lit lamp doesn't give light 15");
                    helper.assertTrue(!helper.getBlockState(unpowered).getValue(MercuryVapourLampBlock.LIT), "A lamp with no FE is lit");
                    helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockState(pos).getValue(MercuryVapourLampBlock.LIT), "A redstone signal didn't turn the lamp off"))
                .thenSucceed();
    }
}
