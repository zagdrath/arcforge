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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

// arcforge:machine_linked: a Wired Machine Link has just linked a machine. {multiblock?: bool, min_distance?: int (blocks of
// Link Conduit)}
public class MachineLinkedTrigger extends SimpleCriterionTrigger<MachineLinkedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, boolean multiblock, int distance) {
        trigger(player, instance -> instance.matches(multiblock, distance));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<Boolean> multiblock, Optional<Integer> minDistance)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.BOOL.optionalFieldOf("multiblock").forGetter(TriggerInstance::multiblock),
                Codec.INT.optionalFieldOf("min_distance").forGetter(TriggerInstance::minDistance))
                .apply(i, TriggerInstance::new));

        boolean matches(boolean linkedMultiblock, int distance) {
            return multiblock.map(want -> want == linkedMultiblock).orElse(true) && minDistance.map(min -> distance >= min).orElse(true);
        }
    }
}
