package net.zagdrath.arcforge.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Arcforge.MODID);

    public static final Supplier<MenuType<GeothermalPlantMenu>> GEOTHERMAL_PLANT = MENU_TYPES.register("geothermal_plant",
            () -> IMenuTypeExtension.create(GeothermalPlantMenu::new));

    private ModMenuTypes() {}

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
