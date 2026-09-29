/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// The modules in an Arc Drill or Arc Saw (data component arcforge:tool_modules): four slots, of which the tool's
// tier opens the first two to four; bit i of `enabled` is whether slot i's module is on; `cursor` is the slot
// sneak + scroll last flipped. One of each type, one Fortune at most, and Silk Touch and Fortune never both on.
public record ToolModules(List<Optional<ModuleType>> slots, int enabled, int cursor) {
    public static final int SIZE = 4;
    public static final ToolModules EMPTY = new ToolModules(List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()), 0, 0);

    // An empty slot is "", so the list keeps its positions.
    private static final Codec<Optional<ModuleType>> SLOT_CODEC = Codec.STRING.xmap(
            name -> name.isEmpty() ? Optional.empty() : Optional.ofNullable(byName(name)),
            slot -> slot.map(ModuleType::getSerializedName).orElse(""));
    private static final StreamCodec<ByteBuf, Optional<ModuleType>> SLOT_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> id == 0 ? Optional.empty() : Optional.of(ModuleType.values()[id - 1]),
            slot -> slot.map(type -> type.ordinal() + 1).orElse(0));

    public static final Codec<ToolModules> CODEC = RecordCodecBuilder.create(i -> i.group(
            SLOT_CODEC.listOf(SIZE, SIZE).fieldOf("slots").forGetter(ToolModules::slots),
            Codec.INT.optionalFieldOf("enabled", 0).forGetter(ToolModules::enabled),
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(ToolModules::cursor))
            .apply(i, ToolModules::new));
    public static final StreamCodec<ByteBuf, ToolModules> STREAM_CODEC = StreamCodec.composite(
            SLOT_STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), ToolModules::slots,
            ByteBufCodecs.VAR_INT, ToolModules::enabled,
            ByteBufCodecs.VAR_INT, ToolModules::cursor,
            ToolModules::new);

    public ToolModules {
        slots = List.copyOf(slots);
    }

    private static @Nullable ModuleType byName(String name) {
        for (ModuleType type : ModuleType.values()) {
            if (type.getSerializedName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    public Optional<ModuleType> slot(int index) {
        return slots.get(index);
    }

    public boolean isOn(int index) {
        return slot(index).isPresent() && (enabled & (1 << index)) != 0;
    }

    public boolean has(ModuleType type) {
        return slots.stream().anyMatch(slot -> slot.isPresent() && slot.get() == type);
    }

    public boolean isOn(ModuleType type) {
        for (int index = 0; index < SIZE; index++) {
            if (slot(index).isPresent() && slot(index).get() == type && isOn(index)) {
                return true;
            }
        }
        return false;
    }

    public List<ModuleType> enabledTypes() {
        List<ModuleType> types = new ArrayList<>();
        for (int index = 0; index < SIZE; index++) {
            if (isOn(index)) {
                types.add(slot(index).get());
            }
        }
        return types;
    }

    public int fortuneLevel() {
        return enabledTypes().stream().mapToInt(ModuleType::fortuneLevel).max().orElse(0);
    }

    // Whether `type` could go in a slot (other than `except`): not already installed, and no second Fortune.
    public boolean canInstall(ModuleType type, int except) {
        for (int index = 0; index < SIZE; index++) {
            if (index != except && slot(index).isPresent()
                    && (slot(index).get() == type || (type.isFortune() && slot(index).get().isFortune()))) {
                return false;
            }
        }
        return true;
    }

    // Sets a slot. A newly installed module starts on, unless it conflicts with one already on.
    public ToolModules withSlot(int index, Optional<ModuleType> type) {
        List<Optional<ModuleType>> changed = new ArrayList<>(slots);
        changed.set(index, type);
        int mask = enabled & ~(1 << index);
        if (type.isPresent() && enabledTypes().stream().noneMatch(on -> on.conflictsWith(type.get()))) {
            mask |= 1 << index;
        }
        return new ToolModules(changed, mask, cursor);
    }

    // Flips a slot's module. Turning one on turns off any it conflicts with.
    public ToolModules toggle(int index) {
        if (slot(index).isEmpty()) {
            return this;
        }
        int mask = enabled ^ (1 << index);
        if ((mask & (1 << index)) != 0) {
            ModuleType type = slot(index).get();
            for (int other = 0; other < SIZE; other++) {
                if (other != index && slot(other).isPresent() && slot(other).get().conflictsWith(type)) {
                    mask &= ~(1 << other);
                }
            }
        }
        return new ToolModules(slots, mask, index);
    }

    // The module a conflict turned off when `index` was toggled on to make `after`, if any.
    public Optional<ModuleType> turnedOffBy(ToolModules after, int index) {
        for (int other = 0; other < SIZE; other++) {
            if (other != index && isOn(other) && !after.isOn(other)) {
                return slot(other);
            }
        }
        return Optional.empty();
    }

    // The installed slot `delta` steps from the cursor (wrapping), or -1 with none installed.
    public int step(int delta, int usable) {
        List<Integer> installed = new ArrayList<>();
        for (int index = 0; index < usable; index++) {
            if (slot(index).isPresent()) {
                installed.add(index);
            }
        }
        if (installed.isEmpty()) {
            return -1;
        }
        int at = installed.indexOf(cursor);
        if (at < 0) {
            return installed.get(delta > 0 ? 0 : installed.size() - 1);
        }
        return installed.get(Math.floorMod(at + Integer.signum(delta), installed.size()));
    }
}
