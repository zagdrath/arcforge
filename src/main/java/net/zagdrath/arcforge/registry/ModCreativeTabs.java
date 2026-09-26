package net.zagdrath.arcforge.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Arcforge.MODID);

    public static final Supplier<CreativeModeTab> MACHINES = CREATIVE_MODE_TABS.register("machines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.machines"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.GEOTHERMAL_PLANT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.GEOTHERMAL_PLANT.get());
            }).build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
