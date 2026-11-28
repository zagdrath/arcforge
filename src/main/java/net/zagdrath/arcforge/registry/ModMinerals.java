/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.farming.FertilizerItem;

// Stone chemistry: the minerals and products made from the natural stones (ModStones), all through the existing machines.
//   Solvay           Limestone/Travertine/Dolomite + Brine + Ammonia -> Soda Ash (+ Calcium Chloride)   Chemical Reactor
//   Carbide          Quicklime + Coal Coke -> Calcium Carbide                                          Arcforge Furnace
//                    Calcium Carbide + Water -> Acetylene (+ Slaked Lime)                              Chemical Reactor
//   Scrubbing        Quicklime + Water -> Slaked Lime; Slaked Lime + Sulfur Dioxide -> Gypsum -> Plaster  Chemical Reactor
//   Magnesium        Dolomite -> Magnesia (+ Quicklime, CO2); Magnesia + Ferrosilicon -> Magnesium      Reactor, Furnace
//   Potash           Potash -> Potash Brine -> Potassium Hydroxide Solution -> Potassium Hydroxide     Reactor, Electrolyzer, Evaporator
//   Carbonation      Serpentine/Olivine + CO2 -> Magnesite; Magnesite -> Magnesia + CO2                Chemical Reactor
//   Kimberlite etc.  Garnet, Olivine, Mica, Chromite from the Sifter; Volcanic Ash and Agricultural Lime from the Crusher
// Every recipe and its numbers: STONE_MINERALS_HANDOFF.md. Items sit in the Materials tab after Quicklime (items()), blocks
// in Building Blocks after the stones (blocks()).
public final class ModMinerals {
    private static final List<DeferredItem<? extends Item>> ITEMS = new ArrayList<>();
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    public static final DeferredItem<Item> SODA_ASH = item("soda_ash");
    // A desiccant: the Solvay by-product. Only tagged (#c:dusts/calcium_chloride) for now.
    public static final DeferredItem<Item> CALCIUM_CHLORIDE = item("calcium_chloride");
    public static final DeferredItem<Item> CALCIUM_CARBIDE = item("calcium_carbide");
    public static final DeferredItem<Item> SLAKED_LIME = item("slaked_lime");
    public static final DeferredItem<FertilizerItem> GYPSUM = fertilizer("gypsum", ArcforgeConfig.GYPSUM_NUTRIENTS);
    public static final DeferredItem<Item> MAGNESIA = item("magnesia");
    public static final DeferredItem<Item> MAGNESIUM_DUST = item("magnesium_dust");
    public static final DeferredItem<Item> MAGNESIUM_INGOT = item("magnesium_ingot");
    public static final DeferredItem<Item> MAGNESIUM_NUGGET = item("magnesium_nugget");
    public static final DeferredItem<Item> POTASSIUM_HYDROXIDE = item("potassium_hydroxide");
    public static final DeferredItem<Item> MAGNESITE = item("magnesite");
    public static final DeferredItem<Item> GARNET = item("garnet");
    public static final DeferredItem<Item> SANDPAPER = item("sandpaper");
    public static final DeferredItem<Item> OLIVINE = item("olivine");
    public static final DeferredItem<Item> FOUNDRY_SAND = item("foundry_sand");
    public static final DeferredItem<Item> MICA = item("mica");
    public static final DeferredItem<Item> CHROMITE = item("chromite");
    public static final DeferredItem<FertilizerItem> VOLCANIC_ASH = fertilizer("volcanic_ash", ArcforgeConfig.VOLCANIC_ASH_NUTRIENTS);
    public static final DeferredItem<FertilizerItem> AGRICULTURAL_LIME = fertilizer("agricultural_lime",
            ArcforgeConfig.AGRICULTURAL_LIME_NUTRIENTS);

    public static final DeferredBlock<Block> MAGNESIUM_BLOCK = block(ModBlocks.BLOCKS.registerSimpleBlock("magnesium_block",
            p -> p.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.METAL)));

    // Plaster: soft, pale, quiet; Magnesia Bricks: a refractory brick, as hard as stone bricks.
    private static final UnaryOperator<BlockBehaviour.Properties> PLASTER_PROPS = p -> p.mapColor(MapColor.SNOW)
            .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.CALCITE);
    private static final UnaryOperator<BlockBehaviour.Properties> MAGNESIA_BRICK_PROPS = p -> p.mapColor(MapColor.TERRACOTTA_WHITE)
            .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE);

    public static final ModStones.Variant PLASTER = family("plaster", "plaster", PLASTER_PROPS);
    public static final ModStones.Variant MAGNESIA_BRICKS = family("magnesia_bricks", "magnesia_brick", MAGNESIA_BRICK_PROPS);

    private ModMinerals() {}

    // Loads the class, so its items and blocks join the registers before they're handed to the event bus.
    public static void bootstrap() {}

    // The new materials, in Materials-tab order.
    public static List<DeferredItem<? extends Item>> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    // Magnesium Block, then Plaster and Magnesia Bricks with their stairs, slabs and walls.
    public static List<DeferredItem<BlockItem>> blocks() {
        return Collections.unmodifiableList(BLOCK_ITEMS);
    }

    private static DeferredItem<Item> item(String name) {
        DeferredItem<Item> item = ModItems.ITEMS.registerSimpleItem(name);
        ITEMS.add(item);
        return item;
    }

    private static DeferredItem<FertilizerItem> fertilizer(String name, net.neoforged.neoforge.common.ModConfigSpec.IntValue nutrients) {
        DeferredItem<FertilizerItem> item = ModItems.ITEMS.registerItem(name, p -> new FertilizerItem(nutrients::getAsInt, p));
        ITEMS.add(item);
        return item;
    }

    private static <B extends Block> DeferredBlock<B> block(DeferredBlock<B> block) {
        BLOCK_ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    // A block and its stairs, slab and wall, named from the stem (plaster_stairs, magnesia_brick_wall).
    private static ModStones.Variant family(String name, String stem, UnaryOperator<BlockBehaviour.Properties> props) {
        DeferredBlock<Block> base = block(ModBlocks.BLOCKS.registerSimpleBlock(name, props));
        DeferredBlock<StairBlock> stairs = block(ModBlocks.BLOCKS.registerBlock(stem + "_stairs",
                p -> new StairBlock(base.get().defaultBlockState(), p), props));
        DeferredBlock<SlabBlock> slab = block(ModBlocks.BLOCKS.registerBlock(stem + "_slab", SlabBlock::new, props));
        DeferredBlock<WallBlock> wall = block(ModBlocks.BLOCKS.registerBlock(stem + "_wall", WallBlock::new,
                p -> props.apply(p).forceSolidOn()));
        return new ModStones.Variant(base, stairs, slab, wall);
    }
}
