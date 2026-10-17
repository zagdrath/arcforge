/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.nuclear.CoriumBlock;
import net.zagdrath.arcforge.blockentity.nuclear.DryCaskBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.FreezePlugBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.GeologicalRepositoryBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.SpentFuelRackBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.WasteLog;
import net.zagdrath.arcforge.nuclear.reactor.CoreLattice;
import net.zagdrath.arcforge.nuclear.reactor.ReactorParams;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.nuclear.safety.Containment;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.nuclear.safety.ReactorAlarm;
import net.zagdrath.arcforge.nuclear.safety.ReactorExplosions;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.ReprocessingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Phase 3 of the nuclear tier: the containment's flood fill (sealed, unlined, open); explosions scaling and capped;
// what ignites hydrogen; what Corium melts through; fallout and waste logs decaying; the Spent Fuel Pool's depth, the
// Dry Cask taking only cooled rods, the repository's site; the waste and reprocessing recipes (PUREX and THOREX by the
// plant's setting); the lead-bismuth fast core and the molten salt core's physics; and the Freeze Plug melting.
public final class NuclearSafetyGameTests {
    private NuclearSafetyGameTests() {}

    // A containment like the Handbook's: walls two thick (concrete outside, liner inside), 7x7x7 from min, an Airlock
    // through the front's liner with the concrete open in front of it, 3x3x3 inside with a stone "vessel" in the middle.
    private static void buildContainment(GameTestHelper helper, BlockPos min) {
        for (int y = 0; y < 7; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    int depth = Math.min(Math.min(Math.min(x, 6 - x), Math.min(z, 6 - z)), Math.min(y, 6 - y));
                    BlockPos pos = min.offset(x, y, z);
                    if (x == 3 && z >= 5 && (y == 2 || y == 3)) {
                        if (z == 5) {
                            helper.setBlock(pos, ModBlocks.AIRLOCK.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                                    .setValue(DoorBlock.HALF, y == 2 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
                        } else {
                            helper.setBlock(pos, Blocks.AIR);
                        }
                    } else if (depth == 0) {
                        helper.setBlock(pos, ModBlocks.CONTAINMENT_CONCRETE.get());
                    } else if (depth == 1) {
                        helper.setBlock(pos, ModBlocks.STEEL_LINER.get());
                    } else {
                        helper.setBlock(pos, x == 3 && y == 3 && z == 3 ? Blocks.STONE : Blocks.AIR);
                    }
                }
            }
        }
    }

    private static Containment scan(GameTestHelper helper, BlockPos min) {
        BlockPos vessel = helper.absolutePos(min.offset(3, 3, 3));
        Containment containment = new Containment(vessel);
        containment.scan(helper.getLevel(), vessel, vessel, 500);
        return containment;
    }

    static void containmentScan(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        buildContainment(helper, min);
        Containment sealed = scan(helper, min);
        helper.assertTrue(sealed.status() == Containment.Status.SEALED, "Not sealed: " + sealed.status() + " at " + sealed.breach());
        helper.assertTrue(sealed.airVolume() == 26, "Air volume " + sealed.airVolume());
        // Concrete facing the room is no lining.
        helper.setBlock(min.offset(1, 3, 3), ModBlocks.CONTAINMENT_CONCRETE.get());
        Containment unlined = scan(helper, min);
        helper.assertTrue(unlined.status() == Containment.Status.UNLINED, "Bare concrete inside: " + unlined.status());
        // A hole right through the wall: the fill gets out.
        helper.setBlock(min.offset(1, 3, 3), Blocks.AIR);
        helper.setBlock(min.offset(0, 3, 3), Blocks.AIR);
        Containment open = scan(helper, min);
        helper.assertFalse(open.sealed(), "Sealed with a hole in the wall");
        helper.assertFalse(open.contains(helper.absolutePos(min.offset(2, 2, 2))), "An open containment still holds its room");
        helper.succeed();
    }

