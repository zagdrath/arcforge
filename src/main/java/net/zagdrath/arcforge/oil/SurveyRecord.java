/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// What a Survey Record holds (the arcforge:survey_record component): where the survey was made (dimension and block x,
// z), and what it found there: the deposit's kind (empty for no oil), the buckets left at the time, the depth of its
// oil (the cavern's top there, or the deposit's depth), and whether it's sour and has a gas cap.
public record SurveyRecord(String dimension, int x, int z, Optional<DepositKind> kind, long buckets, int y, boolean sour, boolean gasCap) {
    public static final Codec<DepositKind> KIND_CODEC = Codec.STRING.xmap(DepositKind::byName, DepositKind::getSerializedName);
    public static final Codec<SurveyRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(SurveyRecord::dimension),
            Codec.INT.fieldOf("x").forGetter(SurveyRecord::x),
            Codec.INT.fieldOf("z").forGetter(SurveyRecord::z),
            KIND_CODEC.optionalFieldOf("kind").forGetter(SurveyRecord::kind),
            Codec.LONG.optionalFieldOf("buckets", 0L).forGetter(SurveyRecord::buckets),
            Codec.INT.optionalFieldOf("y", 0).forGetter(SurveyRecord::y),
            Codec.BOOL.optionalFieldOf("sour", false).forGetter(SurveyRecord::sour),
            Codec.BOOL.optionalFieldOf("gas_cap", false).forGetter(SurveyRecord::gasCap))
            .apply(i, SurveyRecord::new));
    private static final StreamCodec<ByteBuf, Optional<DepositKind>> KIND_STREAM = ByteBufCodecs.optional(
            ByteBufCodecs.VAR_INT.map(ordinal -> DepositKind.values()[Math.floorMod(ordinal, DepositKind.values().length)], DepositKind::ordinal));
    public static final StreamCodec<ByteBuf, SurveyRecord> STREAM_CODEC = StreamCodec.of(
            (buf, record) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, record.dimension());
                ByteBufCodecs.VAR_INT.encode(buf, record.x());
                ByteBufCodecs.VAR_INT.encode(buf, record.z());
                KIND_STREAM.encode(buf, record.kind());
                ByteBufCodecs.VAR_LONG.encode(buf, record.buckets());
                ByteBufCodecs.VAR_INT.encode(buf, record.y());
                ByteBufCodecs.BOOL.encode(buf, record.sour());
                ByteBufCodecs.BOOL.encode(buf, record.gasCap());
            },
            buf -> new SurveyRecord(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    KIND_STREAM.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));
}
