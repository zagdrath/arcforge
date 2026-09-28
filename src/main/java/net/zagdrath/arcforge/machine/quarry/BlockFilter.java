/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.quarry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;

// FilterSettings.Entry read for blocks, for the Arc Quarry: an item entry (a block's item) matches that block, and a
// tag entry matches a BLOCK tag. A tag can also stand alone, typed in, with no item. An empty allowlist matches
// nothing; an empty denylist matches everything. Conduit filters are unchanged.
public final class BlockFilter {
    private BlockFilter() {}

    // Whether the entry says anything: an item, or a tag on its own.
    public static boolean isSet(FilterSettings.Entry entry) {
        return entry.item().isPresent() || entry.tag().isPresent();
    }

    // An entry for a block's item (null for items that aren't blocks).
    public static FilterSettings.@Nullable Entry of(ItemStack stack) {
        return stack.getItem() instanceof BlockItem ? FilterSettings.Entry.of(stack.copyWithCount(1)) : null;
    }

    // An entry for a block tag alone.
    public static FilterSettings.Entry ofTag(Identifier tag) {
        return new FilterSettings.Entry(Optional.empty(), Optional.empty(), Optional.of(tag));
    }

    public static TagKey<Block> tag(Identifier id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    // Whether a block tag with this id exists.
    public static boolean tagExists(Identifier id) {
        return BuiltInRegistries.BLOCK.getTags().anyMatch(set -> set.key().location().equals(id));
    }

    public static @Nullable Block block(FilterSettings.Entry entry) {
        return entry.item().isPresent() && entry.item().get().item().value() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    public static boolean matches(FilterSettings.Entry entry, BlockState state) {
        if (entry.tag().isPresent()) {
            return state.is(tag(entry.tag().get()));
        }
        Block block = block(entry);
        return block != null && state.is(block);
    }

    // Whether the settings' filter picks this block.
    public static boolean matches(QuarrySettings settings, BlockState state) {
        boolean hit = settings.filter().stream().anyMatch(entry -> isSet(entry) && matches(entry, state));
        return settings.deny() != hit;
    }

    // The block tags an item entry can match by, sorted by id: what its chip cycles through.
    public static List<Identifier> availableTags(FilterSettings.Entry entry) {
        Block block = block(entry);
        if (block == null) {
            return List.of();
        }
        return block.builtInRegistryHolder().tags().map(TagKey::location).sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    // Exact -> first tag -> ... -> last tag -> Exact (steps = 1), or backwards (steps = -1). A tag on its own stays.
    public static FilterSettings.Entry cycleTag(FilterSettings.Entry entry, int steps) {
        List<Identifier> tags = availableTags(entry);
        if (entry.item().isEmpty() || tags.isEmpty()) {
            return entry;
        }
        int current = entry.tag().map(id -> tags.indexOf(id) + 1).orElse(0);
        int next = Math.floorMod(current + steps, tags.size() + 1);
        return entry.withTag(next == 0 ? null : tags.get(next - 1));
    }

    // A block to show for an entry: its own, or the first in its tag.
    public static ItemStack icon(FilterSettings.Entry entry) {
        if (entry.item().isPresent()) {
            return entry.item().get().create();
        }
        return entry.tag().flatMap(id -> BuiltInRegistries.BLOCK.get(tag(id)))
                .flatMap(set -> set.stream().map(holder -> new ItemStack(holder.value())).filter(stack -> !stack.isEmpty()).findFirst())
                .orElse(ItemStack.EMPTY);
    }

    // Tag ids containing the text (those starting with it first), for the settings screen's suggestions.
    public static List<Identifier> suggest(String text, int limit) {
        String query = text.startsWith("#") ? text.substring(1) : text;
        if (query.isEmpty()) {
            return List.of();
        }
        return BuiltInRegistries.BLOCK.getTags().map(set -> set.key().location())
                .filter(id -> id.toString().contains(query))
                .sorted(Comparator.<Identifier, Boolean>comparing(id -> !id.toString().startsWith(query) && !id.getPath().startsWith(query))
                        .thenComparing(Identifier::toString))
                .limit(limit)
                .toList();
    }
}
