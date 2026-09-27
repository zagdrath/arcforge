/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;

// An ordinary block model (the fields of a plain blockstate variant: model, x, y, uvlock) with the port
// plate on its outer faces when the block is one of its structure's ports (see MultiblockPorts): the faces
// not against another multiblock block or the hollow inside a structure. Used by the Carbonizer and the
// Arcforge Furnace bricks: {"type": "arcforge:ported", "model": ..., "y": 90}.
public final class PortedModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("arcforge", "ported");

    private PortedModel() {}

    public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            variant.resolveDependencies(resolver);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new Baked(variant.bake(baker), new PortOverlays(baker, variant.modelLocation()));
        }
    }

    private static final class Baked implements DynamicBlockStateModel {
        private final BlockStateModelPart base;
        private final PortOverlays ports;

        Baked(BlockStateModelPart base, PortOverlays ports) {
            this.base = base;
            this.ports = ports;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            parts.add(base);
            SideMode mode = MultiblockPorts.get(state);
            if (mode == SideMode.NONE || !isFormed(state)) {
                return;
            }
            QuadCollection.Builder quads = new QuadCollection.Builder();
            for (Direction face : Direction.values()) {
                BakedQuad port = ports.get(mode, face);
                if (port != null && isOuter(level, pos.relative(face))) {
                    quads.addCulledFace(face, port);
                }
            }
            parts.add(new SimpleModelWrapper(quads.build(), true, base.particleMaterial()));
        }

        // Blocks with no formed state of their own (the furnace bricks) always show their port.
        private static boolean isFormed(BlockState state) {
            return !(state.getBlock() instanceof CarbonizerBlock) || CarbonizerBlock.isFormed(state);
        }

        // Outside the structure: not another multiblock block, nor air walled in by them on both
        // horizontal axes (the furnace's stack).
        static boolean isOuter(BlockAndTintGetter level, BlockPos across) {
            BlockState state = level.getBlockState(across);
            if (state.getBlock() instanceof MultiblockPart) {
                return false;
            }
            if (!state.isAir()) {
                return true;
            }
            return !(isPart(level, across.north()) && isPart(level, across.south()) && isPart(level, across.east()) && isPart(level, across.west()));
        }

        private static boolean isPart(BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockState(pos).getBlock() instanceof MultiblockPart;
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return base.particleMaterial();
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return base.materialFlags() | ports.materialFlags();
        }
    }
}
