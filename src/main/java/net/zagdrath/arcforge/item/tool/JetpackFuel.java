/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.registry.ModDataMaps;

// What a gas does in a Jetpack (data map arcforge:jetpack_fuels, keyed by fluid): the upward push it adds each
// tick it fires, the mB it burns per tick, and whether it puffs steam or burns with a flame. Gravity is 0.08,
// so a thrust above that climbs.
public record JetpackFuel(float thrust, int mbPerTick, Exhaust exhaust) {
    public enum Exhaust implements StringRepresentable {
        STEAM("steam"),
        FLAME("flame");

        public static final Codec<Exhaust> CODEC = StringRepresentable.fromEnum(Exhaust::values);

        private final String name;

        Exhaust(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // The thrust that counts as full output (Hydrogen's), for effects.
    public static final float FULL_THRUST = 0.2F;

    public static final Codec<JetpackFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("thrust").forGetter(JetpackFuel::thrust),
            ExtraCodecs.POSITIVE_INT.fieldOf("mb_per_tick").forGetter(JetpackFuel::mbPerTick),
            Exhaust.CODEC.fieldOf("particle").forGetter(JetpackFuel::exhaust))
            .apply(i, JetpackFuel::new));

    public static @Nullable JetpackFuel of(Fluid fluid) {
        return BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(ModDataMaps.JETPACK_FUELS);
    }

    // Output for effects, 0 to 1: this fuel's thrust against Hydrogen's.
    public float output() {
        return Math.min(1.0F, thrust / FULL_THRUST);
    }
}
