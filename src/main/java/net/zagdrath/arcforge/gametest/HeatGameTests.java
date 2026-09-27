/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.CombustionGeneratorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;

// Power and heat: the burners, the Geothermal Plant, the Thermoelectric Plant and moving heat between them.
// Machines are placed facing north, so a machine's east face is its left side and its west face its right.
public final class HeatGameTests {
    private HeatGameTests() {}

    private static void insertThroughTop(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item, int count) {
        ResourceHandler<ItemResource> input = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(input != null, "Top face does not accept items");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(input.insert(ItemResource.of(item), count, tx) == count, "Top face refused " + item);
            tx.commit();
        }
    }

    // Fills a buffer until it reads the given temperature.
    private static void heatTo(HeatBuffer heat, int celsius) {
        heat.remove(heat.getStored());
        heat.add((int) ((long) heat.getCapacity() * (celsius - HeatBuffer.AMBIENT_CELSIUS) / (heat.getMaxCelsius() - HeatBuffer.AMBIENT_CELSIUS)));
    }

    // Coal through the top face burns at twice furnace speed (800 ticks) into FE, lighting the generator.
    static void generatorBurns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.COMBUSTION_GENERATOR.get());
        insertThroughTop(helper, pos, Items.COAL, 2);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    CombustionGeneratorBlockEntity generator = helper.getBlockEntity(pos, CombustionGeneratorBlockEntity.class);
                    helper.assertTrue(generator.getStored() >= 40 * 15, "Generator made only " + generator.getStored() + " FE");
                    helper.assertTrue(generator.getBurnTime() > 700 && generator.getBurnTime() <= 800, "Coal burns for " + generator.getBurnTime() + " more ticks, expected ~800 in all");
                    helper.assertTrue(generator.getItems().getStack(0).getCount() == 1, "Generator burned more than one coal");
                    helper.assertTrue(helper.getBlockState(pos).getValue(MachineBlock.LIT), "Generator is not lit while burning");
                    BlockPos back = helper.absolutePos(pos).south();
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.SOUTH) != null,
                            "Back face does not give FE " + back);
                })
                .thenSucceed();
    }

    // Lava from a bucket drains into heat; the plant has no FE anywhere. Touching lava and magma heat an
    // empty plant too.
    static void geothermalMakesHeat(GameTestHelper helper) {
        BlockPos tanked = new BlockPos(0, 1, 0);
        BlockPos passive = new BlockPos(3, 1, 0);
        helper.setBlock(tanked, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(passive, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(passive.east(), Blocks.LAVA);
        helper.setBlock(passive.below(), Blocks.MAGMA_BLOCK);
        insertThroughTop(helper, tanked, Items.LAVA_BUCKET, 1);
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    GeothermalPlantBlockEntity plant = helper.getBlockEntity(tanked, GeothermalPlantBlockEntity.class);
                    helper.assertTrue(plant.getHeat().getStored() > 0, "Lava in the tank made no heat");
                    helper.assertTrue(plant.getItems().getStack(GeothermalPlantBlockEntity.SLOT_OUTPUT).is(Items.BUCKET), "Empty bucket did not come out");
                    for (Direction direction : Direction.values()) {
                        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(tanked), direction) == null,
                                "Geothermal Plant exposes FE on its " + direction + " face");
                    }
                    GeothermalPlantBlockEntity warmed = helper.getBlockEntity(passive, GeothermalPlantBlockEntity.class);
                    helper.assertTrue(warmed.getHeat().getStored() > 0, "Touching lava and magma made no heat");
                    helper.assertTrue(helper.getBlockState(passive).getValue(MachineBlock.LIT), "Passively heated plant is not lit");
                })
                .thenSucceed();
    }

    // A hot Firebox touching a Thermoelectric Plant's heat face feeds it, and the plant makes FE.
    static void fireboxDrivesThermoelectric(GameTestHelper helper) {
        BlockPos fireboxPos = new BlockPos(0, 1, 0);
        BlockPos plantPos = new BlockPos(0, 1, 1);
        helper.setBlock(fireboxPos, ModBlocks.FIREBOX.get());
        helper.setBlock(plantPos, ModBlocks.THERMOELECTRIC_PLANT.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(fireboxPos, FireboxBlockEntity.class);
        ThermoelectricPlantBlockEntity plant = helper.getBlockEntity(plantPos, ThermoelectricPlantBlockEntity.class);
        // The firebox's back (south) touches the plant's front.
        plant.setSideMode(RelativeSide.FRONT, SideMode.HEAT);
        firebox.getHeat().add(firebox.getHeat().getCapacity());
        heatTo(plant.getHeat(), 900);
        int start = firebox.getHeat().getStored();
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(firebox.getHeat().getStored() < start, "No heat left the firebox");
                    helper.assertTrue(plant.getFePerTick() >= 25, "Plant at " + plant.getHeat().getTemperature() + "°C makes only " + plant.getFePerTick() + " FE/t");
                    helper.assertTrue(helper.getBlockState(plantPos).getValue(MachineBlock.LIT), "Plant is not lit while making FE");
                })
                .thenSucceed();
    }

    // Heat never flows from colder to hotter: a full Geothermal Plant (600°C) can't feed a hotter plant,
    // and the plant runs at the efficiency of its own temperature.
    static void heatFlowsHotToCold(GameTestHelper helper) {
        BlockPos geoPos = new BlockPos(0, 1, 0);
        BlockPos plantPos = new BlockPos(0, 1, 1);
        helper.setBlock(geoPos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(plantPos, ModBlocks.THERMOELECTRIC_PLANT.get());
        GeothermalPlantBlockEntity geothermal = helper.getBlockEntity(geoPos, GeothermalPlantBlockEntity.class);
        ThermoelectricPlantBlockEntity plant = helper.getBlockEntity(plantPos, ThermoelectricPlantBlockEntity.class);
        plant.setSideMode(RelativeSide.FRONT, SideMode.HEAT);
        geothermal.getHeat().add(geothermal.getHeat().getCapacity());
        heatTo(plant.getHeat(), 800);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(geothermal.getHeat().isFull(), "Heat flowed from 600°C into a plant at " + plant.getHeat().getTemperature() + "°C");
                    helper.assertTrue(ThermoelectricPlantBlockEntity.efficiency(600) == 0.5F, "Efficiency at 600°C is " + ThermoelectricPlantBlockEntity.efficiency(600));
                })
                .thenSucceed();
    }

    // A thermodynamic conduit carries heat from the firebox's heat face to the plant's, and glows.
    static void thermalConduitCarriesHeat(GameTestHelper helper) {
        BlockPos fireboxPos = new BlockPos(0, 1, 0);
        BlockPos plantPos = new BlockPos(4, 1, 0);
        BlockState northFacing = ModBlocks.FIREBOX.get().defaultBlockState();
        helper.setBlock(fireboxPos, northFacing);
        helper.setBlock(plantPos, ModBlocks.THERMOELECTRIC_PLANT.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(fireboxPos, FireboxBlockEntity.class);
        ThermoelectricPlantBlockEntity plant = helper.getBlockEntity(plantPos, ThermoelectricPlantBlockEntity.class);
        firebox.setSideMode(RelativeSide.LEFT, SideMode.HEAT);
        plant.setSideMode(RelativeSide.RIGHT, SideMode.HEAT);
        firebox.getHeat().add(firebox.getHeat().getCapacity());
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), ModBlocks.conduit(ConduitType.THERMAL, ConduitTier.WROUGHT).get());
        }
        for (int x = 1; x <= 3; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 0)));
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plant.getHeat().getStored() > 0, "No heat reached the plant"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 0)).getValue(ActiveConduitBlock.ACTIVE),
                        "Conduit is not glowing while heat moves"))
                .thenSucceed();
    }
}
