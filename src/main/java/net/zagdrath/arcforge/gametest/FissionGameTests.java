/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.VesselControllerBlock;
import net.zagdrath.arcforge.block.multiblock.VesselPart;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.HeavyWaterTowerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamGeneratorBlockEntity;
import net.zagdrath.arcforge.menu.multiblock.FissionReactorMenu;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.ReactorSteam;
import net.zagdrath.arcforge.nuclear.reactor.CoreLattice;
import net.zagdrath.arcforge.nuclear.reactor.ReactorParams;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.FissionReactorMesh;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Fission Reactor drop: the core physics on its own (k-effective with the rods out and in, the void and Doppler
// coefficients' signs for a water-moderated and a graphite core, burnup, xenon building after a SCRAM, decay heat);
// fuel fabrication keeping its enrichment (UF6 to UO2, sintering) and the Fuel Rod recipe; the reactor forming, hiding
// its inside, loading rods only while shut down and SCRAMming; the Steam Generator and the Heavy Water Tower working;
// Contaminated Steam in the turbines; and the reactor's model.
public final class FissionGameTests {
    private FissionGameTests() {}

    // --- The physics, on its own ---

    // A 5x5 lattice 7 cells tall, as the physics was calibrated on: '.' coolant, F fuel, A and B control rod banks, G
    // graphite.
    private static final String[] PWR = { "F.F.F", ".FAF.", "F.FBF", ".FAF.", "F.F.F" };
    private static final String[] GRAPHITE = { "FGFGF", "GFAFG", "FGBGF", "GFAFG", "FGFGF" };

    static ReactorSim core(String[] rows, double enrichment, ReactorSim.Mode mode) {
        int nz = rows.length, nx = rows[0].length(), ny = 7;
        CoreLattice lattice = new CoreLattice(nx, ny, nz);
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    int i = lattice.index(x, y, z);
                    switch (rows[z].charAt(x)) {
                        case 'F' -> lattice.set(i, CoreLattice.Cell.FUEL, -1);
                        case 'A' -> lattice.set(i, CoreLattice.Cell.CONTROL, 0);
                        case 'B' -> lattice.set(i, CoreLattice.Cell.CONTROL, 1);
                        case 'G' -> lattice.set(i, CoreLattice.Cell.GRAPHITE, -1);
                        default -> {}
                    }
                }
            }
        }
        ReactorSim sim = new ReactorSim(ReactorParams.defaults(), lattice);
        for (int cell : sim.fuelCells()) {
            sim.setRod(cell, new ReactorSim.Rod(enrichment));
        }
        sim.coolantType = CoreLattice.Coolant.LIGHT_WATER;
        sim.mode = mode;
        sim.coolant = sim.coolantCapacity;
        return sim;
    }

    private static void banks(ReactorSim sim, double insertion) {
        Arrays.fill(sim.insertion, insertion);
        Arrays.fill(sim.target, insertion);
    }

    // Every tick the coolant it sent out comes back (an ideal Steam Generator or feedwater).
    private static void run(ReactorSim sim, int ticks) {
        for (int t = 0; t < ticks; t++) {
            sim.tick(1.0E9, 1.0E9);
            sim.coolant = Math.min(sim.coolantCapacity, sim.coolant + sim.hotLeg + sim.steamMade);
        }
    }

    // The bank insertion (all four together) at which a cold core is just critical, by halving.
    private static double critical(String[] rows, double enrichment) {
        double low = 0.0, high = 1.0;
        for (int i = 0; i < 12; i++) {
            double mid = (low + high) / 2.0;
            ReactorSim probe = core(rows, enrichment, ReactorSim.Mode.PRESSURIZED);
            banks(probe, mid);
            run(probe, 1);
            if (probe.k > 1.0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    // An operator holding power near goal: the banks follow the reactivity, a little positive below goal, negative
    // above.
    private static void hold(ReactorSim sim, double goal) {
        double want = 0.0003 * (1.0 - sim.power / goal);
        Arrays.fill(sim.target, Math.max(0.0, Math.min(1.0, sim.insertion[0] - (want - sim.rho) * 0.02)));
    }

    static void reactorPhysics(GameTestHelper helper) {
        // A 3% light-water core: supercritical with the rods out, well subcritical with them in.
        ReactorSim pwr = core(PWR, 0.03, ReactorSim.Mode.PRESSURIZED);
        banks(pwr, 0.0);
        run(pwr, 1);
        double kOut = pwr.k;
        helper.assertTrue(kOut > 1.03 && kOut < 1.2, "Rods out k " + kOut);
        helper.assertTrue(pwr.dopplerCoefficient < 0.0, "Doppler isn't negative: " + pwr.dopplerCoefficient);
        helper.assertTrue(pwr.voidCoefficient < 0.0, "A water-moderated core's void coefficient isn't negative: " + pwr.voidCoefficient);
        banks(pwr, 1.0);
        run(pwr, 21);
        helper.assertTrue(pwr.k < 0.95, "Rods in k " + pwr.k);
        helper.assertTrue(pwr.isShutDown(), "Not shut down with every rod in");
        // A graphite-moderated, water-cooled core: boiling adds reactivity.
        ReactorSim rbmk = core(GRAPHITE, 0.03, ReactorSim.Mode.BOILING);
        banks(rbmk, 0.0);
        run(rbmk, 1);
        helper.assertTrue(rbmk.voidCoefficient > 0.0, "A graphite core's void coefficient isn't positive: " + rbmk.voidCoefficient);
        // Burnup: used-up fuel with its fission products is worth far less.
        ReactorSim spent = core(PWR, 0.03, ReactorSim.Mode.PRESSURIZED);
        for (int cell : spent.fuelCells()) {
            ReactorSim.Rod rod = spent.rod(cell);
            rod.burnup = 0.6;
            rod.fissionProducts = 0.6;
            rod.enrichment *= 0.5;
        }
        banks(spent, 0.0);
        run(spent, 1);
        helper.assertTrue(spent.k < kOut - 0.1, "Burnt fuel k " + spent.k + " against fresh " + kOut);
        // At 15% power with the banks held near critical for three minutes, then a SCRAM: decay heat keeps coming and fades;
        // xenon keeps building from the iodine.
        ReactorSim hot = core(PWR, 0.03, ReactorSim.Mode.PRESSURIZED);
        hot.trips.shortPeriod = false;
        hot.power = 0.15;
        hot.settlePrecursors();
        banks(hot, critical(PWR, 0.03));
        for (int t = 0; t < 20 * 60 * 3; t++) {
            run(hot, 1);
            hold(hot, 0.15);
        }
        helper.assertTrue(!hot.scrammed && hot.power > 0.01, "It didn't hold power: " + hot.power + ", " + hot.trip);
        hot.scram(ReactorSim.Trip.MANUAL);
        run(hot, 20);
        double xenon = hot.meanXenon, decay = hot.decayPower;
        helper.assertTrue(decay > 0.0, "No decay heat after the SCRAM");
        run(hot, 20 * 60 * 3);
        helper.assertTrue(hot.meanXenon > xenon, "Xenon didn't build after the SCRAM: " + xenon + " then " + hot.meanXenon);
        helper.assertTrue(hot.decayPower < decay && hot.decayPower > 0.0, "Decay heat didn't fade: " + decay + " then " + hot.decayPower);
        helper.assertTrue(hot.isShutDown(), "Not shut down after the SCRAM");
        helper.succeed();
    }

    // The 20% cap holds for fuel too, and a fresh rod is far less radioactive than a spent one.
    static void fuelRods(GameTestHelper helper) {
        FuelRod fresh = FuelRod.fresh(5_000);
        helper.assertTrue(fresh.isFresh() && !fresh.isSpent() && fresh.enrichment() == 5_000, "A fresh rod is wrong: " + fresh);
        helper.assertTrue(FuelRod.fresh(90_000).enrichment() <= Enrichment.MAX_PCM, "A rod went over 20%");
        FuelRod spent = new FuelRod(fresh.type(), fresh.enrichment(), 1.0F, 0.01F, 1.0F, 0.0F, 0L, false);
        helper.assertTrue(spent.isSpent() && spent.doseRate() > fresh.doseRate() * 100.0, "Spent " + spent.doseRate() + " against fresh " + fresh.doseRate());
        ItemStack rod = new ItemStack(ModItems.FUEL_ROD.get());
        rod.set(ModDataComponents.FUEL_ROD.get(), spent);
        helper.assertTrue(RadiationField.rate(rod) > RadiationField.rate(fresh(5_000)), "A spent rod isn't the stronger source");
        helper.succeed();
    }

    private static ItemStack fresh(int pcm) {
        ItemStack rod = new ItemStack(ModItems.FUEL_ROD.get());
        rod.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fresh(pcm));
        return rod;
    }

    private static ItemStack assayed(net.minecraft.world.item.Item item, int pcm) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.U235_ASSAY.get(), pcm);
        return stack;
    }

    // UF6 + Steam + Hydrogen -> UO2 at the gas's assay; green pellets sinter into Fuel Pellets at theirs; eight pellets of
    // one assay round a Zircaloy Tube make a fresh rod at it, and mixed assays make nothing.
    static void fuelFabrication(GameTestHelper helper) {
        var level = helper.getLevel();
        var sinter = MachineRecipes.sintering(level, assayed(ModItems.GREEN_FUEL_PELLET.get(), 4_000));
        helper.assertTrue(sinter.isPresent(), "Green pellets don't sinter");
        ItemStack sintered = sinter.get().value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(
                assayed(ModItems.GREEN_FUEL_PELLET.get(), 4_000)));
        helper.assertTrue(sintered.is(ModItems.FUEL_PELLET.get()) && Enrichment.assay(sintered) == 4_000,
                "Sintering lost the assay: " + Enrichment.assay(sintered));
        helper.assertTrue(MachineRecipes.isSmeltingInput(level, assayed(ModItems.GREEN_FUEL_PELLET.get(), 4_000)),
                "The Induction Furnace won't take a green pellet");
        ItemStack tube = new ItemStack(ModItems.ZIRCALOY_TUBE.get());
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            grid.set(i, i == 4 ? tube : assayed(ModItems.FUEL_PELLET.get(), 3_000));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(), "No Fuel Rod recipe");
        ItemStack rod = recipe.get().value().assemble(input);
        helper.assertTrue(rod.is(ModItems.FUEL_ROD.get()) && FuelRod.of(rod).enrichment() == 3_000 && FuelRod.of(rod).isFresh(),
                "The rod is wrong: " + FuelRod.of(rod));
        grid.set(0, assayed(ModItems.FUEL_PELLET.get(), 5_000));
        CraftingInput mixed = CraftingInput.of(3, 3, grid);
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, mixed, level).isEmpty(), "Mixed assays made a rod");
        helper.succeed();
    }

    // --- The vessels in the world ---

    // A box of casing with the controller in the middle of the north wall's second row and inside(x, y, z) filling the
    // inside (null for air).
    private static BlockPos vessel(GameTestHelper helper, BlockPos min, int w, int h, int d, Block casing, Block controller,
            java.util.function.BiFunction<Integer, Integer, Block> insideByXZ) {
        BlockPos at = min.offset(w / 2, 1, 0);
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos pos = min.offset(x, y, z);
                    boolean wall = x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1;
                    if (pos.equals(at)) {
                        helper.setBlock(pos, controller.defaultBlockState().setValue(VesselControllerBlock.FACING, Direction.NORTH));
                    } else if (wall) {
                        helper.setBlock(pos, casing);
                    } else {
                        Block inside = insideByXZ.apply(x, z);
                        helper.setBlock(pos, inside != null ? inside : Blocks.AIR);
                    }
                }
            }
        }
        return at;
    }

    private static int fill(net.neoforged.neoforge.transfer.ResourceHandler<FluidResource> handler, FluidResource resource, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // A 5x5x5 reactor: 24 Fuel Channels round a column of Control Rod Channels. It forms and hides its inside; it loads
    // rods while shut down, holds one back once a bank is withdrawn, takes it after a SCRAM, and a broken wall un-forms
    // it and shows the inside again.
    static void reactorForms(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos controller = vessel(helper, min, 5, 5, 5, ModBlocks.REACTOR_CASING.get(), ModBlocks.REACTOR_CONTROLLER.get(),
                (x, z) -> x == 2 && z == 2 ? ModBlocks.CONTROL_ROD_CHANNEL.get() : ModBlocks.FUEL_CHANNEL.get());
        helper.setBlock(min.offset(1, 2, 0), ModBlocks.REACTOR_GLASS.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    FissionReactorBlockEntity reactor = helper.getBlockEntity(controller, FissionReactorBlockEntity.class);
                    reactor.checkNow();
                    helper.assertTrue(reactor.isFormed(), "The 5x5x5 reactor didn't form");
                    helper.assertTrue(helper.getBlockState(min.offset(1, 2, 1)).getValue(VesselPart.FORMED), "The inside isn't hidden");
                    helper.assertTrue(helper.getBlockState(min.offset(1, 2, 0)).getValue(VesselPart.FORMED), "The glass isn't formed");
                    helper.assertTrue(reactor.canRefuel(), "A new reactor isn't shut down");
                    // 23 of the 24 fuel rods and the three control rods.
                    reactor.getItems().setStack(0, fresh(3_000).copyWithCount(23));
                    reactor.getItems().setStack(1, new ItemStack(ModItems.CONTROL_ROD.get(), 3));
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, FissionReactorBlockEntity.class).rods().size() == 26,
                        helper.getBlockEntity(controller, FissionReactorBlockEntity.class).rods().size() + " rods in"))
                .thenExecute(() -> {
                    FissionReactorBlockEntity reactor = helper.getBlockEntity(controller, FissionReactorBlockEntity.class);
                    helper.assertTrue(reactor.loadedFuel() == 23, reactor.loadedFuel() + " fuel rods");
                    reactor.setBankTarget(0, 50);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    FissionReactorBlockEntity reactor = helper.getBlockEntity(controller, FissionReactorBlockEntity.class);
                    helper.assertFalse(reactor.canRefuel(), "Still refuelling with a bank withdrawn");
                    reactor.getItems().setStack(0, fresh(3_000));
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    FissionReactorBlockEntity reactor = helper.getBlockEntity(controller, FissionReactorBlockEntity.class);
                    helper.assertTrue(reactor.loadedFuel() == 23, "A rod loaded while a bank was out");
                    FissionReactorMenu.apply(reactor, FissionReactorMenu.SCRAM);
                    helper.assertTrue(reactor.isScrammed(), "SCRAM didn't trip it");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, FissionReactorBlockEntity.class).loadedFuel() == 24,
                        "The last rod didn't load after the SCRAM"))
                .thenExecute(() -> helper.setBlock(min.offset(4, 2, 2), Blocks.STONE))
                .thenIdle(3)
                .thenExecute(() -> {
                    FissionReactorBlockEntity reactor = helper.getBlockEntity(controller, FissionReactorBlockEntity.class);
                    reactor.checkNow();
                    helper.assertFalse(reactor.isFormed(), "Still formed with a wall broken");
                    helper.assertFalse(helper.getBlockState(min.offset(1, 2, 1)).getValue(VesselPart.FORMED), "The inside is still hidden");
                })
                .thenSucceed();
    }

    // A 3x4x3 Steam Generator: Hot Coolant and feedwater in, High-Pressure Steam and cooled water out.
    static void steamGenerator(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos controller = vessel(helper, min, 3, 4, 3, ModBlocks.STEAM_GENERATOR_CASING.get(), ModBlocks.STEAM_GENERATOR_CONTROLLER.get(),
                (x, z) -> ModBlocks.STEAM_GENERATOR_TUBES.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamGeneratorBlockEntity generator = helper.getBlockEntity(controller, SteamGeneratorBlockEntity.class);
                    generator.checkNow();
                    helper.assertTrue(generator.isFormed() && generator.getTubes() == 2, "It didn't form with two tubes: " + generator.getTubes());
                    helper.assertTrue(fill(generator.getHot(), FluidResource.of(ModFluids.HOT_COOLANT.get()), 2_000) > 0, "It refused Hot Coolant");
                    helper.assertTrue(fill(generator.getFeed(), FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 4_000) > 0,
                            "It refused feedwater");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, SteamGeneratorBlockEntity.class).getSteam().getAmount() > 0,
                        "No steam yet"))
                .thenExecute(() -> {
                    SteamGeneratorBlockEntity generator = helper.getBlockEntity(controller, SteamGeneratorBlockEntity.class);
                    helper.assertTrue(generator.getSteam().getResource(0).is(ModFluids.HIGH_PRESSURE_STEAM.get()), "It made the wrong steam");
                    helper.assertTrue(generator.getCooled().getAmount() > 0 && generator.getCooled().getResource(0).is(net.minecraft.world.level.material.Fluids.WATER),
                            "The coolant didn't come back cooled");
                })
                .thenSucceed();
    }

    // A 3x4x3 Heavy Water Tower with two Exchange Trays: Water, Hydrogen Sulfide, Steam and FE in, Heavy Water out.
    static void heavyWaterTower(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos controller = vessel(helper, min, 3, 4, 3, ModBlocks.HEAVY_WATER_TOWER_CASING.get(), ModBlocks.HEAVY_WATER_TOWER_CONTROLLER.get(),
                (x, z) -> ModBlocks.EXCHANGE_TRAY.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    HeavyWaterTowerBlockEntity tower = helper.getBlockEntity(controller, HeavyWaterTowerBlockEntity.class);
                    tower.checkNow();
                    helper.assertTrue(tower.isFormed() && tower.getTrays() == 2, "It didn't form with two trays: " + tower.getTrays());
                    List<FluidResource> fluids = new ArrayList<>(List.of(FluidResource.of(net.minecraft.world.level.material.Fluids.WATER),
                            FluidResource.of(ModFluids.HYDROGEN_SULFIDE.get()), FluidResource.of(ModFluids.STEAM.get())));
                    for (int i = 0; i < 3; i++) {
                        helper.assertTrue(fill(tower.getInput(i), fluids.get(i), 4_000) > 0, "It refused " + fluids.get(i));
                    }
                    CrushingGameTests.charge(tower.getEnergy(), 100_000);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, HeavyWaterTowerBlockEntity.class).getOutput().getAmount() > 0,
                        "No Heavy Water yet"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(controller, HeavyWaterTowerBlockEntity.class).getOutput().getResource(0)
                        .is(ModFluids.HEAVY_WATER.get()), "It made the wrong fluid"))
                .thenSucceed();
    }

    // A boiling core's steam runs the turbines as High-Pressure Steam, and is radioactive where plain steam isn't.
    static void contaminatedSteam(GameTestHelper helper) {
        FluidResource steam = FluidResource.of(ModFluids.CONTAMINATED_STEAM.get());
        helper.assertTrue(ReactorSteam.isContaminated(steam) && ReactorSteam.isTurbineSteam(steam), "Turbines won't take Contaminated Steam");
        helper.assertTrue(ReactorSteam.turbineGrade(steam) == SteamGrade.HIGH_PRESSURE, "It isn't High-Pressure grade");
        helper.assertTrue(!ReactorSteam.isContaminated(FluidResource.of(ModFluids.HIGH_PRESSURE_STEAM.get())), "Plain steam counts as contaminated");
        helper.assertTrue(RadiationField.rate(ModFluids.CONTAMINATED_STEAM.get()) > 0.0 && RadiationField.rate(ModFluids.HIGH_PRESSURE_STEAM.get()) == 0.0,
                "Contaminated Steam's radioactivity is wrong");
        helper.succeed();
    }

    // The reactor's model: a vessel with its drive housings, a viewport for each pane of glass in the side walls, whose
    // Cherenkov glow is a list of its own (drawn only while running), and none without glass.
    static void reactorModel(GameTestHelper helper) {
        ModelFrame frame = ModelFrame.toward(Direction.SOUTH, 5, 6, 5);
        float[][] bare = new FissionReactorMesh(frame, List.of(), 2, 1).buildStatic(List.of());
        helper.assertTrue(bare[0].length > 0 && bare[3].length == 0, "A reactor without glass has a glow");
        float[][] glazed = new FissionReactorMesh(frame, List.of(new FissionReactorMesh.Viewport(0, 2, 2), new FissionReactorMesh.Viewport(4, 3, 2)), 2, 1)
                .buildStatic(List.of());
        helper.assertTrue(glazed[3].length > 0, "No Cherenkov glow for the viewports");
        helper.assertTrue(glazed[0].length > bare[0].length, "The viewports added nothing");
        helper.succeed();
    }
}
