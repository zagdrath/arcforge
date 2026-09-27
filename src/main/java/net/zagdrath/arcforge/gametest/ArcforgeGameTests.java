/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.registry.ModBlocks;

// In-game tests, run with `gradlew runGameTestServer` or `/test runall` in a dev client.
public final class ArcforgeGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Arcforge.MODID);

    private static final String REDSTONE_BUTTONS = "redstone_buttons";
    private static final String SIDE_CONFIG_BUTTONS = "side_config_buttons";

    // Every test, by name. All run in the same batch on an empty structure with room around it.
    private static final java.util.Map<String, Consumer<GameTestHelper>> TESTS = new java.util.LinkedHashMap<>();
    // Tests that wait on full machine cycles (a Carbonizer chamber, heating the Arcforge Furnace) get longer to finish.
    private static final java.util.Map<String, Consumer<GameTestHelper>> LONG_TESTS = new java.util.LinkedHashMap<>();

    static {
        TESTS.put(REDSTONE_BUTTONS, ArcforgeGameTests::redstoneButtons);
        TESTS.put(SIDE_CONFIG_BUTTONS, ArcforgeGameTests::sideConfigButtons);
        TESTS.put("conduit_energy_transfer", ConduitGameTests::energyTransfer);
        TESTS.put("conduit_item_transfer", ConduitGameTests::itemTransfer);
        TESTS.put("conduit_fluid_transfer", ConduitGameTests::fluidTransfer);
        TESTS.put("conduit_fluid_split", ConduitGameTests::fluidSplit);
        TESTS.put("conduit_auto_connect", ConduitGameTests::autoConnect);
        TESTS.put("conduit_energy_storage", ConduitGameTests::energyStorage);
        TESTS.put("conduit_glow_extends", ConduitGameTests::glowExtends);
        TESTS.put("conduit_item_storage", ConduitGameTests::itemStorage);
        TESTS.put("wrench_conduit", ConduitGameTests::wrenchConduit);
        TESTS.put("wrench_machine", ConduitGameTests::wrenchMachine);
        TESTS.put("tank_buckets", StorageGameTests::tankBuckets);
        TESTS.put("tank_spills", StorageGameTests::tankSpills);
        TESTS.put("tank_wrench_keeps_fluid", StorageGameTests::tankWrenchKeepsFluid);
        TESTS.put("tanks_stack", StorageGameTests::tanksStack);
        TESTS.put("machines_drop_items", StorageGameTests::machinesDropItems);
        TESTS.put("cell_charge", StorageGameTests::cellCharge);
        TESTS.put("cell_output_and_conduit", StorageGameTests::cellOutputAndConduit);
        TESTS.put("generator_burns", HeatGameTests::generatorBurns);
        TESTS.put("geothermal_makes_heat", HeatGameTests::geothermalMakesHeat);
        TESTS.put("firebox_drives_thermoelectric", HeatGameTests::fireboxDrivesThermoelectric);
        TESTS.put("heat_flows_hot_to_cold", HeatGameTests::heatFlowsHotToCold);
        TESTS.put("thermal_conduit_carries_heat", HeatGameTests::thermalConduitCarriesHeat);
        TESTS.put("carbonizer_forms", MultiblockGameTests::carbonizerForms);
        TESTS.put("furnace_forms", MultiblockGameTests::furnaceForms);
        LONG_TESTS.put("carbonizer_processes", MultiblockGameTests::carbonizerProcesses);
        LONG_TESTS.put("furnace_smelts", MultiblockGameTests::furnaceSmelts);
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        LONG_TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private ArcforgeGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ArcforgeGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                600, 0, true, Rotation.NONE, false, 1, 1, false, 8);
        TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(function(name), data)));
        TestData<Holder<TestEnvironmentDefinition<?>>> longData = new TestData<>(environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                1_500, 0, true, Rotation.NONE, false, 1, 1, false, 8);
        LONG_TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(function(name), longData)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, path);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    // Clicking a redstone button sends a container button packet; the server must apply the mode
    // and the synced menu data must report it back.
    @SuppressWarnings("removal")
    private static void redstoneButtons(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(pos);
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        // The mock connection can't receive NeoForge's open-screen payload, so attach the server menu directly.
        GeothermalPlantMenu menu = (GeothermalPlantMenu) plant.createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;

        // Lava next to the plant makes it heat up, which toggles the LIT block state while ticking.
        helper.setBlock(pos.east(), net.minecraft.world.level.block.Blocks.LAVA);

        var sequence = helper.startSequence();
        for (RedstoneMode mode : new RedstoneMode[] { RedstoneMode.LOW, RedstoneMode.HIGH, RedstoneMode.IGNORE }) {
            sequence.thenExecute(() -> player.connection.handleContainerButtonClick(
                            new ServerboundContainerButtonClickPacket(menu.containerId, GeothermalPlantMenu.redstoneButtonId(mode))))
                    .thenIdle(40)
                    .thenExecute(() -> {
                        GeothermalPlantBlockEntity current = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
                        helper.assertTrue(current == plant, "Block entity was replaced while ticking");
                        helper.assertTrue(current.getRedstoneMode() == mode, "Block entity mode is " + current.getRedstoneMode() + ", expected " + mode);
                        helper.assertTrue(menu.getRedstoneMode() == mode, "Menu data reports " + menu.getRedstoneMode() + ", expected " + mode);
                    });
        }
        sequence.thenSucceed();
    }

    // The front face is configurable like any other, and "clear all" sets every face to none.
    @SuppressWarnings("removal")
    private static void sideConfigButtons(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.GEOTHERMAL_PLANT.get());
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(pos, GeothermalPlantBlockEntity.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(pos);
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        GeothermalPlantMenu menu = (GeothermalPlantMenu) plant.createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;

        // Front starts as none; one "next" click makes it an input.
        player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId,
                GeothermalPlantMenu.sideButtonId(RelativeSide.FRONT, SideConfig.ACTION_NEXT)));
        helper.assertTrue(plant.getSideMode(RelativeSide.FRONT) == SideMode.INPUT, "Front is " + plant.getSideMode(RelativeSide.FRONT) + ", expected INPUT");
        helper.assertTrue(menu.getSideMode(RelativeSide.FRONT) == SideMode.INPUT, "Menu reports front as " + menu.getSideMode(RelativeSide.FRONT));
        var facing = helper.getBlockState(pos).getValue(net.zagdrath.arcforge.block.machine.GeothermalPlantBlock.FACING);
        helper.assertTrue(plant.getFluidHandler(facing) != null, "Front input face does not expose the lava tank");

        player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, GeothermalPlantMenu.BUTTON_CLEAR_SIDES));
        for (RelativeSide side : RelativeSide.values()) {
            helper.assertTrue(plant.getSideMode(side) == SideMode.NONE, side + " is " + plant.getSideMode(side) + " after clear all");
        }
        helper.assertTrue(plant.getHeatHandler(facing.getOpposite()) == null, "Back still exposes heat after clear all");
        helper.succeed();
    }
}
