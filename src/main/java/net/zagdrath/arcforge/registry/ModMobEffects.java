/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.nuclear.RadiationPoisoningEffect;

public final class ModMobEffects {
    private static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Arcforge.MODID);

    // Radiation Poisoning I to IV, from the dose built up (see RadiationDose).
    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_POISONING = MOB_EFFECTS.register("radiation_poisoning",
            RadiationPoisoningEffect::new);

    private ModMobEffects() {}

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
