/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.zagdrath.arcforge.client.renderer.ArcforgeRenderTypes;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.client.renderer.StaticMeshes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.ColumnPart;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity.Product;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.RefineryLayout;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.RefineryMesh;
import net.zagdrath.arcforge.steam.SteamGrade;

// Draws a formed Distillation Array as a refinery tower (its blocks draw nothing but their port plates): the reboiler,
// shell, sight glasses, manways, platforms, ladder, draw-offs and condenser bank from its RefineryLayout, built by
// RefineryMesh on the model sheet block/distillation_array/refinery, with a nozzle from the tower out to each port. The
// still parts are built once and again when the ports (checked every second) or the products it draws off change, and
// baked into the chunk mesh (ChunkMeshes). Each frame, out to stillPartsOnlyFrom (see LargeModels): the condenser fans
// (each built once and drawn turned while it runs; standing still past simpleMovingPartsFrom), each sight glass's pool
// (the product its tray collects, as full as that product's tank, easing toward it) and vapour rising above it while it
// runs (tinted by the steam when the batch is stripped), and the reboiler's burner windows glowing as brightly as the
// column is hot (full-bright).
public class DistillationArrayRenderer implements StructureRenderer<DistillationArrayBlockEntity, DistillationArrayRenderer.State> {
    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/distillation_array/refinery");
    private static final Identifier VAPOUR = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/distillation_array/vapour");
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];
    // Share of the gap to the synced fill closed each frame; the fans' speed while running (degrees a tick).
    private static final float EASE = 0.1F, FAN_SPEED = 24.0F;
    private static final float VAPOUR_ALPHA = 0.55F;
    // The glow starts at this temperature (°C) and is full this much hotter.
    private static final int GLOW_FROM = 100, GLOW_RANGE = 500;

    private final Map<DistillationArrayBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final DistillationStructure.Column column;
        final Direction front;
        final int makes;
        final ModelFrame frame;
        final RefineryMesh mesh;
        final List<Product> liquids;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY, glass = EMPTY, glow = EMPTY;
        final float[][] fans, fanAxes;
        final float[] fansAtRest;
        // The box round what this renderer draws: the fans, the pools and vapour, the glow.
        AABB drawn;
        long scanned;
        float fanAngle;
        float lastFrame = -1.0F;

        Built(DistillationStructure.Column column, Direction front, int makes, List<ModelMesh.Port> ports) {
            this.column = column;
            this.front = front;
            this.makes = makes;
            this.frame = frameOf(column, front);
            this.liquids = liquids(makes);
            this.mesh = new RefineryMesh(new RefineryLayout(column.width(), column.height()), frame, liquids.size());
            this.ports = ports;
            rebuild();
            this.fans = mesh.buildFanParts();
            this.fanAxes = mesh.fanAxes();
            this.fansAtRest = mesh.buildFans(0.0F);
            List<float[]> drawnLists = new ArrayList<>(List.of(fansAtRest, glow));
            for (float[] window : mesh.windows) {
                drawnLists.add(mesh.buildPool(window, 1.0F));
                drawnLists.add(mesh.buildVapour(window, 1.0F));
            }
            this.drawn = MeshRendering.bounds(column.min(), drawnLists.toArray(new float[0][])).inflate(0.5);
        }

        void rebuild() {
            float[][] parts = mesh.buildStatic(ports);
            solid = parts[0];
            glass = parts[1];
            glow = parts[2];
        }
    }

    // A sight glass's contents this frame: its pool (in a fluid's sprite and tint) and vapour.
    public record Tray(float[] pool, @Nullable TextureAtlasSprite sprite, int color, float[] vapour) {}

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // From the controller to the column's minimum corner.
        public int offsetX, offsetY, offsetZ;
        public float[] glow = EMPTY, fansAtRest = EMPTY;
        public float[][] fans = {};
        public Matrix4f[] fanTurns = {};
        public final List<Tray> trays = new ArrayList<>();
        public @Nullable TextureAtlasSprite sheet, vapour;
        public int light, vapourColor;
        public float brightness;
    }

    public DistillationArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(DistillationArrayBlockEntity column) {
        return ChunkMeshes.isBaked(column, MeshRendering.SOLID, MeshRendering.GLASS);
    }

    private static ModelFrame frameOf(DistillationStructure.Column column, Direction front) {
        return ModelFrame.toward(front.getOpposite(), column.width(), column.height(), column.width());
    }

    // The liquids the column makes, lightest first (the gas leaves by the vapour line instead of a tray).
    private static List<Product> liquids(int makes) {
        List<Product> liquids = new ArrayList<>();
        for (Product product : Product.values()) {
            if (!product.isGas() && (makes & 1 << product.ordinal()) != 0) {
                liquids.add(product);
            }
        }
        return liquids;
    }

    @Override
    public void extractRenderState(DistillationArrayBlockEntity column, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(column, state, partialTicks, cameraPosition, breakProgress);
        DistillationStructure.Column shape = column.getColumn();
        Level level = column.getLevel();
        state.trays.clear();
        state.formed = shape != null && level != null;
        if (shape == null || level == null) {
            built.remove(column);
            ChunkMeshes.remove(column);
            return;
        }
        BlockPos min = shape.min();
        state.offsetX = min.getX() - column.getBlockPos().getX();
        state.offsetY = min.getY() - column.getBlockPos().getY();
        state.offsetZ = min.getZ() - column.getBlockPos().getZ();
        Direction front = column.getFacing();
        int makes = column.makes();
        long time = level.getGameTime();
        Built model = built.get(column);
        if (model == null || !model.column.equals(shape) || model.front != front || model.makes != makes) {
            model = new Built(shape, front, makes, ports(level, shape, frameOf(shape, front)));
            model.scanned = time;
            built.put(column, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<ModelMesh.Port> ports = ports(level, shape, model.frame);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                model.rebuild();
            }
        }
        BlockState controller = column.getBlockState();
        boolean lit = controller.hasProperty(ColumnPart.LIT) && controller.getValue(ColumnPart.LIT);
        float now = time + partialTicks;
        if (model.lastFrame >= 0 && lit) {
            model.fanAngle = (model.fanAngle + Math.max(0.0F, Math.min(20.0F, now - model.lastFrame)) * FAN_SPEED) % 360.0F;
        }
        model.lastFrame = now;
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(SHEET);
        int light = LightCoordsUtil.getLightCoords(level, column.getBlockPos().relative(front));
        bake(column, MeshRendering.SOLID, model, ChunkSectionLayer.SOLID, model.solid, sheet, light);
        bake(column, MeshRendering.GLASS, model, ChunkSectionLayer.CUTOUT, model.glass, sheet, light);
        state.sheet = sheet;
        state.light = light;
        LargeModels.Detail detail = LargeModels.detail(model.drawn, cameraPosition);
        state.fans = detail == LargeModels.Detail.FULL ? model.fans : new float[0][];
        state.fanTurns = new Matrix4f[state.fans.length];
        for (int i = 0; i < state.fans.length; i++) {
            state.fanTurns[i] = MeshRendering.turn(model.fanAxes[i], model.fanAngle);
        }
        state.fansAtRest = detail == LargeModels.Detail.SIMPLE ? model.fansAtRest : EMPTY;
        state.glow = detail != LargeModels.Detail.STILL ? model.glow : EMPTY;
        if (detail == LargeModels.Detail.STILL) {
            state.vapour = null;
            return;
        }
        state.vapour = SteamBoilerArrayRenderer.sprite(VAPOUR);
        state.brightness = Math.max(0.0F, Math.min(1.0F, (column.temperature() - GLOW_FROM) / (float) GLOW_RANGE));
        SteamGrade grade = SteamGrade.of(column.getSteam().getResource(0));
        state.vapourColor = column.isStripping() ? (grade != null ? grade.tint() : SteamGrade.STEAM.tint()) & 0x00FFFFFF : 0x00FFFFFF;
        // Each product's pool eases once a frame, whichever trays show it.
        float[] fills = new float[Product.values().length];
        for (Product product : model.liquids) {
            fills[product.ordinal()] = column.easeFill(product, EASE);
        }
        int sections = model.mesh.windows.isEmpty() ? 1 : (int) model.mesh.windows.get(model.mesh.windows.size() - 1)[6] + 1;
        for (float[] window : model.mesh.windows) {
            int section = (int) window[6];
            Product product = trayProduct(model.liquids, section, sections);
            float fill = product != null ? fills[product.ordinal()] : 0.0F;
            TextureAtlasSprite sprite = null;
            int color = -1;
            float[] pool = EMPTY;
            if (product != null && fill > 0.005F) {
                Fluid fluid = column.shownFluid(product);
                var fluidModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
                sprite = fluidModel.stillMaterial().sprite();
                color = (fluidModel.fluidTintSource() != null ? fluidModel.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xFF000000;
                pool = model.mesh.buildPool(window, fill);
            }
            state.trays.add(new Tray(pool, sprite, color, lit ? model.mesh.buildVapour(window, fill) : EMPTY));
        }
    }

    // The liquid a section's tray collects: the products spread up the column, heaviest at the bottom.
    private static @Nullable Product trayProduct(List<Product> liquids, int section, int sections) {
        if (liquids.isEmpty()) {
            return null;
        }
        int fromTop = sections - 1 - section;
        return liquids.get(Math.min(liquids.size() - 1, fromTop * liquids.size() / Math.max(1, sections)));
    }

    // The ports on the column's outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, DistillationStructure.Column column, ModelFrame frame) {
        List<ModelMesh.Port> ports = new ArrayList<>();
        BlockPos min = column.min();
        for (BlockPos pos : column.positions()) {
            if (!column.isOuter(pos) && !column.isEndLayer(pos)) {
                continue;
            }
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
                continue;
            }
            int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            for (Direction face : Direction.values()) {
                if (faces.get(face) == SideMode.NONE || column.contains(pos.relative(face))) {
                    continue;
                }
                int[] n = frame.frameNormal(face.getStepX(), face.getStepY(), face.getStepZ());
                ports.add(new ModelMesh.Port(block[2], block[1], block[0], n[0], n[1], n[2]));
            }
        }
        return ports;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ChunkMeshes.submitHeld(state, poseStack, collector);
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] fansAtRest = state.fansAtRest, glow = state.glow;
        int light = state.light;
        for (int i = 0; i < Math.min(state.fans.length, state.fanTurns.length); i++) {
            MeshRendering.submitMoving(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), state.fans[i], sheet, light, state.fanTurns[i]);
        }
        StaticMeshes.submit(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), fansAtRest, sheet, light,
                (pose, buffer) -> emit(pose, buffer, fansAtRest, sheet, -1, light));
        for (Tray tray : state.trays) {
            TextureAtlasSprite sprite = tray.sprite();
            if (sprite != null && tray.pool().length > 0) {
                int color = tray.color();
                collector.submitCustomGeometry(poseStack, ArcforgeRenderTypes.volumeTranslucent(sprite.atlasLocation()),
                        (pose, buffer) -> emit(pose, buffer, tray.pool(), sprite, color, light));
            }
        }
        TextureAtlasSprite vapour = state.vapour;
        if (vapour != null) {
            int color = ARGB.color(Math.round(255 * VAPOUR_ALPHA), state.vapourColor);
            for (Tray tray : state.trays) {
                if (tray.vapour().length > 0) {
                    collector.submitCustomGeometry(poseStack, ArcforgeRenderTypes.volumeTranslucent(vapour.atlasLocation()),
                            (pose, buffer) -> emitLit(pose, buffer, tray.vapour(), vapour, color, LightCoordsUtil.FULL_BRIGHT));
                }
            }
        }
        if (glow.length > 0 && state.brightness > 0.01F) {
            int level = Math.round(255 * state.brightness);
            int color = ARGB.color(level, level, level, level);
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()),
                    (pose, buffer) -> emitLit(pose, buffer, glow, sheet, color, LightCoordsUtil.FULL_BRIGHT));
        }
        poseStack.popPose();
    }

    // RefineryMesh's vertices (x, y, z, u, v across the sheet or a sprite, normal), the UVs mapped into the atlas. Each
    // face is shaded as a block face turned its way is (top 1.0, north and south 0.8, east and west 0.6, bottom 0.5,
    // blended for slanted faces), with its normal straight up so the entity lighting adds nothing: the tower shades like
    // the blocks round it (docs/TEXTURE_STYLE.md, "Large model texturing").
    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sprite, int color, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            float shade = nx * nx * 0.6F + ny * ny * (ny > 0.0F ? 1.0F : 0.5F) + nz * nz * 0.8F;
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(ARGB.scaleRGB(color, shade))
                    .setUv(sprite.getU(data[i + 3]), sprite.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    // The same without face shading, for light: the burners' glow and the vapour.
    private static void emitLit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sprite, int color, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(color)
                    .setUv(sprite.getU(data[i + 3]), sprite.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // A still list into the chunk mesh, shaded as emit shades it (see ChunkMeshes).
    private static void bake(DistillationArrayBlockEntity column, int slot, Built model, ChunkSectionLayer layer, float[] data, TextureAtlasSprite sheet,
            int light) {
        DistillationStructure.Column shape = model.column;
        Direction front = model.front;
        int makes = model.makes;
        ChunkMeshes.bake(column, slot, shape.min(), layer, new MeshRendering.Baked(data, sheet), light,
                () -> shape.equals(column.getColumn()) && column.getFacing() == front && column.makes() == makes,
                (pose, buffer) -> emit(pose, buffer, data, sheet, -1, light));
    }

    // What this renderer draws (the fans, the pools and vapour, the glow), not culled when the controller is off screen;
    // the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(DistillationArrayBlockEntity column) {
        Built model = built.get(column);
        return model != null && model.column.equals(column.getColumn()) ? model.drawn : column.getRenderBox();
    }
}
