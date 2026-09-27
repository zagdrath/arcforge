/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What the Wrench does (see WrenchItem), cycled with Shift + mouse wheel. The colour is its grip's.
public enum WrenchMode implements StringRepresentable {
    CONFIGURE("configure", 0x5FD4C4),
    ROTATE("rotate", 0x5CC04A),
    PORT("port", 0xE8913A),
    DISMANTLE("dismantle", 0xE5483C);

    public static final Codec<WrenchMode> CODEC = StringRepresentable.fromEnum(WrenchMode::values);
    public static final StreamCodec<ByteBuf, WrenchMode> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], WrenchMode::ordinal);

    private final String name;
    private final int color;

    WrenchMode(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int color() {
        return color;
    }

    // The mode steps away (wrapping), e.g. +1 for the next one.
    public WrenchMode cycle(int steps) {
        int count = values().length;
        return values()[Math.floorMod(ordinal() + steps, count)];
    }

    public MutableComponent displayName() {
        return Component.translatable("wrench_mode.arcforge." + name).withColor(color);
    }
}
