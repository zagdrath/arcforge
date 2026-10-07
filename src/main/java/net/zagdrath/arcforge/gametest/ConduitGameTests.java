/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
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
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.conduit.ConduitFilterMenu;
import net.zagdrath.arcforge.network.GhostSlotPayload;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;

// Conduit behaviour: transfer for every type, conservation when networks split, the wrench and Conduit Filters.
// Layouts run along +X at y=1: source at x=0, conduits, sink at the end.
final class ConduitGameTests {
    private ConduitGameTests() {}

    // --- Helpers ---

    // Places a straight run of conduits from x=1 to x=length, then connects them as a player placement would.
    static void placeRun(GameTestHelper helper, ConduitType type, ConduitTier tier, int length) {
        for (int x = 1; x <= length; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), ModBlocks.conduit(type, tier).get());
        }
        for (int x = 1; x <= length; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 0)));
        }
    }

    // Sets a conduit side the way the wrench would.
    static void setPort(GameTestHelper helper, BlockPos pos, Direction side, SideSetting setting) {
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
                .thenExecute(() -> helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "Items were dropped into the world"))
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
                .thenExecute(() -> helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "Items were dropped into the world"))
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
        var drops = helper.getEntities(EntityType.ITEM, pos, 2.0);
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

    // A machine dismantled with the wrench keeps its upgrades: they travel in the dropped item (not into the world) and
    // are back when it's placed, while its other slots still drop. Broken any other way, it drops them as before.
    static void wrenchKeepsUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        var crusher = helper.getBlockEntity(pos, net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity.class);
        CrushingGameTests.install(crusher.getItems(), ModItems.SPEED_UPGRADE.get(), 3);
        crusher.getItems().setStack(0, new ItemStack(Items.COBBLESTONE, 5));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.DISMANTLE);
        var drops = helper.getEntities(EntityType.ITEM, pos, 2.0);
        helper.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().is(ModItems.SPEED_UPGRADE.get())), "The upgrades dropped");
        helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.COBBLESTONE)), "The input slot's cobblestone didn't drop");
        ItemStack dropped = drops.stream().filter(drop -> drop.getItem().is(ModBlocks.ARC_CRUSHER.get().asItem())).findFirst().orElseThrow().getItem();
        drops.forEach(net.minecraft.world.entity.Entity::discard);

        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, helper.absolutePos(pos), dropped);
        var restored = helper.getBlockEntity(pos, net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity.class);
        helper.assertTrue(restored.upgrades(net.zagdrath.arcforge.upgrade.UpgradeType.SPEED) == 3,
                "The placed machine has " + restored.upgrades(net.zagdrath.arcforge.upgrade.UpgradeType.SPEED) + " Speed Upgrades, not 3");
        helper.assertTrue(restored.getItems().getStack(0).isEmpty(), "The placed machine got its cobblestone back (duplicated)");

        // Broken without the wrench, the upgrades drop.
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertTrue(helper.getEntities(EntityType.ITEM, pos, 2.0).stream().anyMatch(drop -> drop.getItem().is(ModItems.SPEED_UPGRADE.get())),
                "A broken machine didn't drop its upgrades");

        // A Heat Cell's Insulation Upgrades stay in it too, and aren't also dropped (which duplicated them).
        BlockPos cellPos = new BlockPos(3, 1, 1);
        helper.setBlock(cellPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        var cell = helper.getBlockEntity(cellPos, net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity.class);
        cell.getUpgrades().setStack(0, new ItemStack(ModItems.INSULATION_UPGRADE.get(), 2));
        useWrench(helper, player, cellPos, new Vec3(0, 0.5, 0), WrenchMode.DISMANTLE);
        var cellDrops = helper.getEntities(EntityType.ITEM, cellPos, 0.9);
        helper.assertTrue(cellDrops.stream().noneMatch(drop -> drop.getItem().is(ModItems.INSULATION_UPGRADE.get())), "The Heat Cell's upgrades dropped");
        ItemStack cellItem = cellDrops.stream().filter(drop -> drop.getItem().is(ModBlocks.heatCell(ConduitTier.WROUGHT).get().asItem())).findFirst()
                .orElseThrow().getItem();
        helper.setBlock(cellPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, helper.absolutePos(cellPos), cellItem);
        helper.assertTrue(helper.getBlockEntity(cellPos, net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity.class).getUpgrades().getStack(0)
                .getCount() == 2, "The placed Heat Cell doesn't have its 2 Insulation Upgrades");
        helper.succeed();
    }

    // --- Conduit Filters ---
    // Item layouts: a source barrel at x=0 feeding an item conduit run along +X, sink A south of conduit 2 and
    // sink B at the east end. Barrels, not chests, so neighbouring sinks never join up.

    private static final BlockPos FILTER_SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos SINK_A = new BlockPos(2, 1, 1);

    private static ItemStack filter(FilterSettings settings) {
        ItemStack filter = new ItemStack(ModItems.CONDUIT_FILTER.get());
        filter.set(ModDataComponents.CONDUIT_FILTER.get(), settings);
        return filter;
    }

    private static FilterSettings list(boolean deny, FilterSettings.Entry... entries) {
        FilterSettings settings = FilterSettings.DEFAULT.withDeny(deny);
        for (int i = 0; i < entries.length; i++) {
            settings = settings.withEntry(i, entries[i]);
        }
        return settings;
    }

    // Source, a run of `length` item conduits, sink A (south of conduit 2) and sink B (east of the last conduit).
    private static void filterLayout(GameTestHelper helper, int length) {
        helper.setBlock(FILTER_SOURCE, Blocks.BARREL);
        helper.setBlock(SINK_A, Blocks.BARREL);
        helper.setBlock(new BlockPos(length + 1, 1, 0), Blocks.BARREL);
        placeRun(helper, ConduitType.ITEM, ConduitTier.ARCFORGED, length);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(2, 1, 0), Direction.SOUTH, SideSetting.INPUT);
        setPort(helper, new BlockPos(length, 1, 0), Direction.EAST, SideSetting.INPUT);
    }

    private static Container container(GameTestHelper helper, BlockPos pos) {
        return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    // Stacks of one, so each item conduit operation moves a single item and round-robin shows.
    private static void fillSingles(Container container, net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < count; i++) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).isEmpty()) {
                    container.setItem(slot, new ItemStack(item));
                    break;
                }
            }
        }
    }

    private static int count(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }

    // An allowlist of iron on A: A gets only iron, and all the gold goes to B.
    static void filterAllowlist(GameTestHelper helper) {
        filterLayout(helper, 3);
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class)
                .setFilter(Direction.SOUTH, filter(list(false, FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT)))));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, new BlockPos(4, 1, 0));
        fillSingles(source, Items.IRON_INGOT, 8);
        fillSingles(source, Items.GOLD_INGOT, 8);
        helper.onEachTick(() -> helper.assertTrue(count(a, Items.GOLD_INGOT) == 0, "Gold got past the allowlist"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(source.isEmpty() && count(b, Items.GOLD_INGOT) == 8
                        && count(a, Items.IRON_INGOT) + count(b, Items.IRON_INGOT) == 8,
                        "A: " + count(a, Items.IRON_INGOT) + " iron; B: " + count(b, Items.IRON_INGOT) + " iron, " + count(b, Items.GOLD_INGOT) + " gold"))
                .thenExecute(() -> helper.assertTrue(count(a, Items.IRON_INGOT) > 0, "A got no iron"))
                .thenSucceed();
    }

    // Both sinks allow only iron: the gold no filter lets through stays in the source instead of being pulled into the
    // conduits' storage to wait for a destination that will never take it.
    static void filterRejectedStaysInSource(GameTestHelper helper) {
        filterLayout(helper, 3);
        var iron = filter(list(false, FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT))));
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class).setFilter(Direction.SOUTH, iron);
        helper.getBlockEntity(new BlockPos(3, 1, 0), ConduitBlockEntity.class).setFilter(Direction.EAST, iron.copy());
        Container source = container(helper, FILTER_SOURCE);
        fillSingles(source, Items.IRON_INGOT, 4);
        fillSingles(source, Items.GOLD_INGOT, 4);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(source, Items.IRON_INGOT) == 0, "Iron left in the source"))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(count(source, Items.GOLD_INGOT) == 4, count(source, Items.GOLD_INGOT) + " gold left in the source, not 4");
                    for (int x = 1; x <= 3; x++) {
                        helper.assertTrue(helper.getBlockEntity(new BlockPos(x, 1, 0), ConduitBlockEntity.class).getStoredItems().isEmpty(),
                                "Conduit " + x + " stored items no filter allows");
                    }
                })
                .thenSucceed();
    }

    // A denylist of gold on A: A never gets gold; the iron is shared between A and B.
    static void filterDenylist(GameTestHelper helper) {
        filterLayout(helper, 3);
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class)
                .setFilter(Direction.SOUTH, filter(list(true, FilterSettings.Entry.of(new ItemStack(Items.GOLD_INGOT)))));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, new BlockPos(4, 1, 0));
        fillSingles(source, Items.IRON_INGOT, 8);
        fillSingles(source, Items.GOLD_INGOT, 8);
        helper.onEachTick(() -> helper.assertTrue(count(a, Items.GOLD_INGOT) == 0, "Gold got past the denylist"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(source.isEmpty() && count(b, Items.GOLD_INGOT) == 8, "Not all the gold reached B yet"))
                .thenExecute(() -> {
                    int ironA = count(a, Items.IRON_INGOT);
                    int ironB = count(b, Items.IRON_INGOT);
                    helper.assertTrue(ironA + ironB == 8 && ironA > 0 && ironB > 0, "Iron split " + ironA + " / " + ironB);
                })
                .thenSucceed();
    }

    // A tag entry (iron ingot, matching #c:ingots) on A lets copper ingots in, but not cobblestone.
    static void filterTagMatch(GameTestHelper helper) {
        filterLayout(helper, 3);
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class).setFilter(Direction.SOUTH, filter(list(false,
                FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT)).withTag(Identifier.fromNamespaceAndPath("c", "ingots")))));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, new BlockPos(4, 1, 0));
        fillSingles(source, Items.COPPER_INGOT, 6);
        fillSingles(source, Items.COBBLESTONE, 6);
        helper.onEachTick(() -> helper.assertTrue(count(a, Items.COBBLESTONE) == 0, "Cobblestone matched #c:ingots"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(source.isEmpty() && count(b, Items.COBBLESTONE) == 6
                        && count(a, Items.COPPER_INGOT) + count(b, Items.COPPER_INGOT) == 6, "Items still moving"))
                .thenExecute(() -> helper.assertTrue(count(a, Items.COPPER_INGOT) > 0, "No copper reached A through the tag"))
                .thenSucceed();
    }

    // An allowlist of a plain iron sword on A: a damaged sword goes to B while components must match; with
    // components ignored (and B gone, so A is the only way) it reaches A.
    static void filterIgnoreComponents(GameTestHelper helper) {
        filterLayout(helper, 3);
        ConduitBlockEntity conduit = helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class);
        FilterSettings swords = list(false, FilterSettings.Entry.of(new ItemStack(Items.IRON_SWORD)));
        conduit.setFilter(Direction.SOUTH, filter(swords));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, new BlockPos(4, 1, 0));
        ItemStack damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(40);
        source.setItem(0, damaged.copy());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(b, Items.IRON_SWORD) == 1, "Damaged sword didn't reach B"))
                .thenExecute(() -> helper.assertTrue(count(a, Items.IRON_SWORD) == 0, "Damaged sword matched an exact entry"))
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(4, 1, 0), Blocks.AIR);
                    conduit.clearFilter(Direction.SOUTH);
                    conduit.setFilter(Direction.SOUTH, filter(swords.withIgnoreComponents(true)));
                    source.setItem(0, damaged.copy());
                })
                .thenWaitUntil(() -> helper.assertTrue(count(a, Items.IRON_SWORD) == 1, "Damaged sword didn't reach A with components ignored"))
                .thenSucceed();
    }

    // Water and lava sources; an allowlist of water on sink A. A only ever holds water, and all the lava ends up in B
    // (a two-slot tank, as the network carries one fluid at a time and B takes both).
    static void filterFluid(GameTestHelper helper) {
        BlockPos water = new BlockPos(0, 1, 0);
        BlockPos lava = new BlockPos(1, 1, 1);
        BlockPos sinkB = new BlockPos(4, 1, 0);
        for (BlockPos pos : new BlockPos[] { water, lava, SINK_A, sinkB }) {
            TestFixtures.reset(helper.absolutePos(pos));
            helper.setBlock(pos, Blocks.TARGET);
        }
        placeRun(helper, ConduitType.FLUID, ConduitTier.ARCFORGED, 3);
        setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(1, 1, 0), Direction.SOUTH, SideSetting.OUTPUT);
        setPort(helper, new BlockPos(2, 1, 0), Direction.SOUTH, SideSetting.INPUT);
        setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class)
                .setFilter(Direction.SOUTH, filter(list(false, FilterSettings.Entry.of(Fluids.WATER))));
        TestFixtures.tank(helper.absolutePos(water)).set(0, FluidResource.of(Fluids.WATER), 2_000);
        TestFixtures.tank(helper.absolutePos(lava)).set(0, FluidResource.of(Fluids.LAVA), 2_000);
        var a = TestFixtures.tank(helper.absolutePos(SINK_A));
        var b = TestFixtures.tank(helper.absolutePos(sinkB), 2);

        helper.onEachTick(() -> helper.assertFalse(a.getResource(0).is(Fluids.LAVA), "Lava got past the water allowlist"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(amountOf(b, Fluids.LAVA) == 2_000 && amountOf(a, Fluids.WATER) + amountOf(b, Fluids.WATER) == 2_000,
                        "A: " + amountOf(a, Fluids.WATER) + " mB water; B: " + amountOf(b, Fluids.WATER) + " mB water, " + amountOf(b, Fluids.LAVA) + " mB lava"))
                .thenExecute(() -> helper.assertTrue(amountOf(a, Fluids.WATER) > 0, "A got no water"))
                .thenSucceed();
    }

    private static int amountOf(FluidStacksResourceHandler tank, net.minecraft.world.level.material.Fluid fluid) {
        int total = 0;
        for (int slot = 0; slot < tank.size(); slot++) {
            if (tank.getResource(slot).is(fluid)) {
                total += tank.getAmountAsInt(slot);
            }
        }
        return total;
    }

    // Three sinks, two allowing iron and one (C, at the east end) gold only: the iron alternates between the two
    // that take it, splitting evenly (within one).
    static void filterRoundRobinKept(GameTestHelper helper) {
        BlockPos sinkB = new BlockPos(4, 1, 1);
        filterLayout(helper, 5);
        helper.setBlock(sinkB, Blocks.BARREL);
        setPort(helper, new BlockPos(4, 1, 0), Direction.SOUTH, SideSetting.INPUT);
        FilterSettings iron = list(false, FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT)));
        helper.getBlockEntity(new BlockPos(2, 1, 0), ConduitBlockEntity.class).setFilter(Direction.SOUTH, filter(iron));
        helper.getBlockEntity(new BlockPos(4, 1, 0), ConduitBlockEntity.class).setFilter(Direction.SOUTH, filter(iron));
        helper.getBlockEntity(new BlockPos(5, 1, 0), ConduitBlockEntity.class)
                .setFilter(Direction.EAST, filter(list(false, FilterSettings.Entry.of(new ItemStack(Items.GOLD_INGOT)))));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, sinkB);
        Container c = container(helper, new BlockPos(6, 1, 0));
        fillSingles(source, Items.IRON_INGOT, 8);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(a, Items.IRON_INGOT) + count(b, Items.IRON_INGOT) == 8, "Iron still moving"))
                .thenExecute(() -> {
                    int ironA = count(a, Items.IRON_INGOT);
                    int ironB = count(b, Items.IRON_INGOT);
                    helper.assertTrue(Math.abs(ironA - ironB) <= 1, "Iron split " + ironA + " / " + ironB + ", expected even");
                    helper.assertTrue(c.isEmpty(), "Sink C got iron through a gold allowlist");
                })
                .thenSucceed();
    }

    // An Extract allowlist of iron on the source side: only iron leaves the source, and the gold stays in it
    // (not pulled into the conduits either).
    static void filterExtractDirection(GameTestHelper helper) {
        filterLayout(helper, 3);
        helper.getBlockEntity(new BlockPos(1, 1, 0), ConduitBlockEntity.class).setFilter(Direction.WEST,
                filter(list(false, FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT))).withFlow(FilterSettings.Flow.EXTRACT)));
        Container source = container(helper, FILTER_SOURCE);
        Container a = container(helper, SINK_A);
        Container b = container(helper, new BlockPos(4, 1, 0));
        fillSingles(source, Items.IRON_INGOT, 4);
        fillSingles(source, Items.GOLD_INGOT, 4);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(count(a, Items.IRON_INGOT) + count(b, Items.IRON_INGOT) == 4, "Iron still moving"))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(count(source, Items.GOLD_INGOT) == 4, "Gold left the source: " + count(source, Items.GOLD_INGOT) + " remain");
                    helper.assertTrue(storedItemCount(helper, 3) == 0, "Gold was pulled into the conduits");
                })
                .thenSucceed();
    }

    // Dismantling a filtered side takes the filter off (with its settings) and leaves the conduit; the next
    // dismantle breaks the conduit.
    static void filterPersistsOnRemove(GameTestHelper helper) {
        filterLayout(helper, 3);
        BlockPos pos = new BlockPos(2, 1, 0);
        FilterSettings settings = list(true, FilterSettings.Entry.of(new ItemStack(Items.GOLD_INGOT)).withTag(Identifier.fromNamespaceAndPath("c", "ingots")))
                .withFlow(FilterSettings.Flow.BOTH).withIgnoreComponents(true);
        helper.getBlockEntity(pos, ConduitBlockEntity.class).setFilter(Direction.SOUTH, filter(settings));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        useWrench(helper, player, pos, new Vec3(0, 0, 0.4), WrenchMode.DISMANTLE);
        helper.assertTrue(helper.getBlockState(pos).getBlock() instanceof ConduitBlock, "Removing the filter broke the conduit");
        helper.assertFalse(helper.getBlockEntity(pos, ConduitBlockEntity.class).hasFilter(Direction.SOUTH), "Filter still installed");
        var drops = helper.getEntities(EntityType.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.CONDUIT_FILTER.get()), "Expected the filter to drop, got " + drops.size() + " drops");
        helper.assertTrue(settings.equals(drops.getFirst().getItem().get(ModDataComponents.CONDUIT_FILTER.get())), "Dropped filter lost its settings");

        useWrench(helper, player, pos, new Vec3(0, 0, 0.4), WrenchMode.DISMANTLE);
        helper.assertFalse(helper.getBlockState(pos).getBlock() instanceof ConduitBlock, "Second dismantle didn't break the conduit");
        helper.succeed();
    }

    // A filter goes only on a connection of an item, fluid or pressurized conduit, and pops off when its side unhooks.
    static void filterInstallRules(GameTestHelper helper) {
        filterLayout(helper, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(2, 1, 0);
        ItemStack held = new ItemStack(ModItems.CONDUIT_FILTER.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        // The top of the conduit faces nothing: refused.
        useFilter(helper, player, pos, new Vec3(0, 0.4, 0));
        helper.assertFalse(helper.getBlockEntity(pos, ConduitBlockEntity.class).hasFilter(Direction.UP), "Filter went on a bare side");
        // The side facing sink A: installed, one used up, without a settings component (so it stays unset).
        useFilter(helper, player, pos, new Vec3(0, 0, 0.4));
        ConduitBlockEntity conduit = helper.getBlockEntity(pos, ConduitBlockEntity.class);
        helper.assertTrue(conduit.hasFilter(Direction.SOUTH) && held.getCount() == 2, "Filter not installed on the connection");
        helper.assertTrue(FilterSettings.mode(conduit.getFilter(Direction.SOUTH)) == FilterSettings.Mode.UNSET, "A fresh filter isn't unset");
        // A second one on the same side: occupied.
        useFilter(helper, player, pos, new Vec3(0, 0, 0.4));
        helper.assertTrue(held.getCount() == 2, "A second filter went on an occupied side");
        // Energy conduits don't take filters.
        BlockPos energy = new BlockPos(2, 3, 0);
        helper.setBlock(energy, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        helper.getBlockEntity(energy, ConduitBlockEntity.class).setFilter(Direction.SOUTH, filter(FilterSettings.DEFAULT));
        helper.assertFalse(helper.getBlockEntity(energy, ConduitBlockEntity.class).hasFilter(Direction.SOUTH), "An energy conduit took a filter");
        // Breaking sink A unhooks the side: the filter drops.
        helper.setBlock(SINK_A, Blocks.AIR);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(conduit.hasFilter(Direction.SOUTH), "Filter stayed on a bare pipe");
                    helper.assertTrue(helper.getEntities(EntityType.ITEM, pos, 2.0).stream().anyMatch(drop -> drop.getItem().is(ModItems.CONDUIT_FILTER.get())),
                            "The unhooked filter didn't drop");
                })
                .thenSucceed();
    }

    private static void useFilter(GameTestHelper helper, Player player, BlockPos pos, Vec3 offsetFromCentre) {
        BlockPos absolute = helper.absolutePos(pos);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).add(offsetFromCentre), Direction.UP, absolute, false));
        player.getMainHandItem().useOn(context);
    }

    // Items and fluids dragged from JEI onto Conduit Filter entries (GhostSlotPayload) set them as clicking with the item
    // would: an item filter takes items, a fluid filter fluids (or an item holding one), a pressurized filter gases, and
    // each refuses the other kinds, as only the matching entries light up while dragging.
    static void filterGhostSlotDrag(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 0));
        player.snapTo(near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5);
        int id = 40;
        for (ConduitType type : List.of(ConduitType.ITEM, ConduitType.FLUID, ConduitType.GAS)) {
            BlockPos pos = new BlockPos(1 + type.ordinal() * 2, 1, 2);
            helper.setBlock(pos, ModBlocks.conduit(type, ConduitTier.ARCFORGED).get());
            ConduitBlockEntity conduit = helper.getBlockEntity(pos, ConduitBlockEntity.class);
            conduit.setFilter(Direction.NORTH, new ItemStack(ModItems.CONDUIT_FILTER.get()));
            ConduitFilterMenu menu = new ConduitFilterMenu(++id, player.getInventory(), helper.absolutePos(pos), Direction.NORTH);
            player.containerMenu = menu;
            boolean item = GhostSlotPayload.apply(player, GhostSlotPayload.item(id, 0, new ItemStack(Items.IRON_INGOT)));
            boolean water = GhostSlotPayload.apply(player, GhostSlotPayload.fluid(id, 1, Fluids.WATER));
            boolean bucket = GhostSlotPayload.apply(player, GhostSlotPayload.item(id, 2, new ItemStack(Items.LAVA_BUCKET)));
            boolean gas = GhostSlotPayload.apply(player, GhostSlotPayload.fluid(id, 3, ModFluids.HYDROGEN.get()));
            FilterSettings settings = FilterSettings.of(conduit.getFilter(Direction.NORTH));
            switch (type) {
                case ITEM -> {
                    helper.assertTrue(item && settings.entry(0).item().isPresent(), "The item filter didn't take a dragged item");
                    helper.assertTrue(bucket && settings.entry(2).item().isPresent(), "The item filter didn't take a bucket as an item");
                    helper.assertFalse(water || gas, "The item filter took a fluid");
                }
                case FLUID -> {
                    helper.assertTrue(water && settings.entry(1).fluid().filter(f -> f.isSame(Fluids.WATER)).isPresent(),
                            "The fluid filter didn't take dragged Water");
                    helper.assertTrue(bucket && settings.entry(2).fluid().filter(f -> f.isSame(Fluids.LAVA)).isPresent(),
                            "The fluid filter didn't take a Lava Bucket as Lava");
                    helper.assertFalse(item || gas, "The fluid filter took an item or a gas");
                }
                default -> {
                    helper.assertTrue(gas && settings.entry(3).fluid().filter(f -> f.isSame(ModFluids.HYDROGEN.get())).isPresent(),
                            "The gas filter didn't take dragged Hydrogen");
                    helper.assertFalse(item || water || bucket, "The gas filter took an item or a liquid");
                }
            }
            helper.assertFalse(GhostSlotPayload.apply(player, GhostSlotPayload.item(id + 100, 4, new ItemStack(Items.STONE))),
                    "A payload for another menu was applied");
            helper.assertFalse(GhostSlotPayload.apply(player, GhostSlotPayload.item(id, FilterSettings.SIZE, new ItemStack(Items.STONE))),
                    "A payload for a slot past the filter was applied");
        }
        helper.succeed();
    }
}
