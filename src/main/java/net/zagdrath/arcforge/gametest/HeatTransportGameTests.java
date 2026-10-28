/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.logistics.HeatPipeBlock;
import net.zagdrath.arcforge.blockentity.logistics.HeatPipeBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.output.OutputLimit;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;

// Moving heat between big machines: the Thermodynamic Conduit rates, the Heat Pipe, and the "Limited by connections"
// warning. Heat cells are placed without a player, so they face north: their left side is east, their right side west.
public final class HeatTransportGameTests {
    private HeatTransportGameTests() {}

    // --- Conduit rates ---

    // The Arcforged conduit carries 51,200 HU/t (and holds one tick's worth); the other tiers, the Heat Cells and the
    // Thermal Capsules keep their rates.
    static void thermalConduitRates(GameTestHelper helper) {
        int[] conduit = { 50, 200, 800, 51_200 };
        int[] cell = { 50, 200, 800, 3_200 };
        for (ConduitTier tier : ConduitTier.STANDARD) {
            helper.assertTrue(tier.heatPerTick() == conduit[tier.ordinal()], tier + " conduit carries " + tier.heatPerTick() + " HU/t");
            helper.assertTrue(tier.heatCellRate() == cell[tier.ordinal()], tier + " heat cell moves " + tier.heatCellRate() + " HU/t");
            helper.assertTrue(tier.thermalCapsuleRate() == cell[tier.ordinal()], tier + " capsule moves " + tier.thermalCapsuleRate() + " HU/t");
        }
        helper.succeed();
    }

    // --- Heat Pipe ---

    private static BlockState pipe(Direction.Axis axis) {
        return ModBlocks.HEAT_PIPE.get().defaultBlockState().setValue(HeatPipeBlock.AXIS, axis);
    }

