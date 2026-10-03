/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.blockentity.experience.XpDrainBlockEntity;
import net.zagdrath.arcforge.blockentity.experience.XpShowerBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.ResinTapBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.PlantingBedBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MillBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HarvesterBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.PlanterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.blockentity.redstone.ThrottleLeverBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SecurityTerminalBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChargepadBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.CrateBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.blockentity.farming.GlassClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GrowChamberBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Arcforge.MODID);

    public static final Supplier<BlockEntityType<GeothermalPlantBlockEntity>> GEOTHERMAL_PLANT = BLOCK_ENTITY_TYPES.register("geothermal_plant",
            () -> new BlockEntityType<>(GeothermalPlantBlockEntity::new, ModBlocks.GEOTHERMAL_PLANT.get()));

    public static final Supplier<BlockEntityType<CombustionPlantBlockEntity>> COMBUSTION_PLANT = BLOCK_ENTITY_TYPES.register("combustion_plant",
            () -> new BlockEntityType<>(CombustionPlantBlockEntity::new, ModBlocks.COMBUSTION_PLANT.get()));

    public static final Supplier<BlockEntityType<FireboxBlockEntity>> FIREBOX = BLOCK_ENTITY_TYPES.register("firebox",
            () -> new BlockEntityType<>(FireboxBlockEntity::new, ModBlocks.FIREBOX.get()));

    public static final Supplier<BlockEntityType<ArcCrusherBlockEntity>> ARC_CRUSHER = BLOCK_ENTITY_TYPES.register("arc_crusher",
            () -> new BlockEntityType<>(ArcCrusherBlockEntity::new, ModBlocks.ARC_CRUSHER.get()));

    // Every casing has one; only the centre of a formed Array runs.
    public static final Supplier<BlockEntityType<ArcCrushingArrayBlockEntity>> ARC_CRUSHING_ARRAY = BLOCK_ENTITY_TYPES.register("arc_crushing_array",
            () -> new BlockEntityType<>(ArcCrushingArrayBlockEntity::new, ModBlocks.ARC_CRUSHING_ARRAY_CASING.get()));

    public static final Supplier<BlockEntityType<InductionFurnaceBlockEntity>> INDUCTION_FURNACE = BLOCK_ENTITY_TYPES.register("induction_furnace",
            () -> new BlockEntityType<>(InductionFurnaceBlockEntity::new, ModBlocks.INDUCTION_FURNACE.get()));

    // Every casing has one; only the centre of a formed Array runs.
    public static final Supplier<BlockEntityType<InductionFurnaceArrayBlockEntity>> INDUCTION_FURNACE_ARRAY = BLOCK_ENTITY_TYPES.register("induction_furnace_array",
            () -> new BlockEntityType<>(InductionFurnaceArrayBlockEntity::new, ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get()));

    public static final Supplier<BlockEntityType<MetalPressBlockEntity>> METAL_PRESS = BLOCK_ENTITY_TYPES.register("metal_press",
            () -> new BlockEntityType<>(MetalPressBlockEntity::new, ModBlocks.METAL_PRESS.get()));

    public static final Supplier<BlockEntityType<ThrottleLeverBlockEntity>> THROTTLE_LEVER = BLOCK_ENTITY_TYPES.register("throttle_lever",
            () -> new BlockEntityType<>(ThrottleLeverBlockEntity::new, ModBlocks.THROTTLE_LEVER.get()));
    public static final Supplier<BlockEntityType<SecurityTerminalBlockEntity>> SECURITY_TERMINAL = BLOCK_ENTITY_TYPES.register("security_terminal",
            () -> new BlockEntityType<>(SecurityTerminalBlockEntity::new, ModBlocks.SECURITY_TERMINAL.get()));

    // One type for all four meters: the block says which kind it is.
    public static final Supplier<BlockEntityType<MeterBlockEntity>> METER = BLOCK_ENTITY_TYPES.register("meter",
            () -> new BlockEntityType<>(MeterBlockEntity::new, ModBlocks.ENERGY_METER.get(), ModBlocks.HEAT_METER.get(), ModBlocks.FLUID_METER.get(),
                    ModBlocks.GAS_METER.get()));

    public static final Supplier<BlockEntityType<CompostBinBlockEntity>> COMPOST_BIN = BLOCK_ENTITY_TYPES.register("compost_bin",
            () -> new BlockEntityType<>(CompostBinBlockEntity::new, ModBlocks.COMPOST_BIN.get()));
    public static final Supplier<BlockEntityType<PlanterBlockEntity>> PLANTER = BLOCK_ENTITY_TYPES.register("planter",
            () -> new BlockEntityType<>(PlanterBlockEntity::new, ModBlocks.PLANTER.get()));
    public static final Supplier<BlockEntityType<HarvesterBlockEntity>> HARVESTER = BLOCK_ENTITY_TYPES.register("harvester",
            () -> new BlockEntityType<>(HarvesterBlockEntity::new, ModBlocks.HARVESTER.get()));
    public static final Supplier<BlockEntityType<FertilizerSpreaderBlockEntity>> FERTILIZER_SPREADER = BLOCK_ENTITY_TYPES.register("fertilizer_spreader",
            () -> new BlockEntityType<>(FertilizerSpreaderBlockEntity::new, ModBlocks.FERTILIZER_SPREADER.get()));
    public static final Supplier<BlockEntityType<CopperSprinklerBlockEntity>> COPPER_SPRINKLER = BLOCK_ENTITY_TYPES.register("copper_sprinkler",
            () -> new BlockEntityType<>(CopperSprinklerBlockEntity::new, ModBlocks.COPPER_SPRINKLER.get()));
    public static final Supplier<BlockEntityType<MillstoneBlockEntity>> MILLSTONE = BLOCK_ENTITY_TYPES.register("millstone",
            () -> new BlockEntityType<>(MillstoneBlockEntity::new, ModBlocks.MILLSTONE.get()));
    public static final Supplier<BlockEntityType<MillBlockEntity>> MILL = BLOCK_ENTITY_TYPES.register("mill",
            () -> new BlockEntityType<>(MillBlockEntity::new, ModBlocks.MILL.get()));
    public static final Supplier<BlockEntityType<OilPressBlockEntity>> OIL_PRESS = BLOCK_ENTITY_TYPES.register("oil_press",
            () -> new BlockEntityType<>(OilPressBlockEntity::new, ModBlocks.OIL_PRESS.get()));
    public static final Supplier<BlockEntityType<SeedExtractorBlockEntity>> SEED_EXTRACTOR = BLOCK_ENTITY_TYPES.register("seed_extractor",
            () -> new BlockEntityType<>(SeedExtractorBlockEntity::new, ModBlocks.SEED_EXTRACTOR.get()));
    public static final Supplier<BlockEntityType<GrainDryerBlockEntity>> GRAIN_DRYER = BLOCK_ENTITY_TYPES.register("grain_dryer",
            () -> new BlockEntityType<>(GrainDryerBlockEntity::new, ModBlocks.GRAIN_DRYER.get()));
    public static final Supplier<BlockEntityType<VulcanizerBlockEntity>> VULCANIZER = BLOCK_ENTITY_TYPES.register("vulcanizer",
            () -> new BlockEntityType<>(VulcanizerBlockEntity::new, ModBlocks.VULCANIZER.get()));
    public static final Supplier<BlockEntityType<ResinTapBlockEntity>> RESIN_TAP = BLOCK_ENTITY_TYPES.register("resin_tap",
            () -> new BlockEntityType<>(ResinTapBlockEntity::new, ModBlocks.RESIN_TAP.get()));
    // Farm chemistry.
    public static final Supplier<BlockEntityType<AirSeparatorBlockEntity>> AIR_SEPARATOR = BLOCK_ENTITY_TYPES.register("air_separator",
            () -> new BlockEntityType<>(AirSeparatorBlockEntity::new, ModBlocks.AIR_SEPARATOR.get()));
    public static final Supplier<BlockEntityType<HaberReactorBlockEntity>> HABER_REACTOR = BLOCK_ENTITY_TYPES.register("haber_reactor",
            () -> new BlockEntityType<>(HaberReactorBlockEntity::new, ModBlocks.HABER_REACTOR.get()));
    public static final Supplier<BlockEntityType<BiogasDigesterBlockEntity>> BIOGAS_DIGESTER = BLOCK_ENTITY_TYPES.register("biogas_digester",
            () -> new BlockEntityType<>(BiogasDigesterBlockEntity::new, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get()));

    // The Firebox Array, on its controller; casings and panes are plain blocks that find it.
    public static final Supplier<BlockEntityType<net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity>> FIREBOX_ARRAY =
            BLOCK_ENTITY_TYPES.register("firebox_array", () -> new BlockEntityType<>(
                    net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity::new, ModBlocks.FIREBOX_ARRAY_CONTROLLER.get()));

    // The Thermal Evaporator Array, on its controller; casings and panes are plain blocks that find it.
    public static final Supplier<BlockEntityType<net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity>> THERMAL_EVAPORATOR =
            BLOCK_ENTITY_TYPES.register("thermal_evaporator", () -> new BlockEntityType<>(
                    net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity::new, ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get()));

    // Automated farms.
    public static final Supplier<BlockEntityType<GlassClocheBlockEntity>> GLASS_CLOCHE = BLOCK_ENTITY_TYPES.register("glass_cloche",
            () -> new BlockEntityType<>(GlassClocheBlockEntity::new, ModBlocks.GLASS_CLOCHE.get()));
    public static final Supplier<BlockEntityType<GrowChamberBlockEntity>> GROW_CHAMBER = BLOCK_ENTITY_TYPES.register("grow_chamber",
            () -> new BlockEntityType<>(GrowChamberBlockEntity::new, ModBlocks.GROW_CHAMBER.get()));
    public static final Supplier<BlockEntityType<HydroponicCellBlockEntity>> HYDROPONIC_CELL = BLOCK_ENTITY_TYPES.register("hydroponic_cell",
            () -> new BlockEntityType<>(HydroponicCellBlockEntity::new, ModBlocks.HYDROPONIC_CELL.get()));

    // The Greenhouse Array: its controller, and each Planting Bed (its soil, seed and growth).
    public static final Supplier<BlockEntityType<GreenhouseBlockEntity>> GREENHOUSE = BLOCK_ENTITY_TYPES.register("greenhouse",
            () -> new BlockEntityType<>(GreenhouseBlockEntity::new, ModBlocks.GREENHOUSE_CONTROLLER.get()));
    public static final Supplier<BlockEntityType<PlantingBedBlockEntity>> PLANTING_BED = BLOCK_ENTITY_TYPES.register("planting_bed",
            () -> new BlockEntityType<>(PlantingBedBlockEntity::new, ModBlocks.PLANTING_BED.get()));

    public static final Supplier<BlockEntityType<ChargepadBlockEntity>> CHARGEPAD = BLOCK_ENTITY_TYPES.register("chargepad",
            () -> new BlockEntityType<>(ChargepadBlockEntity::new, ModBlocks.CHARGEPAD.get()));

    public static final Supplier<BlockEntityType<ElectricPumpBlockEntity>> ELECTRIC_PUMP = BLOCK_ENTITY_TYPES.register("electric_pump",
            () -> new BlockEntityType<>(ElectricPumpBlockEntity::new, ModBlocks.ELECTRIC_PUMP.get()));

    public static final Supplier<BlockEntityType<FermenterBlockEntity>> FERMENTER = BLOCK_ENTITY_TYPES.register("fermenter",
            () -> new BlockEntityType<>(FermenterBlockEntity::new, ModBlocks.FERMENTER.get()));

    public static final Supplier<BlockEntityType<ArcMelterBlockEntity>> ARC_MELTER = BLOCK_ENTITY_TYPES.register("arc_melter",
            () -> new BlockEntityType<>(ArcMelterBlockEntity::new, ModBlocks.ARC_MELTER.get()));

    public static final Supplier<BlockEntityType<ChemicalReactorBlockEntity>> CHEMICAL_REACTOR = BLOCK_ENTITY_TYPES.register("chemical_reactor",
            () -> new BlockEntityType<>(ChemicalReactorBlockEntity::new, ModBlocks.CHEMICAL_REACTOR.get()));
    public static final Supplier<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER = BLOCK_ENTITY_TYPES.register("electrolyzer",
            () -> new BlockEntityType<>(ElectrolyzerBlockEntity::new, ModBlocks.ELECTROLYZER.get()));
    public static final Supplier<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER = BLOCK_ENTITY_TYPES.register("assembler",
            () -> new BlockEntityType<>(AssemblerBlockEntity::new, ModBlocks.ASSEMBLER.get()));
    public static final Supplier<BlockEntityType<BlockBreakerBlockEntity>> BLOCK_BREAKER = BLOCK_ENTITY_TYPES.register("block_breaker",
            () -> new BlockEntityType<>(BlockBreakerBlockEntity::new, ModBlocks.BLOCK_BREAKER.get()));
    public static final Supplier<BlockEntityType<BlockPlacerBlockEntity>> BLOCK_PLACER = BLOCK_ENTITY_TYPES.register("block_placer",
            () -> new BlockEntityType<>(BlockPlacerBlockEntity::new, ModBlocks.BLOCK_PLACER.get()));
    public static final Supplier<BlockEntityType<VacuumCollectorBlockEntity>> VACUUM_COLLECTOR = BLOCK_ENTITY_TYPES.register("vacuum_collector",
            () -> new BlockEntityType<>(VacuumCollectorBlockEntity::new, ModBlocks.VACUUM_COLLECTOR.get()));
    public static final Supplier<BlockEntityType<ArcQuarryBlockEntity>> ARC_QUARRY = BLOCK_ENTITY_TYPES.register("arc_quarry",
            () -> new BlockEntityType<>(ArcQuarryBlockEntity::new, ModBlocks.ARC_QUARRY.get()));
    public static final Supplier<BlockEntityType<ArcQuarryBoundingBlockEntity>> ARC_QUARRY_BOUNDING = BLOCK_ENTITY_TYPES.register("arc_quarry_bounding",
            () -> new BlockEntityType<>(ArcQuarryBoundingBlockEntity::new, ModBlocks.ARC_QUARRY_BOUNDING.get()));

    // Every casing has one; only the master (the minimum corner) runs.
    public static final Supplier<BlockEntityType<SteamBoilerArrayBlockEntity>> STEAM_BOILER_ARRAY = BLOCK_ENTITY_TYPES.register("steam_boiler_array",
            () -> new BlockEntityType<>(SteamBoilerArrayBlockEntity::new, ModBlocks.STEAM_BOILER_ARRAY_CASING.get()));

    public static final Supplier<BlockEntityType<SteamTurbineArrayBlockEntity>> STEAM_TURBINE_ARRAY = BLOCK_ENTITY_TYPES.register("steam_turbine_array",
            () -> new BlockEntityType<>(SteamTurbineArrayBlockEntity::new, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get()));
    public static final Supplier<BlockEntityType<GasTurbineArrayBlockEntity>> GAS_TURBINE_ARRAY = BLOCK_ENTITY_TYPES.register("gas_turbine_array",
            () -> new BlockEntityType<>(GasTurbineArrayBlockEntity::new, ModBlocks.GAS_TURBINE_ARRAY_CASING.get()));

    // Every casing has one; only the centre runs.
    public static final Supplier<BlockEntityType<SuperheaterArrayBlockEntity>> SUPERHEATER_ARRAY = BLOCK_ENTITY_TYPES.register("superheater_array",
            () -> new BlockEntityType<>(SuperheaterArrayBlockEntity::new, ModBlocks.SUPERHEATER_ARRAY_CASING.get()));

    public static final Supplier<BlockEntityType<CondenserArrayBlockEntity>> CONDENSER_ARRAY = BLOCK_ENTITY_TYPES.register("condenser_array",
            () -> new BlockEntityType<>(CondenserArrayBlockEntity::new, ModBlocks.CONDENSER_ARRAY_CASING.get()));

    // Every casing has one; only the centre's runs.
    public static final Supplier<BlockEntityType<MetalPressingArrayBlockEntity>> METAL_PRESSING_ARRAY = BLOCK_ENTITY_TYPES.register("metal_pressing_array",
            () -> new BlockEntityType<>(MetalPressingArrayBlockEntity::new, ModBlocks.METAL_PRESSING_ARRAY_CASING.get()));

    public static final Supplier<BlockEntityType<FiberizerBlockEntity>> FIBERIZER = BLOCK_ENTITY_TYPES.register("fiberizer",
            () -> new BlockEntityType<>(FiberizerBlockEntity::new, ModBlocks.FIBERIZER.get()));

    public static final Supplier<BlockEntityType<FuelBurnerBlockEntity>> FUEL_BURNER = BLOCK_ENTITY_TYPES.register("fuel_burner",
            () -> new BlockEntityType<>(FuelBurnerBlockEntity::new, ModBlocks.FUEL_BURNER.get()));

    public static final Supplier<BlockEntityType<InfuserBlockEntity>> INFUSER = BLOCK_ENTITY_TYPES.register("infuser",
            () -> new BlockEntityType<>(InfuserBlockEntity::new, ModBlocks.INFUSER.get()));

    public static final Supplier<BlockEntityType<ThermoelectricPlantBlockEntity>> THERMOELECTRIC_PLANT = BLOCK_ENTITY_TYPES.register("thermoelectric_plant",
            () -> new BlockEntityType<>(ThermoelectricPlantBlockEntity::new, ModBlocks.THERMOELECTRIC_PLANT.get()));

    // Energy and thermal conduits: state only, rendered entirely by their block models.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> CONDUIT = BLOCK_ENTITY_TYPES.register("conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(false)));

    // Item and fluid conduits: glass, with a block entity renderer drawing their contents.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> TRANSPARENT_CONDUIT = BLOCK_ENTITY_TYPES.register("transparent_conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(true)));

    public static final Supplier<BlockEntityType<FluidTankBlockEntity>> FLUID_TANK = BLOCK_ENTITY_TYPES.register("fluid_tank",
            () -> new BlockEntityType<>(FluidTankBlockEntity::new, tierBlocks(ModBlocks::fluidTank)));

    public static final Supplier<BlockEntityType<ReservoirBlockEntity>> RESERVOIR = BLOCK_ENTITY_TYPES.register("reservoir",
            () -> new BlockEntityType<>(ReservoirBlockEntity::new, ModBlocks.RESERVOIR.get()));
    public static final Supplier<BlockEntityType<XpDrainBlockEntity>> XP_DRAIN = BLOCK_ENTITY_TYPES.register("xp_drain",
            () -> new BlockEntityType<>(XpDrainBlockEntity::new, ModBlocks.XP_DRAIN.get()));
    public static final Supplier<BlockEntityType<XpShowerBlockEntity>> XP_SHOWER = BLOCK_ENTITY_TYPES.register("xp_shower",
            () -> new BlockEntityType<>(XpShowerBlockEntity::new, ModBlocks.XP_SHOWER.get()));
    public static final Supplier<BlockEntityType<QuantumTunnelBlockEntity>> QUANTUM_TUNNEL = BLOCK_ENTITY_TYPES.register("quantum_tunnel",
            () -> new BlockEntityType<>(QuantumTunnelBlockEntity::new, ModBlocks.QUANTUM_TUNNEL.get()));
    public static final Supplier<BlockEntityType<ChunkLoaderBlockEntity>> CHUNK_LOADER = BLOCK_ENTITY_TYPES.register("chunk_loader",
            () -> new BlockEntityType<>(ChunkLoaderBlockEntity::new, ModBlocks.CHUNK_LOADER.get()));

    public static final Supplier<BlockEntityType<PressurizedCylinderBlockEntity>> PRESSURIZED_CYLINDER = BLOCK_ENTITY_TYPES.register("pressurized_cylinder",
            () -> new BlockEntityType<>(PressurizedCylinderBlockEntity::new, tierBlocks(ModBlocks::pressurizedCylinder)));

    public static final Supplier<BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL = BLOCK_ENTITY_TYPES.register("energy_cell",
            () -> new BlockEntityType<>(EnergyCellBlockEntity::new, tierBlocks(ModBlocks::energyCell)));

    public static final Supplier<BlockEntityType<HeatCellBlockEntity>> HEAT_CELL = BLOCK_ENTITY_TYPES.register("heat_cell",
            () -> new BlockEntityType<>(HeatCellBlockEntity::new, tierBlocks(ModBlocks::heatCell)));

    public static final Supplier<BlockEntityType<CrateBlockEntity>> CRATE = BLOCK_ENTITY_TYPES.register("crate",
            () -> new BlockEntityType<>(CrateBlockEntity::new, tierBlocks(ModBlocks::crate)));

    public static final Supplier<BlockEntityType<VaultBlockEntity>> VAULT = BLOCK_ENTITY_TYPES.register("vault",
            () -> new BlockEntityType<>(VaultBlockEntity::new, tierBlocks(ModBlocks::vault)));

    // Every Carbonizer block has one; the structure's master block runs it.
    public static final Supplier<BlockEntityType<CarbonizerBlockEntity>> CARBONIZER = BLOCK_ENTITY_TYPES.register("carbonizer",
            () -> new BlockEntityType<>(CarbonizerBlockEntity::new, ModBlocks.CARBONIZER.get()));

    // On the furnace port only; bricks and walls are plain blocks that find the port.
    public static final Supplier<BlockEntityType<ArcforgeFurnaceBlockEntity>> ARCFORGE_FURNACE = BLOCK_ENTITY_TYPES.register("arcforge_furnace",
            () -> new BlockEntityType<>(ArcforgeFurnaceBlockEntity::new, ModBlocks.ARCFORGE_FURNACE_PORT.get()));

    public static final Supplier<BlockEntityType<DistillationArrayBlockEntity>> DISTILLATION_ARRAY = BLOCK_ENTITY_TYPES.register("distillation_array",
            () -> new BlockEntityType<>(DistillationArrayBlockEntity::new, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get()));

    public static final Supplier<BlockEntityType<SolarThermalArrayBlockEntity>> SOLAR_THERMAL_ARRAY = BLOCK_ENTITY_TYPES.register("solar_thermal_array",
            () -> new BlockEntityType<>(SolarThermalArrayBlockEntity::new, ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get()));

    private ModBlockEntityTypes() {}

    private static Block[] tierBlocks(Function<ConduitTier, DeferredBlock<? extends Block>> byTier) {
        return Arrays.stream(ConduitTier.values()).map(tier -> byTier.apply(tier).get()).toArray(Block[]::new);
    }

    private static Block[] conduitBlocks(boolean transparent) {
        return ModBlocks.allConduits().stream()
                .map(DeferredBlock::get)
                .filter(block -> block.getConduitType().isTransparent() == transparent)
                .toArray(Block[]::new);
    }

    public static void register(IEventBus modEventBus) {
        // Removed in 2.0: a saved Steam Boiler or Steam Turbine loads into the array casing it became.
        BLOCK_ENTITY_TYPES.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler_array"));
        BLOCK_ENTITY_TYPES.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine_array"));
        // The Evaporation Pond's Outlet became the Thermal Evaporator Controller; its old contents are dropped on load.
        BLOCK_ENTITY_TYPES.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "evaporation_pond"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator"));
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