    // Explosions grow with what's stored and stop at the cap; hydrogen's with the gas. Fire, lava and a lit furnace
    // ignite hydrogen; stone doesn't. Corium melts stone but not obsidian, Containment Concrete or a Core Catcher.
    static void failures(GameTestHelper helper) {
        double small = ReactorExplosions.vesselRadius(1_000.0), big = ReactorExplosions.vesselRadius(1.0E7);
        helper.assertTrue(big > small && small > 0.0, "Explosion radii " + small + ", " + big);
        helper.assertTrue(ReactorExplosions.vesselRadius(1.0E30) <= ArcforgeConfig.EXPLOSION_MAX_RADIUS.getAsDouble() + 1.0E-6, "The cap didn't hold");
        helper.assertTrue(ReactorExplosions.hydrogenRadius(50_000.0) > ReactorExplosions.hydrogenRadius(5_000.0), "More hydrogen, no bigger bang");
        helper.assertTrue(Containment.ignites(Blocks.FIRE.defaultBlockState()) && Containment.ignites(Blocks.LAVA.defaultBlockState()),
                "Fire or lava doesn't ignite hydrogen");
        helper.assertTrue(Containment.ignites(Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true)), "A lit furnace doesn't");
        helper.assertFalse(Containment.ignites(Blocks.STONE.defaultBlockState()), "Stone ignites hydrogen");
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(CoriumBlock.canMelt(level, pos, Blocks.STONE.defaultBlockState()), "Corium won't melt stone");
        for (BlockState resists : new BlockState[] { Blocks.OBSIDIAN.defaultBlockState(), ModBlocks.CONTAINMENT_CONCRETE.get().defaultBlockState(),
                ModBlocks.CORE_CATCHER.get().defaultBlockState(), ModBlocks.SOLIDIFIED_CORIUM.get().defaultBlockState() }) {
            helper.assertFalse(CoriumBlock.canMelt(level, pos, resists), "Corium melts " + resists);
        }
        // Every alarm has its flag, and the klaxon sounds only for the ones that have one.
        int all = 0;
        for (ReactorAlarm alarm : ReactorAlarm.values()) {
            helper.assertTrue((all & alarm.bit()) == 0, "Two alarms share a flag");
            all |= alarm.bit();
        }
        helper.assertTrue(ReactorAlarm.of(all).size() == ReactorAlarm.values().length, "Flags don't round-trip");
        helper.assertFalse(ReactorAlarm.NO_CONTAINMENT.in(ReactorAlarm.klaxonMask()), "No containment sounds the klaxon");
        helper.succeed();
    }

    // Ground contamination halves over its half-lives; a waste log's activity over its own.
    static void decay(GameTestHelper helper) {
        double days = ArcforgeConfig.FALLOUT_SHORT_HALF_LIFE.getAsDouble();
        Fallout.Ground ground = new Fallout.Ground(10.0F, 0.0F, 0L);
        double later = ground.at((long) (days * 24_000.0)).total();
        helper.assertTrue(Math.abs(later - 5.0) < 0.2, "Short-lived contamination after one half-life: " + later);
        long halfLife = (long) (ArcforgeConfig.WASTE_LOG_HALF_LIFE.getAsDouble() * 24_000.0);
        double log = WasteLog.decayed(8.0, halfLife);
        helper.assertTrue(Math.abs(log - 4.0) < 0.1, "A waste log after one half-life: " + log);
        WasteLog made = new WasteLog(8.0F, 0L);
        helper.assertTrue(made.heatAt(halfLife * 3) < made.heatAt(0L), "A waste log's heat doesn't fall");
        helper.succeed();
    }

    private static ItemStack spentRod(double heat, long discharged) {
        ItemStack stack = new ItemStack(ModItems.FUEL_ROD.get());
        stack.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fresh(3_000).burnt(1.0F).withHeat(heat, discharged));
        return stack;
    }

    // A rack under 3 blocks of water is deep enough, one under 1 isn't, a dry one has none; a rack takes four rods.
    // A Dry Cask refuses a hot rod and takes a cooled one. A repository up here isn't a safe site.
    static void spentFuel(GameTestHelper helper) {
        BlockState wet = ModBlocks.SPENT_FUEL_RACK.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        helper.setBlock(new BlockPos(1, 1, 1), wet);
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(1, y, 1), Blocks.WATER);
        }
        helper.setBlock(new BlockPos(3, 1, 1), wet);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(5, 1, 1), ModBlocks.SPENT_FUEL_RACK.get().defaultBlockState());
        var level = helper.getLevel();
        int need = ArcforgeConfig.POOL_DEPTH.getAsInt();
        BlockPos deep = helper.absolutePos(new BlockPos(1, 1, 1)), shallow = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.assertTrue(SpentFuelRackBlockEntity.waterDepth(level, deep, level.getBlockState(deep)) >= need, "The deep rack isn't deep enough");
        helper.assertTrue(SpentFuelRackBlockEntity.waterDepth(level, shallow, level.getBlockState(shallow)) < need, "The shallow rack counts as deep");
        BlockPos dry = helper.absolutePos(new BlockPos(5, 1, 1));
        helper.assertTrue(SpentFuelRackBlockEntity.waterDepth(level, dry, level.getBlockState(dry)) == 0, "A dry rack has water");
        SpentFuelRackBlockEntity rack = helper.getBlockEntity(new BlockPos(1, 1, 1), SpentFuelRackBlockEntity.class);
        long now = level.getGameTime();
        for (int i = 0; i < SpentFuelRackBlockEntity.SLOTS; i++) {
            helper.assertTrue(rack.insert(spentRod(200.0, now)), "The rack refused rod " + i);
        }
        helper.assertFalse(rack.insert(spentRod(200.0, now)), "The rack took a fifth rod");
        helper.assertFalse(rack.insert(new ItemStack(Items.STONE)), "The rack took stone");
        helper.assertFalse(DryCaskBlockEntity.accepts(spentRod(200.0, now), now), "A Dry Cask takes a hot rod");
        helper.assertTrue(DryCaskBlockEntity.accepts(spentRod(200.0, now - 100_000_000L), now), "A Dry Cask refuses a cooled rod");
        helper.assertFalse(GeologicalRepositoryBlockEntity.isValidSite(level, helper.absolutePos(new BlockPos(1, 1, 3))),
                "A repository up here counts as a safe site");
        helper.succeed();
    }

    // Spent uranium rods reprocess in PUREX (not THOREX), thorium blankets in THOREX (not PUREX); used filters
    // compact; High-Level Liquid Waste vitrifies with glass.
    static void wasteRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack uranium = spentRod(0.0, 0L);
        helper.assertTrue(MachineRecipes.reprocessing(level, uranium, ReprocessingRecipe.Mode.PUREX).isPresent(), "No PUREX recipe for a uranium rod");
        helper.assertTrue(MachineRecipes.reprocessing(level, uranium, ReprocessingRecipe.Mode.THOREX).isEmpty(), "THOREX takes a uranium rod");
        ItemStack mox = new ItemStack(ModItems.MOX_FUEL_ROD.get());
        helper.assertTrue(MachineRecipes.reprocessing(level, mox, ReprocessingRecipe.Mode.PUREX).isPresent(), "No PUREX recipe for a MOX rod");
        ItemStack blanket = new ItemStack(ModItems.THORIUM_BLANKET_ROD.get());
        var thorex = MachineRecipes.reprocessing(level, blanket, ReprocessingRecipe.Mode.THOREX);
        helper.assertTrue(thorex.isPresent(), "No THOREX recipe for a blanket rod");
        helper.assertTrue(thorex.get().value().fissile().create().is(ModItems.URANIUM_233_OXIDE.get()), "THOREX doesn't give U-233");
        helper.assertTrue(MachineRecipes.reprocessing(level, blanket, ReprocessingRecipe.Mode.PUREX).isEmpty(), "PUREX takes a blanket rod");
        helper.assertTrue(MachineRecipes.compacting(level, new ItemStack(ModItems.USED_HEPA_FILTER.get(), 4)).isPresent(), "Used filters don't compact");
        helper.assertTrue(MachineRecipes.vitrifying(level, FluidResource.of(ModFluids.HIGH_LEVEL_WASTE.get()), new ItemStack(Items.GLASS, 4)).isPresent(),
                "Waste doesn't vitrify");
        helper.succeed();
    }

    private static ReactorSim core(String[] rows, double enrichment, CoreLattice.Coolant coolant, boolean salt) {
        int nz = rows.length, nx = rows[0].length(), ny = 7;
        CoreLattice lattice = new CoreLattice(nx, ny, nz);
        lattice.saltCore = salt;
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    int i = lattice.index(x, y, z);
                    switch (rows[z].charAt(x)) {
                        case 'F' -> lattice.set(i, CoreLattice.Cell.FUEL, -1);
                        case 'A' -> lattice.set(i, CoreLattice.Cell.CONTROL, 0);
                        case 'G' -> lattice.set(i, CoreLattice.Cell.GRAPHITE, -1);
                        case 'S' -> lattice.set(i, CoreLattice.Cell.STEEL, -1);
                        default -> {}
                    }
                }
            }
        }
        ReactorSim sim = new ReactorSim(ReactorParams.defaults(), lattice);
        ReactorSim.Rod shared = new ReactorSim.Rod(enrichment);
        shared.thorium = salt;
        for (int cell : sim.fuelCells()) {
            ReactorSim.Rod rod = salt ? shared : new ReactorSim.Rod(enrichment);
            sim.setRod(cell, rod);
        }
        sim.coolantType = coolant;
        sim.mode = ReactorSim.Mode.PRESSURIZED;
        sim.coolant = sim.coolantCapacity;
        Arrays.fill(sim.insertion, 0.0);
        Arrays.fill(sim.target, 0.0);
        return sim;
    }

    private static void rodsIn(ReactorSim sim) {
        Arrays.fill(sim.insertion, 1.0);
        Arrays.fill(sim.target, 1.0);
        for (int t = 0; t < 21; t++) {
            sim.tick(1.0E9, 0.0);
        }
    }

    // A lead-bismuth core without moderator in a steel reflector: critical at 15-20% (calibrated: 18% gives k about
    // 1.13), far from it at 5%, shut down by its rods; Doppler negative. A molten salt core in graphite: critical at
    // 3%, its salt's void and Doppler coefficients negative, shut down by its rods.
    static void advancedCores(GameTestHelper helper) {
        String[] fast = { "SSSSSSS", "SFFFFFS", "SFFAFFS", "SFAFAFS", "SFFAFFS", "SFFFFFS", "SSSSSSS" };
        ReactorSim lbe = core(fast, 0.18, CoreLattice.Coolant.LEAD_BISMUTH, false);
        lbe.tick(1.0E9, 0.0);
        helper.assertTrue(lbe.k > 1.0, "An 18% fast core isn't critical: " + lbe.k);
        helper.assertTrue(lbe.dopplerCoefficient < 0.0, "The fast core's Doppler isn't negative");
        rodsIn(lbe);
        helper.assertTrue(lbe.k < 1.0, "The fast core's rods don't shut it down: " + lbe.k);
        ReactorSim poor = core(fast, 0.05, CoreLattice.Coolant.LEAD_BISMUTH, false);
        poor.tick(1.0E9, 0.0);
        helper.assertTrue(poor.k < 0.9, "A 5% fast core is critical: " + poor.k);
        // Salt channels in graphite with five control rod channels (enough to hold it down: k about 1.04 out, 0.80 in).
        String[] msr = { "GGGGGGG", "GFGAGFG", "GGFGFGG", "GAGAGAG", "GGFGFGG", "GFGAGFG", "GGGGGGG" };
        ReactorSim salt = core(msr, 0.03, CoreLattice.Coolant.FUEL_SALT, true);
        salt.tick(1.0E9, 0.0);
        helper.assertTrue(salt.k > 1.0, "A 3% salt core isn't critical: " + salt.k);
        helper.assertTrue(salt.voidCoefficient < 0.0 && salt.dopplerCoefficient < 0.0, "The salt core's feedback isn't negative");
        rodsIn(salt);
        helper.assertTrue(salt.k < 1.0, "The salt core's rods don't shut it down: " + salt.k);
        ReactorSim lean = core(msr, 0.01, CoreLattice.Coolant.FUEL_SALT, true);
        lean.tick(1.0E9, 0.0);
        helper.assertTrue(lean.k < 1.0, "A 1% salt core is critical: " + lean.k);
        helper.succeed();
    }

    // A Freeze Plug stays frozen while powered and cool, melts when the salt overheats.
    static void freezePlug(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FREEZE_PLUG.get());
        FreezePlugBlockEntity plug = helper.getBlockEntity(pos, FreezePlugBlockEntity.class);
        CrushingGameTests.charge(plug.getEnergy(), 10_000);
        plug.watch(helper.getLevel(), 650.0);
        helper.assertFalse(plug.isMelted(), "It melted at 650 °C");
        plug.watch(helper.getLevel(), ArcforgeConfig.FREEZE_PLUG_MELT.getAsDouble() + 50.0);
        helper.assertTrue(helper.getBlockEntity(pos, FreezePlugBlockEntity.class).isMelted(), "It didn't melt when the salt overheated");
        helper.succeed();
    }

    // Fallout lies in layers like snow: it stands on a solid face or a full stack of itself, not on a partial one, and
    // its collision is a layer lower than it looks.
    static void falloutLayers(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        helper.setBlock(floor, Blocks.STONE);
        BlockState three = ModBlocks.FALLOUT.get().defaultBlockState().setValue(net.zagdrath.arcforge.block.nuclear.FalloutBlock.LAYERS, 3);
        var level = helper.getLevel();
        BlockPos above = helper.absolutePos(floor.above());
        helper.assertTrue(three.canSurvive(level, above), "Fallout won't lie on stone");
        helper.setBlock(floor.above(), three);
        helper.assertFalse(three.canSurvive(level, above.above()), "Fallout lies on a partial layer");
        helper.assertTrue(three.getShape(level, above).max(net.minecraft.core.Direction.Axis.Y) > three.getCollisionShape(level, above)
                .max(net.minecraft.core.Direction.Axis.Y), "Its collision isn't a layer lower");
        helper.setBlock(floor.above(), three.setValue(net.zagdrath.arcforge.block.nuclear.FalloutBlock.LAYERS, 8));
        helper.assertTrue(three.canSurvive(level, above.above()), "Fallout won't lie on a full block of itself");
        helper.succeed();
    }
}
