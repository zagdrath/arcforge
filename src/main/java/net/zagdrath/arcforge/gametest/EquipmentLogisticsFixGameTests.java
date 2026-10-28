/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.item.tool.JetpackFlight;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.quantum.QuantumFaces;
import net.zagdrath.arcforge.quantum.QuantumFrequencies;
import net.zagdrath.arcforge.quantum.QuantumResource;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

public final class EquipmentLogisticsFixGameTests {
    private EquipmentLogisticsFixGameTests() {}

    static void typedUpgrades(GameTestHelper helper) {
        MachineItemHandler items = new MachineItemHandler(2, (slot, resource) -> true, Set.of(UpgradeType.SPEED, UpgradeType.ENERGY), () -> {});
        int speed = items.slotFor(UpgradeType.SPEED), energy = items.slotFor(UpgradeType.ENERGY);
        helper.assertTrue(items.getUpgradeTypes().size() == 2, "Wrong number of supported types");
        helper.assertTrue(items.isValid(speed, ItemResource.of(ModItems.SPEED_UPGRADE.get()))
                && !items.isValid(speed, ItemResource.of(ModItems.ENERGY_UPGRADE.get())), "Speed slot accepts the wrong type");
        helper.assertFalse(items.isValid(items.getFirstUpgradeSlot() + 3, ItemResource.of(ModItems.SPEED_UPGRADE.get())), "Spare legacy slot accepts upgrades");
        // An old save's arbitrary placement is reordered without losing upgrades or machine inventory.
        items.setStack(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 12));
        items.setStack(speed + 2, new ItemStack(ModItems.SPEED_UPGRADE.get(), 8));
        items.setStack(speed + 3, new ItemStack(ModItems.ENERGY_UPGRADE.get(), 5));
        items.normalizeUpgrades();
        helper.assertTrue(items.getStack(0).getCount() == 12 && items.getStack(speed).getCount() == 8
                && items.getStack(energy).getCount() == 5, "Migration lost inventory or upgrades");
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.METAL_PRESS.get());
        var press = helper.getBlockEntity(new BlockPos(1, 1, 1), net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity.class);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (net.zagdrath.arcforge.menu.machine.MachineMenu) press.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu.getUpgradeSlots().size() == press.getItems().getAcceptedUpgrades().size(), "Menu still shows unused upgrade slots");
        helper.assertTrue(menu.getUpgradeSlots().stream().allMatch(slot -> slot.upgradeType() != null && slot.getNoItemIcon() != null), "Missing typed placeholder");
        helper.succeed();
    }

    static void quantumPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1), sink = pos.east();
        helper.setBlock(pos, ModBlocks.QUANTUM_TUNNEL.get());
        helper.setBlock(sink, Blocks.LODESTONE);
        var level = helper.getLevel();
        var tunnel = helper.getBlockEntity(pos, QuantumTunnelBlockEntity.class);
        var frequencies = QuantumFrequencies.get(level.getServer());
        var key = Frequency.Key.ofPublic("power-" + UUID.randomUUID().toString().substring(0, 12));
        frequencies.create(key, UUID.randomUUID(), "tester");
        tunnel.setKey(key);
        tunnel.setMode(Direction.EAST, QuantumResource.ENERGY, QuantumFaces.OUTPUT);
        TestFixtures.reset(helper.absolutePos(sink));
        try {
            try (Transaction tx = Transaction.openRoot()) {
                tunnel.frequency().energy().insert(2_000_000, tx);
                tx.commit();
            }
            tunnel.serverTick(level, helper.absolutePos(pos));
            helper.assertTrue(TestFixtures.energy(helper.absolutePos(sink)).getAmountAsLong() == TestFixtures.ENERGY_CAPACITY,
                    "Tunnel output is still capped below a full 1,000,000 FE transfer");
        } finally {
            frequencies.remove(key);
            TestFixtures.reset(helper.absolutePos(sink));
        }
        helper.succeed();
    }

    static void quarryNether(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "No Nether dimension");
        BlockPos pos = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), nether.getMaxY() - 2,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        nether.getChunkAt(pos);
        var previous = nether.getBlockState(pos);
        try {
            nether.setBlockAndUpdate(pos, ModBlocks.ARC_QUARRY.get().defaultBlockState());
            var quarry = (ArcQuarryBlockEntity) nether.getBlockEntity(pos);
            quarry.setSettings(new QuarrySettings(0, -64, 319, List.of(), false, false, false, false));
            quarry.scanOnly();
            quarry.serverTick(nether, pos, quarry.getBlockState());
            helper.assertTrue(quarry.getSettings().minY() >= nether.getMinY() && quarry.getSettings().maxY() <= nether.getMaxY(),
                    "Saved Overworld range was not clamped for the Nether");
        } finally {
            nether.setBlockAndUpdate(pos, previous);
        }
        helper.succeed();
    }

    static void curioJetpack(GameTestHelper helper) {
        var player = TestFixtures.player(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
        helper.assertTrue(inventory.getCurios().containsKey("jetpack"), "Dedicated jetpack slot is missing");
        ItemStack pack = new ItemStack(ModItems.TEMPERED_JETPACK.get());
        JetpackItem.setFluid(pack, new net.neoforged.neoforge.fluids.FluidStack(net.zagdrath.arcforge.registry.ModFluids.HYDROGEN.get(), 1_000));
        inventory.setEquippedCurio("jetpack", 0, pack);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STEEL_CHESTPLATE.get()));
        helper.assertTrue(JetpackFlight.worn(player).getItem() instanceof JetpackItem, "Chest armor hides Curios jetpack");
        player.setOnGround(false);
        helper.assertTrue(JetpackFlight.tick(player, true, false, false) > 0 && player.getDeltaMovement().y > 0, "Curios jetpack does not fly");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.STEEL_CHESTPLATE.get()), "Flight replaced chest armor");
        helper.succeed();
    }
}
