/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.SeismicSurveyToolItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.oil.DepositKind;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Crude oil: where deposits are, the Seismic Survey Tool, the Oil Derrick drilling to a Well Head, and the Pumpjack.
final class OilGameTests {
    private OilGameTests() {}

    // Deposits follow from the seed and the region alone; oil-rich regions have them more often, and bigger ones.
    static void depositsFollowTheSeed(GameTestHelper helper) {
        int size = ArcforgeConfig.OIL_REGION_SIZE.getAsInt();
        OilDeposits.Deposit a = null;
        for (int x = 0; x < 50 && a == null; x++) {
            a = OilDeposits.roll(1234L, "minecraft:overworld", x, 7, size, false);
        }
        helper.assertTrue(a != null, "No deposit in 50 regions");
        OilDeposits.Deposit again = OilDeposits.roll(1234L, "minecraft:overworld", a.regionX(), a.regionZ(), size, false);
        helper.assertTrue(a.equals(again), "The same region rolled " + a + " then " + again);
        int plain = 0, rich = 0;
        long plainOil = 0, richOil = 0;
        for (int x = 0; x < 400; x++) {
            OilDeposits.Deposit p = OilDeposits.roll(99L, "minecraft:overworld", x, -3, size, false);
            OilDeposits.Deposit r = OilDeposits.roll(99L, "minecraft:overworld", x, -3, size, true);
            if (p != null) {
                plain++;
                plainOil += p.amount();
            }
            if (r != null) {
                rich++;
                richOil += r.amount();
            }
        }
        helper.assertTrue(rich > plain * 1.5, "Oil-rich regions had " + rich + " deposits against " + plain);
        helper.assertTrue(plain > 0 && richOil / rich > plainOil / plain, "Oil-rich deposits aren't bigger on average");
        // Each deposit lies inside its region, between the configured heights.
        int min = ArcforgeConfig.OIL_MIN_Y.getAsInt(), max = ArcforgeConfig.OIL_MAX_Y.getAsInt();
        helper.assertTrue(a.y() >= min && a.y() <= max, "A deposit lies at y " + a.y());
        helper.assertTrue(a.centreChunkX() >= a.regionX() * size && a.centreChunkX() < (a.regionX() + 1) * size, "A deposit's middle is outside its region");
        helper.succeed();
    }

