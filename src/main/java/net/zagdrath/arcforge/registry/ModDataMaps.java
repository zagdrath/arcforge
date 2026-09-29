/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.item.tool.JetpackFuel;

// Arcforge data maps: per-entry data other packs can add to or override.
public final class ModDataMaps {
    // Liquid fuels for the Fuel Burner, keyed by fluid. Synced so the GUI's bucket slot knows what it takes.
    public static final DataMapType<Fluid, BurnerFuel> BURNER_FUELS = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "burner_fuels"), Registries.FLUID, BurnerFuel.CODEC)
            .synced(BurnerFuel.CODEC, false)
            .build();

    // Gases a Jetpack burns, keyed by fluid. Synced so the client picks the sound and particles from its own copy.
    public static final DataMapType<Fluid, JetpackFuel> JETPACK_FUELS = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_fuels"), Registries.FLUID, JetpackFuel.CODEC)
            .synced(JetpackFuel.CODEC, false)
            .build();

    private ModDataMaps() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterDataMapTypesEvent.class, event -> {
            event.register(BURNER_FUELS);
            event.register(JETPACK_FUELS);
        });
    }
}
