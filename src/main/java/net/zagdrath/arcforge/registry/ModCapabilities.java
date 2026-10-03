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
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.zagdrath.arcforge.blockentity.experience.XpShowerBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.InductionFurnaceArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.CondenserArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SuperheaterArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.MetalPressingArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.item.storage.PortableFluidHandler;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.JetpackFluidHandler;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.SolarThermalStructure;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;

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
        // Flue Gas faces give out Carbon Dioxide.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.COMBUSTION_PLANT.get(),
                CombustionPlantBlockEntity::getFluidHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getItemHandler);
        // Oxygen faces take oxygen for oxy-fuel; Flue Gas faces give out Carbon Dioxide.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FIREBOX.get(),
                FireboxBlockEntity::getFluidHandler);
        // Meters: in on their left, out on their right; the Chargepad takes FE through its back.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.METER.get(),
                net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.METER.get(),
                net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.METER.get(),
                net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.CHARGEPAD.get(),
                net.zagdrath.arcforge.blockentity.logistics.ChargepadBlockEntity::getEnergyHandler);
        // The Compost Bin: plant matter in, Compost out (hoppers and conduits).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.COMPOST_BIN.get(),
                net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity::getItemHandler);
        // Rustic farming machines: seeds and fertilizer in, the harvest out, water in (hoppers, pipes and conduits).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.PLANTER.get(),
                net.zagdrath.arcforge.blockentity.farming.PlanterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.HARVESTER.get(),
                net.zagdrath.arcforge.blockentity.farming.HarvesterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FERTILIZER_SPREADER.get(),
                net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.COPPER_SPRINKLER.get(),
                net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity::getFluidHandler);
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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.METAL_PRESS.get(),
                MetalPressBlockEntity::getEnergyHandler);
        // Every casing of a formed steam array exposes the face of the box it lies on (served by the master).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof SteamBoilerArrayBlockEntity boiler ? boiler.itemHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof SteamBoilerArrayBlockEntity boiler ? boiler.fluidHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof SteamBoilerArrayBlockEntity boiler ? boiler.heatHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof SteamTurbineArrayBlockEntity turbine ? turbine.fluidHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof SteamTurbineArrayBlockEntity turbine ? turbine.energyHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof GasTurbineArrayBlockEntity turbine ? turbine.fluidHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof GasTurbineArrayBlockEntity turbine ? turbine.energyHandlerAt(casing.getBlockPos(), side) : null);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), (casing, side) ->
                casing.getMaster() instanceof GasTurbineArrayBlockEntity turbine ? turbine.heatHandlerAt(casing.getBlockPos(), side) : null);
        // Every casing of a formed Superheater or Condenser Array exposes the cube face it lies on (served by the centre).
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.SUPERHEATER_ARRAY.get(), (casing, side) -> {
            SuperheaterArrayBlockEntity array = SuperheaterArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.fluidHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.SUPERHEATER_ARRAY.get(), (casing, side) -> {
            SuperheaterArrayBlockEntity array = SuperheaterArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.heatHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.CONDENSER_ARRAY.get(), (casing, side) -> {
            CondenserArrayBlockEntity array = CondenserArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.fluidHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ELECTRIC_PUMP.get(),
                ElectricPumpBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.ELECTRIC_PUMP.get(),
                ElectricPumpBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ELECTRIC_PUMP.get(),
                ElectricPumpBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ARC_MELTER.get(),
                ArcMelterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.ARC_MELTER.get(),
                ArcMelterBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ARC_MELTER.get(),
                ArcMelterBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FERMENTER.get(),
                FermenterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FERMENTER.get(),
                FermenterBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.FERMENTER.get(),
                FermenterBlockEntity::getEnergyHandler);
        // Farm processing: the Millstone's items; the Mill, Oil Press and Seed Extractor's items and FE (and the press's
        // oil); the Grain Dryer's items and heat.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.MILLSTONE.get(),
                net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.MILL.get(),
                net.zagdrath.arcforge.blockentity.machine.MillBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.MILL.get(),
                net.zagdrath.arcforge.blockentity.machine.MillBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.OIL_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.OIL_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.OIL_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.SEED_EXTRACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.SEED_EXTRACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GRAIN_DRYER.get(),
                net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.GRAIN_DRYER.get(),
                net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GRAIN_DRYER.get(),
                net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.VULCANIZER.get(),
                net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.VULCANIZER.get(),
                net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity::getHeatHandler);
        // The Resin Tap: its Latex and Pine Resin come out of any face.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.RESIN_TAP.get(),
                net.zagdrath.arcforge.blockentity.farming.ResinTapBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.RESIN_TAP.get(),
                net.zagdrath.arcforge.blockentity.farming.ResinTapBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.CHEMICAL_REACTOR.get(),
                ChemicalReactorBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.CHEMICAL_REACTOR.get(),
                ChemicalReactorBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.CHEMICAL_REACTOR.get(),
                ChemicalReactorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.ELECTROLYZER.get(),
                ElectrolyzerBlockEntity::getFluidHandler);
        // The Sifter's items and FE; the Diamond Press's items, FE and heat.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.SIFTER.get(),
                net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.SIFTER.get(),
                net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.DIAMOND_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.DIAMOND_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.DIAMOND_PRESS.get(),
                net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity::getHeatHandler);
        // Carbon capture: the Carbon Reclaimer's gases, Water, Carbon Dust and FE; the Gasifier's fuel, steam, Syngas, ash and
        // heat; the Fischer-Tropsch Reactor's Syngas, products, catalyst, FE and heat.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.CARBON_RECLAIMER.get(),
                net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.CARBON_RECLAIMER.get(),
                net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.CARBON_RECLAIMER.get(),
                net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GASIFIER.get(),
                net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GASIFIER.get(),
                net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity::getFluidHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.GASIFIER.get(),
                net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity::getHeatHandler);
        // Farm chemistry: the Air Separator's gases and FE; the Haber Reactor's gases, FE and heat.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.AIR_SEPARATOR.get(),
                net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.AIR_SEPARATOR.get(),
                net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.HABER_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.HABER_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.HABER_REACTOR.get(),
                net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity::getHeatHandler);
        // Automated farms: items, their water or Nutrient Solution (and Carbon Dioxide), and FE for the powered two.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GLASS_CLOCHE.get(), ClocheBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GLASS_CLOCHE.get(), ClocheBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GROW_CHAMBER.get(), ClocheBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.GROW_CHAMBER.get(), ClocheBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.GROW_CHAMBER.get(), ClocheBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.HYDROPONIC_CELL.get(), ClocheBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.HYDROPONIC_CELL.get(), ClocheBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.HYDROPONIC_CELL.get(), ClocheBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ELECTROLYZER.get(),
                ElectrolyzerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ASSEMBLER.get(),
                AssemblerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ASSEMBLER.get(),
                AssemblerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.BLOCK_BREAKER.get(),
                BlockBreakerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.BLOCK_BREAKER.get(),
                BlockBreakerBlockEntity::getEnergyHandler);
        // The Tree Cutter: saplings and bone meal in, the trees out, FE in.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.TREE_CUTTER.get(),
                net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.TREE_CUTTER.get(),
                net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity::getEnergyHandler);
        // The Hydrothermal Carbonizer: biomass in, Bio-Coal out, water in and back out, heat in.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(),
                net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(),
                net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity::getFluidHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(),
                net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.BLOCK_PLACER.get(),
                BlockPlacerBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.BLOCK_PLACER.get(),
                BlockPlacerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.VACUUM_COLLECTOR.get(),
                VacuumCollectorBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.VACUUM_COLLECTOR.get(),
                VacuumCollectorBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.VACUUM_COLLECTOR.get(),
                VacuumCollectorBlockEntity::getFluidHandler);
        // The Arc Quarry acts as one 3x3x3 block: only the outer faces of its parts expose it (the main block is the
        // centre, so its own faces are all inside).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ARC_QUARRY.get(),
                (quarry, side) -> quarry.itemHandlerAt(quarry.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ARC_QUARRY.get(),
                (quarry, side) -> quarry.energyHandlerAt(quarry.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ARC_QUARRY_BOUNDING.get(),
                ArcQuarryBoundingBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ARC_QUARRY_BOUNDING.get(),
                ArcQuarryBoundingBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.METAL_PRESS.get(),
                MetalPressBlockEntity::getItemHandler);
        // Every casing of a formed Metal Pressing Array exposes the cube face it lies on (served by the centre).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.METAL_PRESSING_ARRAY.get(), (casing, side) -> {
            MetalPressingArrayBlockEntity array = MetalPressingArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.itemHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.METAL_PRESSING_ARRAY.get(), (casing, side) -> {
            MetalPressingArrayBlockEntity array = MetalPressingArrayCasingBlock.STRUCTURE.findController(casing.getLevel(), casing.getBlockPos());
            return array != null ? array.energyHandlerAt(casing.getBlockPos(), side) : null;
        });
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.FIBERIZER.get(),
                FiberizerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FIBERIZER.get(),
                FiberizerBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FIBERIZER.get(),
                FiberizerBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FUEL_BURNER.get(),
                FuelBurnerBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FUEL_BURNER.get(),
                FuelBurnerBlockEntity::getItemHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.FUEL_BURNER.get(),
                FuelBurnerBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.INFUSER.get(),
                InfuserBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.INFUSER.get(),
                InfuserBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.INFUSER.get(),
                InfuserBlockEntity::getFluidHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(),
                ThermoelectricPlantBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.FLUID_TANK.get(),
                FluidTankBlockEntity::getFluidHandler);
        // A Reservoir: the whole tank it's part of, from every face.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.RESERVOIR.get(),
                (reservoir, side) -> reservoir.getFluidHandler());
        // The XP Shower fills from any face.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.XP_SHOWER.get(),
                XpShowerBlockEntity::getFluidHandler);
        // The Quantum Tunnel: each face offers what it's set to take or give.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.QUANTUM_TUNNEL.get(), QuantumTunnelBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.QUANTUM_TUNNEL.get(), QuantumTunnelBlockEntity::getHeatHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.QUANTUM_TUNNEL.get(), QuantumTunnelBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.QUANTUM_TUNNEL.get(), QuantumTunnelBlockEntity::getItemHandler);
        // The Chunk Loader takes FE only when chunkLoader.energyPerChunk is above 0.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.CHUNK_LOADER.get(), ChunkLoaderBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntityTypes.PRESSURIZED_CYLINDER.get(),
                PressurizedCylinderBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.ENERGY_CELL.get(),
                EnergyCellBlockEntity::getEnergyHandler);
        event.registerBlockEntity(HEAT, ModBlockEntityTypes.HEAT_CELL.get(),
                HeatCellBlockEntity::getHeatHandler);

        // The storage blocks' item slots: input faces take items into the drain slot, output faces give filled items from the fill slot.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.FLUID_TANK.get(), StorageBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.PRESSURIZED_CYLINDER.get(), StorageBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.ENERGY_CELL.get(), StorageBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.HEAT_CELL.get(), StorageBlockEntity::getItemHandler);
        // Crates and Vaults: their contents, through the faces set to input or output. (Neither is a vanilla
        // Container, so hoppers use this too, and a Vault isn't mistaken for one 64-item slot.)
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.CRATE.get(), StorageBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.VAULT.get(), StorageBlockEntity::getItemHandler);

        // Portable storage: Batteries hold FE, Canisters liquids and Gas Cartridges gases. (Thermal Capsules hold
        // heat, which has no item capability; the Heat Cell reads them through PortableStorageItem.)
        for (DeferredItem<PortableStorageItem> holder : ModItems.allPortables()) {
            PortableStorageItem item = holder.get();
            switch (item.kind()) {
                case BATTERY -> event.registerItem(Capabilities.Energy.ITEM,
                        (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(), item.capacity(), item.rate()), item);
                case CANISTER, GAS_CARTRIDGE -> event.registerItem(Capabilities.Fluid.ITEM,
                        (stack, access) -> new PortableFluidHandler(access, item), item);
                case THERMAL_CAPSULE -> {
                }
            }
        }

        // Jetpacks hold a jetpack fuel gas; Arc Drills and Saws charge like a battery but can't be drained.
        for (DeferredItem<JetpackItem> holder : ModItems.jetpacks()) {
            JetpackItem item = holder.get();
            event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new JetpackFluidHandler(access, item), item);
        }
        for (DeferredItem<ArcToolItem> holder : ModItems.arcTools()) {
            ArcToolItem item = holder.get();
            event.registerItem(Capabilities.Energy.ITEM,
                    (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(), item.capacity(), item.receiveRate(), 0), item);
        }

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
        // Oxygen ports.
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            MultiblockController furnace = ArcforgeFurnaceStructure.findFormedPort(level, pos);
            return furnace != null ? furnace.fluidHandlerAt(pos, side) : null;
        }, furnaceParts);
        Block[] solarParts = {
                ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get(), ModBlocks.SOLAR_THERMAL_ARRAY_CASING.get(), ModBlocks.SOLAR_COLLECTOR.get() };
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            SolarThermalArrayBlockEntity solar = SolarThermalStructure.findController(level, pos);
            return solar != null ? solar.heatHandlerAt(pos, side) : null;
        }, solarParts);
        // The Biogas Digester: plant matter and water in, Biogas and Digestate out, and heat in, through its ports.
        Block[] digesterParts = { ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get(), ModBlocks.DIGESTER_CASING.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            var digester = net.zagdrath.arcforge.multiblock.BiogasDigesterStructure.findController(level, pos);
            return digester != null ? digester.itemHandlerAt(pos, side) : null;
        }, digesterParts);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            var digester = net.zagdrath.arcforge.multiblock.BiogasDigesterStructure.findController(level, pos);
            return digester != null ? digester.fluidHandlerAt(pos, side) : null;
        }, digesterParts);
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            var digester = net.zagdrath.arcforge.multiblock.BiogasDigesterStructure.findController(level, pos);
            return digester != null ? digester.heatHandlerAt(pos, side) : null;
        }, digesterParts);
        // The Firebox Array: fuel items, liquids and gases in, oxygen in, heat out, through its ports.
        Block[] fireboxArrayParts = { ModBlocks.FIREBOX_ARRAY_CONTROLLER.get(), ModBlocks.FIREBOX_ARRAY_CASING.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            var array = net.zagdrath.arcforge.multiblock.FireboxArrayStructure.findController(level, pos);
            return array != null ? array.itemHandlerAt(pos, side) : null;
        }, fireboxArrayParts);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            var array = net.zagdrath.arcforge.multiblock.FireboxArrayStructure.findController(level, pos);
            return array != null ? array.fluidHandlerAt(pos, side) : null;
        }, fireboxArrayParts);
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            var array = net.zagdrath.arcforge.multiblock.FireboxArrayStructure.findController(level, pos);
            return array != null ? array.heatHandlerAt(pos, side) : null;
        }, fireboxArrayParts);
        // The Battery Array: FE in through Energy Input ports and out through Energy Output ports.
        Block[] batteryArrayParts = { ModBlocks.BATTERY_ARRAY_CONTROLLER.get(), ModBlocks.BATTERY_ARRAY_CASING.get() };
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            var array = net.zagdrath.arcforge.multiblock.BatteryArrayStructure.findController(level, pos);
            return array != null ? array.energyHandlerAt(pos, side) : null;
        }, batteryArrayParts);
        // The Thermal Evaporator Array: Seawater or Brine in, heat in, Brine, Salt and Water out, through its ports.
        Block[] evaporatorParts = { ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get(), ModBlocks.THERMAL_EVAPORATOR_CASING.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            var evaporator = net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure.findController(level, pos);
            return evaporator != null ? evaporator.itemHandlerAt(pos, side) : null;
        }, evaporatorParts);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            var evaporator = net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure.findController(level, pos);
            return evaporator != null ? evaporator.fluidHandlerAt(pos, side) : null;
        }, evaporatorParts);
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            var evaporator = net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure.findController(level, pos);
            return evaporator != null ? evaporator.heatHandlerAt(pos, side) : null;
        }, evaporatorParts);
        // The Greenhouse Array: fertilizer and fluids in, the harvest out, FE and heat in, through the ports on its frames.
        Block[] greenhouseParts = { ModBlocks.GREENHOUSE_CONTROLLER.get(), ModBlocks.GREENHOUSE_FRAME.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            GreenhouseBlockEntity greenhouse = GreenhouseStructure.findController(level, pos);
            return greenhouse != null ? greenhouse.itemHandlerAt(pos, side) : null;
        }, greenhouseParts);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            GreenhouseBlockEntity greenhouse = GreenhouseStructure.findController(level, pos);
            return greenhouse != null ? greenhouse.fluidHandlerAt(pos, side) : null;
        }, greenhouseParts);
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            GreenhouseBlockEntity greenhouse = GreenhouseStructure.findController(level, pos);
            return greenhouse != null ? greenhouse.energyHandlerAt(pos, side) : null;
        }, greenhouseParts);
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            GreenhouseBlockEntity greenhouse = GreenhouseStructure.findController(level, pos);
            return greenhouse != null ? greenhouse.heatHandlerAt(pos, side) : null;
        }, greenhouseParts);
        Block[] columnParts = {
                ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get(), ModBlocks.DISTILLATION_ARRAY_CASING.get(), ModBlocks.TRAY_LEVEL_CASING.get() };
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            DistillationArrayBlockEntity column = DistillationStructure.findController(level, pos);
            return column != null ? column.itemHandlerAt(pos, side) : null;
        }, columnParts);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            DistillationArrayBlockEntity column = DistillationStructure.findController(level, pos);
            return column != null ? column.fluidHandlerAt(pos, side) : null;
        }, columnParts);
        event.registerBlock(HEAT, (level, pos, state, blockEntity, side) -> {
            DistillationArrayBlockEntity column = DistillationStructure.findController(level, pos);
            return column != null ? column.heatHandlerAt(pos, side) : null;
        }, columnParts);
    }
}
