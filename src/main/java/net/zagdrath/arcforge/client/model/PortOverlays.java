/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;

import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.machine.config.SideMode;

// The plates marking a multiblock's ports (see MultiblockPorts), baked onto the outer faces of the port
// blocks: arcforge:block/port/<mode> (a ring, blue in and orange out, round a chip in the resource's colour),
// one full face per mode and side, just outside the face and above the connected textures' beams.
public final class PortOverlays {
    // In pixels, like ConnectedModel's overlays.
    static final float OFFSET = 0.06F;

    private final Map<SideMode, Map<Direction, BakedQuad>> quads = new EnumMap<>(SideMode.class);
    private final int materialFlags;

    public PortOverlays(ModelBaker baker, Identifier name) {
        int flags = 0;
        for (SideMode mode : SideMode.values()) {
            if (mode == SideMode.NONE) {
                continue;
            }
            Material.Baked texture = ConnectedModel.Baked.material(baker, texture(mode), name);
            Map<Direction, BakedQuad> faces = new EnumMap<>(Direction.class);
            for (Direction face : Direction.values()) {
                BakedQuad quad = ConnectedModel.Baked.bake(baker, face, 0, 0, 16, 16, OFFSET, texture, 0, 0, 16, 16, Quadrant.R0);
                faces.put(face, quad);
                flags |= quad.materialInfo().flags();
            }
            quads.put(mode, faces);
        }
        this.materialFlags = flags;
    }

    public static Identifier texture(SideMode mode) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/port/" + mode.getSerializedName());
    }

    // The plate for a port in this mode on this face, or null for NONE.
    public @Nullable BakedQuad get(SideMode mode, Direction face) {
        Map<Direction, BakedQuad> faces = quads.get(mode);
        return faces != null ? faces.get(face) : null;
    }

    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return materialFlags;
    }
}
