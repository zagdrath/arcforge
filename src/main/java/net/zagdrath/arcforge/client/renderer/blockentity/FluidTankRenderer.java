/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;

// Draws the liquid inside a fluid tank's glass, rising with the fill level (gases hang from the top).
// Glowing fluids render full-bright.
public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    // Just inside the glass (4..12) so the faces don't z-fight, between the plates (2..14).
    private static final float MIN = 4.02F / 16.0F, MAX = 11.98F / 16.0F;
    private static final float BOTTOM = 2.0F / 16.0F, HEIGHT = 12.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public int color = -1;
        public int light;
        public float fill;
        public boolean gaseous;
    }

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity tank, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty() || tank.getCapacity() <= 0) {
            return;
        }
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        state.sprite = model.stillMaterial().sprite();
        int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1;
        state.color = tint | 0xFF000000;
        state.light = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, fluid.getFluidType().getLightLevel(fluid));
        state.fill = Math.min(1.0F, fluid.getAmount() / (float) tank.getCapacity());
        state.gaseous = fluid.getFluidType().isLighterThanAir();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null || state.fill <= 0.0F) {
            return;
        }
        float height = HEIGHT * state.fill;
        float y0 = state.gaseous ? BOTTOM + HEIGHT - height : BOTTOM;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, state.color, state.light, MIN, y0, MIN, MAX, y0 + height, MAX));
    }
}
