/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.advancement;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

// arcforge:machine_produced: a machine or multiblock put something out. {item?: item predicate, fluid?: id,
// recipe_category?: the recipe's category}; every condition given must match.
public class MachineProducedTrigger extends SimpleCriterionTrigger<MachineProducedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item, @Nullable Fluid fluid, @Nullable String category) {
        trigger(player, instance -> instance.matches(item, fluid, category));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<ItemPredicate> item, Optional<Identifier> fluid,
            Optional<String> recipeCategory) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item),
                Identifier.CODEC.optionalFieldOf("fluid").forGetter(TriggerInstance::fluid),
                Codec.STRING.optionalFieldOf("recipe_category").forGetter(TriggerInstance::recipeCategory))
                .apply(i, TriggerInstance::new));

        boolean matches(ItemStack made, @Nullable Fluid madeFluid, @Nullable String category) {
            if (item.isPresent() && (made.isEmpty() || !item.get().test(made))) {
                return false;
            }
            if (fluid.isPresent() && (madeFluid == null || !fluid.get().equals(BuiltInRegistries.FLUID.getKey(madeFluid)))) {
                return false;
            }
            return recipeCategory.isEmpty() || recipeCategory.get().equals(category);
        }
    }
}
