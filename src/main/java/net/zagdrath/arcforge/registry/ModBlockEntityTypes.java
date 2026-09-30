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
import net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity;
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
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;

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
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
