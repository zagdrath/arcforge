/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.zagdrath.arcforge.machine.config.SideMode;

// The ports on one block: a mode for each of its six faces. Immutable, so it can be read while chunks mesh.
public final class PortFaces {
    public static final PortFaces NONE = new PortFaces(new SideMode[6]);

    private static final Codec<SideMode> MODE_CODEC = StringRepresentable.fromEnum(SideMode::values);
    public static final Codec<PortFaces> CODEC = Codec.unboundedMap(Direction.CODEC, MODE_CODEC).xmap(PortFaces::of, PortFaces::asMap);
    public static final StreamCodec<RegistryFriendlyByteBuf, PortFaces> STREAM_CODEC = StreamCodec.of(
            (buf, faces) -> {
                for (Direction face : Direction.values()) {
                    buf.writeByte(faces.get(face).ordinal());
                }
            },
            buf -> {
                SideMode[] modes = new SideMode[6];
                for (Direction face : Direction.values()) {
                    modes[face.get3DDataValue()] = SideMode.byId(buf.readByte());
                }
                return new PortFaces(modes);
            });

    // Indexed by Direction.get3DDataValue(); null is NONE.
    private final SideMode[] modes;

    private PortFaces(SideMode[] modes) {
        this.modes = modes;
    }

    private static PortFaces of(Map<Direction, SideMode> map) {
        SideMode[] modes = new SideMode[6];
        map.forEach((face, mode) -> modes[face.get3DDataValue()] = mode);
        return new PortFaces(modes);
    }

    private Map<Direction, SideMode> asMap() {
        Map<Direction, SideMode> map = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            if (get(face) != SideMode.NONE) {
                map.put(face, get(face));
            }
        }
        return map;
    }

    public SideMode get(Direction face) {
        SideMode mode = modes[face.get3DDataValue()];
        return mode != null ? mode : SideMode.NONE;
    }

    public PortFaces with(Direction face, SideMode mode) {
        SideMode[] copy = modes.clone();
        copy[face.get3DDataValue()] = mode == SideMode.NONE ? null : mode;
        return new PortFaces(copy);
    }

    public boolean isEmpty() {
        for (SideMode mode : modes) {
            if (mode != null && mode != SideMode.NONE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PortFaces faces && Arrays.equals(normal(), faces.normal());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(normal());
    }

    private SideMode[] normal() {
        SideMode[] normal = new SideMode[6];
        for (Direction face : Direction.values()) {
            normal[face.get3DDataValue()] = get(face);
        }
        return normal;
    }
}
