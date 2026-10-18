/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.client.renderer.ArcforgeRenderTypes;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.ChemicalReactorArrayMesh;

// Draws a formed Chemical Reactor Array from its centre (ChemicalReactorArrayMesh, on the refinery tower's sheet): the
// still model, each vessel's liquid behind its sight glass in its lane's fluid (what the lane's recipe makes, else what
// it reacts; it sloshes a little while the lane runs, and stands empty when the lane has no recipe), and each vessel's
// agitator coupling and fan, which turn while its lane runs and coast to a stop when it stops. The models are built once
// per facing.
public class ChemicalReactorArrayRenderer implements StructureRenderer<ChemicalReactorArrayBlockEntity, ChemicalReactorArrayRenderer.State> {
    private static final float[] EMPTY = new float[0];
    // The agitators' speed, degrees a tick.
    private static final float SPIN = 24.0F;
    // How full each vessel looks, before the slosh.
    private static final float LEVEL = 0.68F;

    private record Built(float[][] still, float[][] agitators, float[][] axes, float[][][] liquids) {}

    private static final Map<Direction, Built> BUILT = new HashMap<>();

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public float[] solid = EMPTY, cutout = EMPTY, glow = EMPTY;
        public float[][] agitators = new float[0][];
        public float[][] axes = new float[0][];
        public final float[] angles = new float[ChemicalReactorArrayBlockEntity.LANES];
        // Per lane: the liquid's box in blocks from the minimum corner, its sprite, colour and light (null sprite: none).
        public final float[][] liquidBoxes = new float[ChemicalReactorArrayBlockEntity.LANES][];
        public final @Nullable TextureAtlasSprite[] liquidSprites = new TextureAtlasSprite[ChemicalReactorArrayBlockEntity.LANES];
        public final int[] liquidColors = new int[ChemicalReactorArrayBlockEntity.LANES];
        public final int[] liquidLights = new int[ChemicalReactorArrayBlockEntity.LANES];
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public ChemicalReactorArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    private static Built build(Direction facing) {
        ModelFrame frame = ModelFrame.toward(facing.getOpposite(), 3, 3, 3);
        ChemicalReactorArrayMesh mesh = new ChemicalReactorArrayMesh(frame);
        float[][] still = mesh.build();
        int lanes = ChemicalReactorArrayBlockEntity.LANES;
        float[][] agitators = new float[lanes][];
        float[][] axes = new float[lanes][];
        float[][][] liquids = new float[lanes][][];
        for (int lane = 0; lane < lanes; lane++) {
            agitators[lane] = mesh.agitator(lane);
            float x = ChemicalReactorArrayMesh.VESSEL_X[lane];
            float[] axis = frame.worldPoint(x, 0.0F, ChemicalReactorArrayMesh.VESSEL_U);
            axes[lane] = new float[] { axis[0] / 16.0F, axis[2] / 16.0F };
            float half = ChemicalReactorArrayMesh.WINDOW_W / 2.0F - 0.05F;
            float front = ChemicalReactorArrayMesh.WINDOW_U + 0.35F, back = ChemicalReactorArrayMesh.LIQUID_BACK - 0.05F;
            // The liquid's corners at the bottom and the top of the window (its height is scaled per frame).
            float[] a = frame.worldPoint(x - half, ChemicalReactorArrayMesh.WINDOW_Y0 + 0.05F, front);
            float[] b = frame.worldPoint(x + half, ChemicalReactorArrayMesh.WINDOW_Y1 - 0.05F, back);
            liquids[lane] = new float[][] { { Math.min(a[0], b[0]) / 16.0F, a[1] / 16.0F, Math.min(a[2], b[2]) / 16.0F },
                    { Math.max(a[0], b[0]) / 16.0F, b[1] / 16.0F, Math.max(a[2], b[2]) / 16.0F } };
        }
        return new Built(still, agitators, axes, liquids);
    }

