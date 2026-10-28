/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How a worn Jetpack flies (see JetpackFlight), cycled with the Jetpack Mode key: Normal thrusts while jump is
// held, Hover holds altitude in the air (jump climbs, sneak sinks), Distance thrusts forward, Off doesn't fire.
public enum JetpackMode implements StringRepresentable {
    NORMAL("normal"),
    HOVER("hover"),
    OFF("off"),
    DISTANCE("distance");

    public static final Codec<JetpackMode> CODEC = StringRepresentable.fromEnum(JetpackMode::values);
    public static final StreamCodec<ByteBuf, JetpackMode> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], JetpackMode::ordinal);

    private final String name;

    JetpackMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public JetpackMode next() {
        // Keep the existing enum ordinals stable on the wire.
        return switch (this) {
            case NORMAL -> HOVER;
            case HOVER -> DISTANCE;
            case DISTANCE -> OFF;
            case OFF -> NORMAL;
        };
    }

    public Component displayName() {
        return Component.translatable("message.arcforge.jetpack.mode." + name);
    }
}
