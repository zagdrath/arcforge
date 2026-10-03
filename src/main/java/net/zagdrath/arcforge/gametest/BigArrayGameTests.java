/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.FireboxArrayPart;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;

// The large heat source and the bigger arrays: the Firebox Array (it forms as a rectangular hollow box with a controller
// and windows, and not too tall; it burns coal at 160 HU/t a block until it is as hot as the fuel, Coal Coke for 1.5 times
// coal's heat at 1,300°C, Naphtha by the mB, and pushes its heat out of a Heat port); rectangular Steam Boiler and Steam
// Turbine Arrays and a Superheater box that scale with their size; and the power multiplier reaching every generator's
// FE and the energy balance.
public final class BigArrayGameTests {
    private BigArrayGameTests() {}

    // A hollow box from min (relative) of sx x sy x sz: casings on the edges and faces (glass where asked), air inside.
    static void buildBox(GameTestHelper helper, BlockPos min, int sx, int sy, int sz, Block casing, Set<BlockPos> glass) {
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    int boundaries = (x == 0 || x == sx - 1 ? 1 : 0) + (y == 0 || y == sy - 1 ? 1 : 0) + (z == 0 || z == sz - 1 ? 1 : 0);
                    helper.setBlock(pos, boundaries == 0 ? Blocks.AIR : glass.contains(pos) ? ModBlocks.PRESSURE_GLASS.get() : casing);
                }
            }
        }
    }

    // A Firebox Array box with its controller in the middle of its north wall (z = min), facing north. Returns the
    // controller's position.
    static BlockPos buildFirebox(GameTestHelper helper, BlockPos min, int sx, int sy, int sz, Set<BlockPos> glass) {
        buildBox(helper, min, sx, sy, sz, ModBlocks.FIREBOX_ARRAY_CASING.get(), glass);
        BlockPos controller = min.offset(sx / 2, sy / 2, 0);
        helper.setBlock(controller, ModBlocks.FIREBOX_ARRAY_CONTROLLER.get().defaultBlockState().setValue(FireboxArrayControllerBlock.FACING, Direction.NORTH));
        return controller;
    }

    private static FireboxArrayBlockEntity firebox(GameTestHelper helper, BlockPos controller) {
        return helper.getBlockEntity(controller, FireboxArrayBlockEntity.class);
    }

    // A 5 wide, 3 tall, 4 deep box forms (it needn't be square), with a window in its east wall: 60 blocks, so 9,600 HU/t.
    // Its walls know their way in; the size rule takes 7x9 either way round and 3 to 5 tall, and nothing bigger.
    static void fireboxArrayForms(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos pane = min.offset(4, 1, 2);
        BlockPos controller = buildFirebox(helper, min, 5, 3, 4, Set.of(pane));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    helper.assertTrue(array.isFormed(), "The 5x3x4 Firebox Array didn't form");
                    helper.assertTrue(array.volume() == 60 && array.maxHeatPerTick() == 9_600,
                            "Volume " + array.volume() + ", " + array.maxHeatPerTick() + " HU/t");
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(pane)), "The window isn't part of it");
                    helper.assertTrue(FireboxArrayPart.inward(helper.getBlockState(min.offset(2, 0, 2))) == Direction.UP, "The floor doesn't face up into it");
                    helper.assertTrue(FireboxArrayPart.inward(helper.getBlockState(min.offset(0, 1, 2))) == Direction.EAST, "The west wall doesn't face in");
                    helper.assertTrue(FireboxArrayPart.inward(helper.getBlockState(min)) == null, "A corner has a way in");
                    helper.assertTrue(FireboxArrayStructure.allows(7, 5, 9) && FireboxArrayStructure.allows(9, 3, 7)
                            && !FireboxArrayStructure.allows(8, 3, 8) && !FireboxArrayStructure.allows(3, 6, 3), "The size rule is wrong");
                    helper.assertTrue(array.getHeat().getCapacity() == 600_000, "Heat buffer " + array.getHeat().getCapacity());
                    // A block inside breaks it (within its periodic check).
                    helper.setBlock(min.offset(2, 1, 2), Blocks.STONE);
                })
                .thenIdle(45)
                .thenExecute(() -> helper.assertFalse(firebox(helper, controller).isFormed(), "Still formed with stone inside"))
                .thenSucceed();
    }

    // A 3x3x3 burns coal at 4,320 HU/t (27 blocks x 160), at most 1,100°C: it stops (Full) once it's that hot. Each coal
    // gives a Firebox's 64,000 HU; Coal Coke gives 1.5 times that and burns at 1,300°C.
    static void fireboxArrayBurnsCoal(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildFirebox(helper, min, 3, 3, 3, Set.of());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    helper.assertTrue(array.isFormed(), "The 3x3x3 didn't form");
                    double coal = FireboxArrayBlockEntity.solidHeat(helper.getLevel(), array, new ItemStack(Items.COAL));
                    double coke = FireboxArrayBlockEntity.solidHeat(helper.getLevel(), array, new ItemStack(ModItems.COAL_COKE.get()));
                    helper.assertTrue(Math.abs(coal - 64_000) < 1e-6, "Coal gives " + coal + " HU");
                    helper.assertTrue(Math.abs(coke - 96_000) < 1e-6, "Coal Coke gives " + coke + " HU");
                    helper.assertTrue(FireboxArrayBlockEntity.solidTemperature(new ItemStack(ModItems.COAL_COKE.get())) == 1_300
                            && FireboxArrayBlockEntity.solidTemperature(new ItemStack(Items.COAL)) == 1_100, "Wrong burn temperatures");
                    array.getItems().setStack(FireboxArrayBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 8));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    helper.assertTrue(array.getHeatPerTick() == 4_320, "Burning coal makes " + array.getHeatPerTick() + " HU/t");
                    helper.assertTrue(array.getStatus() == MachineStatus.BURNING, "Status is " + array.getStatus());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    helper.assertTrue(array.getStatus() == MachineStatus.FULL, "At 1,100°C it's " + array.getStatus());
                    helper.assertTrue(array.getHeat().getTemperature() <= 1_100, "It got hotter than coal: " + array.getHeat().getTemperature());
                    helper.assertTrue(array.getHeat().getStored() == array.getHeat().storedAt(1_100), "It stopped at " + array.getHeat().getStored() + " HU");
                    // 211,304 HU is 3.3 coal: four taken, four left.
                    helper.assertTrue(array.getItems().getStack(FireboxArrayBlockEntity.SLOT_FUEL).getCount() == 4,
                            "Coal left: " + array.getItems().getStack(FireboxArrayBlockEntity.SLOT_FUEL).getCount());
                })
                .thenSucceed();
    }

    // Naphtha burns by the mB: 4,320 HU/t at 400 HU/mB is 10.8 mB/t, so ten ticks take 108 mB (give or take one).
    static void fireboxArrayBurnsNaphtha(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildFirebox(helper, min, 3, 3, 3, Set.of());
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> SteamGameTests.fill(firebox(helper, controller).getFluidHandler(SideMode.INPUT), FluidResource.of(ModFluids.NAPHTHA.get()), 20_000))
                .thenIdle(3)
                .thenExecute(() -> before[0] = firebox(helper, controller).getTank().getAmount())
                .thenIdle(10)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    int used = before[0] - array.getTank().getAmount();
                    helper.assertTrue(Math.abs(used - 108) <= 1, "Ten ticks burned " + used + " mB of Naphtha");
                    helper.assertTrue(array.getHeatPerTick() == 4_320, "Naphtha makes " + array.getHeatPerTick() + " HU/t");
                })
                .thenSucceed();
    }

    // A Heat port on its east wall pushes the heat into a Heat Cell against it.
    static void fireboxArrayHeatPort(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildFirebox(helper, min, 3, 3, 3, Set.of());
        BlockPos port = min.offset(2, 1, 1);
        BlockPos cell = port.east();
        helper.setBlock(cell, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    FireboxArrayBlockEntity array = firebox(helper, controller);
                    helper.assertTrue(array.isFormed(), "It didn't form");
                    MultiblockPorts.set(helper.getLevel(), array, helper.absolutePos(port), SideMode.HEAT, Direction.EAST);
                    array.getItems().setStack(FireboxArrayBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(cell, HeatCellBlockEntity.class).getHeat().getStored() > 0,
                        "No heat reached the Heat Cell"))
                .thenSucceed();
    }

    // A 5 wide, 7 deep, 4 tall boiler forms (its edges casings, a window in a wall), and scales by footprint and height:
    // 5 x 7 / 9 x 4 = 15.6 sections, so 9,333 HU/t and 248,889 mB tanks. A turbine 5 wide, 4 tall and 6 long along X forms
    // too: 13.3 sections, 533 mB/t, its rotor sized to the 4-block side; its ends have no middle block, so no generator.
    static void rectangularSteamArrays(GameTestHelper helper) {
        BlockPos boilerMin = new BlockPos(0, 1, 0);
        BlockPos pane = boilerMin.offset(2, 1, 0);
        buildBox(helper, boilerMin, 5, 4, 7, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), Set.of(pane, pane.above()));
        BlockPos turbineMin = new BlockPos(6, 1, 0);
        buildBox(helper, turbineMin, 6, 4, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), Set.of());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(boiler.isMaster(), "The 5x7x4 boiler didn't form");
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(pane)), "Its window isn't part of it");
                    helper.assertTrue(boiler.maxHeatPerTick() == 9_333, "It boils with " + boiler.maxHeatPerTick() + " HU/t");
                    helper.assertTrue(boiler.getWater().getCapacity() == 248_889, "Water tank " + boiler.getWater().getCapacity());
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(turbineMin, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.isMaster() && turbine.getShell() != null && turbine.getShell().axis() == Direction.Axis.X,
                            "The 5x4x6 turbine didn't form along X");
                    helper.assertTrue(turbine.maxFlow() == 533, "Turbine flow " + turbine.maxFlow());
                    helper.assertTrue(turbine.getShell().narrowSide() == 4, "Narrow side " + turbine.getShell().narrowSide());
                    helper.assertTrue(helper.getBlockState(turbineMin.offset(5, 1, 2)).getValue(SteamTurbineArrayCasingBlock.END)
                            == SteamTurbineArrayCasingBlock.End.NONE, "An even end has a generator");
                    helper.assertTrue(turbine.getEnergy().getCapacityAsInt() == turbine.maxOutput() * ArcforgeConfig.TURBINE_ARRAY_ENERGY_BUFFER_TICKS.getAsInt(),
                            "FE buffer " + turbine.getEnergy().getCapacityAsInt());
                    // Glass on an edge of a box wider than 3 isn't allowed.
                    helper.setBlock(boilerMin.offset(0, 1, 0), ModBlocks.PRESSURE_GLASS.get());
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(!ShellCasingBlock.isFormed(helper.getBlockState(boilerMin)), "A boiler with glass on an edge stayed formed"))
                .thenSucceed();
    }

    // A 5x3x4 Superheater box forms as one connected skin with its master at the minimum corner, and scales by its 60 blocks
    // (60 / 27 cubes): 2,222 mB/t and 4,444 HU/t. A 4x3x3 Condenser box condenses 160 mB/t in open air. Breaking a casing
    // breaks it.
    static void boxArrays(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        BlockPos condenserMin = new BlockPos(0, 1, 6);
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(4, 2, 3))) {
            helper.setBlock(pos.immutable(), ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        }
        for (BlockPos pos : BlockPos.betweenClosed(condenserMin, condenserMin.offset(3, 2, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.CONDENSER_ARRAY_CASING.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity superheater = helper.getBlockEntity(min, SuperheaterArrayBlockEntity.class);
                    helper.assertTrue(superheater.isFormed() && superheater.getBox() != null, "The 5x3x4 Superheater didn't form as a box");
                    helper.assertTrue(CubeCasingBlock.isBox(helper.getBlockState(min.offset(2, 1, 2))), "A casing isn't part of the box");
                    helper.assertTrue(superheater.maxFlow() == 2_222 && superheater.maxHeatPerTick() == 4_444,
                            "Superheater: " + superheater.maxFlow() + " mB/t, " + superheater.maxHeatPerTick() + " HU/t");
                    CondenserArrayBlockEntity condenser = helper.getBlockEntity(condenserMin, CondenserArrayBlockEntity.class);
                    helper.assertTrue(condenser.isFormed() && condenser.getBox() != null, "The 4x3x3 Condenser didn't form as a box");
                    helper.setBlock(min.offset(4, 2, 3), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!CubeCasingBlock.isFormed(helper.getBlockState(min)), "The Superheater stayed formed with a casing missing");
                    CondenserArrayBlockEntity condenser = helper.getBlockEntity(condenserMin, CondenserArrayBlockEntity.class);
                    helper.assertTrue(CondenserArrayBlockEntity.cooling(helper.getLevel(), helper.absolutePos(condenserMin),
                            helper.absolutePos(condenserMin.offset(3, 2, 2))).air() == 160, "Open air condenses "
                            + CondenserArrayBlockEntity.cooling(helper.getLevel(), helper.absolutePos(condenserMin), helper.absolutePos(condenserMin.offset(3, 2, 2))).air());
                    helper.assertTrue(condenser.isFormed(), "The Condenser broke");
                })
                .thenSucceed();
    }

    // Every generator's FE goes through PowerGeneration: the turbines' caps are their best output times the multiplier,
    // and the energy balance counts it, so the steam route stays 66.08 FE per 14 HU times the multiplier.
    static void powerMultiplier(GameTestHelper helper) {
        double multiplier = PowerGeneration.multiplier();
        double lubricant = ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble(), vacuum = ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble();
        int flow = ArcforgeConfig.TURBINE_ARRAY_FLOW_PER_SECTION.getAsInt() * 3;
        helper.assertTrue(SteamTurbineArrayBlockEntity.maxOutput(3.0) == PowerGeneration.cap(flow * SteamGrade.SUPERHEATED.arrayFePerMb() * (1.0 + lubricant + vacuum)),
                "The Steam Turbine Array's cap is " + SteamTurbineArrayBlockEntity.maxOutput(3.0));
        helper.assertTrue(GasTurbineArrayBlockEntity.maxOutput(9) == PowerGeneration.cap(ArcforgeConfig.GAS_TURBINE_MAX_HU_PER_LENGTH.getAsInt() * 9.0
                * ArcforgeConfig.GAS_TURBINE_SIMPLE_CYCLE_FACTOR.getAsDouble() * (1.0 + lubricant)), "The Gas Turbine Array's cap is " + GasTurbineArrayBlockEntity.maxOutput(9));
        helper.assertTrue(PowerGeneration.fe(1_000.0) == Math.round(1_000.0 * multiplier), "1,000 FE comes out as " + PowerGeneration.fe(1_000.0));
        double steam = EnergyBalance.steamFePerHu(SteamGrade.STEAM, SteamGrade.SUPERHEATED);
        helper.assertTrue(Math.abs(steam - 56 * (1.0 + lubricant + vacuum) / 14.0 * multiplier) < 1e-9, "The steam route gives " + steam + " FE/HU");
        helper.succeed();
    }
}
