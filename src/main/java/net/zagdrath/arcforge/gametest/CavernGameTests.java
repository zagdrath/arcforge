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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.machine.GasWellheadBlock;
import net.zagdrath.arcforge.block.multiblock.WellHeadBlock;
import net.zagdrath.arcforge.blockentity.machine.GasWellheadBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.item.tool.JetpackFuel;
import net.zagdrath.arcforge.item.tool.SeismicSurveyToolItem;
import net.zagdrath.arcforge.item.tool.SurveyRecordItem;
import net.zagdrath.arcforge.machine.EngineFuel;
import net.zagdrath.arcforge.oil.DepositKind;
import net.zagdrath.arcforge.oil.OilCavern;
import net.zagdrath.arcforge.oil.OilCaverns;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.oil.SurveyRecord;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Oil caverns and what came with them: the cavern's shape and size, carving it and keeping its blocks in step with the
// deposit's amount, sour deposits, gas caps and the Gas Wellhead, sour fuels, and Survey Records.
final class CavernGameTests {
    private CavernGameTests() {}

    // A cavern holds about a source block per bucket of its deposit, is about twice as wide as it is tall, and its oil
    // falls a whole layer at a time from the top as the deposit drains. A gas cap leaves its top empty.
    static void cavernShape(GameTestHelper helper) {
        int[][] kinds = { { 2_000, 1 }, { 7_000, 2 }, { 20_000, 3 }, { 100_000, 4 } };
        DepositKind[] order = { DepositKind.SEEP, DepositKind.POCKET, DepositKind.FIELD, DepositKind.GIANT };
        double multiplier = ArcforgeConfig.OIL_AMOUNT_MULTIPLIER.getAsDouble();
        for (int i = 0; i < kinds.length; i++) {
            long amount = Math.round(kinds[i][0] * 1_000L * multiplier);
            OilDeposits.Deposit deposit = new OilDeposits.Deposit("minecraft:overworld", 40 + i, 40, order[i], amount, 40 * 8 + 4, 40 * 8 + 4, kinds[i][1],
                    -20, false, false);
            OilCavern cavern = OilCavern.of(deposit, 4321L);
            double blocks = amount / 1_000.0;
            helper.assertTrue(Math.abs(cavern.capacity() - blocks) <= blocks * 0.2,
                    order[i] + " of " + (long) blocks + " buckets holds " + cavern.capacity() + " blocks");
            helper.assertTrue(Math.abs(cavern.across - 2 * cavern.high) < 0.01, order[i] + " is " + cavern.across + " across and " + cavern.high + " high");
            helper.assertTrue(cavern.levelFor(amount) > cavern.levelFor(amount / 2) && cavern.levelFor(amount / 2) > cavern.levelFor(0)
                    && cavern.levelFor(0) == cavern.minY, order[i] + "'s level doesn't fall as it drains");
            helper.assertTrue(OilCavern.of(deposit, 4321L).capacity() == cavern.capacity(), "The same deposit gave a different cavern");
        }
        OilDeposits.Deposit giant = new OilDeposits.Deposit("minecraft:overworld", 50, 50, DepositKind.GIANT, 100_000_000L, 404, 404, 4, -20, false, false);
        OilCavern big = OilCavern.of(giant, 1L);
        helper.assertTrue(big.across * 2 >= 55 && big.across * 2 <= 85, "A full Giant is " + Math.round(big.across * 2) + " across");
        OilDeposits.Deposit capped = new OilDeposits.Deposit("minecraft:overworld", 51, 50, DepositKind.FIELD, 20_000_000L, 412, 404, 3, -20, false, true);
        OilCavern withCap = OilCavern.of(capped, 1L);
        helper.assertTrue(withCap.domeCells() > 0 && !withCap.holdsOil(withCap.maxY) && withCap.holdsOil(withCap.minY), "The gas cap's dome is wrong");
        helper.assertTrue(withCap.levelFor(capped.amount()) <= withCap.domeBottom, "Oil rises into the gas cap");
        helper.succeed();
    }