    @Override
    public void extractRenderState(ChemicalReactorArrayBlockEntity array, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(array, state, partialTicks, cameraPosition, breakProgress);
        Level level = array.getLevel();
        state.formed = level != null && array.isFormed() && array.getBox() == null;
        if (!state.formed) {
            return;
        }
        Direction facing = array.getBlockState().getValue(CubeCasingBlock.FACING);
        Built built = BUILT.computeIfAbsent(facing, ChemicalReactorArrayRenderer::build);
        state.solid = built.still()[0];
        state.cutout = built.still()[1];
        state.glow = built.still()[2];
        state.agitators = built.agitators();
        state.axes = built.axes();
        state.sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        state.light = LightCoordsUtil.getLightCoords(level, array.getBlockPos().above(2));
        // Turn the agitators of the lanes that run, by the time since the last frame.
        float now = level.getGameTime() + partialTicks;
        float dt = array.agitatorTime < 0.0F ? 0.0F : Mth.clamp(now - array.agitatorTime, 0.0F, 5.0F);
        array.agitatorTime = now;
        for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
            if (array.isLaneRunning(lane)) {
                array.agitatorAngles[lane] = (array.agitatorAngles[lane] + SPIN * dt) % 360.0F;
            }
            state.angles[lane] = array.agitatorAngles[lane];
            state.liquidSprites[lane] = null;
            int id = array.laneFluid(lane);
            if (id < 0) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.byId(id);
            if (fluid == null) {
                continue;
            }
            FluidStack stack = new FluidStack(fluid, 1000);
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            state.liquidSprites[lane] = model.stillMaterial().sprite();
            int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(stack) : -1;
            state.liquidColors[lane] = tint | 0xFF000000;
            state.liquidLights[lane] = LightCoordsUtil.lightCoordsWithEmission(state.light, stack.getFluidType().getLightLevel(stack));
            float slosh = array.isLaneRunning(lane) ? Mth.sin(now * 0.35F + lane * 2.1F) * 0.03F : 0.0F;
            float[][] box = built.liquids()[lane];
            float fill = Mth.clamp(LEVEL + slosh, 0.05F, 1.0F);
            float top = box[0][1] + (box[1][1] - box[0][1]) * fill;
            state.liquidBoxes[lane] = new float[] { box[0][0], box[0][1], box[0][2], box[1][0], top, box[1][2] };
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        // From the centre to the cube's minimum corner, where the model is laid out.
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        int light = state.light;
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), state.solid, sheet, light);
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), state.cutout, sheet, light);
        if (state.glow.length > 0) {
            float[] glow = state.glow;
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()),
                    (pose, buffer) -> MeshRendering.emit(pose, buffer, glow, sheet, LightCoordsUtil.FULL_BRIGHT));
        }
        for (int lane = 0; lane < state.agitators.length; lane++) {
            float[] axis = state.axes[lane];
            float[] rotor = state.agitators[lane];
            poseStack.pushPose();
            poseStack.translate(axis[0], 0.0F, axis[1]);
            poseStack.rotate(Axis.YP.rotationDegrees(state.angles[lane]));
            poseStack.translate(-axis[0], 0.0F, -axis[1]);
            collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()),
                    (pose, buffer) -> MeshRendering.emit(pose, buffer, rotor, sheet, light));
            poseStack.popPose();
            TextureAtlasSprite sprite = state.liquidSprites[lane];
            float[] box = state.liquidBoxes[lane];
            if (sprite != null && box != null) {
                int color = state.liquidColors[lane], liquidLight = state.liquidLights[lane];
                collector.submitCustomGeometry(poseStack, ArcforgeRenderTypes.fluid(sprite, color),
                        (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, color, liquidLight, box[0], box[1], box[2], box[3], box[4], box[5]));
            }
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ChemicalReactorArrayBlockEntity array) {
        BlockPos pos = array.getBlockPos();
        return new AABB(pos).inflate(1.0);
    }
}
