/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.quantum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.arcforge.Arcforge;

// Every Quantum Tunnel frequency on the server, with its buffers, saved once for the whole server (so tunnels in
// different dimensions share them).
public class QuantumFrequencies extends SavedData {
    private static final Codec<QuantumFrequencies> CODEC = Frequency.Data.CODEC.listOf()
            .xmap(QuantumFrequencies::new, frequencies -> frequencies.frequencies.values().stream().map(Frequency::save).toList());
    public static final SavedDataType<QuantumFrequencies> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "quantum_frequencies"), QuantumFrequencies::new, CODEC);

    private final Map<Frequency.Key, Frequency> frequencies = new LinkedHashMap<>();

    public QuantumFrequencies() {
        this(List.of());
    }

    private QuantumFrequencies(List<Frequency.Data> saved) {
        for (Frequency.Data data : saved) {
            Frequency frequency = Frequency.load(data);
            frequency.listen(this::setDirty);
            frequencies.put(frequency.key(), frequency);
        }
    }

    public static QuantumFrequencies get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Frequency get(Frequency.Key key) {
        return frequencies.get(key);
    }

    // Makes a frequency (if there's none by that key), owned by creator for deleting.
    public Frequency create(Frequency.Key key, UUID creator, String creatorName) {
        Frequency existing = frequencies.get(key);
        if (existing != null) {
            return existing;
        }
        Frequency frequency = new Frequency(key, creator, creatorName);
        frequency.listen(this::setDirty);
        frequencies.put(key, frequency);
        setDirty();
        return frequency;
    }

    // Deletes a frequency and everything in its buffers.
    public boolean remove(Frequency.Key key) {
        boolean removed = frequencies.remove(key) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int countCreatedBy(UUID player) {
        return (int) frequencies.values().stream().filter(frequency -> frequency.creator().equals(player)).count();
    }

    // The frequencies a player may use: every public one, then the private ones they own or are trusted with; by name.
    public List<Frequency> usableBy(UUID player, MinecraftServer server) {
        List<Frequency> usable = new ArrayList<>();
        for (Frequency frequency : frequencies.values()) {
            if (frequency.canUse(player, server)) {
                usable.add(frequency);
            }
        }
        usable.sort(Comparator.comparing((Frequency frequency) -> frequency.key().isPrivate())
                .thenComparing(frequency -> frequency.key().name().toLowerCase(java.util.Locale.ROOT)));
        return usable;
    }
}
