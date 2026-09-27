/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.SteamGrade;

// Draws the inside of a formed Steam Boiler Array, seen through its windows (the casings draw only the
// outside of the structure): an opaque dark drum just inside the walls, then water rising from the floor
// (a full tank fills the lower 65%), and steam gathering under the roof and filling the space above the
// water as it builds up, thicker the more there is. Levels ease toward the synced ones.
public class SteamBoilerArrayRenderer implements BlockEntityRenderer<SteamBoilerArrayBlockEntity, SteamBoilerArrayRenderer.State> {
    private static final float VOLUME_INSET = 1.0F / 16.0F, DRUM_INSET = 2.0F / 16.0F;
    private static final int DRUM_COLOR = 0xFF1C1F22;
    // Share of the gap to the synced level closed each frame.
    private static final float EASE = 0.08F;
    // A full water tank fills this share of the drum; the rest is headspace for the steam.
    private static final float WATER_SHARE = 0.65F;
    // Steam is this see-through when there is only a wisp of it, and this thick when the tank is full
    // (a little thicker again while it boils).
    private static final float STEAM_MIN_ALPHA = 0.2F, STEAM_FULL_ALPHA = 0.75F, BOILING_ALPHA = 0.1F;
    private static final Identifier DRUM = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_boiler_array/formed_base");

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public float sizeX, sizeY, sizeZ;
        public float water, steam;
        public @Nullable TextureAtlasSprite waterSprite, steamSprite, drumSprite;
        public int waterColor, steamColor;
        public int light;
    }

    public SteamBoilerArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SteamBoilerArrayBlockEntity boiler, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(boiler, state, partialTicks, cameraPosition, breakProgress);
        ShellStructure.Shell shell = boiler.getShell();
        state.formed = boiler.isMaster() && shell != null;
        if (!state.formed) {
            return;
        }
        state.sizeX = shell.size(Direction.Axis.X);
        state.sizeY = shell.size(Direction.Axis.Y);
        state.sizeZ = shell.size(Direction.Axis.Z);
        state.water = boiler.easeWater(fill(boiler.getWater().getAmount(), boiler.getWater().getCapacity()), EASE);
        state.steam = boiler.easeSteam(fill(boiler.getSteam().getAmount(), boiler.getSteam().getCapacity()), EASE);
        state.drumSprite = sprite(DRUM);

        Fluid waterFluid = boiler.getWater().getAmount() > 0 ? boiler.getWater().getResource(0).getFluid() : Fluids.WATER;
        var waterModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(waterFluid.defaultFluidState());
        state.waterSprite = waterModel.stillMaterial().sprite();
        state.waterColor = (waterModel.fluidTintSource() != null ? waterModel.fluidTintSource().colorAsStack(new FluidStack(waterFluid, 1)) : -1) | 0xFF000000;

        SteamGrade grade = SteamGrade.of(boiler.getSteam().getResource(0));
        Fluid steamFluid = (grade != null ? grade : SteamGrade.STEAM).fluid();
        var steamModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(steamFluid.defaultFluidState());
        state.steamSprite = steamModel.stillMaterial().sprite();
        float density = STEAM_MIN_ALPHA + (STEAM_FULL_ALPHA - STEAM_MIN_ALPHA) * state.steam + (boiler.isBoilingOnClient() ? BOILING_ALPHA : 0.0F);
        int alpha = (int) (255 * Math.min(0.9F, density));
        state.steamColor = ((grade != null ? grade : SteamGrade.STEAM).tint() & 0x00FFFFFF) | (alpha << 24);

        // Lit by the middle of the structure, not by the corner the master sits in.
        state.light = boiler.getLevel() != null
                ? LightCoordsUtil.getLightCoords(boiler.getLevel(), shell.min().offset(1, shell.length() / 2, 1))
                : state.lightCoords;
    }

    private static float fill(int amount, int capacity) {
        return capacity > 0 ? Math.min(1.0F, amount / (float) capacity) : 0.0F;
    }

    static TextureAtlasSprite sprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.drumSprite == null || state.waterSprite == null || state.steamSprite == null) {
            return;
        }
        TextureAtlasSprite drum = state.drumSprite;
        TextureAtlasSprite water = state.waterSprite;
        TextureAtlasSprite steam = state.steamSprite;
        float x0 = VOLUME_INSET, y0 = VOLUME_INSET, z0 = VOLUME_INSET;
        float x1 = state.sizeX - VOLUME_INSET, y1 = state.sizeY - VOLUME_INSET, z1 = state.sizeZ - VOLUME_INSET;
        float waterTop = y0 + (y1 - y0) * WATER_SHARE * state.water;
        // Steam gathers under the roof and fills the headspace down toward the water as it builds up.
        float steamBottom = y1 - (y1 - waterTop) * state.steam;

        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(drum.atlasLocation()),
                (pose, buffer) -> TiledBoxes.box(pose, buffer, drum, DRUM_COLOR, state.light,
                        DRUM_INSET, DRUM_INSET, DRUM_INSET, state.sizeX - DRUM_INSET, state.sizeY - DRUM_INSET, state.sizeZ - DRUM_INSET, false));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(water.atlasLocation()), (pose, buffer) -> {
            TiledBoxes.box(pose, buffer, water, state.waterColor, state.light, x0, y0, z0, x1, waterTop, z1, false);
            TiledBoxes.box(pose, buffer, steam, state.steamColor, state.light, x0, steamBottom, z0, x1, y1, z1, false);
        });
    }

    // The whole structure, so it isn't culled when the master corner is off screen.
    @Override
    public AABB getRenderBoundingBox(SteamBoilerArrayBlockEntity boiler) {
        return boiler.getRenderBox();
    }
}
