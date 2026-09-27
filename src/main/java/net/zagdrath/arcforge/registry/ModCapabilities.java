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
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.InductionFurnaceArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.COMBUSTION_PLANT.get(),
                CombustionPlantBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.COMBUSTION_PLANT.get(),
                CombustionPlantBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ARC_CRUSHER.get(),
                ArcCrusherBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ARC_CRUSHER.get(),
                ArcCrusherBlockEntity::getItemHandler);
        // Every casing of a formed Arc Crushing Array exposes the cube face it lies on (served by the centre).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(), (casing, side) -> {
            ArcCrushingArrayBlockEntity array = ArcCrushingArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.itemHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(), (casing, side) -> {
            ArcCrushingArrayBlockEntity array = ArcCrushingArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.energyHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.INDUCTION_FURNACE.get(),
                InductionFurnaceBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.INDUCTION_FURNACE.get(),
                InductionFurnaceBlockEntity::getItemHandler);
        // Every casing of a formed Induction Furnace Array exposes the cube face it lies on (served by the centre).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.INDUCTION_FURNACE_ARRAY.get(), (casing, side) -> {
            InductionFurnaceArrayBlockEntity array = InductionFurnaceArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.itemHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.INDUCTION_FURNACE_ARRAY.get(), (casing, side) -> {
            InductionFurnaceArrayBlockEntity array = InductionFurnaceArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.energyHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FLUID_TANK.get(),
                FluidTankBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ENERGY_CELL.get(),
                EnergyCellBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.HEAT_CELL.get(),
                HeatCellBlockEntity::getHeatHandler);

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
