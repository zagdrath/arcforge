/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
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
// lining, then the rotor along the core: the shaft through every block, compressor blades in the first third
// from the intake, the combustor can in the middle (still, its glow full-bright with the fuel burned and
// flickering a little), and turbine blades on to the exhaust. Blade sets are staggered 22.5° per block and turn
// at up to 60° a tick (see GasTurbineArrayBlockEntity.advanceAngle). The intake and exhaust caps are drawn over
// the two end faces.
public class GasTurbineArrayRenderer implements BlockEntityRenderer<GasTurbineArrayBlockEntity, GasTurbineArrayRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> SHAFT = key("gas_turbine_rotor_shaft");
    public static final StandaloneModelKey<QuadCollection> COMPRESSOR = key("gas_turbine_compressor_blades");
    public static final StandaloneModelKey<QuadCollection> TURBINE = key("gas_turbine_turbine_blades");
    public static final StandaloneModelKey<QuadCollection> COMBUSTOR = key("gas_turbine_combustor");
    public static final StandaloneModelKey<QuadCollection> COMBUSTOR_GLOW = key("gas_turbine_combustor_glow");
    public static final StandaloneModelKey<QuadCollection> INTAKE_CAP = key("gas_turbine_intake_cap");
    public static final StandaloneModelKey<QuadCollection> EXHAUST_CAP = key("gas_turbine_exhaust_cap");
    // Each key and the model it bakes (for ArcforgeClient.registerStandaloneModels).
    public static final List<StandaloneModelKey<QuadCollection>> KEYS = List.of(SHAFT, COMPRESSOR, TURBINE, COMBUSTOR, COMBUSTOR_GLOW, INTAKE_CAP, EXHAUST_CAP);
    public static final List<String> MODELS = List.of("rotor_shaft", "compressor_blades", "turbine_blades", "combustor", "combustor_glow", "intake_cap", "exhaust_cap");

    private static final float LINER_INSET = SteamBoilerArrayRenderer.LINER_INSET;
    private static final float STAGGER = 22.5F;
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
            state.light = LightCoordsUtil.getLightCoords(turbine.getLevel(), shell.min().offset(1, 1, 1).relative(shell.axis(), shell.length() / 2 - 1));
            state.intakeLight = endLight(turbine, shell, turbine.getIntakeEnd());
            state.exhaustLight = endLight(turbine, shell, turbine.getIntakeEnd().opposite());
        } else {
            state.light = state.intakeLight = state.exhaustLight = state.lightCoords;
        }
    }

    // The light just outside an end face (what the cap there is lit by).
    private static int endLight(GasTurbineArrayBlockEntity turbine, ShellStructure.Shell shell, Direction.AxisDirection end) {
        BlockPos outside = shell.endCenter(end).relative(Direction.fromAxisAndDirection(shell.axis(), end));
        return LightCoordsUtil.getLightCoords(turbine.getLevel(), outside);
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
        QuadCollection compressor = models.getStandaloneModel(COMPRESSOR);
        QuadCollection turbineBlades = models.getStandaloneModel(TURBINE);
        QuadCollection combustor = models.getStandaloneModel(COMBUSTOR);
        QuadCollection glow = models.getStandaloneModel(COMBUSTOR_GLOW);
        if (shaft == null || compressor == null || turbineBlades == null || combustor == null || glow == null) {
            return;
        }
        RenderType cutout = RenderTypes.entityCutout(liner.atlasLocation());
        List<BakedQuad> shaftQuads = shaft.getAll();
        int core = state.length - 2;
        for (int i = 0; i < state.length; i++) {
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

            if (i == 0 || i == state.length - 1) {
                continue;
            }
            int fromIntake = state.intakeNegative ? i - 1 : core - i;
            Section section = section(fromIntake, core);
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
            } else {
                List<BakedQuad> blades = (section == Section.COMPRESSOR ? compressor : turbineBlades).getAll();
                poseStack.pushPose();
                rotorPose(poseStack, state, i, state.angle + i * STAGGER);
                collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, blades, -1, state.light));
                poseStack.popPose();
            }
        }
        submitCap(state, poseStack, collector, models.getStandaloneModel(INTAKE_CAP), state.intakeNegative, state.intakeLight, cutout);
        submitCap(state, poseStack, collector, models.getStandaloneModel(EXHAUST_CAP), !state.intakeNegative, state.exhaustLight, cutout);
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
        poseStack.translate(
                state.axis == Direction.Axis.X ? i + 0.5 : 1.5,
                1.5,
                state.axis == Direction.Axis.Z ? i + 0.5 : 1.5);
        if (state.axis == Direction.Axis.X) {
            poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
        }
        poseStack.rotate(Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    @Override
    public AABB getRenderBoundingBox(GasTurbineArrayBlockEntity turbine) {
        return turbine.getRenderBox().inflate(0.05);
    }
}
