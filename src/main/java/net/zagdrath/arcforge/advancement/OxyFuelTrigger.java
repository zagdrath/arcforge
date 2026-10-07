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
import net.minecraft.server.level.ServerPlayer;

// arcforge:oxy_fuel: a Firebox or Fuel Burner is burning on oxy-fuel at this temperature. {min_temperature: °C}
public class OxyFuelTrigger extends SimpleCriterionTrigger<OxyFuelTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int celsius) {
        trigger(player, instance -> celsius >= instance.minTemperature());
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minTemperature) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.INT.optionalFieldOf("min_temperature", 0).forGetter(TriggerInstance::minTemperature))
                .apply(i, TriggerInstance::new));
    }
}
