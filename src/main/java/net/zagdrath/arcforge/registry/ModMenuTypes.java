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
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.menu.storage.EnergyCellMenu;
import net.zagdrath.arcforge.menu.storage.FluidTankMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Arcforge.MODID);

    public static final Supplier<MenuType<GeothermalPlantMenu>> GEOTHERMAL_PLANT = MENU_TYPES.register("geothermal_plant",
            () -> IMenuTypeExtension.create(GeothermalPlantMenu::new));

    public static final Supplier<MenuType<FluidTankMenu>> FLUID_TANK = MENU_TYPES.register("fluid_tank",
            () -> IMenuTypeExtension.create(FluidTankMenu::new));

    public static final Supplier<MenuType<EnergyCellMenu>> ENERGY_CELL = MENU_TYPES.register("energy_cell",
            () -> IMenuTypeExtension.create(EnergyCellMenu::new));

    private ModMenuTypes() {}

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
