/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;

public final class ModParticleTypes {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Arcforge.MODID);

    // The shimmer over a Gas Turbine Array's exhaust while it vents heat to the air.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAT_HAZE = PARTICLE_TYPES.register("heat_haze",
            () -> new SimpleParticleType(false));

    private ModParticleTypes() {}

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
