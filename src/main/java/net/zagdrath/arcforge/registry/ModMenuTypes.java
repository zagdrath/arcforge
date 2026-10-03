/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.menu.logistics.ChunkLoaderMenu;
import net.zagdrath.arcforge.menu.logistics.QuantumTunnelMenu;
import net.zagdrath.arcforge.menu.machine.AirSeparatorMenu;
import net.zagdrath.arcforge.menu.machine.HaberReactorMenu;
import net.zagdrath.arcforge.menu.multiblock.BiogasDigesterMenu;
import net.zagdrath.arcforge.menu.multiblock.GasTurbineArrayMenu;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.conduit.ConduitFilterMenu;
import net.zagdrath.arcforge.menu.machine.ArcCrusherMenu;
import net.zagdrath.arcforge.menu.machine.GrainDryerMenu;
import net.zagdrath.arcforge.menu.machine.VulcanizerMenu;
import net.zagdrath.arcforge.menu.machine.MillMenu;
import net.zagdrath.arcforge.menu.machine.OilPressMenu;
import net.zagdrath.arcforge.menu.machine.SeedExtractorMenu;
import net.zagdrath.arcforge.menu.machine.FermenterMenu;
import net.zagdrath.arcforge.menu.machine.InductionFurnaceMenu;
import net.zagdrath.arcforge.menu.machine.MetalPressMenu;
import net.zagdrath.arcforge.menu.machine.ArcMelterMenu;
import net.zagdrath.arcforge.menu.machine.ChemicalReactorMenu;
import net.zagdrath.arcforge.menu.machine.ElectrolyzerMenu;
import net.zagdrath.arcforge.menu.machine.SecurityTerminalMenu;
import net.zagdrath.arcforge.menu.logistics.MeterMenu;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.menu.machine.ArcQuarryConfigMenu;
import net.zagdrath.arcforge.menu.machine.ArcQuarryMenu;
import net.zagdrath.arcforge.menu.machine.BlockPlacerMenu;
import net.zagdrath.arcforge.menu.machine.BlockBreakerMenu;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.SteamBoilerMenu;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.menu.machine.FiberizerMenu;
import net.zagdrath.arcforge.menu.machine.FuelBurnerMenu;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.DistillationArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.GreenhouseMenu;
import net.zagdrath.arcforge.menu.multiblock.InductionFurnaceArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.MetalPressingArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.SolarThermalArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.SteamTurbineArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.CondenserArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.SuperheaterArrayMenu;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.menu.machine.InfuserMenu;
import net.zagdrath.arcforge.menu.machine.ThermoelectricPlantMenu;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;
import net.zagdrath.arcforge.menu.multiblock.CarbonizerMenu;
import net.zagdrath.arcforge.menu.storage.CrateMenu;
import net.zagdrath.arcforge.menu.storage.EnergyCellMenu;
import net.zagdrath.arcforge.menu.storage.FluidTankMenu;
import net.zagdrath.arcforge.menu.storage.PressurizedCylinderMenu;
import net.zagdrath.arcforge.menu.storage.HeatCellMenu;
import net.zagdrath.arcforge.menu.storage.VaultMenu;
import net.zagdrath.arcforge.menu.tool.ArcToolMenu;
import net.zagdrath.arcforge.menu.machine.ClocheMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Arcforge.MODID);

    public static final Supplier<MenuType<GeothermalPlantMenu>> GEOTHERMAL_PLANT = MENU_TYPES.register("geothermal_plant",
            () -> IMenuTypeExtension.create(GeothermalPlantMenu::new));

    public static final Supplier<MenuType<BurnerMenu>> COMBUSTION_PLANT = MENU_TYPES.register("combustion_plant",
            () -> IMenuTypeExtension.create(BurnerMenu::combustionPlant));

    public static final Supplier<MenuType<BurnerMenu>> FIREBOX = MENU_TYPES.register("firebox",
            () -> IMenuTypeExtension.create(BurnerMenu::firebox));

    public static final Supplier<MenuType<ThermoelectricPlantMenu>> THERMOELECTRIC_PLANT = MENU_TYPES.register("thermoelectric_plant",
            () -> IMenuTypeExtension.create(ThermoelectricPlantMenu::new));

    public static final Supplier<MenuType<ArcCrusherMenu>> ARC_CRUSHER = MENU_TYPES.register("arc_crusher",
            () -> IMenuTypeExtension.create(ArcCrusherMenu::new));

    public static final Supplier<MenuType<ArcCrushingArrayMenu>> ARC_CRUSHING_ARRAY = MENU_TYPES.register("arc_crushing_array",
            () -> IMenuTypeExtension.create(ArcCrushingArrayMenu::new));

    public static final Supplier<MenuType<InductionFurnaceMenu>> INDUCTION_FURNACE = MENU_TYPES.register("induction_furnace",
            () -> IMenuTypeExtension.create(InductionFurnaceMenu::new));

    public static final Supplier<MenuType<InductionFurnaceArrayMenu>> INDUCTION_FURNACE_ARRAY = MENU_TYPES.register("induction_furnace_array",
            () -> IMenuTypeExtension.create(InductionFurnaceArrayMenu::new));

    public static final Supplier<MenuType<MetalPressMenu>> METAL_PRESS = MENU_TYPES.register("metal_press",
            () -> IMenuTypeExtension.create(MetalPressMenu::new));

    public static final Supplier<MenuType<SteamBoilerMenu>> STEAM_BOILER_ARRAY = MENU_TYPES.register("steam_boiler_array",
            () -> IMenuTypeExtension.create(SteamBoilerMenu::array));

    public static final Supplier<MenuType<ElectricPumpMenu>> ELECTRIC_PUMP = MENU_TYPES.register("electric_pump",
            () -> IMenuTypeExtension.create(ElectricPumpMenu::new));

    public static final Supplier<MenuType<SecurityTerminalMenu>> SECURITY_TERMINAL = MENU_TYPES.register("security_terminal",
            () -> IMenuTypeExtension.create(SecurityTerminalMenu::new));

    public static final Supplier<MenuType<MeterMenu>> METER = MENU_TYPES.register("meter", () -> IMenuTypeExtension.create(MeterMenu::new));
    public static final Supplier<MenuType<QuantumTunnelMenu>> QUANTUM_TUNNEL = MENU_TYPES.register("quantum_tunnel",
            () -> IMenuTypeExtension.create(QuantumTunnelMenu::new));
    public static final Supplier<MenuType<ChunkLoaderMenu>> CHUNK_LOADER = MENU_TYPES.register("chunk_loader",
            () -> IMenuTypeExtension.create(ChunkLoaderMenu::new));

    public static final Supplier<MenuType<ArcMelterMenu>> ARC_MELTER = MENU_TYPES.register("arc_melter",
            () -> IMenuTypeExtension.create(ArcMelterMenu::new));

    public static final Supplier<MenuType<FermenterMenu>> FERMENTER = MENU_TYPES.register("fermenter",
            () -> IMenuTypeExtension.create(FermenterMenu::new));

    // Farm processing.
    public static final Supplier<MenuType<MillMenu>> MILL = MENU_TYPES.register("mill",
            () -> IMenuTypeExtension.create(MillMenu::new));
    public static final Supplier<MenuType<OilPressMenu>> OIL_PRESS = MENU_TYPES.register("oil_press",
            () -> IMenuTypeExtension.create(OilPressMenu::new));
    public static final Supplier<MenuType<SeedExtractorMenu>> SEED_EXTRACTOR = MENU_TYPES.register("seed_extractor",
            () -> IMenuTypeExtension.create(SeedExtractorMenu::new));
    public static final Supplier<MenuType<GrainDryerMenu>> GRAIN_DRYER = MENU_TYPES.register("grain_dryer",
            () -> IMenuTypeExtension.create(GrainDryerMenu::new));
    public static final Supplier<MenuType<VulcanizerMenu>> VULCANIZER = MENU_TYPES.register("vulcanizer",
            () -> IMenuTypeExtension.create(VulcanizerMenu::new));

    // Farm chemistry.
    public static final Supplier<MenuType<AirSeparatorMenu>> AIR_SEPARATOR = MENU_TYPES.register("air_separator",
            () -> IMenuTypeExtension.create(AirSeparatorMenu::new));
    public static final Supplier<MenuType<HaberReactorMenu>> HABER_REACTOR = MENU_TYPES.register("haber_reactor",
            () -> IMenuTypeExtension.create(HaberReactorMenu::new));
    public static final Supplier<MenuType<BiogasDigesterMenu>> BIOGAS_DIGESTER = MENU_TYPES.register("biogas_digester",
            () -> IMenuTypeExtension.create(BiogasDigesterMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.multiblock.FireboxArrayMenu>> FIREBOX_ARRAY =
            MENU_TYPES.register("firebox_array", () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.multiblock.FireboxArrayMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.multiblock.BatteryArrayMenu>> BATTERY_ARRAY =
            MENU_TYPES.register("battery_array", () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.multiblock.BatteryArrayMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.multiblock.ThermalEvaporatorMenu>> THERMAL_EVAPORATOR =
            MENU_TYPES.register("thermal_evaporator",
                    () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.multiblock.ThermalEvaporatorMenu::new));

    // Automated farms: one menu class, one type each.
    public static final Supplier<MenuType<ClocheMenu>> GLASS_CLOCHE = MENU_TYPES.register("glass_cloche",
            () -> IMenuTypeExtension.create(ClocheMenu::glassCloche));
    public static final Supplier<MenuType<ClocheMenu>> GROW_CHAMBER = MENU_TYPES.register("grow_chamber",
            () -> IMenuTypeExtension.create(ClocheMenu::growChamber));
    public static final Supplier<MenuType<ClocheMenu>> HYDROPONIC_CELL = MENU_TYPES.register("hydroponic_cell",
            () -> IMenuTypeExtension.create(ClocheMenu::hydroponicCell));
    public static final Supplier<MenuType<GreenhouseMenu>> GREENHOUSE = MENU_TYPES.register("greenhouse",
            () -> IMenuTypeExtension.create(GreenhouseMenu::new));

    public static final Supplier<MenuType<ChemicalReactorMenu>> CHEMICAL_REACTOR = MENU_TYPES.register("chemical_reactor",
            () -> IMenuTypeExtension.create(ChemicalReactorMenu::new));
    public static final Supplier<MenuType<ElectrolyzerMenu>> ELECTROLYZER = MENU_TYPES.register("electrolyzer",
            () -> IMenuTypeExtension.create(ElectrolyzerMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.machine.CarbonReclaimerMenu>> CARBON_RECLAIMER =
            MENU_TYPES.register("carbon_reclaimer", () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.machine.CarbonReclaimerMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.machine.GasifierMenu>> GASIFIER =
            MENU_TYPES.register("gasifier", () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.machine.GasifierMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.machine.FischerTropschReactorMenu>> FISCHER_TROPSCH_REACTOR =
            MENU_TYPES.register("fischer_tropsch_reactor",
                    () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.machine.FischerTropschReactorMenu::new));
    public static final Supplier<MenuType<AssemblerMenu>> ASSEMBLER = MENU_TYPES.register("assembler",
            () -> IMenuTypeExtension.create(AssemblerMenu::new));
    public static final Supplier<MenuType<BlockBreakerMenu>> BLOCK_BREAKER = MENU_TYPES.register("block_breaker",
            () -> IMenuTypeExtension.create(BlockBreakerMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.machine.TreeCutterMenu>> TREE_CUTTER = MENU_TYPES.register("tree_cutter",
            () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.machine.TreeCutterMenu::new));
    public static final Supplier<MenuType<net.zagdrath.arcforge.menu.machine.HydrothermalCarbonizerMenu>> HYDROTHERMAL_CARBONIZER =
            MENU_TYPES.register("hydrothermal_carbonizer",
                    () -> IMenuTypeExtension.create(net.zagdrath.arcforge.menu.machine.HydrothermalCarbonizerMenu::new));
    public static final Supplier<MenuType<BlockPlacerMenu>> BLOCK_PLACER = MENU_TYPES.register("block_placer",
            () -> IMenuTypeExtension.create(BlockPlacerMenu::new));
    public static final Supplier<MenuType<VacuumCollectorMenu>> VACUUM_COLLECTOR = MENU_TYPES.register("vacuum_collector",
            () -> IMenuTypeExtension.create(VacuumCollectorMenu::new));
    public static final Supplier<MenuType<ArcQuarryMenu>> ARC_QUARRY = MENU_TYPES.register("arc_quarry",
            () -> IMenuTypeExtension.create(ArcQuarryMenu::new));
    public static final Supplier<MenuType<ArcQuarryConfigMenu>> ARC_QUARRY_CONFIG = MENU_TYPES.register("arc_quarry_config",
            () -> IMenuTypeExtension.create(ArcQuarryConfigMenu::new));

    public static final Supplier<MenuType<SteamTurbineArrayMenu>> STEAM_TURBINE_ARRAY = MENU_TYPES.register("steam_turbine_array",
            () -> IMenuTypeExtension.create(SteamTurbineArrayMenu::new));
    public static final Supplier<MenuType<GasTurbineArrayMenu>> GAS_TURBINE_ARRAY = MENU_TYPES.register("gas_turbine_array",
            () -> IMenuTypeExtension.create(GasTurbineArrayMenu::new));

    public static final Supplier<MenuType<SuperheaterArrayMenu>> SUPERHEATER_ARRAY = MENU_TYPES.register("superheater_array",
            () -> IMenuTypeExtension.create(SuperheaterArrayMenu::new));

    public static final Supplier<MenuType<CondenserArrayMenu>> CONDENSER_ARRAY = MENU_TYPES.register("condenser_array",
            () -> IMenuTypeExtension.create(CondenserArrayMenu::new));

    public static final Supplier<MenuType<MetalPressingArrayMenu>> METAL_PRESSING_ARRAY = MENU_TYPES.register("metal_pressing_array",
            () -> IMenuTypeExtension.create(MetalPressingArrayMenu::new));

    public static final Supplier<MenuType<FiberizerMenu>> FIBERIZER = MENU_TYPES.register("fiberizer",
            () -> IMenuTypeExtension.create(FiberizerMenu::new));

    public static final Supplier<MenuType<FuelBurnerMenu>> FUEL_BURNER = MENU_TYPES.register("fuel_burner",
            () -> IMenuTypeExtension.create(FuelBurnerMenu::new));

    public static final Supplier<MenuType<InfuserMenu>> INFUSER = MENU_TYPES.register("infuser",
            () -> IMenuTypeExtension.create(InfuserMenu::new));

    public static final Supplier<MenuType<FluidTankMenu>> FLUID_TANK = MENU_TYPES.register("fluid_tank",
            () -> IMenuTypeExtension.create(FluidTankMenu::new));

    public static final Supplier<MenuType<PressurizedCylinderMenu>> PRESSURIZED_CYLINDER = MENU_TYPES.register("pressurized_cylinder",
            () -> IMenuTypeExtension.create(PressurizedCylinderMenu::new));

    public static final Supplier<MenuType<EnergyCellMenu>> ENERGY_CELL = MENU_TYPES.register("energy_cell",
            () -> IMenuTypeExtension.create(EnergyCellMenu::new));

    public static final Supplier<MenuType<HeatCellMenu>> HEAT_CELL = MENU_TYPES.register("heat_cell",
            () -> IMenuTypeExtension.create(HeatCellMenu::new));

    public static final Supplier<MenuType<CrateMenu>> CRATE = MENU_TYPES.register("crate",
            () -> IMenuTypeExtension.create(CrateMenu::new));

    public static final Supplier<MenuType<VaultMenu>> VAULT = MENU_TYPES.register("vault",
            () -> IMenuTypeExtension.create(VaultMenu::new));

    public static final Supplier<MenuType<ConduitFilterMenu>> CONDUIT_FILTER = MENU_TYPES.register("conduit_filter",
            () -> IMenuTypeExtension.create(ConduitFilterMenu::new));

    public static final Supplier<MenuType<ArcToolMenu>> ARC_TOOL = MENU_TYPES.register("arc_tool",
            () -> IMenuTypeExtension.create(ArcToolMenu::new));

    public static final Supplier<MenuType<CarbonizerMenu>> CARBONIZER = MENU_TYPES.register("carbonizer",
            () -> IMenuTypeExtension.create(CarbonizerMenu::new));

    public static final Supplier<MenuType<ArcforgeFurnaceMenu>> ARCFORGE_FURNACE = MENU_TYPES.register("arcforge_furnace",
            () -> IMenuTypeExtension.create(ArcforgeFurnaceMenu::new));

    public static final Supplier<MenuType<DistillationArrayMenu>> DISTILLATION_ARRAY = MENU_TYPES.register("distillation_array",
            () -> IMenuTypeExtension.create(DistillationArrayMenu::new));

    public static final Supplier<MenuType<SolarThermalArrayMenu>> SOLAR_THERMAL_ARRAY = MENU_TYPES.register("solar_thermal_array",
            () -> IMenuTypeExtension.create(SolarThermalArrayMenu::new));

    private ModMenuTypes() {}

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
