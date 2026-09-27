/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.conduit.ConnectionMode;

// Snapshot of a glass conduit's contents for one frame.
public class ConduitRenderState extends BlockEntityRenderState {
    public final ConnectionMode[] sides = new ConnectionMode[Direction.values().length];
    public Direction.@Nullable Axis straight;

    // Fluid conduits.
    public @Nullable TextureAtlasSprite fluidSprite;
    public int fluidColor = -1;
    public int fluidLight;
    public float fill;

    // Item conduits.
    public final List<Item> items = new ArrayList<>();

    public record Item(ItemStackRenderState state, Vec3 position, float spin) {}
}
