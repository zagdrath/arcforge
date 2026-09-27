/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// Conduit behaviour: transfer for every type, conservation when networks split, and the wrench.
// Layouts run along +X at y=1: source at x=0, conduits, sink at the end.
final class ConduitGameTests {
    private ConduitGameTests() {}

    // --- Helpers ---

    // Places a straight run of conduits from x=1 to x=length, then connects them as a player placement would.
    private static void placeRun(GameTestHelper helper, ConduitType type, ConduitTier tier, int length) {
        for (int x = 1; x <= length; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), ModBlocks.conduit(type, tier).get());
        }
        for (int x = 1; x <= length; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 0)));
        }
    }

    // Sets a conduit side the way the wrench would.
    private static void setPort(GameTestHelper helper, BlockPos pos, Direction side, ConnectionMode mode) {
        BlockPos absolute = helper.absolutePos(pos);
        var state = helper.getLevel().getBlockState(absolute);
        helper.getLevel().setBlock(absolute, state.setValue(ConduitBlock.property(side), mode), 3);
        ConduitNetworkManager.get(helper.getLevel()).markDirty(absolute);
    }

    private static int fluidIn(FluidStacksResourceHandler tank) {
        return tank.getAmountAsInt(0);
    }

    private static int fluidInConduits(GameTestHelper helper, int length) {
        int total = 0;
        for (int x = 1; x <= length; x++) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 0))) instanceof ConduitBlockEntity conduit) {
                total += conduit.getFluid().getAmount();
            }
        }
        return total;
    }

    private static void useWrench(GameTestHelper helper, Player player, BlockPos pos, Vec3 offsetFromCentre) {
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(absolute).add(offsetFromCentre);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, absolute, false));
        wrench.getItem().onItemUseFirst(wrench, context);
    }

    // --- Tests ---

    // FE moves from source to sink at no more than the tier rate, is conserved, and the conduits
    // light up while it flows and go dark shortly after.
    static void energyTransfer(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(source, Blocks.LODESTONE);
        helper.setBlock(sink, Blocks.LODESTONE);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, ConnectionMode.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, ConnectionMode.INPUT);
        var from = TestFixtures.energy(helper.absolutePos(source));
        var to = TestFixtures.energy(helper.absolutePos(sink));
        from.set(2_000);
        BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 0));
        long[] last = { helper.getLevel().getGameTime(), 0 };

        // Checks may not land on every game tick, so compare against the ticks that actually elapsed.
        helper.onEachTick(() -> {
            int total = from.getAmountAsInt() + to.getAmountAsInt();
            helper.assertTrue(total == 2_000, "Energy not conserved: " + total);
            long now = helper.getLevel().getGameTime();
            long moved = to.getAmountAsInt() - last[1];
            long allowed = ConduitTier.WROUGHT.energyPerTick() * Math.max(1, now - last[0]);
            helper.assertTrue(moved <= allowed, "Moved " + moved + " FE in " + (now - last[0]) + " ticks");
            last[0] = now;
            last[1] = to.getAmountAsInt();
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockState(middle).getValue(ActiveConduitBlock.ACTIVE), "Conduit not lit while flowing"))
                .thenWaitUntil(() -> helper.assertTrue(to.getAmountAsInt() == 2_000, "Sink has " + to.getAmountAsInt()))
                .thenIdle(15)
                .thenExecute(() -> helper.assertFalse(helper.getLevel().getBlockState(middle).getValue(ActiveConduitBlock.ACTIVE), "Conduit still lit after flow stopped"))
                .thenSucceed();
    }

    // Items travel from one chest to another; none are lost or dropped.
    static void itemTransfer(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(sink, Blocks.CHEST);
        placeRun(helper, ConduitType.ITEM, ConduitTier.ARCFORGED, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, ConnectionMode.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, ConnectionMode.INPUT);
        ChestBlockEntity from = helper.getBlockEntity(source, ChestBlockEntity.class);
        ChestBlockEntity to = helper.getBlockEntity(sink, ChestBlockEntity.class);
        from.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        from.setItem(1, new ItemStack(Items.COBBLESTONE, 36));
        from.setItem(2, new ItemStack(Items.DIRT, 5));

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(from.isEmpty(), "Source chest not empty yet");
                    helper.assertTrue(countItem(to, Items.COBBLESTONE) == 100 && countItem(to, Items.DIRT) == 5,
                            "Sink has " + countItem(to, Items.COBBLESTONE) + " cobblestone, " + countItem(to, Items.DIRT) + " dirt");
                })
                .thenExecute(() -> helper.assertTrue(helper.getEntities(EntityTypes.ITEM).isEmpty(), "Items were dropped into the world"))
                .thenSucceed();
    }

    private static int countItem(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                count += chest.getItem(slot).getCount();
            }
        }
        return count;
    }

    // Liquid moves from one tank to another; source + conduits + sink always add up.
    static void liquidTransfer(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(source, Blocks.TARGET);
        helper.setBlock(sink, Blocks.TARGET);
        placeRun(helper, ConduitType.LIQUID, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, ConnectionMode.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, ConnectionMode.INPUT);
        var from = TestFixtures.tank(helper.absolutePos(source));
        var to = TestFixtures.tank(helper.absolutePos(sink));
        from.set(0, FluidResource.of(Fluids.WATER), 4_000);

        helper.onEachTick(() -> {
            int total = fluidIn(from) + fluidIn(to) + fluidInConduits(helper, 3);
            helper.assertTrue(total == 4_000, "Liquid not conserved: " + total + " mB");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fluidIn(to) == 4_000, "Sink has " + fluidIn(to) + " mB"))
                .thenExecute(() -> helper.assertTrue(fluidInConduits(helper, 3) == 0, "Conduits still hold liquid"))
                .thenSucceed();
    }

    // Breaking a conduit in a filled network splits it in two without creating or losing liquid.
    static void liquidSplit(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        helper.setBlock(source, Blocks.TARGET);
        placeRun(helper, ConduitType.LIQUID, ConduitTier.ARCFORGED, 5);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, ConnectionMode.OUTPUT);
        var from = TestFixtures.tank(helper.absolutePos(source));
        from.set(0, FluidResource.of(Fluids.LAVA), 3_000);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fluidInConduits(helper, 5) == 3_000, "Network holds " + fluidInConduits(helper, 5) + " mB"))
                .thenExecute(() -> helper.destroyBlock(new BlockPos(3, 1, 0)))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(fluidInConduits(helper, 5) == 3_000, "After the split the conduits hold " + fluidInConduits(helper, 5) + " mB");
                    int left = fluidAt(helper, 1) + fluidAt(helper, 2);
                    int right = fluidAt(helper, 4) + fluidAt(helper, 5);
                    helper.assertTrue(left == 1_500 && right == 1_500, "Split unevenly: " + left + " / " + right);
                })
                .thenSucceed();
    }

    private static int fluidAt(GameTestHelper helper, int x) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 0))) instanceof ConduitBlockEntity conduit
                ? conduit.getFluid().getAmount() : 0;
    }

    // The wrench cycles a machine-facing side none -> input -> output -> none (backwards when sneaking),
    // and a conduit-to-conduit joint stays disconnected after neighbour updates.
    static void wrenchConduit(GameTestHelper helper) {
        BlockPos plant = new BlockPos(0, 1, 0);
        helper.setBlock(plant, ModBlocks.GEOTHERMAL_PLANT.get());
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 2);
        BlockPos first = new BlockPos(1, 1, 0);
        BlockPos second = new BlockPos(2, 1, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 westArm = new Vec3(-0.4, 0, 0);
        Vec3 eastArm = new Vec3(0.4, 0, 0);

        // The plant faces north, so its east face is its left side, which defaults to energy output.
        ConnectionMode[] expected = { ConnectionMode.INPUT, ConnectionMode.OUTPUT, ConnectionMode.NONE };
        for (ConnectionMode mode : expected) {
            useWrench(helper, player, first, westArm);
            helper.assertTrue(ConduitBlock.mode(helper.getBlockState(first), Direction.WEST) == mode,
                    "West side is " + ConduitBlock.mode(helper.getBlockState(first), Direction.WEST) + ", expected " + mode);
        }
        player.setShiftKeyDown(true);
        useWrench(helper, player, first, westArm);
        helper.assertTrue(ConduitBlock.mode(helper.getBlockState(first), Direction.WEST) == ConnectionMode.OUTPUT, "Sneak-use did not cycle backwards");
        player.setShiftKeyDown(false);

        useWrench(helper, player, first, eastArm);
        helper.assertTrue(ConduitBlock.mode(helper.getBlockState(first), Direction.EAST) == ConnectionMode.NONE
                && ConduitBlock.mode(helper.getBlockState(second), Direction.WEST) == ConnectionMode.NONE, "Joint did not disconnect on both sides");
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.STONE);
        helper.assertTrue(ConduitBlock.mode(helper.getBlockState(first), Direction.EAST) == ConnectionMode.NONE,
                "Joint reconnected after a neighbour update");
        useWrench(helper, player, first, eastArm);
        helper.assertTrue(ConduitBlock.mode(helper.getBlockState(first), Direction.EAST) == ConnectionMode.PIPE
                && ConduitBlock.mode(helper.getBlockState(second), Direction.WEST) == ConnectionMode.PIPE, "Joint did not reconnect");
        helper.succeed();
    }

    // The wrench rotates machines and dismantles them into an item that keeps their contents.
    static void wrenchMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            plant.getInteractionFluidHandler().insert(FluidResource.of(Fluids.LAVA), 3_000, tx);
            tx.commit();
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        useWrench(helper, player, pos, new Vec3(0, 0.5, 0));
        helper.assertTrue(helper.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "Plant did not rotate clockwise");

        player.setShiftKeyDown(true);
        useWrench(helper, player, pos, new Vec3(0, 0.5, 0));
        helper.assertBlockNotPresent(ModBlocks.GEOTHERMAL_PLANT.get(), pos);
        var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 1, "Expected exactly one drop, got " + drops.size());
        ItemStack dropped = drops.getFirst().getItem();
        helper.assertTrue(dropped.has(DataComponents.BLOCK_ENTITY_DATA), "Dropped machine has no saved data");

        // Place it back: the lava tank comes back too.
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, helper.absolutePos(pos), dropped);
        GeothermalPlantBlockEntity restored = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
        int lava = restored.getInteractionFluidHandler().getAmountAsInt(0);
        helper.assertTrue(lava == 3_000, "Restored plant has " + lava + " mB of lava");
        helper.succeed();
    }
}
