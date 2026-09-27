/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.ColumnPart;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Draws the inside of a formed Distillation Array's tray levels, seen through their glass. Every layer with
// trays in it has a steel tray deck level with the top of the trays' sill (with an underside, for the
// layer below to see), the fraction that layer collects pooled on it (as deep as its tank is full), and
// vapour rising from the pool while the column runs; the vapour layer at the top of the trays has only
// vapour, thicker. Where there's no glass round the layer (the controller or a plain casing in it) the
// wall has a lining, as does the ceiling under a solid layer and the band under each deck. The column's
// own blocks draw only its outside.
public class DistillationArrayRenderer implements BlockEntityRenderer<DistillationArrayBlockEntity, DistillationArrayRenderer.State> {
    // Linings sit this far inside the column's outer faces.
    private static final float INSET = 1.0F / 16.0F;
    // The deck's top: the top of the trays' sill as seen from outside (row 10 of their texture).
    private static final float DECK_Y = 6.0F / 16.0F;
    private static final float DECK_THICKNESS = 1.0F / 16.0F;
    // A full tank pools this deep on the deck (glass rows 7-9); any at all shows at least a film.
    private static final float POOL_MAX = 3.0F / 16.0F, POOL_MIN = 0.5F / 16.0F;
    // The pool stands this far inside the lining, so none of its faces meet another.
    private static final float POOL_GAP = 0.5F / 16.0F;
    // Share of the gap to the synced fill closed each frame.
    private static final float EASE = 0.1F;
    private static final float VAPOUR_ALPHA = 0.35F, NAPHTHA_VAPOUR_ALPHA = 0.55F, TOP_VAPOUR_ALPHA = 0.85F;

    private static final Identifier DECK = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/distillation_array/tray_plate");
    private static final Identifier UNDERSIDE = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/distillation_array/tray_underside");
    private static final Identifier VAPOUR = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/distillation_array/vapour");

    // One tray layer, in column-local coordinates.
    public record Layer(int y, TrayLevelCasingBlock.@Nullable Fraction fraction, float poolTop, @Nullable TextureAtlasSprite pool,
            int poolColor, List<Wall> walls, boolean ceiling, int light) {}

