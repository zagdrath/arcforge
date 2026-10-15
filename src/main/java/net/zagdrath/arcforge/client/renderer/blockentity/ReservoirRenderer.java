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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.client.ConduitTints;
import net.zagdrath.arcforge.client.renderer.ArcforgeRenderTypes;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirKind;

// Draws a Reservoir's contents. Each block draws its own share.
// - Fluid Reservoir: since its tank fills from the bottom up with every block of a layer at one level (see ReservoirGroup),
//   the shares line up into one body: toward a neighbouring Fluid Reservoir holding the same fluid the box runs to the
//   block's edge and the face between them is left out (at the same level, or over a full block below), so the fluid reads
//   as one volume at its shared level. Toward anything else it stops just inside the glass.
// - Gas Reservoir: the gas fills its whole block, not to a level: a flat translucent fill (block/gas_reservoir/fill, a plain
//   white tile) in the gas's tint, from GAS_ALPHA_EMPTY to GAS_ALPHA_FULL opaque by how full it is. Its tank shares the gas
//   evenly, so neighbouring blocks match and the faces between them are left out: the gas reads as one volume.
// Glowing contents render at their light level. The contents don't write depth (see ArcforgeRenderTypes), so glass
// conduits and other translucent blocks behind a Reservoir still show through it.
public class ReservoirRenderer implements BlockEntityRenderer<ReservoirBlockEntity, ReservoirRenderer.State> {
    // Just inside the glass and frame, so no fluid face lies on a model face.
    private static final float INSET = 0.01F;
    private static final float SAME_LEVEL = 0.001F;
    public static final Identifier GAS_FILL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/gas_reservoir/fill");
    private static final float GAS_ALPHA_EMPTY = 0.12F, GAS_ALPHA_FULL = 0.72F;

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public int color = -1;
        public int light;
        public float x0, y0, z0, x1, y1, z1;
        public int skip;
    }

    public ReservoirRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ReservoirBlockEntity reservoir, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(reservoir, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        FluidStack fluid = reservoir.getFluid();
        Level level = reservoir.getLevel();
        if (fluid.isEmpty() || level == null) {
            return;
        }
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1;
        state.light = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, fluid.getFluidType().getLightLevel(fluid));
        if (reservoir.kind() == ReservoirKind.GAS) {
            extractGas(reservoir, state, level, fluid, ConduitTints.gasColor(fluid));
            return;
        }
        state.sprite = model.stillMaterial().sprite();
        state.color = tint | 0xFF000000;

        BlockPos pos = reservoir.getBlockPos();
        float top = surface(fluid.getAmount(), reservoir.capacity());
        boolean full = fluid.getAmount() >= reservoir.capacity();
        int skip = 0;
        float[] min = new float[3], max = new float[3];
        for (Direction direction : Direction.values()) {
            ReservoirBlockEntity neighbour = level.getBlockEntity(pos.relative(direction)) instanceof ReservoirBlockEntity other
                    && other.kind() == ReservoirKind.FLUID && FluidStack.isSameFluidSameComponents(other.getFluid(), fluid) ? other : null;
            boolean joined = neighbour != null;
            int axis = direction.getAxis().ordinal();
            boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            if (direction == Direction.UP) {
                // The surface: the top of the block only when full and more of the fluid sits above.
                max[axis] = full ? (joined ? 1.0F : 1.0F - INSET) : top;
                if (full && joined) {
                    skip |= 1 << direction.get3DDataValue();
                }
            } else if (direction == Direction.DOWN) {
                min[axis] = joined ? 0.0F : INSET;
                if (joined) {
                    skip |= 1 << direction.get3DDataValue();
                }
            } else {
                if (positive) {
                    max[axis] = joined ? 1.0F : 1.0F - INSET;
                } else {
                    min[axis] = joined ? 0.0F : INSET;
                }
                // A side face meeting the same level next door is inside the body.
                if (joined && Math.abs(surfaceOf(neighbour) - surfaceOf(reservoir)) < SAME_LEVEL) {
                    skip |= 1 << direction.get3DDataValue();
                }
            }
        }
        state.x0 = min[0];
        state.y0 = min[1];
        state.z0 = min[2];
        state.x1 = max[0];
        state.y1 = Math.max(max[1], min[1] + 1.0F / 64.0F);
        state.z1 = max[2];
        state.skip = skip;
    }

    // The whole block filled, toward a neighbouring Gas Reservoir with the same gas to its edge with the face between left
    // out; as opaque as the block is full.
    private static void extractGas(ReservoirBlockEntity reservoir, State state, Level level, FluidStack gas, int tint) {
        state.sprite = SteamBoilerArrayRenderer.sprite(GAS_FILL);
        float share = Math.min(1.0F, gas.getAmount() / (float) reservoir.capacity());
        int alpha = Math.round(255 * (GAS_ALPHA_EMPTY + (GAS_ALPHA_FULL - GAS_ALPHA_EMPTY) * share));
        state.color = (tint & 0x00FFFFFF) | alpha << 24;
        BlockPos pos = reservoir.getBlockPos();
        float[] min = new float[3], max = new float[3];
        int skip = 0;
        for (Direction direction : Direction.values()) {
            boolean joined = level.getBlockEntity(pos.relative(direction)) instanceof ReservoirBlockEntity other && other.kind() == ReservoirKind.GAS
                    && FluidStack.isSameFluidSameComponents(other.getFluid(), gas);
            int axis = direction.getAxis().ordinal();
            if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                max[axis] = joined ? 1.0F : 1.0F - INSET;
            } else {
                min[axis] = joined ? 0.0F : INSET;
            }
            if (joined) {
                skip |= 1 << direction.get3DDataValue();
            }
        }
        state.x0 = min[0];
        state.y0 = min[1];
        state.z0 = min[2];
        state.x1 = max[0];
        state.y1 = max[1];
        state.z1 = max[2];
        state.skip = skip;
    }

    // The fluid's surface in a block holding amount of capacity (every block of a tank's surface layer holds the same).
    private static float surface(int amount, int capacity) {
        return INSET + (1.0F - 2.0F * INSET) * Math.min(1.0F, amount / (float) capacity);
    }

    private static float surfaceOf(ReservoirBlockEntity reservoir) {
        return surface(reservoir.ownAmount(), reservoir.capacity());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        int color = state.color, light = state.light, skip = state.skip;
        float x0 = state.x0, y0 = state.y0, z0 = state.z0, x1 = state.x1, y1 = state.y1, z1 = state.z1;
        collector.submitCustomGeometry(poseStack, ArcforgeRenderTypes.volumeTranslucent(sprite.atlasLocation()),
                (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, color, light, x0, y0, z0, x1, y1, z1, skip));
    }
}
