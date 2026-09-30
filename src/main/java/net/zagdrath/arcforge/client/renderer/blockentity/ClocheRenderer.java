/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;

// Draws what grows inside the Glass Cloche, Grow Chamber and Hydroponic Cell, seen through their glass: the soil's
// block, squashed into the planter bed, and the plant's own block model (its crop stage for the growth), shrunk into
// the space under the glass. Plants without stages to step (saplings, flowers, cane) grow in size instead. Both are
// ordinary block models, drawn with their own tints and render types. Sizes are in pixels.
public class ClocheRenderer implements BlockEntityRenderer<ClocheBlockEntity, ClocheRenderState> {
    private static final float PX = 1.0F / 16.0F;
    // Plants that grow in size start this big.
    private static final float MIN_SCALE = 0.3F;

    // The bed the soil fills and the space the plant grows in, per farm (see the models).
    private record Interior(float soilInset, float soilBottom, float soilTop, float cropBase, float cropWidth, float cropHeight) {}

    private static final Interior GLASS_CLOCHE = new Interior(2.0F, 1.5F, 3.0F, 3.0F, 9.0F, 10.5F);
    private static final Interior GROW_CHAMBER = new Interior(2.5F, 2.0F, 3.5F, 3.5F, 9.0F, 8.5F);
    private static final Interior HYDROPONIC_CELL = new Interior(0.0F, 0.0F, 0.0F, 4.0F, 9.0F, 8.0F);

    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockModelResolver;

    public ClocheRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    @Override
    public ClocheRenderState createRenderState() {
        return new ClocheRenderState();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void extractRenderState(ClocheBlockEntity farm, ClocheRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(farm, state, partialTicks, cameraPosition, breakProgress);
        state.kind = farm.kind();
        state.light = farm.getLevel() != null ? LightCoordsUtil.getLightCoords(farm.getLevel(), farm.getBlockPos()) : LightCoordsUtil.FULL_BRIGHT;
        ClocheSoil soil = state.kind.hydroponic() ? null : ClocheSoil.of(farm.getSoil());
        state.soil.clear();
        if (soil != null) {
            blockModelResolver.update(state.soil, soil.renderState(), DISPLAY_CONTEXT);
        }
        ClochePlants.Plant plant = farm.plant();
        float growth = farm.growth();
        state.plant.clear();
        if (plant != null) {
            blockModelResolver.update(state.plant, plant.stateAt(growth), DISPLAY_CONTEXT);
            Interior interior = interior(state.kind);
            float fit = Math.min(interior.cropWidth() / 16.0F, interior.cropHeight() / (16.0F * plant.render().height()));
            state.plantScale = plant.scales() ? fit * (MIN_SCALE + (1.0F - MIN_SCALE) * growth) : fit;
        }
    }

    private static Interior interior(ClocheBlockEntity.Kind kind) {
        return switch (kind) {
            case GLASS_CLOCHE -> GLASS_CLOCHE;
            case GROW_CHAMBER -> GROW_CHAMBER;
            case HYDROPONIC_CELL -> HYDROPONIC_CELL;
        };
    }

    @Override
    public void submit(ClocheRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Interior interior = interior(state.kind);
        if (!state.soil.isEmpty() && interior.soilTop() > interior.soilBottom()) {
            float size = 16.0F - 2.0F * interior.soilInset();
            poseStack.pushPose();
            poseStack.translate(interior.soilInset() * PX, interior.soilBottom() * PX, interior.soilInset() * PX);
            poseStack.scale(size * PX, (interior.soilTop() - interior.soilBottom()) * PX, size * PX);
            state.soil.submitMultiLayer(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (!state.plant.isEmpty() && state.plantScale > 0) {
            poseStack.pushPose();
            poseStack.translate(0.5F, interior.cropBase() * PX, 0.5F);
            poseStack.scale(state.plantScale, state.plantScale, state.plantScale);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            state.plant.submitMultiLayer(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
