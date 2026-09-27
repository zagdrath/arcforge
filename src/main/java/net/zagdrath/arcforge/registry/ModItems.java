/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.item.conduit.ConduitBlockItem;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.storage.StorageBlockItem;
import net.zagdrath.arcforge.item.tool.DieItem;
import net.zagdrath.arcforge.item.tool.EngineersHandbookItem;
import net.zagdrath.arcforge.item.tool.WrenchItem;
import net.zagdrath.arcforge.item.upgrade.UpgradeItem;
import net.zagdrath.arcforge.upgrade.UpgradeType;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Arcforge.MODID);

    public static final DeferredItem<BlockItem> GEOTHERMAL_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.GEOTHERMAL_PLANT);
    public static final DeferredItem<BlockItem> COMBUSTION_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.COMBUSTION_PLANT);
    public static final DeferredItem<BlockItem> FIREBOX = ITEMS.registerSimpleBlockItem(ModBlocks.FIREBOX);
    public static final DeferredItem<BlockItem> THERMOELECTRIC_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.THERMOELECTRIC_PLANT);
    public static final DeferredItem<BlockItem> ARC_CRUSHER = ITEMS.registerSimpleBlockItem(ModBlocks.ARC_CRUSHER);
    public static final DeferredItem<BlockItem> ARC_CRUSHING_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.ARC_CRUSHING_ARRAY_CASING);
    public static final DeferredItem<BlockItem> INDUCTION_FURNACE = ITEMS.registerSimpleBlockItem(ModBlocks.INDUCTION_FURNACE);
    public static final DeferredItem<BlockItem> INDUCTION_FURNACE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.INDUCTION_FURNACE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> METAL_PRESS = ITEMS.registerSimpleBlockItem(ModBlocks.METAL_PRESS);
    public static final DeferredItem<BlockItem> METAL_PRESSING_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.METAL_PRESSING_ARRAY_CASING);
    public static final DeferredItem<BlockItem> STEAM_BOILER = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_BOILER);
    public static final DeferredItem<BlockItem> STEAM_TURBINE = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_TURBINE);
    public static final DeferredItem<BlockItem> ELECTRIC_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_PUMP);
    public static final DeferredItem<BlockItem> STEAM_BOILER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_BOILER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> STEAM_TURBINE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_TURBINE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> PRESSURE_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_GLASS);
    public static final DeferredItem<BlockItem> FIBERIZER = ITEMS.registerSimpleBlockItem(ModBlocks.FIBERIZER);
    public static final DeferredItem<BlockItem> INFUSER = ITEMS.registerSimpleBlockItem(ModBlocks.INFUSER);
    public static final DeferredItem<BlockItem> FUEL_BURNER = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_BURNER);

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<EngineersHandbookItem> ENGINEERS_HANDBOOK = ITEMS.registerItem("engineers_handbook", EngineersHandbookItem::new, p -> p.stacksTo(1));

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

    // --- Crushing ---

    private static final ResourceKey<ContextIntProvider> CARBON_DUST_BURN_TIME = cookingTime("time_carbon_dust");

    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust");
    public static final DeferredItem<Item> ANCIENT_DEBRIS_DUST = ITEMS.registerSimpleItem("ancient_debris_dust");
    public static final DeferredItem<Item> CARBON_DUST = ITEMS.registerSimpleItem("carbon_dust", p -> p.cookingFuel(CARBON_DUST_BURN_TIME));
    public static final DeferredItem<Item> NETHER_QUARTZ_DUST = ITEMS.registerSimpleItem("nether_quartz_dust");

    // --- Components ---

    public static final DeferredItem<Item> SLAG_WOOL = ITEMS.registerSimpleItem("slag_wool");
    public static final DeferredItem<Item> ROCK_WOOL = ITEMS.registerSimpleItem("rock_wool");

    // Dies for the Metal Press, and what they make. Dies are never used up.
    public static final DeferredItem<DieItem> PLATE_DIE = ITEMS.registerItem("plate_die", DieItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DieItem> GEAR_DIE = ITEMS.registerItem("gear_die", DieItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DieItem> ROD_DIE = ITEMS.registerItem("rod_die", DieItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");
    public static final DeferredItem<Item> STEEL_GEAR = ITEMS.registerSimpleItem("steel_gear");
    public static final DeferredItem<Item> STEEL_ROD = ITEMS.registerSimpleItem("steel_rod");
    public static final DeferredItem<Item> COPPER_PLATE = ITEMS.registerSimpleItem("copper_plate");
    public static final DeferredItem<Item> COPPER_GEAR = ITEMS.registerSimpleItem("copper_gear");
    public static final DeferredItem<Item> COPPER_ROD = ITEMS.registerSimpleItem("copper_rod");

    // --- Alloys: the tier material of every tiered block and item, made in the Arcforge Furnace ---

    public static final DeferredItem<Item> WROUGHT_ALLOY = ITEMS.registerSimpleItem("wrought_alloy");
    public static final DeferredItem<Item> TEMPERED_ALLOY = ITEMS.registerSimpleItem("tempered_alloy");
    public static final DeferredItem<Item> HARDENED_ALLOY = ITEMS.registerSimpleItem("hardened_alloy");
    public static final DeferredItem<Item> ARCFORGED_ALLOY = ITEMS.registerSimpleItem("arcforged_alloy");

    // --- Upgrades ---

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade", p -> new UpgradeItem(UpgradeType.SPEED, p));
    public static final DeferredItem<UpgradeItem> ENERGY_UPGRADE = ITEMS.registerItem("energy_upgrade", p -> new UpgradeItem(UpgradeType.ENERGY, p));
    public static final DeferredItem<UpgradeItem> HEAT_UPGRADE = ITEMS.registerItem("heat_upgrade", p -> new UpgradeItem(UpgradeType.HEAT, p));
    public static final DeferredItem<UpgradeItem> INSULATION_UPGRADE = ITEMS.registerItem("insulation_upgrade", p -> new UpgradeItem(UpgradeType.INSULATION, p));
    public static final DeferredItem<UpgradeItem> THERMOELECTRIC_UPGRADE = ITEMS.registerItem("thermoelectric_upgrade", p -> new UpgradeItem(UpgradeType.THERMOELECTRIC, p));

    public static final DeferredItem<BucketItem> CREOSOTE_BUCKET = ITEMS.registerItem("creosote_bucket",
            p -> new BucketItem(ModFluids.CREOSOTE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> NAPHTHA_BUCKET = ITEMS.registerItem("naphtha_bucket",
            p -> new BucketItem(ModFluids.NAPHTHA.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LIGHT_OIL_BUCKET = ITEMS.registerItem("light_oil_bucket",
            p -> new BucketItem(ModFluids.LIGHT_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> HEAVY_OIL_BUCKET = ITEMS.registerItem("heavy_oil_bucket",
            p -> new BucketItem(ModFluids.HEAVY_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    // --- Ores (see ModBlocks), their dusts, and what they make ---

    public static final DeferredItem<BlockItem> SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SILVER_ORE);
    public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerSimpleItem("raw_silver");
    public static final DeferredItem<BlockItem> RAW_SILVER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_SILVER_BLOCK);
    public static final DeferredItem<Item> SILVER_DUST = ITEMS.registerSimpleItem("silver_dust");
    public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerSimpleItem("silver_ingot");
    public static final DeferredItem<BlockItem> SILVER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.SILVER_BLOCK);
    public static final DeferredItem<BlockItem> NICKEL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.NICKEL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_NICKEL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_NICKEL_ORE);
    public static final DeferredItem<Item> RAW_NICKEL = ITEMS.registerSimpleItem("raw_nickel");
    public static final DeferredItem<BlockItem> RAW_NICKEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_NICKEL_BLOCK);
    public static final DeferredItem<Item> NICKEL_DUST = ITEMS.registerSimpleItem("nickel_dust");
    public static final DeferredItem<Item> NICKEL_INGOT = ITEMS.registerSimpleItem("nickel_ingot");
    public static final DeferredItem<BlockItem> NICKEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.NICKEL_BLOCK);
    public static final DeferredItem<BlockItem> WOLFRAMITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.WOLFRAMITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_WOLFRAMITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_WOLFRAMITE_ORE);
    public static final DeferredItem<Item> RAW_WOLFRAMITE = ITEMS.registerSimpleItem("raw_wolframite");
    public static final DeferredItem<BlockItem> RAW_WOLFRAMITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_WOLFRAMITE_BLOCK);
    public static final DeferredItem<Item> TUNGSTEN_DUST = ITEMS.registerSimpleItem("tungsten_dust");
    public static final DeferredItem<Item> TUNGSTEN_INGOT = ITEMS.registerSimpleItem("tungsten_ingot");
    public static final DeferredItem<BlockItem> TUNGSTEN_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.TUNGSTEN_BLOCK);
    public static final DeferredItem<BlockItem> FLUORITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUORITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_FLUORITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_FLUORITE_ORE);
    public static final DeferredItem<Item> RAW_FLUORITE = ITEMS.registerSimpleItem("raw_fluorite");
    public static final DeferredItem<BlockItem> RAW_FLUORITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_FLUORITE_BLOCK);
    public static final DeferredItem<Item> FLUORITE_DUST = ITEMS.registerSimpleItem("fluorite_dust");
    public static final DeferredItem<Item> FLUORITE_CRYSTAL = ITEMS.registerSimpleItem("fluorite_crystal");
    public static final DeferredItem<BlockItem> FLUORITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.FLUORITE_BLOCK);
    public static final DeferredItem<BlockItem> BISMUTH_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.BISMUTH_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_BISMUTH_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_BISMUTH_ORE);
    public static final DeferredItem<Item> RAW_BISMUTH = ITEMS.registerSimpleItem("raw_bismuth");
    public static final DeferredItem<BlockItem> RAW_BISMUTH_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_BISMUTH_BLOCK);
    public static final DeferredItem<Item> BISMUTH_DUST = ITEMS.registerSimpleItem("bismuth_dust");
    public static final DeferredItem<Item> BISMUTH_INGOT = ITEMS.registerSimpleItem("bismuth_ingot");
    public static final DeferredItem<BlockItem> BISMUTH_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.BISMUTH_BLOCK);
    public static final DeferredItem<BlockItem> ARCITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.ARCITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_ARCITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_ARCITE_ORE);
    public static final DeferredItem<Item> RAW_ARCITE = ITEMS.registerSimpleItem("raw_arcite");
    public static final DeferredItem<BlockItem> RAW_ARCITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_ARCITE_BLOCK);
    public static final DeferredItem<Item> ARCITE_DUST = ITEMS.registerSimpleItem("arcite_dust");
    public static final DeferredItem<Item> ARCITE_CRYSTAL = ITEMS.registerSimpleItem("arcite_crystal");
    public static final DeferredItem<BlockItem> ARCITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.ARCITE_BLOCK);
    public static final DeferredItem<Item> SILVER_PLATE = ITEMS.registerSimpleItem("silver_plate");
    public static final DeferredItem<Item> NICKEL_PLATE = ITEMS.registerSimpleItem("nickel_plate");
    public static final DeferredItem<Item> TUNGSTEN_PLATE = ITEMS.registerSimpleItem("tungsten_plate");
    public static final DeferredItem<Item> INVAR_PLATE = ITEMS.registerSimpleItem("invar_plate");
    public static final DeferredItem<Item> INVAR_DUST = ITEMS.registerSimpleItem("invar_dust");
    public static final DeferredItem<Item> INVAR_INGOT = ITEMS.registerSimpleItem("invar_ingot");
    public static final DeferredItem<Item> TUNGSTEN_HEATING_COIL = ITEMS.registerSimpleItem("tungsten_heating_coil");
    public static final DeferredItem<Item> THERMOCOUPLE = ITEMS.registerSimpleItem("thermocouple");
    public static final DeferredItem<Item> ARCITE_TUNGSTEN_COMPOSITE = ITEMS.registerSimpleItem("arcite_tungsten_composite");

    // The ore items in creative tab order: per ore its ore, deepslate ore, raw item, raw block, dust,
    // ingot or crystal and block; then Invar's dust and ingot.
    public static List<DeferredItem<? extends Item>> ores() {
        return List.of(SILVER_ORE, DEEPSLATE_SILVER_ORE, RAW_SILVER, RAW_SILVER_BLOCK, SILVER_DUST, SILVER_INGOT, SILVER_BLOCK, NICKEL_ORE, DEEPSLATE_NICKEL_ORE, RAW_NICKEL, RAW_NICKEL_BLOCK, NICKEL_DUST, NICKEL_INGOT, NICKEL_BLOCK, WOLFRAMITE_ORE, DEEPSLATE_WOLFRAMITE_ORE, RAW_WOLFRAMITE, RAW_WOLFRAMITE_BLOCK, TUNGSTEN_DUST, TUNGSTEN_INGOT, TUNGSTEN_BLOCK, FLUORITE_ORE, DEEPSLATE_FLUORITE_ORE, RAW_FLUORITE, RAW_FLUORITE_BLOCK, FLUORITE_DUST, FLUORITE_CRYSTAL, FLUORITE_BLOCK, BISMUTH_ORE, DEEPSLATE_BISMUTH_ORE, RAW_BISMUTH, RAW_BISMUTH_BLOCK, BISMUTH_DUST, BISMUTH_INGOT, BISMUTH_BLOCK, ARCITE_ORE, DEEPSLATE_ARCITE_ORE, RAW_ARCITE, RAW_ARCITE_BLOCK, ARCITE_DUST, ARCITE_CRYSTAL, ARCITE_BLOCK, INVAR_DUST, INVAR_INGOT);
    }

    // What's made from the ores, in creative tab order (Components): the plates, then the components.
    public static List<DeferredItem<Item>> oreComponents() {
        return List.of(SILVER_PLATE, NICKEL_PLATE, TUNGSTEN_PLATE, INVAR_PLATE, TUNGSTEN_HEATING_COIL, THERMOCOUPLE, ARCITE_TUNGSTEN_COMPOSITE);
    }

    // --- Distillation ---

    private static final ResourceKey<ContextIntProvider> PITCH_BURN_TIME = cookingTime("time_pitch");

    public static final DeferredItem<BlockItem> DISTILLATION_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.DISTILLATION_ARRAY_CASING);
    public static final DeferredItem<BlockItem> TRAY_LEVEL_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.TRAY_LEVEL_CASING);
    public static final DeferredItem<BlockItem> DISTILLATION_ARRAY_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.DISTILLATION_ARRAY_CONTROLLER);
    public static final DeferredItem<Item> PITCH = ITEMS.registerSimpleItem("pitch", p -> p.cookingFuel(PITCH_BURN_TIME));
    public static final DeferredItem<Item> CARBON_FIBER = ITEMS.registerSimpleItem("carbon_fiber");
    public static final DeferredItem<BlockItem> ASPHALT = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT);

    // --- Solar Thermal Array ---

    public static final DeferredItem<BlockItem> SOLAR_THERMAL_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.SOLAR_THERMAL_ARRAY_CASING);
    public static final DeferredItem<BlockItem> SOLAR_THERMAL_ARRAY_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER);
    public static final DeferredItem<BlockItem> SOLAR_COLLECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.SOLAR_COLLECTOR);
    public static final DeferredItem<Item> TROUGH_MIRROR = ITEMS.registerSimpleItem("trough_mirror");
    public static final DeferredItem<Item> RECEIVER_TUBE = ITEMS.registerSimpleItem("receiver_tube");

    public static final DeferredItem<BlockItem> ASPHALT_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_STAIRS);
    public static final DeferredItem<BlockItem> ASPHALT_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_SLAB);

    // Treated wood, like crimson and warped: not furnace fuel. The door places both halves.
    private static final List<DeferredItem<BlockItem>> TREATED_WOOD = new ArrayList<>();

    static {
        for (DeferredBlock<? extends Block> block : ModBlocks.treatedWoodSet()) {
            TREATED_WOOD.add(block == ModBlocks.TREATED_DOOR
                    ? ITEMS.registerItem(block.getId().getPath(), p -> new DoubleHighBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix())
                    : ITEMS.registerSimpleBlockItem(block));
        }
    }

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

    // Batteries, Canisters, Gas Cartridges and Thermal Capsules, ordered by kind then tier.
    private static final List<DeferredItem<PortableStorageItem>> PORTABLES = new ArrayList<>();

    static {
        for (PortableStorageItem.Kind kind : PortableStorageItem.Kind.values()) {
            for (ConduitTier tier : ConduitTier.values()) {
                String name = tier.getSerializedName() + "_" + kind.name().toLowerCase(Locale.ROOT);
                PORTABLES.add(ITEMS.registerItem(name, p -> new PortableStorageItem(kind, tier, p)));
            }
        }
    }

    private ModItems() {}

    public static List<DeferredItem<Item>> alloys() {
        return List.of(WROUGHT_ALLOY, TEMPERED_ALLOY, HARDENED_ALLOY, ARCFORGED_ALLOY);
    }

    public static List<DeferredItem<PortableStorageItem>> allPortables() {
        return PORTABLES;
    }

    public static PortableStorageItem portable(PortableStorageItem.Kind kind, ConduitTier tier) {
        return PORTABLES.get(kind.ordinal() * ConduitTier.values().length + tier.ordinal()).get();
    }

    private static ResourceKey<ContextIntProvider> cookingTime(String name) {
        return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Identifier.fromNamespaceAndPath(Arcforge.MODID, "cooking/" + name));
    }

    // In the order of the Building Blocks tab.
    public static List<DeferredItem<BlockItem>> treatedWood() {
        return TREATED_WOOD;
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
