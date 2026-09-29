/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Who besides its owner may use a block: anyone, the owner's trusted players, or no one.
public enum SecurityMode implements StringRepresentable {
    PUBLIC("public"),
    TRUSTED("trusted"),
    PRIVATE("private");

    public static final Codec<SecurityMode> CODEC = StringRepresentable.fromEnum(SecurityMode::values);
    public static final StreamCodec<ByteBuf, SecurityMode> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], SecurityMode::ordinal);

    private final String name;

    SecurityMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static SecurityMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : PUBLIC;
    }

    public Component displayName() {
        return Component.translatable("security.arcforge." + name);
    }
}
