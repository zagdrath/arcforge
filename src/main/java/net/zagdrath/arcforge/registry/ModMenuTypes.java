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
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.conduit.ConduitFilterMenu;
import net.zagdrath.arcforge.menu.machine.ArcCrusherMenu;
import net.zagdrath.arcforge.menu.machine.InductionFurnaceMenu;
import net.zagdrath.arcforge.menu.machine.MetalPressMenu;
import net.zagdrath.arcforge.menu.machine.ArcMelterMenu;
import net.zagdrath.arcforge.menu.machine.ChemicalReactorMenu;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.SteamBoilerMenu;
import net.zagdrath.arcforge.menu.machine.SteamTurbineMenu;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.menu.machine.FiberizerMenu;
import net.zagdrath.arcforge.menu.machine.FuelBurnerMenu;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.DistillationArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.InductionFurnaceArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.MetalPressingArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.SolarThermalArrayMenu;
import net.zagdrath.arcforge.menu.multiblock.SteamTurbineArrayMenu;
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

    public static final Supplier<MenuType<SteamBoilerMenu>> STEAM_BOILER = MENU_TYPES.register("steam_boiler",
            () -> IMenuTypeExtension.create(SteamBoilerMenu::single));

    public static final Supplier<MenuType<SteamBoilerMenu>> STEAM_BOILER_ARRAY = MENU_TYPES.register("steam_boiler_array",
            () -> IMenuTypeExtension.create(SteamBoilerMenu::array));

    public static final Supplier<MenuType<SteamTurbineMenu>> STEAM_TURBINE = MENU_TYPES.register("steam_turbine",
            () -> IMenuTypeExtension.create(SteamTurbineMenu::new));

    public static final Supplier<MenuType<ElectricPumpMenu>> ELECTRIC_PUMP = MENU_TYPES.register("electric_pump",
            () -> IMenuTypeExtension.create(ElectricPumpMenu::new));

    public static final Supplier<MenuType<ArcMelterMenu>> ARC_MELTER = MENU_TYPES.register("arc_melter",
            () -> IMenuTypeExtension.create(ArcMelterMenu::new));

    public static final Supplier<MenuType<ChemicalReactorMenu>> CHEMICAL_REACTOR = MENU_TYPES.register("chemical_reactor",
            () -> IMenuTypeExtension.create(ChemicalReactorMenu::new));

    public static final Supplier<MenuType<SteamTurbineArrayMenu>> STEAM_TURBINE_ARRAY = MENU_TYPES.register("steam_turbine_array",
            () -> IMenuTypeExtension.create(SteamTurbineArrayMenu::new));

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
