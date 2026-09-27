/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
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

    // --- Steelmaking ---

    // Furnace burn times, data-driven like vanilla's (data/arcforge/context_int_provider/cooking/).
    private static final ResourceKey<ContextIntProvider> COAL_COKE_BURN_TIME = cookingTime("time_coal_coke");
    private static final ResourceKey<ContextIntProvider> COAL_COKE_BLOCK_BURN_TIME = cookingTime("time_coal_coke_block");

    public static final DeferredItem<BlockItem> CARBONIZER = ITEMS.registerSimpleBlockItem(ModBlocks.CARBONIZER);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_PORT = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_PORT);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_BRICKS);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_BRICK_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_BRICK_WALL);

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<BlockItem> STEEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_BLOCK);
    public static final DeferredItem<Item> COAL_COKE = ITEMS.registerSimpleItem("coal_coke", p -> p.cookingFuel(COAL_COKE_BURN_TIME));
    public static final DeferredItem<BlockItem> COAL_COKE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.COAL_COKE_BLOCK,
            p -> p.cookingFuel(COAL_COKE_BLOCK_BURN_TIME));
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("slag");

    public static final DeferredItem<BucketItem> CREOSOTE_BUCKET = ITEMS.registerItem("creosote_bucket",
            p -> new BucketItem(ModFluids.CREOSOTE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

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

    private static ResourceKey<ContextIntProvider> cookingTime(String name) {
        return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Identifier.fromNamespaceAndPath(Arcforge.MODID, "cooking/" + name));
    }

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
