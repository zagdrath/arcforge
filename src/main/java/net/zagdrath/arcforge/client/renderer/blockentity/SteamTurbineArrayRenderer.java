/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.SteamGrade;

// Draws the inside of a formed Steam Turbine Array, seen through its windows: a ribbed lining on the inside
// of the walls' outer skin (only its inward faces, so the near wall never hides the far one; none behind
// glass, so opposite windows see through), then the rotor: the shaft
// through every block from bearing to generator (stopping just short of the end caps' faces), and a set of blades in each block between them, each
// set turned 22.5° further so they look staggered; and the steam around it, thicker the faster it flows. The rotor turns at up to one turn a second.
public class SteamTurbineArrayRenderer implements BlockEntityRenderer<SteamTurbineArrayBlockEntity, SteamTurbineArrayRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> ROTOR_SHAFT = new StandaloneModelKey<>(() -> Arcforge.MODID + ":rotor_shaft");
    public static final StandaloneModelKey<QuadCollection> ROTOR_BLADES = new StandaloneModelKey<>(() -> Arcforge.MODID + ":rotor_blades");
    public static final Identifier ROTOR_SHAFT_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/rotor_shaft");
    public static final Identifier ROTOR_BLADES_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/rotor_blades");
    private static final Identifier LINER = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/liner");
    // The lining is the inside of the structure's outer skin (see TiledBoxes.lining).
    private static final float LINER_INSET = 0.0F;
    // The steam fills the chamber just inside the lining, around the rotor.
    private static final float STEAM_INSET = 2.5F / 16.0F;
    // Steam is this see-through with steam in the tank but none flowing, and this thick at full flow.
    private static final float STEAM_IDLE_ALPHA = 0.1F, STEAM_FULL_ALPHA = 0.8F;
    // Share of the gap to the synced flow closed each frame.
    private static final float EASE = 0.05F;
    private static final float STAGGER = 22.5F;
    // In the end blocks the shaft stops this far inside the outer face, so it never lies on the end caps.
    private static final float SHAFT_END_INSET = 1.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public Direction.Axis axis = Direction.Axis.Z;
        public int length;
        public float sizeX, sizeY, sizeZ;
        // Offsets (from the minimum corner) of the Pressure Glass panes: no lining behind them.
        public final Set<BlockPos> windows = new HashSet<>();
        public float angle;
        public @Nullable TextureAtlasSprite liner;
        public @Nullable TextureAtlasSprite steamSprite;
        public int steamColor;
        public int light;
    }

    public SteamTurbineArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SteamTurbineArrayBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
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
        state.liner = SteamBoilerArrayRenderer.sprite(LINER);
        SteamBoilerArrayRenderer.findWindows(turbine.getLevel(), shell, state.windows);
        // The faster steam goes through, the thicker it looks.
        float flow = turbine.easeSteamDensity(EASE);
        SteamGrade grade = SteamGrade.of(turbine.getSteam().getResource(0));
        boolean hasSteam = turbine.getSteam().getAmount() > 0 && grade != null;
        float density = hasSteam || flow > 0.01F ? STEAM_IDLE_ALPHA + (STEAM_FULL_ALPHA - STEAM_IDLE_ALPHA) * flow : 0.0F;
        state.steamSprite = null;
        if (density > 0.0F) {
            SteamGrade shown = grade != null ? grade : SteamGrade.STEAM;
            state.steamSprite = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(shown.fluid().defaultFluidState()).stillMaterial().sprite();
            state.steamColor = (shown.tint() & 0x00FFFFFF) | ((int) (255 * density) << 24);
        }
        double time = turbine.getLevel() != null ? turbine.getLevel().getGameTime() + partialTicks : 0.0;
        state.angle = turbine.advanceAngle(time);
        state.light = turbine.getLevel() != null
                ? LightCoordsUtil.getLightCoords(turbine.getLevel(), shell.min().offset(1, 1, 1).relative(shell.axis(), shell.length() / 2 - 1))
                : state.lightCoords;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.liner == null) {
            return;
        }
        TextureAtlasSprite liner = state.liner;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(liner.atlasLocation()),
                (pose, buffer) -> TiledBoxes.lining(pose, buffer, liner, -1, state.light, LINER_INSET, state.sizeX, state.sizeY, state.sizeZ, state.windows::contains));

        var models = Minecraft.getInstance().getModelManager();
        QuadCollection shaft = models.getStandaloneModel(ROTOR_SHAFT);
        QuadCollection blades = models.getStandaloneModel(ROTOR_BLADES);
        if (shaft == null || blades == null) {
            return;
        }
        List<BakedQuad> shaftQuads = shaft.getAll();
        List<BakedQuad> bladeQuads = blades.getAll();
        RenderType cutout = RenderTypes.entityCutout(liner.atlasLocation());
        for (int i = 0; i < state.length; i++) {
            // The shaft turns as one piece, so every block's segment is at the same angle.
            poseStack.pushPose();
            rotorPose(poseStack, state, i, state.angle);
            // The model runs along its Z from the bearing end (block 0) toward the generator end.
            if (i == 0) {
                poseStack.translate(0.0F, 0.0F, SHAFT_END_INSET);
                poseStack.scale(1.0F, 1.0F, 1.0F - SHAFT_END_INSET);
            } else if (i == state.length - 1) {
                poseStack.scale(1.0F, 1.0F, 1.0F - SHAFT_END_INSET);
            }
            collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, shaftQuads, -1, state.light));
            poseStack.popPose();

            // Each blade set is turned a little further than the last, so they look staggered.
            if (i > 0 && i < state.length - 1) {
                poseStack.pushPose();
                rotorPose(poseStack, state, i, state.angle + i * STAGGER);
                collector.submitCustomGeometry(poseStack, cutout, (pose, buffer) -> TiledBoxes.quads(pose, buffer, bladeQuads, -1, state.light));
                poseStack.popPose();
            }
        }
        submitSteam(state, poseStack, collector);
    }

    // Steam filling the chamber around the rotor, drawn after it so the rotor shows through.
    private static void submitSteam(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        TextureAtlasSprite steam = state.steamSprite;
        if (steam == null) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(steam.atlasLocation()),
                (pose, buffer) -> TiledBoxes.box(pose, buffer, steam, state.steamColor, state.light,
                        STEAM_INSET, STEAM_INSET, STEAM_INSET, state.sizeX - STEAM_INSET, state.sizeY - STEAM_INSET, state.sizeZ - STEAM_INSET, false));
    }

    // Places a rotor model (built along Z, one block long) in block i along the axis, in the centre of
    // the cross-section, turned to the given angle about the axis.
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

    // The whole structure, so it isn't culled when the master corner is off screen.
    @Override
    public AABB getRenderBoundingBox(SteamTurbineArrayBlockEntity turbine) {
        return turbine.getRenderBox();
    }
}
