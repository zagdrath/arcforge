/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.List;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.ArcMelterBlock;
import net.zagdrath.arcforge.block.machine.ChemicalReactorBlock;
import net.zagdrath.arcforge.block.machine.ElectrolyzerBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.block.machine.ArcQuarryBoundingBlock;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.block.machine.VacuumCollectorBlock;
import net.zagdrath.arcforge.block.machine.BlockPlacerBlock;
import net.zagdrath.arcforge.block.machine.BlockBreakerBlock;
import net.zagdrath.arcforge.block.machine.AssemblerBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SolarBlock;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.block.multiblock.CondenserArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SuperheaterArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

// Jade support (only loaded when Jade is installed): formed multiblocks are named as the machine, heat is
// shown for everything that holds it, conduits show what their network carries, and an Arc Melter or
// Chemical Reactor what it is making. Loam Farmland shows its nutrients and a Compost Bin how full it is. Looking
// through a Pressure Glass window shows the same as looking at its array's casings.
@WailaPlugin
public class ArcforgeJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, BlockEntity.class);
        // Owner and security of any owned block, or any part of an owned structure (glass included).
        registration.registerBlockDataProvider(SecurityProvider.INSTANCE, Block.class);
        registration.registerEnergyStorage(ConduitProviders.Energy.INSTANCE, ConduitBlockEntity.class);
        registration.registerFluidStorage(ConduitProviders.Fluid.INSTANCE, ConduitBlockEntity.class);
        registration.registerItemStorage(ConduitProviders.Items.INSTANCE, ConduitBlockEntity.class);
        registration.registerItemStorage(VaultProviders.HideItems.INSTANCE, VaultBlockEntity.class);
        registration.registerBlockDataProvider(ArcMelterProvider.INSTANCE, ArcMelterBlockEntity.class);
        registration.registerBlockDataProvider(ChemicalReactorProvider.INSTANCE, ChemicalReactorBlockEntity.class);
        registration.registerBlockDataProvider(ElectrolyzerProvider.INSTANCE, ElectrolyzerBlockEntity.class);
        registration.registerBlockDataProvider(AutomationProvider.INSTANCE, AssemblerBlockEntity.class);
        registration.registerBlockDataProvider(AutomationProvider.INSTANCE, BlockBreakerBlockEntity.class);
        registration.registerBlockDataProvider(AutomationProvider.INSTANCE, BlockPlacerBlockEntity.class);
        registration.registerBlockDataProvider(AutomationProvider.INSTANCE, VacuumCollectorBlockEntity.class);
        registration.registerBlockDataProvider(ArcQuarryProvider.INSTANCE, ArcQuarryBlockEntity.class);
        registration.registerBlockDataProvider(ArcQuarryProvider.INSTANCE, ArcQuarryBoundingBlockEntity.class);
        registration.registerBlockDataProvider(ArcforgeFurnaceProvider.INSTANCE, ArcforgeFurnaceBlockEntity.class);
        // Any casing: the cube arrays' casings each have a block entity; a turbine's glass doesn't.
        registration.registerBlockDataProvider(SteamCycleProvider.INSTANCE, SuperheaterArrayBlockEntity.class);
        registration.registerBlockDataProvider(SteamCycleProvider.INSTANCE, CondenserArrayBlockEntity.class);
        registration.registerBlockDataProvider(SteamCycleProvider.INSTANCE, SteamTurbineArrayBlockEntity.class);
        registration.registerBlockDataProvider(GasTurbineProvider.INSTANCE, GasTurbineArrayBlockEntity.class);
        registration.registerBlockDataProvider(MeterProviders.Meter.INSTANCE, net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity.class);
        registration.registerBlockDataProvider(MeterProviders.Chargepad.INSTANCE, net.zagdrath.arcforge.blockentity.logistics.ChargepadBlockEntity.class);
        registration.registerBlockDataProvider(FarmingProviders.RusticMachine.INSTANCE, net.zagdrath.arcforge.blockentity.farming.FarmMachineBlockEntity.class);
        registration.registerBlockDataProvider(FarmingProviders.RusticMachine.INSTANCE, net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity.class);
        registration.registerBlockDataProvider(FarmingProviders.RusticMachine.INSTANCE, net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity.class);
        for (Class<? extends net.minecraft.world.level.block.entity.BlockEntity> type : List.of(
                net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity.class,
                net.zagdrath.arcforge.blockentity.machine.MillBlockEntity.class,
                net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity.class,
                net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity.class,
                net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity.class,
                net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity.class)) {
            registration.registerBlockDataProvider(FarmProcessingProvider.INSTANCE, type);
        }
        // Farm chemistry: the Air Separator, the Haber Reactor, and the Biogas Digester's controller and casings.
        registration.registerBlockDataProvider(FarmChemistryProvider.INSTANCE, net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity.class);
        registration.registerBlockDataProvider(FarmChemistryProvider.INSTANCE, net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity.class);
        registration.registerBlockDataProvider(FarmChemistryProvider.INSTANCE, net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity.class);
        registration.registerBlockDataProvider(FarmChemistryProvider.INSTANCE, net.zagdrath.arcforge.block.multiblock.DigesterCasingBlock.class);
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, net.zagdrath.arcforge.block.multiblock.DigesterCasingBlock.class);
        registration.registerBlockDataProvider(GasTurbineProvider.INSTANCE, PressureGlassBlock.class);
        registration.registerBlockDataProvider(SteamCycleProvider.INSTANCE, PressureGlassBlock.class);
        // Pressure Glass has no block entity: these read the array it belongs to.
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, PressureGlassBlock.class);
        registration.registerFluidStorage(WindowProviders.Fluid.INSTANCE, PressureGlassBlock.class);
        registration.registerEnergyStorage(WindowProviders.Energy.INSTANCE, PressureGlassBlock.class);
        // Nor do the Distillation Array's casings and trays: these read its controller.
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, DistillationArrayCasingBlock.class);
        // The Solar Thermal Array's casings and collectors show its heat.
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, SolarBlock.class);
        registration.registerFluidStorage(WindowProviders.Fluid.INSTANCE, DistillationArrayCasingBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MultiblockNameProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(SecurityProvider.Client.INSTANCE, Block.class);
        registration.registerBlockComponent(HeatProvider.Client.INSTANCE, Block.class);
        registration.registerBlockComponent(ConduitProviders.Info.INSTANCE, ConduitBlock.class);
        registration.registerBlockComponent(ConduitProviders.Filters.INSTANCE, ConduitBlock.class);
        registration.registerBlockComponent(VaultProviders.Info.INSTANCE, VaultBlock.class);
        registration.registerBlockComponent(ArcMelterProvider.Client.INSTANCE, ArcMelterBlock.class);
        registration.registerBlockComponent(ChemicalReactorProvider.Client.INSTANCE, ChemicalReactorBlock.class);
        registration.registerBlockComponent(ElectrolyzerProvider.Client.INSTANCE, ElectrolyzerBlock.class);
        registration.registerBlockComponent(AutomationProvider.Client.INSTANCE, AssemblerBlock.class);
        registration.registerBlockComponent(AutomationProvider.Client.INSTANCE, BlockBreakerBlock.class);
        registration.registerBlockComponent(AutomationProvider.Client.INSTANCE, BlockPlacerBlock.class);
        registration.registerBlockComponent(AutomationProvider.Client.INSTANCE, VacuumCollectorBlock.class);
        registration.registerBlockComponent(ArcQuarryProvider.Client.INSTANCE, ArcQuarryBlock.class);
        registration.registerBlockComponent(ArcQuarryProvider.Client.INSTANCE, ArcQuarryBoundingBlock.class);
        registration.registerBlockComponent(ArcforgeFurnaceProvider.Client.INSTANCE, ArcforgeFurnacePortBlock.class);
        registration.registerBlockComponent(SteamCycleProvider.Client.INSTANCE, SuperheaterArrayCasingBlock.class);
        registration.registerBlockComponent(SteamCycleProvider.Client.INSTANCE, CondenserArrayCasingBlock.class);
        registration.registerBlockComponent(SteamCycleProvider.Client.INSTANCE, SteamTurbineArrayCasingBlock.class);
        registration.registerBlockComponent(GasTurbineProvider.Client.INSTANCE, GasTurbineArrayCasingBlock.class);
        registration.registerBlockComponent(MeterProviders.Meter.Client.INSTANCE, net.zagdrath.arcforge.block.logistics.MeterBlock.class);
        registration.registerBlockComponent(MeterProviders.Chargepad.Client.INSTANCE, net.zagdrath.arcforge.block.logistics.ChargepadBlock.class);
        registration.registerBlockComponent(GasTurbineProvider.Client.INSTANCE, PressureGlassBlock.class);
        registration.registerBlockComponent(SteamCycleProvider.Client.INSTANCE, PressureGlassBlock.class);
        // Farming: read from the block state on the client.
        registration.registerBlockComponent(FarmingProviders.LoamFarmland.INSTANCE, net.zagdrath.arcforge.block.farming.LoamFarmlandBlock.class);
        registration.registerBlockComponent(FarmingProviders.CompostBin.INSTANCE, net.zagdrath.arcforge.block.farming.CompostBinBlock.class);
        registration.registerBlockComponent(FarmingProviders.Trellis.INSTANCE, net.zagdrath.arcforge.block.farming.TrellisBlock.class);
        registration.registerBlockComponent(FarmingProviders.Scarecrow.INSTANCE, net.zagdrath.arcforge.block.farming.ScarecrowBlock.class);
        registration.registerBlockComponent(FarmingProviders.RusticMachine.Client.INSTANCE, net.zagdrath.arcforge.block.farming.FarmMachineBlock.class);
        registration.registerBlockComponent(FarmingProviders.RusticMachine.Client.INSTANCE, net.zagdrath.arcforge.block.farming.FertilizerSpreaderBlock.class);
        registration.registerBlockComponent(FarmingProviders.RusticMachine.Client.INSTANCE, net.zagdrath.arcforge.block.farming.CopperSprinklerBlock.class);
        for (Class<? extends Block> type : List.of(
                net.zagdrath.arcforge.block.farming.MillstoneBlock.class,
                net.zagdrath.arcforge.block.machine.MillBlock.class,
                net.zagdrath.arcforge.block.machine.OilPressBlock.class,
                net.zagdrath.arcforge.block.machine.SeedExtractorBlock.class,
                net.zagdrath.arcforge.block.machine.GrainDryerBlock.class,
                net.zagdrath.arcforge.block.machine.FermenterBlock.class)) {
            registration.registerBlockComponent(FarmProcessingProvider.Client.INSTANCE, type);
        }
        for (Class<? extends Block> type : List.of(
                net.zagdrath.arcforge.block.machine.AirSeparatorBlock.class,
                net.zagdrath.arcforge.block.machine.HaberReactorBlock.class,
                net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock.class,
                net.zagdrath.arcforge.block.multiblock.DigesterCasingBlock.class)) {
            registration.registerBlockComponent(FarmChemistryProvider.Client.INSTANCE, type);
        }
        registration.registerEnergyStorageClient(ConduitProviders.Energy.INSTANCE);
        registration.registerFluidStorageClient(ConduitProviders.Fluid.INSTANCE);
        registration.registerItemStorageClient(ConduitProviders.Items.INSTANCE);
        registration.registerItemStorageClient(VaultProviders.HideItems.INSTANCE);
        registration.registerFluidStorageClient(WindowProviders.Fluid.INSTANCE);
        registration.registerEnergyStorageClient(WindowProviders.Energy.INSTANCE);
    }
}
