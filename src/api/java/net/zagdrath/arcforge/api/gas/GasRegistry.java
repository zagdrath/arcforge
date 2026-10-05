/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.zagdrath.arcforge.api.ArcforgeApi;

/**
 * Looks gases up: every gas, for search and display, and a gas by id or by fluid.
 *
 * <p>A gas is a source fluid that is lighter than air (its NeoForge {@code FluidType} has a negative density) or is
 * tagged {@link #TAG #arcforge:gases}. That includes other mods' gases, which Arcforge's Pressurized Conduits and
 * Pressurized Cylinders carry too. The lookups read the fluid registry and its tags, so call them once a world is
 * loaded; they work on both the server and the client.
 */
public final class GasRegistry {
    /** The fluid tag {@code #arcforge:gases}: fluids that count as gases although they are not lighter than air. */
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(ArcforgeApi.MOD_ID, "gases"));

    /**
     * The data map {@code arcforge:gas_properties}, keyed by fluid: each gas's {@link GasProperties} (its colour).
     * Synced to clients. Arcforge registers it.
     */
    public static final DataMapType<Fluid, GasProperties> PROPERTIES = DataMapType.builder(
            Identifier.fromNamespaceAndPath(ArcforgeApi.MOD_ID, "gas_properties"), Registries.FLUID, GasProperties.CODEC)
            .synced(GasProperties.CODEC, false)
            .build();

    private GasRegistry() {}

    /**
     * Returns whether a fluid is a gas.
     *
     * @param fluid any fluid, source or flowing
     * @return {@code true} for a gas
     */
    public static boolean isGas(Fluid fluid) {
        return fluid != Fluids.EMPTY && (fluid.getFluidType().getDensity() < 0 || fluid.defaultFluidState().is(TAG));
    }

    /**
     * Returns the gas a fluid is.
     *
     * @param fluid any fluid, source or flowing
     * @return the gas, or empty if the fluid isn't one
     */
    public static Optional<Gas> of(Fluid fluid) {
        return isGas(fluid) ? Optional.of(new Gas(fluid)) : Optional.empty();
    }

    /**
     * Returns the gas with an id.
     *
     * @param id a fluid id, e.g. {@code arcforge:hydrogen}
     * @return the gas, or empty if there is no such fluid or it isn't a gas
     */
    public static Optional<Gas> get(Identifier id) {
        return BuiltInRegistries.FLUID.getOptional(id).flatMap(GasRegistry::of);
    }

    /**
     * Returns every gas: Arcforge's and other mods'. Each call builds a new list from the fluid registry, so keep it
     * rather than calling this every tick. Filter by {@link Gas#displayName()} or {@link Gas#id()} to search.
     *
     * @return every gas, sorted by id; unmodifiable
     */
    public static List<Gas> all() {
        return BuiltInRegistries.FLUID.stream()
                .filter(fluid -> fluid.isSource(fluid.defaultFluidState()) && isGas(fluid))
                .map(Gas::new)
                .sorted(Comparator.comparing(Gas::id))
                .toList();
    }
}
