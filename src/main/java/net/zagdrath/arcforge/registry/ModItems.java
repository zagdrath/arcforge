/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.chemistry.OreSlurry;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.item.conduit.ConduitBlockItem;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.item.farming.SickleItem;
import net.zagdrath.arcforge.item.conduit.ConduitFilterItem;
import net.zagdrath.arcforge.item.machine.ArcQuarryItem;
import net.zagdrath.arcforge.item.storage.CrateBlockItem;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.storage.StorageBlockItem;
import net.zagdrath.arcforge.item.storage.StorageUpgradeItem;
import net.zagdrath.arcforge.item.storage.VaultBlockItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.AreaToolItem;
import net.zagdrath.arcforge.item.tool.DieItem;
import net.zagdrath.arcforge.item.tool.EngineersHandbookItem;
import net.zagdrath.arcforge.item.tool.FoundrySuit;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.item.tool.ModuleType;
import net.zagdrath.arcforge.item.tool.SettingsCardItem;
import net.zagdrath.arcforge.item.tool.SteelMaterials;
import net.zagdrath.arcforge.item.tool.ToolModuleItem;
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
    public static final DeferredItem<BlockItem> ELECTRIC_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_PUMP);
    public static final DeferredItem<BlockItem> SECURITY_TERMINAL = ITEMS.registerSimpleBlockItem(ModBlocks.SECURITY_TERMINAL);
    public static final DeferredItem<BlockItem> THROTTLE_LEVER = ITEMS.registerSimpleBlockItem(ModBlocks.THROTTLE_LEVER);
    public static final DeferredItem<BlockItem> ENERGY_METER = ITEMS.registerSimpleBlockItem(ModBlocks.ENERGY_METER);
    public static final DeferredItem<BlockItem> HEAT_METER = ITEMS.registerSimpleBlockItem(ModBlocks.HEAT_METER);
    public static final DeferredItem<BlockItem> FLUID_METER = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_METER);
    public static final DeferredItem<BlockItem> GAS_METER = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_METER);
    public static final DeferredItem<BlockItem> CHARGEPAD = ITEMS.registerSimpleBlockItem(ModBlocks.CHARGEPAD);
    public static final DeferredItem<BlockItem> ARC_MELTER = ITEMS.registerSimpleBlockItem(ModBlocks.ARC_MELTER);
    public static final DeferredItem<BlockItem> FERMENTER = ITEMS.registerSimpleBlockItem(ModBlocks.FERMENTER);
    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.CHEMICAL_REACTOR);
    public static final DeferredItem<BlockItem> ELECTROLYZER = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTROLYZER);
    public static final DeferredItem<BlockItem> ASSEMBLER = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> BLOCK_BREAKER = ITEMS.registerSimpleBlockItem(ModBlocks.BLOCK_BREAKER);
    public static final DeferredItem<BlockItem> BLOCK_PLACER = ITEMS.registerSimpleBlockItem(ModBlocks.BLOCK_PLACER);
    public static final DeferredItem<BlockItem> VACUUM_COLLECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.VACUUM_COLLECTOR);
    // Places a whole 3x3x3 (see ArcQuarryItem). The bounding parts have no item.
    public static final DeferredItem<ArcQuarryItem> ARC_QUARRY = ITEMS.registerItem("arc_quarry",
            p -> new ArcQuarryItem(ModBlocks.ARC_QUARRY.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> STEAM_BOILER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_BOILER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> STEAM_TURBINE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_TURBINE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> GAS_TURBINE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_TURBINE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> SUPERHEATER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.SUPERHEATER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> CONDENSER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.CONDENSER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> PRESSURE_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_GLASS);
    public static final DeferredItem<BlockItem> FIBERIZER = ITEMS.registerSimpleBlockItem(ModBlocks.FIBERIZER);
    public static final DeferredItem<BlockItem> INFUSER = ITEMS.registerSimpleBlockItem(ModBlocks.INFUSER);
    public static final DeferredItem<BlockItem> FUEL_BURNER = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_BURNER);

    public static final DeferredItem<SettingsCardItem> SETTINGS_CARD = ITEMS.registerItem("settings_card", SettingsCardItem::new);
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ConduitFilterItem> CONDUIT_FILTER = ITEMS.registerItem("conduit_filter", ConduitFilterItem::new, p -> p.stacksTo(16));
    // Encases a conduit (right-click it); ConduitBlock handles fitting it and copying a look onto it.
    public static final DeferredItem<Item> CONDUIT_COVER = ITEMS.registerSimpleItem("conduit_cover");
    public static final DeferredItem<EngineersHandbookItem> ENGINEERS_HANDBOOK = ITEMS.registerItem("engineers_handbook", EngineersHandbookItem::new, p -> p.stacksTo(1));

    // Steel tools and armour (between iron and diamond, mining at iron level), and the 3x3 Hammer and Excavator.
    public static final DeferredItem<Item> STEEL_SWORD = ITEMS.registerItem("steel_sword", Item::new, p -> p.sword(SteelMaterials.STEEL, 3.0F, -2.4F));
    public static final DeferredItem<Item> STEEL_PICKAXE = ITEMS.registerItem("steel_pickaxe", Item::new, p -> p.pickaxe(SteelMaterials.STEEL, 1.0F, -2.8F));
    public static final DeferredItem<Item> STEEL_AXE = ITEMS.registerItem("steel_axe", Item::new, p -> p.axe(SteelMaterials.STEEL, 6.0F, -3.1F));
    public static final DeferredItem<Item> STEEL_SHOVEL = ITEMS.registerItem("steel_shovel", Item::new, p -> p.shovel(SteelMaterials.STEEL, 1.5F, -3.0F));
    public static final DeferredItem<Item> STEEL_HOE = ITEMS.registerItem("steel_hoe", Item::new, p -> p.hoe(SteelMaterials.STEEL, -2.5F, -0.5F));
    public static final DeferredItem<AreaToolItem> STEEL_HAMMER = ITEMS.registerItem("steel_hammer", AreaToolItem::new,
            p -> p.pickaxe(SteelMaterials.STEEL_AREA, 2.0F, -3.2F));
    public static final DeferredItem<AreaToolItem> STEEL_EXCAVATOR = ITEMS.registerItem("steel_excavator", AreaToolItem::new,
            p -> p.shovel(SteelMaterials.STEEL_AREA, 1.5F, -3.1F));
    public static final DeferredItem<Item> STEEL_HELMET = ITEMS.registerItem("steel_helmet", Item::new, p -> p.humanoidArmor(SteelMaterials.STEEL_ARMOR, ArmorType.HELMET));
    public static final DeferredItem<Item> STEEL_CHESTPLATE = ITEMS.registerItem("steel_chestplate", Item::new, p -> p.humanoidArmor(SteelMaterials.STEEL_ARMOR, ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> STEEL_LEGGINGS = ITEMS.registerItem("steel_leggings", Item::new, p -> p.humanoidArmor(SteelMaterials.STEEL_ARMOR, ArmorType.LEGGINGS));
    public static final DeferredItem<Item> STEEL_BOOTS = ITEMS.registerItem("steel_boots", Item::new, p -> p.humanoidArmor(SteelMaterials.STEEL_ARMOR, ArmorType.BOOTS));

    // The Foundry Suit (see FoundrySuit).
    public static final DeferredItem<Item> FOUNDRY_HELMET = foundry("foundry_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> FOUNDRY_CHESTPLATE = foundry("foundry_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> FOUNDRY_LEGGINGS = foundry("foundry_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> FOUNDRY_BOOTS = foundry("foundry_boots", ArmorType.BOOTS);

    private static DeferredItem<Item> foundry(String name, ArmorType type) {
        return ITEMS.registerItem(name, p -> new Item(p) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                    java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
                builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.arcforge.foundry.piece").withStyle(net.minecraft.ChatFormatting.GRAY));
                builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.arcforge.foundry.set").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }, p -> p.humanoidArmor(FoundrySuit.ARMOR, type));
    }

    // Jetpacks, Arc Drills and Arc Saws in the Tempered, Hardened and Arcforged tiers, and the tools' modules.
    public static final DeferredItem<JetpackItem> TEMPERED_JETPACK = jetpack(ConduitTier.TEMPERED);
    public static final DeferredItem<JetpackItem> HARDENED_JETPACK = jetpack(ConduitTier.HARDENED);
    public static final DeferredItem<JetpackItem> ARCFORGED_JETPACK = jetpack(ConduitTier.ARCFORGED);
    public static final DeferredItem<ArcToolItem> TEMPERED_ARC_DRILL = arcTool(ConduitTier.TEMPERED, ArcToolItem.Kind.DRILL);
    public static final DeferredItem<ArcToolItem> HARDENED_ARC_DRILL = arcTool(ConduitTier.HARDENED, ArcToolItem.Kind.DRILL);
    public static final DeferredItem<ArcToolItem> ARCFORGED_ARC_DRILL = arcTool(ConduitTier.ARCFORGED, ArcToolItem.Kind.DRILL);
    public static final DeferredItem<ArcToolItem> TEMPERED_ARC_SAW = arcTool(ConduitTier.TEMPERED, ArcToolItem.Kind.SAW);
    public static final DeferredItem<ArcToolItem> HARDENED_ARC_SAW = arcTool(ConduitTier.HARDENED, ArcToolItem.Kind.SAW);
    public static final DeferredItem<ArcToolItem> ARCFORGED_ARC_SAW = arcTool(ConduitTier.ARCFORGED, ArcToolItem.Kind.SAW);
    private static final Map<ModuleType, DeferredItem<ToolModuleItem>> TOOL_MODULES = new EnumMap<>(ModuleType.class);
    static {
        for (ModuleType type : ModuleType.values()) {
            TOOL_MODULES.put(type, ITEMS.registerItem(type.getSerializedName() + "_module", p -> new ToolModuleItem(type, p)));
        }
    }

    private static DeferredItem<JetpackItem> jetpack(ConduitTier tier) {
        return ITEMS.registerItem(tier.getSerializedName() + "_jetpack", p -> new JetpackItem(tier, p));
    }

    private static DeferredItem<ArcToolItem> arcTool(ConduitTier tier, ArcToolItem.Kind kind) {
        return ITEMS.registerItem(tier.getSerializedName() + (kind == ArcToolItem.Kind.DRILL ? "_arc_drill" : "_arc_saw"),
                p -> new ArcToolItem(tier, kind, p), p -> ArcToolItem.properties(p, tier, kind));
    }

    public static DeferredItem<ToolModuleItem> toolModule(ModuleType type) {
        return TOOL_MODULES.get(type);
    }

    public static List<DeferredItem<JetpackItem>> jetpacks() {
        return List.of(TEMPERED_JETPACK, HARDENED_JETPACK, ARCFORGED_JETPACK);
    }

    public static List<DeferredItem<ArcToolItem>> arcTools() {
        return List.of(TEMPERED_ARC_DRILL, HARDENED_ARC_DRILL, ARCFORGED_ARC_DRILL, TEMPERED_ARC_SAW, HARDENED_ARC_SAW, ARCFORGED_ARC_SAW);
    }

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
    public static final DeferredItem<Item> SULFUR_DUST = ITEMS.registerSimpleItem("sulfur_dust");

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
    // Light Oil and Hydrogen, reacted in the Chemical Reactor (#c:plastics).
    public static final DeferredItem<Item> PLASTIC_SHEET = ITEMS.registerSimpleItem("plastic_sheet");
    // Arc Quarry parts.
    public static final DeferredItem<Item> TUNGSTEN_DRILL_HEAD = ITEMS.registerSimpleItem("tungsten_drill_head");
    public static final DeferredItem<Item> QUARRY_SCANNER = ITEMS.registerSimpleItem("quarry_scanner");

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
    public static final DeferredItem<BucketItem> ETHANOL_BUCKET = ITEMS.registerItem("ethanol_bucket",
            p -> new BucketItem(ModFluids.ETHANOL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> NAPHTHA_BUCKET = ITEMS.registerItem("naphtha_bucket",
            p -> new BucketItem(ModFluids.NAPHTHA.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LIGHT_OIL_BUCKET = ITEMS.registerItem("light_oil_bucket",
            p -> new BucketItem(ModFluids.LIGHT_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> HEAVY_OIL_BUCKET = ITEMS.registerItem("heavy_oil_bucket",
            p -> new BucketItem(ModFluids.HEAVY_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SULFURIC_ACID_BUCKET = ITEMS.registerItem("sulfuric_acid_bucket",
            p -> new BucketItem(ModFluids.SULFURIC_ACID.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    private static final Map<OreSlurry, DeferredItem<BucketItem>> SLURRY_BUCKETS = registerSlurryBuckets();

    public static DeferredItem<BucketItem> slurryBucket(OreSlurry slurry) {
        return SLURRY_BUCKETS.get(slurry);
    }

    private static Map<OreSlurry, DeferredItem<BucketItem>> registerSlurryBuckets() {
        Map<OreSlurry, DeferredItem<BucketItem>> buckets = new EnumMap<>(OreSlurry.class);
        for (OreSlurry slurry : OreSlurry.values()) {
            buckets.put(slurry, ITEMS.registerItem(slurry.fluidName() + "_bucket",
                    p -> new BucketItem(ModFluids.slurry(slurry).source().get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1)));
        }
        return buckets;
    }

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
    public static final DeferredItem<BlockItem> NETHER_SULFUR_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.NETHER_SULFUR_ORE);
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
    // Sulfuric Acid and the slurries, for the Fluids tab.
    public static List<DeferredItem<BucketItem>> chemicalBuckets() {
        List<DeferredItem<BucketItem>> buckets = new ArrayList<>();
        buckets.add(SULFURIC_ACID_BUCKET);
        for (OreSlurry slurry : OreSlurry.values()) {
            buckets.add(slurryBucket(slurry));
        }
        return buckets;
    }

    public static List<DeferredItem<? extends Item>> ores() {
        return List.of(SILVER_ORE, DEEPSLATE_SILVER_ORE, RAW_SILVER, RAW_SILVER_BLOCK, SILVER_DUST, SILVER_INGOT, SILVER_BLOCK, NICKEL_ORE, DEEPSLATE_NICKEL_ORE, RAW_NICKEL, RAW_NICKEL_BLOCK, NICKEL_DUST, NICKEL_INGOT, NICKEL_BLOCK, WOLFRAMITE_ORE, DEEPSLATE_WOLFRAMITE_ORE, RAW_WOLFRAMITE, RAW_WOLFRAMITE_BLOCK, TUNGSTEN_DUST, TUNGSTEN_INGOT, TUNGSTEN_BLOCK, FLUORITE_ORE, DEEPSLATE_FLUORITE_ORE, RAW_FLUORITE, RAW_FLUORITE_BLOCK, FLUORITE_DUST, FLUORITE_CRYSTAL, FLUORITE_BLOCK, BISMUTH_ORE, DEEPSLATE_BISMUTH_ORE, RAW_BISMUTH, RAW_BISMUTH_BLOCK, BISMUTH_DUST, BISMUTH_INGOT, BISMUTH_BLOCK, ARCITE_ORE, DEEPSLATE_ARCITE_ORE, RAW_ARCITE, RAW_ARCITE_BLOCK, ARCITE_DUST, ARCITE_CRYSTAL, ARCITE_BLOCK, NETHER_SULFUR_ORE, INVAR_DUST, INVAR_INGOT);
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

    // --- Gas Turbine Array parts ---

    public static final DeferredItem<Item> TURBINE_BLADE_SET = ITEMS.registerSimpleItem("turbine_blade_set");
    public static final DeferredItem<Item> COMBUSTOR = ITEMS.registerSimpleItem("combustor");
    public static final DeferredItem<BlockItem> ASPHALT = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT);

    // --- Farming ---

    public static final DeferredItem<BlockItem> COMPOST_BIN = ITEMS.registerSimpleBlockItem(ModBlocks.COMPOST_BIN);
    public static final DeferredItem<BlockItem> LOAM = ITEMS.registerSimpleBlockItem(ModBlocks.LOAM);
    public static final DeferredItem<BlockItem> IRRIGATED_LOAM_FARMLAND = ITEMS.registerSimpleBlockItem(ModBlocks.IRRIGATED_LOAM_FARMLAND);
    // Fertilizers: each adds its nutrients (config farming.fertilizers) to Loam Farmland.
    public static final DeferredItem<FertilizerItem> COMPOST = ITEMS.registerItem("compost",
            p -> new FertilizerItem(ArcforgeConfig.COMPOST_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> WOOD_ASH = ITEMS.registerItem("wood_ash",
            p -> new FertilizerItem(ArcforgeConfig.WOOD_ASH_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> BASIC_SLAG = ITEMS.registerItem("basic_slag",
            p -> new FertilizerItem(ArcforgeConfig.BASIC_SLAG_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> MIXED_FERTILIZER = ITEMS.registerItem("mixed_fertilizer",
            p -> new FertilizerItem(ArcforgeConfig.MIXED_FERTILIZER_NUTRIENTS::getAsInt, p));

    // Crops. Seeds that plant a crop are block items named as items ("Flax Seeds", not the crop). Compost Bins take them
    // at vanilla's chances: 30% for seeds, 65% for the harvest.
    public static final DeferredItem<BlockItem> FLAX_SEEDS = ITEMS.registerItem("flax_seeds",
            p -> new BlockItem(ModBlocks.FLAX.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<Item> FLAX_FIBRE = ITEMS.registerSimpleItem("flax_fibre", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> LINEN = ITEMS.registerSimpleItem("linen");
    public static final DeferredItem<BlockItem> RAPESEEDS = ITEMS.registerItem("rapeseeds",
            p -> new BlockItem(ModBlocks.RAPESEED.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<BlockItem> SORGHUM_SEEDS = ITEMS.registerItem("sorghum_seeds",
            p -> new BlockItem(ModBlocks.SORGHUM.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<Item> SORGHUM_STALKS = ITEMS.registerSimpleItem("sorghum_stalks", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    // Hop Seeds are planted by using them on a Trellis (TrellisBlock), so they're a plain item.
    public static final DeferredItem<Item> HOP_SEEDS = ITEMS.registerSimpleItem("hop_seeds", p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
    public static final DeferredItem<Item> HOP_CONES = ITEMS.registerSimpleItem("hop_cones", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> TRELLIS = ITEMS.registerSimpleBlockItem(ModBlocks.TRELLIS);
    public static final DeferredItem<BlockItem> WILD_FLAX = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_FLAX, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_RAPESEED = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_RAPESEED, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_SORGHUM = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_SORGHUM, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_HOPS = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_HOPS, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

    // Rustic farming machines and tools. Durability is fixed here (it's set before the config loads); the areas are config.
    public static final DeferredItem<BlockItem> PLANTER = ITEMS.registerSimpleBlockItem(ModBlocks.PLANTER);
    public static final DeferredItem<BlockItem> HARVESTER = ITEMS.registerSimpleBlockItem(ModBlocks.HARVESTER);
    public static final DeferredItem<BlockItem> FERTILIZER_SPREADER = ITEMS.registerSimpleBlockItem(ModBlocks.FERTILIZER_SPREADER);
    public static final DeferredItem<BlockItem> COPPER_SPRINKLER = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_SPRINKLER);
    public static final DeferredItem<DoubleHighBlockItem> SCARECROW = ITEMS.registerItem("scarecrow",
            p -> new DoubleHighBlockItem(ModBlocks.SCARECROW.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<SickleItem> IRON_SICKLE = ITEMS.registerItem("iron_sickle",
            p -> new SickleItem(ArcforgeConfig.SICKLE_RADIUS::getAsInt, p), p -> p.durability(250).repairable(ItemTags.IRON_TOOL_MATERIALS).enchantable(14));
    public static final DeferredItem<SickleItem> STEEL_SICKLE = ITEMS.registerItem("steel_sickle",
            p -> new SickleItem(ArcforgeConfig.SICKLE_RADIUS::getAsInt, p), p -> p.durability(500).repairable(ModItemTags.STEEL_INGOTS).enchantable(12));
    public static final DeferredItem<SickleItem> STEEL_SCYTHE = ITEMS.registerItem("steel_scythe",
            p -> new SickleItem(ArcforgeConfig.SCYTHE_RADIUS::getAsInt, p), p -> p.durability(1_000).repairable(ModItemTags.STEEL_INGOTS).enchantable(12));

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

    // Crate and Vault items (ordered like ModBlocks.allCratesAndVaults()), then the Storage Upgrades.
    private static final List<DeferredItem<? extends BlockItem>> CRATES_AND_VAULTS = new ArrayList<>();
    private static final List<DeferredItem<StorageUpgradeItem>> STORAGE_UPGRADES = new ArrayList<>();

    static {
        for (ConduitTier tier : ConduitTier.values()) {
            CRATES_AND_VAULTS.add(ITEMS.registerItem(tier.getSerializedName() + "_crate",
                    p -> new CrateBlockItem(ModBlocks.crate(tier).get(), p), p -> p.useBlockDescriptionPrefix()));
        }
        for (ConduitTier tier : ConduitTier.values()) {
            CRATES_AND_VAULTS.add(ITEMS.registerItem(tier.getSerializedName() + "_vault",
                    p -> new VaultBlockItem(ModBlocks.vault(tier).get(), p), p -> p.useBlockDescriptionPrefix()));
        }
        for (ConduitTier tier : ConduitTier.values()) {
            if (tier.previous() != null) {
                STORAGE_UPGRADES.add(ITEMS.registerItem(tier.getSerializedName() + "_storage_upgrade",
                        p -> new StorageUpgradeItem(tier, p), p -> p.stacksTo(16)));
            }
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

    public static List<DeferredItem<? extends BlockItem>> allCratesAndVaults() {
        return CRATES_AND_VAULTS;
    }

    public static List<DeferredItem<StorageUpgradeItem>> storageUpgrades() {
        return STORAGE_UPGRADES;
    }

    public static void register(IEventBus modEventBus) {
        // Removed in 2.0: Steam Boilers and Steam Turbines in inventories become array casings.
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler_array_casing"));
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine_array_casing"));
        ITEMS.register(modEventBus);
    }
}
