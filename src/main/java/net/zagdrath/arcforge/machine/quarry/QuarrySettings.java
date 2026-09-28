/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.quarry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.LevelHeightAccessor;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// An Arc Quarry's settings, kept on the item when it's picked up (arcforge:quarry_settings): the area (a square
// `radius` blocks out from the quarry, from minY to maxY), the block filter (18 entries, allow or deny; see
// BlockFilter), and the Silk Touch, Replace and show-area switches.
public record QuarrySettings(int radius, int minY, int maxY, List<FilterSettings.Entry> filter, boolean deny, boolean silkTouch,
        boolean replace, boolean showArea) {
    public static final int FILTER_SIZE = 18;

    public static final Codec<QuarrySettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("radius").forGetter(QuarrySettings::radius),
            Codec.INT.fieldOf("min_y").forGetter(QuarrySettings::minY),
            Codec.INT.fieldOf("max_y").forGetter(QuarrySettings::maxY),
            FilterSettings.Entry.CODEC.sizeLimitedListOf(FILTER_SIZE).optionalFieldOf("filter", List.of()).forGetter(QuarrySettings::filter),
            Codec.BOOL.optionalFieldOf("deny", false).forGetter(QuarrySettings::deny),
            Codec.BOOL.optionalFieldOf("silk_touch", false).forGetter(QuarrySettings::silkTouch),
            Codec.BOOL.optionalFieldOf("replace", true).forGetter(QuarrySettings::replace),
            Codec.BOOL.optionalFieldOf("show_area", false).forGetter(QuarrySettings::showArea))
            .apply(i, QuarrySettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuarrySettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuarrySettings::radius,
            ByteBufCodecs.INT, QuarrySettings::minY,
            ByteBufCodecs.INT, QuarrySettings::maxY,
            FilterSettings.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(FILTER_SIZE)), QuarrySettings::filter,
            ByteBufCodecs.BOOL, QuarrySettings::deny,
            ByteBufCodecs.BOOL, QuarrySettings::silkTouch,
            ByteBufCodecs.BOOL, QuarrySettings::replace,
            ByteBufCodecs.BOOL, QuarrySettings::showArea,
            QuarrySettings::new);

    // Always exactly FILTER_SIZE entries, whatever was saved.
    public QuarrySettings {
        List<FilterSettings.Entry> padded = new ArrayList<>(filter.subList(0, Math.min(FILTER_SIZE, filter.size())));
        while (padded.size() < FILTER_SIZE) {
            padded.add(FilterSettings.Entry.EMPTY);
        }
        filter = List.copyOf(padded);
    }

    // A new quarry: the configured area, an empty allowlist (which mines nothing until set), Replace on.
    public static QuarrySettings defaults() {
        return new QuarrySettings(ArcforgeConfig.QUARRY_DEFAULT_RADIUS.getAsInt(), ArcforgeConfig.QUARRY_DEFAULT_MIN_Y.getAsInt(),
                ArcforgeConfig.QUARRY_DEFAULT_MAX_Y.getAsInt(), Collections.nCopies(FILTER_SIZE, FilterSettings.Entry.EMPTY),
                false, false, true, false);
    }

    public FilterSettings.Entry entry(int index) {
        return filter.get(index);
    }

    public int count() {
        return (int) filter.stream().filter(entry -> !entry.isEmpty() || entry.tag().isPresent()).count();
    }

    // How many blocks the area holds: (2r+1)² × (maxY − minY + 1).
    public long areaVolume() {
        long side = 2L * radius + 1;
        return side * side * (maxY - minY + 1L);
    }

    public int side() {
        return 2 * radius + 1;
    }

    // --- Changes. The area is clamped against the level and the other bound (see QuarrySettings.clamped). ---

    public QuarrySettings withRadius(int value) {
        return new QuarrySettings(Math.max(0, Math.min(ArcforgeConfig.QUARRY_MAX_RADIUS.getAsInt(), value)), minY, maxY, filter, deny,
                silkTouch, replace, showArea);
    }

    // The lowest layer, no lower than the level's floor and no higher than maxY.
    public QuarrySettings withMinY(int value, LevelHeightAccessor level) {
        return new QuarrySettings(radius, Math.max(level.getMinY(), Math.min(maxY, value)), maxY, filter, deny, silkTouch, replace, showArea);
    }

    // The highest layer, no lower than minY and no higher than the level's top layer.
    public QuarrySettings withMaxY(int value, LevelHeightAccessor level) {
        return new QuarrySettings(radius, minY, Math.max(minY, Math.min(level.getMaxY(), value)), filter, deny, silkTouch, replace, showArea);
    }

    // All three at once (from the settings screen): radius, then minY against the current maxY, then maxY against
    // the new minY. Values that didn't change are left alone, so each is clamped against the other one as it stands.
    public QuarrySettings withArea(int radius, int minY, int maxY, LevelHeightAccessor level) {
        QuarrySettings result = withRadius(radius);
        if (minY != this.minY) {
            result = result.withMinY(minY, level);
        }
        if (maxY != this.maxY) {
            result = result.withMaxY(maxY, level);
        }
        // The level's bounds still apply to both.
        return result.withMinY(result.minY, level).withMaxY(result.maxY, level);
    }

    public QuarrySettings withEntry(int index, FilterSettings.Entry entry) {
        List<FilterSettings.Entry> updated = new ArrayList<>(filter);
        updated.set(index, entry);
        return new QuarrySettings(radius, minY, maxY, updated, deny, silkTouch, replace, showArea);
    }

    public QuarrySettings withDeny(boolean value) {
        return new QuarrySettings(radius, minY, maxY, filter, value, silkTouch, replace, showArea);
    }

    public QuarrySettings withSilkTouch(boolean value) {
        return new QuarrySettings(radius, minY, maxY, filter, deny, value, replace, showArea);
    }

    public QuarrySettings withReplace(boolean value) {
        return new QuarrySettings(radius, minY, maxY, filter, deny, silkTouch, value, showArea);
    }

    public QuarrySettings withShowArea(boolean value) {
        return new QuarrySettings(radius, minY, maxY, filter, deny, silkTouch, replace, value);
    }
}