    // Deposits roll their sulfur and gas cap from the seed alone; sour-rich regions are sour more often.
    static void depositTraits(GameTestHelper helper) {
        int size = ArcforgeConfig.OIL_REGION_SIZE.getAsInt();
        int plain = 0, plainSour = 0, rich = 0, richSour = 0, caps = 0;
        for (int x = 0; x < 600; x++) {
            OilDeposits.Deposit a = OilDeposits.roll(77L, "minecraft:overworld", x, 5, size, true, false);
            OilDeposits.Deposit again = OilDeposits.roll(77L, "minecraft:overworld", x, 5, size, true, false);
            helper.assertTrue(a == null ? again == null : a.equals(again), "Region " + x + " rolled two different deposits");
            OilDeposits.Deposit sour = OilDeposits.roll(77L, "minecraft:overworld", x, 5, size, true, true);
            if (a != null) {
                plain++;
                plainSour += a.sour() ? 1 : 0;
                caps += a.gasCap() ? 1 : 0;
                helper.assertTrue(sour != null && sour.amount() == a.amount() && sour.gasCap() == a.gasCap(),
                        "Sour-richness changed more than the sulfur");
                rich++;
                richSour += sour.sour() ? 1 : 0;
            }
        }
        double sourChance = ArcforgeConfig.OIL_SOUR_CHANCE.getAsDouble(), capChance = ArcforgeConfig.OIL_GAS_CAP_CHANCE.getAsDouble();
        helper.assertTrue(plain > 50, "Only " + plain + " deposits in 600 rich regions");
        helper.assertTrue(Math.abs(plainSour / (double) plain - sourChance) < 0.12, plainSour + " of " + plain + " deposits are sour");
        helper.assertTrue(richSour >= plainSour, "Sour-rich regions are sour less often (" + richSour + " against " + plainSour + ")");
        helper.assertTrue(Math.abs(caps / (double) plain - capChance) < 0.12, caps + " of " + plain + " deposits have a gas cap");
        helper.assertTrue(rich == plain, "Sour-richness changed whether there's a deposit");
        helper.succeed();
    }

    // A test deposit with its own small cavern, centred in the middle of the chunk holding the test's (16, 0, 16) at
    // the test's height plus up.
    private record TestCavern(OilDeposits.Deposit deposit, OilCavern cavern, BlockPos centre) {}

    private static TestCavern testCavern(GameTestHelper helper, int up, double across, double high, long buckets, boolean sour, boolean gasCap) {
        BlockPos corner = helper.absolutePos(new BlockPos(16, 0, 16));
        BlockPos centre = new BlockPos((corner.getX() >> 4) * 16 + 8, corner.getY() + up, (corner.getZ() >> 4) * 16 + 8);
        OilDeposits.Deposit deposit = OilDeposits.putTestDeposit(helper.getLevel(), centre, DepositKind.POCKET, buckets * 1_000L, centre.getY(), sour, gasCap);
        OilCavern cavern = OilCavern.test(centre.getX(), centre.getY(), centre.getZ(), across, high, gasCap);
        OilCaverns.putTestCavern(deposit, cavern);
        return new TestCavern(deposit, cavern, centre);
    }

    private static void clear(GameTestHelper helper, TestCavern test) {
        OilCaverns.clearTestCavern(helper.getLevel(), test.deposit(), test.centre());
        OilDeposits.clearTestDeposit(helper.getLevel(), test.centre());
    }

    // Fills the cavern's bounds (shell included) with stone, so carving it doesn't depend on the generated ground where the
    // test lands: a chest, spawner or other block a cavern may not replace there would leave a cell without oil.
    private static void solidGround(ServerLevel level, OilCavern cavern) {
        int[] b = cavern.bounds();
        for (BlockPos pos : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // {oil sources in the cavern, the highest y holding one (or minY - 1)}.
    private static int[] oil(ServerLevel level, OilCavern cavern, net.minecraft.world.level.material.Fluid fluid) {
        int sources = 0, top = cavern.minY - 1;
        int[] b = cavern.bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int z = b[2]; z <= b[5]; z++) {
                for (int y = cavern.minY; y <= cavern.maxY; y++) {
                    FluidState state = level.getFluidState(pos.set(x, y, z));
                    if (cavern.inside(x, y, z) && state.isSource() && state.getType().isSame(fluid)) {
                        sources++;
                        top = Math.max(top, y);
                    }
                }
            }
        }
        return new int[] { sources, top };
    }

