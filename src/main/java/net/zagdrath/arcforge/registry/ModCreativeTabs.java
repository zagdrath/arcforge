/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.List;

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
import net.zagdrath.arcforge.item.tool.ModuleType;

// Arcforge tabs, in order: Machines, Materials, Components, Building Blocks, Fluids, Logistics, Tools & Upgrades, Farming.
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
                output.accept(ModItems.FIREBOX_ARRAY_CONTROLLER.get());
                output.accept(ModItems.FIREBOX_ARRAY_CASING.get());
                output.accept(ModItems.FUEL_BURNER.get());
                output.accept(ModItems.THERMOELECTRIC_PLANT.get());
                output.accept(ModItems.ARC_CRUSHER.get());
                output.accept(ModItems.ARC_CRUSHING_ARRAY_CASING.get());
                output.accept(ModItems.INDUCTION_FURNACE.get());
                output.accept(ModItems.INDUCTION_FURNACE_ARRAY_CASING.get());
                output.accept(ModItems.METAL_PRESS.get());
                output.accept(ModItems.METAL_PRESSING_ARRAY_CASING.get());
                output.accept(ModItems.ELECTRIC_PUMP.get());
                output.accept(ModItems.ARC_MELTER.get());
                output.accept(ModItems.FERMENTER.get());
                output.accept(ModItems.CHEMICAL_REACTOR.get());
                output.accept(ModItems.ELECTROLYZER.get());
                output.accept(ModItems.THERMAL_EVAPORATOR_CONTROLLER.get());
                output.accept(ModItems.THERMAL_EVAPORATOR_CASING.get());
                output.accept(ModItems.ASSEMBLER.get());
                output.accept(ModItems.BLOCK_BREAKER.get());
                output.accept(ModItems.BLOCK_PLACER.get());
                output.accept(ModItems.VACUUM_COLLECTOR.get());
                output.accept(ModItems.ARC_QUARRY.get());
                output.accept(ModItems.SECURITY_TERMINAL.get());
                output.accept(ModItems.STEAM_BOILER_ARRAY_CASING.get());
                output.accept(ModItems.STEAM_TURBINE_ARRAY_CASING.get());
                output.accept(ModItems.GAS_TURBINE_ARRAY_CASING.get());
                output.accept(ModItems.SUPERHEATER_ARRAY_CASING.get());
                output.accept(ModItems.CONDENSER_ARRAY_CASING.get());
                output.accept(ModItems.PRESSURE_GLASS.get());
                output.accept(ModItems.FIBERIZER.get());
                output.accept(ModItems.INFUSER.get());
                output.accept(ModItems.CARBONIZER.get());
                output.accept(ModItems.ARCFORGE_FURNACE_PORT.get());
                output.accept(ModItems.ARCFORGE_FURNACE_BRICKS.get());
                output.accept(ModItems.ARCFORGE_FURNACE_BRICK_WALL.get());
                output.accept(ModItems.DISTILLATION_ARRAY_CONTROLLER.get());
                output.accept(ModItems.DISTILLATION_ARRAY_CASING.get());
                output.accept(ModItems.TRAY_LEVEL_CASING.get());
                output.accept(ModItems.SOLAR_THERMAL_ARRAY_CONTROLLER.get());
                output.accept(ModItems.SOLAR_THERMAL_ARRAY_CASING.get());
                output.accept(ModItems.SOLAR_COLLECTOR.get());
                ModItems.allStorage().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.RESERVOIR.get());
                output.accept(ModItems.XP_DRAIN.get());
                output.accept(ModItems.XP_SHOWER.get());
                output.accept(ModItems.QUANTUM_TUNNEL.get());
                output.accept(ModItems.CHUNK_LOADER.get());
                output.accept(ModItems.ENERGY_METER.get());
                output.accept(ModItems.HEAT_METER.get());
                output.accept(ModItems.FLUID_METER.get());
                output.accept(ModItems.GAS_METER.get());
                output.accept(ModItems.CHARGEPAD.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS = CREATIVE_MODE_TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.materials"))
            .withTabsBefore(MACHINES.getKey())
            .icon(() -> ModItems.STEEL_INGOT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.STEEL_INGOT.get());
                output.accept(ModItems.STEEL_BLOCK.get());
                ModItems.ores().forEach(item -> output.accept(item.get()));
                ModItems.alloys().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.COAL_COKE.get());
                output.accept(ModItems.COAL_COKE_BLOCK.get());
                output.accept(ModItems.SLAG.get());
                output.accept(ModItems.PITCH.get());
                output.accept(ModItems.IRON_DUST.get());
                output.accept(ModItems.COPPER_DUST.get());
                output.accept(ModItems.GOLD_DUST.get());
                output.accept(ModItems.ANCIENT_DEBRIS_DUST.get());
                output.accept(ModItems.CARBON_DUST.get());
                output.accept(ModItems.NETHER_QUARTZ_DUST.get());
                output.accept(ModItems.SULFUR_DUST.get());
                output.accept(ModItems.HALITE_ORE.get());
                output.accept(ModItems.DEEPSLATE_HALITE_ORE.get());
                output.accept(ModItems.ROCK_SALT.get());
                output.accept(ModItems.RAW_ROCK_SALT_BLOCK.get());
                output.accept(ModItems.SALT.get());
                output.accept(ModItems.SALT_BLOCK.get());
                output.accept(ModItems.RAW_RUBBER.get());
                output.accept(ModItems.RUBBER.get());
                output.accept(ModItems.RUBBER_BLOCK.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMPONENTS = CREATIVE_MODE_TABS.register("components", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.components"))
            .withTabsBefore(MATERIALS.getKey())
            .icon(() -> ModItems.ROCK_WOOL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SLAG_WOOL.get());
                output.accept(ModItems.ROCK_WOOL.get());
                output.accept(ModItems.CARBON_FIBER.get());
                output.accept(ModItems.TROUGH_MIRROR.get());
                output.accept(ModItems.RECEIVER_TUBE.get());
                output.accept(ModItems.TURBINE_BLADE_SET.get());
                output.accept(ModItems.COMBUSTOR.get());
                output.accept(ModItems.PLATE_DIE.get());
                output.accept(ModItems.GEAR_DIE.get());
                output.accept(ModItems.ROD_DIE.get());
                output.accept(ModItems.STEEL_PLATE.get());
                output.accept(ModItems.STEEL_GEAR.get());
                output.accept(ModItems.STEEL_ROD.get());
                output.accept(ModItems.COPPER_PLATE.get());
                output.accept(ModItems.COPPER_GEAR.get());
                output.accept(ModItems.COPPER_ROD.get());
                output.accept(ModItems.PLASTIC_SHEET.get());
                output.accept(ModItems.PVC_SHEET.get());
                output.accept(ModItems.RUBBER_GASKET.get());
                output.accept(ModItems.TUNGSTEN_DRILL_HEAD.get());
                output.accept(ModItems.QUARRY_SCANNER.get());
                ModItems.oreComponents().forEach(item -> output.accept(item.get()));
            }).build());

    // Ordered like vanilla's Building Blocks: logs and wood, planks, then the shapes.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_BLOCKS = CREATIVE_MODE_TABS.register("building_blocks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.building_blocks"))
            .withTabsBefore(COMPONENTS.getKey())
            .icon(() -> ModBlocks.TREATED_PLANKS.get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.treatedWood().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.ASPHALT.get());
                output.accept(ModItems.ASPHALT_STAIRS.get());
                output.accept(ModItems.ASPHALT_SLAB.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLUIDS = CREATIVE_MODE_TABS.register("fluids", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.fluids"))
            .withTabsBefore(BUILDING_BLOCKS.getKey())
            .icon(() -> ModItems.CREOSOTE_BUCKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CREOSOTE_BUCKET.get());
                output.accept(ModItems.NAPHTHA_BUCKET.get());
                output.accept(ModItems.ETHANOL_BUCKET.get());
                output.accept(ModItems.LIGHT_OIL_BUCKET.get());
                output.accept(ModItems.HEAVY_OIL_BUCKET.get());
                output.accept(ModItems.LIQUID_EXPERIENCE_BUCKET.get());
                ModItems.chemicalBuckets().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOGISTICS = CREATIVE_MODE_TABS.register("logistics", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.logistics"))
            .withTabsBefore(FLUIDS.getKey())
            .icon(() -> ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.ARCFORGED).get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.allConduits().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.CONDUIT_FILTER.get());
                output.accept(ModItems.CONDUIT_COVER.get());
                ModItems.allCratesAndVaults().forEach(item -> output.accept(item.get()));
                ModItems.storageUpgrades().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_AND_UPGRADES = CREATIVE_MODE_TABS.register("tools_and_upgrades", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.tools_and_upgrades"))
            .withTabsBefore(LOGISTICS.getKey())
            .icon(() -> ModItems.WRENCH.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.ENGINEERS_HANDBOOK.get());
                output.accept(ModItems.WRENCH.get());
                output.accept(ModItems.THROTTLE_LEVER.get());
                output.accept(ModItems.SETTINGS_CARD.get());
                for (var item : List.of(ModItems.STEEL_SWORD, ModItems.STEEL_PICKAXE, ModItems.STEEL_AXE, ModItems.STEEL_SHOVEL, ModItems.STEEL_HOE,
                        ModItems.STEEL_HAMMER, ModItems.STEEL_EXCAVATOR, ModItems.STEEL_HELMET, ModItems.STEEL_CHESTPLATE, ModItems.STEEL_LEGGINGS,
                        ModItems.STEEL_BOOTS, ModItems.FOUNDRY_HELMET, ModItems.FOUNDRY_CHESTPLATE, ModItems.FOUNDRY_LEGGINGS, ModItems.FOUNDRY_BOOTS)) {
                    output.accept(item.get());
                }
                ModItems.jetpacks().forEach(item -> output.accept(item.get()));
                ModItems.arcTools().forEach(item -> output.accept(item.get()));
                for (ModuleType type : ModuleType.values()) {
                    output.accept(ModItems.toolModule(type).get());
                }
                output.accept(ModItems.SPEED_UPGRADE.get());
                output.accept(ModItems.ENERGY_UPGRADE.get());
                output.accept(ModItems.HEAT_UPGRADE.get());
                output.accept(ModItems.INSULATION_UPGRADE.get());
                output.accept(ModItems.THERMOELECTRIC_UPGRADE.get());
                // Portable storage: Batteries, Canisters, Gas Cartridges and Thermal Capsules, empty.
                ModItems.allPortables().forEach(item -> output.accept(item.get()));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FARMING = CREATIVE_MODE_TABS.register("farming", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arcforge.farming"))
            .withTabsBefore(TOOLS_AND_UPGRADES.getKey())
            .icon(() -> ModItems.COMPOST_BIN.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.COMPOST_BIN.get());
                output.accept(ModItems.LOAM.get());
                output.accept(ModItems.IRRIGATED_LOAM_FARMLAND.get());
                output.accept(ModItems.COMPOST.get());
                output.accept(ModItems.WOOD_ASH.get());
                output.accept(ModItems.BASIC_SLAG.get());
                output.accept(ModItems.MIXED_FERTILIZER.get());
                output.accept(ModItems.TRELLIS.get());
                output.accept(ModItems.FLAX_SEEDS.get());
                output.accept(ModItems.FLAX_FIBRE.get());
                output.accept(ModItems.LINEN.get());
                output.accept(ModItems.RAPESEEDS.get());
                output.accept(ModItems.SORGHUM_SEEDS.get());
                output.accept(ModItems.SORGHUM_STALKS.get());
                output.accept(ModItems.HOP_SEEDS.get());
                output.accept(ModItems.HOP_CONES.get());
                output.accept(ModItems.SOYBEANS.get());
                output.accept(ModItems.WILD_FLAX.get());
                output.accept(ModItems.WILD_RAPESEED.get());
                output.accept(ModItems.WILD_SORGHUM.get());
                output.accept(ModItems.WILD_HOPS.get());
                output.accept(ModItems.WILD_SOYBEANS.get());
                output.accept(ModItems.RUBBER_DANDELION_SEEDS.get());
                output.accept(ModItems.RUBBER_DANDELION_ROOTS.get());
                output.accept(ModItems.WILD_RUBBER_DANDELION.get());
                output.accept(ModItems.RESIN_TAP.get());
                output.accept(ModItems.PINE_RESIN.get());
                output.accept(ModItems.LATEX_BUCKET.get());
                output.accept(ModItems.PLANTER.get());
                output.accept(ModItems.HARVESTER.get());
                output.accept(ModItems.FERTILIZER_SPREADER.get());
                output.accept(ModItems.COPPER_SPRINKLER.get());
                output.accept(ModItems.SCARECROW.get());
                output.accept(ModItems.IRON_SICKLE.get());
                output.accept(ModItems.STEEL_SICKLE.get());
                output.accept(ModItems.STEEL_SCYTHE.get());
                output.accept(ModItems.MILLSTONE.get());
                output.accept(ModItems.MILL.get());
                output.accept(ModItems.OIL_PRESS.get());
                output.accept(ModItems.SEED_EXTRACTOR.get());
                output.accept(ModItems.GRAIN_DRYER.get());
                output.accept(ModItems.VULCANIZER.get());
                output.accept(ModItems.FLOUR.get());
                output.accept(ModItems.SEED_MEAL.get());
                output.accept(ModItems.PRESS_CAKE.get());
                output.accept(ModItems.SEED_OIL_BUCKET.get());
                output.accept(ModItems.DRIED_HOPS.get());
                output.accept(ModItems.DRIED_GRAIN.get());
                output.accept(ModItems.DRIED_SORGHUM.get());
                output.accept(ModItems.AIR_SEPARATOR.get());
                output.accept(ModItems.HABER_REACTOR.get());
                output.accept(ModItems.BIOGAS_DIGESTER_CONTROLLER.get());
                output.accept(ModItems.DIGESTER_CASING.get());
                output.accept(ModItems.NPK_FERTILIZER.get());
                output.accept(ModItems.DIGESTATE.get());
                output.accept(ModItems.NUTRIENT_SOLUTION_BUCKET.get());
                output.accept(ModItems.BIODIESEL_BUCKET.get());
                output.accept(ModItems.GLASS_CLOCHE.get());
                output.accept(ModItems.GROW_CHAMBER.get());
                output.accept(ModItems.HYDROPONIC_CELL.get());
                output.accept(ModItems.GREENHOUSE_CONTROLLER.get());
                output.accept(ModItems.GREENHOUSE_FRAME.get());
                output.accept(ModItems.PLANTING_BED.get());
                output.accept(ModItems.GROW_LAMP.get());
            }).build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
