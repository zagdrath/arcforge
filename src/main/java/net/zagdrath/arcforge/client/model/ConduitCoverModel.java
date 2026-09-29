/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
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

// A covered conduit: its own model (and filter sleeves and sheath, which the wrappers before this add), inside a
// full-block cover. With no look copied onto it the cover is the clear pane arcforge:block/conduit/cover
// (translucent); with one it is that block's own model, in that block's own render type. An opaque look hides the
// conduit; the pane shows it. Tint indices of a copied look (grass, leaves) are not passed through.
public final class ConduitCoverModel extends DelegateBlockStateModel {
    private static final StandaloneModelKey<BlockStateModelPart> PANE = new StandaloneModelKey<>(() -> Arcforge.MODID + ":conduit_cover");

    private final BlockStateModelPart pane;

    private ConduitCoverModel(BlockStateModel delegate, BlockStateModelPart pane) {
        super(delegate);
        this.pane = pane;
    }

    public static void registerStandalone(ModelEvent.RegisterStandalone event) {
        event.register(PANE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/conduit/cover"),
                BlockModelRotation.IDENTITY));
    }

    // Wraps every conduit's block state models (after the filter and sheath wrappers).
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var result = event.getBakingResult();
        BlockStateModelPart pane = result.standaloneModels().get(PANE);
        if (pane == null) {
            return;
        }
        result.blockStateModels().replaceAll((state, model) -> state.getBlock() instanceof ConduitBlock ? new ConduitCoverModel(model, pane) : model);
    }

    private static ConduitBlockEntity.Cover cover(BlockAndTintGetter level, BlockPos pos) {
        return level.getModelData(pos).get(ConduitBlockEntity.COVER);
    }

    private static BlockStateModel lookModel(BlockState look) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(look);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        super.collectParts(level, pos, state, random, parts);
        ConduitBlockEntity.Cover cover = cover(level, pos);
        if (cover == null) {
            return;
        }
        if (cover.look().isPresent()) {
            BlockState look = cover.look().get();
            lookModel(look).collectParts(level, pos, look, random, parts);
        } else {
            parts.add(pane);
        }
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int flags = super.materialFlags(level, pos, state);
        ConduitBlockEntity.Cover cover = cover(level, pos);
        if (cover == null) {
            return flags;
        }
        return cover.look().map(look -> flags | lookModel(look).materialFlags(level, pos, look)).orElse(flags | pane.materialFlags());
    }
}
