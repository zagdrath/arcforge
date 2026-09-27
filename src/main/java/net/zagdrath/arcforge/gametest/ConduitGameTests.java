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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;

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
    private static void setPort(GameTestHelper helper, BlockPos pos, Direction side, SideSetting setting) {
        BlockPos absolute = helper.absolutePos(pos);
        helper.getBlockEntity(pos, ConduitBlockEntity.class).setSetting(side, setting);
        ConduitBlock.refreshConnections(helper.getLevel(), absolute);
        ConduitNetworkManager.get(helper.getLevel()).markDirty(absolute);
    }

    private static ConnectionMode sideOf(GameTestHelper helper, BlockPos pos, Direction side) {
        return ConduitBlock.mode(helper.getBlockState(pos), side);
    }

    // FE or HU held by the conduits of a straight run.
    private static int storedInConduits(GameTestHelper helper, int length) {
        int total = 0;
        for (int x = 1; x <= length; x++) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 0))) instanceof ConduitBlockEntity conduit) {
                total += conduit.getStored();
            }
        }
        return total;
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
        useWrench(helper, player, pos, offsetFromCentre, WrenchMode.CONFIGURE);
    }

    static void useWrench(GameTestHelper helper, Player player, BlockPos pos, Vec3 offsetFromCentre, WrenchMode mode) {
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(absolute).add(offsetFromCentre);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        wrench.set(ModDataComponents.WRENCH_MODE.get(), mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, absolute, false));
        wrench.getItem().onItemUseFirst(wrench, context);
    }

    // --- Tests ---

    // FE moves from source to sink at no more than the tier rate, is conserved (counting what the
    // conduits hold), and the conduits light up while it flows and go dark shortly after.
    static void energyTransfer(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(source, Blocks.LODESTONE);
        helper.setBlock(sink, Blocks.LODESTONE);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        var from = TestFixtures.energy(helper.absolutePos(source));
        var to = TestFixtures.energy(helper.absolutePos(sink));
        from.set(2_000);
        BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 0));
        long[] last = { helper.getLevel().getGameTime(), 0 };

        // Checks may not land on every game tick, so compare against the ticks that actually elapsed.
        helper.onEachTick(() -> {
            int total = from.getAmountAsInt() + to.getAmountAsInt() + storedInConduits(helper, 3);
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
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
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

    // Fluid moves from one tank to another; source + conduits + sink always add up.
    static void fluidTransfer(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(source, Blocks.TARGET);
        helper.setBlock(sink, Blocks.TARGET);
        placeRun(helper, ConduitType.FLUID, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        var from = TestFixtures.tank(helper.absolutePos(source));
        var to = TestFixtures.tank(helper.absolutePos(sink));
        from.set(0, FluidResource.of(Fluids.WATER), 4_000);

        helper.onEachTick(() -> {
            int total = fluidIn(from) + fluidIn(to) + fluidInConduits(helper, 3);
            helper.assertTrue(total == 4_000, "Fluid not conserved: " + total + " mB");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fluidIn(to) == 4_000, "Sink has " + fluidIn(to) + " mB"))
                .thenExecute(() -> helper.assertTrue(fluidInConduits(helper, 3) == 0, "Conduits still hold fluid"))
                .thenSucceed();
    }

    // Breaking a conduit in a filled network splits it in two without creating or losing fluid.
    static void fluidSplit(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        helper.setBlock(source, Blocks.TARGET);
        placeRun(helper, ConduitType.FLUID, ConduitTier.ARCFORGED, 5);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
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

    // A conduit placed next to a machine connects by itself, following the machine's side configuration.
    // The wrench then cycles that side auto -> input -> output -> disabled (backwards when sneaking),
    // and a conduit-to-conduit joint stays disconnected after neighbour updates.
    static void wrenchConduit(GameTestHelper helper) {
        BlockPos combustionPlant = new BlockPos(0, 1, 0);
        helper.setBlock(combustionPlant, ModBlocks.COMBUSTION_PLANT.get());
        helper.getBlockEntity(combustionPlant, CombustionPlantBlockEntity.class).setSideMode(RelativeSide.LEFT, SideMode.ENERGY);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 2);
        BlockPos first = new BlockPos(1, 1, 0);
        BlockPos second = new BlockPos(2, 1, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 westArm = new Vec3(-0.4, 0, 0);
        Vec3 eastArm = new Vec3(0.4, 0, 0);

        // The plant faces north, so its east face is its left side, set to energy output.
        helper.assertTrue(sideOf(helper, first, Direction.WEST) == ConnectionMode.OUTPUT,
                "Auto side is " + sideOf(helper, first, Direction.WEST) + ", expected OUTPUT");
        ConnectionMode[] expected = { ConnectionMode.INPUT, ConnectionMode.OUTPUT, ConnectionMode.NONE, ConnectionMode.OUTPUT };
        for (ConnectionMode mode : expected) {
            useWrench(helper, player, first, westArm);
            helper.assertTrue(sideOf(helper, first, Direction.WEST) == mode,
                    "West side is " + sideOf(helper, first, Direction.WEST) + ", expected " + mode);
        }
        helper.assertTrue(helper.getBlockEntity(first, ConduitBlockEntity.class).getSetting(Direction.WEST) == SideSetting.AUTO,
                "The wrench cycle did not return to auto");
        player.setShiftKeyDown(true);
        useWrench(helper, player, first, westArm);
        helper.assertTrue(helper.getBlockEntity(first, ConduitBlockEntity.class).getSetting(Direction.WEST) == SideSetting.DISABLED,
                "Sneak-use did not cycle backwards");
        player.setShiftKeyDown(false);

        useWrench(helper, player, first, eastArm);
        helper.assertTrue(sideOf(helper, first, Direction.EAST) == ConnectionMode.NONE
                && sideOf(helper, second, Direction.WEST) == ConnectionMode.NONE, "Joint did not disconnect on both sides");
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.STONE);
        helper.assertTrue(sideOf(helper, first, Direction.EAST) == ConnectionMode.NONE,
                "Joint reconnected after a neighbour update");
        useWrench(helper, player, first, eastArm);
        helper.assertTrue(sideOf(helper, first, Direction.EAST) == ConnectionMode.PIPE
                && sideOf(helper, second, Direction.WEST) == ConnectionMode.PIPE, "Joint did not reconnect");
        helper.succeed();
    }

    // With no wrench configuration, a combustion plant's energy side feeds a buffer at the other end, and changing
    // the plant's side configuration disconnects the conduit.
    static void autoConnect(GameTestHelper helper) {
        BlockPos plantPos = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(plantPos, ModBlocks.COMBUSTION_PLANT.get());
        helper.setBlock(sink, Blocks.LODESTONE);
        CombustionPlantBlockEntity plant = helper.getBlockEntity(plantPos, CombustionPlantBlockEntity.class);
        plant.setSideMode(RelativeSide.LEFT, SideMode.ENERGY);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 3);
        ((GeneratorEnergyHandler) plant.getEnergyHandler(null)).generate(5_000);
        var to = TestFixtures.energy(helper.absolutePos(sink));

        helper.assertTrue(sideOf(helper, new BlockPos(1, 1, 0), Direction.WEST) == ConnectionMode.OUTPUT, "Conduit does not pull from the plant");
        helper.assertTrue(sideOf(helper, new BlockPos(3, 1, 0), Direction.EAST) == ConnectionMode.INPUT, "Conduit does not push into the buffer");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(to.getAmountAsInt() == 5_000, "Sink has " + to.getAmountAsInt()))
                .thenExecute(() -> plant.setSideMode(RelativeSide.LEFT, SideMode.NONE))
                .thenExecute(() -> helper.assertTrue(sideOf(helper, new BlockPos(1, 1, 0), Direction.WEST) == ConnectionMode.NONE,
                        "Conduit still connected to a face set to none"))
                .thenSucceed();
    }

    // A conduit added to a lit network lights up too (merged networks used to keep unlit members dark).
    static void glowExtends(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(source, Blocks.LODESTONE);
        helper.setBlock(sink, Blocks.LODESTONE);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        TestFixtures.energy(helper.absolutePos(source)).set(1_000_000);
        BlockPos branch = new BlockPos(2, 1, 1);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 0)).getValue(ActiveConduitBlock.ACTIVE), "Network not lit"))
                .thenExecute(() -> {
                    helper.setBlock(branch, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
                    ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(branch));
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(branch).getValue(ActiveConduitBlock.ACTIVE), "New conduit on a lit network is dark"))
                .thenSucceed();
    }

    // Energy conduits pull from a source and hold it even with nowhere to send it; the rest waits.
    static void energyStorage(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        TestFixtures.reset(helper.absolutePos(source));
        helper.setBlock(source, Blocks.LODESTONE);
        placeRun(helper, ConduitType.ENERGY, ConduitTier.WROUGHT, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        var from = TestFixtures.energy(helper.absolutePos(source));
        from.set(10_000);
        int capacity = 3 * ConduitTier.WROUGHT.energyPerTick();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storedInConduits(helper, 3) == capacity, "Conduits hold " + storedInConduits(helper, 3)))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(from.getAmountAsInt() == 10_000 - capacity, "Source has " + from.getAmountAsInt()))
                .thenSucceed();
    }

    // Item conduits pull items in with no destination, hold them, and deliver them once a chest is
    // placed at the other end (which connects by itself).
    static void itemStorage(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(4, 1, 0);
        helper.setBlock(source, Blocks.CHEST);
        placeRun(helper, ConduitType.ITEM, ConduitTier.ARCFORGED, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        ChestBlockEntity from = helper.getBlockEntity(source, ChestBlockEntity.class);
        from.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        from.setItem(1, new ItemStack(Items.DIRT, 10));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(from.isEmpty(), "Source chest not empty yet"))
                .thenExecute(() -> helper.assertTrue(storedItemCount(helper, 3) == 74, "Conduits store " + storedItemCount(helper, 3) + " items"))
                .thenExecute(() -> helper.setBlock(sink, Blocks.CHEST))
                .thenWaitUntil(() -> {
                    ChestBlockEntity to = helper.getBlockEntity(sink, ChestBlockEntity.class);
                    helper.assertTrue(countItem(to, Items.COBBLESTONE) == 64 && countItem(to, Items.DIRT) == 10,
                            "Sink has " + countItem(to, Items.COBBLESTONE) + " cobblestone, " + countItem(to, Items.DIRT) + " dirt");
                })
                .thenExecute(() -> helper.assertTrue(storedItemCount(helper, 3) == 0, "Conduits still store items"))
                .thenExecute(() -> helper.assertTrue(helper.getEntities(EntityTypes.ITEM).isEmpty(), "Items were dropped into the world"))
                .thenSucceed();
    }

    private static int storedItemCount(GameTestHelper helper, int length) {
        int total = 0;
        for (int x = 1; x <= length; x++) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 0))) instanceof ConduitBlockEntity conduit) {
                for (ItemStack stack : conduit.getStoredItems()) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    // The wrench rotates machines (Rotate mode) and dismantles them (Dismantle mode) into an item that keeps
    // their contents.
    static void wrenchMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            plant.getInteractionFluidHandler().insert(FluidResource.of(Fluids.LAVA), 3_000, tx);
            plant.getItemHandler(null).insert(ItemResource.of(Items.LAVA_BUCKET), 1, tx);
            tx.commit();
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.ROTATE);
        helper.assertTrue(helper.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "Plant did not rotate clockwise");

        // Sneaking in Rotate mode turns it back; in Configure mode it does nothing to a machine.
        player.setShiftKeyDown(true);
        useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.ROTATE);
        helper.assertTrue(helper.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH, "Plant did not rotate back");
        useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.CONFIGURE);
        helper.assertBlockPresent(ModBlocks.GEOTHERMAL_PLANT.get(), pos);

        useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.DISMANTLE);
        helper.assertBlockNotPresent(ModBlocks.GEOTHERMAL_PLANT.get(), pos);
        // The plant drops as an item carrying its lava, and the lava bucket in its slot drops beside it.
        var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 2, "Expected the plant and its lava bucket, got " + drops.size() + " drops");
        helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.LAVA_BUCKET)), "Lava bucket in the slot did not drop");
        ItemStack dropped = drops.stream().filter(drop -> drop.getItem().is(ModBlocks.GEOTHERMAL_PLANT.get().asItem())).findFirst().orElseThrow().getItem();
        helper.assertTrue(dropped.has(DataComponents.BLOCK_ENTITY_DATA), "Dropped machine has no saved data");

        // Place it back: the lava tank comes back too.
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, helper.absolutePos(pos), dropped);
        GeothermalPlantBlockEntity restored = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
        int lava = restored.getInteractionFluidHandler().getAmountAsInt(0);
        helper.assertTrue(restored.getItemHandler(null).getAmountAsInt(GeothermalPlantBlockEntity.SLOT_INPUT) == 0, "Placed plant got its lava bucket back (duplicated)");
        helper.assertTrue(lava == 3_000, "Restored plant has " + lava + " mB of lava");
        helper.succeed();
    }
}
