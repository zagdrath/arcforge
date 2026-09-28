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

    // Machines' running loops (see MachineLoopSound): single machines are heard over 16 blocks, the arrays 24.
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_CRUSHER_RUN = loop("arc_crusher_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_CRUSHING_ARRAY_RUN = loop("arc_crushing_array_run", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> INDUCTION_FURNACE_RUN = loop("induction_furnace_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> INDUCTION_FURNACE_ARRAY_RUN = loop("induction_furnace_array_run", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_PRESS_RUN = loop("metal_press_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_PRESSING_ARRAY_RUN = loop("metal_pressing_array_run", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERHEATER_ARRAY_RUN = loop("superheater_array_run", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> CONDENSER_ARRAY_RUN = loop("condenser_array_run", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_MELTER_RUN = loop("arc_melter_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL_REACTOR_RUN = loop("chemical_reactor_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTROLYZER_RUN = loop("electrolyzer_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLER_RUN = loop("assembler_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOCK_BREAKER_RUN = loop("block_breaker_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOCK_PLACER_RUN = loop("block_placer_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_COLLECTOR_RUN = loop("vacuum_collector_run", 16.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_QUARRY_RUN = loop("arc_quarry_run", 24.0F);

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> loop(String name, float range) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath(Arcforge.MODID, name), range));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
