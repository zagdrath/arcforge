/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.client.model.WindowQuadrants;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.SteamGrade;

import java.util.HashSet;
import java.util.Set;

// Draws the inside of a formed Steam Boiler Array, seen through its windows (the casings draw only the
// outside of the structure, so the inside of that skin is drawn here too, just inside it and cut away
// behind the windows; see TiledBoxes.lining): an opaque dark drum just inside the walls, then water rising from the floor
// (a full tank fills the lower 65%), and steam gathering under the roof and filling the space above the
// water as it builds up, thicker the more there is. Levels ease toward the synced ones.
public class SteamBoilerArrayRenderer implements BlockEntityRenderer<SteamBoilerArrayBlockEntity, SteamBoilerArrayRenderer.State> {
    // Lining, then the water and steam, then the drum: each a little further in, so no two share a plane.
    static final float LINER_INSET = 1.0F / 16.0F;
    private static final float VOLUME_INSET = 1.5F / 16.0F, DRUM_INSET = 2.0F / 16.0F;
    private static final int DRUM_COLOR = 0xFF1C1F22;
    // Share of the gap to the synced level closed each frame.
    private static final float EASE = 0.08F;
    // A full water tank fills this share of the drum; the rest is headspace for the steam.
    private static final float WATER_SHARE = 0.65F;
    // Steam is this see-through when there is only a wisp of it, and this thick when the tank is full
    // (a little thicker again while it boils).
    private static final float STEAM_MIN_ALPHA = 0.2F, STEAM_FULL_ALPHA = 0.75F, BOILING_ALPHA = 0.1F;
    private static final Identifier DRUM = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_boiler_array/formed_base");
    static final Identifier LINER = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/ctm/shell_liner");
    static final Identifier JAMB = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/ctm/shell_jamb");

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // The lining tiles behind windows (see TiledBoxes.windowKey), which are left out.
        public Set<Long> windows = Set.of();
        public float sizeX, sizeY, sizeZ;
        public float water, steam;
        public @Nullable TextureAtlasSprite waterSprite, steamSprite, drumSprite, liner, jamb;
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
        state.liner = sprite(LINER);
        state.jamb = sprite(JAMB);
        state.windows = boiler.windowQuads(found -> findWindowQuads(boiler.getLevel(), found));

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
                ? LevelRenderer.getLightCoords(boiler.getLevel(), shell.centre())
                : state.lightCoords;
    }

    private static float fill(int amount, int capacity) {
        return capacity > 0 ? Math.min(1.0F, amount / (float) capacity) : 0.0F;
    }

    // The half-block lining tiles of a shell's walls that lie behind window (TiledBoxes.windowKey): those
    // behind a Pressure Glass pane, and those behind a casing quadrant that shows window because glass
    // touches that corner (the same rule ConnectedModel draws the casings by, so there's never lining in
    // the plane of a half-block window). The turbine's end caps never show window.
    static Set<Long> findWindowQuads(@Nullable Level level, ShellStructure.Shell shell) {
        Set<Long> windows = new HashSet<>();
        if (level == null) {
            return windows;
        }
        int[] size = { shell.size(Direction.Axis.X), shell.size(Direction.Axis.Y), shell.size(Direction.Axis.Z) };
        for (Direction face : Direction.values()) {
            int a = face.getAxis().ordinal();
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;
            Direction up = WindowQuadrants.up(face);
            Direction right = WindowQuadrants.right(face);
            for (int hu = 0; hu < size[u] * 2; hu++) {
                for (int hv = 0; hv < size[v] * 2; hv++) {
                    int[] offset = new int[3];
                    offset[a] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size[a] - 1 : 0;
                    offset[u] = hu / 2;
                    offset[v] = hv / 2;
                    BlockPos pos = shell.min().offset(offset[0], offset[1], offset[2]);
                    BlockState state = level.getBlockState(pos);
                    boolean window;
                    if (PressureGlassBlock.isFormed(state)) {
                        window = true;
                    } else if (!ShellCasingBlock.isFormed(state) || state.hasProperty(SteamTurbineArrayCasingBlock.END)
                            && state.getValue(SteamTurbineArrayCasingBlock.END) != SteamTurbineArrayCasingBlock.End.NONE
                            || state.hasProperty(GasTurbineArrayCasingBlock.END)
                            && state.getValue(GasTurbineArrayCasingBlock.END) != GasTurbineArrayCasingBlock.End.NONE) {
                        window = false;
                    } else {
                        // Which quadrant of the casing's face the tile lies behind, from its centre.
                        float[] centre = new float[3];
                        centre[u] = hu % 2 == 0 ? -1.0F : 1.0F;
                        centre[v] = hv % 2 == 0 ? -1.0F : 1.0F;
                        boolean top = centre[up.getAxis().ordinal()] * up.getAxisDirection().getStep() > 0;
                        boolean isRight = centre[right.getAxis().ordinal()] * right.getAxisDirection().getStep() > 0;
                        window = WindowQuadrants.windowed(level, pos, face, top, isRight);
                    }
                    if (window) {
                        windows.add(TiledBoxes.windowKey(face, hu, hv));
                    }
                }
            }
        }
        return windows;
    }

    static TextureAtlasSprite sprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.drumSprite == null || state.waterSprite == null || state.steamSprite == null
                || state.liner == null || state.jamb == null) {
            return;
        }
        TextureAtlasSprite drum = state.drumSprite;
        TextureAtlasSprite water = state.waterSprite;
        TextureAtlasSprite steam = state.steamSprite;
        TextureAtlasSprite liner = state.liner;
        TextureAtlasSprite jamb = state.jamb;
        float x0 = VOLUME_INSET, y0 = VOLUME_INSET, z0 = VOLUME_INSET;
        float x1 = state.sizeX - VOLUME_INSET, y1 = state.sizeY - VOLUME_INSET, z1 = state.sizeZ - VOLUME_INSET;
        float waterTop = y0 + (y1 - y0) * WATER_SHARE * state.water;
        // Steam gathers under the roof and fills the headspace down toward the water as it builds up.
        float steamBottom = y1 - (y1 - waterTop) * state.steam;
        // Once the steam reaches the water it sits on it: its bottom would share the water's surface and
        // flicker, so only the water's top is drawn there.
        boolean touching = state.water > 0 && steamBottom - waterTop < 1.0F / 64.0F;
        if (touching) {
            steamBottom = waterTop;
        }
        float steamFloor = steamBottom;
        Direction steamSkip = touching ? Direction.DOWN : null;

        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(drum.atlasLocation()),
                (pose, buffer) -> {
                    TiledBoxes.box(pose, buffer, drum, DRUM_COLOR, state.light,
                            DRUM_INSET, DRUM_INSET, DRUM_INSET, state.sizeX - DRUM_INSET, state.sizeY - DRUM_INSET, state.sizeZ - DRUM_INSET, false);
                    TiledBoxes.lining(pose, buffer, liner, jamb, -1, state.light, LINER_INSET, state.sizeX, state.sizeY, state.sizeZ, state.windows);
                });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(water.atlasLocation()), (pose, buffer) -> {
            TiledBoxes.box(pose, buffer, water, state.waterColor, state.light, x0, y0, z0, x1, waterTop, z1, false);
            TiledBoxes.box(pose, buffer, steam, state.steamColor, state.light, x0, steamFloor, z0, x1, y1, z1, false, steamSkip);
        });
    }

    // The whole structure, so it isn't culled when the master corner is off screen.
    @Override
    public AABB getRenderBoundingBox(SteamBoilerArrayBlockEntity boiler) {
        return boiler.getRenderBox();
    }
}
