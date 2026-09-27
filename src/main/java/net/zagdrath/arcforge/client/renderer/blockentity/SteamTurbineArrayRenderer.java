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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// Draws the inside of a formed Steam Turbine Array, seen through its windows: a ribbed lining just inside
// the walls (only its inward faces, so the near wall never hides the far one), then the rotor: the shaft
// through every block from bearing to generator, and a set of blades in each block between them, each
// set turned 22.5° further so they look staggered. The rotor turns at up to one turn a second.
public class SteamTurbineArrayRenderer implements BlockEntityRenderer<SteamTurbineArrayBlockEntity, SteamTurbineArrayRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> ROTOR_SHAFT = new StandaloneModelKey<>(() -> Arcforge.MODID + ":rotor_shaft");
    public static final StandaloneModelKey<QuadCollection> ROTOR_BLADES = new StandaloneModelKey<>(() -> Arcforge.MODID + ":rotor_blades");
    public static final Identifier ROTOR_SHAFT_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/rotor_shaft");
    public static final Identifier ROTOR_BLADES_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/rotor_blades");
    private static final Identifier LINER = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/liner");
    private static final float LINER_INSET = 2.0F / 16.0F;
    private static final float STAGGER = 22.5F;

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public Direction.Axis axis = Direction.Axis.Z;
        public int length;
        public float sizeX, sizeY, sizeZ;
        public float angle;
        public @Nullable TextureAtlasSprite liner;
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
                (pose, buffer) -> TiledBoxes.box(pose, buffer, liner, -1, state.light,
                        LINER_INSET, LINER_INSET, LINER_INSET, state.sizeX - LINER_INSET, state.sizeY - LINER_INSET, state.sizeZ - LINER_INSET, true));

        var models = Minecraft.getInstance().getModelManager();
        QuadCollection shaft = models.getStandaloneModel(ROTOR_SHAFT);
        QuadCollection blades = models.getStandaloneModel(ROTOR_BLADES);
        if (shaft == null || blades == null) {
            return;
        }
        List<BakedQuad> shaftQuads = shaft.getAll();
        List<BakedQuad> bladeQuads = blades.getAll();
        for (int i = 0; i < state.length; i++) {
            poseStack.pushPose();
            // To the middle of block i along the axis, in the centre of the cross-section.
            poseStack.translate(
                    state.axis == Direction.Axis.X ? i + 0.5 : 1.5,
                    1.5,
                    state.axis == Direction.Axis.Z ? i + 0.5 : 1.5);
            // The models are built along Z.
            if (state.axis == Direction.Axis.X) {
                poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
            }
            poseStack.rotate(Axis.ZP.rotationDegrees(state.angle + i * STAGGER));
            poseStack.translate(-0.5, -0.5, -0.5);
            boolean bladeSet = i > 0 && i < state.length - 1;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(liner.atlasLocation()), (pose, buffer) -> {
                TiledBoxes.quads(pose, buffer, shaftQuads, -1, state.light);
                if (bladeSet) {
                    TiledBoxes.quads(pose, buffer, bladeQuads, -1, state.light);
                }
            });
            poseStack.popPose();
        }
    }

    // The whole structure, so it isn't culled when the master corner is off screen.
    @Override
    public AABB getRenderBoundingBox(SteamTurbineArrayBlockEntity turbine) {
        return turbine.getRenderBox();
    }
}
