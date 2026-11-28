/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
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
import net.zagdrath.arcforge.block.fusion.FusionModuleBlock;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.fusion.TokamakLayout;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.TokamakMesh;

// Draws a formed Fusion Reactor (TokamakMesh, on textures/block/fusion/tokamak_model.png) from its controller: the
// cryostat with its viewports onto the plasma chamber, port flanges, the control desk and the nozzles of its ports and
// modules; its blocks draw nothing while formed. The model is built when the reactor forms and again when its glass,
// ports or modules change (checked every second). While the plasma burns it's drawn full-bright inside, a core in a
// halo, tinted for its temperature (the controller's synced glow, 1 to 15: a deep violet when it's cool, a brighter purple
// as it heats; a D-He3 plasma a bluer lilac) and brighter with it, its light on the viewports' ducts too. Its particles
// (motes and sparks) are FusionPlasmaParticles'.
//
// The see-through parts go in order: the glow and the plasma first, then the glass tint over them (see VesselRenderer).
public class FusionReactorRenderer implements StructureRenderer<FusionReactorBlockEntity, FusionReactorRenderer.State> {
    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fusion/tokamak_model");
    private static final int SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];
    private static final int TINT_ALPHA = 0x5A, PLASMA_ORDER = 1, TINT_ORDER = 2;
    // The halo's share of the core's strength.
    private static final float HALO = 0.55F;
    // The plasma's tint, cool and hot, for D-T and D-He3 {r, g, b} 0..1.
    private static final float[][] DT = { { 0.62F, 0.16F, 0.96F }, { 0.84F, 0.48F, 1.0F } };
    private static final float[][] DHE3 = { { 0.46F, 0.38F, 1.0F }, { 0.70F, 0.66F, 1.0F } };

    private final Map<FusionReactorBlockEntity, Built> built = new WeakHashMap<>();

    public FusionReactorRenderer(BlockEntityRendererProvider.Context context) {}

    // What the model is laid out from; equal layouts build the same model.
    private record Layout(TokamakStructure.Shape shape, List<TokamakMesh.Viewport> viewports, List<TokamakMesh.PortBlock> portBlocks, int[] controller,
            List<ModelMesh.Port> ports) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Layout l && shape.equals(l.shape) && viewports.equals(l.viewports) && portBlocks.equals(l.portBlocks)
                    && java.util.Arrays.equals(controller, l.controller) && ports.equals(l.ports);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shape, viewports, portBlocks, java.util.Arrays.hashCode(controller), ports);
        }
    }

    private static final class Built {
        Layout layout;
        float[][] parts;
        long scanned;

        Built(Layout layout, float[][] parts, long scanned) {
            this.layout = layout;
            this.parts = parts;
            this.scanned = scanned;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed, alive;
        public int offsetX, offsetY, offsetZ;
        public float[] solid = EMPTY, glass = EMPTY, glow = EMPTY, core = EMPTY, halo = EMPTY, tint = EMPTY;
        public int plasmaColor;
        public float plasmaLevel;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // The model's frame over the reactor's bounding box: x across the front, u back from it.
    public static ModelFrame frame(TokamakStructure.Shape shape) {
        TokamakLayout layout = shape.layout();
        return ModelFrame.toward(shape.front().getOpposite(), layout.width(), layout.height(), layout.width());
    }

    // The glass, Vessel Ports, modules and ports in the cryostat's wall, and the controller, in the model's frame.
    static Layout layout(Level level, FusionReactorBlockEntity reactor, TokamakStructure.Shape shape, ModelFrame frame) {
        TokamakLayout l = shape.layout();
        BlockPos min = shape.min(), axis = shape.axis();
        List<TokamakMesh.Viewport> viewports = new ArrayList<>();
        List<TokamakMesh.PortBlock> portBlocks = new ArrayList<>();
        List<BlockPos> walls = new ArrayList<>();
        Set<ModelMesh.Port> ports = new LinkedHashSet<>();
        int ro = l.outer();
        for (int dx = -ro; dx <= ro; dx++) {
            for (int dz = -ro; dz <= ro; dz++) {
                for (int dy = -(l.top() - 1); dy <= l.top() - 1; dy++) {
                    if (l.at(dx, dy, dz) != TokamakLayout.Part.CRYOSTAT_WALL) {
                        continue;
                    }
                    BlockPos pos = axis.offset(dx, dy, dz);
                    walls.add(pos);
                    BlockState state = level.getBlockState(pos);
                    int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
                    if (state.is(ModBlocks.REACTOR_GLASS.get())) {
                        viewports.add(new TokamakMesh.Viewport(block[0], block[1], block[2]));
                    } else if (state.is(ModBlocks.VESSEL_PORT.get())) {
                        portBlocks.add(new TokamakMesh.PortBlock(block[0], block[1], block[2]));
                        // A module on it gets a nozzle to the face it's on.
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            BlockPos next = pos.relative(side);
                            BlockState module = level.getBlockState(next);
                            if (!shape.contains(next) && module.getBlock() instanceof FusionModuleBlock && module.getValue(FusionModuleBlock.FACING) == side.getOpposite()) {
                                int[] n = frame.frameNormal(side.getStepX(), 0, side.getStepZ());
                                ports.add(new ModelMesh.Port(block[2], block[1], block[0], n[0], n[1], n[2]));
                            }
                        }
                    }
                }
            }
        }
        ports.addAll(MeshRendering.ports(level, walls, shape::contains, min, frame));
        BlockPos c = reactor.getBlockPos();
        int[] controller = frame.frameBlock(c.getX() - min.getX(), c.getY() - min.getY(), c.getZ() - min.getZ());
        return new Layout(shape, viewports, portBlocks, controller, List.copyOf(ports));
    }

    static float[][] build(ModelFrame frame, Layout layout) {
        TokamakStructure.Shape shape = layout.shape();
        return new TokamakMesh(frame, shape.radius(), shape.stellarator(), layout.viewports(), layout.portBlocks(), layout.controller())
                .buildStatic(layout.ports());
    }

    @Override
    public void extractRenderState(FusionReactorBlockEntity reactor, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(reactor, state, partialTicks, cameraPosition, breakProgress);
        TokamakStructure.Shape shape = reactor.getShape();
        Level level = reactor.getLevel();
        state.formed = shape != null && level != null;
        if (shape == null || level == null) {
            built.remove(reactor);
            return;
        }
        BlockPos min = shape.min();
        state.offsetX = min.getX() - reactor.getBlockPos().getX();
        state.offsetY = min.getY() - reactor.getBlockPos().getY();
        state.offsetZ = min.getZ() - reactor.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(reactor);
        if (model == null || !model.layout.shape().equals(shape) || Math.abs(time - model.scanned) >= SCAN_INTERVAL) {
            ModelFrame frame = frame(shape);
            Layout layout = layout(level, reactor, shape, frame);
            if (model == null || !layout.equals(model.layout)) {
                model = new Built(layout, build(frame, layout), time);
                built.put(reactor, model);
            } else {
                model.scanned = time;
            }
        }
        float[][] parts = model.parts;
        state.solid = parts[0];
        state.glass = parts[1];
        state.glow = parts[2];
        state.alive = reactor.isAlive() && reactor.getGlow() > 0;
        state.core = state.alive ? parts[3] : EMPTY;
        state.halo = state.alive ? parts[4] : EMPTY;
        state.tint = parts[5];
        float heat = Math.clamp((reactor.getGlow() - 1) / 14.0F, 0.0F, 1.0F);
        float[][] ramp = reactor.getFuelMode() == FusionReactorBlockEntity.FuelMode.DHE3 ? DHE3 : DT;
        int rgb = 0;
        for (int k = 0; k < 3; k++) {
            rgb = rgb << 8 | Math.round(255.0F * (ramp[0][k] + (ramp[1][k] - ramp[0][k]) * heat));
        }
        state.plasmaColor = rgb;
        state.plasmaLevel = 0.65F + 0.35F * heat;
        state.sheet = SteamBoilerArrayRenderer.sprite(SHEET);
        state.light = LightCoordsUtil.getLightCoords(level, shape.axis().above(shape.layout().top() + 1));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        int light = state.light;
        float[] glow = state.glow, core = state.core, halo = state.halo, tint = state.tint;
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), state.solid, sheet, light);
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), state.glass, sheet, light);
        if (glow.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> emitLit(pose, buffer, glow, sheet, 0xFFFFFF, 1.0F));
        }
        if (core.length > 0 || halo.length > 0) {
            int color = state.plasmaColor;
            float level = state.plasmaLevel;
            collector.order(PLASMA_ORDER).submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> {
                emitLit(pose, buffer, halo, sheet, color, level * HALO);
                emitLit(pose, buffer, core, sheet, color, level);
            });
        }
        if (tint.length > 0) {
            collector.order(TINT_ORDER).submitCustomGeometry(poseStack, RenderTypes.entityTranslucentCull(sheet.atlasLocation()),
                    (pose, buffer) -> emitTint(pose, buffer, tint, sheet, light));
        }
        poseStack.popPose();
    }

    // Full-bright in color (0xRRGGBB), at level (0..1) of its full strength.
    private static void emitLit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int color, float level) {
        int argb = Math.round(255.0F * Math.clamp(level, 0.0F, 1.0F)) << 24 | color;
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(argb)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // The glass tint, shaded as MeshRendering.emit does, at TINT_ALPHA.
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

    @Override
    public AABB getRenderBoundingBox(FusionReactorBlockEntity reactor) {
        return reactor.getRenderBox();
    }
}
