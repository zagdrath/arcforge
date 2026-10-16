/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.PlantControllerBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed refinery box (the Cracking Unit, the Steam Cracker or the Diesel Generator; its casings draw nothing but their port
// plates) from its mesh on a model sheet, with a nozzle from the model out to each port. The still parts are built once
// and again when the ports change (checked every second); the turning parts each frame; the glow list full-bright, as
// bright as the subclass says. Shaded like the Pumpjack (see PumpjackRenderer.emit).
public abstract class PlantRenderer<T extends PlantControllerBlockEntity, M> implements StructureRenderer<T, PlantRenderer.State> {
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<T, Built<M>> built = new WeakHashMap<>();

    private static final class Built<M> {
        final OilfieldStructures.PlantBox box;
        final ModelFrame frame;
        final M mesh;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY, glass = EMPTY, glow = EMPTY;
        long scanned;

        Built(OilfieldStructures.PlantBox box, ModelFrame frame, M mesh, List<ModelMesh.Port> ports) {
            this.box = box;
            this.frame = frame;
            this.mesh = mesh;
            this.ports = ports;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        public float[] solid = EMPTY, glass = EMPTY, glow = EMPTY, moving = EMPTY;
        public float brightness;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    // The sheet the model is painted on.
    protected abstract Identifier sheet();

    protected abstract M newMesh(ModelFrame frame);

    // The still parts with these ports: {solid, cutout, glow}.
    protected abstract float[][] buildStatic(M mesh, List<ModelMesh.Port> ports);

    // The turning parts this frame (none by default).
    protected float[] buildMoving(M mesh, T plant, float now) {
        return EMPTY;
    }

    // How bright the glow list is (0 to 1).
    protected float brightness(T plant) {
        return 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T plant, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(plant, state, partialTicks, cameraPosition, breakProgress);
        OilfieldStructures.PlantBox box = plant.getBox();
        Level level = plant.getLevel();
        state.formed = box != null && level != null;
        if (box == null || level == null) {
            built.remove(plant);
            return;
        }
        BlockPos min = box.min();
        state.offsetX = min.getX() - plant.getBlockPos().getX();
        state.offsetY = min.getY() - plant.getBlockPos().getY();
        state.offsetZ = min.getZ() - plant.getBlockPos().getZ();
        long time = level.getGameTime();
        Built<M> model = built.get(plant);
        if (model == null || !model.box.equals(box)) {
            ModelFrame frame = PlantControllerBlockEntity.frame(box);
            model = new Built<>(box, frame, newMesh(frame), ports(level, box, frame));
            rebuild(model);
            model.scanned = time;
            built.put(plant, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<ModelMesh.Port> ports = ports(level, box, model.frame);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                rebuild(model);
            }
        }
        state.solid = model.solid;
        state.glass = model.glass;
        state.glow = model.glow;
        state.moving = buildMoving(model.mesh, plant, time + partialTicks);
        state.brightness = brightness(plant);
        state.sheet = SteamBoilerArrayRenderer.sprite(sheet());
        // The light just above the structure's front middle (inside it is the casings' own darkness).
        state.light = LightCoordsUtil.getLightCoords(level, box.at(0, box.height(), 0));
    }

    private void rebuild(Built<M> model) {
        float[][] parts = buildStatic(model.mesh, model.ports);
        model.solid = parts[0];
        model.glass = parts[1];
        model.glow = parts[2];
    }

    // The ports on the structure's outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, OilfieldStructures.PlantBox box, ModelFrame frame) {
        return ports(level, box.parts(), box::contains, box.min(), frame);
    }

    // The ports on a structure's outer faces (its parts, inside telling which blocks are its own), in the model's frame
    // from min.
    static List<ModelMesh.Port> ports(Level level, List<BlockPos> parts, java.util.function.Predicate<BlockPos> inside, BlockPos min, ModelFrame frame) {
        List<ModelMesh.Port> ports = new ArrayList<>();
        for (BlockPos pos : parts) {
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
                continue;
            }
            int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            for (Direction face : Direction.values()) {
                if (faces.get(face) == SideMode.NONE || inside.test(pos.relative(face))) {
                    continue;
                }
                int[] n = frame.frameNormal(face.getStepX(), face.getStepY(), face.getStepZ());
                ports.add(new ModelMesh.Port(block[2], block[1], block[0], n[0], n[1], n[2]));
            }
        }
        return ports;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] solid = state.solid, moving = state.moving, glass = state.glass, glow = state.glow;
        int light = state.light;
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), solid, sheet, light);
        if (moving.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> emit(pose, buffer, moving, sheet, light));
        }
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), glass, sheet, light);
        if (glow.length > 0 && state.brightness > 0.01F) {
            int level = Math.round(255 * Math.min(1.0F, state.brightness));
            int color = ARGB.color(level, level, level, level);
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()),
                    (pose, buffer) -> emitLit(pose, buffer, glow, sheet, color, LightCoordsUtil.FULL_BRIGHT));
        }
        poseStack.popPose();
    }

    // Each face shaded as a block face turned its way is, its normal straight up (as PumpjackRenderer.emit).
    static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            int shade = Math.round(255.0F * (nx * nx * 0.6F + ny * ny * (ny > 0.0F ? 1.0F : 0.5F) + nz * nz * 0.8F));
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFF000000 | shade << 16 | shade << 8 | shade)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    // The same without face shading, for light: the glow.
    private static void emitLit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int color, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(color)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    @Override
    public AABB getRenderBoundingBox(T plant) {
        return plant.getRenderBox();
    }
}
