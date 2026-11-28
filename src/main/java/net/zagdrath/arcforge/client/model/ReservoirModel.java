/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.zagdrath.arcforge.block.storage.ItemReservoirBlock;

// Connected Reservoirs ("connect": "reservoir"): touching Reservoirs of the same block read as one glass tank in one steel
// frame (the Fluid Reservoir's thin frame; the Gas Reservoir's heavy corner pieces and edge beams). Each face is four 8x8
// quadrants, each cut from one of five face textures by which of its two edges carry the frame:
//   frame          both edges (an outer corner)       frame_h   only its top or bottom edge
//   frame_v        only its left or right edge        glass     neither
//   frame_corners  neither, but the frame turns a corner there (an inner corner, where the diagonal block is missing)
// A face edge carries the frame unless the block beyond it is a Reservoir whose same face is also open (an edge where
// two Reservoirs join drops out); at a concave edge (the block beyond is a Reservoir but the block diagonally out in
// front of the face is one too) it stays. Faces toward another Reservoir are left out; the others are culled like glass.
// Each face of a Fluid or Gas Reservoir not toward another Reservoir also gets its back: the same quadrant, mirrored, just
// inside the face's plane (BACK_INSET) and facing into the tank. A tank is solid Reservoir blocks with no faces between
// them, so without the backs the far walls' frame (the back corner posts and edges) didn't show through the near glass.
// Backs are never culled, so the floor's frame shows from inside a tank standing on solid ground. A back is culled from
// the front, so the two never z-fight, and like the face it is cutout, so it sits behind the contents rather than over them.
// ReservoirRenderer draws the fluid or gas inside.
//
// The Item Reservoir ("connect": "item_reservoir") is the same quadrant scheme on a solid block: panel / rim / rim_h /
// rim_v / rim_corners, joined only between FORMED blocks of one store, so the rims run round the store's outer edges.
// On each face along the store's length it adds a riveted band (band_top / band_bottom / band_left / band_right, one per
// edge so the strap stays lit top-left) at an edge where the store ends, so both ends of a long store are banded.
final class ReservoirModel implements DynamicBlockStateModel {
    private enum Piece { GLASS, FRAME, FRAME_H, FRAME_V, CORNERS }

    // Bands sit just outside the face, like ConnectedModel's lip overlays, so they never z-fight with it.
    private static final float BAND_OFFSET = 0.02F;
    // Backs sit this far (in pixels) inside the face: still outside the contents (ReservoirRenderer.INSET, 0.16 px), but
    // off the block boundary, where the top of a floor block or a conduit's end cap facing into the tank lay on the same
    // plane, facing the same way, and z-fought with them.
    private static final float BACK_INSET = 0.08F;
    static final String[] FLUID_TEXTURES = { "glass", "frame", "frame_h", "frame_v", "frame_corners" };
    static final String[] STORE_TEXTURES = { "panel", "rim", "rim_h", "rim_v", "rim_corners", "band_top", "band_bottom", "band_left",
            "band_right" };

    private final Material.Baked particle;
    private final boolean store;
    // [face][piece][quadrant: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right]
    private final Map<Direction, BakedQuad[][]> quads = new EnumMap<>(Direction.class);
    // The same, facing into the tank (Fluid and Gas Reservoirs only)
    private final Map<Direction, BakedQuad[][]> backs = new EnumMap<>(Direction.class);
    // [face][edge: 0 top, 1 bottom, 2 left, 3 right] (Item Reservoir only)
    private final Map<Direction, BakedQuad[]> bands = new EnumMap<>(Direction.class);
    private final int materialFlags;

    ReservoirModel(ModelBaker baker, ConnectedModel.JsonModel unbaked, Identifier name, boolean store) {
        this.store = store;
        Map<String, Identifier> textures = unbaked.textures();
        String[] names = store ? STORE_TEXTURES : FLUID_TEXTURES;
        this.particle = ConnectedModel.Baked.material(baker, textures.getOrDefault("particle", textures.get(names[1])), name);
        Material.Baked[] pieces = new Material.Baked[Piece.values().length];
        for (int piece = 0; piece < pieces.length; piece++) {
            pieces[piece] = ConnectedModel.Baked.material(baker, textures.get(names[piece]), name);
        }
        int flags = 0;
        if (store) {
            for (Direction face : Direction.values()) {
                BakedQuad[] edgeQuads = new BakedQuad[4];
                for (int edge = 0; edge < 4; edge++) {
                    Material.Baked band = ConnectedModel.Baked.material(baker, textures.get(names[5 + edge]), name);
                    edgeQuads[edge] = ConnectedModel.Baked.bake(baker, face, 0, 0, 16, 16, BAND_OFFSET, band, 0, 0, 16, 16, Quadrant.R0);
                    flags |= edgeQuads[edge].materialInfo().flags();
                }
                bands.put(face, edgeQuads);
            }
        }
        for (Direction face : Direction.values()) {
            BakedQuad[][] faceQuads = new BakedQuad[Piece.values().length][4];
            BakedQuad[][] backQuads = store ? null : new BakedQuad[Piece.values().length][4];
            for (Piece piece : Piece.values()) {
                for (int quadrant = 0; quadrant < 4; quadrant++) {
                    float u0 = (quadrant & 1) != 0 ? 8 : 0, v0 = (quadrant & 2) != 0 ? 8 : 0;
                    BakedQuad quad = ConnectedModel.Baked.bake(baker, face, u0, v0, u0 + 8, v0 + 8, 0, pieces[piece.ordinal()],
                            u0, v0, u0 + 8, v0 + 8, Quadrant.R0);
                    faceQuads[piece.ordinal()][quadrant] = quad;
                    flags |= quad.materialInfo().flags();
                    if (backQuads != null) {
                        backQuads[piece.ordinal()][quadrant] = back(baker, face, u0, v0, pieces[piece.ordinal()]);
                    }
                }
            }
            quads.put(face, faceQuads);
            if (backQuads != null) {
                backs.put(face, backQuads);
            }
        }
        this.materialFlags = flags;
    }

