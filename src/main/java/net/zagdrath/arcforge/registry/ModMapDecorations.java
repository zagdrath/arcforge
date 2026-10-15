/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.oil.DepositKind;

// Map markers: a small oil derrick for each kind of deposit, put on a filled map from a Survey Record (sprites at
// textures/map/decorations/oil_<kind>.png, bigger for bigger deposits).
public final class ModMapDecorations {
    private static final DeferredRegister<MapDecorationType> TYPES = DeferredRegister.create(Registries.MAP_DECORATION_TYPE, Arcforge.MODID);
    private static final Map<DepositKind, DeferredHolder<MapDecorationType, MapDecorationType>> OIL = new EnumMap<>(DepositKind.class);

    static {
        for (DepositKind kind : DepositKind.values()) {
            String name = "oil_" + kind.getSerializedName();
            OIL.put(kind, TYPES.register(name, () -> new MapDecorationType(Identifier.fromNamespaceAndPath(Arcforge.MODID, name), true, false)));
        }
    }

    private ModMapDecorations() {}

    public static DeferredHolder<MapDecorationType, MapDecorationType> oil(DepositKind kind) {
        return OIL.get(kind);
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
