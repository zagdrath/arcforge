/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Vitrified Waste Log (the arcforge:waste_log component): High-Level Liquid Waste set in borosilicate glass in a
// steel canister. Its radioactivity (activity, mSv/h at a block, when it was poured) and its warmth (decay heat, HU/t,
// in proportion) fall with nuclear.reprocessing.wasteLogHalfLife; made is when it was poured (game time). How long ago
// that was is worked out against the server's clock as last seen (NOW, kept by Fallout's level tick), so the dose
// rate of a log anywhere (a chest, a player's inventory) falls as it should.
public record WasteLog(float activity, long made) {
    public static final Codec<WasteLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("activity").forGetter(WasteLog::activity),
            Codec.LONG.optionalFieldOf("made", 0L).forGetter(WasteLog::made))
            .apply(i, WasteLog::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WasteLog> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WasteLog::activity,
            ByteBufCodecs.VAR_LONG, WasteLog::made,
            WasteLog::new);

    // A fresh log's decay heat (HU/t) per mSv/h of its activity.
    public static final double HEAT_PER_ACTIVITY = 0.02;
    // The server's game time as last seen (the overworld's), for decaying logs wherever they are.
    public static volatile long now;

    // Something of this activity after this many ticks.
    public static double decayed(double activity, long ticks) {
        if (activity <= 0.0 || ticks <= 0L) {
            return activity;
        }
        double days = ticks / 24_000.0;
        return activity * Math.pow(0.5, days / ArcforgeConfig.WASTE_LOG_HALF_LIFE.getAsDouble());
    }

    public double activityAt(long time) {
        return decayed(activity, time - made);
    }

    public double heatAt(long time) {
        return activityAt(time) * HEAT_PER_ACTIVITY;
    }

    // A stack's log's dose rate per item now (0 for anything else).
    public static double doseRate(ItemStack stack) {
        WasteLog log = stack.get(ModDataComponents.WASTE_LOG.get());
        return log != null ? log.activityAt(now) : 0.0;
    }
}
