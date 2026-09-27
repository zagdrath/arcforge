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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;

// While a formed Arcforge Furnace burns, a pool of molten metal glows at the bottom of its stack.
public class ArcforgeFurnaceRenderer implements BlockEntityRenderer<ArcforgeFurnaceBlockEntity, ArcforgeFurnaceRenderer.State> {
    private static final float INSET = 0.02F, DEPTH = 3.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public Direction facing = Direction.NORTH;
    }

    public ArcforgeFurnaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArcforgeFurnaceBlockEntity furnace, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(furnace, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        if (!furnace.isFormed() || !furnace.getBlockState().getValue(ArcforgeFurnacePortBlock.LIT)) {
            return;
        }
        state.facing = furnace.getFacing();
        state.sprite = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(Fluids.LAVA.defaultFluidState()).stillMaterial().sprite();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        // The pool sits in the hollow centre, one block behind the port.
        poseStack.pushPose();
        poseStack.translate(-state.facing.getStepX(), 0.0F, -state.facing.getStepZ());
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT,
                        INSET, INSET, INSET, 1.0F - INSET, DEPTH, 1.0F - INSET));
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ArcforgeFurnaceBlockEntity furnace) {
        BlockPos pos = furnace.getBlockPos();
        return new AABB(pos).inflate(1.0, 0.0, 1.0);
    }
}
