/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.item.ItemPacket;

// Draws what is inside glass conduits: the liquid (filled to its level) or the items travelling through.
// The glass itself comes from the block model.
public class ConduitRenderer implements BlockEntityRenderer<ConduitBlockEntity, ConduitRenderState> {
    private static final float ITEM_SCALE = 0.3F;
    // Fluid cross-section inside the 6px pipe, in 1/16 block units.
    private static final float LO = 6.0F / 16.0F, HI = 10.0F / 16.0F, SPAN = HI - LO;
    private static final float CORE_LO = 6.0F / 16.0F, CORE_HI = 10.0F / 16.0F;
    // Port arms stop 2px short of the block face.
    private static final float PORT_INSET = 2.0F / 16.0F;

    private final ItemModelResolver itemModelResolver;

    public ConduitRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ConduitRenderState createRenderState() {
        return new ConduitRenderState();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public void extractRenderState(ConduitBlockEntity conduit, ConduitRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(conduit, state, partialTicks, cameraPosition, breakProgress);
        var blockState = conduit.getBlockState();
        for (Direction side : Direction.values()) {
            state.sides[side.ordinal()] = ConduitBlock.mode(blockState, side);
        }
        state.straight = ConduitBlock.straightAxis(blockState);

        state.fluidSprite = null;
        FluidStack fluid = conduit.getFluid();
        if (!fluid.isEmpty()) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            state.fluidSprite = model.stillMaterial().sprite();
            int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1;
            state.fluidColor = tint | 0xFF000000;
            state.fluidLight = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, fluid.getFluidType().getLightLevel());
            state.fill = Math.min(1.0F, fluid.getAmount() / (float) ConduitTier.LIQUID_CAPACITY_PER_CONDUIT);
        }

        state.items.clear();
        if (!conduit.getPackets().isEmpty() && conduit.getLevel() != null) {
            float time = conduit.getLevel().getGameTime() + partialTicks;
            int seed = (int) conduit.getBlockPos().asLong();
            for (ItemPacket packet : conduit.getPackets()) {
                ItemStackRenderState itemState = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(itemState, packet.stack, ItemDisplayContext.GROUND, conduit.getLevel(), null, seed++);
                float progress = Math.min(1.0F, packet.progress + conduit.getItemSpeed() * partialTicks);
                state.items.add(new ConduitRenderState.Item(itemState, ItemPacket.positionAt(packet.from, packet.to, progress), time * 4.0F));
            }
        }
    }

    @Override
    public void submit(ConduitRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fluidSprite != null && state.fill > 0.0F) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(state.fluidSprite.atlasLocation()),
                    (pose, buffer) -> drawFluid(state, pose, buffer));
        }
        for (ConduitRenderState.Item item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.position().x, item.position().y, item.position().z);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            poseStack.rotateDegrees(Axis.YP, item.spin());
            item.state().submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    // --- Liquid ---

    // Horizontal runs fill from the bottom; vertical runs fill their whole cross-section.
    private static void drawFluid(ConduitRenderState state, PoseStack.Pose pose, VertexConsumer buffer) {
        float top = LO + SPAN * state.fill;
        if (state.straight != null) {
            switch (state.straight) {
                case X -> box(state, pose, buffer, 0, LO, LO, 1, top, HI);
                case Z -> box(state, pose, buffer, LO, LO, 0, HI, top, 1);
                case Y -> box(state, pose, buffer, LO, 0, LO, HI, 1, HI);
            }
            return;
        }

        box(state, pose, buffer, CORE_LO, CORE_LO, CORE_LO, CORE_HI, top, CORE_HI);
        for (Direction side : Direction.values()) {
            ConnectionMode mode = state.sides[side.ordinal()];
            if (mode == ConnectionMode.NONE) {
                continue;
            }
            float outer = mode.isPort() ? PORT_INSET : 0.0F;
            switch (side) {
                case NORTH -> box(state, pose, buffer, LO, LO, outer, HI, top, CORE_LO);
                case SOUTH -> box(state, pose, buffer, LO, LO, CORE_HI, HI, top, 1 - outer);
                case WEST -> box(state, pose, buffer, outer, LO, LO, CORE_LO, top, HI);
                case EAST -> box(state, pose, buffer, CORE_HI, LO, LO, 1 - outer, top, HI);
                case DOWN -> box(state, pose, buffer, LO, outer, LO, HI, CORE_LO, HI);
                case UP -> box(state, pose, buffer, LO, CORE_HI, LO, HI, 1 - outer, HI);
            }
        }
    }

    private static void box(ConduitRenderState state, PoseStack.Pose pose, VertexConsumer buffer,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        TextureAtlasSprite sprite = state.fluidSprite;
        int color = state.fluidColor;
        int light = state.fluidLight;
        // Vertex order matches vanilla block faces so every face is wound outwards.
        quad(pose, buffer, sprite, color, light, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, z1, x1, z0);
        quad(pose, buffer, sprite, color, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1);
        quad(pose, buffer, sprite, color, light, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, x0, y0);
        quad(pose, buffer, sprite, color, light, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, x1, y0);
        quad(pose, buffer, sprite, color, light, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, z0, y1, z1, y0);
        quad(pose, buffer, sprite, color, light, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, z1, y1, z0, y0);
    }

    // Four corners plus the texture region (u from a to b, v from c to d, in block units mapped onto the sprite).
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        float minU = sprite.getU(u0), maxU = sprite.getU(u1);
        float minV = sprite.getV(1 - v0), maxV = sprite.getV(1 - v1);
        vertex(pose, buffer, ax, ay, az, minU, minV, color, light, nx, ny, nz);
        vertex(pose, buffer, bx, by, bz, minU, maxV, color, light, nx, ny, nz);
        vertex(pose, buffer, cx, cy, cz, maxU, maxV, color, light, nx, ny, nz);
        vertex(pose, buffer, dx, dy, dz, maxU, minV, color, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
            int color, int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
