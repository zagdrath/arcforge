/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorControllerBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorPart;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.client.model.WindowQuadrants;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The Thermal Evaporator Array (it forms as a 3x3x9 tower with a hollow core and windows up the middle of its sides, and
// not with anything in its core, glass on a corner or a layer missing; it boils Seawater into Brine and Brine into Salt
// with heat, returning water; it does nothing below 100°C and speeds up with heat), Seawater from the Electric Pump in an
// ocean, its windows reaching half a block into the casings round them (but not into the controller's side of the base),
// Halite (drops, crushing, tags, worldgen) and the Evaporation Pond's blocks loading as the tower's.
public final class ThermalEvaporatorGameTests {
    private ThermalEvaporatorGameTests() {}

    // A tower at min (relative), the controller in the middle of the north side of its bottom layer facing north, and
    // Pressure Glass up the middle of its east side if windows is set. Returns the controller's position.
    static BlockPos buildTower(GameTestHelper helper, BlockPos min, boolean windows) {
        for (int y = 0; y < ThermalEvaporatorStructure.HEIGHT; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    BlockPos pos = min.offset(x, y, z);
                    boolean core = x == 1 && z == 1 && y > 0 && y < ThermalEvaporatorStructure.HEIGHT - 1;
                    boolean window = windows && x == 2 && z == 1 && y > 0 && y < ThermalEvaporatorStructure.HEIGHT - 1;
                    helper.setBlock(pos, core ? Blocks.AIR : window ? ModBlocks.PRESSURE_GLASS.get() : ModBlocks.THERMAL_EVAPORATOR_CASING.get());
                }
            }
        }
        BlockPos controller = min.offset(1, 0, 0);
        helper.setBlock(controller, ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get().defaultBlockState()
                .setValue(ThermalEvaporatorControllerBlock.FACING, Direction.NORTH));
        return controller;
    }

    private static ThermalEvaporatorBlockEntity tower(GameTestHelper helper, BlockPos controller) {
        return helper.getBlockEntity(controller, ThermalEvaporatorBlockEntity.class);
    }

    private static void fill(FilteredFluidTank tank, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            tank.insert(0, FluidResource.of(fluid), amount, tx);
            tx.commit();
        }
    }

    // Windows reach half a block into the casings round them, as on the steam and gas turbine arrays: the corners beside a
    // window, the cap above it and the base below it show window on the half toward the glass. On the controller's side
    // the base stays solid, so the controller's display is never cut.
    static void windows(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildTower(helper, min, true);
        for (int y = 1; y < ThermalEvaporatorStructure.HEIGHT - 1; y++) {
            helper.setBlock(min.offset(1, y, 0), ModBlocks.PRESSURE_GLASS.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(tower(helper, controller).isFormed(), "The tower didn't form");
                    var level = helper.getLevel();
                    // East side (window at x 2, z 1): on an east face, texture-right is north.
                    BlockPos corner = helper.absolutePos(min.offset(2, 4, 2));
                    helper.assertTrue(WindowQuadrants.windowedEvaporator(level, corner, Direction.EAST, true, true),
                            "The corner's half beside the window isn't window");
                    helper.assertTrue(!WindowQuadrants.windowedEvaporator(level, corner, Direction.EAST, true, false),
                            "The corner's far half is window");
                    BlockPos cap = helper.absolutePos(min.offset(2, 8, 1));
                    helper.assertTrue(WindowQuadrants.windowedEvaporator(level, cap, Direction.EAST, false, true)
                            && !WindowQuadrants.windowedEvaporator(level, cap, Direction.EAST, true, true), "The cap's lower half isn't window");
                    BlockPos base = helper.absolutePos(min.offset(2, 0, 1));
                    helper.assertTrue(WindowQuadrants.windowedEvaporator(level, base, Direction.EAST, true, false)
                            && !WindowQuadrants.windowedEvaporator(level, base, Direction.EAST, false, false), "The base's upper half isn't window");
                    // North side, the controller's: on a north face, texture-right is west.
                    for (boolean right : new boolean[] { false, true }) {
                        helper.assertTrue(!WindowQuadrants.windowedEvaporator(level, helper.absolutePos(controller), Direction.NORTH, true, right),
                                "The window cuts into the controller");
                        helper.assertTrue(!WindowQuadrants.windowedEvaporator(level, helper.absolutePos(min), Direction.NORTH, true, right),
                                "The window cuts into the base beside the controller");
                    }
                    helper.assertTrue(WindowQuadrants.windowedEvaporator(level, helper.absolutePos(min.offset(0, 1, 0)), Direction.NORTH, true, false),
                            "The ring corner beside the controller's window isn't window");
                })
                .thenSucceed();
    }

    // A tower with a window forms: the casings, the controller and the panes are marked formed, the controller faces out.
    // Breaking a casing in its ring unforms it.
    static void forms(GameTestHelper helper) {
        BlockPos controller = buildTower(helper, new BlockPos(0, 1, 1), true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(tower(helper, controller).isFormed(), "The tower didn't form");
                    helper.assertTrue(helper.getBlockState(new BlockPos(0, 5, 1)).getValue(ThermalEvaporatorPart.FORMED), "A ring casing isn't formed");
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 9, 2)).getValue(ThermalEvaporatorPart.FORMED), "The cap isn't formed");
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(new BlockPos(2, 4, 2))), "A window isn't formed");
                    helper.assertTrue(helper.getBlockState(controller).getValue(ThermalEvaporatorControllerBlock.FACING) == Direction.NORTH,
                            "The controller turned");
                    helper.setBlock(new BlockPos(0, 6, 1), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!tower(helper, controller).isFormed(), "The tower stayed formed with a hole in its wall");
                    helper.assertTrue(!PressureGlassBlock.isFormed(helper.getBlockState(new BlockPos(2, 4, 2))), "A window stayed formed");
                    helper.assertTrue(tower(helper, controller).getStatus() == MachineStatus.NOT_FORMED, "It's " + tower(helper, controller).getStatus());
                })
                .thenSucceed();
    }

    // Something in the core, glass on a corner, or the cap missing: no tower.
    static void rejectsBadTowers(GameTestHelper helper) {
        BlockPos blocked = buildTower(helper, new BlockPos(0, 1, 1), false);
        helper.setBlock(new BlockPos(1, 5, 2), Blocks.STONE);
        BlockPos corner = buildTower(helper, new BlockPos(4, 1, 1), false);
        helper.setBlock(new BlockPos(4, 4, 1), ModBlocks.PRESSURE_GLASS.get());
        BlockPos capless = buildTower(helper, new BlockPos(8, 1, 1), false);
        for (int x = 8; x < 11; x++) {
            for (int z = 1; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 9, z), Blocks.AIR);
            }
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!tower(helper, blocked).isFormed(), "A tower with stone in its core formed");
                    helper.assertTrue(!tower(helper, corner).isFormed(), "A tower with glass on a corner formed");
                    helper.assertTrue(!tower(helper, capless).isFormed(), "An 8-tall tower formed");
                })
                .thenSucceed();
    }

    // Hot (full speed), Seawater boils down into Brine at the configured throughput, using heat and returning water.
    static void seawaterToBrine(GameTestHelper helper) {
        BlockPos controller = buildTower(helper, new BlockPos(0, 1, 1), true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    helper.assertTrue(tower.isFormed(), "The tower didn't form");
                    fill(tower.getInput(), ModFluids.SEAWATER.get(), 4_000);
                    tower.getHeat().add(tower.getHeat().getCapacity());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    helper.assertTrue(tower.getStatus() == MachineStatus.EVAPORATING, "Hot with Seawater it's " + tower.getStatus());
                    helper.assertTrue(Math.abs(tower.getRate() - ArcforgeConfig.EVAPORATOR_THROUGHPUT.getAsInt()) < 0.01,
                            "It evaporates " + tower.getRate() + " mB/t at " + tower.getHeat().getTemperature() + "°C");
                    helper.assertTrue(tower.getHeatUsage() > 0, "It used no heat");
                })
                .thenWaitUntil(() -> helper.assertTrue(tower(helper, controller).getOutput().contains(ModFluids.BRINE.get()), "No Brine yet"))
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    helper.assertTrue(tower.getWater().getAmount() > 0, "No water came back");
                    helper.assertTrue(tower.getInput().getAmount() < 4_000, "The Seawater wasn't used");
                    helper.assertTrue(!tower.showsSaltBed(), "A salt bed shows while it makes Brine");
                })
                .thenSucceed();
    }

    // Brine boils down into Salt, with the salt bed showing; a hopper's view of the tower sees it.
    static void brineToSalt(GameTestHelper helper) {
        BlockPos controller = buildTower(helper, new BlockPos(0, 1, 1), false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    fill(tower.getInput(), ModFluids.BRINE.get(), 2_000);
                    tower.getHeat().add(tower.getHeat().getCapacity());
                })
                .thenWaitUntil(() -> helper.assertTrue(tower(helper, controller).getSalt().is(ModItems.SALT.get()), "No Salt yet"))
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    helper.assertTrue(tower.showsSaltBed() || tower.getInput().getAmount() == 0, "No salt bed while it makes Salt");
                    helper.assertTrue(tower.getItemHandler(null).getAmountAsInt(ThermalEvaporatorBlockEntity.SLOT_SALT) >= 1, "Automation can't see the Salt");
                })
                .thenSucceed();
    }

    // Cold, it holds its input; the speed curve runs from minSpeed at 100°C to 1 at fullSpeedTemperature.
    static void needsHeat(GameTestHelper helper) {
        int min = ArcforgeConfig.EVAPORATOR_MIN_TEMPERATURE.getAsInt(), full = ArcforgeConfig.EVAPORATOR_FULL_SPEED_TEMPERATURE.getAsInt();
        helper.assertTrue(ThermalEvaporatorBlockEntity.speedAt(min - 1) == 0, "It works below " + min + "°C");
        helper.assertTrue(Math.abs(ThermalEvaporatorBlockEntity.speedAt(min) - ArcforgeConfig.EVAPORATOR_MIN_SPEED.getAsDouble()) < 1e-9, "Wrong speed at " + min);
        helper.assertTrue(ThermalEvaporatorBlockEntity.speedAt(full) == 1.0 && ThermalEvaporatorBlockEntity.speedAt(full + 500) == 1.0, "Not full speed when hot");
        helper.assertTrue(ThermalEvaporatorBlockEntity.speedAt((min + full) / 2) < 1.0, "No slower half way");
        BlockPos controller = buildTower(helper, new BlockPos(0, 1, 1), false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> fill(tower(helper, controller).getInput(), ModFluids.SEAWATER.get(), 1_000))
                .thenIdle(10)
                .thenExecute(() -> {
                    ThermalEvaporatorBlockEntity tower = tower(helper, controller);
                    helper.assertTrue(tower.getStatus() == MachineStatus.TOO_COLD, "Cold it's " + tower.getStatus());
                    helper.assertTrue(tower.getInput().getAmount() == 1_000, "Seawater went while cold");
                })
                .thenSucceed();
    }

    // The input tank takes Seawater and Brine and nothing else; the recipes are data-driven.
    static void inputs(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(MachineRecipes.isEvaporatingInput(level, FluidResource.of(ModFluids.SEAWATER.get())), "Seawater isn't an input");
        helper.assertTrue(MachineRecipes.isEvaporatingInput(level, FluidResource.of(ModFluids.BRINE.get())), "Brine isn't an input");
        helper.assertTrue(!MachineRecipes.isEvaporatingInput(level, FluidResource.of(Fluids.LAVA)), "Lava is an input");
        helper.succeed();
    }

    // In an ocean, the Electric Pump draws Seawater, not Water.
    static void pumpDrawsSeawater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FillBiomeCommand.fill(level, helper.absolutePos(new BlockPos(-1, -1, -1)), helper.absolutePos(new BlockPos(5, 5, 5)),
                level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.OCEAN));
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.WATER);
            helper.setBlock(new BlockPos(x, 1, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        }
        BlockPos pumpPos = new BlockPos(1, 2, 1);
        helper.setBlock(pumpPos, ModBlocks.ELECTRIC_PUMP.get());
        ElectricPumpBlockEntity pump = helper.getBlockEntity(pumpPos, ElectricPumpBlockEntity.class);
        FiberGameTests.charge(pump.getEnergy(), 20_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump.getTank().getAmount() >= 1_000, "No bucket pumped yet"))
                .thenExecute(() -> helper.assertTrue(pump.getTank().contains(ModFluids.SEAWATER.get()), "Pumped " + pump.getTank().getResource(0)))
                .thenSucceed();
    }

    // Halite drops Rock Salt (the ore itself with Silk Touch is in its loot table); Rock Salt crushes into 2 Salt as an
    // ore (so the Arc Crushing Array doubles it); the tags and the worldgen are there.
    static void halite(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        for (Block ore : List.of(ModBlocks.HALITE_ORE.get(), ModBlocks.DEEPSLATE_HALITE_ORE.get())) {
            List<ItemStack> drops = Block.getDrops(ore.defaultBlockState(), level, helper.absolutePos(pos), null);
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.ROCK_SALT.get())), ore + " drops " + drops);
        }
        var crushing = MachineRecipes.crushing(level, new ItemStack(ModItems.ROCK_SALT.get()));
        helper.assertTrue(crushing.isPresent() && crushing.get().value().ore()
                && crushing.get().value().result().map(result -> result.create().is(ModItems.SALT.get()) && result.create().getCount() == 2).orElse(false),
                "Rock Salt doesn't crush into 2 Salt as an ore");
        TagKey<Item> ores = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores/halite"));
        helper.assertTrue(new ItemStack(ModItems.HALITE_ORE.get()).is(ores), "Halite isn't #c:ores/halite");
        var placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .get(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "ore_halite")));
        helper.assertTrue(placed.isPresent(), "No halite worldgen");
        helper.succeed();
    }

    // Worlds from before keep their blocks: the Evaporation Pond's load as the tower's.
    static void pondAliases(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_liner")) == ModBlocks.THERMAL_EVAPORATOR_CASING.get(),
                "Pond Liner doesn't load as a Thermal Evaporator Casing");
        helper.assertTrue(BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_outlet")) == ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get(),
                "Pond Outlet doesn't load as a Thermal Evaporator Controller");
        helper.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_liner")) == ModItems.THERMAL_EVAPORATOR_CASING.get(),
                "The Pond Liner item doesn't become a Thermal Evaporator Casing");
        helper.succeed();
    }
}
