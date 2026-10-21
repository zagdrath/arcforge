/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.steam.DerrickMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Oil Derrick (its blocks draw nothing but the casings' port plates) from DerrickLayout, built by
// DerrickMesh on the Pumpjack's sheet: the lattice tower, rig floor, crown, drawworks, engine house and ladder once
// (and again when its ports change, checked every second), baked into the chunk mesh (ChunkMeshes); the travelling
// block and the drawworks drum built once and drawn moved, and the lines and kelly (which change length) each frame,
// out to stillPartsOnlyFrom (at rest past simpleMovingPartsFrom; see LargeModels).
// While it drills, the kelly turns (18° a tick) and the travelling block feeds slowly down with it over four seconds,
// then the kelly stops and the drawworks hoists the block back up in one, easing in and out of the lift; the drum pays
// the line out going down and reels it in coming up. Idle, the block hangs at the top of its run. Shaded like the
// refinery boxes (see PlantRenderer.emit).
public class OilDerrickRenderer implements StructureRenderer<OilDerrickBlockEntity, OilDerrickRenderer.State> {
    // The kelly's turn a tick, a stroke's feed down and hoist up (ticks), and the drum's turns over the block's run.
    private static final float SPIN = 18.0F, FEED = 80.0F, HOIST = 20.0F, DRUM_TURNS = 2.5F;
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<OilDerrickBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final OilfieldStructures.Rig rig;
        final ModelFrame frame;
        final DerrickMesh mesh;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY;
        final float[] drum, rest, drumAxis;
        // The box the drill string and drum stay in over the block's whole run.
        private final AABB drawn;
        long scanned;

        Built(OilfieldStructures.Rig rig, ModelFrame frame, DerrickMesh mesh, List<ModelMesh.Port> ports) {
            this.rig = rig;
            this.frame = frame;
            this.mesh = mesh;
            this.ports = ports;
            this.drum = mesh.buildDrum();
            this.drumAxis = mesh.drumAxis();
            this.rest = mesh.buildMoving(0.0F, 0.0F, 0.0F);
            this.drawn = MeshRendering.bounds(rig.min(), rest, mesh.buildMoving(1.0F, 0.0F, 0.0F)).inflate(0.5);
        }

        AABB drawn() {
            return drawn;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        // Up close: the drum turned, and the drill string; far off, the drill string at rest.
        public float[] drum = EMPTY, links = EMPTY, rest = EMPTY;
        public @Nullable Matrix4f drumTurn;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(OilDerrickBlockEntity derrick) {
        return ChunkMeshes.isBaked(derrick, MeshRendering.SOLID);
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
            ChunkMeshes.remove(derrick);
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
        float hoist = 0.0F;
        // In double: game time times the kelly's turn outgrows a float's precision on an old world.
        double drilled = 0.0;
        if (lit) {
            float stroke = FEED + HOIST, at = (float) (((double) time + partialTicks) % stroke);
            // The kelly only turns while the block feeds down: its turn so far is every stroke's feed, and this one's.
            drilled = Math.floor(((double) time + partialTicks) / stroke) * FEED + Math.min(at, FEED);
            if (at < FEED) {
                hoist = at / FEED;
            } else {
                float t = (at - FEED) / HOIST;
                hoist = 1.0F - t * t * (3.0F - 2.0F * t);
            }
        }
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(PumpjackRenderer.SHEET);
        // The light just above the rig floor's front middle (inside the casings is their own darkness).
        int light = LightCoordsUtil.getLightCoords(level, rig.controller().above());
        OilfieldStructures.Rig builtRig = model.rig;
        MeshRendering.bake(derrick, MeshRendering.SOLID, min, ChunkSectionLayer.SOLID, model.solid, sheet, light, () -> builtRig.equals(derrick.getRig()));
        state.sheet = sheet;
        state.light = light;
        state.drum = state.links = state.rest = EMPTY;
        state.drumTurn = null;
        switch (LargeModels.detail(model.drawn(), cameraPosition)) {
            case FULL -> {
                state.drum = model.drum;
                state.drumTurn = MeshRendering.turn(model.drumAxis, hoist * DRUM_TURNS * 360.0F);
                state.links = model.mesh.buildLinks(hoist, (float) (drilled * SPIN % 360.0));
            }
            case SIMPLE -> state.rest = model.rest;
            case STILL -> {}
        }
    }

    private static List<ModelMesh.Port> ports(Level level, OilfieldStructures.Rig rig, ModelFrame frame) {
        return PlantRenderer.ports(level, rig.parts(), rig::contains, rig.min(), frame);
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
        float[] links = state.links;
        int light = state.light;
        var solid = RenderTypes.entitySolid(sheet.atlasLocation());
        if (state.drumTurn != null) {
            MeshRendering.submitMoving(collector, poseStack, solid, state.drum, sheet, light, state.drumTurn);
        }
        if (links.length > 0) {
            collector.submitCustomGeometry(poseStack, solid, (pose, buffer) -> PlantRenderer.emit(pose, buffer, links, sheet, light));
        }
        MeshRendering.submitStill(collector, poseStack, solid, state.rest, sheet, light);
        poseStack.popPose();
    }

    // Only the drill string and the drum are drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(OilDerrickBlockEntity derrick) {
        Built model = built.get(derrick);
        return model != null && model.rig.equals(derrick.getRig()) ? model.drawn() : derrick.getRenderBox();
    }
}
