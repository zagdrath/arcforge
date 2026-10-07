/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.advancement.MachineProducedTrigger;
import net.zagdrath.arcforge.advancement.MultiblockFormedTrigger;
import net.zagdrath.arcforge.advancement.OxyFuelTrigger;
import net.zagdrath.arcforge.advancement.TurbineFullSpeedTrigger;

// The advancement triggers of the Arcforge tab (data/arcforge/advancement); ArcforgeAdvancements fires them.
public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Arcforge.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, MultiblockFormedTrigger> MULTIBLOCK_FORMED = TRIGGERS.register("multiblock_formed",
            MultiblockFormedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, MachineProducedTrigger> MACHINE_PRODUCED = TRIGGERS.register("machine_produced",
            MachineProducedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, TurbineFullSpeedTrigger> TURBINE_FULL_SPEED = TRIGGERS.register("turbine_full_speed",
            TurbineFullSpeedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, OxyFuelTrigger> OXY_FUEL = TRIGGERS.register("oxy_fuel", OxyFuelTrigger::new);

    private ModTriggers() {}

    public static void register(IEventBus bus) {
        TRIGGERS.register(bus);
    }
}
