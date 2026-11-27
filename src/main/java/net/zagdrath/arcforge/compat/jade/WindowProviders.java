/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.nuclear.VesselInteriorBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.Multiblocks;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.impl.lookup.WrappedHierarchyLookup;
import snownee.jade.util.CommonProxy;

// Every block of a formed multiblock shows the same in Jade as its controller. Its casings, glass, bricks, cells and the
// blocks inside a vessel mostly have no capabilities of their own (or only on their port faces), so Jade would show
// nothing, or something else, for them. partOf finds the controller a block belongs to; HeatProvider reads its heat
// there, and these storage providers show its fluids, FE and items by asking Jade's own storage lookup about the
// controller, so a part's bars are exactly the controller's. For the controller itself, and anything that isn't part
// of a formed multiblock, they give nothing and Jade's own providers answer.
public final class WindowProviders {
    private WindowProviders() {}

    // The master of the formed array the pane at pos is part of, or null.
    static @Nullable ShellMultiblockBlockEntity master(BlockGetter level, BlockPos pos) {
        return PressureGlassBlock.findMaster(level, pos);
    }

    // Whether the block can be part of a multiblock (cheap: no structure lookup).
    static boolean isPartBlock(Block block) {
        return block instanceof MultiblockPart || block instanceof PressureGlassBlock || block instanceof VesselInteriorBlock;
    }

    // The controller of the formed multiblock the block at pos is part of, or null (also for the controller itself).
    static @Nullable MultiblockController partOf(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        // What's inside a vessel (fuel and control rod channels, tubes, trays) belongs to the vessel round it.
        MultiblockController controller = block instanceof VesselInteriorBlock inside ? inside.structure().findController(level, pos) : null;
        if (controller == null && (block instanceof MultiblockPart || block instanceof PressureGlassBlock)) {
            controller = Multiblocks.controllerAt(level, pos);
        }
        return controller != null && controller.isFormed() && !controller.getBlockPos().equals(pos) ? controller : null;
    }

    // Where the machine the block at pos belongs to keeps its contents: its controller's position, or null.
    static @Nullable BlockPos machinePos(Level level, BlockPos pos) {
        MultiblockController controller = partOf(level, pos);
        return controller != null ? controller.getBlockPos() : null;
    }

    // The same look, aimed at the controller of the multiblock the looked-at block is part of; null if it isn't one.
    private static @Nullable BlockAccessor atController(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || !isPartBlock(block.getBlock())) {
            return null;
        }
        Level level = block.getLevel();
        BlockPos target = machinePos(level, block.getPosition());
        if (target == null) {
            return null;
        }
        return new BlockAccessorImpl.Builder().from(block)
                .blockState(level.getBlockState(target))
                .blockEntity(() -> level.getBlockEntity(target))
                .hit(new BlockHitResult(Vec3.atCenterOf(target), block.getSide(), target, false))
                .build();
    }

    // What Jade's storage providers send for the controller (none of these answer it: it isn't a part of itself).
    private static <T> @Nullable List<ViewGroup<T>> forward(Accessor<?> accessor, WrappedHierarchyLookup<IServerExtensionProvider<T>> providers) {
        BlockAccessor controller = atController(accessor);
        if (controller == null) {
            return null;
        }
        Map.Entry<Identifier, List<ViewGroup<T>>> data = CommonProxy.getServerExtensionData(controller, providers);
        return data != null ? data.getValue() : null;
    }

    // The client can't always find the controller (it doesn't know every structure), so ask for any part block.
    private static boolean mayBePart(Accessor<?> accessor) {
        return accessor instanceof BlockAccessor block && isPartBlock(block.getBlock());
    }

    public enum Fluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            return forward(accessor, WailaCommonRegistration.instance().fluidStorageProviders);
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return mayBePart(accessor);
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
            return forward(accessor, WailaCommonRegistration.instance().energyStorageProviders);
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return mayBePart(accessor);
        }

        // In the unit the controller's view names (as Jade's own energy view), FE by default.
        @Override
        public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<EnergyView.Data>> groups) {
            String unit = groups.isEmpty() ? "FE" : groups.getFirst().getExtraData().getStringOr("Unit", CommonProxy.defaultEnergyUnit());
            return ClientViewGroup.map(groups, data -> EnergyView.read(data, unit), null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "window_energy");
        }
    }

    public enum Items implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            return forward(accessor, WailaCommonRegistration.instance().itemStorageProviders);
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return mayBePart(accessor);
        }

        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, ItemView::new, null);
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "multiblock_items");
        }
    }
}
