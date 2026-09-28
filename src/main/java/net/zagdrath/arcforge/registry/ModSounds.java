/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;

// Arcforge's own sounds (assets/arcforge/sounds.json).
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Arcforge.MODID);

    // The Steam Turbine Array's running loop; its pitch and volume follow the rotor (see TurbineArraySound).
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_ARRAY_RUN = SOUND_EVENTS.register("turbine_array_run",
            () -> SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath(Arcforge.MODID, "turbine_array_run"), 24.0F));

    private ModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
