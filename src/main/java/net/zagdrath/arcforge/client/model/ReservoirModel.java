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
import net.zagdrath.arcforge.block.storage.ReservoirBlock;

// Connected Reservoirs ("connect": "reservoir"): touching Reservoirs read as one glass tank in one steel frame. Each face is
// four 8x8 quadrants, each cut from one of five face textures by which of its two edges carry the frame:
//   frame          both edges (an outer corner)       frame_h   only its top or bottom edge
//   frame_v        only its left or right edge        glass     neither
//   frame_corners  neither, but the frame turns a corner there (an inner corner, where the diagonal block is missing)
// A face edge carries the frame unless the block beyond it is a Reservoir whose same face is also open (an edge where
// two Reservoirs join drops out); at a concave edge (the block beyond is a Reservoir but the block diagonally out in
// front of the face is one too) it stays. Faces toward another Reservoir are left out; the others are culled like glass.
// ReservoirRenderer draws the fluid inside.
final class ReservoirModel implements DynamicBlockStateModel {
    private enum Piece { GLASS, FRAME, FRAME_H, FRAME_V, CORNERS }

    private final Material.Baked particle;
    // [face][piece][quadrant: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right]
    private final Map<Direction, BakedQuad[][]> quads = new EnumMap<>(Direction.class);
    private final int materialFlags;

    ReservoirModel(ModelBaker baker, ConnectedModel.JsonModel unbaked, Identifier name) {
        Map<String, Identifier> textures = unbaked.textures();
        this.particle = ConnectedModel.Baked.material(baker, textures.getOrDefault("particle", textures.get("frame")), name);
        Material.Baked[] pieces = {
                ConnectedModel.Baked.material(baker, textures.get("glass"), name),
                ConnectedModel.Baked.material(baker, textures.get("frame"), name),
                ConnectedModel.Baked.material(baker, textures.get("frame_h"), name),
                ConnectedModel.Baked.material(baker, textures.get("frame_v"), name),
                ConnectedModel.Baked.material(baker, textures.get("frame_corners"), name) };
        int flags = 0;
        for (Direction face : Direction.values()) {
            BakedQuad[][] faceQuads = new BakedQuad[Piece.values().length][4];
            for (Piece piece : Piece.values()) {
                for (int quadrant = 0; quadrant < 4; quadrant++) {
                    float u0 = (quadrant & 1) != 0 ? 8 : 0, v0 = (quadrant & 2) != 0 ? 8 : 0;
                    BakedQuad quad = ConnectedModel.Baked.bake(baker, face, u0, v0, u0 + 8, v0 + 8, 0, pieces[piece.ordinal()],
                            u0, v0, u0 + 8, v0 + 8, Quadrant.R0);
                    faceQuads[piece.ordinal()][quadrant] = quad;
                    flags |= quad.materialInfo().flags();
                }
            }
            quads.put(face, faceQuads);
        }
        this.materialFlags = flags;
    }

    private static boolean isReservoir(BlockAndTintGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ReservoirBlock;
    }

    // Whether the face's edge toward side shows the frame.
    private static boolean framed(BlockAndTintGetter level, BlockPos pos, Direction face, Direction side) {
        BlockPos beyond = pos.relative(side);
        return !isReservoir(level, beyond) || isReservoir(level, beyond.relative(face));
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction face : Direction.values()) {
            if (isReservoir(level, pos.relative(face))) {
                continue;
            }
            Direction up = WindowQuadrants.up(face), right = WindowQuadrants.right(face);
            BakedQuad[][] faceQuads = quads.get(face);
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                Direction vertical = (quadrant & 2) != 0 ? up.getOpposite() : up;
                Direction horizontal = (quadrant & 1) != 0 ? right : right.getOpposite();
                boolean alongTop = framed(level, pos, face, vertical);
                boolean alongSide = framed(level, pos, face, horizontal);
                Piece piece;
                if (alongTop && alongSide) {
                    piece = Piece.FRAME;
                } else if (alongTop) {
                    piece = Piece.FRAME_H;
                } else if (alongSide) {
                    piece = Piece.FRAME_V;
                } else {
                    BlockPos diagonal = pos.relative(vertical).relative(horizontal);
                    piece = !isReservoir(level, diagonal) || isReservoir(level, diagonal.relative(face)) ? Piece.CORNERS : Piece.GLASS;
                }
                builder.addCulledFace(face, faceQuads[piece.ordinal()][quadrant]);
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