    // The back of face's quadrant at face-local (u0, v0): the opposite face moved a block in onto face's plane, covering
    // the same rectangle with the texture mirrored (across u for a side face, across v for the top or bottom, the axis
    // the two faces' local coordinates run opposite ways on), so it reads as the same pane seen from behind.
    private static BakedQuad back(ModelBaker baker, Direction face, float u0, float v0, Material.Baked texture) {
        float u1 = u0 + 8, v1 = v0 + 8;
        Direction inward = face.getOpposite();
        if (face.getAxis() == Direction.Axis.Y) {
            return ConnectedModel.Baked.bake(baker, inward, u0, 16 - v1, u1, 16 - v0, BACK_INSET - 16, texture, u0, v1, u1, v0, Quadrant.R0);
        }
        return ConnectedModel.Baked.bake(baker, inward, 16 - u1, v0, 16 - u0, v1, BACK_INSET - 16, texture, u1, v0, u0, v1, Quadrant.R0);
    }

    // Another block of the same Reservoir (a Fluid Reservoir joins only Fluid Reservoirs, a Gas Reservoir Gas Reservoirs).
    // An Item Reservoir block joins only when both are FORMED: touching formed blocks are always one store's box.
    private boolean isReservoir(BlockAndTintGetter level, BlockPos pos, BlockState self) {
        BlockState other = level.getBlockState(pos);
        if (!other.is(self.getBlock())) {
            return false;
        }
        return !store || formed(self) && formed(other);
    }

    private static boolean formed(BlockState state) {
        return state.hasProperty(ItemReservoirBlock.FORMED) && state.getValue(ItemReservoirBlock.FORMED);
    }

    // Whether the face's edge toward side shows the frame.
    private boolean framed(BlockAndTintGetter level, BlockPos pos, Direction face, Direction side, BlockState self) {
        BlockPos beyond = pos.relative(side);
        return !isReservoir(level, beyond, self) || isReservoir(level, beyond.relative(face), self);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        Direction.Axis length = store && formed(state) ? state.getValue(ItemReservoirBlock.AXIS) : Direction.Axis.Y;
        for (Direction face : Direction.values()) {
            if (isReservoir(level, pos.relative(face), state)) {
                continue;
            }
            Direction up = WindowQuadrants.up(face), right = WindowQuadrants.right(face);
            BakedQuad[][] faceQuads = quads.get(face);
            BakedQuad[][] backQuads = backs.get(face);
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                Direction vertical = (quadrant & 2) != 0 ? up.getOpposite() : up;
                Direction horizontal = (quadrant & 1) != 0 ? right : right.getOpposite();
                boolean alongTop = framed(level, pos, face, vertical, state);
                boolean alongSide = framed(level, pos, face, horizontal, state);
                Piece piece;
                if (alongTop && alongSide) {
                    piece = Piece.FRAME;
                } else if (alongTop) {
                    piece = Piece.FRAME_H;
                } else if (alongSide) {
                    piece = Piece.FRAME_V;
                } else {
                    BlockPos diagonal = pos.relative(vertical).relative(horizontal);
                    piece = !isReservoir(level, diagonal, state) || isReservoir(level, diagonal.relative(face), state) ? Piece.CORNERS : Piece.GLASS;
                }
                builder.addCulledFace(face, faceQuads[piece.ordinal()][quadrant]);
                // Never culled: a face against a solid block (the floor under a tank) is hidden from outside, but from
                // inside the tank its frame still shows.
                if (backQuads != null) {
                    builder.addUnculledFace(backQuads[piece.ordinal()][quadrant]);
                }
            }
            if (store && face.getAxis() != length) {
                for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                    Direction end = Direction.fromAxisAndDirection(length, sign);
                    if (!isReservoir(level, pos.relative(end), state)) {
                        int edge = end == up ? 0 : end == up.getOpposite() ? 1 : end == right ? 3 : 2;
                        builder.addCulledFace(face, bands.get(face)[edge]);
                    }
                }
            }
        }
        parts.add(new SimpleModelWrapper(builder.build(), true, particle));
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return materialFlags;
    }
}
