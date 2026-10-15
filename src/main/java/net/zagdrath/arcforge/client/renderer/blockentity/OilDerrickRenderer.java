/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.steam.DerrickMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Oil Derrick (its blocks draw nothing but the casings' port plates) from DerrickLayout, built by
// DerrickMesh on the Pumpjack's sheet: the lattice tower, rig floor, crown, drawworks, engine house and ladder once
// (and again when its ports change, checked every second), and every frame the drill string and the drawworks drum.
// While it drills, the kelly turns (18° a tick) and the travelling block runs down with it, a stroke every two
// seconds, the drum paying the line out as it goes; idle, the block hangs at the top of its run. Shaded like the
// refinery boxes (see PlantRenderer.emit).
public class OilDerrickRenderer implements StructureRenderer<OilDerrickBlockEntity, OilDerrickRenderer.State> {
    private static final float SPIN = 18.0F, STROKE = 40.0F, DRUM_TURNS = 2.5F;
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<OilDerrickBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final OilfieldStructures.Rig rig;
        final ModelFrame frame;
        final DerrickMesh mesh;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY;
        long scanned;

        Built(OilfieldStructures.Rig rig, ModelFrame frame, DerrickMesh mesh, List<ModelMesh.Port> ports) {
            this.rig = rig;
            this.frame = frame;
            this.mesh = mesh;
            this.ports = ports;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        public float[] solid = EMPTY, moving = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public OilDerrickRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OilDerrickBlockEntity derrick, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(derrick, state, partialTicks, cameraPosition, breakProgress);
        OilfieldStructures.Rig rig = derrick.getRig();
        Level level = derrick.getLevel();
        state.formed = rig != null && level != null;
        if (rig == null || level == null) {
            built.remove(derrick);
            return;
        }
        BlockPos min = rig.min();
        state.offsetX = min.getX() - derrick.getBlockPos().getX();
        state.offsetY = min.getY() - derrick.getBlockPos().getY();
        state.offsetZ = min.getZ() - derrick.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(derrick);
        if (model == null || !model.rig.equals(rig)) {
            ModelFrame frame = OilDerrickBlockEntity.frame(rig);
            model = new Built(rig, frame, new DerrickMesh(frame), ports(level, rig, frame));
            model.solid = model.mesh.buildStatic(model.ports)[0];
            model.scanned = time;
            built.put(derrick, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<ModelMesh.Port> ports = ports(level, rig, model.frame);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                model.solid = model.mesh.buildStatic(ports)[0];
            }
        }
        boolean lit = derrick.getBlockState().getValue(OilfieldControllerBlock.LIT);
        float now = time + partialTicks;
        float hoist = lit ? now % STROKE / STROKE : 0.0F;
        state.solid = model.solid;
        state.moving = model.mesh.buildMoving(hoist, lit ? now * SPIN % 360.0F : 0.0F, hoist * DRUM_TURNS * 360.0F);
        state.sheet = SteamBoilerArrayRenderer.sprite(PumpjackRenderer.SHEET);
        // The light just above the rig floor's front middle (inside the casings is their own darkness).
        state.light = LightCoordsUtil.getLightCoords(level, rig.controller().above());
    }

    private static List<ModelMesh.Port> ports(Level level, OilfieldStructures.Rig rig, ModelFrame frame) {
        return PlantRenderer.ports(level, rig.parts(), rig::contains, rig.min(), frame);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] solid = state.solid, moving = state.moving;
        int light = state.light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> {
            PlantRenderer.emit(pose, buffer, solid, sheet, light);
            PlantRenderer.emit(pose, buffer, moving, sheet, light);
        });
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(OilDerrickBlockEntity derrick) {
        return derrick.getRenderBox();
    }
}
