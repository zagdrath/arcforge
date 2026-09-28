/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.storage.VaultContents;

// Item components carrying a storage block's or Vault's contents while it is an item, the Wrench's mode and a
// Conduit Filter's settings.
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Arcforge.MODID);

    // Fluid held by a fluid tank.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENTS =
            DATA_COMPONENTS.registerComponentType("fluid_contents", b -> b.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    // FE held by an energy cell.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // HU held by a heat cell.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEAT =
            DATA_COMPONENTS.registerComponentType("heat", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // What the Wrench does (absent: Configure).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WrenchMode>> WRENCH_MODE =
            DATA_COMPONENTS.registerComponentType("wrench_mode", b -> b.persistent(WrenchMode.CODEC).networkSynchronized(WrenchMode.STREAM_CODEC));

    // A Conduit Filter's settings (absent: unconfigured, see FilterSettings).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterSettings>> CONDUIT_FILTER =
            DATA_COMPONENTS.registerComponentType("conduit_filter", b -> b.persistent(FilterSettings.CODEC).networkSynchronized(FilterSettings.STREAM_CODEC));

    // What a Vault holds while it's an item (absent: empty, unlocked, void off). Crates use minecraft:container.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VaultContents>> VAULT_CONTENTS =
            DATA_COMPONENTS.registerComponentType("vault_contents", b -> b.persistent(VaultContents.CODEC).networkSynchronized(VaultContents.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
