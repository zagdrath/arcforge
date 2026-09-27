/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

// Jade support (only loaded when Jade is installed): formed multiblocks are named as the machine, heat is
// shown for everything that holds it, and conduits show what their network carries.
@WailaPlugin
public class ArcforgeJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HeatProvider.INSTANCE, BlockEntity.class);
        registration.registerEnergyStorage(ConduitProviders.Energy.INSTANCE, ConduitBlockEntity.class);
        registration.registerFluidStorage(ConduitProviders.Fluid.INSTANCE, ConduitBlockEntity.class);
        registration.registerItemStorage(ConduitProviders.Items.INSTANCE, ConduitBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MultiblockNameProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(HeatProvider.Client.INSTANCE, Block.class);
        registration.registerBlockComponent(ConduitProviders.Info.INSTANCE, ConduitBlock.class);
        registration.registerEnergyStorageClient(ConduitProviders.Energy.INSTANCE);
        registration.registerFluidStorageClient(ConduitProviders.Fluid.INSTANCE);
        registration.registerItemStorageClient(ConduitProviders.Items.INSTANCE);
    }
}
