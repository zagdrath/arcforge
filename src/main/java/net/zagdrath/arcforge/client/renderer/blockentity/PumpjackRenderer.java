/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.PumpjackLayout;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.PumpjackMesh;

// Draws a formed Pumpjack (its casings draw nothing but their port plates) from PumpjackLayout, built by PumpjackMesh
// on the sheet block/pumpjack/pumpjack, with a nozzle from the model out to each port. The still parts are built once
// (and again when the ports change, checked every second) and baked into the chunk mesh (ChunkMeshes). The beam and the
// cranks are built once at rest and drawn turned to the crank's angle, which turns as fast as the pumpjack pumps (see
// PumpjackBlockEntity.advanceCrank), so it slows as its deposit drains; the pitman arms and the bridle, which change
// shape as they move, are built each frame. Past simpleMovingPartsFrom it all stands still at rest, and past
// stillPartsOnlyFrom only the chunk mesh's still parts show (see LargeModels).
public class PumpjackRenderer implements StructureRenderer<PumpjackBlockEntity, PumpjackRenderer.State> {
    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/pumpjack/pumpjack");
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<PumpjackBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final OilfieldStructures.Unit unit;
        final ModelFrame frame;
        final PumpjackMesh mesh;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY, glass = EMPTY;
        final float[] beam, cranks, rest;
        final float[] beamAxis, crankAxis;
        final AABB drawn;
        long scanned;

        Built(OilfieldStructures.Unit unit, List<ModelMesh.Port> ports) {
            this.unit = unit;
            this.frame = PumpjackBlockEntity.frame(unit);
            this.mesh = new PumpjackMesh(frame);
            this.ports = ports;
            this.beam = mesh.buildBeam();
            this.cranks = mesh.buildCranks();
            this.rest = mesh.buildMoving(0.0);
            this.beamAxis = mesh.beamAxis();
            this.crankAxis = mesh.crankAxis();
            // The moving parts sweep within a block and a half of where they rest.
            this.drawn = MeshRendering.bounds(unit.min(), rest).inflate(1.5);
            rebuild();
        }

        void rebuild() {
            float[][] parts = mesh.buildStatic(ports);
            solid = parts[0];
            glass = parts[1];
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        // Up close: the beam and cranks turned, and the links; far off, the moving parts at rest.
        public float[] beam = EMPTY, cranks = EMPTY, links = EMPTY, rest = EMPTY;
        public @Nullable Matrix4f beamTurn, crankTurn;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(PumpjackBlockEntity pumpjack) {
        return ChunkMeshes.isBaked(pumpjack, MeshRendering.SOLID, MeshRendering.GLASS);
    }

    @Override
    public void extractRenderState(PumpjackBlockEntity pumpjack, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(pumpjack, state, partialTicks, cameraPosition, breakProgress);
        OilfieldStructures.Unit unit = pumpjack.getUnit();
        Level level = pumpjack.getLevel();
        state.formed = unit != null && level != null;
        if (unit == null || level == null) {
            built.remove(pumpjack);
            ChunkMeshes.remove(pumpjack);
            return;
        }
        BlockPos min = unit.min();
        state.offsetX = min.getX() - pumpjack.getBlockPos().getX();
        state.offsetY = min.getY() - pumpjack.getBlockPos().getY();
        state.offsetZ = min.getZ() - pumpjack.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(pumpjack);
        if (model == null || !model.unit.equals(unit)) {
            model = new Built(unit, ports(level, unit, PumpjackBlockEntity.frame(unit)));
            model.scanned = time;
            built.put(pumpjack, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<ModelMesh.Port> ports = ports(level, unit, model.frame);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                model.rebuild();
            }
        }
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(SHEET);
        int light = LightCoordsUtil.getLightCoords(level, unit.at(0, 1, OilfieldStructures.POST_U).relative(unit.back().getClockWise()));
        OilfieldStructures.Unit builtUnit = model.unit;
        java.util.function.BooleanSupplier stands = () -> builtUnit.equals(pumpjack.getUnit());
        MeshRendering.bake(pumpjack, MeshRendering.SOLID, min, ChunkSectionLayer.SOLID, model.solid, sheet, light, stands);
        MeshRendering.bake(pumpjack, MeshRendering.GLASS, min, ChunkSectionLayer.CUTOUT, model.glass, sheet, light, stands);
        state.sheet = sheet;
        state.light = light;
        state.beam = state.cranks = state.links = state.rest = EMPTY;
        double theta = Math.toRadians(pumpjack.advanceCrank(time + partialTicks));
        switch (LargeModels.detail(model.drawn, cameraPosition)) {
            case FULL -> {
                state.beam = model.beam;
                state.cranks = model.cranks;
                state.beamTurn = MeshRendering.turn(model.beamAxis, (float) Math.toDegrees(PumpjackLayout.beamAngle(theta)));
                state.crankTurn = MeshRendering.turn(model.crankAxis, (float) Math.toDegrees(theta));
                state.links = model.mesh.buildLinks(theta);
            }
            case SIMPLE -> state.rest = model.rest;
            case STILL -> {}
        }
    }

    // The ports on the pumpjack's outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, OilfieldStructures.Unit unit, ModelFrame frame) {
        List<ModelMesh.Port> ports = new ArrayList<>();
        BlockPos min = unit.min(), max = unit.max();
        for (BlockPos pos : unit.parts()) {
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
                BlockPos next = pos.relative(face);
                boolean inside = next.getX() >= min.getX() && next.getX() <= max.getX() && next.getY() >= min.getY() && next.getY() <= max.getY()
                        && next.getZ() >= min.getZ() && next.getZ() <= max.getZ();
                if (faces.get(face) == SideMode.NONE || inside) {
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
        if (state.beamTurn != null && state.crankTurn != null) {
            MeshRendering.submitMoving(collector, poseStack, solid, state.beam, sheet, light, state.beamTurn);
            MeshRendering.submitMoving(collector, poseStack, solid, state.cranks, sheet, light, state.crankTurn);
        }
        if (links.length > 0) {
            collector.submitCustomGeometry(poseStack, solid, (pose, buffer) -> emit(pose, buffer, links, sheet, light));
        }
        MeshRendering.submitStill(collector, poseStack, solid, state.rest, sheet, light);
        poseStack.popPose();
    }

    // Each face shaded as a block face turned its way is (top 1.0, north and south 0.8, east and west 0.6, bottom 0.5,
    // blended for slanted faces), with its normal straight up so the entity lighting adds nothing: the model shades like
    // the casing blocks round it instead of darker.
    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
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

    // Only the moving parts are drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity pumpjack) {
        Built model = built.get(pumpjack);
        return model != null && model.unit.equals(pumpjack.getUnit()) ? model.drawn : pumpjack.getRenderBox();
    }
}
