/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.farming.PlantingBedBlockEntity;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;

// Draws a Planting Bed's soil (its block, squashed into the bed's recess) and the crop growing on it, full size, into the
// air of the greenhouse above: its crop stage for the growth, or grown in size for saplings, flowers and cane. Tall
// (two-block) crops are drawn smaller so they stay under the Grow Lamps. Both are ordinary block models.
public class PlantingBedRenderer implements BlockEntityRenderer<PlantingBedBlockEntity, ClocheRenderState> {
    private static final float PX = 1.0F / 16.0F;
    // The recess the soil fills (see the bed's model), in pixels.
    private static final float SOIL_INSET = 2.0F, SOIL_BOTTOM = 12.0F, SOIL_TOP = 15.0F;
    private static final float FULL_SCALE = 0.875F, TALL_SCALE = 0.75F, MIN_SCALE = 0.3F;

    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockModelResolver;

    public PlantingBedRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    @Override
    public ClocheRenderState createRenderState() {
        return new ClocheRenderState();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    // The crop reaches up to two blocks above the bed.
    @Override
    public AABB getRenderBoundingBox(PlantingBedBlockEntity bed) {
        return new AABB(bed.getBlockPos()).expandTowards(0.0, 2.0, 0.0);
    }

    @Override
    public void extractRenderState(PlantingBedBlockEntity bed, ClocheRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(bed, state, partialTicks, cameraPosition, breakProgress);
        // Lit by the air above the bed (the bed itself is solid).
        state.light = bed.getLevel() != null ? LevelRenderer.getLightCoords(bed.getLevel(), bed.getBlockPos().above()) : LightCoordsUtil.FULL_BRIGHT;
        ClocheSoil soil = ClocheSoil.of(bed.getSoil());
        state.soil.clear();
        if (soil != null) {
            blockModelResolver.update(state.soil, soil.renderState(), DISPLAY_CONTEXT);
        }
        ClochePlants.Plant plant = bed.plant();
        float growth = bed.growth();
        state.plant.clear();
        if (plant != null) {
            blockModelResolver.update(state.plant, plant.stateAt(growth), DISPLAY_CONTEXT);
            float fit = plant.render().height() > 1 ? TALL_SCALE : FULL_SCALE;
            state.plantScale = plant.scales() ? fit * (MIN_SCALE + (1.0F - MIN_SCALE) * growth) : fit;
        }
    }

    @Override
    public void submit(ClocheRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.soil.isEmpty()) {
            float size = 16.0F - 2.0F * SOIL_INSET;
            poseStack.pushPose();
            poseStack.translate(SOIL_INSET * PX, SOIL_BOTTOM * PX, SOIL_INSET * PX);
            poseStack.scale(size * PX, (SOIL_TOP - SOIL_BOTTOM) * PX, size * PX);
            state.soil.submitMultiLayer(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (!state.plant.isEmpty() && state.plantScale > 0) {
            poseStack.pushPose();
            poseStack.translate(0.5F, SOIL_TOP * PX, 0.5F);
            poseStack.scale(state.plantScale, state.plantScale, state.plantScale);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            state.plant.submitMultiLayer(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