    // The survey reads the deposit under the chunk: its kind and roughly what's left, or that it's drained, or nothing.
    static void surveyTool(GameTestHelper helper) {
        BlockPos here = helper.absolutePos(new BlockPos(1, 1, 1));
        OilDeposits.Deposit deposit = OilDeposits.putTestDeposit(helper.getLevel(), here, DepositKind.FIELD, 18_400_000L, -20);
        helper.assertTrue(key(SeismicSurveyToolItem.report(helper.getLevel(), here)).equals("message.arcforge.survey.found"), "The survey found nothing");
        helper.assertTrue(OilDeposits.roughBuckets(18_437_000L) == 18_000 && OilDeposits.roughBuckets(56_000L) == 56,
                "Rough amounts: " + OilDeposits.roughBuckets(18_437_000L));
        OilDeposits.get(helper.getLevel().getServer()).setExtracted(deposit, deposit.amount());
        helper.assertTrue(key(SeismicSurveyToolItem.report(helper.getLevel(), here)).equals("message.arcforge.survey.drained"),
                "A drained deposit doesn't read as drained");
        OilDeposits.clearTestDeposit(helper.getLevel(), here);
        helper.succeed();
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    // A rig whose floor covers min to min + (2, 0, 2) in the test's own frame (which may be turned): the floor ring of
    // casings round an open bore, the controller in the middle of its north side, frame legs and the crown. The rig and
    // its parts are in world positions, and placed there: helper.relativePos doesn't undo absolutePos in a turned test.
    static OilfieldStructures.Rig buildRig(GameTestHelper helper, BlockPos min) {
        OilfieldStructures.Rig rig = new OilfieldStructures.Rig(worldMin(helper, min, min.offset(2, 0, 2)), Direction.NORTH);
        for (BlockPos pos : rig.parts()) {
            place(helper, pos, pos.equals(rig.controller())
                    ? ModBlocks.OIL_DERRICK_CONTROLLER.get().defaultBlockState().setValue(OilfieldControllerBlock.FACING, Direction.NORTH)
                    : (rig.isFrame(pos) ? ModBlocks.DERRICK_FRAME.get() : ModBlocks.DERRICK_CASING.get()).defaultBlockState());
        }
        return rig;
    }

    private static void place(GameTestHelper helper, BlockPos world, net.minecraft.world.level.block.state.BlockState state) {
        helper.getLevel().setBlock(world, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    // The block entity at a world position.
    private static <T> T at(GameTestHelper helper, BlockPos world, Class<T> type) {
        Object blockEntity = helper.getLevel().getBlockEntity(world);
        helper.assertTrue(type.isInstance(blockEntity), "No " + type.getSimpleName() + " at " + world);
        return type.cast(blockEntity);
    }

    // The GameTest server (with no players) only ticks the chunks round the test area itself, so the other chunks a test
    // builds in (one per deposit) are forced to load and tick. The returned action lets go of the ones forced here.
    static Runnable forceChunks(GameTestHelper helper, BlockPos... positions) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.world.level.ChunkPos own = net.minecraft.world.level.ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        java.util.Set<net.minecraft.world.level.ChunkPos> forced = new java.util.LinkedHashSet<>();
        for (BlockPos pos : positions) {
            net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(helper.absolutePos(pos));
            if (!chunk.equals(own) && forced.add(chunk)) {
                level.setChunkForced(chunk.x(), chunk.z(), true);
            }
        }
        return () -> forced.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
    }

    // The world corner of the box between two of the test's own positions.
    private static BlockPos worldMin(GameTestHelper helper, BlockPos a, BlockPos b) {
        BlockPos worldA = helper.absolutePos(a), worldB = helper.absolutePos(b);
        return new BlockPos(Math.min(worldA.getX(), worldB.getX()), Math.min(worldA.getY(), worldB.getY()), Math.min(worldA.getZ(), worldB.getZ()));
    }

    // An Oil Derrick over a deposit three blocks down drills with FE, Water and Steel Pipe, then comes down: a Well
    // Head caps the bore and the rig's blocks lie there to pick up. Without pipe it waits; over no oil it won't start.
    static void derrickStrikesOil(GameTestHelper helper) {
        // Clear of the test area's own barrier walls round its corner.
        BlockPos min = new BlockPos(2, 1, 2), dry = new BlockPos(34, 1, 2);
        Runnable release = forceChunks(helper, min, min.offset(2, 0, 2), dry, dry.offset(2, 0, 2));
        // World positions.
        BlockPos controllerPos = buildRig(helper, min).controller();
        BlockPos dryController = buildRig(helper, dry).controller();
        BlockPos bore = helper.absolutePos(min.offset(1, 0, 1));
        OilDeposits.putTestDeposit(helper.getLevel(), bore, DepositKind.SEEP, 1_000_000L, bore.getY() - 3);
        int blocks = 3, ticks = ArcforgeConfig.DERRICK_TICKS_PER_BLOCK.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    OilDerrickBlockEntity derrick = at(helper, controllerPos, OilDerrickBlockEntity.class);
                    helper.assertTrue(derrick.isFormed(), "The rig didn't form");
                    OilDerrickBlockEntity other = at(helper, dryController, OilDerrickBlockEntity.class);
                    helper.assertTrue(other.isFormed() && other.getStatus() == MachineStatus.NO_OIL || OilDeposits.at(helper.getLevel(),
                            helper.absolutePos(dry.offset(1, 0, 1))) != null, "A rig over no oil is " + other.getStatus());
                    derrick.getEnergy().set(derrick.getEnergy().getCapacityAsInt());
                    helper.assertTrue(SteamGameTests.fill(derrick.getFluidHandler(null), FluidResource.of(Fluids.WATER), 8_000) == 8_000, "It took no water");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    OilDerrickBlockEntity derrick = at(helper, controllerPos, OilDerrickBlockEntity.class);
                    helper.assertTrue(derrick.getStatus() == MachineStatus.NO_PIPE, "Without pipe it's " + derrick.getStatus());
                    helper.assertTrue(derrick.getTarget() == blocks && derrick.pipesNeeded() == 1, "It needs " + derrick.getTarget() + " blocks, "
                            + derrick.pipesNeeded() + " pipes");
                    derrick.getItems().setStack(OilDerrickBlockEntity.SLOT_PIPES, new ItemStack(ModItems.STEEL_PIPE.get(), 4));
                })
                .thenIdle(ticks * blocks + 10)
                .thenExecute(() -> {
                    helper.assertBlockPresent(ModBlocks.WELL_HEAD.get(), min.offset(1, 0, 1));
                    helper.assertTrue(!helper.getLevel().getBlockState(controllerPos).is(ModBlocks.OIL_DERRICK_CONTROLLER.get()), "The controller is still there");
                    helper.assertBlockNotPresent(ModBlocks.DERRICK_FRAME.get(), min.offset(0, 1, 0));
                    AABB around = new AABB(helper.absolutePos(min)).inflate(4);
                    int casings = 0, frames = 0, controllers = 0, pipes = 0;
                    for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
                        ItemStack stack = item.getItem();
                        casings += stack.is(ModItems.DERRICK_CASING.get()) ? stack.getCount() : 0;
                        frames += stack.is(ModItems.DERRICK_FRAME.get()) ? stack.getCount() : 0;
                        controllers += stack.is(ModItems.OIL_DERRICK_CONTROLLER.get()) ? stack.getCount() : 0;
                        pipes += stack.is(ModItems.STEEL_PIPE.get()) ? stack.getCount() : 0;
                    }
                    helper.assertTrue(casings == 7 && frames == 25 && controllers == 1, "Handed back " + casings + " casings, " + frames + " frames, "
                            + controllers + " controllers");
                    helper.assertTrue(pipes == 3, pipes + " spare pipes dropped");
                    OilDeposits.clearTestDeposit(helper.getLevel(), helper.absolutePos(min.offset(1, 0, 1)));
                    release.run();
                })
                .thenSucceed();
    }

    // A pumpjack round a Well Head, its controller at the south end (in the world; the test's own frame may be turned).
    static OilfieldStructures.Unit buildPumpjack(GameTestHelper helper, BlockPos well) {
        OilfieldStructures.Unit unit = new OilfieldStructures.Unit(helper.absolutePos(well), Direction.SOUTH);
        place(helper, unit.well(), ModBlocks.WELL_HEAD.get().defaultBlockState());
        for (BlockPos pos : unit.parts()) {
            place(helper, pos, pos.equals(unit.controller())
                    ? ModBlocks.PUMPJACK_CONTROLLER.get().defaultBlockState().setValue(OilfieldControllerBlock.FACING, Direction.SOUTH)
                    : ModBlocks.PUMPJACK_CASING.get().defaultBlockState());
        }
        return unit;
    }

    // A Pumpjack pumps at full rate from a full deposit, slower as it drains (to minShare of it) and a trickle from a
    // drained one; it needs FE, and taking the Well Head away breaks it.
    static void pumpjackPumps(GameTestHelper helper) {
        // Clear of the test area's own barrier walls round its corner.
        BlockPos full = new BlockPos(3, 1, 6), drained = new BlockPos(19, 1, 6);
        // Each skid reaches up to 4 blocks from its well, whichever way the test is turned.
        Runnable release = forceChunks(helper, full.offset(-1, 0, -4), full.offset(1, 0, 4), drained.offset(-1, 0, -4), drained.offset(1, 0, 4));
        // World positions.
        BlockPos fullController = buildPumpjack(helper, full).controller();
        BlockPos dryController = buildPumpjack(helper, drained).controller();
        long amount = 2_000_000L;
        OilDeposits.Deposit fullDeposit = OilDeposits.putTestDeposit(helper.getLevel(), helper.absolutePos(full), DepositKind.POCKET, amount, -10);
        OilDeposits.Deposit dryDeposit = OilDeposits.putTestDeposit(helper.getLevel(), helper.absolutePos(drained), DepositKind.SEEP, amount, -10);
        OilDeposits.get(helper.getLevel().getServer()).setExtracted(dryDeposit, amount);
        int rate = ArcforgeConfig.PUMPJACK_RATE.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    PumpjackBlockEntity pumpjack = at(helper, fullController, PumpjackBlockEntity.class);
                    helper.assertTrue(pumpjack.isFormed(), "The pumpjack didn't form");
                    helper.assertTrue(pumpjack.getStatus() == MachineStatus.NO_POWER, "Without FE it's " + pumpjack.getStatus());
                    for (BlockPos pos : new BlockPos[] { fullController, dryController }) {
                        PumpjackBlockEntity unit = at(helper, pos, PumpjackBlockEntity.class);
                        unit.getEnergy().set(unit.getEnergy().getCapacityAsInt());
                    }
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    PumpjackBlockEntity pumpjack = at(helper, fullController, PumpjackBlockEntity.class);
                    helper.assertTrue(pumpjack.getStatus() == MachineStatus.PUMPING, "With FE it's " + pumpjack.getStatus());
                    int crude = pumpjack.getCrude().getAmount();
                    helper.assertTrue(crude >= rate * 18 && crude <= rate * 21, "A full deposit gave " + crude + " mB in 20 ticks");
                    long left = OilDeposits.get(helper.getLevel().getServer()).remaining(fullDeposit);
                    helper.assertTrue(left == amount - crude, "The deposit has " + left + " mB left after " + crude + " mB was pumped");
                    PumpjackBlockEntity trickle = at(helper, dryController, PumpjackBlockEntity.class);
                    double expected = ArcforgeConfig.PUMPJACK_TRICKLE.getAsInt() / 20.0;
                    helper.assertTrue(Math.abs(trickle.getRate() - expected) < 1.0E-6 && trickle.getCrude().getAmount() > 0,
                            "A drained deposit pumps " + trickle.getRate() + " mB/t");
                    // Nearly dry: down to minShare of the full rate.
                    OilDeposits.get(helper.getLevel().getServer()).setExtracted(fullDeposit, amount - 1_000);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    PumpjackBlockEntity pumpjack = at(helper, fullController, PumpjackBlockEntity.class);
                    double slow = rate * (ArcforgeConfig.PUMPJACK_MIN_SHARE.getAsDouble() + (1 - ArcforgeConfig.PUMPJACK_MIN_SHARE.getAsDouble()) * 1_000.0 / amount);
                    helper.assertTrue(pumpjack.getRate() < rate * 0.5 && pumpjack.getRate() >= slow - 1.0, "Nearly dry it pumps " + pumpjack.getRate());
                    helper.setBlock(full, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!at(helper, fullController, PumpjackBlockEntity.class).isFormed(), "It stayed formed without its Well Head");
                    OilDeposits.clearTestDeposit(helper.getLevel(), helper.absolutePos(full));
                    OilDeposits.clearTestDeposit(helper.getLevel(), helper.absolutePos(drained));
                    release.run();
                })
                .thenSucceed();
    }

    // Crude Oil is a fluid with a bucket; Kerosene, Diesel and Refinery Gas burn; Refinery Gas is a gas.
    static void crudeOilFluids(GameTestHelper helper) {
        helper.assertTrue(ModItems.CRUDE_OIL_BUCKET.get().getDefaultInstance().is(ModItems.CRUDE_OIL_BUCKET.get())
                && ModFluids.CRUDE_OIL.get().getBucket() == ModItems.CRUDE_OIL_BUCKET.get(), "Crude Oil has no bucket");
        helper.assertTrue(net.zagdrath.arcforge.steam.Gases.isGas(ModFluids.REFINERY_GAS.get()), "Refinery Gas isn't a gas");
        for (var fuel : new net.minecraft.world.level.material.Fluid[] { ModFluids.KEROSENE.get(), ModFluids.DIESEL.get(), ModFluids.REFINERY_GAS.get() }) {
            helper.assertTrue(net.zagdrath.arcforge.heat.BurnerFuel.of(fuel) != null, fuel + " doesn't burn");
        }
        helper.succeed();
    }
}
