/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.gas.GasRegistry;
import net.zagdrath.arcforge.farming.ClocheSoil;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.item.tool.JetpackFuel;
import net.zagdrath.arcforge.nuclear.RadiationShielding;
import net.zagdrath.arcforge.nuclear.Radioactivity;

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

    // Fuels the Diesel Generator burns, keyed by fluid: the FE each mB makes. Synced so its GUI and JEI can show them.
    public static final DataMapType<Fluid, net.zagdrath.arcforge.machine.EngineFuel> ENGINE_FUELS = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "engine_fuels"), Registries.FLUID, net.zagdrath.arcforge.machine.EngineFuel.CODEC)
            .synced(net.zagdrath.arcforge.machine.EngineFuel.CODEC, false)
            .build();

    // Soils for the Glass Cloche and Grow Chamber, keyed by item: the block drawn inside and how fast crops grow in it.
    // Synced so the client can draw it and the GUI's soil slot knows what it takes.
    public static final DataMapType<Item, ClocheSoil> CLOCHE_SOILS = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "cloche_soils"), Registries.ITEM, ClocheSoil.CODEC)
            .synced(ClocheSoil.CODEC, false)
            .build();

    // How radioactive an item, a block or a bucket of a fluid is (see Radioactivity and RadiationField): one data map
    // id on each of the three registries. Synced so tooltips, the Geiger Counter's tooltip line and Jade can show it.
    private static final Identifier RADIOACTIVITY = Identifier.fromNamespaceAndPath(Arcforge.MODID, "radioactivity");
    public static final DataMapType<Item, Radioactivity> RADIOACTIVE_ITEMS = DataMapType.builder(RADIOACTIVITY, Registries.ITEM, Radioactivity.CODEC)
            .synced(Radioactivity.CODEC, false)
            .build();
    public static final DataMapType<Block, Radioactivity> RADIOACTIVE_BLOCKS = DataMapType.builder(RADIOACTIVITY, Registries.BLOCK, Radioactivity.CODEC)
            .synced(Radioactivity.CODEC, false)
            .build();
    public static final DataMapType<Fluid, Radioactivity> RADIOACTIVE_FLUIDS = DataMapType.builder(RADIOACTIVITY, Registries.FLUID, Radioactivity.CODEC)
            .synced(Radioactivity.CODEC, false)
            .build();

    // How much radiation one block of a kind lets through (see RadiationShielding). Synced for its tooltip line.
    public static final DataMapType<Block, RadiationShielding> RADIATION_SHIELDING = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "radiation_shielding"), Registries.BLOCK, RadiationShielding.CODEC)
            .synced(RadiationShielding.CODEC, false)
            .build();

    private ModDataMaps() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterDataMapTypesEvent.class, event -> {
            event.register(BURNER_FUELS);
            event.register(JETPACK_FUELS);
            event.register(ENGINE_FUELS);
            event.register(CLOCHE_SOILS);
            event.register(RADIOACTIVE_ITEMS);
            event.register(RADIOACTIVE_BLOCKS);
            event.register(RADIOACTIVE_FLUIDS);
            event.register(RADIATION_SHIELDING);
            // The gas API's gas colours (its own type, declared in GasRegistry).
            event.register(GasRegistry.PROPERTIES);
        });
    }
}
