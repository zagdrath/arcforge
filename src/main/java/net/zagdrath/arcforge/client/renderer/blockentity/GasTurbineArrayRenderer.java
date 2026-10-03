/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;
import java.util.Set;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// Draws the inside of a formed Gas Turbine Array, seen through its windows: the Steam Turbine Array's ribbed
// lining, then the rotor along the core. The compressor fills the first third from the intake and the turbine the
// rest past the combustor can in the middle (still, its glow full-bright with the fuel burned and flickering a
// little); the shaft runs through the combustor and the end blocks. Compressor and turbine blocks each hold a
// rotor drum and several stages of thin blades: 4 stages of 36 in a compressor block, 3 of 28 in a turbine block.
// The compressor narrows toward the combustor and the turbine widens toward the exhaust, and neighbouring stages
// are offset by half a blade. The rotor turns at up to 60° a tick (see GasTurbineArrayBlockEntity.advanceAngle).
// The intake and exhaust caps are drawn over the two end faces.
public class GasTurbineArrayRenderer implements BlockEntityRenderer<GasTurbineArrayBlockEntity, GasTurbineArrayRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> SHAFT = key("gas_turbine_rotor_shaft");
    public static final StandaloneModelKey<QuadCollection> COMPRESSOR_BLADE = key("gas_turbine_compressor_blade");
    public static final StandaloneModelKey<QuadCollection> TURBINE_BLADE = key("gas_turbine_turbine_blade");
    public static final StandaloneModelKey<QuadCollection> ROTOR_DRUM = key("gas_turbine_rotor_drum");
    public static final StandaloneModelKey<QuadCollection> COMBUSTOR = key("gas_turbine_combustor");
    public static final StandaloneModelKey<QuadCollection> COMBUSTOR_GLOW = key("gas_turbine_combustor_glow");
    public static final StandaloneModelKey<QuadCollection> INTAKE_CAP = key("gas_turbine_intake_cap");
    public static final StandaloneModelKey<QuadCollection> EXHAUST_CAP = key("gas_turbine_exhaust_cap");
    // The round bearing housing that holds each end of the shaft against the end wall (modelled for the -Z end).
    public static final StandaloneModelKey<QuadCollection> BEARING = key("gas_turbine_bearing");
    // Each key and the model it bakes (for ArcforgeClient.registerStandaloneModels).
    public static final List<StandaloneModelKey<QuadCollection>> KEYS = List.of(SHAFT, COMPRESSOR_BLADE, TURBINE_BLADE, ROTOR_DRUM, COMBUSTOR,
            COMBUSTOR_GLOW, INTAKE_CAP, EXHAUST_CAP, BEARING);
    public static final List<String> MODELS = List.of("rotor_shaft", "compressor_blade", "turbine_blade", "rotor_drum", "combustor", "combustor_glow",
            "intake_cap", "exhaust_cap", "bearing");

    private static final float LINER_INSET = SteamBoilerArrayRenderer.LINER_INSET;
    // A blade model's tip radius, in px.
    private static final float MODEL_TIP = 16.0F;

    // The blading of a compressor or turbine block: its stages (centres in px along the axis), blades per stage, and
    // the tip radius (px) of its first and last stage counted from the intake.
    private enum Blading {
        COMPRESSOR(new float[] { 2, 6, 10, 14 }, 36, 17.0F, 12.0F),
        TURBINE(new float[] { 3, 8, 13 }, 28, 13.0F, 20.0F);

        final float[] centres;
        final int blades;
        final float firstTip, lastTip;
        // One ring of blades baked from the blade model, and the model it was baked from (a reload replaces it).
        @Nullable QuadCollection bakedFrom;
        float[] ring = new float[0];

        Blading(float[] centres, int blades, float firstTip, float lastTip) {
            this.centres = centres;
            this.blades = blades;
            this.firstTip = firstTip;
            this.lastTip = lastTip;
        }

        float[] ring(QuadCollection blade) {
            if (bakedFrom != blade) {
                ring = bakeRing(blade, blades);
                bakedFrom = blade;
            }
            return ring;
        }

        // The tip radius (px) of stage `stage` of a section `stages` long, counted from the intake.
        float tip(int stage, int stages) {
            return Mth.lerp(stages > 1 ? stage / (float) (stages - 1) : 0.0F, firstTip, lastTip);
        }
    }
    private static final float SHAFT_END_INSET = 1.5F / 16.0F;
    private static final float FLICKER = 0.1F;

    public enum Section { COMPRESSOR, COMBUSTOR, TURBINE }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public Direction.Axis axis = Direction.Axis.Z;
        public int length;
        public float sizeX, sizeY, sizeZ;
        public Set<Long> windows = Set.of();
        public float angle;
        // Whether the intake is the negative end.
        public boolean intakeNegative = true;
        public float glow;
        public @Nullable TextureAtlasSprite liner, jamb;
        public int light;
        public int intakeLight, exhaustLight;
    }

    public GasTurbineArrayRenderer(BlockEntityRendererProvider.Context context) {}

    private static StandaloneModelKey<QuadCollection> key(String name) {
        return new StandaloneModelKey<>(() -> Arcforge.MODID + ":" + name);
    }

    public static Identifier model(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/gas_turbine_array/" + name);
    }

    // What sits in core block j (0 = next to the intake) of a core c blocks long: compressor blades in the first
    // ceil(c / 3), then the combustor (one block, or two when the rest divides evenly), then turbine blades.
    public static Section section(int j, int core) {
        int compressor = (core + 2) / 3;
        int combustor = Math.max(1, core - 2 * compressor);
        return j < compressor ? Section.COMPRESSOR : j < compressor + combustor ? Section.COMBUSTOR : Section.TURBINE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GasTurbineArrayBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(turbine, state, partialTicks, cameraPosition, breakProgress);
        ShellStructure.Shell shell = turbine.getShell();
        state.formed = turbine.isMaster() && shell != null;
        if (!state.formed) {
            return;
        }
        state.axis = shell.axis();
        state.length = shell.length();
        state.sizeX = shell.size(Direction.Axis.X);
        state.sizeY = shell.size(Direction.Axis.Y);
        state.sizeZ = shell.size(Direction.Axis.Z);
        state.liner = SteamBoilerArrayRenderer.sprite(SteamBoilerArrayRenderer.LINER);
        state.jamb = SteamBoilerArrayRenderer.sprite(SteamBoilerArrayRenderer.JAMB);
        state.windows = turbine.windowQuads(found -> SteamBoilerArrayRenderer.findWindowQuads(turbine.getLevel(), found));
        state.intakeNegative = turbine.getIntakeEnd() == Direction.AxisDirection.NEGATIVE;
        double time = turbine.getLevel() != null ? turbine.getLevel().getGameTime() + partialTicks : 0.0;
        state.angle = turbine.advanceAngle(time);
        // The fuel burned, held back while the rotor spools (load x spun is at most the rotor's share of full speed).
        float speed = Mth.clamp(turbine.getSyncedRpm() / (float) GasTurbineArrayBlockEntity.maxRpm(), 0.0F, 1.0F);
        float glow = Math.min(turbine.getSyncedLoad(), speed);
        long tick = turbine.getLevel() != null ? turbine.getLevel().getGameTime() : 0L;
        float noise = (Mth.murmurHash3Mixer((int) tick * 31 + turbine.getBlockPos().hashCode()) & 0xFFFF) / 65535.0F;
        state.glow = Mth.clamp(glow * (1.0F - FLICKER + 2.0F * FLICKER * noise), 0.0F, 1.0F);
        if (turbine.getLevel() != null) {
            state.light = LightCoordsUtil.getLightCoords(turbine.getLevel(), shell.centre());
            state.intakeLight = endLight(turbine, shell, turbine.getIntakeEnd());
            state.exhaustLight = endLight(turbine, shell, turbine.getIntakeEnd().opposite());
        } else {
            state.light = state.intakeLight = state.exhaustLight = state.lightCoords;
        }
    }

    // The light just outside an end face (what the cap there is lit by): the brightest of the 3x3 blocks in front of
    // its middle, so a solid block against the centre (a conduit's meter, say) doesn't black the whole cap out.
    private static int endLight(GasTurbineArrayBlockEntity turbine, ShellStructure.Shell shell, Direction.AxisDirection end) {
        Direction out = Direction.fromAxisAndDirection(shell.axis(), end);
        BlockPos outside = shell.endCenter(end).relative(out);
        // One block either side of the middle across the face, none along the axis.
        int dx = out.getAxis() == Direction.Axis.X ? 0 : 1;
        int dy = out.getAxis() == Direction.Axis.Y ? 0 : 1;
        int dz = out.getAxis() == Direction.Axis.Z ? 0 : 1;
        int light = 0;
        for (BlockPos pos : BlockPos.betweenClosed(outside.offset(-dx, -dy, -dz), outside.offset(dx, dy, dz))) {
            light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(turbine.getLevel(), pos));
        }
        return light;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.liner == null || state.jamb == null) {
            return;
        }
        TextureAtlasSprite liner = state.liner;
        TextureAtlasSprite jamb = state.jamb;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(liner.atlasLocation()),
                (pose, buffer) -> TiledBoxes.lining(pose, buffer, liner, jamb, -1, state.light, LINER_INSET, state.sizeX, state.sizeY, state.sizeZ, state.windows));

        var models = Minecraft.getInstance().getModelManager();
        QuadCollection shaft = models.getStandaloneModel(SHAFT);
        QuadCollection compressorBlade = models.getStandaloneModel(COMPRESSOR_BLADE);
        QuadCollection turbineBlade = models.getStandaloneModel(TURBINE_BLADE);
        QuadCollection drum = models.getStandaloneModel(ROTOR_DRUM);
        QuadCollection combustor = models.getStandaloneModel(COMBUSTOR);
        QuadCollection glow = models.getStandaloneModel(COMBUSTOR_GLOW);
        if (shaft == null || compressorBlade == null || turbineBlade == null || drum == null || combustor == null || glow == null) {
            return;
        }
        RenderType cutout = RenderTypes.entityCutout(liner.atlasLocation());
        List<BakedQuad> shaftQuads = shaft.getAll();
        List<BakedQuad> drumQuads = drum.getAll();
        int core = state.length - 2;
        int compressorBlocks = (core + 2) / 3;
        int combustorBlocks = Math.max(1, core - 2 * compressorBlocks);
        int turbineBlocks = core - compressorBlocks - combustorBlocks;
        for (int i = 0; i < state.length; i++) {
            int fromIntake = state.intakeNegative ? i - 1 : core - i;
            Section section = i == 0 || i == state.length - 1 ? null : section(fromIntake, core);
            if (section == null || section == Section.COMBUSTOR) {
                // The bare shaft: through the end blocks and the combustor.
                poseStack.pushPose();
                rotorPose(poseStack, state, i, state.angle);
                if (i == 0) {
                    poseStack.translate(0.0F, 0.0F, SHAFT_END_INSET);
                    poseStack.scale(1.0F, 1.0F, 1.0F - SHAFT_END_INSET);
                } else if (i == state.length - 1) {
                    poseStack.scale(1.0F, 1.0F, 1.0F - SHAFT_END_INSET);
                }
                collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, shaftQuads, -1, state.light));
                poseStack.popPose();
            }
            if (section == null) {
                continue;
            }
            if (section == Section.COMBUSTOR) {
                poseStack.pushPose();
                rotorPose(poseStack, state, i, 0.0F);
                List<BakedQuad> can = combustor.getAll();
                collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, can, -1, state.light));
                if (state.glow > 0.01F) {
                    int level = Math.round(255 * state.glow);
                    int color = ARGB.color(level, level, level, level);
                    List<BakedQuad> flame = glow.getAll();
                    collector.submitCustomGeometry(poseStack, RenderTypes.eyes(liner.atlasLocation()),
                            (pose, buffer) -> TiledBoxes.quads(pose, buffer, flame, color, LightCoordsUtil.FULL_BRIGHT));
                }
                poseStack.popPose();
                continue;
            }
            boolean compressor = section == Section.COMPRESSOR;
            Blading blading = compressor ? Blading.COMPRESSOR : Blading.TURBINE;
            float[] ring = blading.ring(compressor ? compressorBlade : turbineBlade);
            // This block's place in its section, from the intake, and the section's stage count.
            int block = compressor ? fromIntake : fromIntake - compressorBlocks - combustorBlocks;
            int perBlock = blading.centres.length;
            int stages = (compressor ? compressorBlocks : turbineBlocks) * perBlock;
            float scaleSum = 0.0F;
            for (int k = 0; k < perBlock; k++) {
                // Stage k is the k-th along the axis; counted from the intake, reversed when the intake is at the far end.
                int stage = block * perBlock + (state.intakeNegative ? k : perBlock - 1 - k);
                float scale = blading.tip(stage, stages) / MODEL_TIP;
                scaleSum += scale;
                poseStack.pushPose();
                axisPose(poseStack, state, i, blading.centres[k] / 16.0F - 0.5F);
                poseStack.rotate(Axis.ZP.rotationDegrees(state.angle + (stage % 2) * 180.0F / blading.blades));
                poseStack.scale(scale, scale, 1.0F);
                collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.vertices(pose, buffer, ring, -1, state.light));
                poseStack.popPose();
            }
            // The drum, sized to the stages on it so the blade roots sit inside it.
            float drumScale = scaleSum / perBlock;
            poseStack.pushPose();
            axisPose(poseStack, state, i, 0.0F);
            poseStack.rotate(Axis.ZP.rotationDegrees(state.angle));
            poseStack.scale(drumScale, drumScale, 1.0F);
            poseStack.translate(-0.5, -0.5, -0.5);
            collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, drumQuads, -1, state.light));
            poseStack.popPose();
        }
        submitBearings(state, poseStack, collector, models.getStandaloneModel(BEARING), cutout);
        submitCap(state, poseStack, collector, models.getStandaloneModel(INTAKE_CAP), state.intakeNegative, state.intakeLight, cutout);
        submitCap(state, poseStack, collector, models.getStandaloneModel(EXHAUST_CAP), !state.intakeNegative, state.exhaustLight, cutout);
    }

    // The bearing housings: static, in the first and last blocks along the axis, against the end walls. The model is
    // built for the -Z end; the far end's copy is turned 180° about Y so its flange sits on that wall.
    private static void submitBearings(State state, PoseStack poseStack, SubmitNodeCollector collector, @Nullable QuadCollection bearing,
            RenderType renderType) {
        if (bearing == null) {
            return;
        }
        List<BakedQuad> quads = bearing.getAll();
        for (int i : new int[] { 0, state.length - 1 }) {
            poseStack.pushPose();
            rotorPose(poseStack, state, i, 0.0F);
            if (i != 0) {
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
                poseStack.translate(-0.5, -0.5, -0.5);
            }
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> TiledBoxes.quads(pose, buffer, quads, -1, state.light));
            poseStack.popPose();
        }
    }

    // A cap over the middle block of one end face, its model's north face turned to face out along the axis.
    private static void submitCap(State state, PoseStack poseStack, SubmitNodeCollector collector, @Nullable QuadCollection cap, boolean negative,
            int light, RenderType renderType) {
        if (cap == null) {
            return;
        }
        List<BakedQuad> quads = cap.getAll();
        int along = negative ? 0 : state.length - 1;
        float yaw = state.axis == Direction.Axis.X ? (negative ? 90.0F : -90.0F) : (negative ? 0.0F : 180.0F);
        poseStack.pushPose();
        poseStack.translate(
                state.axis == Direction.Axis.X ? along + 0.5 : 1.5,
                1.5,
                state.axis == Direction.Axis.Z ? along + 0.5 : 1.5);
        poseStack.rotate(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-0.5, -0.5, -0.5);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> TiledBoxes.quads(pose, buffer, quads, -1, light));
        poseStack.popPose();
    }

    // Places a rotor model (built along Z, one block long) in block i along the axis, in the centre of the
    // cross-section, turned to the given angle about the axis.
    private static void rotorPose(PoseStack poseStack, State state, int i, float angle) {
        axisPose(poseStack, state, i, 0.0F);
        poseStack.rotate(Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    // Moves to the axis at `offset` blocks along it from the centre of block i, with local Z along the axis.
    private static void axisPose(PoseStack poseStack, State state, int i, float offset) {
        poseStack.translate(
                state.axis == Direction.Axis.X ? i + 0.5 : 1.5,
                1.5,
                state.axis == Direction.Axis.Z ? i + 0.5 : 1.5);
        if (state.axis == Direction.Axis.X) {
            poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
        }
        poseStack.translate(0.0F, 0.0F, offset);
    }

    // One ring of `count` blades from a blade model (one blade pointing along +X, centred on the block), baked
    // once into vertices centred on the axis: x, y, z, u, v, nx, ny, nz each (for TiledBoxes.vertices).
    private static float[] bakeRing(QuadCollection blade, int count) {
        List<BakedQuad> quads = blade.getAll();
        float[] out = new float[quads.size() * count * BakedQuad.VERTEX_COUNT * 8];
        int n = 0;
        for (int b = 0; b < count; b++) {
            double radians = Math.toRadians(360.0 * b / count);
            float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
            for (BakedQuad quad : quads) {
                var normal = quad.direction().getUnitVec3i();
                float nx = normal.getX() * cos - normal.getY() * sin;
                float ny = normal.getX() * sin + normal.getY() * cos;
                for (int v = 0; v < BakedQuad.VERTEX_COUNT; v++) {
                    Vector3fc position = quad.position(v);
                    float x = position.x() - 0.5F, y = position.y() - 0.5F;
                    long uv = quad.packedUV(v);
                    out[n++] = x * cos - y * sin;
                    out[n++] = x * sin + y * cos;
                    out[n++] = position.z() - 0.5F;
                    out[n++] = UVPair.unpackU(uv);
                    out[n++] = UVPair.unpackV(uv);
                    out[n++] = nx;
                    out[n++] = ny;
                    out[n++] = normal.getZ();
                }
            }
        }
        return out;
    }

    @Override
    public AABB getRenderBoundingBox(GasTurbineArrayBlockEntity turbine) {
        return turbine.getRenderBox().inflate(0.05);
    }
}
