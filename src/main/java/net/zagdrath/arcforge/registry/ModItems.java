/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.item.conduit.ConduitBlockItem;
import net.zagdrath.arcforge.item.storage.StorageBlockItem;
import net.zagdrath.arcforge.item.tool.WrenchItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Arcforge.MODID);

    public static final DeferredItem<BlockItem> GEOTHERMAL_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.GEOTHERMAL_PLANT);

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));

    // Conduit items, ordered by type then tier.
    private static final List<DeferredItem<ConduitBlockItem>> CONDUITS = new ArrayList<>();

    static {
        for (DeferredBlock<ConduitBlock> block : ModBlocks.allConduits()) {
            CONDUITS.add(ITEMS.registerItem(block.getId().getPath(),
                    p -> new ConduitBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        }
    }

    // Fluid tank and energy cell items, in the same order as ModBlocks.allStorage().
    private static final List<DeferredItem<StorageBlockItem>> STORAGE = new ArrayList<>();

    static {
        for (DeferredBlock<? extends StorageBlock> block : ModBlocks.allStorage()) {
            STORAGE.add(ITEMS.registerItem(block.getId().getPath(),
                    p -> new StorageBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        }
    }

    private ModItems() {}

    public static List<DeferredItem<StorageBlockItem>> allStorage() {
        return STORAGE;
    }

    public static List<DeferredItem<ConduitBlockItem>> allConduits() {
        return CONDUITS;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
