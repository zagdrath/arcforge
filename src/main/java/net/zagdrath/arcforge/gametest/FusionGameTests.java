/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.VesselControllerBlock;
import net.zagdrath.arcforge.blockentity.fusion.FusionPlantBlockEntity;
import net.zagdrath.arcforge.fusion.FusionExhaustPool;
import net.zagdrath.arcforge.fusion.Lithium6;
import net.zagdrath.arcforge.fusion.PlasmaSim;
import net.zagdrath.arcforge.fusion.TokamakLayout;
import net.zagdrath.arcforge.fusion.Tritium;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The fusion tier: the plasma model (the smallest tokamak, R3, ignites to a few hundred MW and ends its pulse cleanly; too dense a
// plasma disrupts; a stellarator runs steady), the tokamak's layout, the exhaust pool's bookkeeping, tritium decay,
// lithium-6 enrichment, the neutron shielding figures, and the three plant vessels forming and running.
public final class FusionGameTests {
    private FusionGameTests() {}

    // Runs a plasma the way the controller does, once a second, at field tesla, fuelled to frac of the density limit;
    // returns the last event and the peak fusion power (MW) through out[0].
    private static PlasmaSim.Event run(PlasmaSim sim, double field, double heating, double frac, int seconds, double[] out) {
        sim.bField = field;
        sim.ready = true;
        sim.ipTarget = sim.maxCurrent();
        PlasmaSim.Event last = PlasmaSim.Event.NONE;
        for (int sec = 0; sec < seconds; sec++) {
            sim.pEch = sec < 30 ? 10.0 : 0.0;
            sim.pNbi = heating;
            double fuel = sim.alive ? sim.fuelDemand(frac, 1.0) : 2.0;
            sim.srcD = fuel / 2.0;
            sim.srcT = fuel / 2.0;
            PlasmaSim.Event event = sim.step();
            out[0] = Math.max(out[0], sim.pFusion);
            if (event != PlasmaSim.Event.NONE) {
                last = event;
                if (event.name().contains("DISRUPTION") || event.name().contains("COLLAPSE") || event == PlasmaSim.Event.PULSE_END) {
                    break;
                }
            }
        }
        return last;
    }

    static void plasma(GameTestHelper helper) {
        double[] peak = { 0.0 };
        PlasmaSim r3 = new PlasmaSim(new TokamakLayout(3).majorRadiusMetres(), false, new PlasmaSim.Params());
        PlasmaSim.Event end = run(r3, 8.0, 50.0, 0.8, 900, peak);
        helper.assertTrue(peak[0] > 100.0 && peak[0] < 1_000.0, "An R3 tokamak peaked at " + peak[0] + " MW");
        helper.assertTrue(end == PlasmaSim.Event.PULSE_END, "An R3 pulse ended with " + end);
        helper.assertTrue(peak[0] / 50.0 > 2.0, "An R3 tokamak didn't reach Q 2");
        double[] stellarator = { 0.0 };
        PlasmaSim steady = new PlasmaSim(new TokamakLayout(5).majorRadiusMetres(), true, new PlasmaSim.Params());
        PlasmaSim.Event stellaratorEnd = run(steady, 6.65, 50.0, 0.8, 600, stellarator);
        helper.assertTrue(stellarator[0] > 0.0 && steady.alive && (steady.pulseLeft() < 0.0 || Double.isInfinite(steady.pulseLeft())) && !stellaratorEnd.name().contains("DISRUPTION"),
                "A stellarator didn't run steady: " + stellaratorEnd + ", " + stellarator[0] + " MW");
        double[] dense = { 0.0 };
        PlasmaSim overfed = new PlasmaSim(new TokamakLayout(4).majorRadiusMetres(), false, new PlasmaSim.Params());
        PlasmaSim.Event disrupted = run(overfed, 7.5, 50.0, 1.3, 600, dense);
        helper.assertTrue(disrupted == PlasmaSim.Event.DISRUPTION_DENSITY, "Fuelling past the Greenwald limit gave " + disrupted);
        // A pulse's planned ramp-down cools the plasma with its current still flowing: it ends the pulse, never a collapse.
        double[] ramped = { 0.0 };
        PlasmaSim r4 = new PlasmaSim(new TokamakLayout(4).majorRadiusMetres(), false, new PlasmaSim.Params());
        PlasmaSim.Event rampEnd = run(r4, 7.5, 30.0, 0.85, 3_000, ramped);
        helper.assertTrue(rampEnd == PlasmaSim.Event.PULSE_END, "An R4 pulse on 30 MW ended with " + rampEnd);
        // A weakly pumped vessel (one Cryopump: a quarter of the exhaust taken, the rest recycling back in) holds its density
        // setpoint instead of being fuelled past the Greenwald limit, at the full field and at Liquid Nitrogen's.
        for (double field : new double[] { 8.0, 3.2 }) {
            double[] pumped = { 0.0 };
            PlasmaSim weak = new PlasmaSim(new TokamakLayout(3).majorRadiusMetres(), false, new PlasmaSim.Params());
            weak.pumping = 0.25;
            PlasmaSim.Event weakEnd = run(weak, field, 50.0, 0.85, 900, pumped);
            helper.assertTrue(!weakEnd.name().contains("DISRUPTION"), "An R3 on one Cryopump at " + field + " T ended with " + weakEnd);
        }
        helper.succeed();
    }

    static void layout(GameTestHelper helper) {
        TokamakLayout r3 = new TokamakLayout(3);
        helper.assertTrue(r3.width() == 9 && r3.height() == 9, "R3 is " + r3.width() + "x" + r3.height());
        helper.assertTrue(r3.count(TokamakLayout.Part.TOROIDAL_COIL) == 26 && r3.count(TokamakLayout.Part.FIRST_WALL) == 44
                && r3.count(TokamakLayout.Part.DIVERTOR) == 20 && r3.count(TokamakLayout.Part.BLANKET) == 152
                && r3.count(TokamakLayout.Part.POLOIDAL_COIL) == 48 && r3.count(TokamakLayout.Part.CENTRAL_SOLENOID) == 7
                && r3.count(TokamakLayout.Part.CRYOSTAT_WALL) == 288 && r3.count(TokamakLayout.Part.CRYOSTAT_LID) == 0, "R3's part counts changed");
        TokamakLayout r7 = new TokamakLayout(7);
        helper.assertTrue(r7.width() == 17 && r7.height() == 13, "R7 is " + r7.width() + "x" + r7.height());
        helper.assertTrue(r3.at(0, 0) == TokamakLayout.Part.CENTRAL_SOLENOID && r3.at(1, 0) == TokamakLayout.Part.FIRST_WALL
                && r3.at(2, 0) == TokamakLayout.Part.AIR && r3.at(3, 0) == TokamakLayout.Part.BLANKET
                && r3.at(r3.outer(), 0) == TokamakLayout.Part.CRYOSTAT_WALL && r3.at(2, r3.top()) == TokamakLayout.Part.TOROIDAL_COIL
                && r3.at(1, -r3.top()) == TokamakLayout.Part.POLOIDAL_COIL, "R3's layers are wrong");
        // Each size keeps the power its larger predecessor had: R3 stands for 2.48 m, R7 for 6.2 m.
        helper.assertTrue(Math.abs(r3.majorRadiusMetres() - 2.48) < 1.0E-9 && Math.abs(r7.majorRadiusMetres() - 6.2) < 1.0E-9,
                "The physics radii moved");
        helper.succeed();
    }

    static void tritiumAndLithium(GameTestHelper helper) {
        double halfLife = net.zagdrath.arcforge.config.ArcforgeConfig.TRITIUM_HALF_LIFE.getAsDouble() * 24_000.0;
        helper.assertTrue(Math.abs(Tritium.decayFraction(halfLife) - 0.5) < 1.0E-6 && Tritium.decayFraction(0) == 0.0, "Tritium's half-life is off");
        FusionExhaustPool pool = new FusionExhaustPool();
        helper.assertTrue(pool.shares().equals(FusionExhaustPool.DEFAULT), "An empty pool isn't an even D-T mix");
        pool.add(30.0, 10.0, 0.0, 60.0);
        FusionExhaustPool.Shares taken = pool.take(50.0);
        helper.assertTrue(Math.abs(taken.helium() - 0.6) < 1.0E-9 && Math.abs(pool.shares().deuterium() - 0.3) < 1.0E-9, "The pool's shares are off");
        pool.decay(1.0);
        helper.assertTrue(pool.shares().tritium() < 1.0E-9 && pool.shares().helium3() > 0.0, "Decay didn't turn the tritium into helium-3");
        ItemStack natural = new ItemStack(ModItems.LITHIUM_6_FLUORIDE.get());
        helper.assertTrue(Lithium6.minimum() > Lithium6.NATURAL && Lithium6.product(Lithium6.max()).is(ModItems.LITHIUM_6_FLUORIDE.get())
                && Lithium6.product(Lithium6.NATURAL + Lithium6.step()).is(ModItems.ENRICHED_LITHIUM_FLUORIDE.get()), "Enrichment's products are wrong");
        helper.assertTrue(Lithium6.assay(natural) >= Lithium6.minimum(), "Lithium-6 Fluoride isn't enriched enough to use");
        helper.succeed();
    }

    // Light nuclei stop neutrons, lead hardly does: the data map's neutron figures, and a Hazmat Suit doesn't cut them.
    static void neutronShielding(GameTestHelper helper) {
        var water = RadiationField.shielding(Blocks.WATER);
        var lead = RadiationField.shielding(ModBlocks.LEAD_BLOCK.get());
        var blanket = RadiationField.shielding(ModBlocks.BREEDING_BLANKET.get());
        helper.assertTrue(water != null && lead != null && blanket != null, "Shielding entries are missing");
        helper.assertTrue(water.transmission(true) < lead.transmission(true) && lead.transmission(false) < lead.transmission(true),
                "Neutrons aren't shielded by light nuclei rather than lead");
        helper.assertTrue(blanket.transmission(true) <= 0.3F, "The blanket doesn't stop neutrons");
        helper.succeed();
    }

    private static int fill(ResourceHandler<FluidResource> handler, FluidResource resource, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // A hollow box of casing, its controller in the middle of the north wall's second row.
    private static BlockPos box(GameTestHelper helper, BlockPos min, int w, int h, int d, Block casing, Block controller) {
        BlockPos at = min.offset(w / 2, 1, 0);
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos pos = min.offset(x, y, z);
                    boolean wall = x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1;
                    if (pos.equals(at)) {
                        helper.setBlock(pos, controller.defaultBlockState().setValue(VesselControllerBlock.FACING, Direction.NORTH));
                    } else {
                        helper.setBlock(pos, wall ? casing : Blocks.AIR);
                    }
                }
            }
        }
        return at;
    }

    // A formed column's ladder: the middle block of its left side can be climbed all the way up (its neighbour can't),
    // the well in front of the ladder is open (a player stands in it, against the shell), and the top deck can be stood
    // on.
    private static void columnLadder(GameTestHelper helper, FusionPlantBlockEntity column) {
        var level = helper.getLevel();
        var box = column.getBox();
        helper.assertTrue(box != null, "The column has no box");
        var frame = box.frame();
        float h = frame.height() * 16.0F;
        for (int y = 0; y < frame.height(); y++) {
            BlockPos ladder = world(box.min(), frame.worldBlock(8.0F, y * 16.0F + 8.0F, 24.0F));
            BlockPos beside = world(box.min(), frame.worldBlock(8.0F, y * 16.0F + 8.0F, 8.0F));
            var state = level.getBlockState(ladder);
            helper.assertTrue(state.isLadder(level, ladder, null) == net.zagdrath.arcforge.steam.CryogenicColumnMesh.isLadderBlock(0, y, 1, h),
                    "Ladder block " + y + " climbable: " + state.isLadder(level, ladder, null));
            helper.assertFalse(level.getBlockState(beside).isLadder(level, beside, null), "The block beside the ladder can be climbed");
            // The well: the outer 5 px of the ladder's block, across the middle 10 px, clear of the bottom deck up.
            if (y > 0) {
                float[] a = frame.worldPoint(0.5F, y * 16.0F + 1.0F, 19.0F), b = frame.worldPoint(5.5F, y * 16.0F + 15.0F, 29.0F);
                var well = new net.minecraft.world.phys.AABB(a[0] / 16.0, a[1] / 16.0, a[2] / 16.0, b[0] / 16.0, b[1] / 16.0, b[2] / 16.0)
                        .move(box.min());
                for (var part : state.getCollisionShape(level, ladder).toAabbs()) {
                    helper.assertFalse(part.move(ladder).intersects(well), "Something solid in the ladder's well at " + y + ": " + part);
                }
            }
        }
        float deck = net.zagdrath.arcforge.steam.CryogenicColumnMesh.topDeck(h);
        BlockPos corner = world(box.min(), frame.worldBlock(4.0F, deck - 0.5F, 4.0F));
        helper.assertFalse(level.getBlockState(corner).getCollisionShape(level, corner).isEmpty(), "Nothing to stand on at the top deck's corner");
    }

    private static BlockPos world(BlockPos min, int[] offset) {
        return min.offset(offset[0], offset[1], offset[2]);
    }

    // The Cryogenic Plant liquefies Nitrogen; the column distils air with no feed; the Tritium Plant splits Fusion
    // Exhaust.
    static void plants(GameTestHelper helper) {
        BlockPos cold = box(helper, new BlockPos(1, 1, 1), 3, 3, 3, ModBlocks.CRYOGENIC_PLANT_CASING.get(), ModBlocks.CRYOGENIC_PLANT_CONTROLLER.get());
        BlockPos column = box(helper, new BlockPos(5, 1, 1), 3, 6, 3, ModBlocks.CRYOGENIC_COLUMN_CASING.get(), ModBlocks.CRYOGENIC_COLUMN_CONTROLLER.get());
        BlockPos tritium = box(helper, new BlockPos(1, 1, 5), 3, 3, 3, ModBlocks.TRITIUM_PLANT_CASING.get(), ModBlocks.TRITIUM_PLANT_CONTROLLER.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (BlockPos at : new BlockPos[] { cold, column, tritium }) {
                        FusionPlantBlockEntity plant = helper.getBlockEntity(at, FusionPlantBlockEntity.class);
                        plant.checkNow();
                        helper.assertTrue(plant.isFormed(), plant.getKind() + " didn't form");
                        CrushingGameTests.charge(plant.getEnergy(), 1_000_000);
                    }
                    columnLadder(helper, helper.getBlockEntity(column, FusionPlantBlockEntity.class));
                    FusionPlantBlockEntity plant = helper.getBlockEntity(cold, FusionPlantBlockEntity.class);
                    helper.assertTrue(fill(plant.getInput(), FluidResource.of(ModFluids.NITROGEN.get()), 4_000) > 0, "The Cryogenic Plant refused Nitrogen");
                    FusionPlantBlockEntity separator = helper.getBlockEntity(tritium, FusionPlantBlockEntity.class);
                    helper.assertTrue(fill(separator.getInput(), FluidResource.of(ModFluids.FUSION_EXHAUST.get()), 4_000) > 0,
                            "The Tritium Plant refused Fusion Exhaust");
                    helper.assertTrue(fill(separator.getInput(), FluidResource.of(ModFluids.NITROGEN.get()), 1_000) == 0, "The Tritium Plant took Nitrogen");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockEntity(cold, FusionPlantBlockEntity.class).getOutput(0).getAmount() > 0, "No Liquid Nitrogen yet");
                    helper.assertTrue(helper.getBlockEntity(column, FusionPlantBlockEntity.class).getOutput(0).getAmount() > 0, "The column made nothing");
                    FusionPlantBlockEntity separator = helper.getBlockEntity(tritium, FusionPlantBlockEntity.class);
                    helper.assertTrue(separator.getOutput(0).getAmount() + separator.getOutput(1).getAmount() > 0, "The Tritium Plant made nothing");
                })
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(cold, FusionPlantBlockEntity.class).getOutput(0).getResource(0).is(ModFluids.LIQUID_NITROGEN.get()),
                            "The plant made the wrong liquid");
                    FusionPlantBlockEntity distiller = helper.getBlockEntity(column, FusionPlantBlockEntity.class);
                    helper.assertTrue(distiller.getOutput(0).getResource(0).is(ModFluids.NITROGEN.get())
                            && distiller.getOutput(2).getResource(0).is(ModFluids.ARGON.get()), "Air didn't distil to Nitrogen and Argon");
                })
                .thenSucceed();
    }
}
