/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.HashSet;
import java.util.Set;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.client.model.WindowQuadrants;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;

// Draws the inside of a formed Firebox Array, seen through its windows. Like the steam arrays, the box is a thin skin (its
// casings and panes draw only their outer faces, and each wall casing leaves out its face toward the inside; see
// ConnectedModel, "firebox_array"), so this draws the inside of that skin: a firebrick lining just inside the walls, in the
// furnace brick tones, cut away behind every window with a reveal round each opening (TiledBoxes.lining). On the floor
// lies a bed of coals, dark while it's out; while it burns the coals glow and a flame rises from every block of the floor
// (two crossed planes of the animated flame sprite, a little taller toward the middle and swaying with the time),
// full-bright, and the lining glows with them.
public class FireboxArrayRenderer implements BlockEntityRenderer<FireboxArrayBlockEntity, FireboxArrayRenderer.State> {
    private static final float LINER_INSET = 1.0F / 16.0F;
    private static final float BED_INSET = 1.5F / 16.0F, BED_DEPTH = 2.0F / 16.0F;
    private static final int FULL_BRIGHT = 0xF000F0;
    // The lining and the coals while it's out, tinted darker.
    private static final int COLD_TINT = 0xFF8A8A8A;
    private static final Identifier LINING = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/firebox_array/lining");
    private static final Identifier COALS = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/firebox_array/coals");
    private static final Identifier COALS_LIT = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/firebox_array/coals_lit");
    private static final Identifier FLAME = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/firebox_array/flame");

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // The box's minimum corner, relative to the controller, and its size.
        public float x, y, z;
        public int sizeX, sizeY, sizeZ;
        public boolean burning;
        public float time;
        // The lining tiles behind windows (see TiledBoxes.windowKey), which are left out.
        public Set<Long> windows = Set.of();
        public @Nullable TextureAtlasSprite lining, jamb, coals, coalsLit, flame;
        public int light;
    }

    public FireboxArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FireboxArrayBlockEntity array, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(array, state, partialTicks, cameraPosition, breakProgress);
        FireboxArrayStructure.Box box = array.getBox();
        state.formed = box != null;
        if (box == null) {
            return;
        }
        BlockPos rel = box.min().subtract(array.getBlockPos());
        state.x = rel.getX();
        state.y = rel.getY();
        state.z = rel.getZ();
        state.sizeX = box.size(Direction.Axis.X);
        state.sizeY = box.size(Direction.Axis.Y);
        state.sizeZ = box.size(Direction.Axis.Z);
        state.burning = array.isRunning();
        state.time = array.getLevel() != null ? (array.getLevel().getGameTime() % 24_000L) + partialTicks : 0.0F;
        state.lining = SteamBoilerArrayRenderer.sprite(LINING);
        state.jamb = SteamBoilerArrayRenderer.sprite(SteamBoilerArrayRenderer.JAMB);
        state.coals = SteamBoilerArrayRenderer.sprite(COALS);
        state.coalsLit = SteamBoilerArrayRenderer.sprite(COALS_LIT);
        state.flame = SteamBoilerArrayRenderer.sprite(FLAME);
        state.windows = array.windowQuads(found -> findWindowQuads(array.getLevel(), found));
        state.light = state.burning ? FULL_BRIGHT
                : array.getLevel() != null ? LightCoordsUtil.getLightCoords(array.getLevel(), box.centre()) : state.lightCoords;
    }

    // The half-block lining tiles of the box's walls that lie behind window (TiledBoxes.windowKey): those behind a Pressure
    // Glass pane, and those behind a casing quadrant that shows window (the rule ConnectedModel draws the casings by).
    static Set<Long> findWindowQuads(@Nullable Level level, FireboxArrayStructure.Box box) {
        Set<Long> windows = new HashSet<>();
        if (level == null) {
            return windows;
        }
        int[] size = { box.size(Direction.Axis.X), box.size(Direction.Axis.Y), box.size(Direction.Axis.Z) };
        for (Direction face : Direction.values()) {
            int a = face.getAxis().ordinal();
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;
            Direction up = WindowQuadrants.up(face);
            Direction right = WindowQuadrants.right(face);
            for (int hu = 0; hu < size[u] * 2; hu++) {
                for (int hv = 0; hv < size[v] * 2; hv++) {
                    int[] offset = new int[3];
                    offset[a] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size[a] - 1 : 0;
                    offset[u] = hu / 2;
                    offset[v] = hv / 2;
                    BlockPos pos = box.min().offset(offset[0], offset[1], offset[2]);
                    BlockState state = level.getBlockState(pos);
                    boolean window;
                    if (PressureGlassBlock.isFormed(state)) {
                        window = true;
                    } else if (!FireboxArrayStructure.isFormedPart(state)) {
                        window = false;
                    } else {
                        float[] centre = new float[3];
                        centre[u] = hu % 2 == 0 ? -1.0F : 1.0F;
                        centre[v] = hv % 2 == 0 ? -1.0F : 1.0F;
                        boolean top = centre[up.getAxis().ordinal()] * up.getAxisDirection().getStep() > 0;
                        boolean isRight = centre[right.getAxis().ordinal()] * right.getAxisDirection().getStep() > 0;
                        window = WindowQuadrants.windowed(level, pos, face, top, isRight);
                    }
                    if (window) {
                        windows.add(TiledBoxes.windowKey(face, hu, hv));
                    }
                }
            }
        }
        return windows;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.lining == null || state.jamb == null || state.coals == null || state.coalsLit == null || state.flame == null) {
            return;
        }
        TextureAtlasSprite lining = state.lining;
        TextureAtlasSprite jamb = state.jamb;
        TextureAtlasSprite coals = state.burning ? state.coalsLit : state.coals;
        int tint = state.burning ? -1 : COLD_TINT;
        float floor = BED_INSET;
        float bedTop = floor + BED_DEPTH;
        poseStack.pushPose();
        // Drawn from the box's minimum corner.
        poseStack.translate(state.x, state.y, state.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(lining.atlasLocation()), (pose, buffer) -> {
            TiledBoxes.lining(pose, buffer, lining, jamb, tint, state.light, LINER_INSET, state.sizeX, state.sizeY, state.sizeZ, state.windows);
            TiledBoxes.box(pose, buffer, coals, tint, state.light, BED_INSET, floor, BED_INSET, state.sizeX - BED_INSET, bedTop,
                    state.sizeZ - BED_INSET, false, Direction.DOWN);
        });
        if (state.burning) {
            TextureAtlasSprite flame = state.flame;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(flame.atlasLocation()),
                    (pose, buffer) -> flames(pose, buffer, flame, state, bedTop));
        }
        poseStack.popPose();
    }

    // A flame over every block of the floor (inside the walls' skin): two crossed planes, each drawn from both sides.
    private static void flames(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, State state, float base) {
        float room = state.sizeY - base - LINER_INSET - 0.25F;
        float midX = state.sizeX / 2.0F, midZ = state.sizeZ / 2.0F;
        float reach = Math.max(midX, midZ);
        for (int bx = 0; bx < state.sizeX; bx++) {
            for (int bz = 0; bz < state.sizeZ; bz++) {
                float cx = bx + 0.5F, cz = bz + 0.5F;
                // Taller toward the middle, and each sways on its own phase.
                float toMiddle = 1.0F - 0.35F * (Math.max(Math.abs(cx - midX), Math.abs(cz - midZ)) / reach);
                float sway = 0.85F + 0.15F * (float) Math.sin(state.time * 0.15F + bx * 1.7F + bz * 2.3F);
                float height = Math.min(room, 1.6F) * toMiddle * sway;
                float half = 0.5F;
                quad(pose, buffer, sprite, cx - half, base, cz - half, cx + half, base + height, cz + half);
                quad(pose, buffer, sprite, cx + half, base, cz - half, cx - half, base + height, cz + half);
            }
        }
    }

    // A vertical plane from (x0, y0, z0) to (x1, y1, z1), full-bright, both sides.
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1,
            float z1) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float nx = z1 - z0, nz = x0 - x1;
        float length = (float) Math.sqrt(nx * nx + nz * nz);
        nx /= length;
        nz /= length;
        vertex(pose, buffer, x0, y1, z0, u0, v0, nx, nz);
        vertex(pose, buffer, x0, y0, z0, u0, v1, nx, nz);
        vertex(pose, buffer, x1, y0, z1, u1, v1, nx, nz);
        vertex(pose, buffer, x1, y1, z1, u1, v0, nx, nz);
        vertex(pose, buffer, x1, y1, z1, u1, v0, -nx, -nz);
        vertex(pose, buffer, x1, y0, z1, u1, v1, -nx, -nz);
        vertex(pose, buffer, x0, y0, z0, u0, v1, -nx, -nz);
        vertex(pose, buffer, x0, y1, z0, u0, v0, -nx, -nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, 0.0F, nz);
    }

    // The whole box, so the inside isn't culled when the controller is off screen.
    @Override
    public AABB getRenderBoundingBox(FireboxArrayBlockEntity array) {
        return array.getRenderBox();
    }
}
