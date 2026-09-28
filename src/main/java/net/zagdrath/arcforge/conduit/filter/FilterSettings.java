/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Conduit Filter's settings, kept on the filter item (arcforge:conduit_filter) and so on the conduit side it
// is installed on: nine entries, allowlist or denylist, whether item components must match, and which way
// it applies. An item with no component is an unconfigured filter and behaves as DEFAULT (an empty
// allowlist, which lets nothing through); the component is only written once the GUI changes something,
// so fresh filters keep stacking.
public record FilterSettings(List<Entry> entries, boolean deny, boolean ignoreComponents, Flow flow) {
    public static final int SIZE = 9;

    // Which way the filter applies: to what the network inserts into the machine, what it extracts from it, or both.
    public enum Flow implements StringRepresentable {
        INSERT("insert"),
        EXTRACT("extract"),
        BOTH("both");

        public static final Codec<Flow> CODEC = StringRepresentable.fromEnum(Flow::values);
        public static final StreamCodec<ByteBuf, Flow> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], Flow::ordinal);

        private final String name;

        Flow(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Flow next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Component displayName() {
            return Component.translatable("gui.arcforge.conduit_filter.dir_" + name);
        }
    }

    // How a filter looks: its LED is grey until an entry is set, then green for an allowlist and red for a denylist.
    public enum Mode implements StringRepresentable {
        UNSET("unset"),
        ALLOW("allow"),
        DENY("deny");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // One slot: an item (item conduits) or a fluid (fluid and pressurized conduits), matched exactly or by one
    // of its tags (an item tag for items, a fluid tag for fluids).
    public record Entry(Optional<ItemStackTemplate> item, Optional<Fluid> fluid, Optional<Identifier> tag) {
        public static final Entry EMPTY = new Entry(Optional.empty(), Optional.empty(), Optional.empty());

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(Entry::item),
                BuiltInRegistries.FLUID.byNameCodec().optionalFieldOf("fluid").forGetter(Entry::fluid),
                Identifier.CODEC.optionalFieldOf("tag").forGetter(Entry::tag))
                .apply(i, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), Entry::item,
                ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.FLUID)), Entry::fluid,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Entry::tag,
                Entry::new);

        // The item alone (count 1, components kept), matched exactly.
        public static Entry of(ItemStack stack) {
            return stack.isEmpty() ? EMPTY : new Entry(Optional.of(ItemStackTemplate.fromNonEmptyStack(stack, 1)), Optional.empty(), Optional.empty());
        }

        public static Entry of(Fluid fluid) {
            return fluid == Fluids.EMPTY ? EMPTY : new Entry(Optional.empty(), Optional.of(fluid), Optional.empty());
        }

        public boolean isEmpty() {
            return item.isEmpty() && fluid.isEmpty();
        }

        public Entry withTag(@Nullable Identifier id) {
            return new Entry(item, fluid, Optional.ofNullable(id));
        }

        // The tags this entry can match by, sorted by id: what its chip cycles through.
        public List<Identifier> availableTags() {
            List<Identifier> tags = new ArrayList<>();
            item.ifPresent(template -> template.tags().forEach(tag -> tags.add(tag.location())));
            fluid.ifPresent(f -> f.defaultFluidState().tags().forEach(tag -> tags.add(tag.location())));
            tags.sort(Comparator.comparing(Identifier::toString));
            return tags;
        }

        // Exact -> first tag -> ... -> last tag -> Exact (steps = 1), or backwards (steps = -1).
        public Entry cycleTag(int steps) {
            List<Identifier> tags = availableTags();
            if (isEmpty() || tags.isEmpty()) {
                return withTag(null);
            }
            // Position 0 is Exact, 1..n the tags.
            int current = tag.map(id -> tags.indexOf(id) + 1).orElse(0);
            int next = Math.floorMod(current + steps, tags.size() + 1);
            return withTag(next == 0 ? null : tags.get(next - 1));
        }

        boolean matches(ItemStack stack, boolean ignoreComponents) {
            if (item.isEmpty()) {
                return false;
            }
            if (tag.isPresent()) {
                return stack.is(TagKey.create(Registries.ITEM, tag.get()));
            }
            ItemStackTemplate template = item.get();
            return ignoreComponents ? stack.is(template.item()) : ItemStack.isSameItemSameComponents(stack, template);
        }

        boolean matches(Fluid other) {
            if (fluid.isEmpty()) {
                return false;
            }
            if (tag.isPresent()) {
                return other.defaultFluidState().is(TagKey.create(Registries.FLUID, tag.get()));
            }
            return other.isSame(fluid.get());
        }
    }

    public static final Codec<FilterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.sizeLimitedListOf(SIZE).fieldOf("entries").forGetter(FilterSettings::entries),
            Codec.BOOL.optionalFieldOf("deny", false).forGetter(FilterSettings::deny),
            Codec.BOOL.optionalFieldOf("ignore_components", false).forGetter(FilterSettings::ignoreComponents),
            Flow.CODEC.optionalFieldOf("direction", Flow.INSERT).forGetter(FilterSettings::flow))
            .apply(i, FilterSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterSettings> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), FilterSettings::entries,
            ByteBufCodecs.BOOL, FilterSettings::deny,
            ByteBufCodecs.BOOL, FilterSettings::ignoreComponents,
            Flow.STREAM_CODEC.cast(), FilterSettings::flow,
            FilterSettings::new);

    public static final FilterSettings DEFAULT = new FilterSettings(Collections.nCopies(SIZE, Entry.EMPTY), false, false, Flow.INSERT);

    // Always exactly SIZE entries, whatever was saved.
    public FilterSettings {
        List<Entry> padded = new ArrayList<>(entries.subList(0, Math.min(SIZE, entries.size())));
        while (padded.size() < SIZE) {
            padded.add(Entry.EMPTY);
        }
        entries = List.copyOf(padded);
    }

    // The settings on a filter item (DEFAULT if it has none).
    public static FilterSettings of(ItemStack filter) {
        return filter.getOrDefault(ModDataComponents.CONDUIT_FILTER.get(), DEFAULT);
    }

    // Unset without a component or with every entry empty; otherwise allow or deny.
    public static Mode mode(ItemStack filter) {
        FilterSettings settings = filter.get(ModDataComponents.CONDUIT_FILTER.get());
        if (settings == null || settings.count() == 0) {
            return Mode.UNSET;
        }
        return settings.deny ? Mode.DENY : Mode.ALLOW;
    }

    // How many entries are set.
    public int count() {
        return (int) entries.stream().filter(entry -> !entry.isEmpty()).count();
    }

    public Entry entry(int index) {
        return entries.get(index);
    }

    public FilterSettings withEntry(int index, Entry entry) {
        List<Entry> updated = new ArrayList<>(entries);
        updated.set(index, entry);
        return new FilterSettings(updated, deny, ignoreComponents, flow);
    }

    public FilterSettings withDeny(boolean value) {
        return new FilterSettings(entries, value, ignoreComponents, flow);
    }

    public FilterSettings withIgnoreComponents(boolean value) {
        return new FilterSettings(entries, deny, value, flow);
    }

    public FilterSettings withFlow(Flow value) {
        return new FilterSettings(entries, deny, ignoreComponents, value);
    }

    // Whether the filter has a say when the network inserts into (true) or extracts from (false) the machine.
    public boolean appliesTo(boolean inserting) {
        return flow == Flow.BOTH || (inserting ? flow == Flow.INSERT : flow == Flow.EXTRACT);
    }

    // An empty allowlist lets nothing through; an empty denylist lets everything through.
    public boolean matches(ItemStack stack) {
        boolean hit = entries.stream().anyMatch(entry -> entry.matches(stack, ignoreComponents));
        return deny != hit;
    }

    // Fluids and gases: components never count.
    public boolean matches(Fluid fluid) {
        boolean hit = entries.stream().anyMatch(entry -> entry.matches(fluid));
        return deny != hit;
    }

    public Component listName() {
        return Component.translatable(deny ? "gui.arcforge.conduit_filter.denylist" : "gui.arcforge.conduit_filter.allowlist");
    }
}
