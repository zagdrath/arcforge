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
import net.zagdrath.arcforge.menu.machine.ArcCrusherMenu;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.menu.machine.ThermoelectricPlantMenu;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;
import net.zagdrath.arcforge.menu.multiblock.CarbonizerMenu;
import net.zagdrath.arcforge.menu.storage.EnergyCellMenu;
import net.zagdrath.arcforge.menu.storage.FluidTankMenu;
import net.zagdrath.arcforge.menu.storage.HeatCellMenu;

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

    public static final Supplier<MenuType<FluidTankMenu>> FLUID_TANK = MENU_TYPES.register("fluid_tank",
            () -> IMenuTypeExtension.create(FluidTankMenu::new));

    public static final Supplier<MenuType<EnergyCellMenu>> ENERGY_CELL = MENU_TYPES.register("energy_cell",
            () -> IMenuTypeExtension.create(EnergyCellMenu::new));

    public static final Supplier<MenuType<HeatCellMenu>> HEAT_CELL = MENU_TYPES.register("heat_cell",
            () -> IMenuTypeExtension.create(HeatCellMenu::new));

    public static final Supplier<MenuType<CarbonizerMenu>> CARBONIZER = MENU_TYPES.register("carbonizer",
            () -> IMenuTypeExtension.create(CarbonizerMenu::new));

    public static final Supplier<MenuType<ArcforgeFurnaceMenu>> ARCFORGE_FURNACE = MENU_TYPES.register("arcforge_furnace",
            () -> IMenuTypeExtension.create(ArcforgeFurnaceMenu::new));

    private ModMenuTypes() {}

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
