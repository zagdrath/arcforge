/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.storage;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

// What a Vault holds while it's an item (arcforge:vault_contents): the item type (count 1; absent when it holds
// nothing and isn't locked to anything), how many, and its lock and void settings. An empty, unlocked vault
// with void off carries no component, so fresh vaults stack.
public record VaultContents(Optional<ItemStackTemplate> item, int amount, boolean locked, boolean voidMode) {
    public static final VaultContents EMPTY = new VaultContents(Optional.empty(), 0, false, false);

    public static final Codec<VaultContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(VaultContents::item),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amount", 0).forGetter(VaultContents::amount),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(VaultContents::locked),
            Codec.BOOL.optionalFieldOf("void", false).forGetter(VaultContents::voidMode))
            .apply(i, VaultContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), VaultContents::item,
            ByteBufCodecs.VAR_INT, VaultContents::amount,
            ByteBufCodecs.BOOL, VaultContents::locked,
            ByteBufCodecs.BOOL, VaultContents::voidMode,
            VaultContents::new);

    public static VaultContents of(ItemStack template, int amount, boolean locked, boolean voidMode) {
        return new VaultContents(template.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(template.copyWithCount(1))),
                amount, locked, voidMode);
    }

    // The item type as a count-1 stack (EMPTY if none).
    public ItemStack template() {
        return item.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }
}
