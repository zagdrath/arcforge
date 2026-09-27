/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.JadeForgeUtils;

// Pressure Glass has no block entity or capabilities of its own, so Jade would show nothing for a window.
// These show the fluids and FE of the array it belongs to, read from the master, as a casing does.
// (Heat is in HeatProvider, which follows glass to the master too.)
public final class WindowProviders {
    private WindowProviders() {}

    static boolean isFormedGlass(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block && PressureGlassBlock.isFormed(block.getBlockState());
    }

    // The master of the formed array the pane at pos is part of, or null.
    static @Nullable ShellMultiblockBlockEntity master(BlockGetter level, BlockPos pos) {
        if (!PressureGlassBlock.isFormed(level.getBlockState(pos))) {
            return null;
        }
        ShellMultiblockBlockEntity master = SteamBoilerArrayCasingBlock.STRUCTURE.findMaster(level, pos);
        return master != null ? master : SteamTurbineArrayCasingBlock.STRUCTURE.findMaster(level, pos);
    }

    private static @Nullable ShellMultiblockBlockEntity master(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block ? master(block.getLevel(), block.getPosition()) : null;
    }

    public enum Fluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            ShellMultiblockBlockEntity master = master(accessor);
            ResourceHandler<FluidResource> fluids = master != null && master.getLevel() != null
                    ? master.getLevel().getCapability(Capabilities.Fluid.BLOCK, master.getBlockPos(), null)
                    : null;
            return fluids != null ? JadeForgeUtils.fromFluidHandler(fluids) : null;
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return isFormedGlass(accessor);
        }

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "window_fluid");
        }
    }

    public enum Energy implements IServerExtensionProvider<EnergyView.Data>, IClientExtensionProvider<EnergyView.Data, EnergyView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<EnergyView.Data>> getGroups(Accessor<?> accessor) {
            ShellMultiblockBlockEntity master = master(accessor);
            EnergyHandler energy = master != null && master.getLevel() != null
                    ? master.getLevel().getCapability(Capabilities.Energy.BLOCK, master.getBlockPos(), null)
                    : null;
            return energy != null ? List.of(new ViewGroup<>(List.of(new EnergyView.Data(energy.getAmountAsLong(), energy.getCapacityAsLong())))) : null;
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return isFormedGlass(accessor);
        }

        @Override
        public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<EnergyView.Data>> groups) {
            return ClientViewGroup.map(groups, data -> EnergyView.read(data, "FE"), null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "window_energy");
        }
    }
}
