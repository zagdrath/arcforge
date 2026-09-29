/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.logistics.ChargepadBlock;
import net.zagdrath.arcforge.block.logistics.MeterBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChargepadBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.meter.MeterSettings;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Conduit Cover, the Meters and the Chargepad.
public final class LogisticsGameTests {
    private LogisticsGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos standRelative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(standRelative);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    // An Energy Cell at pos with every side set to `mode`, holding `energy` FE.
    private static EnergyCellBlockEntity cell(GameTestHelper helper, BlockPos pos, SideMode mode, int energy) {
        helper.setBlock(pos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        for (RelativeSide side : RelativeSide.values()) {
            cell.setSideMode(side, mode);
        }
        // A cell takes at most its rate a call, so fill it a call at a time.
        int left = energy;
        while (left > 0) {
            int inserted;
            try (Transaction tx = Transaction.openRoot()) {
                inserted = cell.getEnergyHandler(null).insert(left, tx);
                tx.commit();
            }
            if (inserted <= 0) {
                break;
            }
            left -= inserted;
        }
        return cell;
    }

    // Each conduit's connection on each side (not its lit state, which follows the flow).
    private static List<ConnectionMode> connections(GameTestHelper helper, List<BlockPos> positions) {
        List<ConnectionMode> modes = new ArrayList<>();
        for (BlockPos pos : positions) {
            for (Direction side : Direction.values()) {
                modes.add(ConduitBlock.mode(helper.getBlockState(pos), side));
            }
        }
        return modes;
    }

    // --- Conduit Cover ---

    // Covering the middle of three energy conduits changes none of their connections, and the energy still flows.
    static void coverKeepsConnections(GameTestHelper helper) {
        cell(helper, new BlockPos(0, 1, 1), SideMode.OUTPUT, 50_000);
        EnergyCellBlockEntity sink = cell(helper, new BlockPos(4, 1, 1), SideMode.INPUT, 0);
        List<BlockPos> line = List.of(new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1));
        line.forEach(pos -> helper.setBlock(pos, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get()));
        line.forEach(pos -> ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(pos)));
        List<ConnectionMode> before = new ArrayList<>();
        int[] received = new int[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    before.addAll(connections(helper, line));
                    helper.getBlockEntity(line.get(1), ConduitBlockEntity.class).setCover(ConduitBlockEntity.Cover.PLAIN);
                    received[0] = sink.getEnergy();
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(connections(helper, line).equals(before), "Covering the conduit changed its connections");
                    helper.assertTrue(sink.getEnergy() > received[0], "No energy flowed through the covered conduit");
                    helper.assertTrue(helper.getBlockState(line.get(1)).getShape(helper.getLevel(), helper.absolutePos(line.get(1)))
                            .bounds().getSize() >= 1.0, "The covered conduit isn't a full block");
                })
                .thenSucceed();
    }

    // A look copied onto a cover survives saving and loading the block entity.
    static void coverLookPersists(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        ConduitBlockEntity conduit = helper.getBlockEntity(pos, ConduitBlockEntity.class);
        conduit.setCover(new ConduitBlockEntity.Cover(Optional.of(Blocks.STONE.defaultBlockState())));
        var registries = helper.getLevel().registryAccess();
        var saved = conduit.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), conduit.getBlockState(), saved, registries);
        helper.assertTrue(loaded instanceof ConduitBlockEntity copy && copy.getCover() != null
                && copy.getCover().look().map(look -> look.is(Blocks.STONE)).orElse(false), "The stone look didn't survive a reload");
        helper.succeed();
    }

    // Only full, solid blocks can be copied onto a cover: stone yes; a slab or a glass pane no.
    static void coverRejectsNonFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        ConduitBlockEntity conduit = helper.getBlockEntity(pos, ConduitBlockEntity.class);
        conduit.setCover(ConduitBlockEntity.Cover.PLAIN);
        BlockPos at = helper.absolutePos(pos);
        helper.assertTrue(ConduitBlock.isFullSolidBlock(Blocks.STONE.defaultBlockState(), helper.getLevel(), at), "Stone can't be copied");
        helper.assertFalse(ConduitBlock.isFullSolidBlock(Blocks.STONE_SLAB.defaultBlockState(), helper.getLevel(), at), "A slab can be copied");
        helper.assertFalse(ConduitBlock.isFullSolidBlock(Blocks.GLASS_PANE.defaultBlockState(), helper.getLevel(), at), "A glass pane can be copied");
        ServerPlayer player = player(helper, new BlockPos(0, 1, 1));
        ItemStack slab = new ItemStack(Items.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, slab);
        player.gameMode.useItemOn(player, helper.getLevel(), slab, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.UP, at, false));
        helper.assertTrue(conduit.getCover() != null && conduit.getCover().look().isEmpty(), "A slab set the cover's look");
        helper.succeed();
    }

    // The Wrench (Dismantle, sneaking) takes the cover off first, giving it back, and leaves the conduit.
    static void coverDismantleReturnsItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        ConduitBlockEntity conduit = helper.getBlockEntity(pos, ConduitBlockEntity.class);
        conduit.setCover(ConduitBlockEntity.Cover.PLAIN);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 1));
        player.setShiftKeyDown(true);
        PortGameTests.wrench(helper, player, pos, Direction.UP, WrenchMode.DISMANTLE);
        helper.assertTrue(!conduit.hasCover(), "The cover is still on");
        helper.assertBlockPresent(ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get(), pos);
        helper.assertItemEntityPresent(ModItems.CONDUIT_COVER.get(), pos, 2.0);
        helper.succeed();
    }

    // --- Meters ---

    private static MeterBlockEntity meter(GameTestHelper helper, BlockPos pos, MeterBlock block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(MeterBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, MeterBlockEntity.class);
    }

    // Facing north, a meter's left (input) is east and its right (output) west. An Energy Meter between two cells
    // passes FE and reads a rate; its signal follows the threshold.
    static void meterEnergyPassesAndReads(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 1);
        MeterBlockEntity meter = meter(helper, pos, ModBlocks.ENERGY_METER.get());
        EnergyCellBlockEntity source = cell(helper, pos.east(), SideMode.OUTPUT, 100_000);
        EnergyCellBlockEntity sink = cell(helper, pos.west(), SideMode.INPUT, 0);
        int[] rate = new int[1];
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    rate[0] = meter.getRate();
                    helper.assertTrue(rate[0] > 0, "The meter reads no flow (source " + source.getEnergy() + " FE, sink " + sink.getEnergy()
                            + " FE, input " + (helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.EAST) != null) + ")");
                    helper.assertTrue(sink.getEnergy() > 0, "No FE reached the cell on the right");
                    meter.setSettings(new MeterSettings(Math.max(0, rate[0] - 1), MeterSettings.Mode.ABOVE));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(pos).getValue(MeterBlock.POWERED), "Not signalling above rate - 1");
                    helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(pos.above()), Direction.UP) == 15
                            || helper.getBlockState(pos).getSignal(helper.getLevel(), helper.absolutePos(pos), Direction.UP) == 15, "The signal isn't 15");
                    meter.setSettings(new MeterSettings(Math.max(0, rate[0] - 1), MeterSettings.Mode.BELOW));
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertFalse(helper.getBlockState(pos).getValue(MeterBlock.POWERED), "Signalling below rate - 1"))
                .thenSucceed();
    }

    // A Fluid Meter takes liquids on its left, not gases.
    static void meterFluidRejectsGas(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        MeterBlockEntity meter = meter(helper, pos, ModBlocks.FLUID_METER.get());
        var left = meter.getFluidHandler(Direction.EAST);
        helper.assertTrue(left != null, "No fluid input on the left");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(left.insert(SteamGrade.STEAM.resource(), 100, tx) == 0, "The Fluid Meter took steam");
            helper.assertTrue(left.insert(FluidResource.of(Fluids.WATER), 100, tx) == 100, "The Fluid Meter refused water");
        }
        helper.assertTrue(meter.getFluidHandler(Direction.UP) == null, "The top takes fluids");
        helper.succeed();
    }

    // Hydrogen from a cylinder through a Pressurized Conduit and a Gas Meter reaches a cylinder on the meter's right.
    static void meterGasViaPressurizedConduit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 1);
        meter(helper, pos, ModBlocks.GAS_METER.get());
        BlockPos conduit = pos.east();
        BlockPos source = conduit.east();
        BlockPos target = pos.west();
        helper.setBlock(source, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.setBlock(target, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        PressurizedCylinderBlockEntity in = helper.getBlockEntity(source, PressurizedCylinderBlockEntity.class);
        in.getTank().set(0, FluidResource.of(ModFluids.HYDROGEN.get()), 4_000);
        for (RelativeSide side : RelativeSide.values()) {
            in.setSideMode(side, SideMode.OUTPUT);
        }
        helper.setBlock(conduit, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
        ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(conduit));
        PressurizedCylinderBlockEntity out = helper.getBlockEntity(target, PressurizedCylinderBlockEntity.class);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(out.getGas().getAmount() > 0, "No hydrogen reached the cylinder on the right"))
                .thenExecute(() -> helper.assertTrue(out.getGas().getFluid() == ModFluids.HYDROGEN.get(), "Something other than hydrogen arrived"))
                .thenSucceed();
    }

    // Comparators read the rate against the cap: half the cap reads 8; no flow 0; any flow at least 1.
    static void meterComparator(GameTestHelper helper) {
        int cap = net.zagdrath.arcforge.machine.meter.MeterKind.ENERGY.cap();
        helper.assertTrue(MeterBlockEntity.comparatorSignal(cap / 2, cap) == 8, "Half the cap reads " + MeterBlockEntity.comparatorSignal(cap / 2, cap));
        helper.assertTrue(MeterBlockEntity.comparatorSignal(0, cap) == 0, "No flow doesn't read 0");
        helper.assertTrue(MeterBlockEntity.comparatorSignal(1, cap) == 1, "A trickle doesn't read 1");
        helper.assertTrue(MeterBlockEntity.comparatorSignal(cap, cap) == 15, "The cap doesn't read 15");
        helper.succeed();
    }

    // Breaking a meter keeps its threshold and mode on the item.
    static void meterSettingsSurviveBreak(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        MeterBlockEntity meter = meter(helper, pos, ModBlocks.HEAT_METER.get());
        MeterSettings settings = new MeterSettings(1_234, MeterSettings.Mode.BELOW);
        meter.setSettings(settings);
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2.0));
        ItemStack drop = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(ModItems.HEAT_METER.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertFalse(drop.isEmpty(), "The meter didn't drop");
        helper.assertTrue(settings.equals(drop.get(ModDataComponents.METER_SETTINGS.get())), "The drop has " + drop.get(ModDataComponents.METER_SETTINGS.get()));
        helper.succeed();
    }

    // --- Chargepad ---

    private static ChargepadBlockEntity pad(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, ModBlocks.CHARGEPAD.get().defaultBlockState().setValue(ChargepadBlock.FACING, Direction.NORTH));
        ChargepadBlockEntity pad = helper.getBlockEntity(pos, ChargepadBlockEntity.class);
        pad.getEnergy().load(energy);
        return pad;
    }

    private static long stored(ItemStack stack) {
        EnergyHandler handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
        return handler != null ? handler.getAmountAsLong() : 0;
    }

    // Standing on a full pad charges the held Arc Drill and a Battery in the hotbar, within 8,192 FE a tick.
    static void chargepadChargesPlayerItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        pad(helper, pos, 500_000);
        ServerPlayer player = player(helper, pos);
        BlockPos at = helper.absolutePos(pos);
        player.snapTo(at.getX() + 0.5, at.getY() + 0.125, at.getZ() + 0.4);
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(ModItems.TEMPERED_ARC_DRILL.get()));
        player.getInventory().setItem(5, new ItemStack(ModItems.portable(PortableStorageItem.Kind.BATTERY, ConduitTier.TEMPERED)));
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    long drill = stored(player.getInventory().getItem(0));
                    long battery = stored(player.getInventory().getItem(5));
                    helper.assertTrue(drill > 0, "The held Arc Drill wasn't charged");
                    helper.assertTrue(battery > 0, "The Battery in the hotbar wasn't charged");
                    helper.assertTrue(drill + battery <= 21L * 8_192, "Charged " + (drill + battery) + " FE in 20 ticks");
                })
                .thenSucceed();
    }

    // FE goes in only through the back: a conduit on a side doesn't connect, one on the back connects and fills it.
    static void chargepadBackOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        ChargepadBlockEntity pad = pad(helper, pos, 0);
        helper.assertTrue(pad.getConduitConnection(Direction.EAST, ConduitType.ENERGY) == ConnectionMode.NONE, "A side face connects");
        helper.assertTrue(pad.getEnergyHandler(Direction.EAST) == null, "A side face takes FE");
        helper.assertTrue(pad.getConduitConnection(Direction.SOUTH, ConduitType.ENERGY) == ConnectionMode.INPUT, "The back doesn't connect");
        BlockPos conduit = pos.south();
        cell(helper, conduit.south(), SideMode.OUTPUT, 100_000);
        helper.setBlock(conduit, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(conduit));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pad.getEnergy().getAmountAsInt() > 0, "No FE reached the pad through its back"))
                .thenSucceed();
    }

    // With nobody on it the pad isn't charging, and its gauge follows the FE it holds.
    static void chargepadIdleState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        pad(helper, pos, 250_000);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    BlockState state = helper.getBlockState(pos);
                    helper.assertFalse(state.getValue(ChargepadBlock.CHARGING), "Charging with nobody on it");
                    helper.assertTrue(state.getValue(ChargepadBlock.CHARGE) == 2, "Half full shows " + state.getValue(ChargepadBlock.CHARGE));
                })
                .thenSucceed();
    }
}
