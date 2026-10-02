/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.experience.XpShowerBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.chunkloading.ChunkLoaders;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.quantum.QuantumFaces;
import net.zagdrath.arcforge.quantum.QuantumFrequencies;
import net.zagdrath.arcforge.quantum.QuantumResource;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.security.SecurityProfiles;

// The Reservoir (one tank of touching blocks, filled bottom-up, drained top-down, read by a comparator, a broken block
// keeping its share, a different liquid staying apart), Liquid Experience (the tag, the XP Drain taking orbs and a
// sneaking player's levels, the XP Shower giving them and stopping under redstone, the Vacuum Collector's tank), the
// Quantum Tunnel (FE, liquid and items through a frequency, faces set to NONE offering nothing, private frequencies) and
// the Chunk Loader (tickets, the registry, the per-player limit, redstone, breaking).
public final class ExtendedLogisticsGameTests {
    private ExtendedLogisticsGameTests() {}

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return extracted;
        }
    }

    private static ReservoirBlockEntity reservoir(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ReservoirBlockEntity.class);
    }

    // Four Reservoirs (a column of three and one beside the bottom) are one 128,000 mB tank: it fills the bottom layer
    // first, evenly, then the next; drains from the top; a comparator reads the whole; a broken block keeps its share;
    // a Reservoir of lava placed against it stays a tank of its own.
    static void reservoir(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1), c = new BlockPos(1, 2, 1), d = new BlockPos(1, 3, 1);
        for (BlockPos pos : List.of(a, b, c, d)) {
            helper.setBlock(pos, ModBlocks.RESERVOIR.get());
        }
        ResourceHandler<FluidResource> top = reservoir(helper, d).getFluidHandler();
        helper.assertTrue(insert(top, Fluids.WATER, 50_000) == 50_000, "The tank didn't take 50,000 mB");
        helper.assertTrue(reservoir(helper, a).ownAmount() == 25_000 && reservoir(helper, b).ownAmount() == 25_000
                && reservoir(helper, c).ownAmount() == 0 && reservoir(helper, d).ownAmount() == 0,
                "Not filled bottom-up evenly: " + reservoir(helper, a).ownAmount() + "/" + reservoir(helper, b).ownAmount() + "/"
                        + reservoir(helper, c).ownAmount() + "/" + reservoir(helper, d).ownAmount());
        helper.assertTrue(top.getCapacityAsLong(0, FluidResource.of(Fluids.WATER)) == 4L * ReservoirBlockEntity.CAPACITY, "Group capacity is "
                + top.getCapacityAsLong(0, FluidResource.of(Fluids.WATER)));
        helper.assertTrue(insert(top, Fluids.LAVA, 1_000) == 0, "It took a second liquid");
        insert(reservoir(helper, b).getFluidHandler(), Fluids.WATER, 30_000);
        helper.assertTrue(reservoir(helper, c).ownAmount() == 16_000, "The second layer holds " + reservoir(helper, c).ownAmount());
        helper.assertTrue(extract(reservoir(helper, a).getFluidHandler(), Fluids.WATER, 20_000) == 20_000, "Couldn't drain from the bottom block");
        helper.assertTrue(reservoir(helper, c).ownAmount() == 0 && reservoir(helper, a).ownAmount() == 30_000,
                "Not drained from the top: " + reservoir(helper, c).ownAmount() + "/" + reservoir(helper, a).ownAmount());
        helper.assertTrue(reservoir(helper, d).getComparatorSignal() == 15 * 60_000 / 128_000, "Comparator reads "
                + reservoir(helper, d).getComparatorSignal());
        // Broken: its 30,000 mB go with it.
        var level = helper.getLevel();
        List<ItemStack> drops = Block.getDrops(level.getBlockState(helper.absolutePos(b)), level, helper.absolutePos(b),
                level.getBlockEntity(helper.absolutePos(b)));
        FluidStack kept = drops.isEmpty() ? FluidStack.EMPTY
                : drops.getFirst().getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        helper.assertTrue(kept.getAmount() == 30_000 && kept.getFluid() == Fluids.WATER, "The broken block kept " + kept.getAmount());
        helper.setBlock(b, Blocks.AIR);
        // A Reservoir already holding lava, placed against the water, stays apart.
        BlockPos e = new BlockPos(0, 1, 1);
        helper.setBlock(e, ModBlocks.RESERVOIR.get());
        ItemStack lava = new ItemStack(ModItems.RESERVOIR.get());
        lava.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(new FluidStack(Fluids.LAVA, 1_000)));
        reservoir(helper, e).applyComponentsFromItemStack(lava);
        helper.assertTrue(insert(reservoir(helper, e).getFluidHandler(), Fluids.LAVA, 1_000) == 1_000, "The lava Reservoir took no lava");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(reservoir(helper, a).getGroup().size() == 3 && reservoir(helper, a).getGroup().amount() == 30_000,
                            "The water tank is now " + reservoir(helper, a).getGroup().size() + " blocks, " + reservoir(helper, a).getGroup().amount() + " mB");
                    helper.assertTrue(reservoir(helper, e).getGroup().size() == 1 && reservoir(helper, e).ownAmount() == 2_000,
                            "The lava joined the water");
                })
                .thenSucceed();
    }

    // Liquid Experience is c:experience. An XP Drain on a Reservoir turns an orb into 20 mB a point, and drains a
    // sneaking player a level at a time.
    static void xpDrain(GameTestHelper helper) {
        helper.assertTrue(ModFluids.LIQUID_EXPERIENCE.get().defaultFluidState().is(LiquidExperience.TAG), "Liquid Experience isn't c:experience");
        helper.assertTrue(LiquidExperience.pointsForLevel(0) == 7 && LiquidExperience.pointsForLevel(15) == 37
                && LiquidExperience.pointsForLevel(30) == 112, "Wrong level curve");
        BlockPos tank = new BlockPos(1, 1, 1), drain = new BlockPos(1, 2, 1);
        helper.setBlock(tank, ModBlocks.RESERVOIR.get());
        helper.setBlock(drain, ModBlocks.XP_DRAIN.get());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.15, 1.5));
        helper.getLevel().addFreshEntity(new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 7));
        int twoLevels = LiquidExperience.pointsForLevel(0) + LiquidExperience.pointsForLevel(1);
        ServerPlayer[] player = new ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(reservoir(helper, tank).ownAmount() == 7 * LiquidExperience.MB_PER_POINT,
                        "The orb didn't drain: " + reservoir(helper, tank).ownAmount()))
                // Only then the player, so the orb can't fly to them instead.
                .thenExecute(() -> {
                    player[0] = helper.makeMockServerPlayerInLevel();
                    player[0].setGameMode(GameType.SURVIVAL);
                    player[0].giveExperienceLevels(2);
                    player[0].setPos(helper.absoluteVec(new Vec3(1.5, 2.2, 1.5)));
                    player[0].setShiftKeyDown(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(player[0].experienceLevel == 0 && player[0].experienceProgress < 0.01F,
                        "The player still has level " + player[0].experienceLevel))
                .thenExecute(() -> helper.assertTrue(reservoir(helper, tank).ownAmount() == (7 + twoLevels) * LiquidExperience.MB_PER_POINT,
                        "The tank holds " + reservoir(helper, tank).ownAmount() + " mB"))
                .thenSucceed();
    }

    // An XP Shower under a Reservoir of Liquid Experience gives a sneaking player a level at a time; redstone stops it.
    static void xpShower(GameTestHelper helper) {
        BlockPos tank = new BlockPos(1, 4, 1), shower = new BlockPos(1, 3, 1);
        helper.setBlock(tank, ModBlocks.RESERVOIR.get());
        helper.setBlock(shower, ModBlocks.XP_SHOWER.get());
        insert(reservoir(helper, tank).getFluidHandler(), ModFluids.LIQUID_EXPERIENCE.get(), 10_000);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        player.setShiftKeyDown(true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.experienceLevel >= 2, "The player is level " + player.experienceLevel))
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(2, 3, 1), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(helper.getBlockEntity(shower, XpShowerBlockEntity.class).isOff(), "Redstone didn't turn it off");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    int level = player.experienceLevel;
                    helper.runAfterDelay(4L * ArcforgeConfig.XP_SHOWER_LEVEL_INTERVAL.getAsInt(),
                            () -> helper.assertTrue(player.experienceLevel == level, "It kept going under redstone"));
                })
                .thenIdle(4 * ArcforgeConfig.XP_SHOWER_LEVEL_INTERVAL.getAsInt() + 2)
                .thenSucceed();
    }

    // The Vacuum Collector takes an orb into its Liquid Experience tank, which its output face gives out.
    static void vacuumExperience(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.VACUUM_COLLECTOR.get());
        VacuumCollectorBlockEntity collector = helper.getBlockEntity(pos, VacuumCollectorBlockEntity.class);
        collector.setRange(2);
        CrushingGameTests.charge(collector.getEnergy(), 20_000);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.2, 2.5));
        helper.getLevel().addFreshEntity(new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(collector.getXpTank().getAmount() == 5 * LiquidExperience.MB_PER_POINT,
                        "The collector's tank holds " + collector.getXpTank().getAmount()))
                .thenExecute(() -> {
                    // The default bottom face is an Output face.
                    ResourceHandler<FluidResource> out = collector.getFluidHandler(Direction.DOWN);
                    helper.assertTrue(out != null && extract(out, ModFluids.LIQUID_EXPERIENCE.get(), 100) == 100, "Couldn't drain its experience");
                })
                .thenSucceed();
    }

    // Two tunnels on one frequency: FE, water and iron put into one come out of the other's output faces; a face left
    // at NONE offers nothing; a private frequency works only for its owner and the players they trust.
    static void quantumTunnel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuantumFrequencies frequencies = QuantumFrequencies.get(server);
        Frequency.Key key = Frequency.Key.ofPublic("gametest-" + UUID.randomUUID());
        frequencies.create(key, UUID.randomUUID(), "tester");
        BlockPos inPos = new BlockPos(1, 1, 1), outPos = new BlockPos(5, 1, 1);
        helper.setBlock(inPos, ModBlocks.QUANTUM_TUNNEL.get());
        helper.setBlock(outPos, ModBlocks.QUANTUM_TUNNEL.get());
        QuantumTunnelBlockEntity in = helper.getBlockEntity(inPos, QuantumTunnelBlockEntity.class);
        QuantumTunnelBlockEntity out = helper.getBlockEntity(outPos, QuantumTunnelBlockEntity.class);
        in.setKey(key);
        out.setKey(key);
        helper.assertTrue(in.getFluidHandler(Direction.WEST) == null, "A NONE face offers fluid");
        for (QuantumResource resource : List.of(QuantumResource.ENERGY, QuantumResource.FLUID, QuantumResource.ITEMS)) {
            in.setMode(Direction.WEST, resource, QuantumFaces.INPUT);
        }
        out.setMode(Direction.EAST, QuantumResource.FLUID, QuantumFaces.OUTPUT);
        out.setMode(Direction.UP, QuantumResource.ITEMS, QuantumFaces.OUTPUT);
        out.setMode(Direction.NORTH, QuantumResource.ENERGY, QuantumFaces.OUTPUT);
        // The cell's back (an input face) against the tunnel.
        BlockPos tankPos = outPos.east(), chestPos = outPos.above(), cellPos = outPos.north();
        helper.setBlock(tankPos, ModBlocks.fluidTank(net.zagdrath.arcforge.conduit.ConduitTier.WROUGHT).get());
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(cellPos, ModBlocks.energyCell(net.zagdrath.arcforge.conduit.ConduitTier.WROUGHT).get());
        insert(in.getFluidHandler(Direction.WEST), Fluids.WATER, 1_000);
        try (Transaction tx = Transaction.openRoot()) {
            in.getItemHandler(Direction.WEST).insert(ItemResource.of(Items.IRON_INGOT), 5, tx);
            in.getEnergyHandler(Direction.WEST).insert(10_000, tx);
            tx.commit();
        }
        var level = helper.getLevel();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var tank = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(tankPos), Direction.WEST);
                    helper.assertTrue(tank != null && tank.getAmountAsLong(0) == 1_000, "The water didn't come through");
                    var chest = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(chestPos), Direction.DOWN);
                    long iron = 0;
                    for (int slot = 0; chest != null && slot < chest.size(); slot++) {
                        iron += chest.getResource(slot).is(Items.IRON_INGOT) ? chest.getAmountAsLong(slot) : 0;
                    }
                    helper.assertTrue(iron == 5, "The iron didn't come through: " + iron);
                    var cell = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(cellPos), Direction.SOUTH);
                    helper.assertTrue(cell != null && cell.getAmountAsLong() > 0, "No FE came through");
                })
                .thenExecute(() -> {
                    // A private frequency: the owner's tunnel uses it, a stranger's doesn't until they're trusted.
                    UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
                    Frequency.Key secret = Frequency.Key.ofPrivate("gametest-" + UUID.randomUUID(), owner);
                    frequencies.create(secret, owner, "owner");
                    in.setOwner(stranger, "stranger");
                    in.setKey(secret);
                    helper.assertTrue(in.frequency() == null, "A stranger's tunnel used a private frequency");
                    SecurityProfiles.get(server).trust(owner, stranger, "stranger");
                    helper.assertTrue(in.frequency() != null, "A trusted player's tunnel can't use it");
                    SecurityProfiles.get(server).untrust(owner, stranger);
                    frequencies.remove(secret);
                    frequencies.remove(key);
                })
                .thenSucceed();
    }

    // A Chunk Loader holds tickets for its area and lists them in the registry; a second loader of the same owner that
    // would go over the limit stays off; redstone turns it off; breaking it drops the tickets and the entry.
    static void chunkLoader(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        BlockPos aPos = new BlockPos(1, 1, 1), bPos = new BlockPos(4, 1, 1);
        helper.setBlock(aPos, ModBlocks.CHUNK_LOADER.get());
        helper.setBlock(bPos, ModBlocks.CHUNK_LOADER.get());
        ChunkLoaderBlockEntity a = helper.getBlockEntity(aPos, ChunkLoaderBlockEntity.class);
        ChunkLoaderBlockEntity b = helper.getBlockEntity(bPos, ChunkLoaderBlockEntity.class);
        a.setOwner(owner, "owner");
        b.setOwner(owner, "owner");
        a.setRadius(1);
        int limit = ArcforgeConfig.CHUNK_LOADER_PLAYER_LIMIT.getAsInt();
        GlobalPos aGlobal = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(aPos));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getStatus() == ChunkLoaderStatus.ACTIVE && a.getHeld().size() == 9,
                        "Loader A is " + a.getStatus() + " with " + a.getHeld().size() + " chunks"))
                .thenExecute(() -> {
                    ChunkLoaders.Entry entry = ChunkLoaders.get(server).get(aGlobal);
                    helper.assertTrue(entry != null && entry.owner().equals(owner) && entry.chunks().size() == 9, "The registry doesn't list A's chunks");
                    // B at radius 2 would take the owner to 9 + 25 chunks.
                    b.setRadius(2);
                })
                .thenWaitUntil(() -> helper.assertTrue(9 + 25 <= limit ? b.getStatus() == ChunkLoaderStatus.ACTIVE : b.getStatus() == ChunkLoaderStatus.LIMIT,
                        "Loader B is " + b.getStatus()))
                .thenExecute(() -> helper.setBlock(aPos.above(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(a.getStatus() == ChunkLoaderStatus.DISABLED && a.getHeld().isEmpty(), "Redstone didn't stop A"))
                .thenExecute(() -> {
                    helper.setBlock(aPos, Blocks.AIR);
                    helper.setBlock(bPos, Blocks.AIR);
                    helper.assertTrue(ChunkLoaders.get(server).get(aGlobal) == null, "A broken loader is still registered");
                    helper.assertTrue(ChunkLoaders.get(server).chunksOf(owner, null) == 0, "The owner still holds chunks");
                })
                .thenSucceed();
    }
}
