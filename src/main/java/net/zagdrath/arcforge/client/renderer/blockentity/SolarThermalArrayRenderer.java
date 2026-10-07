/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.multiblock.SolarThermalStructure;

// Draws the formed Solar Thermal Array's moving parts: the trough mirror and its receiver tube on the yoke
// at the top of the mast, turned to follow the sun (east in the morning, west in the evening on the
// north-south axis; straight up by day on the east-west one) and stowed face down at night and in
// thunderstorms, easing there at a few degrees a tick; the receiver glowing from cold steel through dull
// red to bright orange with its temperature (full-bright once hot); and the control panel in the base's lower
// bay on the controller's side. The tower's base and mast are baked block models.
public class SolarThermalArrayRenderer implements BlockEntityRenderer<SolarThermalArrayBlockEntity, SolarThermalArrayRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> MIRROR = new StandaloneModelKey<>(() -> Arcforge.MODID + ":solar_mirror");
    public static final StandaloneModelKey<QuadCollection> RECEIVER = new StandaloneModelKey<>(() -> Arcforge.MODID + ":solar_receiver");
    public static final StandaloneModelKey<QuadCollection> PANEL = new StandaloneModelKey<>(() -> Arcforge.MODID + ":solar_control_panel");
    public static final StandaloneModelKey<QuadCollection> PANEL_ON = new StandaloneModelKey<>(() -> Arcforge.MODID + ":solar_control_panel_on");
    public static final Identifier MIRROR_MODEL = model("mirror");
    public static final Identifier RECEIVER_MODEL = model("receiver");
    public static final Identifier PANEL_MODEL = model("control_panel");
    public static final Identifier PANEL_ON_MODEL = model("control_panel_on");

    // The trough's pivot, in pixels from the tower's minimum corner: the top of the yoke's uprights.
    private static final float PIVOT_X = 16.0F, PIVOT_Y = 54.0F, PIVOT_Z = 16.0F;
    // The panel: 8x9x2 px, its screen on +Z, centred in these model pixels.
    private static final float PANEL_CENTRE_X = 8.0F, PANEL_CENTRE_Y = 4.5F, PANEL_CENTRE_Z = 1.0F;
    // Where it sits in the base: its back against the core (8 px in from the tower's side), in the lower bay
    // (3..12 px up), in the half of the bay between the legs that's in the controller's own cell.
    private static final float CORE_NEAR = 8.0F, CORE_FAR = 24.0F, PANEL_Y = 7.5F, HALF_NEAR = 13.0F, HALF_FAR = 19.0F;
    // Receiver colour by temperature (°C): cold steel, then dull red to bright orange.
    private static final int[] GLOW_TEMPS = { 150, 250, 350, 450, 550 };
    private static final int[] GLOW_COLORS = { 0x6E757D, 0x5A1A0E, 0x9A2A0C, 0xE0561A, 0xFFB02E };

    private static Identifier model(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/solar_thermal_array/" + name);
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // From the controller to the tower's minimum corner, in blocks.
        public float minX, minY, minZ;
        public boolean northSouth;
        public float angle;
        public int receiverColor;
        public int receiverLight;
        public int troughLight;
        public Direction facing = Direction.NORTH;
        public float panelX, panelZ;
        public boolean lit;
    }

    public SolarThermalArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SolarThermalArrayBlockEntity array, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(array, state, partialTicks, cameraPosition, breakProgress);
        SolarThermalStructure.Tower tower = array.getTower();
        Level level = array.getLevel();
        state.formed = tower != null && level != null;
        if (!state.formed) {
            return;
        }
        BlockPos pos = array.getBlockPos();
        state.minX = tower.min().getX() - pos.getX();
        state.minY = tower.min().getY() - pos.getY();
        state.minZ = tower.min().getZ() - pos.getZ();
        state.northSouth = tower.northSouth();

        long dayTime = Math.floorMod(level.getDefaultClockTime(), (long) SolarModel.DAY_LENGTH);
        float limit = ArcforgeConfig.SOLAR_TRACKING_LIMIT.getAsInt();
        float target = array.isStowed() ? 180.0F
                : state.northSouth ? Mth.clamp((dayTime + partialTicks) / SolarModel.DAYLIGHT * 180.0F - 90.0F, -limit, limit)
                : 0.0F;
        state.angle = array.advancePanelAngle(level.getGameTime() + partialTicks, target, (float) ArcforgeConfig.SOLAR_PANEL_SPEED.getAsDouble());

        int temperature = array.getReceiverTemperature();
        state.receiverColor = glow(temperature) | 0xFF000000;
        state.troughLight = LevelRenderer.getLightCoords(level, tower.max().above());
        state.receiverLight = temperature >= ArcforgeConfig.SOLAR_GLOW_FULLBRIGHT.getAsInt() ? LightCoordsUtil.FULL_BRIGHT : state.troughLight;

        state.facing = array.getBlockState().getValue(SolarThermalArrayControllerBlock.FACING);
        state.lit = array.getBlockState().getValue(SolarThermalArrayControllerBlock.LIT);
        // Tower pixels of the panel's centre, across the tower (x, z).
        boolean positive = state.facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float along = positive ? CORE_FAR + PANEL_CENTRE_Z : CORE_NEAR - PANEL_CENTRE_Z;
        int cell = state.facing.getAxis() == Direction.Axis.Z ? pos.getX() - tower.min().getX() : pos.getZ() - tower.min().getZ();
        float across = cell == 0 ? HALF_NEAR : HALF_FAR;
        state.panelX = state.facing.getAxis() == Direction.Axis.Z ? across : along;
        state.panelZ = state.facing.getAxis() == Direction.Axis.Z ? along : across;
    }

    // The receiver's colour at this temperature.
    static int glow(int celsius) {
        if (celsius <= GLOW_TEMPS[0]) {
            return GLOW_COLORS[0];
        }
        for (int i = 1; i < GLOW_TEMPS.length; i++) {
            if (celsius <= GLOW_TEMPS[i]) {
                float t = (celsius - GLOW_TEMPS[i - 1]) / (float) (GLOW_TEMPS[i] - GLOW_TEMPS[i - 1]);
                return lerp(GLOW_COLORS[i - 1], GLOW_COLORS[i], t);
            }
        }
        return GLOW_COLORS[GLOW_COLORS.length - 1];
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed) {
            return;
        }
        var models = Minecraft.getInstance().getModelManager();
        QuadCollection mirror = models.getStandaloneModel(MIRROR);
        QuadCollection receiver = models.getStandaloneModel(RECEIVER);
        QuadCollection panel = models.getStandaloneModel(state.lit ? PANEL_ON : PANEL);
        RenderType cutout = RenderTypes.entityCutout(Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());

        if (mirror != null && receiver != null) {
            List<BakedQuad> mirrorQuads = mirror.getAll();
            List<BakedQuad> receiverQuads = receiver.getAll();
            poseStack.pushPose();
            poseStack.translate(state.minX + PIVOT_X / 16.0F, state.minY + PIVOT_Y / 16.0F, state.minZ + PIVOT_Z / 16.0F);
            if (!state.northSouth) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.angle));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, mirrorQuads, -1, state.troughLight));
            collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, receiverQuads, state.receiverColor, state.receiverLight));
            poseStack.popPose();
        }

        if (panel != null) {
            List<BakedQuad> panelQuads = panel.getAll();
            poseStack.pushPose();
            poseStack.translate(state.minX + state.panelX / 16.0F, state.minY + PANEL_Y / 16.0F, state.minZ + state.panelZ / 16.0F);
            // The screen faces +Z in the model: turn it to face the controller's way.
            poseStack.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
                case EAST -> 90.0F;
                case NORTH -> 180.0F;
                case WEST -> -90.0F;
                default -> 0.0F;
            }));
            poseStack.translate(-PANEL_CENTRE_X / 16.0F, -PANEL_CENTRE_Y / 16.0F, -PANEL_CENTRE_Z / 16.0F);
            collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, panelQuads, -1, state.lightCoords));
            poseStack.popPose();
        }
    }

    // The tower and a block round it, which the trough swings through.
    @Override
    public AABB getRenderBoundingBox(SolarThermalArrayBlockEntity array) {
        return array.getRenderBox();
    }
}
