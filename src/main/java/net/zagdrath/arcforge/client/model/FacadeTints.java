/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitFacades;

// Facade colours: a facade of a tinted block (grass, leaves...) takes the target's own tint at the conduit's position.
// FacadeQuads moves each target tint layer to FacadeQuads.FIRST_TINT + side * TINTS + layer; these sources look the
// facade up and ask the target's tint source for that layer. They follow a conduit's own sources (its glow and dye).
public final class FacadeTints {
    private FacadeTints() {}

    // The sources for tint indices FIRST_TINT onward, in order.
    public static List<BlockTintSource> sources(BlockColors colors) {
        List<BlockTintSource> sources = new ArrayList<>(6 * FacadeQuads.TINTS);
        for (Direction side : Direction.values()) {
            for (int layer = 0; layer < FacadeQuads.TINTS; layer++) {
                sources.add(new Source(colors, side, layer));
            }
        }
        return sources;
    }

    private record Source(BlockColors colors, Direction side, int layer) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            ConduitFacades facades = level.getModelData(pos).get(ConduitBlockEntity.FACADES);
            BlockState target = facades != null ? facades.target(side) : null;
            BlockTintSource source = target != null ? colors.getTintSource(target, layer) : null;
            return source != null ? source.colorInWorld(target, level, pos) : -1;
        }
    }
}
