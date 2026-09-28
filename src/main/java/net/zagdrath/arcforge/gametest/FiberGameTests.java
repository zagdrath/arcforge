/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The Fiberizer and mineral wool, insulated Heat Cells, and the hotter Geothermal Plant driving a
// Thermoelectric Plant past 100%. Machines are placed facing north.
public final class FiberGameTests {
    private FiberGameTests() {}

    static void charge(EnergyHandler energy, int amount) {
        while (amount > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = energy.insert(amount, tx);
                tx.commit();
                if (inserted <= 0) {
                    return;
                }
                amount -= inserted;
            }
        }
    }

    static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, tx);
            tx.commit();
            return inserted;
        }
    }

    // Fills a buffer until it reads the given temperature.
    static void heatTo(HeatBuffer heat, int celsius) {
        heat.remove(heat.getStored());
        heat.add((int) Math.ceil((double) heat.getCapacity() * (celsius - HeatBuffer.AMBIENT_CELSIUS) / (heat.getMaxCelsius() - HeatBuffer.AMBIENT_CELSIUS)));
    }

    // Slag, basalt, smooth basalt and polished basalt go in; anything else is refused.
    static void fiberizerAcceptsInputs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.FIBERIZER.get());
        FiberizerBlockEntity fiberizer = helper.getBlockEntity(pos, FiberizerBlockEntity.class);
        for (Item item : new Item[] { ModItems.SLAG.get(), Items.BASALT, Items.SMOOTH_BASALT, Items.POLISHED_BASALT }) {
            helper.assertTrue(fiberizer.getItems().isValid(FiberizerBlockEntity.SLOT_INPUT, ItemResource.of(item)), "Fiberizer refuses " + item);
        }
        helper.assertTrue(insert(fiberizer.getItemHandler(null), Items.COBBLESTONE, 1) == 0, "Fiberizer took cobblestone");
        helper.succeed();
    }

    // With FE but no heat it's too cold and waits; once a burning Firebox feeds its top face it spins
    // slag into two Slag Wool, lit while it works.
    static void fiberizerNeedsHeat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        BlockPos fireboxPos = new BlockPos(0, 2, 0);
        helper.setBlock(pos, ModBlocks.FIBERIZER.get());
        helper.setBlock(fireboxPos, ModBlocks.FIREBOX.get());
        FiberizerBlockEntity fiberizer = helper.getBlockEntity(pos, FiberizerBlockEntity.class);
        FireboxBlockEntity firebox = helper.getBlockEntity(fireboxPos, FireboxBlockEntity.class);
        firebox.setSideMode(RelativeSide.BOTTOM, SideMode.HEAT);
        charge(fiberizer.getEnergy(), fiberizer.getEnergy().getCapacityAsInt());
        insert(fiberizer.getItemHandler(null), ModItems.SLAG.get(), 1);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(fiberizer.getStatus() == MachineStatus.TOO_COLD, "Cold fiberizer is " + fiberizer.getStatus());
                    helper.assertTrue(fiberizer.getProgress() == 0, "Cold fiberizer made progress");
                    insert(firebox.getItemHandler(null), Items.COAL, 4);
                })
                .thenWaitUntil(() -> helper.assertTrue(fiberizer.getStatus() == MachineStatus.FIBERIZING, "Fiberizer never started"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getValue(MachineBlock.LIT), "Fiberizer is not lit while working"))
                .thenWaitUntil(() -> helper.assertTrue(fiberizer.getItems().getStack(FiberizerBlockEntity.SLOT_OUTPUT).getCount() == 2,
                        "No Slag Wool yet (" + fiberizer.getStatus() + ", " + fiberizer.getHeat().getTemperature() + "°C)"))
                .thenExecute(() -> {
                    helper.assertTrue(fiberizer.getItems().getStack(FiberizerBlockEntity.SLOT_OUTPUT).is(ModItems.SLAG_WOOL.get()), "Output is not Slag Wool");
                    // 160 ticks at 30 FE/t.
                    int used = fiberizer.getEnergy().getCapacityAsInt() - fiberizer.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 160 * 30, "One operation used " + used + " FE");
                })
                .thenSucceed();
    }

    // Eight Insulation Upgrades cut a cell's leak to 0.8^8 (~17%). The slot takes nothing else, and no more than 8.
    static void heatCellInsulation(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(0, 1, 0);
        BlockPos insulatedPos = new BlockPos(2, 1, 0);
        helper.setBlock(plainPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        helper.setBlock(insulatedPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        HeatCellBlockEntity plain = helper.getBlockEntity(plainPos, HeatCellBlockEntity.class);
        HeatCellBlockEntity insulated = helper.getBlockEntity(insulatedPos, HeatCellBlockEntity.class);
        var upgrades = insulated.getUpgrades();
        helper.assertTrue(insert(upgrades, ModItems.SPEED_UPGRADE.get(), 1) == 0, "Heat cell took a Speed Upgrade");
        helper.assertTrue(insert(upgrades, ModItems.INSULATION_UPGRADE.get(), 9) == 8, "Heat cell didn't take exactly 8 Insulation Upgrades");
        helper.assertTrue(insulated.insulation() == 8, "Heat cell counts " + insulated.insulation() + " Insulation Upgrades");
        for (HeatCellBlockEntity cell : new HeatCellBlockEntity[] { plain, insulated }) {
            cell.clearSideModes();
            cell.getHeat().add(cell.getHeat().getCapacity());
        }
        helper.startSequence()
                .thenIdle(61)
                .thenExecute(() -> {
                    double plainLost = plain.getHeat().getCapacity() - plain.getHeat().getStored();
                    double insulatedLost = insulated.getHeat().getCapacity() - insulated.getHeat().getStored();
                    double ratio = insulatedLost / plainLost;
                    helper.assertTrue(ratio > 0.15 && ratio < 0.19, "8 Insulation Upgrades leave " + ratio * 100 + "% of the leak, expected ~16.8%");
                    helper.assertTrue(insulated.getLeakPerTick() < plain.getLeakPerTick(), "Insulated cell shows no smaller leak");
                    helper.assertTrue("1.68".equals(HeatCellBlockEntity.leakPercentText(HeatCellBlockEntity.leakPercentPerMinute(ConduitTier.WROUGHT, 8))),
                            "Wrought leak with 8 upgrades reads " + HeatCellBlockEntity.leakPercentText(HeatCellBlockEntity.leakPercentPerMinute(ConduitTier.WROUGHT, 8)));
                })
                .thenSucceed();
    }

    // Full cells read their tier's temperature, and a Firebox (1,100°C) can't push into a Hardened cell
    // that's already at 1,100°C (about 84% full).
    static void heatCellTierTemperature(GameTestHelper helper) {
        int[] expected = { 1_100, 1_100, 1_300, 1_400 };
        for (ConduitTier tier : ConduitTier.values()) {
            BlockPos pos = new BlockPos(tier.ordinal() * 2, 1, 2);
            helper.setBlock(pos, ModBlocks.heatCell(tier).get());
            HeatCellBlockEntity cell = helper.getBlockEntity(pos, HeatCellBlockEntity.class);
            cell.clearSideModes();
            cell.getHeat().add(cell.getHeat().getCapacity());
            helper.assertTrue(cell.getHeat().getTemperature() == expected[tier.ordinal()],
                    "Full " + tier.getSerializedName() + " cell is at " + cell.getHeat().getTemperature() + "°C");
        }
        BlockPos fireboxPos = new BlockPos(0, 1, 0);
        BlockPos cellPos = new BlockPos(0, 1, 1);
        helper.setBlock(fireboxPos, ModBlocks.FIREBOX.get());
        helper.setBlock(cellPos, ModBlocks.heatCell(ConduitTier.HARDENED).get());
        FireboxBlockEntity firebox = helper.getBlockEntity(fireboxPos, FireboxBlockEntity.class);
        HeatCellBlockEntity cell = helper.getBlockEntity(cellPos, HeatCellBlockEntity.class);
        cell.setSideMode(RelativeSide.FRONT, SideMode.INPUT);
        heatTo(cell.getHeat(), 1_100);
        double fill = (double) cell.getHeat().getStored() / cell.getHeat().getCapacity();
        helper.assertTrue(fill > 0.83 && fill < 0.85, "A Hardened cell at 1,100°C is " + fill * 100 + "% full");
        // A little hotter, so a leak during the test can't cool it below the Firebox.
        cell.getHeat().add(cell.getHeat().getCapacity() / 100);
        firebox.getHeat().add(firebox.getHeat().getCapacity());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(firebox.getHeat().isFull(), "A 1,100°C Firebox pushed heat into a 1,100°C Hardened cell"))
                .thenSucceed();
    }

    // A full plant is at 1,400°C. Touching lava and magma give +40 and +16 HU/t each; the tank's lava +80 HU/t.
    static void geothermalRebalance(GameTestHelper helper) {
        BlockPos fullPos = new BlockPos(0, 1, 0);
        BlockPos passivePos = new BlockPos(3, 1, 0);
        BlockPos tankedPos = new BlockPos(8, 1, 0);
        helper.setBlock(fullPos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(passivePos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(tankedPos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(passivePos.east(), Blocks.LAVA);
        helper.setBlock(passivePos.below(), Blocks.MAGMA_BLOCK);
        GeothermalPlantBlockEntity full = helper.getBlockEntity(fullPos, GeothermalPlantBlockEntity.class);
        full.getHeat().add(full.getHeat().getCapacity());
        helper.assertTrue(full.getHeat().getCapacity() == 60_000, "Plant holds " + full.getHeat().getCapacity() + " HU");
        helper.assertTrue(full.getHeat().getTemperature() == 1_400, "Full plant is at " + full.getHeat().getTemperature() + "°C");
        insert(helper.getBlockEntity(tankedPos, GeothermalPlantBlockEntity.class).getItemHandler(null), Items.LAVA_BUCKET, 1);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    GeothermalPlantBlockEntity passive = helper.getBlockEntity(passivePos, GeothermalPlantBlockEntity.class);
                    GeothermalPlantBlockEntity tanked = helper.getBlockEntity(tankedPos, GeothermalPlantBlockEntity.class);
                    passive.getHeat().remove(passive.getHeat().getStored());
                    tanked.getHeat().remove(tanked.getHeat().getStored());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    int passive = helper.getBlockEntity(passivePos, GeothermalPlantBlockEntity.class).getHeat().getStored();
                    int tanked = helper.getBlockEntity(tankedPos, GeothermalPlantBlockEntity.class).getHeat().getStored();
                    // 10 ticks, give or take the tick the test runs on.
                    helper.assertTrue(passive >= 56 * 9 && passive <= 56 * 11, "1 lava + 1 magma made " + passive + " HU in 10 ticks, expected ~560");
                    helper.assertTrue(tanked >= 80 * 9 && tanked <= 80 * 11, "Draining lava made " + tanked + " HU in 10 ticks, expected ~800");
                })
                .thenSucceed();
    }

    // Efficiency rises past 100% above 1,100°C, to 115% at 1,400°C. A full Geothermal Plant heats a
    // Thermoelectric Plant past 1,100°C, and it then runs above 100%.
    static void thermoelectricBonus(GameTestHelper helper) {
        helper.assertTrue(ThermoelectricPlantBlockEntity.efficiency(1_100) == 1.0F, "Efficiency at 1,100°C is " + ThermoelectricPlantBlockEntity.efficiency(1_100));
        helper.assertTrue(Math.abs(ThermoelectricPlantBlockEntity.efficiency(1_250) - 1.075F) < 0.001F, "Efficiency at 1,250°C is " + ThermoelectricPlantBlockEntity.efficiency(1_250));
        helper.assertTrue(Math.abs(ThermoelectricPlantBlockEntity.efficiency(1_400) - 1.15F) < 0.001F, "Efficiency at 1,400°C is " + ThermoelectricPlantBlockEntity.efficiency(1_400));
        helper.assertTrue(Math.abs(ThermoelectricPlantBlockEntity.efficiency(1_600) - 1.15F) < 0.001F, "Efficiency at 1,600°C is " + ThermoelectricPlantBlockEntity.efficiency(1_600));

        BlockPos geoPos = new BlockPos(0, 1, 0);
        BlockPos plantPos = new BlockPos(0, 1, 1);
        helper.setBlock(geoPos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(plantPos, ModBlocks.THERMOELECTRIC_PLANT.get());
        GeothermalPlantBlockEntity geothermal = helper.getBlockEntity(geoPos, GeothermalPlantBlockEntity.class);
        ThermoelectricPlantBlockEntity plant = helper.getBlockEntity(plantPos, ThermoelectricPlantBlockEntity.class);
        plant.setSideMode(RelativeSide.FRONT, SideMode.HEAT);
        geothermal.getHeat().add(geothermal.getHeat().getCapacity());
        heatTo(plant.getHeat(), 1_100);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plant.getHeat().getTemperature() > 1_150,
                        "Plant only reached " + plant.getHeat().getTemperature() + "°C from a 1,400°C Geothermal Plant"))
                .thenExecute(() -> {
                    helper.assertTrue(plant.upgradedEfficiency() > 1.0F, "Plant at " + plant.getHeat().getTemperature() + "°C runs at " + plant.upgradedEfficiency());
                    helper.assertTrue(plant.getFePerTick() > 80, "Plant at " + plant.getHeat().getTemperature() + "°C makes only " + plant.getFePerTick() + " FE/t");
                })
                .thenSucceed();
    }
}
