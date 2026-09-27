/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;

// Arcforge tabs, in order: Machines, Logistics, Tools & Upgrades.
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Arcforge.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES = CREATIVE_MODE_TABS.register("machines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.machines"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.GEOTHERMAL_PLANT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.GEOTHERMAL_PLANT.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOGISTICS = CREATIVE_MODE_TABS.register("logistics", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.logistics"))
            .withTabsBefore(MACHINES.getKey())
            .icon(() -> ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.ARCFORGED).get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.allConduits().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_AND_UPGRADES = CREATIVE_MODE_TABS.register("tools_and_upgrades", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.tools_and_upgrades"))
            .withTabsBefore(LOGISTICS.getKey())
            .icon(() -> ModItems.WRENCH.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.WRENCH.get());
            }).build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
