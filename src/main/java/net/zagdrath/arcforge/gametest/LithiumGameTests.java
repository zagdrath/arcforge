/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.BatteryArrayPart;
import net.zagdrath.arcforge.block.multiblock.LithiumCellBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.LithiumCellBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Lithium and the Battery Array: the lithium recipes (Spodumene crushing, Lithium Brine from the reactor and the
// evaporator, Lithium Hydroxide, the three-item LFP Cathode, Graphite and its anode); the reactor's third item slot; the
// evaporator's Lithium Brine by-product; and the Battery Array forming (capacity and transfer from its cells and
// regulators), charging through an Energy Input port at no more than its transfer rate, giving out through an Energy
// Output port, its comparator and charge display, and a broken cell keeping its share.
public final class LithiumGameTests {
    private LithiumGameTests() {}

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // The recipes and their numbers.
    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var ore = MachineRecipes.crushing(level, new ItemStack(ModItems.SPODUMENE_ORE.get()));
        helper.assertTrue(ore.isPresent() && ore.get().value().ore() && ore.get().value().result().map(r -> r.count() == 2).orElse(false),
                "Spodumene Ore doesn't crush into 2 dust (doubled in the array)");
        var raw = MachineRecipes.crushing(level, new ItemStack(ModItems.RAW_SPODUMENE.get()));
        helper.assertTrue(raw.isPresent() && raw.get().value().ore(), "Raw Spodumene doesn't crush as an ore");
        var leach = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModItems.SPODUMENE_DUST.get()),
                FluidResource.of(ModFluids.SULFURIC_ACID.get()), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(leach.isPresent() && leach.get().value().fluidOutput().map(out -> out.fluid().value() == ModFluids.LITHIUM_BRINE.get()).orElse(false),
                "Spodumene Dust and Sulfuric Acid don't make Lithium Brine");
        var hydroxide = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(ItemStack.EMPTY,
                FluidResource.of(ModFluids.LYE.get()), 1_000, FluidResource.of(ModFluids.LITHIUM_BRINE.get()), 1_000));
        helper.assertTrue(hydroxide.isPresent() && hydroxide.get().value().itemOutput().map(out -> out.create().is(ModItems.LITHIUM_HYDROXIDE.get())).orElse(false)
                && hydroxide.get().value().fluidOutput().map(out -> out.fluid().value() == ModFluids.BRINE.get()).orElse(false),
                "Lithium Brine and Lye don't make Lithium Hydroxide and Brine");
        // The three items go in any order, one per slot.
        var cathode = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModItems.BASIC_SLAG.get()),
                new ItemStack(ModItems.LITHIUM_HYDROXIDE.get()), FluidResource.EMPTY, 0, FluidResource.EMPTY, 0, FluidResource.EMPTY, 0,
                new ItemStack(ModItems.IRON_DUST.get())));
        helper.assertTrue(cathode.isPresent() && cathode.get().value().itemInputs().size() == 3
                && cathode.get().value().itemOutput().map(out -> out.create().is(ModItems.LFP_CATHODE.get())).orElse(false),
                "The three items don't make an LFP Cathode");
        int[] slots = cathode.get().value().slotsFor(new ChemicalReactorInput(new ItemStack(ModItems.BASIC_SLAG.get()),
                new ItemStack(ModItems.LITHIUM_HYDROXIDE.get()), FluidResource.EMPTY, 0, FluidResource.EMPTY, 0, FluidResource.EMPTY, 0,
                new ItemStack(ModItems.IRON_DUST.get())));
        helper.assertTrue(slots != null && slots[0] == 1 && slots[1] == 2 && slots[2] == 0, "The items were matched to the wrong slots");
        var salt = MachineRecipes.evaporating(level, FluidResource.of(ModFluids.BRINE.get()));
        helper.assertTrue(salt.isPresent() && salt.get().value().byproduct().map(out -> out.fluid().value() == ModFluids.LITHIUM_BRINE.get()
                && out.amount() == 25).orElse(false), "Drying Brine leaves no Lithium Brine");
        var graphite = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.COAL_COKE.get()), ItemStack.EMPTY);
        helper.assertTrue(graphite.isPresent() && graphite.get().value().result().create().is(ModItems.GRAPHITE.get()), "Coal Coke doesn't bake into Graphite");
        var carbon = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.CARBON_DUST.get(), 2), ItemStack.EMPTY);
        helper.assertTrue(carbon.isPresent() && carbon.get().value().result().create().is(ModItems.GRAPHITE.get()), "Carbon Dust doesn't bake into Graphite");
        var anode = MachineRecipes.pressing(level, new ItemStack(ModItems.PLATE_DIE.get()), new ItemStack(ModItems.GRAPHITE.get()));
        helper.assertTrue(anode.isPresent() && anode.get().value().result().create().is(ModItems.GRAPHITE_ANODE.get()), "Graphite doesn't press into an anode");
        helper.succeed();
    }

    // Three items fed through a hopper face sort into the three input slots and react into an LFP Cathode.
    static void reactorThirdSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        var input = reactor.getItemHandler(Direction.UP);
        helper.assertTrue(CrushingGameTests.insert(input, ModItems.LITHIUM_HYDROXIDE.get(), 2) == 2, "It refused Lithium Hydroxide");
        helper.assertTrue(CrushingGameTests.insert(input, ModItems.IRON_DUST.get(), 2) == 2, "It refused iron dust");
        helper.assertTrue(CrushingGameTests.insert(input, ModItems.BASIC_SLAG.get(), 2) == 2, "It refused Basic Slag");
        helper.assertTrue(!reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT_C).isEmpty(), "The third slot is empty");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).is(ModItems.LFP_CATHODE.get()),
                        "No LFP Cathode yet"))
                .thenExecute(() -> {
                    int left = 0;
                    for (int slot : new int[] { ChemicalReactorBlockEntity.SLOT_INPUT, ChemicalReactorBlockEntity.SLOT_INPUT_B, ChemicalReactorBlockEntity.SLOT_INPUT_C }) {
                        left += reactor.getItems().getStack(slot).getCount();
                    }
                    helper.assertTrue(left <= 3, "It didn't use one of each: " + left + " left");
                })
                .thenSucceed();
    }

    // Drying Brine into Salt fills the by-product tank with Lithium Brine (25 mB a Salt).
    static void evaporatorLithiumBrine(GameTestHelper helper) {
        BlockPos controller = ThermalEvaporatorGameTests.buildTower(helper, new BlockPos(0, 1, 1), false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = helper.getBlockEntity(controller, ThermalEvaporatorBlockEntity.class);
                    fill(tower.getFluidHandler(SideMode.INPUT), ModFluids.BRINE.get(), 2_000);
                    tower.getHeat().add(tower.getHeat().getCapacity());
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, ThermalEvaporatorBlockEntity.class).getSalt().is(ModItems.SALT.get()),
                        "No Salt yet"))
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = helper.getBlockEntity(controller, ThermalEvaporatorBlockEntity.class);
                    int salt = tower.getSalt().getCount();
                    helper.assertTrue(tower.getByproduct().contains(ModFluids.LITHIUM_BRINE.get()) && tower.getByproduct().getAmount() == 25 * salt,
                            tower.getByproduct().getAmount() + " mB Lithium Brine for " + salt + " Salt");
                    helper.assertTrue(tower.getFluidHandler(SideMode.LITHIUM_BRINE) != null, "No Lithium Brine port handler");
                })
                .thenSucceed();
    }

    // A box from min of sx x sy x sz: casings on the edges and faces (glass where asked), the controller in the middle of
    // its north face facing north, and the inside filled from fill (x, then z, then y) with cells and regulators.
    static BlockPos buildBattery(GameTestHelper helper, BlockPos min, int sx, int sy, int sz, Set<BlockPos> glass, ConduitTier cellTier,
            int regulators, ConduitTier regulatorTier) {
        BigArrayGameTests.buildBox(helper, min, sx, sy, sz, ModBlocks.BATTERY_ARRAY_CASING.get(), glass);
        int placed = 0;
        for (int y = 1; y < sy - 1; y++) {
            for (int z = 1; z < sz - 1; z++) {
                for (int x = 1; x < sx - 1; x++) {
                    helper.setBlock(min.offset(x, y, z), placed++ < regulators ? ModBlocks.powerRegulator(regulatorTier).get()
                            : ModBlocks.lithiumCell(cellTier).get());
                }
            }
        }
        BlockPos controller = min.offset(sx / 2, sy / 2, 0);
        helper.setBlock(controller, ModBlocks.BATTERY_ARRAY_CONTROLLER.get().defaultBlockState().setValue(BatteryArrayControllerBlock.FACING, Direction.NORTH));
        return controller;
    }

    private static BatteryArrayBlockEntity battery(GameTestHelper helper, BlockPos controller) {
        return helper.getBlockEntity(controller, BatteryArrayBlockEntity.class);
    }

    // The smallest (3x3x3, one Wrought cell, no regulators) stores 64,000,000 FE and moves 16,384 FE/t; a 4x3x4 with a
    // window, three Hardened cells and a Wrought regulator stores 12,000,000,000 (past the int limit) and moves 65,536.
    // Something else inside, or no cell, and it doesn't form; the size rule is 3 to 5 each way.
    static void batteryArrayForms(GameTestHelper helper) {
        BlockPos small = buildBattery(helper, new BlockPos(0, 1, 1), 3, 3, 3, Set.of(), ConduitTier.WROUGHT, 0, ConduitTier.WROUGHT);
        BlockPos bigMin = new BlockPos(4, 1, 1);
        BlockPos pane = bigMin.offset(3, 1, 1);
        BlockPos big = buildBattery(helper, bigMin, 4, 3, 4, Set.of(pane), ConduitTier.HARDENED, 1, ConduitTier.WROUGHT);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BatteryArrayBlockEntity a = battery(helper, small);
                    helper.assertTrue(a.isFormed(), "The 3x3x3 didn't form");
                    helper.assertTrue(a.getCapacity() == ArcforgeConfig.LITHIUM_CELL_WROUGHT.getAsLong() && a.transfer() == 16_384,
                            "3x3x3: " + a.getCapacity() + " FE, " + a.transfer() + " FE/t");
                    BatteryArrayBlockEntity b = battery(helper, big);
                    helper.assertTrue(b.isFormed(), "The 4x3x4 didn't form");
                    helper.assertTrue(b.getCellCount() == 3 && b.getRegulatorCount() == 1, b.getCellCount() + " cells, " + b.getRegulatorCount() + " regulators");
                    helper.assertTrue(b.getCapacity() == 3 * ArcforgeConfig.LITHIUM_CELL_HARDENED.getAsLong() && b.getCapacity() > Integer.MAX_VALUE,
                            "4x3x4 capacity " + b.getCapacity());
                    helper.assertTrue(b.transfer() == ArcforgeConfig.POWER_REGULATOR_WROUGHT.getAsInt(), "4x3x4 transfer " + b.transfer());
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(pane)), "The window isn't part of it");
                    helper.assertTrue(helper.getBlockState(bigMin.offset(1, 1, 1)).getValue(BatteryArrayPart.FORMED), "A cell isn't formed");
                    helper.assertTrue(BatteryArrayStructure.allows(3, 5, 4) && !BatteryArrayStructure.allows(6, 3, 3) && !BatteryArrayStructure.allows(2, 3, 3),
                            "The size rule is wrong");
                    // Stone inside breaks the small one.
                    helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertFalse(battery(helper, small).isFormed(), "Still formed with stone inside"))
                .thenSucceed();
    }

    // Energy goes in through an Energy Input port at most its transfer rate a tick, and out of an Energy Output port into
    // an Energy Cell; redstone pauses the output. The cells' windows and the controller show the fill, and a comparator
    // reads it. Broken out, a cell keeps its share and the array un-forms.
    static void batteryArrayCharges(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildBattery(helper, min, 3, 3, 4, Set.of(), ConduitTier.WROUGHT, 0, ConduitTier.WROUGHT);
        BlockPos inPort = min.offset(0, 1, 1);
        BlockPos outPort = min.offset(2, 1, 1);
        BlockPos cellPos = outPort.east();
        helper.setBlock(cellPos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        long[] inserted = new long[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BatteryArrayBlockEntity array = battery(helper, controller);
                    helper.assertTrue(array.isFormed() && array.getCellCount() == 2, "The 3x3x4 didn't form with two cells");
                    MultiblockPorts.set(helper.getLevel(), array, helper.absolutePos(inPort), SideMode.ENERGY_INPUT, Direction.WEST);
                    MultiblockPorts.set(helper.getLevel(), array, helper.absolutePos(outPort), SideMode.ENERGY_OUTPUT, Direction.EAST);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(inPort), Direction.WEST);
                    helper.assertTrue(handler != null, "No energy handler on the input port");
                    try (Transaction tx = Transaction.openRoot()) {
                        inserted[0] = handler.insert(1_000_000, tx);
                        tx.commit();
                    }
                    helper.assertTrue(inserted[0] == 16_384, "One tick took " + inserted[0] + " FE, not its 16,384 FE/t");
                    // Fill most of it straight away, as many ticks of charging would.
                    BatteryArrayBlockEntity array = battery(helper, controller);
                    array.getEnergy().set(array.getCapacity() * 3 / 4);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    BatteryArrayBlockEntity array = battery(helper, controller);
                    helper.assertTrue(helper.getBlockEntity(cellPos, net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity.class).getEnergy() > 0,
                            "Nothing came out of the output port");
                    helper.assertTrue(array.getStatus() == MachineStatus.DISCHARGING, "Status " + array.getStatus());
                    helper.assertTrue(helper.getBlockState(controller).getValue(BatteryArrayControllerBlock.CHARGE) == 3, "The controller shows "
                            + helper.getBlockState(controller).getValue(BatteryArrayControllerBlock.CHARGE));
                    helper.assertTrue(helper.getBlockState(min.offset(1, 1, 1)).getValue(LithiumCellBlock.CHARGE) == 3, "A cell's windows don't show 3 of 4");
                    int signal = array.getComparatorSignal();
                    helper.assertTrue(signal >= 10 && signal <= 12, "Comparator reads " + signal);
                    // Redstone pauses the output.
                    array.setRedstoneMode(net.zagdrath.arcforge.machine.config.RedstoneMode.LOW);
                    helper.setBlock(controller.below().below(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    BatteryArrayBlockEntity array = battery(helper, controller);
                    helper.assertTrue(array.getStatus() == MachineStatus.DISABLED && array.getLastOutput() == 0,
                            "Powered, it's " + array.getStatus() + " giving " + array.getLastOutput());
                    // Break a cell out: it carries its half of the energy, and the array un-forms.
                    long half = array.getStored() / 2;
                    helper.getLevel().destroyBlock(helper.absolutePos(min.offset(1, 1, 1)), true);
                    var drops = helper.getEntities(EntityType.ITEM, min.offset(1, 1, 1), 2.0);
                    helper.assertTrue(drops.size() == 1, "Expected one drop, got " + drops.size());
                    long carried = drops.getFirst().getItem().getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L);
                    helper.assertTrue(Math.abs(carried - half) <= 2, "The cell carried " + carried + " FE, its share was " + half);
                    helper.assertFalse(battery(helper, controller).isFormed(), "Still formed without a cell");
                    LithiumCellBlockEntity other = helper.getBlockEntity(min.offset(1, 1, 2), LithiumCellBlockEntity.class);
                    helper.assertTrue(Math.abs(other.getStored() - half) <= 2, "The other cell keeps " + other.getStored() + " FE");
                })
                .thenSucceed();
    }
}
