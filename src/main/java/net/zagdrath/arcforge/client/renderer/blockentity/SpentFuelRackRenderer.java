/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.nuclear.SpentFuelRackBlockEntity;

// Draws the rods standing in a Spent Fuel Rack's four cells (block/nuclear/rack_rod: the Zircaloy tube with its end
// plug, at 1:1), and while they're hot under water the Cherenkov glow round them (block/nuclear/cherenkov, full-bright): a
// sleeve of blue light a little wider than each rod.
public class SpentFuelRackRenderer implements BlockEntityRenderer<SpentFuelRackBlockEntity, SpentFuelRackRenderer.State> {
    public static final Identifier ROD = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/nuclear/rack_rod");
    public static final Identifier GLOW = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/nuclear/cherenkov");
    // Each cell's middle (px) and the rod's half width, its glow's, and how high they stand.
    private static final float[][] CELLS = { { 4.25F, 4.25F }, { 11.75F, 4.25F }, { 4.25F, 11.75F }, { 11.75F, 11.75F } };
    private static final float ROD_HALF = 1.5F, GLOW_HALF = 3.0F, BOTTOM = 2.0F, TOP = 15.0F;

    public static class State extends BlockEntityRenderState {
        public boolean[] rods = new boolean[4];
        public boolean glowing;
        public @Nullable TextureAtlasSprite rod, glow;
        public int light;
    }

    public SpentFuelRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpentFuelRackBlockEntity rack, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks, cameraPosition, breakProgress);
        for (int i = 0; i < 4; i++) {
            state.rods[i] = !rack.rod(i).isEmpty();
        }
        state.glowing = rack.isGlowing();
        state.rod = SteamBoilerArrayRenderer.sprite(ROD);
        state.glow = SteamBoilerArrayRenderer.sprite(GLOW);
        Level level = rack.getLevel();
        state.light = level != null ? LightCoordsUtil.getLightCoords(level, rack.getBlockPos()) : LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite rod = state.rod, glow = state.glow;
        if (rod == null || glow == null) {
            return;
        }
        boolean[] rods = state.rods.clone();
        int light = state.light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(rod.atlasLocation()), (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                if (rods[i]) {
                    column(pose, buffer, rod, CELLS[i][0], CELLS[i][1], ROD_HALF, light);
                }
            }
        });
        if (state.glowing) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(glow.atlasLocation()), (pose, buffer) -> {
                for (int i = 0; i < 4; i++) {
                    if (rods[i]) {
                        column(pose, buffer, glow, CELLS[i][0], CELLS[i][1], GLOW_HALF, LightCoordsUtil.FULL_BRIGHT);
                    }
                }
            });
        }
    }

    // An upright square column about (cx, cz) px, half wide. Its sprite is laid at 1:1 (never stretched): each side
    // shows the sprite's left 2 * half columns over its bottom TOP - BOTTOM rows, and the top its top-left square.
    private static void column(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float cx, float cz, float half, int light) {
        float x0 = (cx - half) / 16.0F, x1 = (cx + half) / 16.0F, z0 = (cz - half) / 16.0F, z1 = (cz + half) / 16.0F;
        float y0 = BOTTOM / 16.0F, y1 = TOP / 16.0F;
        float w = 2.0F * half / 16.0F;
        float u0 = sprite.getU(0.0F), u1 = sprite.getU(w), v0 = sprite.getV(1.0F - (TOP - BOTTOM) / 16.0F), v1 = sprite.getV(1.0F);
        quad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u1, u0, v1, v0, 0, 0, -1, light);
        quad(pose, buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u1, u0, v1, v0, 0, 0, 1, light);
        quad(pose, buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u1, u0, v1, v0, -1, 0, 0, light);
        quad(pose, buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u1, u0, v1, v0, 1, 0, 0, light);
        quad(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u0, u1, sprite.getV(w), sprite.getV(0.0F), 0, 1, 0, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
            float cz, float dx, float dy, float dz, float ua, float ub, float vBottom, float vTop, float nx, float ny, float nz, int light) {
        vertex(pose, buffer, ax, ay, az, ub, vBottom, nx, ny, nz, light);
        vertex(pose, buffer, bx, by, bz, ua, vBottom, nx, ny, nz, light);
        vertex(pose, buffer, cx, cy, cz, ua, vTop, nx, ny, nz, light);
        vertex(pose, buffer, dx, dy, dz, ub, vTop, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float ny, float nz,
            int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
