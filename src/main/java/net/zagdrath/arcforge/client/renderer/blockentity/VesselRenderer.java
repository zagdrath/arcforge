/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.client.renderer.ArcforgeRenderTypes;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.FissionReactorMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed walled vessel (the Fission Reactor, the Steam Generator, the Heavy Water Tower) from its VesselMesh on
// the refinery tower's sheet; its walls' blocks draw nothing but their port plates. The model is built once, and again
// when its ports or what the subclass lays it out from change (checked every second). Its lists: solid and cutout
// (baked into the chunk mesh, see ChunkMeshes), the glow (full-bright, always), the running glow (full-bright, only
// while the controller is lit) and the tint (glass's tint, translucent); and a vessel with windows onto its water (the
// Fission Reactor) adds that water, cut to the level the controller reports whenever it changes. Those last are drawn
// here, out to stillPartsOnlyFrom (see LargeModels).
//
// The see-through parts go in order: the glow, then the water over it, then the tint over both (glass, water, core seen
// from outside). Render types in one order are drawn in no set order among themselves, so each goes in an order of its
// own.
public abstract class VesselRenderer<B extends VesselControllerBlockEntity> implements StructureRenderer<B, VesselRenderer.State> {
    private static final int SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];
    // How opaque the tint list is drawn (of 255): a light smoky glass the inside still shows clearly through.
    private static final int TINT_ALPHA = 0x5A;
    // The water's tint (vanilla water's default blue) and how opaque it's drawn over the water sprite's own alpha: the
    // core, and its glow while running, still show through it.
    private static final int WATER_COLOR = 0xA03F76E4;
    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    // The orders the water and the tint are submitted in (the glow is in the default, 0).
    private static final int WATER_ORDER = 1, TINT_ORDER = 2;

    private final Map<B, Built> built = new WeakHashMap<>();

    private static final class Built {
        final VesselStructure.Box box;
        final ModelFrame frame;
        Object key;
        List<ModelMesh.Port> ports;
        float[][] parts = { EMPTY, EMPTY, EMPTY, EMPTY };
        FissionReactorMesh.@Nullable Water water;
        // The water cut to the last fill asked for.
        float waterFill = -1.0F;
        float[] waterQuads = EMPTY;
        // The box round what this renderer draws (the glows, the tint and the water), or null when it draws nothing.
        @Nullable AABB drawn;
        long scanned;
        int light;

        void set(Model model) {
            parts = model.parts();
            water = model.water();
            waterFill = -1.0F;
            FissionReactorMesh.Water w = water;
            float[] full = w != null ? w.at(1.0F) : EMPTY;
            float[] running = parts.length > 3 ? parts[3] : EMPTY, tint = parts.length > 4 ? parts[4] : EMPTY;
            drawn = parts[2].length + running.length + tint.length + full.length == 0 ? null : MeshRendering.bounds(box.min(), parts[2], running, tint, full);
        }

        float[] water(float fill) {
            FissionReactorMesh.Water w = water;
            if (w == null) {
                return EMPTY;
            }
            if (fill != waterFill) {
                waterFill = fill;
                waterQuads = w.at(fill);
            }
            return waterQuads;
        }

        Built(VesselStructure.Box box, ModelFrame frame, Object key, List<ModelMesh.Port> ports) {
            this.box = box;
            this.frame = frame;
            this.key = key;
            this.ports = ports;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed, running;
        public int offsetX, offsetY, offsetZ;
        public float[] glow = EMPTY, runningGlow = EMPTY, tint = EMPTY, water = EMPTY;
        public float glowLevel = 1.0F;
        // The camera in the model's frame (blocks from the box's minimum corner), to cull the water's back faces.
        public float cameraX, cameraY, cameraZ;
        public @Nullable TextureAtlasSprite sheet, waterSprite;
        public int waterColor = WATER_COLOR;
        public int light;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(B vessel) {
        return ChunkMeshes.isBaked(vessel, MeshRendering.SOLID, MeshRendering.GLASS);
    }

    // The model's frame: x across the front, u back from it, over the whole box.
    public static ModelFrame frame(VesselStructure.Box box) {
        Direction along = box.front().getOpposite();
        int width = box.size(along.getClockWise().getAxis()), length = box.size(along.getAxis());
        return ModelFrame.toward(along, width, box.size(Direction.Axis.Y), length);
    }

    // What the model is laid out from besides its box and ports (compared with equals to tell when to rebuild).
    protected abstract Object layout(Level level, B vessel, VesselStructure.Box box, ModelFrame frame);

    // The model's lists for this layout: {solid, cutout, glow} and, optionally, the glow drawn only while running and
    // the tint drawn translucent.
    protected abstract float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports);

    // The model's lists and the water its windows show (null for none).
    protected record Model(float[][] parts, FissionReactorMesh.@Nullable Water water) {}

    // The model for this layout: build's lists and no water, unless the vessel shows its water.
    protected Model model(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        return new Model(build(frame, layout, ports), null);
    }

    // The controller's block {x, y, u} in the frame.
    protected static int[] controllerBlock(VesselControllerBlockEntity vessel, VesselStructure.Box box, ModelFrame frame) {
        BlockPos min = box.min(), pos = vessel.getBlockPos();
        return frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
    }

    @Override
    public void extractRenderState(B vessel, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(vessel, state, partialTicks, cameraPosition, breakProgress);
        VesselStructure.Box box = vessel.getBox();
        Level level = vessel.getLevel();
        state.formed = box != null && level != null && vessel.isFormed();
        if (box == null || level == null || !state.formed) {
            built.remove(vessel);
            ChunkMeshes.remove(vessel);
            return;
        }
        BlockPos min = box.min();
        state.offsetX = min.getX() - vessel.getBlockPos().getX();
        state.offsetY = min.getY() - vessel.getBlockPos().getY();
        state.offsetZ = min.getZ() - vessel.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(vessel);
        if (model == null || !model.box.equals(box)) {
            ModelFrame frame = frame(box);
            model = new Built(box, frame, layout(level, vessel, box, frame), ports(level, box, frame));
            model.set(model(frame, model.key, model.ports));
            model.light = exteriorLight(level, box);
            model.scanned = time;
            built.put(vessel, model);
        } else if (Math.abs(time - model.scanned) >= SCAN_INTERVAL) {
            model.scanned = time;
            model.light = exteriorLight(level, box);
            Object key = layout(level, vessel, box, model.frame);
            List<ModelMesh.Port> ports = ports(level, box, model.frame);
            if (!Objects.equals(key, model.key) || !ports.equals(model.ports)) {
                model.key = key;
                model.ports = ports;
                model.set(model(model.frame, key, ports));
            }
        }
        float[][] parts = model.parts;
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        int light = model.light;
        VesselStructure.Box builtBox = model.box;
        java.util.function.BooleanSupplier stands = () -> vessel.isFormed() && builtBox.equals(vessel.getBox());
        MeshRendering.bake(vessel, MeshRendering.SOLID, min, ChunkSectionLayer.SOLID, parts[0], sheet, light, stands);
        MeshRendering.bake(vessel, MeshRendering.GLASS, min, ChunkSectionLayer.CUTOUT, parts[1], sheet, light, stands);
        state.sheet = sheet;
        state.light = light;
        if (LargeModels.detail(getRenderBoundingBox(vessel), cameraPosition) == LargeModels.Detail.STILL) {
            state.glow = state.runningGlow = state.tint = state.water = EMPTY;
            return;
        }
        state.glow = parts[2];
        state.running = vessel.isLit();
        state.runningGlow = state.running && parts.length > 3 ? parts[3] : EMPTY;
        state.glowLevel = vessel.glowLevel();
        state.tint = parts.length > 4 ? parts[4] : EMPTY;
        state.water = model.water(vessel.waterLevel());
        Identifier coolant = vessel.coolantTexture();
        state.waterSprite = state.water.length > 0 ? SteamBoilerArrayRenderer.sprite(coolant != null ? coolant : WATER_STILL) : null;
        state.waterColor = vessel.coolantColor();
        state.cameraX = (float) (cameraPosition.x - min.getX());
        state.cameraY = (float) (cameraPosition.y - min.getY());
        state.cameraZ = (float) (cameraPosition.z - min.getZ());
    }

    // The model uses one light value. Sample just outside all six faces, so floor and wall lights count indoors too;
    // a single sample above the roof can be dark (or inside the building's ceiling). Cache this with the layout scan
    // instead of visiting the whole surface each frame. Keep block light and skylight independently.
    private static int exteriorLight(Level level, VesselStructure.Box box) {
        BlockPos min = box.min(), max = box.max();
        int light = 0;
        for (Direction face : Direction.values()) {
            BlockPos from = min.relative(face), to = max.relative(face);
            switch (face.getAxis()) {
                case X -> {
                    int x = face.getStepX() < 0 ? from.getX() : to.getX();
                    from = new BlockPos(x, min.getY(), min.getZ());
                    to = new BlockPos(x, max.getY(), max.getZ());
                }
                case Y -> {
                    int y = face.getStepY() < 0 ? from.getY() : to.getY();
                    from = from.atY(y);
                    to = to.atY(y);
                }
                case Z -> {
                    int z = face.getStepZ() < 0 ? from.getZ() : to.getZ();
                    from = new BlockPos(min.getX(), min.getY(), z);
                    to = new BlockPos(max.getX(), max.getY(), z);
                }
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, pos));
                if (light == LightCoordsUtil.FULL_BRIGHT) {
                    return light;
                }
            }
        }
        return light;
    }

    // The ports on the walls' outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, VesselStructure.Box box, ModelFrame frame) {
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos pos : box.positions()) {
            if (box.isWall(pos)) {
                walls.add(pos.immutable());
            }
        }
        return MeshRendering.ports(level, walls, box::contains, box.min(), frame);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ChunkMeshes.submitHeld(state, poseStack, collector);
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] glow = state.glow, lit = state.runningGlow, tint = state.tint;
        float glowLevel = state.glowLevel;
        int light = state.light;
        if (glow.length > 0 || lit.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> {
                emitLit(pose, buffer, glow, sheet, 1.0F);
                emitLit(pose, buffer, lit, sheet, glowLevel);
            });
        }
        // The water over the glow, and the tint over both (see the class comment).
        float[] water = state.water;
        TextureAtlasSprite waterSprite = state.waterSprite;
        if (water.length > 0 && waterSprite != null) {
            float camX = state.cameraX, camY = state.cameraY, camZ = state.cameraZ;
            int waterColor = state.waterColor;
            collector.order(WATER_ORDER).submitCustomGeometry(poseStack, ArcforgeRenderTypes.volumeTranslucent(waterSprite.atlasLocation()),
                    (pose, buffer) -> emitWater(pose, buffer, water, waterSprite, waterColor, light, camX, camY, camZ));
        }
        if (tint.length > 0) {
            collector.order(TINT_ORDER).submitCustomGeometry(poseStack, RenderTypes.entityTranslucentCull(sheet.atlasLocation()),
                    (pose, buffer) -> emitTint(pose, buffer, tint, sheet, light));
        }
        poseStack.popPose();
    }

    // The tint list shaded as MeshRendering.emit does, at TINT_ALPHA.
    private static void emitTint(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            int shade = Math.round(255.0F * (nx * nx * 0.6F + ny * ny * (ny > 0.0F ? 1.0F : 0.5F) + nz * nz * 0.8F));
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(TINT_ALPHA << 24 | shade << 16 | shade << 8 | shade)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    // The water (or the vessel's other coolant) in its sprite, tinted its colour (WATER_COLOR for water), only the quads
    // facing the camera: its pipeline draws both sides, and a face seen from behind is one across the vessel, behind the
    // water already drawn in front of it.
    private static void emitWater(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sprite, int color, int light,
            float camX, float camY, float camZ) {
        for (int q = 0; q + 31 < data.length; q += 32) {
            float nx = data[q + 5], ny = data[q + 6], nz = data[q + 7];
            if ((camX - data[q]) * nx + (camY - data[q + 1]) * ny + (camZ - data[q + 2]) * nz <= 0.0F) {
                continue;
            }
            for (int i = q; i < q + 32; i += 8) {
                buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                        .setColor(color)
                        .setUv(sprite.getU(data[i + 3]), sprite.getV(data[i + 4]))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    // Full-bright, at level (0..1) of its full strength: the eyes pipeline blends by alpha, so a fainter glow lets more of
    // the unlit part behind it show.
    private static void emitLit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, float level) {
        int color = Math.round(255.0F * Math.clamp(level, 0.0F, 1.0F)) << 24 | 0xFFFFFF;
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(color)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // Only the glows, the tint and the water are drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(B vessel) {
        Built model = built.get(vessel);
        if (model != null && model.box.equals(vessel.getBox())) {
            AABB drawn = model.drawn;
            return drawn != null ? drawn : new AABB(model.box.min()).deflate(0.5);
        }
        return vessel.getRenderBox();
    }
}
