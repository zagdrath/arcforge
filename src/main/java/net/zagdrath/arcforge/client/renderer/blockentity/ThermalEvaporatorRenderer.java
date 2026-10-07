/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.HashSet;
import java.util.Set;

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
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.client.model.WindowQuadrants;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;

// Draws the inside of a formed Thermal Evaporator Array, seen through its windows. Like the steam and gas turbine arrays,
// the tower is a thin skin (its casings and panes draw only their outer faces, and each window reaches half a block into
// the casings round it; see ConnectedModel and WindowQuadrants.windowedEvaporator), so this draws the inside of that skin:
// a lining just inside the tower's walls in the tower's inner plate, cut away behind every window with a reveal round
// each opening (TiledBoxes.lining). Inside it, the input (Seawater or Brine, in its own fluid sprite and colour) rises
// from the floor with how full the input tank is (a full tank fills the tower), and, while it makes Salt, a white bed of
// salt lies on the floor under the liquid. The level eases toward the synced one.
public class ThermalEvaporatorRenderer implements BlockEntityRenderer<ThermalEvaporatorBlockEntity, ThermalEvaporatorRenderer.State> {
    // The lining, then the liquid and salt a little further in, so no two share a plane.
    private static final float LINER_INSET = 1.0F / 16.0F;
    private static final float VOLUME_INSET = 1.5F / 16.0F;
    // The salt bed's depth.
    private static final float BED = 2.0F / 16.0F;
    private static final float EASE = 0.08F;
    private static final Identifier LINER = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/thermal_evaporator/inner");
    private static final Identifier SALT = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/salt_block");

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // The tower's minimum corner, relative to the controller.
        public float x, y, z;
        public float level;
        public boolean saltBed;
        // The lining tiles behind windows (see TiledBoxes.windowKey), which are left out.
        public Set<Long> windows = Set.of();
        public @Nullable TextureAtlasSprite fluidSprite, saltSprite, liner, jamb;
        public int fluidColor;
        public int light;
    }

    public ThermalEvaporatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThermalEvaporatorBlockEntity evaporator, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(evaporator, state, partialTicks, cameraPosition, breakProgress);
        ThermalEvaporatorStructure.Tower tower = evaporator.getTower();
        state.formed = tower != null;
        if (tower == null) {
            return;
        }
        BlockPos rel = tower.min().subtract(evaporator.getBlockPos());
        state.x = rel.getX();
        state.y = rel.getY();
        state.z = rel.getZ();
        state.level = evaporator.easeLevel(evaporator.columnLevel(), EASE);
        state.saltBed = evaporator.showsSaltBed();
        state.saltSprite = SteamBoilerArrayRenderer.sprite(SALT);
        state.liner = SteamBoilerArrayRenderer.sprite(LINER);
        state.jamb = SteamBoilerArrayRenderer.sprite(SteamBoilerArrayRenderer.JAMB);
        state.windows = evaporator.windowQuads(found -> findWindowQuads(evaporator.getLevel(), found));
        Fluid fluid = evaporator.getColumnFluid();
        state.fluidSprite = null;
        if (fluid != Fluids.EMPTY && state.level > 0.001F) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            state.fluidSprite = model.stillMaterial().sprite();
            state.fluidColor = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xE0000000;
        }
        // Lit by the middle of the core, not by the controller at the foot of the tower.
        state.light = evaporator.getLevel() != null
                ? LevelRenderer.getLightCoords(evaporator.getLevel(), tower.coreBottom().above(ThermalEvaporatorStructure.HEIGHT / 2 - 1))
                : state.lightCoords;
    }

    // The half-block lining tiles of the tower's walls that lie behind window (TiledBoxes.windowKey): those behind a
    // Pressure Glass pane, and those behind a casing quadrant that shows window (the same rule ConnectedModel draws the
    // casings by, so there's never lining in the plane of a half-block window).
    static Set<Long> findWindowQuads(@Nullable Level level, ThermalEvaporatorStructure.Tower tower) {
        Set<Long> windows = new HashSet<>();
        if (level == null) {
            return windows;
        }
        int[] size = { ThermalEvaporatorStructure.WIDTH, ThermalEvaporatorStructure.HEIGHT, ThermalEvaporatorStructure.WIDTH };
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
                    BlockPos pos = tower.min().offset(offset[0], offset[1], offset[2]);
                    BlockState state = level.getBlockState(pos);
                    boolean window;
                    if (PressureGlassBlock.isFormed(state)) {
                        window = true;
                    } else if (!ThermalEvaporatorStructure.isFormedPart(state)) {
                        window = false;
                    } else {
                        // Which quadrant of the casing's face the tile lies behind, from its centre.
                        float[] centre = new float[3];
                        centre[u] = hu % 2 == 0 ? -1.0F : 1.0F;
                        centre[v] = hv % 2 == 0 ? -1.0F : 1.0F;
                        boolean top = centre[up.getAxis().ordinal()] * up.getAxisDirection().getStep() > 0;
                        boolean isRight = centre[right.getAxis().ordinal()] * right.getAxisDirection().getStep() > 0;
                        window = WindowQuadrants.windowedEvaporator(level, pos, face, top, isRight);
                    }
                    if (window) {
                        windows.add(TiledBoxes.windowKey(face, hu, hv));
                    }
                }
            }
        }
        return windows;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.liner == null || state.jamb == null) {
            return;
        }
        TextureAtlasSprite liner = state.liner;
        TextureAtlasSprite jamb = state.jamb;
        float width = ThermalEvaporatorStructure.WIDTH, height = ThermalEvaporatorStructure.HEIGHT;
        float x0 = VOLUME_INSET, z0 = VOLUME_INSET, x1 = width - VOLUME_INSET, z1 = width - VOLUME_INSET;
        float floor = VOLUME_INSET;
        float bedTop = floor + BED;
        TextureAtlasSprite salt = state.saltSprite;
        boolean bed = state.saltBed && salt != null;

        poseStack.pushPose();
        // Drawn from the tower's minimum corner.
        poseStack.translate(state.x, state.y, state.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(liner.atlasLocation()), (pose, buffer) -> {
            TiledBoxes.lining(pose, buffer, liner, jamb, -1, state.light, LINER_INSET, width, height, width, state.windows);
            if (bed) {
                TiledBoxes.box(pose, buffer, salt, -1, state.light, x0, floor, z0, x1, bedTop, z1, false, Direction.DOWN);
            }
        });
        TextureAtlasSprite fluid = state.fluidSprite;
        if (fluid != null) {
            float bottom = bed ? bedTop : floor;
            float top = Math.max(bottom + 1.0F / 64.0F, floor + (height - 2 * VOLUME_INSET) * state.level);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(fluid.atlasLocation()), (pose, buffer) ->
                    TiledBoxes.box(pose, buffer, fluid, state.fluidColor, state.light, x0, bottom, z0, x1, top, z1, false,
                            bed ? Direction.DOWN : null));
        }
        poseStack.popPose();
    }

    // The whole tower, so the inside isn't culled when the controller is off screen.
    @Override
    public AABB getRenderBoundingBox(ThermalEvaporatorBlockEntity evaporator) {
        return evaporator.getRenderBox();
    }
}
