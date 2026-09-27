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

// Arcforge tabs, in order: Machines, Materials, Components, Building Blocks, Fluids, Logistics, Tools & Upgrades.
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Arcforge.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES = CREATIVE_MODE_TABS.register("machines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.machines"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.GEOTHERMAL_PLANT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.GEOTHERMAL_PLANT.get());
                output.accept(ModItems.COMBUSTION_PLANT.get());
                output.accept(ModItems.FIREBOX.get());
                output.accept(ModItems.FUEL_BURNER.get());
                output.accept(ModItems.THERMOELECTRIC_PLANT.get());
                output.accept(ModItems.ARC_CRUSHER.get());
                output.accept(ModItems.ARC_CRUSHING_ARRAY_CASING.get());
                output.accept(ModItems.INDUCTION_FURNACE.get());
                output.accept(ModItems.INDUCTION_FURNACE_ARRAY_CASING.get());
                output.accept(ModItems.METAL_PRESS.get());
                output.accept(ModItems.METAL_PRESSING_ARRAY_CASING.get());
                output.accept(ModItems.FIBERIZER.get());
                output.accept(ModItems.INFUSER.get());
                output.accept(ModItems.CARBONIZER.get());
                output.accept(ModItems.ARCFORGE_FURNACE_PORT.get());
                output.accept(ModItems.ARCFORGE_FURNACE_BRICKS.get());
                output.accept(ModItems.ARCFORGE_FURNACE_BRICK_WALL.get());
                ModItems.allStorage().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS = CREATIVE_MODE_TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.materials"))
            .withTabsBefore(MACHINES.getKey())
            .icon(() -> ModItems.STEEL_INGOT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.STEEL_INGOT.get());
                output.accept(ModItems.STEEL_BLOCK.get());
                output.accept(ModItems.COAL_COKE.get());
                output.accept(ModItems.COAL_COKE_BLOCK.get());
                output.accept(ModItems.SLAG.get());
                output.accept(ModItems.IRON_DUST.get());
                output.accept(ModItems.COPPER_DUST.get());
                output.accept(ModItems.GOLD_DUST.get());
                output.accept(ModItems.ANCIENT_DEBRIS_DUST.get());
                output.accept(ModItems.CARBON_DUST.get());
                output.accept(ModItems.NETHER_QUARTZ_DUST.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMPONENTS = CREATIVE_MODE_TABS.register("components", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.components"))
            .withTabsBefore(MATERIALS.getKey())
            .icon(() -> ModItems.ROCK_WOOL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SLAG_WOOL.get());
                output.accept(ModItems.ROCK_WOOL.get());
                output.accept(ModItems.PLATE_DIE.get());
                output.accept(ModItems.GEAR_DIE.get());
                output.accept(ModItems.ROD_DIE.get());
                output.accept(ModItems.STEEL_PLATE.get());
                output.accept(ModItems.STEEL_GEAR.get());
                output.accept(ModItems.STEEL_ROD.get());
            }).build());

    // Ordered like vanilla's Building Blocks: logs and wood, planks, then the shapes.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_BLOCKS = CREATIVE_MODE_TABS.register("building_blocks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.building_blocks"))
            .withTabsBefore(COMPONENTS.getKey())
            .icon(() -> ModBlocks.TREATED_PLANKS.get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.treatedWood().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLUIDS = CREATIVE_MODE_TABS.register("fluids", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.fluids"))
            .withTabsBefore(BUILDING_BLOCKS.getKey())
            .icon(() -> ModItems.CREOSOTE_BUCKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CREOSOTE_BUCKET.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOGISTICS = CREATIVE_MODE_TABS.register("logistics", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.logistics"))
            .withTabsBefore(FLUIDS.getKey())
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
                output.accept(ModItems.SPEED_UPGRADE.get());
                output.accept(ModItems.ENERGY_UPGRADE.get());
                output.accept(ModItems.HEAT_UPGRADE.get());
                output.accept(ModItems.INSULATION_UPGRADE.get());
            }).build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