    // A cell at x 0 (its east face set to give heat out), a pipe of length along X, and a cell after it (its west face takes
    // heat in by default). Returns the far cell's position.
    private static BlockPos pipeBetweenCells(GameTestHelper helper, int length, boolean hotNear) {
        BlockPos near = new BlockPos(0, 1, 0);
        BlockPos far = new BlockPos(length + 1, 1, 0);
        helper.setBlock(near, ModBlocks.heatCell(ConduitTier.ARCFORGED).get());
        helper.setBlock(far, ModBlocks.heatCell(ConduitTier.ARCFORGED).get());
        for (int x = 1; x <= length; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), pipe(Direction.Axis.X));
        }
        HeatCellBlockEntity nearCell = helper.getBlockEntity(near, HeatCellBlockEntity.class);
        HeatCellBlockEntity farCell = helper.getBlockEntity(far, HeatCellBlockEntity.class);
        nearCell.setSideMode(RelativeSide.LEFT, SideMode.OUTPUT);
        farCell.setSideMode(RelativeSide.RIGHT, SideMode.INPUT);
        HeatCellBlockEntity hot = hotNear ? nearCell : farCell;
        hot.getHeat().add(hot.getHeat().getCapacity());
        return far;
    }

    // A 10-block pipe takes heat from the hot cell and gives the cold one 95% of it, glowing while it does.
    static void heatPipeCarriesHeat(GameTestHelper helper) {
        BlockPos far = pipeBetweenCells(helper, 10, true);
        HeatCellBlockEntity hot = helper.getBlockEntity(new BlockPos(0, 1, 0), HeatCellBlockEntity.class);
        HeatCellBlockEntity cold = helper.getBlockEntity(far, HeatCellBlockEntity.class);
        AtomicLong hotStart = new AtomicLong();
        AtomicLong coldStart = new AtomicLong();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    hotStart.set(hot.getHeat().getStored());
                    coldStart.set(cold.getHeat().getStored());
                })
                .thenIdle(18)
                .thenExecute(() -> {
                    long drawn = hotStart.get() - hot.getHeat().getStored();
                    long delivered = cold.getHeat().getStored() - coldStart.get();
                    helper.assertTrue(delivered > 0, "The cold cell got no heat through the pipe");
                    helper.assertTrue(Math.abs(HeatPipeBlockEntity.efficiency(10) - 0.95) < 1.0E-9, "A 10-block pipe delivers "
                            + HeatPipeBlockEntity.efficiency(10));
                    // 95% over 10 blocks; the hot cell's own slow leak takes a little more.
                    double share = (double) delivered / Math.max(1, drawn);
                    helper.assertTrue(share > 0.935 && share < 0.955, "Delivered " + delivered + " of " + drawn + " HU (" + share + ")");
                    HeatPipeBlockEntity controller = helper.getBlockEntity(new BlockPos(1, 1, 0), HeatPipeBlockEntity.class);
                    helper.assertTrue(controller.getLength() == 10, "Pipe is " + controller.getLength() + " blocks long");
                    helper.assertTrue(controller.getRate() > 0 && controller.getCarrying() > 1_000, "Pipe shows " + controller.getRate()
                            + " HU/t at " + controller.getCarrying() + "°C");
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 0)).getValue(HeatPipeBlock.LIT), "The pipe isn't lit");
                    helper.assertTrue(helper.getBlockEntity(new BlockPos(10, 1, 0), HeatPipeBlockEntity.class).getGlowCelsius() > 1_000,
                            "The far end doesn't glow hot");
                })
                .thenSucceed();
    }

    // Heat only goes from the hotter end to the colder one: a hot cell that only takes heat in, at the far end, gets
    // nothing taken from it and gives nothing back.
    static void heatPipeOneWay(GameTestHelper helper) {
        BlockPos far = pipeBetweenCells(helper, 4, false);
        HeatCellBlockEntity cold = helper.getBlockEntity(new BlockPos(0, 1, 0), HeatCellBlockEntity.class);
        HeatCellBlockEntity hot = helper.getBlockEntity(far, HeatCellBlockEntity.class);
        AtomicLong start = new AtomicLong();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> start.set(hot.getHeat().getStored()))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(cold.getHeat().getStored() == 0, "Heat moved into the near cell through its output face");
                    // Only the leak (a few hundred HU a second): nothing drawn through its input face.
                    helper.assertTrue(start.get() - hot.getHeat().getStored() < 1_000, "Heat left the hot cell's input face");
                    HeatPipeBlockEntity controller = helper.getBlockEntity(new BlockPos(1, 1, 0), HeatPipeBlockEntity.class);
                    helper.assertTrue(controller.getRate() == 0, "Pipe shows " + controller.getRate() + " HU/t");
                })
                .thenSucceed();
    }

    // A pipe joins only at its ends: a hot cell against its side gives it nothing.
    static void heatPipeSidesDontConnect(GameTestHelper helper) {
        BlockPos cellPos = new BlockPos(0, 1, 0);
        BlockPos coldPos = new BlockPos(1, 1, 3);
        helper.setBlock(cellPos, ModBlocks.heatCell(ConduitTier.ARCFORGED).get());
        for (int z = 0; z < 3; z++) {
            helper.setBlock(new BlockPos(1, 1, z), pipe(Direction.Axis.Z));
        }
        helper.setBlock(coldPos, ModBlocks.heatCell(ConduitTier.ARCFORGED).get());
        HeatCellBlockEntity hot = helper.getBlockEntity(cellPos, HeatCellBlockEntity.class);
        HeatCellBlockEntity cold = helper.getBlockEntity(coldPos, HeatCellBlockEntity.class);
        hot.setSideMode(RelativeSide.LEFT, SideMode.OUTPUT);
        hot.getHeat().add(hot.getHeat().getCapacity());
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(cold.getHeat().getStored() == 0, "Heat went in through the pipe's side"))
                .thenSucceed();
    }

    // A straight line longer than heatPipe.maxLength splits into pipes of that length from its negative end.
    static void heatPipeSplitsLongLines(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), pipe(Direction.Axis.X));
        }
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    int before = ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.getAsInt();
                    try {
                        ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.set(3);
                        HeatPipeBlock.update(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 0)), Direction.Axis.X);
                        boolean[] neg = { true, false, false, true, false };
                        boolean[] pos = { false, false, true, false, true };
                        for (int x = 0; x < 5; x++) {
                            BlockState state = helper.getBlockState(new BlockPos(x, 1, 0));
                            helper.assertTrue(state.getValue(HeatPipeBlock.NEG_END) == neg[x] && state.getValue(HeatPipeBlock.POS_END) == pos[x],
                                    "Block " + x + " ends: " + state.getValue(HeatPipeBlock.NEG_END) + "/" + state.getValue(HeatPipeBlock.POS_END));
                        }
                    } finally {
                        ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.set(before);
                        HeatPipeBlock.update(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 0)), Direction.Axis.X);
                    }
                    BlockState first = helper.getBlockState(new BlockPos(0, 1, 0));
                    BlockState last = helper.getBlockState(new BlockPos(4, 1, 0));
                    helper.assertTrue(first.getValue(HeatPipeBlock.NEG_END) && !first.getValue(HeatPipeBlock.POS_END)
                            && last.getValue(HeatPipeBlock.POS_END) && !last.getValue(HeatPipeBlock.NEG_END), "The 5-block line isn't one pipe again");
                })
                .thenSucceed();
    }

    // --- "Limited by connections" ---

    // The detector on its own: on once the buffer stays full and output short for the delay; off when output catches up,
    // when idle, and when nothing is connected.
    static void outputLimitDetects(GameTestHelper helper) {
        OutputLimit limit = new OutputLimit(OutputLimit.Unit.HU);
        OutputLimit unconnected = new OutputLimit(OutputLimit.Unit.HU);
        OutputLimit idle = new OutputLimit(OutputLimit.Unit.HU);
        int delay = ArcforgeConfig.OUTPUT_LIMIT_DELAY.getAsInt();
        long drained = 0;
        for (int tick = 0; tick < delay + 60; tick++) {
            drained += 3_200;
            limit.tick(helper.getLevel(), true, () -> true, 95, 100, drained, 50_400);
            // With nothing connected nothing is given out.
            unconnected.tick(helper.getLevel(), true, () -> false, 95, 100, 0, 50_400);
            idle.tick(helper.getLevel(), false, () -> true, 95, 100, drained, 50_400);
        }
        OutputLimit.Shown shown = limit.shown();
        helper.assertTrue(shown != null, "Not limited after " + (delay + 60) + " ticks full and short");
        helper.assertTrue(Math.abs(shown.given() - 3_200) <= 5 && Math.abs(shown.could() - 50_400) <= 50,
                "Shows " + shown.given() + " / " + shown.could());
        helper.assertTrue(!unconnected.isLimited(), "Limited with nothing connected");
        helper.assertTrue(!idle.isLimited(), "Limited while idle");
        // Output catches up: the warning clears.
        for (int tick = 0; tick < 80; tick++) {
            drained += 50_400;
            limit.tick(helper.getLevel(), true, () -> true, 95, 100, drained, 50_400);
        }
        helper.assertTrue(!limit.isLimited(), "Still limited once output caught up");
        // Shown again only after the delay, and not while the buffer has room.
        for (int tick = 0; tick < delay + 60; tick++) {
            drained += 100;
            limit.tick(helper.getLevel(), true, () -> true, 50, 100, drained, 50_400);
        }
        helper.assertTrue(!limit.isLimited(), "Limited with a half-empty buffer");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static MachineMenu menuOf(GameTestHelper helper, FireboxBlockEntity firebox) {
        ServerPlayer player = TestFixtures.player(helper);
        return (MachineMenu) firebox.createMenu(1, player.getInventory(), player);
    }

    private static void addCoal(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<ItemResource> input = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(input != null, "Top face does not accept items");
        try (Transaction tx = Transaction.openRoot()) {
            input.insert(ItemResource.of(Items.COAL), 8, tx);
            tx.commit();
        }
    }

    // A full Firebox whose heat face feeds a Wrought Heat Cell (50 HU/t) is limited (80 HU/t); one with nothing on its heat
    // face isn't. The GUI's menu data and the line say so.
    static void fireboxLimitedByConnections(GameTestHelper helper) {
        BlockPos fed = new BlockPos(0, 1, 0);
        BlockPos alone = new BlockPos(3, 1, 0);
        helper.setBlock(fed, ModBlocks.FIREBOX.get());
        helper.setBlock(alone, ModBlocks.FIREBOX.get());
        // The firebox's back (south) is its heat face; the cell's front (north) gives heat out, so take it in there.
        helper.setBlock(fed.south(), ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        helper.getBlockEntity(fed.south(), HeatCellBlockEntity.class).setSideMode(RelativeSide.FRONT, SideMode.INPUT);
        FireboxBlockEntity limited = helper.getBlockEntity(fed, FireboxBlockEntity.class);
        FireboxBlockEntity free = helper.getBlockEntity(alone, FireboxBlockEntity.class);
        for (FireboxBlockEntity firebox : new FireboxBlockEntity[] { limited, free }) {
            firebox.getHeat().add(firebox.getHeat().getCapacity());
        }
        addCoal(helper, fed);
        addCoal(helper, alone);
        helper.startSequence()
                .thenIdle(ArcforgeConfig.OUTPUT_LIMIT_DELAY.getAsInt() + 80)
                .thenExecute(() -> {
                    OutputLimit.Shown shown = limited.getOutputLimit().shown();
                    helper.assertTrue(shown != null, "The fed firebox isn't limited");
                    helper.assertTrue(shown.unit() == OutputLimit.Unit.HU && shown.given() <= 55 && shown.could() >= 75,
                            "Shows " + shown.given() + " / " + shown.could() + " " + shown.unit());
                    helper.assertTrue(!free.getOutputLimit().isLimited(), "The firebox with nothing connected is limited");
                    OutputLimit.Shown inMenu = menuOf(helper, limited).getOutputLimit();
                    helper.assertTrue(inMenu != null && inMenu.could() == shown.could(), "The menu doesn't carry the warning");
                    helper.assertTrue(shown.line().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents line
                            && line.getKey().equals("gui.arcforge.output_limited") && line.getArgs().length == 3, "Line is " + shown.line());
                })
                .thenSucceed();
    }
}
