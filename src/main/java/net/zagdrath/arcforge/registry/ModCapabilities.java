/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.CombustionGeneratorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;

// Exposes machine storage through NeoForge's standard capabilities, so any mod using
// Capabilities.Energy (FE), Capabilities.Fluid or Capabilities.Item can interact with Arcforge machines.
// What each face exposes is decided by the machine's side configuration.
public final class ModCapabilities {
    // Arcforge heat, in HU. Other mods can query or provide it the same way as the NeoForge capabilities.
    public static final BlockCapability<HeatHandler, @Nullable Direction> HEAT =
            BlockCapability.createSided(Identifier.fromNamespaceAndPath(Arcforge.MODID, "heat_handler"), HeatHandler.class);

    private ModCapabilities() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GEOTHERMAL_PLANT.get(),
                GeothermalPlantBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GEOTHERMAL_PLANT.get(),
                GeothermalPlantBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.GEOTHERMAL_PLANT.get(),
                GeothermalPlantBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.COMBUSTION_GENERATOR.get(),
                CombustionGeneratorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.COMBUSTION_GENERATOR.get(),
                CombustionGeneratorBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FLUID_TANK.get(),
                FluidTankBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ENERGY_CELL.get(),
                EnergyCellBlockEntity::getEnergyHandler);

        // Multiblocks: every block of a formed structure exposes the structure face it lies on (served by the controller).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.CARBONIZER.get(),
                CarbonizerBlockEntity::getItemCapability);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.CARBONIZER.get(),
                CarbonizerBlockEntity::getFluidCapability);
        Block[] furnaceParts = {
                ModBlocks.ARCFORGE_FURNACE_PORT.get(), ModBlocks.ARCFORGE_FURNACE_BRICKS.get(), ModBlocks.ARCFORGE_FURNACE_BRICK_WALL.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            MultiblockController furnace = ArcforgeFurnaceStructure.findFormedPort(level, pos);
            return furnace != null ? furnace.itemHandlerAt(pos, side) : null;
        }, furnaceParts);
    }
}
