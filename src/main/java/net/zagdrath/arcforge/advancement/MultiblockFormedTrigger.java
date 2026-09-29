/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

// arcforge:multiblock_formed: a structure went from unformed to formed. {multiblock: id, min_length?: int}
public class MultiblockFormedTrigger extends SimpleCriterionTrigger<MultiblockFormedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier multiblock, int length) {
        trigger(player, instance -> instance.matches(multiblock, length));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Identifier multiblock, Optional<Integer> minLength)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Identifier.CODEC.fieldOf("multiblock").forGetter(TriggerInstance::multiblock),
                Codec.INT.optionalFieldOf("min_length").forGetter(TriggerInstance::minLength))
                .apply(i, TriggerInstance::new));

        boolean matches(Identifier formed, int length) {
            return multiblock.equals(formed) && minLength.map(min -> length >= min).orElse(true);
        }
    }
}
