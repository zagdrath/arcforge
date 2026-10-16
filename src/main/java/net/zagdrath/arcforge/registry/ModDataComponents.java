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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.tool.JetpackMode;
import net.zagdrath.arcforge.item.tool.SettingsCardData;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.storage.VaultContents;

import com.mojang.serialization.Codec;

// Item components carrying a storage block's or Vault's contents while it is an item, the Wrench's mode, a
// Conduit Facade's look and a Conduit Filter's settings.
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Arcforge.MODID);

    // Fluid held by a fluid tank.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENTS =
            DATA_COMPONENTS.registerComponentType("fluid_contents", b -> b.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    // FE held by an energy cell.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // FE held by a Lithium Cell: its share of a Battery Array's energy, which can pass the int limit.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> STORED_ENERGY =
            DATA_COMPONENTS.registerComponentType("stored_energy", b -> b.persistent(com.mojang.serialization.Codec.LONG
                    .validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value)
                            : com.mojang.serialization.DataResult.error(() -> "Stored energy can't be negative: " + value)))
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    // HU held by a heat cell.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEAT =
            DATA_COMPONENTS.registerComponentType("heat", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // What the Wrench does (absent: Configure).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WrenchMode>> WRENCH_MODE =
            DATA_COMPONENTS.registerComponentType("wrench_mode", b -> b.persistent(WrenchMode.CODEC).networkSynchronized(WrenchMode.STREAM_CODEC));

    // The block a Conduit Facade copies the look of; a facade without one is blank.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> FACADE_TARGET =
            DATA_COMPONENTS.registerComponentType("facade_target", b -> b.persistent(BlockState.CODEC).networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

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

    // What a Settings Card copied (absent: empty).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SettingsCardData>> SETTINGS_CARD =
            DATA_COMPONENTS.registerComponentType("settings_card", b -> b.persistent(SettingsCardData.CODEC).networkSynchronized(SettingsCardData.STREAM_CODEC));

    // A conduit's plastic sheath colour (absent: unsheathed). Sheathed conduits only join their own colour.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> CONDUIT_COLOR =
            DATA_COMPONENTS.registerComponentType("conduit_color", b -> b.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC));

    // A Meter's threshold and mode, kept on the item when it's broken.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.zagdrath.arcforge.machine.meter.MeterSettings>> METER_SETTINGS =
            DATA_COMPONENTS.registerComponentType("meter_settings", b -> b.persistent(net.zagdrath.arcforge.machine.meter.MeterSettings.CODEC)
                    .networkSynchronized(net.zagdrath.arcforge.machine.meter.MeterSettings.STREAM_CODEC));

    // A Survey Record's survey (see SurveyRecord).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.zagdrath.arcforge.oil.SurveyRecord>> SURVEY_RECORD =
            DATA_COMPONENTS.registerComponentType("survey_record", b -> b.persistent(net.zagdrath.arcforge.oil.SurveyRecord.CODEC)
                    .networkSynchronized(net.zagdrath.arcforge.oil.SurveyRecord.STREAM_CODEC));

    // The oil deposits marked on a filled map from Survey Records, named in its tooltip (the markers themselves are
    // vanilla map decorations).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<net.zagdrath.arcforge.oil.SurveyRecord>>> OIL_MARKERS =
            DATA_COMPONENTS.registerComponentType("oil_markers", b -> b.persistent(net.zagdrath.arcforge.oil.SurveyRecord.CODEC.listOf())
                    .networkSynchronized(net.zagdrath.arcforge.oil.SurveyRecord.STREAM_CODEC.apply(ByteBufCodecs.list())));

    // The U-235 assay of a stack of Uranium Hexafluoride or its depleted tails, in thousandths of a percent (711 =
    // 0.711%; absent: the fluid's own, see Enrichment). Held to 20% (civil fuel only): the codec refuses more.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> U235_ASSAY =
            DATA_COMPONENTS.registerComponentType("u235_assay", b -> b.persistent(Codec.intRange(1, net.zagdrath.arcforge.nuclear.Enrichment.MAX_PCM))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    // Present on a Pressurized Cylinder lined with PVC: it can hold a corrosive gas (#arcforge:corrosive_gases, UF6).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> PVC_LINED =
            DATA_COMPONENTS.registerComponentType("pvc_lined", b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    // A Fuel Rod: its fuel, enrichment, burnup, what it has built up and its decay heat (see FuelRod).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.zagdrath.arcforge.nuclear.FuelRod>> FUEL_ROD =
            DATA_COMPONENTS.registerComponentType("fuel_rod", b -> b.persistent(net.zagdrath.arcforge.nuclear.FuelRod.CODEC)
                    .networkSynchronized(net.zagdrath.arcforge.nuclear.FuelRod.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
