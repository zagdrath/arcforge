/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.tool.JetpackMode;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.storage.VaultContents;

import com.mojang.serialization.Codec;

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

    // An Arc Quarry's area, filter and switches, kept when it's picked up (see QuarrySettings).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<QuarrySettings>> QUARRY_SETTINGS =
            DATA_COMPONENTS.registerComponentType("quarry_settings", b -> b.persistent(QuarrySettings.CODEC).networkSynchronized(QuarrySettings.STREAM_CODEC));

    // A Jetpack's flight mode (absent: Normal).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<JetpackMode>> JETPACK_MODE =
            DATA_COMPONENTS.registerComponentType("jetpack_mode", b -> b.persistent(JetpackMode.CODEC).networkSynchronized(JetpackMode.STREAM_CODEC));

    // Present on a Jetpack smithed with a Steel Chestplate (see JetpackPlatingRecipe).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> JETPACK_PLATING =
            DATA_COMPONENTS.registerComponentType("jetpack_plating", b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    // An Arc Drill or Arc Saw's modules and which are on (absent: none).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolModules>> TOOL_MODULES =
            DATA_COMPONENTS.registerComponentType("tool_modules", b -> b.persistent(ToolModules.CODEC).networkSynchronized(ToolModules.STREAM_CODEC));

    // Whether an Arc Saw fells whole trunks (absent: on).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FELLING =
            DATA_COMPONENTS.registerComponentType("felling", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
