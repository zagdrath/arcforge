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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SolarBlock;
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
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
// These show the fluids and FE of the array it belongs to, read from the master, as a casing does. The
// Distillation Array's casings and trays have no block entity either: the fluid view reads its controller.
// (Heat is in HeatProvider, which follows glass and casings to the machine too.)
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

    // Where the machine the block-entity-less part at pos belongs to keeps its contents: an array's master
    // for a pane of glass, the controller for a column casing. Null for anything else.
    static @Nullable BlockPos machinePos(BlockGetter level, BlockPos pos) {
        ShellMultiblockBlockEntity master = master(level, pos);
        if (master != null) {
            return master.getBlockPos();
        }
        if (level.getBlockState(pos).getBlock() instanceof DistillationArrayCasingBlock) {
            DistillationArrayBlockEntity column = DistillationStructure.findController(level, pos);
            return column != null ? column.getBlockPos() : null;
        }
        // A Solar Thermal Array casing or collector: its controller.
        if (level instanceof Level full && level.getBlockState(pos).getBlock() instanceof SolarBlock part) {
            MultiblockController controller = part.findController(full, pos);
            return controller != null ? controller.getBlockPos() : null;
        }
        return null;
    }

    private static boolean isFormedPart(Accessor<?> accessor) {
        return isFormedGlass(accessor) || accessor instanceof BlockAccessor block && block.getBlock() instanceof DistillationArrayCasingBlock
                && DistillationStructure.isFormedPart(block.getBlockState());
    }

    public enum Fluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor block)) {
                return null;
            }
            BlockPos pos = machinePos(block.getLevel(), block.getPosition());
            ResourceHandler<FluidResource> fluids = pos != null ? block.getLevel().getCapability(Capabilities.Fluid.BLOCK, pos, null) : null;
            return fluids != null ? JadeForgeUtils.fromFluidHandler(fluids) : null;
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return isFormedPart(accessor);
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
