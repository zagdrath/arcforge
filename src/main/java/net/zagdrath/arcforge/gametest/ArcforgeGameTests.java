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
    // Solar Thermal Array tests set the time of day and the weather, which are the whole level's: each runs
    // in its own batch (its own environment), one after another, under open sky.
    private static final java.util.Map<String, Consumer<GameTestHelper>> SOLAR_TESTS = new java.util.LinkedHashMap<>();

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
        TESTS.put("ports_defaults", PortGameTests::defaultPorts);
        TESTS.put("ports_wrench_sets_port", PortGameTests::wrenchSetsPort);
        TESTS.put("ports_wrench_modes_on_multiblock", PortGameTests::wrenchModesOnMultiblock);
        TESTS.put("ports_turbine_ends", PortGameTests::turbinePorts);
        TESTS.put("ports_conduits_connect", PortGameTests::conduitsConnectToPorts);
        TESTS.put("tank_buckets", StorageGameTests::tankBuckets);
        TESTS.put("tank_spills", StorageGameTests::tankSpills);
        TESTS.put("tank_wrench_keeps_fluid", StorageGameTests::tankWrenchKeepsFluid);
        TESTS.put("tanks_stack", StorageGameTests::tanksStack);
        TESTS.put("machines_drop_items", StorageGameTests::machinesDropItems);
        TESTS.put("cell_charge", StorageGameTests::cellCharge);
        TESTS.put("cell_output_and_conduit", StorageGameTests::cellOutputAndConduit);
        TESTS.put("combustion_plant_burns", HeatGameTests::combustionPlantBurns);
        TESTS.put("geothermal_makes_heat", HeatGameTests::geothermalMakesHeat);
        TESTS.put("firebox_drives_thermoelectric", HeatGameTests::fireboxDrivesThermoelectric);
        TESTS.put("heat_flows_hot_to_cold", HeatGameTests::heatFlowsHotToCold);
        TESTS.put("thermal_conduit_carries_heat", HeatGameTests::thermalConduitCarriesHeat);
        TESTS.put("thermal_conduit_keeps_temperature", HeatGameTests::thermalConduitKeepsTemperature);
        TESTS.put("heat_cell_fills", HeatGameTests::heatCellFills);
        TESTS.put("heat_cell_leaks", HeatGameTests::heatCellLeaks);
        TESTS.put("heat_cell_outputs", HeatGameTests::heatCellOutputs);
        TESTS.put("heat_cell_keeps_heat", HeatGameTests::heatCellKeepsHeat);
        TESTS.put("fiberizer_accepts_inputs", FiberGameTests::fiberizerAcceptsInputs);
        TESTS.put("heat_cell_insulation", FiberGameTests::heatCellInsulation);
        TESTS.put("heat_cell_tier_temperature", FiberGameTests::heatCellTierTemperature);
        TESTS.put("geothermal_rebalance", FiberGameTests::geothermalRebalance);
        TESTS.put("thermoelectric_bonus", FiberGameTests::thermoelectricBonus);
        TESTS.put("infuser_accepts_vanilla_wood", InfusionGameTests::infuserAcceptsVanillaWood);
        TESTS.put("fuel_burner_burns_creosote", InfusionGameTests::fuelBurnerBurnsCreosote);
        TESTS.put("treated_wood_strips_and_resists_fire", InfusionGameTests::treatedWoodStripsAndResistsFire);
        TESTS.put("upgrade_slots", CrushingGameTests::upgradeSlots);
        TESTS.put("crusher_upgrades", CrushingGameTests::crusherUpgrades);
        TESTS.put("firebox_heat_upgrades", CrushingGameTests::fireboxHeatUpgrades);
        TESTS.put("array_forms", CrushingGameTests::arrayForms);
        TESTS.put("press_slots_and_recipes", PressingGameTests::pressSlotsAndRecipes);
        TESTS.put("press_upgrades", PressingGameTests::pressUpgrades);
        TESTS.put("pressing_array_routes_input", PressingGameTests::arrayRoutesInput);
        TESTS.put("steam_gas_rules", SteamGameTests::gasRules);
        TESTS.put("steam_gas_conduit_carries_steam", SteamGameTests::gasConduitCarriesSteam);
        TESTS.put("steam_pump_drains_source", SteamGameTests::pumpDrainsSource);
        TESTS.put("steam_pump_leaves_infinite_water", SteamGameTests::pumpLeavesInfiniteWater);
        TESTS.put("steam_boiler_grades", SteamGameTests::boilerGrades);
        TESTS.put("steam_boiler_pressure_holds", SteamGameTests::boilerPressureHolds);
        TESTS.put("steam_boiler_auto_holds_boiling", SteamGameTests::boilerAutoHoldsBoiling);
        TESTS.put("steam_turbine_output", SteamGameTests::turbineOutput);
        TESTS.put("steam_boiler_array_forms", SteamGameTests::boilerArrayForms);
        TESTS.put("steam_turbine_array_spins", SteamGameTests::turbineArraySpins);
        TESTS.put("steam_turbine_array_conduits", SteamGameTests::turbineArrayConduits);
        TESTS.put("steam_boiler_array_rebuild_keeps_sides", SteamGameTests::boilerArrayRebuildKeepsSides);
        TESTS.put("alloy_carbon_dust_carbonizes", AlloyGameTests::carbonDustCarbonizes);
        TESTS.put("alloy_furnace_recipes", AlloyGameTests::furnaceRecipes);
        TESTS.put("alloy_furnace_old_save_migrates", AlloyGameTests::furnaceOldSaveMigrates);
        TESTS.put("alloy_recipes_loaded", AlloyGameTests::recipesLoaded);
        TESTS.put("alloy_tier_upgrade_keeps_contents", AlloyGameTests::tierUpgradeKeepsContents);
        TESTS.put("portable_fluid_rules", AlloyGameTests::portableFluidRules);
        TESTS.put("portable_battery_in_energy_cell", AlloyGameTests::batteryInEnergyCell);
        TESTS.put("portable_canister_in_tank", AlloyGameTests::canisterInTank);
        TESTS.put("portable_cartridge_in_cylinder", AlloyGameTests::cartridgeInCylinder);
        TESTS.put("distillation_column_forms", DistillationGameTests::columnForms);
        TESTS.put("distillation_makes_fractions", DistillationGameTests::columnMakesFractions);
        TESTS.put("distillation_steam_stripping", DistillationGameTests::steamStripping);
        TESTS.put("distillation_steam_tank_mixes_down", DistillationGameTests::steamTankMixesDown);
        TESTS.put("distillation_burn_temperature", DistillationGameTests::burnTemperature);
        TESTS.put("distillation_turbine_lubricant", DistillationGameTests::turbineLubricant);
        TESTS.put("distillation_turbine_array_lubricant", DistillationGameTests::turbineArrayLubricant);
        TESTS.put("distillation_pitch_products", DistillationGameTests::pitchProducts);
        TESTS.put("portable_capsule_heat_flows_hot_to_cold", AlloyGameTests::capsuleHeatFlowsHotToCold);
        TESTS.put("steam_copper_parts_and_upgrades", SteamGameTests::copperPartsAndUpgrades);
        TESTS.put("induction_array_casings_dont_mix", InductionGameTests::arrayCasingsDontMix);
        TESTS.put("auto_eject", InductionGameTests::autoEject);
        TESTS.put("creosote_moves", InductionGameTests::creosoteMoves);
        TESTS.put("carbonizer_forms", MultiblockGameTests::carbonizerForms);
        TESTS.put("furnace_forms", MultiblockGameTests::furnaceForms);
        TESTS.put("carbonizer_takes_coal_blocks", MultiblockGameTests::carbonizerTakesCoalBlocks);
        TESTS.put("carbonizer_big_forms", MultiblockGameTests::carbonizerBigForms);
        TESTS.put("carbonizer_mixed_sizes", MultiblockGameTests::carbonizerMixedSizes);
        LONG_TESTS.put("carbonizer_processes", MultiblockGameTests::carbonizerProcesses);
        LONG_TESTS.put("furnace_smelts", MultiblockGameTests::furnaceSmelts);
        LONG_TESTS.put("furnace_burns_coke_blocks", MultiblockGameTests::furnaceBurnsCokeBlocks);
        LONG_TESTS.put("carbonizer_big_batch", MultiblockGameTests::carbonizerBigBatch);
        LONG_TESTS.put("crusher_recipes", CrushingGameTests::crusherRecipes);
        LONG_TESTS.put("array_doubles_ores", CrushingGameTests::arrayDoublesOres);
        LONG_TESTS.put("firebox_heats_plant_from_cold", HeatGameTests::fireboxHeatsPlantFromCold);
        LONG_TESTS.put("induction_furnace_smelts", InductionGameTests::furnaceSmelts);
        LONG_TESTS.put("induction_array_smelts", InductionGameTests::arraySmelts);
        LONG_TESTS.put("fiberizer_needs_heat", FiberGameTests::fiberizerNeedsHeat);
        LONG_TESTS.put("infuser_treats_planks", InfusionGameTests::infuserTreatsPlanks);
        LONG_TESTS.put("press_presses", PressingGameTests::pressPresses);
        LONG_TESTS.put("pressing_array_lanes", PressingGameTests::arrayPressesLanes);
        TESTS.put("solar_model_worked_values", SolarGameTests::modelWorkedValues);
        TESTS.put("ores_crushing_doubles", OreGameTests::crushingDoubles);
        TESTS.put("ores_raw_to_ingot", OreGameTests::rawToIngot);
        TESTS.put("ores_crystals", OreGameTests::crystals);
        TESTS.put("ores_storage_round_trip", OreGameTests::storageRoundTrip);
        TESTS.put("ores_invar_mix", OreGameTests::invarMix);
        TESTS.put("ores_fluorite_flux", OreGameTests::fluoriteFlux);
        TESTS.put("ores_two_additives", OreGameTests::twoAdditives);
        TESTS.put("ores_arcforged_alloy", OreGameTests::arcforgedAlloy);
        TESTS.put("ores_save_migration", OreGameTests::saveMigration);
        TESTS.put("ores_thermoelectric_upgrade", OreGameTests::thermoelectricUpgrade);
        TESTS.put("ores_arcite_pickaxe", OreGameTests::arcitePickaxe);
        TESTS.put("ores_tags", OreGameTests::tags);
        TESTS.put("ores_toggle", OreGameTests::oreToggle);
        TESTS.put("ores_vein_generates", OreGameTests::veinGenerates);
        SOLAR_TESTS.put("solar_zero_at_night", SolarGameTests::zeroAtNight);
        SOLAR_TESTS.put("solar_peak_at_noon", SolarGameTests::peakAtNoon);
        SOLAR_TESTS.put("solar_rain_reduces_output", SolarGameTests::rainReducesOutput);
        SOLAR_TESTS.put("solar_thunder_stows_panel", SolarGameTests::thunderStowsPanel);
        SOLAR_TESTS.put("solar_shading_one_collector", SolarGameTests::shadingOneCollector);
        SOLAR_TESTS.put("solar_north_south_beats_east_west", SolarGameTests::northSouthBeatsEastWest);
        SOLAR_TESTS.put("solar_boiler_grades", SolarGameTests::boilerGrades);
        SOLAR_TESTS.put("solar_no_sky", SolarGameTests::noSky);
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        SOLAR_TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
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
        SOLAR_TESTS.keySet().forEach(name -> {
            Holder<TestEnvironmentDefinition<?>> own = event.registerEnvironment(id(name));
            event.registerTest(id(name), new FunctionGameTestInstance(function(name), new TestData<>(own, Level.OVERWORLD,
                    Identifier.withDefaultNamespace("empty"), 1_500, 0, true, Rotation.NONE, false, 1, 1, true, 8)));
        });
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
