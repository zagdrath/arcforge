/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.machine.config.SideMode;

// Shared multiblock automation: pushing out of output and by-product faces, and refreshing everything
// around a structure when its shape or side configuration changes.
public final class MultiblockAutomation {
    private static final int ITEMS_PER_FACE = 16;
    private static final int FLUID_PER_FACE = FluidType.BUCKET_VOLUME;

    private MultiblockAutomation() {}

    // With auto-eject on, pushes items and fluids out of every outward face configured as output or
    // by-product, into whatever inventory or tank is next to it.
    public static void pushOutputs(ServerLevel level, MultiblockController controller) {
        if (!controller.isAutoEject()) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            if (!controller.isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                SideMode mode = controller.faceMode(pos, side);
                if (mode != SideMode.OUTPUT && mode != SideMode.BYPRODUCT) {
                    continue;
                }
                BlockPos target = pos.relative(side);
                if (!level.isLoaded(target)) {
                    continue;
                }
                ResourceHandler<ItemResource> items = controller.getItemHandler(mode);
                if (items != null) {
                    ResourceHandler<ItemResource> into = level.getCapability(Capabilities.Item.BLOCK, target, side.getOpposite());
                    if (into != null) {
                        ResourceHandlerUtil.move(items, into, resource -> true, ITEMS_PER_FACE, null);
                    }
                }
                ResourceHandler<FluidResource> fluid = controller.getFluidHandler(mode);
                if (fluid != null) {
                    ResourceHandler<FluidResource> into = level.getCapability(Capabilities.Fluid.BLOCK, target, side.getOpposite());
                    if (into != null) {
                        ResourceHandlerUtil.move(fluid, into, resource -> true, FLUID_PER_FACE, null);
                    }
                }
            }
        }
    }

    // Capabilities and conduit connections of every block in the box may have changed.
    public static void refresh(Level level, BlockPos min, BlockPos max) {
        if (level.isClientSide()) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos immutable = pos.immutable();
            level.invalidateCapabilities(immutable);
            ConduitBlock.refreshAround(level, immutable);
        }
    }
}
