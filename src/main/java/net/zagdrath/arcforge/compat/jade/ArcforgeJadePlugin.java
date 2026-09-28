/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SolarBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

// Jade support (only loaded when Jade is installed): formed multiblocks are named as the machine, heat is
// shown for everything that holds it, and conduits show what their network carries. Looking through a
// Pressure Glass window shows the same as looking at its array's casings.
@WailaPlugin
public class ArcforgeJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, BlockEntity.class);
        registration.registerEnergyStorage(ConduitProviders.Energy.INSTANCE, ConduitBlockEntity.class);
        registration.registerFluidStorage(ConduitProviders.Fluid.INSTANCE, ConduitBlockEntity.class);
        registration.registerItemStorage(ConduitProviders.Items.INSTANCE, ConduitBlockEntity.class);
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
        registration.registerBlockComponent(HeatProvider.Client.INSTANCE, Block.class);
        registration.registerBlockComponent(ConduitProviders.Info.INSTANCE, ConduitBlock.class);
        registration.registerEnergyStorageClient(ConduitProviders.Energy.INSTANCE);
        registration.registerFluidStorageClient(ConduitProviders.Fluid.INSTANCE);
        registration.registerItemStorageClient(ConduitProviders.Items.INSTANCE);
        registration.registerFluidStorageClient(WindowProviders.Fluid.INSTANCE);
        registration.registerEnergyStorageClient(WindowProviders.Energy.INSTANCE);
    }
}