    // Carving a sour cavern fills it with Sour Crude Oil up to its deposit's level inside a stone shell. After its first
    // check, source blocks taken out (a bucket) come off the deposit at 1,000 mB each, once; pumping lowers the oil from
    // the top down.
    static void cavernKeepsInStep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestCavern test = testCavern(helper, 6, 3.0, 2.0, 1_000, true, false);
        OilCavern cavern = test.cavern();
        Runnable release = OilGameTests.forceChunks(helper, new BlockPos(16, 0, 16));
        long full = cavern.capacity() * 1_000L;
        OilDeposits deposits = OilDeposits.get(level.getServer());
        deposits.setExtracted(test.deposit(), test.deposit().amount() - full);
        solidGround(level, cavern);
        helper.assertTrue(OilCaverns.place(level, test.centre()), "Nothing was carved");
        var sour = ModFluids.SOUR_CRUDE_OIL.get();
        int[] start = oil(level, cavern, sour);
        helper.assertTrue(start[0] == cavern.capacity(), "The cavern holds " + start[0] + " of " + cavern.capacity() + " sources");
        int chunkX = test.centre().getX() >> 4, chunkZ = test.centre().getZ() >> 4;
        // Its first check counts what generation left, charging nothing.
        long untouched = deposits.remaining(test.deposit());
        OilCaverns.reconcile(level, chunkX, chunkZ, true);
        helper.assertTrue(deposits.remaining(test.deposit()) == untouched, "The first check charged the deposit");
        BlockPos shell = test.centre().below((int) Math.ceil(cavern.high) + 1);
        helper.assertTrue(!level.getBlockState(shell).isAir() && level.getFluidState(shell).isEmpty(), "No shell under the cavern");
        // Three buckets out of the top.
        int taken = 0;
        int[] b = cavern.bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = start[1]; y >= cavern.minY && taken < 3; y--) {
            for (int x = b[0]; x <= b[3] && taken < 3; x++) {
                for (int z = b[2]; z <= b[5] && taken < 3; z++) {
                    if (level.getFluidState(pos.set(x, y, z)).isSource()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        taken++;
                    }
                }
            }
        }
        long before = deposits.remaining(test.deposit());
        OilCaverns.reconcile(level, chunkX, chunkZ, true);
        helper.assertTrue(deposits.remaining(test.deposit()) == before - taken * 1_000L,
                "Taking " + taken + " sources left " + deposits.remaining(test.deposit()) + " of " + before);
        OilCaverns.reconcile(level, chunkX, chunkZ, true);
        helper.assertTrue(deposits.remaining(test.deposit()) == before - taken * 1_000L, "The same missing sources were charged twice");
        // Pumped down to half: the oil's top falls, and no oil is left above the new level.
        deposits.setExtracted(test.deposit(), test.deposit().amount() - full / 2);
        OilCaverns.reconcile(level, chunkX, chunkZ, false);
        int[] half = oil(level, cavern, sour);
        int levelNow = cavern.levelFor(deposits.remaining(test.deposit()));
        helper.assertTrue(half[1] < levelNow && half[1] < start[1], "The oil's top is at " + half[1] + " (level " + levelNow + ")");
        helper.assertTrue(half[0] <= full / 2_000 + 1, half[0] + " sources left for " + full / 2_000 + " buckets");
        clear(helper, test);
        // Take the carved cavern and its shell away again.
        for (BlockPos cell : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) {
            if (cavern.inside(cell.getX(), cell.getY(), cell.getZ()) || cavern.inShell(cell.getX(), cell.getY(), cell.getZ())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        release.run();
        helper.succeed();
    }

    // Drilled into a gas cap's dome, the Oil Derrick makes a gas well; a Gas Wellhead on it flows Natural Gas with no FE
    // and a Pumpjack can't be built there.
    static void gasWell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The cavern's top cell (in the dome) is three blocks under the rig floor, its dome big enough not to run dry here.
        TestCavern test = testCavern(helper, -5, 6.0, 3.0, 1_000, false, true);
        BlockPos bore = test.centre().above(6);
        Runnable release = OilGameTests.forceChunks(helper, new BlockPos(16, 0, 16));
        OilCaverns.markGenerated(level, bore);
        helper.assertTrue(!test.cavern().holdsOil(test.cavern().topAt(bore.getX(), bore.getZ())), "The top under the bore isn't in the dome");
        BlockPos min = bore.offset(-1, 0, -1);
        net.zagdrath.arcforge.multiblock.OilfieldStructures.Rig rig = new net.zagdrath.arcforge.multiblock.OilfieldStructures.Rig(min, Direction.NORTH);
        for (BlockPos part : rig.parts()) {
            level.setBlock(part, part.equals(rig.controller())
                    ? ModBlocks.OIL_DERRICK_CONTROLLER.get().defaultBlockState().setValue(net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock.FACING,
                            Direction.NORTH)
                    : (rig.isFrame(part) ? ModBlocks.DERRICK_FRAME.get() : ModBlocks.DERRICK_CASING.get()).defaultBlockState(), Block.UPDATE_ALL);
        }
        int ticks = ArcforgeConfig.DERRICK_TICKS_PER_BLOCK.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    OilDerrickBlockEntity derrick = (OilDerrickBlockEntity) level.getBlockEntity(rig.controller());
                    helper.assertTrue(derrick != null && derrick.isFormed(), "The rig didn't form");
                    derrick.getEnergy().set(derrick.getEnergy().getCapacityAsInt());
                    SteamGameTests.fill(derrick.getFluidHandler(null), FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 8_000);
                    derrick.getItems().setStack(OilDerrickBlockEntity.SLOT_PIPES, new ItemStack(ModItems.STEEL_PIPE.get(), 4));
                })
                .thenIdle(ticks * 3 + 10)
                .thenExecute(() -> {
                    helper.assertTrue(WellHeadBlock.isGasWell(level.getBlockState(bore)), "It didn't strike gas: " + level.getBlockState(bore));
                    level.setBlock(bore.above(), ModBlocks.GAS_WELLHEAD.get().defaultBlockState().setValue(GasWellheadBlock.FACING, Direction.EAST),
                            Block.UPDATE_ALL);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    GasWellheadBlockEntity wellhead = (GasWellheadBlockEntity) level.getBlockEntity(bore.above());
                    helper.assertTrue(wellhead != null && wellhead.isOnWell(), "The Gas Wellhead isn't on the well");
                    int rate = ArcforgeConfig.GAS_WELLHEAD_RATE.getAsInt();
                    int gas = wellhead.getTank().getAmount();
                    helper.assertTrue(gas > 0 && wellhead.getTank().getResource(0).is(ModFluids.NATURAL_GAS.get()), "No Natural Gas");
                    helper.assertTrue(gas <= Math.max(rate * 21, 1), "It flowed " + gas + " mB in 20 ticks");
                    OilDeposits deposits = OilDeposits.get(level.getServer());
                    long full = OilDeposits.gasAmount(test.deposit(), level.getSeed());
                    helper.assertTrue(deposits.gasRemaining(test.deposit(), level.getSeed()) == full - gas, "The gas cap didn't go down by what flowed");
                    // Spent: a trickle.
                    deposits.setGasExtracted(test.deposit(), full);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    GasWellheadBlockEntity wellhead = (GasWellheadBlockEntity) level.getBlockEntity(bore.above());
                    double trickle = ArcforgeConfig.GAS_WELLHEAD_TRICKLE.getAsInt() / 20.0;
                    helper.assertTrue(Math.abs(wellhead.getRate() - trickle) < 1.0E-6, "A spent cap flows " + wellhead.getRate() + " mB/t");
                    level.setBlock(bore.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    // A pumpjack round a gas well doesn't form.
                    net.zagdrath.arcforge.multiblock.OilfieldStructures.Unit unit = new net.zagdrath.arcforge.multiblock.OilfieldStructures.Unit(bore,
                            Direction.SOUTH);
                    for (BlockPos part : unit.parts()) {
                        level.setBlock(part, part.equals(unit.controller())
                                ? ModBlocks.PUMPJACK_CONTROLLER.get().defaultBlockState()
                                        .setValue(net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock.FACING, Direction.SOUTH)
                                : ModBlocks.PUMPJACK_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos controller = new net.zagdrath.arcforge.multiblock.OilfieldStructures.Unit(bore, Direction.SOUTH).controller();
                    helper.assertTrue(level.getBlockEntity(controller) instanceof PumpjackBlockEntity pumpjack && !pumpjack.isFormed(),
                            "A pumpjack formed on a gas well");
                    clear(helper, test);
                    release.run();
                })
                .thenSucceed();
    }

    // A sour deposit (in a chunk generated with caverns) gives Sour Crude Oil to its pumpjack.
    static void pumpjackPumpsSour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos well = new BlockPos(3, 1, 6);
        Runnable release = OilGameTests.forceChunks(helper, well.offset(-1, 0, -4), well.offset(1, 0, 4));
        BlockPos controller = OilGameTests.buildPumpjack(helper, well).controller();
        BlockPos at = helper.absolutePos(well);
        OilDeposits.putTestDeposit(level, at, DepositKind.POCKET, 2_000_000L, -10, true, false);
        OilCaverns.markGenerated(level, at);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    PumpjackBlockEntity pumpjack = (PumpjackBlockEntity) level.getBlockEntity(controller);
                    helper.assertTrue(pumpjack != null && pumpjack.isFormed(), "The pumpjack didn't form");
                    pumpjack.getEnergy().set(pumpjack.getEnergy().getCapacityAsInt());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    PumpjackBlockEntity pumpjack = (PumpjackBlockEntity) level.getBlockEntity(controller);
                    helper.assertTrue(pumpjack.getCrude().getAmount() > 0 && pumpjack.getCrude().getResource(0).is(ModFluids.SOUR_CRUDE_OIL.get()),
                            "It pumped " + pumpjack.getCrude().getResource(0));
                    OilDeposits.get(level.getServer()).clearCavernChunk(level.dimension().identifier().toString(), at.getX() >> 4, at.getZ() >> 4);
                    OilDeposits.clearTestDeposit(level, at);
                    release.run();
                })
                .thenSucceed();
    }

    // Sour fuels burn at 75% of the heat, 100°C cooler, and nothing else takes them; Natural Gas burns.
    static void sourFuels(GameTestHelper helper) {
        double share = ArcforgeConfig.SOUR_FUEL_HEAT_SHARE.getAsDouble();
        int drop = ArcforgeConfig.SOUR_FUEL_TEMPERATURE_DROP.getAsInt();
        net.minecraft.world.level.material.Fluid[][] pairs = { { ModFluids.SOUR_DIESEL.get(), ModFluids.DIESEL.get() },
                { ModFluids.SOUR_KEROSENE.get(), ModFluids.KEROSENE.get() } };
        for (net.minecraft.world.level.material.Fluid[] pair : pairs) {
            BurnerFuel sour = BurnerFuel.of(pair[0]), clean = BurnerFuel.of(pair[1]);
            helper.assertTrue(sour != null && clean != null, pair[0] + " doesn't burn");
            helper.assertTrue(Math.abs(sour.huPerMb() - clean.huPerMb() * share) <= 1, pair[0] + " gives " + sour.huPerMb() + " HU/mB");
            helper.assertTrue(sour.burnTemperature(Integer.MAX_VALUE) == clean.burnTemperature(Integer.MAX_VALUE) - drop,
                    pair[0] + " burns at " + sour.burnTemperature(Integer.MAX_VALUE));
            helper.assertTrue(!sour.gasTurbine(), pair[0] + " runs the Gas Turbine Array");
            helper.assertTrue(EngineFuel.of(pair[0]) == null, pair[0] + " runs the Diesel Generator");
            helper.assertTrue(JetpackFuel.of(pair[0]) == null, pair[0] + " fuels the Jetpack");
        }
        BurnerFuel gas = BurnerFuel.of(ModFluids.NATURAL_GAS.get());
        helper.assertTrue(gas != null && gas.huPerMb() == ArcforgeConfig.NATURAL_GAS_HU_PER_MB.getAsInt()
                && gas.burnTemperature(Integer.MAX_VALUE) == ArcforgeConfig.NATURAL_GAS_BURN_TEMPERATURE.getAsInt() && gas.gasTurbine(),
                "Natural Gas burns wrong");
        helper.assertTrue(net.zagdrath.arcforge.steam.Gases.isGas(ModFluids.NATURAL_GAS.get()) && net.zagdrath.arcforge.steam.Gases.isGas(
                ModFluids.HYDROGEN_SULFIDE.get()), "Natural Gas and Hydrogen Sulfide aren't gases");
        helper.assertTrue(ModFluids.SOUR_CRUDE_OIL.get().getBucket() == ModItems.SOUR_CRUDE_OIL_BUCKET.get(), "Sour Crude Oil has no bucket");
        helper.succeed();
    }

    // The survey of a sour, gas-capped deposit says so; with Paper it writes a Survey Record (using the Paper), which
    // marks a filled map once per spot.
    @SuppressWarnings("removal")
    static void surveyRecords(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(new BlockPos(1, 1, 1));
        OilDeposits.putTestDeposit(level, here, DepositKind.FIELD, 18_000_000L, -20, true, true);
        SurveyRecord legacy = SeismicSurveyToolItem.survey(level, here);
        OilDeposits.get(level.getServer()).clearCavernChunk(level.dimension().identifier().toString(), here.getX() >> 4, here.getZ() >> 4);
        SurveyRecord old = SeismicSurveyToolItem.survey(level, here);
        helper.assertTrue(!old.sour() && !old.gasCap(), "A deposit under an older chunk shows its sulfur or gas cap");
        OilCaverns.markGenerated(level, here);
        SurveyRecord survey = SeismicSurveyToolItem.survey(level, here);
        helper.assertTrue(survey.kind().orElse(null) == DepositKind.FIELD && survey.sour() && survey.gasCap() && survey.buckets() == 18_000,
                "The survey read " + survey + " (before: " + legacy + ")");
        ServerPlayer player = TestFixtures.player(helper);
        player.getInventory().add(new ItemStack(Items.PAPER, 2));
        SeismicSurveyToolItem.recordOnPaper(player, survey);
        int paper = player.getInventory().countItem(Items.PAPER);
        helper.assertTrue(paper == 1 || player.getAbilities().instabuild && paper == 2, paper + " Paper left");
        ItemStack record = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.SURVEY_RECORD.get())) {
                record = player.getInventory().getItem(slot);
            }
        }
        helper.assertTrue(survey.equals(record.get(ModDataComponents.SURVEY_RECORD.get())), "No Survey Record of the survey");
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        SurveyRecordItem.mark(map, survey);
        SurveyRecordItem.mark(map, survey);
        List<SurveyRecord> markers = map.getOrDefault(ModDataComponents.OIL_MARKERS.get(), List.of());
        var decorations = map.get(DataComponents.MAP_DECORATIONS);
        helper.assertTrue(markers.size() == 1 && decorations != null && decorations.decorations().containsKey("arcforge_oil_" + survey.x() + "_" + survey.z()),
                "The map has " + markers.size() + " markers and " + decorations);
        OilDeposits.get(level.getServer()).clearCavernChunk(level.dimension().identifier().toString(), here.getX() >> 4, here.getZ() >> 4);
        OilDeposits.clearTestDeposit(level, here);
        helper.succeed();
    }
}
