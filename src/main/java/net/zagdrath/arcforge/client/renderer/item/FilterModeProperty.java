/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.item;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;

// Select item model property "arcforge:filter_mode": a Conduit Filter's look, "unset", "allow" or "deny" (see
// FilterSettings.mode). The filter's item model switches its LED on it.
public record FilterModeProperty() implements SelectItemModelProperty<FilterSettings.Mode> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "filter_mode");
    public static final SelectItemModelProperty.Type<FilterModeProperty, FilterSettings.Mode> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new FilterModeProperty()), FilterSettings.Mode.CODEC);

    @Override
    public FilterSettings.Mode get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return FilterSettings.mode(stack);
    }

    @Override
    public Codec<FilterSettings.Mode> valueCodec() {
        return FilterSettings.Mode.CODEC;
    }

    @Override
    public SelectItemModelProperty.Type<FilterModeProperty, FilterSettings.Mode> type() {
        return TYPE;
    }
}
