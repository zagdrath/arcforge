/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitFacades;

// A conduit with Conduit Facades: its own model (and filter sleeves, which the wrapper before this adds), plus the
// facade panels from the block entity's model data (FacadeQuads). A panel over a connected side has a hole for the arm:
// 6x6 round a pipe or an input's nozzle, 8x8 round an output's flange, which shows through it like a port.
public final class ConduitFacadeModel extends DelegateBlockStateModel {
    private static final StandaloneModelKey<BlockStateModelPart> BLANK = new StandaloneModelKey<>(() -> Arcforge.MODID + ":conduit_facade");

    private final FacadeQuads facades;

    private ConduitFacadeModel(BlockStateModel delegate, FacadeQuads facades) {
        super(delegate);
        this.facades = facades;
    }

    public static void registerStandalone(ModelEvent.RegisterStandalone event) {
        event.register(BLANK, SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/conduit/facade"),
                BlockModelRotation.IDENTITY));
    }

    // Wraps every conduit's block state models (after the filter wrapper).
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var result = event.getBakingResult();
        BlockStateModelPart blank = result.standaloneModels().get(BLANK);
        if (blank == null) {
            return;
        }
        FacadeQuads facades = new FacadeQuads(blank);
        result.blockStateModels().replaceAll((state, model) -> state.getBlock() instanceof ConduitBlock ? new ConduitFacadeModel(model, facades) : model);
    }

    private static @Nullable ConduitFacades facades(BlockAndTintGetter level, BlockPos pos) {
        ConduitFacades facades = level.getModelData(pos).get(ConduitBlockEntity.FACADES);
        return facades == null || facades.isEmpty() ? null : facades;
    }

    // Each side's hole for the conduit's arm (0 where it doesn't connect).
    private static int[] holes(BlockState state) {
        int[] holes = new int[6];
        for (Direction side : Direction.values()) {
            holes[side.ordinal()] = switch (ConduitBlock.mode(state, side)) {
                case NONE -> 0;
                case OUTPUT -> 8;
                case PIPE, INPUT -> 6;
            };
        }
        return holes;
    }

    private @Nullable BlockStateModelPart panels(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        ConduitFacades facades = facades(level, pos);
        return facades == null ? null : this.facades.build(level, pos, facades, holes(state), delegate.particleMaterial(level, pos, state));
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        super.collectParts(level, pos, state, random, parts);
        BlockStateModelPart panels = panels(level, pos, state);
        if (panels != null) {
            parts.add(panels);
        }
    }

    // The panels change with the block entity, not the block state: never share a cached mesh between conduits.
    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return facades(level, pos) == null ? super.createGeometryKey(level, pos, state, random) : null;
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int flags = super.materialFlags(level, pos, state);
        BlockStateModelPart panels = panels(level, pos, state);
        return panels != null ? flags | panels.materialFlags() : flags;
    }
}
