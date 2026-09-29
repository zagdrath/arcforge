/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.client.ConduitTints;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.item.ItemPacket;

// Draws what is inside glass conduits: the fluid (filled to its level) or the items travelling through.
// The glass itself comes from the block model.
public class ConduitRenderer implements BlockEntityRenderer<ConduitBlockEntity, ConduitRenderState> {
    private static final float ITEM_SCALE = 0.3F;
    // Gas tint opacity from a nearly empty to a full pressurized conduit.
    private static final int GAS_MIN_ALPHA = 60;
    private static final int GAS_MAX_ALPHA = 200;
    // Gas flowing through a conduit that holds none of it is drawn as if it were this full, so it can be seen.
    private static final float GAS_FLOWING_SHARE = 0.4F;
    // Fluid cross-section inside the 6px pipe, in 1/16 block units.
    private static final float LO = 6.0F / 16.0F, HI = 10.0F / 16.0F, SPAN = HI - LO;
    private static final float CORE_LO = 6.0F / 16.0F, CORE_HI = 10.0F / 16.0F;
    // Port arms stop 2px short of the block face.
    private static final float PORT_INSET = 2.0F / 16.0F;
    private static final Vec3[] STORED_OFFSETS = {
            new Vec3(-0.04, -0.06, -0.04), new Vec3(0.04, -0.06, 0.04), new Vec3(0.04, 0.0, -0.04), new Vec3(-0.04, 0.0, 0.04) };

    private final ItemModelResolver itemModelResolver;

    public ConduitRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ConduitRenderState createRenderState() {
        return new ConduitRenderState();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public void extractRenderState(ConduitBlockEntity conduit, ConduitRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(conduit, state, partialTicks, cameraPosition, breakProgress);
        var blockState = conduit.getBlockState();
        for (Direction side : Direction.values()) {
            state.sides[side.ordinal()] = ConduitBlock.mode(blockState, side);
        }
        state.straight = ConduitBlock.straightAxis(blockState);

        state.fluidSprite = null;
        FluidStack fluid = conduit.getFluid();
        boolean gas = conduit.getConduitType() == ConduitType.GAS;
        boolean flowing = gas && conduit.getFlowingGas() != Fluids.EMPTY;
        if (fluid.isEmpty() && flowing) {
            fluid = new FluidStack(conduit.getFlowingGas(), 1);
        }
        if (!fluid.isEmpty()) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            state.fluidSprite = model.stillMaterial().sprite();
            int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1;
            state.fluidColor = tint | 0xFF000000;
            state.fluidLight = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, fluid.getFluidType().getLightLevel());
            if (gas) {
                // Gas fills the whole bore; how full the conduit is shows as how dense the tint is, and gas moving
                // through shows at least GAS_FLOWING_SHARE.
                float share = Math.min(1.0F, fluid.getAmount() / (float) ConduitTier.GAS_CAPACITY_PER_CONDUIT);
                if (flowing) {
                    share = Math.max(share, GAS_FLOWING_SHARE);
                }
                int alpha = Math.round(GAS_MIN_ALPHA + (GAS_MAX_ALPHA - GAS_MIN_ALPHA) * share);
                state.fluidColor = (ConduitTints.gasColor(fluid) & 0x00FFFFFF) | (alpha << 24);
                state.fill = 1.0F;
            } else {
                state.fill = Math.min(1.0F, fluid.getAmount() / (float) ConduitTier.FLUID_CAPACITY_PER_CONDUIT);
            }
        }

        state.items.clear();
        if ((!conduit.getPackets().isEmpty() || !conduit.getStoredItems().isEmpty()) && conduit.getLevel() != null) {
            float time = conduit.getLevel().getGameTime() + partialTicks;
            int seed = (int) conduit.getBlockPos().asLong();
            for (ItemPacket packet : conduit.getPackets()) {
                ItemStackRenderState itemState = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(itemState, packet.stack, ItemDisplayContext.GROUND, conduit.getLevel(), null, seed++);
                float progress = Math.min(1.0F, packet.progress + conduit.getItemSpeed() * partialTicks);
                state.items.add(new ConduitRenderState.Item(itemState, ItemPacket.positionAt(packet.from, packet.to, progress), time * 4.0F));
            }
            // Stored items rest in the core, spread slightly so several stacks stay visible.
            List<ItemStack> stored = conduit.getStoredItems();
            for (int i = 0; i < stored.size(); i++) {
                ItemStackRenderState itemState = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(itemState, stored.get(i), ItemDisplayContext.GROUND, conduit.getLevel(), null, seed++);
                Vec3 offset = STORED_OFFSETS[i % STORED_OFFSETS.length];
                state.items.add(new ConduitRenderState.Item(itemState, new Vec3(0.5, 0.5, 0.5).add(offset), time * 2.0F + i * 90.0F));
            }
        }
    }

    @Override
    public void submit(ConduitRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fluidSprite != null && state.fill > 0.0F) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(state.fluidSprite.atlasLocation()),
                    (pose, buffer) -> drawFluid(state, pose, buffer));
        }
        for (ConduitRenderState.Item item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.position().x, item.position().y, item.position().z);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            poseStack.rotateDegrees(Axis.YP, item.spin());
            item.state().submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    // --- Fluid ---

    // Horizontal runs fill from the bottom; vertical runs fill their whole cross-section.
    private static void drawFluid(ConduitRenderState state, PoseStack.Pose pose, VertexConsumer buffer) {
        float top = LO + SPAN * state.fill;
        if (state.straight != null) {
            switch (state.straight) {
                case X -> box(state, pose, buffer, 0, LO, LO, 1, top, HI);
                case Z -> box(state, pose, buffer, LO, LO, 0, HI, top, 1);
                case Y -> box(state, pose, buffer, LO, 0, LO, HI, 1, HI);
            }
            return;
        }

        box(state, pose, buffer, CORE_LO, CORE_LO, CORE_LO, CORE_HI, top, CORE_HI);
        for (Direction side : Direction.values()) {
            ConnectionMode mode = state.sides[side.ordinal()];
            if (mode == ConnectionMode.NONE) {
                continue;
            }
            float outer = mode.isPort() ? PORT_INSET : 0.0F;
            switch (side) {
                case NORTH -> box(state, pose, buffer, LO, LO, outer, HI, top, CORE_LO);
                case SOUTH -> box(state, pose, buffer, LO, LO, CORE_HI, HI, top, 1 - outer);
                case WEST -> box(state, pose, buffer, outer, LO, LO, CORE_LO, top, HI);
                case EAST -> box(state, pose, buffer, CORE_HI, LO, LO, 1 - outer, top, HI);
                case DOWN -> box(state, pose, buffer, LO, outer, LO, HI, CORE_LO, HI);
                case UP -> box(state, pose, buffer, LO, CORE_HI, LO, HI, 1 - outer, HI);
            }
        }
    }

    private static void box(ConduitRenderState state, PoseStack.Pose pose, VertexConsumer buffer,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        FluidBoxes.box(pose, buffer, state.fluidSprite, state.fluidColor, state.fluidLight, x0, y0, z0, x1, y1, z1);
    }
}