    // A block in a tray layer with no glass (cell x, z) and the outward side the lining covers.
    public record Wall(int x, int z, Direction side) {}

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        // From the controller to the column's minimum corner.
        public int offsetX, offsetY, offsetZ;
        public final List<Layer> layers = new ArrayList<>();
        public boolean lit;
        public int vapourColor;
        public @Nullable TextureAtlasSprite deck, underside, liner, vapour;
    }

    public DistillationArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DistillationArrayBlockEntity column, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(column, state, partialTicks, cameraPosition, breakProgress);
        DistillationStructure.Column shape = column.getColumn();
        Level level = column.getLevel();
        state.layers.clear();
        state.formed = shape != null && level != null;
        if (!state.formed) {
            return;
        }
        BlockPos min = shape.min();
        state.offsetX = min.getX() - column.getBlockPos().getX();
        state.offsetY = min.getY() - column.getBlockPos().getY();
        state.offsetZ = min.getZ() - column.getBlockPos().getZ();
        state.deck = SteamBoilerArrayRenderer.sprite(DECK);
        state.underside = SteamBoilerArrayRenderer.sprite(UNDERSIDE);
        state.liner = SteamBoilerArrayRenderer.sprite(SteamBoilerArrayRenderer.LINER);
        state.vapour = SteamBoilerArrayRenderer.sprite(VAPOUR);
        BlockState controller = column.getBlockState();
        state.lit = controller.hasProperty(ColumnPart.LIT) && controller.getValue(ColumnPart.LIT);
        SteamGrade grade = SteamGrade.of(column.getSteam().getResource(0));
        state.vapourColor = column.isStripping() ? (grade != null ? grade.tint() : SteamGrade.STEAM.tint()) & 0x00FFFFFF : 0x00FFFFFF;

        // Each pool eases once a frame, whichever layers show it.
        float[] fills = { column.easeFill(0, EASE), column.easeFill(1, EASE), column.easeFill(2, EASE) };
        for (int y = 1; y < shape.height() - 1; y++) {
            TrayLevelCasingBlock.Fraction fraction = null;
            BlockPos glass = null;
            List<Wall> walls = new ArrayList<>();
            for (int x = 0; x < DistillationStructure.WIDTH; x++) {
                for (int z = 0; z < DistillationStructure.WIDTH; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState part = level.getBlockState(pos);
                    boolean tray = part.getBlock() instanceof TrayLevelCasingBlock;
                    if (tray) {
                        fraction = part.getValue(TrayLevelCasingBlock.FRACTION);
                    }
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        if (shape.contains(pos.relative(side))) {
                            continue;
                        }
                        if (!tray) {
                            walls.add(new Wall(x, z, side));
                        } else if (glass == null) {
                            glass = pos.relative(side);
                        }
                    }
                }
            }
            if (fraction == null) {
                continue;
            }
            boolean ceiling = !hasTray(level, min, y + 1);
            int index = switch (fraction) {
                case NAPHTHA -> 0;
                case LIGHT -> 1;
                case HEAVY -> 2;
                case VAPOR -> -1;
            };
            float poolTop = DECK_Y;
            TextureAtlasSprite poolSprite = null;
            int poolColor = -1;
            if (index >= 0) {
                FilteredFluidTank tank = switch (index) {
                    case 0 -> column.getNaphtha();
                    case 1 -> column.getLightOil();
                    default -> column.getHeavyOil();
                };
                float fill = fills[index];
                if (tank.getAmount() > 0 || fill > 0.005F) {
                    poolTop = DECK_Y + Math.max(POOL_MIN, POOL_MAX * fill);
                    Fluid fluid = tank.getAmount() > 0 ? tank.getResource(0).getFluid() : productFluid(index);
                    var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
                    poolSprite = model.stillMaterial().sprite();
                    poolColor = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xFF000000;
                }
            }
            // Tray blocks are lit by the air outside their glass (inside a block there's no light of its own).
            BlockPos lightAt = glass != null ? glass : column.getBlockPos().relative(column.getFacing());
            state.layers.add(new Layer(y, fraction, poolTop, poolSprite, poolColor, walls, ceiling, LightCoordsUtil.getLightCoords(level, lightAt)));
        }
    }

    private static boolean hasTray(Level level, BlockPos min, int y) {
        for (int x = 0; x < DistillationStructure.WIDTH; x++) {
            for (int z = 0; z < DistillationStructure.WIDTH; z++) {
                if (level.getBlockState(min.offset(x, y, z)).getBlock() instanceof TrayLevelCasingBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Fluid productFluid(int index) {
        return switch (index) {
            case 0 -> ModFluids.NAPHTHA.get();
            case 1 -> ModFluids.LIGHT_OIL.get();
            default -> ModFluids.HEAVY_OIL.get();
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.layers.isEmpty() || state.deck == null || state.underside == null || state.liner == null || state.vapour == null) {
            return;
        }
        TextureAtlasSprite deck = state.deck;
        TextureAtlasSprite underside = state.underside;
        TextureAtlasSprite liner = state.liner;
        TextureAtlasSprite vapour = state.vapour;
        float size = DistillationStructure.WIDTH;
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);

        // The steel: decks, the band under them, linings and ceilings.
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(deck.atlasLocation()), (pose, buffer) -> {
            for (Layer layer : state.layers) {
                float y = layer.y();
                float deckTop = y + DECK_Y, deckBottom = deckTop - DECK_THICKNESS;
                TiledBoxes.plane(pose, buffer, deck, -1, layer.light(), Direction.UP, 0, deckTop, 0, size, deckTop, size);
                TiledBoxes.plane(pose, buffer, underside, -1, layer.light(), Direction.DOWN, 0, deckBottom, 0, size, deckBottom, size);
                // Under the deck, the inside of the sill band (seen from the layer below).
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    wall(pose, buffer, liner, layer.light(), side, 0, size, y, deckBottom);
                }
                for (Wall wall : layer.walls()) {
                    float along = wall.side().getAxis() == Direction.Axis.X ? wall.z() : wall.x();
                    wall(pose, buffer, liner, layer.light(), wall.side(), Math.max(INSET, along), Math.min(size - INSET, along + 1), deckTop, y + 1);
                }
                if (layer.ceiling()) {
                    TiledBoxes.plane(pose, buffer, liner, -1, layer.light(), Direction.DOWN, 0, y + 1, 0, size, y + 1, size);
                }
            }
        });

        // The pools, then the vapour over them.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(deck.atlasLocation()), (pose, buffer) -> {
            float lo = INSET + POOL_GAP, hi = size - INSET - POOL_GAP;
            for (Layer layer : state.layers) {
                TextureAtlasSprite pool = layer.pool();
                if (pool != null) {
                    float y = layer.y() + DECK_Y;
                    TiledBoxes.box(pose, buffer, pool, layer.poolColor(), layer.light(), lo, y, lo, hi, layer.y() + layer.poolTop(), hi, false, Direction.DOWN);
                }
            }
            if (!state.lit) {
                return;
            }
            for (Layer layer : state.layers) {
                boolean top = layer.fraction() == TrayLevelCasingBlock.Fraction.VAPOR;
                float alpha = top ? TOP_VAPOUR_ALPHA : layer.fraction() == TrayLevelCasingBlock.Fraction.NAPHTHA ? NAPHTHA_VAPOUR_ALPHA : VAPOUR_ALPHA;
                int color = state.vapourColor | (int) (255 * alpha) << 24;
                float y0 = layer.y() + layer.poolTop(), y1 = layer.y() + 1;
                // Two sheets crossing along the diagonals, and on the vapour layer a third across the middle.
                vapour(pose, buffer, vapour, color, layer.light(), INSET, INSET, size - INSET, size - INSET, y0, y1);
                vapour(pose, buffer, vapour, color, layer.light(), size - INSET, INSET, INSET, size - INSET, y0, y1);
                if (top) {
                    vapour(pose, buffer, vapour, color, layer.light(), 1, INSET, 1, size - INSET, y0, y1);
                }
            }
        });
        poseStack.popPose();
    }

    // Lining on the inside of the column's side wall, from along0 to along1 across it and y0 to y1 up it.
    private static void wall(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite liner, int light, Direction side,
            float along0, float along1, float y0, float y1) {
        float size = DistillationStructure.WIDTH;
        float plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - INSET : INSET;
        Direction inward = side.getOpposite();
        if (side.getAxis() == Direction.Axis.X) {
            TiledBoxes.plane(pose, buffer, liner, -1, light, inward, plane, y0, along0, plane, y1, along1);
        } else {
            TiledBoxes.plane(pose, buffer, liner, -1, light, inward, along0, y0, plane, along1, y1, plane);
        }
    }

    // A vertical sheet of vapour from (x0, z0) to (x1, z1), seen from both sides: the texture repeats about
    // once a block along it, and its bottom rows sit on the pool.
    private static void vapour(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float x0, float z0, float x1, float z1, float y0, float y1) {
        float length = (float) Math.hypot(x1 - x0, z1 - z0);
        int pieces = Math.max(1, Math.round(length));
        float v0 = sprite.getV(Math.max(0.0F, 1.0F - (y1 - y0))), v1 = sprite.getV(1.0F);
        for (int i = 0; i < pieces; i++) {
            float a = i / (float) pieces, b = (i + 1) / (float) pieces;
            float ax = x0 + (x1 - x0) * a, az = z0 + (z1 - z0) * a;
            float bx = x0 + (x1 - x0) * b, bz = z0 + (z1 - z0) * b;
            float u0 = sprite.getU(0.0F), u1 = sprite.getU(1.0F);
            // Front, then back.
            vertex(pose, buffer, ax, y0, az, u0, v1, color, light);
            vertex(pose, buffer, bx, y0, bz, u1, v1, color, light);
            vertex(pose, buffer, bx, y1, bz, u1, v0, color, light);
            vertex(pose, buffer, ax, y1, az, u0, v0, color, light);
            vertex(pose, buffer, ax, y1, az, u0, v0, color, light);
            vertex(pose, buffer, bx, y1, bz, u1, v0, color, light);
            vertex(pose, buffer, bx, y0, bz, u1, v1, color, light);
            vertex(pose, buffer, ax, y0, az, u0, v1, color, light);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public AABB getRenderBoundingBox(DistillationArrayBlockEntity column) {
        return column.getRenderBox();
    }
}
