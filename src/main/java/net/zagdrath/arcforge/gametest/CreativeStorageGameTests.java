/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.SteamGrade;

// The creative storage blocks (see StorageBlock): set by the first thing put in, never run down, destroy more of the
// same, refuse anything else, clear on request, and drop nothing when broken.
final class CreativeStorageGameTests {
    private CreativeStorageGameTests() {}

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return moved;
        }
    }

    private static int extract(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return moved;
        }
    }

    // Broken, it drops nothing (and a tank spills nothing).
    private static void breaksToNothing(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertTrue(helper.getEntities(EntityTypes.ITEM, pos, 2.0).isEmpty(), "A broken creative block dropped something");
    }

    // A bucket's worth of water fills it for good: taking a million mB leaves it full, more water is destroyed, lava is
    // refused. Cleared, it takes lava. Broken, it drops nothing and spills nothing.
    static void fluidTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.CREATIVE_FLUID_TANK.get());
        FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
        ResourceHandler<FluidResource> handler = tank.getFluidHandler(null);
        int capacity = tank.getCapacity();
        helper.assertTrue(insert(handler, Fluids.WATER, 1_000) == 1_000, "The tank took no water");
        helper.assertTrue(tank.getFluid().getAmount() == capacity, "A bucket filled it to " + tank.getFluid().getAmount() + " mB");
        helper.assertTrue(extract(handler, Fluids.WATER, 1_000_000) == 1_000_000, "It gave less than a million mB");
        helper.assertTrue(insert(handler, Fluids.WATER, 5_000) == 5_000, "It refused more water");
        helper.assertTrue(tank.getFluid().getAmount() == capacity, "It holds " + tank.getFluid().getAmount() + " mB");
        helper.assertTrue(insert(handler, Fluids.LAVA, 1_000) == 0, "It took lava while set to water");
        tank.clearContents();
        helper.assertTrue(tank.getFluid().isEmpty(), "Cleared, it holds " + tank.getFluid().getHoverName().getString());
        helper.assertTrue(insert(handler, Fluids.LAVA, 1) == 1 && tank.getFluid().getFluid() == Fluids.LAVA, "Cleared, it took no lava");
        breaksToNothing(helper, pos);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).isEmpty(), "The broken tank spilled"))
                .thenSucceed();
    }

    // Set to Steam it gives any amount and stays full; water isn't a gas, so it's refused even when empty.
    static void pressurizedCylinder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.CREATIVE_PRESSURIZED_CYLINDER.get());
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(pos, PressurizedCylinderBlockEntity.class);
        ResourceHandler<FluidResource> handler = cylinder.getFluidHandler(null);
        Fluid steam = SteamGrade.STEAM.fluid();
        helper.assertTrue(insert(handler, Fluids.WATER, 1_000) == 0, "The cylinder took water");
        helper.assertTrue(insert(handler, steam, 10) == 10, "The cylinder took no steam");
        helper.assertTrue(extract(handler, steam, 1_000_000) == 1_000_000, "It gave less than a million mB");
        helper.assertTrue(cylinder.getGas().getAmount() == cylinder.getTank().getCapacity(), "It holds " + cylinder.getGas().getAmount() + " mB");
        cylinder.clearContents();
        helper.assertTrue(cylinder.getGas().isEmpty(), "Cleared, it still holds gas");
        breaksToNothing(helper, pos);
        helper.succeed();
    }

    // Always full: it gives any FE asked for and takes any FE put in, and shows all four segments lit.
    static void energyCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.CREATIVE_ENERGY_CELL.get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        EnergyHandler handler = cell.getEnergyHandler(null);
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(handler.extract(Integer.MAX_VALUE, tx) == Integer.MAX_VALUE, "It gave less than it was asked for");
            helper.assertTrue(handler.insert(5_000, tx) == 5_000, "It refused FE");
            tx.commit();
        }
        helper.assertTrue(cell.getEnergy() == cell.getCapacity(), "It holds " + cell.getEnergy() + " of " + cell.getCapacity() + " FE");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EnergyCellBlock.CHARGE) == 4, "Not all segments lit"))
                .thenExecute(() -> breaksToNothing(helper, pos))
                .thenSucceed();
    }

    // One cobblestone fills it for good: taking a hundred thousand leaves it full, more is destroyed, dirt is refused.
    // The GUI's Clear button empties it.
    static void vault(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.CREATIVE_VAULT.get());
        VaultBlockEntity vault = helper.getBlockEntity(pos, VaultBlockEntity.class);
        helper.assertTrue(vault.insert(new ItemStack(Items.COBBLESTONE)) == 1, "The vault took no cobblestone");
        helper.assertTrue(vault.getAmount() == vault.getCapacity(), "One cobblestone filled it to " + vault.getAmount());
        helper.assertTrue(vault.extract(64).getCount() == 64, "Taking a stack gave less");
        try (Transaction tx = Transaction.openRoot()) {
            ResourceHandler<ItemResource> handler = vault.getItemHandler(null);
            helper.assertTrue(handler.extract(ItemResource.of(Items.COBBLESTONE), 100_000, tx) == 100_000, "Automation took less than 100,000");
            helper.assertTrue(handler.insert(ItemResource.of(Items.COBBLESTONE), 640, tx) == 640, "It refused more cobblestone");
            helper.assertTrue(handler.insert(ItemResource.of(Items.DIRT), 1, tx) == 0, "It took dirt while set to cobblestone");
            tx.commit();
        }
        helper.assertTrue(vault.getAmount() == vault.getCapacity(), "It holds " + vault.getAmount());
        MachineMenuButtons.handle(ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), MachineMenuButtons.CLEAR_CONTENTS);
        helper.assertTrue(vault.getAmount() == 0 && vault.getTemplate().isEmpty(), "Cleared, it holds " + vault.getAmount() + " " + vault.getTemplate());
        breaksToNothing(helper, pos);
        helper.succeed();
    }
}
