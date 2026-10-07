/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

// arcforge:turbine_full_speed: a turbine array is at full speed. {multiblock: id, min_length?: int, min_signal?: int}
public class TurbineFullSpeedTrigger extends SimpleCriterionTrigger<TurbineFullSpeedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier multiblock, int length, int signal) {
        trigger(player, instance -> instance.matches(multiblock, length, signal));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Identifier multiblock, Optional<Integer> minLength,
            Optional<Integer> minSignal) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Identifier.CODEC.fieldOf("multiblock").forGetter(TriggerInstance::multiblock),
                Codec.INT.optionalFieldOf("min_length").forGetter(TriggerInstance::minLength),
                Codec.INT.optionalFieldOf("min_signal").forGetter(TriggerInstance::minSignal))
                .apply(i, TriggerInstance::new));

        boolean matches(Identifier turbine, int length, int signal) {
            return multiblock.equals(turbine) && minLength.map(min -> length >= min).orElse(true)
                    && minSignal.map(min -> signal >= min).orElse(true);
        }
    }
}
