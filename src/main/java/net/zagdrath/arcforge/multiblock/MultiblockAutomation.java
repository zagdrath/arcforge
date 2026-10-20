/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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

    // With auto-eject on, pushes items and fluids out of every outward face in an output mode (output,
    // by-product, a distillation product), into whatever inventory or tank is next to it.
    public static void pushOutputs(ServerLevel level, MultiblockController controller) {
        if (controller.isAutoEject()) {
            pushOutputs(level, controller, true);
        }
    }

    // Pushes out of every output face whether auto-eject is on or not (machines whose ports always push,
    // like the Superheater and Condenser Arrays). force = false behaves like pushOutputs(level, controller).
    public static void pushOutputs(ServerLevel level, MultiblockController controller, boolean force) {
        pushOutputs(level, controller, force, FLUID_PER_FACE);
    }

    // The same, moving up to fluidPerFace mB through each fluid face (machines that make more than a bucket per face
    // between pushes: the Condenser Array, the Industrial Water Intake).
    public static void pushOutputs(ServerLevel level, MultiblockController controller, boolean force, int fluidPerFace) {
        if (!force && !controller.isAutoEject()) {
            return;
        }
        for (BlockPos pos : portBlocks(level, controller)) {
            if (!controller.isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                SideMode mode = controller.faceMode(pos, side);
                if (mode == null || !mode.isOutput()) {
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
                        ResourceHandlerUtil.move(fluid, into, resource -> true, fluidPerFace, null);
                    }
                }
            }
        }
    }

    // Pushes fluid out of every port in one of these modes (ones that aren't outputs for every machine, such as Gas
    // Output and Hydrogen), up to fluidPerFace mB a face.
    public static void pushFluids(ServerLevel level, MultiblockController controller, java.util.Set<SideMode> modes, int fluidPerFace) {
        for (BlockPos pos : portBlocks(level, controller)) {
            if (!controller.isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                SideMode mode = controller.faceMode(pos, side);
                BlockPos target = pos.relative(side);
                if (mode == null || !modes.contains(mode) || !level.isLoaded(target)) {
                    continue;
                }
                ResourceHandler<FluidResource> fluid = controller.getFluidHandler(mode);
                ResourceHandler<FluidResource> into = fluid != null ? level.getCapability(Capabilities.Fluid.BLOCK, target, side.getOpposite()) : null;
                if (into != null) {
                    ResourceHandlerUtil.move(fluid, into, resource -> true, fluidPerFace, null);
                }
            }
        }
    }

    // The blocks of each structure that have ports, found again every PORT_BLOCKS_TICKS (or when its box moves) and as
    // soon as its ports change (see forget), so the pushes each tick look at those few blocks rather than every block
    // and face of the box (a 7x9x7 vessel's 2,646 faces), and make no garbage doing it.
    private static final Map<MultiblockController, PortBlocks> PORT_BLOCKS = new WeakHashMap<>();
    private static final int PORT_BLOCKS_TICKS = 20;

    private record PortBlocks(BlockPos min, BlockPos max, List<BlockPos> positions, long found) {}

    private static List<BlockPos> portBlocks(ServerLevel level, MultiblockController controller) {
        BlockPos min = controller.getMinCorner(), max = controller.getMaxCorner();
        long now = level.getGameTime();
        PortBlocks known = PORT_BLOCKS.get(controller);
        if (known != null && known.min().equals(min) && known.max().equals(max) && now - known.found() < PORT_BLOCKS_TICKS && now >= known.found()) {
            return known.positions();
        }
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!MultiblockPorts.faces(level, pos).isEmpty() && controller.isPart(pos)) {
                positions.add(pos.immutable());
            }
        }
        PORT_BLOCKS.put(controller, new PortBlocks(min.immutable(), max.immutable(), positions, now));
        return positions;
    }

    // Its ports (or its shape) changed: look again on its next push.
    public static void forget(MultiblockController controller) {
        PORT_BLOCKS.remove(controller);
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
