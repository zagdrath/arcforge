/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;

// Draws a multiblock blueprint as isometric blocks in a GUI (the JEI build viewer and the Engineer's Handbook). Each
// block is a cube of its real face textures: the top, and the two sides facing the viewer, shaded,
// sheared onto the screen and drawn back to front. The view turns in quarter turns, and can show the
// structure up to a given layer, so it can be followed as build steps.
public final class StructureRenderer {
    // Shading of the top, left and right faces.
    private static final int TOP = 0xFFFFFFFF, LEFT = 0xFFD0D0D0, RIGHT = 0xFFA8A8A8;
    // Blocks below the layer being built are drawn a little darker, so the new layer stands out.
    private static final int BELOW_TINT = 0xFFB8B8B8;
    // Height of a block's side relative to half its width (a little taller than true isometric).
    private static final float SIDE_HEIGHT = 1.2F;

    private static final Map<BlockState, Map<Direction, TextureAtlasSprite>> SPRITES = new HashMap<>();

    private StructureRenderer() {}

    // Draws the blueprint centred in the box (x, y, width, height). rotation: quarter turns (0-3).
    // layer: the top layer shown (layers above it are hidden); highlightLayer: layers below it are darker.
    public static void draw(GuiGraphicsExtractor graphics, MultiblockBlueprints.Blueprint blueprint, int x, int y, int width, int height,
            int rotation, int layer, boolean highlightLayer) {
        BlockPos size = blueprint.size();
        int turns = Math.floorMod(rotation, 4);
        int sizeX = turns % 2 == 0 ? size.getX() : size.getZ();
        int sizeZ = turns % 2 == 0 ? size.getZ() : size.getX();

        // Half a block's width (a) so the whole structure fits the box.
        float spanX = sizeX + sizeZ;
        float spanY = (sizeX + sizeZ) / 2.0F + size.getY() * SIDE_HEIGHT;
        float a = Math.min(width / spanX, height / spanY);
        float h = a * SIDE_HEIGHT;
        // Screen position of world point (0, 0, 0) so the structure is centred.
        float originX = x + width / 2.0F + (sizeZ - sizeX) * a / 2.0F;
        float originY = y + (height - spanY * a) / 2.0F + size.getY() * h;

        List<Placed> blocks = new ArrayList<>();
        Set<BlockPos> occupied = new HashSet<>();
        for (MultiblockBlueprints.Placement placement : blueprint.placements()) {
            BlockPos pos = placement.pos();
            if (pos.getY() > layer) {
                continue;
            }
            BlockPos turned = turn(pos, size, turns);
            blocks.add(new Placed(turned, placement.state()));
            occupied.add(turned);
        }
        // Back to front: further blocks first (smaller x + y + z), lower first on ties.
        blocks.sort(Comparator.<Placed>comparingInt(p -> p.pos.getX() + p.pos.getY() + p.pos.getZ()).thenComparingInt(p -> p.pos.getY()));

        for (Placed block : blocks) {
            Map<Direction, TextureAtlasSprite> sprites = sprites(block.state);
            int tint = highlightLayer && block.pos.getY() < layer ? BELOW_TINT : 0xFFFFFFFF;
            float bx = block.pos.getX(), by = block.pos.getY(), bz = block.pos.getZ();
            // The sides this view shows are +Y, +Z (left) and +X (right); each shows the block face that
            // pointed that way before the view turned.
            if (!occupied.contains(block.pos.above())) {
                face(graphics, sprites.get(Direction.UP), multiply(TOP, tint), a, h, originX, originY,
                        bx, by + 1, bz, bx + 1, by + 1, bz, bx, by + 1, bz + 1);
            }
            if (!occupied.contains(block.pos.south())) {
                face(graphics, sprites.get(unturn(Direction.SOUTH, turns)), multiply(LEFT, tint), a, h, originX, originY,
                        bx, by + 1, bz + 1, bx + 1, by + 1, bz + 1, bx, by, bz + 1);
            }
            if (!occupied.contains(block.pos.east())) {
                face(graphics, sprites.get(unturn(Direction.EAST, turns)), multiply(RIGHT, tint), a, h, originX, originY,
                        bx + 1, by + 1, bz + 1, bx + 1, by + 1, bz, bx + 1, by, bz + 1);
            }
        }
    }

    private record Placed(BlockPos pos, BlockState state) {}

    // Turns a block position a quarter turn clockwise (seen from above) per turn, within the structure's box.
    private static BlockPos turn(BlockPos pos, BlockPos size, int turns) {
        int x = pos.getX(), z = pos.getZ(), sx = size.getX(), sz = size.getZ();
        for (int i = 0; i < turns; i++) {
            int nx = sz - 1 - z;
            int nz = x;
            x = nx;
            z = nz;
            int swap = sx;
            sx = sz;
            sz = swap;
        }
        return new BlockPos(x, pos.getY(), z);
    }

    // The direction a face pointed before the view was turned.
    private static Direction unturn(Direction direction, int turns) {
        Direction original = direction;
        for (int i = 0; i < turns; i++) {
            original = original.getCounterClockWise();
        }
        return original;
    }

    // One face as a sheared sprite: (x0,y0,z0) is the texture's top-left corner, (x1..) its top-right and
    // (x2..) its bottom-left.
    private static void face(GuiGraphicsExtractor graphics, @Nullable TextureAtlasSprite sprite, int color, float a, float h,
            float originX, float originY, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2) {
        if (sprite == null) {
            return;
        }
        float ox = originX + (x0 - z0) * a, oy = originY + (x0 + z0) * a / 2.0F - y0 * h;
        float ux = originX + (x1 - z1) * a - ox, uy = originY + (x1 + z1) * a / 2.0F - y1 * h - oy;
        float vx = originX + (x2 - z2) * a - ox, vy = originY + (x2 + z2) * a / 2.0F - y2 * h - oy;
        graphics.pose().pushMatrix();
        graphics.pose().mul(new Matrix3x2f(ux / 16.0F, uy / 16.0F, vx / 16.0F, vy / 16.0F, ox, oy));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 16, 16, color);
        graphics.pose().popMatrix();
    }

    private static int multiply(int a, int b) {
        int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
        int g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // The sprite on each side of a block's model: the first quad facing that way (culled or not), or the
    // model's particle sprite.
    private static Map<Direction, TextureAtlasSprite> sprites(BlockState state) {
        return SPRITES.computeIfAbsent(state, key -> {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(key);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, key, RandomSource.create(42L), parts);
            Map<Direction, TextureAtlasSprite> sprites = new HashMap<>();
            for (Direction direction : Direction.values()) {
                TextureAtlasSprite sprite = null;
                for (BlockStateModelPart part : parts) {
                    List<BakedQuad> quads = part.getQuads(direction);
                    if (!quads.isEmpty()) {
                        sprite = quads.getFirst().materialInfo().sprite();
                        break;
                    }
                    for (BakedQuad quad : part.getQuads(null)) {
                        if (quad.direction() == direction) {
                            sprite = quad.materialInfo().sprite();
                            break;
                        }
                    }
                    if (sprite != null) {
                        break;
                    }
                }
                sprites.put(direction, sprite != null ? sprite : model.particleMaterial(BlockAndTintGetter.EMPTY, BlockPos.ZERO, key).sprite());
            }
            return sprites;
        });
    }

    // Model sprites change on resource reload.
    public static void clearCache() {
        SPRITES.clear();
    }
}
