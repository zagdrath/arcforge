/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.meter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

// A Meter's redstone setting: a signal while the rate is above (or below) the threshold. Kept on the item when the
// meter is broken (data component arcforge:meter_settings) and copied by the Settings Card.
public record MeterSettings(int threshold, Mode mode) {
    public static final MeterSettings DEFAULT = new MeterSettings(0, Mode.ABOVE);

    public enum Mode implements StringRepresentable {
        ABOVE("above"),
        BELOW("below");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Component displayName() {
            return Component.translatable("gui.arcforge.meter." + name);
        }

        public static Mode byId(int id) {
            return id == 1 ? BELOW : ABOVE;
        }
    }

    public static final Codec<MeterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("threshold", 0).forGetter(MeterSettings::threshold),
            Mode.CODEC.optionalFieldOf("mode", Mode.ABOVE).forGetter(MeterSettings::mode))
            .apply(i, MeterSettings::new));

    public static final StreamCodec<ByteBuf, MeterSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeterSettings::threshold,
            ByteBufCodecs.VAR_INT.map(Mode::byId, Mode::ordinal), MeterSettings::mode,
            MeterSettings::new);

    // Whether a rate sets the signal off.
    public boolean signals(int rate) {
        return mode == Mode.ABOVE ? rate > threshold : rate < threshold;
    }
}
