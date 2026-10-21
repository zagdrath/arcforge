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
import net.minecraft.world.item.Rarity;
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
import net.zagdrath.arcforge.item.storage.ItemReservoirItem;
import net.zagdrath.arcforge.item.storage.ReservoirItem;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.item.conduit.ConduitBlockItem;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.item.farming.SickleItem;
import net.zagdrath.arcforge.item.conduit.ConduitFilterItem;
import net.zagdrath.arcforge.item.machine.ArcQuarryItem;
import net.zagdrath.arcforge.item.storage.CrateBlockItem;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.storage.StorageBlockItem;
import net.zagdrath.arcforge.item.storage.BatteryPartItem;
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
    public static final DeferredItem<BlockItem> FIREBOX_ARRAY_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.FIREBOX_ARRAY_CONTROLLER);
    public static final DeferredItem<BlockItem> FIREBOX_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.FIREBOX_ARRAY_CASING);
    public static final DeferredItem<BlockItem> BATTERY_ARRAY_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.BATTERY_ARRAY_CONTROLLER);
    public static final DeferredItem<BlockItem> BATTERY_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.BATTERY_ARRAY_CASING);
    public static final DeferredItem<BlockItem> THERMOELECTRIC_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.THERMOELECTRIC_PLANT);
    public static final DeferredItem<BlockItem> ARC_CRUSHER = ITEMS.registerSimpleBlockItem(ModBlocks.ARC_CRUSHER);
    public static final DeferredItem<BlockItem> ARC_CRUSHING_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.ARC_CRUSHING_ARRAY_CASING);
    public static final DeferredItem<BlockItem> INDUCTION_FURNACE = ITEMS.registerSimpleBlockItem(ModBlocks.INDUCTION_FURNACE);
    public static final DeferredItem<BlockItem> INDUCTION_FURNACE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.INDUCTION_FURNACE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.CHEMICAL_REACTOR_ARRAY_CASING);
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
    public static final DeferredItem<BlockItem> SIFTER = ITEMS.registerSimpleBlockItem(ModBlocks.SIFTER);
    public static final DeferredItem<BlockItem> DIAMOND_PRESS = ITEMS.registerSimpleBlockItem(ModBlocks.DIAMOND_PRESS);
    public static final DeferredItem<BlockItem> DEEPSLATE_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_GRAVEL);
    // Sifter Meshes, by tier (see SifterMeshItem): they wear out, less with Unbreaking.
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SifterMeshItem> STEEL_MESH = ITEMS.registerItem("steel_mesh",
            p -> new net.zagdrath.arcforge.item.tool.SifterMeshItem(net.zagdrath.arcforge.item.tool.SifterMeshItem.Tier.STEEL, p.enchantable(10)));
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SifterMeshItem> INVAR_MESH = ITEMS.registerItem("invar_mesh",
            p -> new net.zagdrath.arcforge.item.tool.SifterMeshItem(net.zagdrath.arcforge.item.tool.SifterMeshItem.Tier.INVAR, p.enchantable(10)));
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SifterMeshItem> TUNGSTEN_MESH = ITEMS.registerItem("tungsten_mesh",
            p -> new net.zagdrath.arcforge.item.tool.SifterMeshItem(net.zagdrath.arcforge.item.tool.SifterMeshItem.Tier.TUNGSTEN, p.enchantable(10)));
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SifterMeshItem> SILICON_CARBIDE_MESH = ITEMS.registerItem("silicon_carbide_mesh",
            p -> new net.zagdrath.arcforge.item.tool.SifterMeshItem(net.zagdrath.arcforge.item.tool.SifterMeshItem.Tier.SILICON_CARBIDE, p.enchantable(12)));
    public static final DeferredItem<BlockItem> CARBON_RECLAIMER = ITEMS.registerSimpleBlockItem(ModBlocks.CARBON_RECLAIMER);
    public static final DeferredItem<BlockItem> GASIFIER = ITEMS.registerSimpleBlockItem(ModBlocks.GASIFIER);
    public static final DeferredItem<BlockItem> FISCHER_TROPSCH_REACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.FISCHER_TROPSCH_REACTOR);
    public static final DeferredItem<BlockItem> ASSEMBLER = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> BLOCK_BREAKER = ITEMS.registerSimpleBlockItem(ModBlocks.BLOCK_BREAKER);
    public static final DeferredItem<BlockItem> TREE_CUTTER = ITEMS.registerSimpleBlockItem(ModBlocks.TREE_CUTTER);
    public static final DeferredItem<BlockItem> BLOCK_PLACER = ITEMS.registerSimpleBlockItem(ModBlocks.BLOCK_PLACER);
    public static final DeferredItem<BlockItem> VACUUM_COLLECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.VACUUM_COLLECTOR);
    public static final DeferredItem<ReservoirItem> FLUID_RESERVOIR = ITEMS.registerItem("fluid_reservoir",
            p -> new ReservoirItem(ModBlocks.FLUID_RESERVOIR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<ReservoirItem> GAS_RESERVOIR = ITEMS.registerItem("gas_reservoir",
            p -> new ReservoirItem(ModBlocks.GAS_RESERVOIR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<ItemReservoirItem> ITEM_RESERVOIR = ITEMS.registerItem("item_reservoir",
            p -> new ItemReservoirItem(ModBlocks.ITEM_RESERVOIR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> XP_DRAIN = ITEMS.registerSimpleBlockItem(ModBlocks.XP_DRAIN);
    public static final DeferredItem<BlockItem> XP_SHOWER = ITEMS.registerSimpleBlockItem(ModBlocks.XP_SHOWER);
    public static final DeferredItem<BlockItem> QUANTUM_TUNNEL = ITEMS.registerSimpleBlockItem(ModBlocks.QUANTUM_TUNNEL, p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> HEAT_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.HEAT_PIPE, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> CHUNK_LOADER = ITEMS.registerSimpleBlockItem(ModBlocks.CHUNK_LOADER, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> WIRED_MACHINE_LINK = ITEMS.registerSimpleBlockItem(ModBlocks.WIRED_MACHINE_LINK, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> LINK_CONDUIT = ITEMS.registerSimpleBlockItem(ModBlocks.LINK_CONDUIT);
    // Places a whole 3x3x3 (see ArcQuarryItem). The bounding parts have no item.
    public static final DeferredItem<ArcQuarryItem> ARC_QUARRY = ITEMS.registerItem("arc_quarry",
            p -> new ArcQuarryItem(ModBlocks.ARC_QUARRY.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> STEAM_BOILER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_BOILER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> STEAM_TURBINE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_TURBINE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> GAS_TURBINE_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_TURBINE_ARRAY_CASING);
    public static final DeferredItem<BlockItem> SUPERHEATER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.SUPERHEATER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> CONDENSER_ARRAY_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.CONDENSER_ARRAY_CASING);
    public static final DeferredItem<BlockItem> WATER_INTAKE_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_INTAKE_CASING);
    public static final DeferredItem<BlockItem> PRESSURE_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_GLASS);
    public static final DeferredItem<BlockItem> FIBERIZER = ITEMS.registerSimpleBlockItem(ModBlocks.FIBERIZER);
    public static final DeferredItem<BlockItem> INFUSER = ITEMS.registerSimpleBlockItem(ModBlocks.INFUSER);
    public static final DeferredItem<BlockItem> ELECTRIC_ENCHANTER = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_ENCHANTER);
    public static final DeferredItem<BlockItem> FUEL_BURNER = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_BURNER);

    public static final DeferredItem<SettingsCardItem> SETTINGS_CARD = ITEMS.registerItem("settings_card", SettingsCardItem::new);
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ConduitFilterItem> CONDUIT_FILTER = ITEMS.registerItem("conduit_filter", ConduitFilterItem::new, p -> p.stacksTo(16));
    // Covers one side of a conduit (right-click it); ConduitBlock handles mounting it and dressing it in a block.
    public static final DeferredItem<net.zagdrath.arcforge.item.conduit.ConduitFacadeItem> CONDUIT_FACADE = ITEMS.registerItem("conduit_facade",
            net.zagdrath.arcforge.item.conduit.ConduitFacadeItem::new);
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
    // Bio-Coal burns as long as coal (1,600 ticks), its block as a coal block (16,000).
    private static final ResourceKey<ContextIntProvider> BIO_COAL_BURN_TIME = cookingTime("time_bio_coal");
    private static final ResourceKey<ContextIntProvider> BIO_COAL_BLOCK_BURN_TIME = cookingTime("time_bio_coal_block");

    public static final DeferredItem<BlockItem> CARBONIZER = ITEMS.registerSimpleBlockItem(ModBlocks.CARBONIZER);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_PORT = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_PORT);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_BRICKS);
    public static final DeferredItem<BlockItem> ARCFORGE_FURNACE_BRICK_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.ARCFORGE_FURNACE_BRICK_WALL);

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<BlockItem> STEEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_BLOCK);
    public static final DeferredItem<Item> COAL_COKE = ITEMS.registerSimpleItem("coal_coke", p -> p.cookingFuel(COAL_COKE_BURN_TIME));
    // The Coker's Petroleum Coke: tagged c:coal_coke, so it is Coal Coke everywhere (Arcforge Furnace fuel and reagent,
    // Graphite, the Gasifier, the Firebox Array), and burns as long.
    public static final DeferredItem<Item> PETROLEUM_COKE = ITEMS.registerSimpleItem("petroleum_coke", p -> p.cookingFuel(COAL_COKE_BURN_TIME));
    // The Cracking Unit's catalyst: zeolite pellets, worn away slowly (see the cracking recipes' catalyst_uses).
    public static final DeferredItem<Item> CRACKING_CATALYST = ITEMS.registerSimpleItem("cracking_catalyst");
    public static final DeferredItem<BlockItem> COAL_COKE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.COAL_COKE_BLOCK,
            p -> p.cookingFuel(COAL_COKE_BLOCK_BURN_TIME));
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("slag");
    // Renewable coal: biomass cooked in water under heat (the Hydrothermal Carbonizer). It works as coal everywhere.
    public static final DeferredItem<Item> BIO_COAL = ITEMS.registerSimpleItem("bio_coal", p -> p.cookingFuel(BIO_COAL_BURN_TIME));
    public static final DeferredItem<BlockItem> BIO_COAL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.BIO_COAL_BLOCK,
            p -> p.cookingFuel(BIO_COAL_BLOCK_BURN_TIME));

    // --- Crushing ---

    private static final ResourceKey<ContextIntProvider> CARBON_DUST_BURN_TIME = cookingTime("time_carbon_dust");

    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust");
    public static final DeferredItem<Item> ANCIENT_DEBRIS_DUST = ITEMS.registerSimpleItem("ancient_debris_dust");
    public static final DeferredItem<Item> CARBON_DUST = ITEMS.registerSimpleItem("carbon_dust", p -> p.cookingFuel(CARBON_DUST_BURN_TIME));
    public static final DeferredItem<Item> NETHER_QUARTZ_DUST = ITEMS.registerSimpleItem("nether_quartz_dust");
    // The silica line: Silica Dust (crushed Flint or Quartz) into glass, Ferrosilicon (a steel flux), Silica Gel (a cracking
    // catalyst carrier), Silicon Carbide (the top Sifter Mesh) and Silicon, refined to Polysilicon.
    public static final DeferredItem<Item> SILICA_DUST = ITEMS.registerSimpleItem("silica_dust");
    public static final DeferredItem<Item> FERROSILICON = ITEMS.registerSimpleItem("ferrosilicon");
    public static final DeferredItem<Item> SILICA_GEL = ITEMS.registerSimpleItem("silica_gel");
    public static final DeferredItem<Item> SILICON_CARBIDE_BLEND = ITEMS.registerSimpleItem("silicon_carbide_blend");
    public static final DeferredItem<Item> SILICON_CARBIDE = ITEMS.registerSimpleItem("silicon_carbide");
    public static final DeferredItem<Item> METALLURGICAL_SILICON = ITEMS.registerSimpleItem("metallurgical_silicon");
    public static final DeferredItem<Item> POLYSILICON = ITEMS.registerSimpleItem("polysilicon");
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
    // Ethylene and Chlorine, reacted in the Chemical Reactor: an acid-proof lining for fluid conduits and tanks.
    public static final DeferredItem<Item> PVC_SHEET = ITEMS.registerSimpleItem("pvc_sheet");
    // From Propylene in the Chemical Reactor; any arcforge:plastics recipe takes it.
    public static final DeferredItem<Item> POLYPROPYLENE_SHEET = ITEMS.registerSimpleItem("polypropylene_sheet");
    // Rubber: Raw Rubber (Latex dried in the Grain Dryer) cured with Sulfur in the Vulcanizer. Gaskets seal the
    // pressurized conduits, cylinders, cartridges and jetpacks.
    public static final DeferredItem<Item> RAW_RUBBER = ITEMS.registerSimpleItem("raw_rubber");
    // EPDM: Ethylene and Propylene over a Cracking Catalyst in the Chemical Reactor. It cures in the Vulcanizer as Raw
    // Rubber does (both are in arcforge:raw_rubber).
    public static final DeferredItem<Item> SYNTHETIC_RUBBER = ITEMS.registerSimpleItem("synthetic_rubber");
    public static final DeferredItem<Item> RUBBER = ITEMS.registerSimpleItem("rubber");
    public static final DeferredItem<BlockItem> RUBBER_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RUBBER_BLOCK);
    public static final DeferredItem<Item> RUBBER_GASKET = ITEMS.registerSimpleItem("rubber_gasket");
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
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE = ITEMS.registerItem("range_upgrade", p -> new UpgradeItem(UpgradeType.RANGE, p));
    // Creative only for now: no recipe.
    public static final DeferredItem<UpgradeItem> REACTOR_AUTOPILOT = ITEMS.registerItem("reactor_autopilot",
            p -> new UpgradeItem(UpgradeType.AUTOPILOT, p.stacksTo(1)));

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
    public static final DeferredItem<BucketItem> CRUDE_OIL_BUCKET = ITEMS.registerItem("crude_oil_bucket",
            p -> new BucketItem(ModFluids.CRUDE_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> KEROSENE_BUCKET = ITEMS.registerItem("kerosene_bucket",
            p -> new BucketItem(ModFluids.KEROSENE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> GASOLINE_BUCKET = ITEMS.registerItem("gasoline_bucket",
            p -> new BucketItem(ModFluids.GASOLINE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LUBRICANT_BUCKET = ITEMS.registerItem("lubricant_bucket",
            p -> new BucketItem(ModFluids.LUBRICANT.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> DIESEL_BUCKET = ITEMS.registerItem("diesel_bucket",
            p -> new BucketItem(ModFluids.DIESEL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> BITUMEN_BUCKET = ITEMS.registerItem("bitumen_bucket",
            p -> new BucketItem(ModFluids.BITUMEN.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SOUR_CRUDE_OIL_BUCKET = ITEMS.registerItem("sour_crude_oil_bucket",
            p -> new BucketItem(ModFluids.SOUR_CRUDE_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SOUR_KEROSENE_BUCKET = ITEMS.registerItem("sour_kerosene_bucket",
            p -> new BucketItem(ModFluids.SOUR_KEROSENE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SOUR_DIESEL_BUCKET = ITEMS.registerItem("sour_diesel_bucket",
            p -> new BucketItem(ModFluids.SOUR_DIESEL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SULFURIC_ACID_BUCKET = ITEMS.registerItem("sulfuric_acid_bucket",
            p -> new BucketItem(ModFluids.SULFURIC_ACID.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SEED_OIL_BUCKET = ITEMS.registerItem("seed_oil_bucket",
            p -> new BucketItem(ModFluids.SEED_OIL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> NUTRIENT_SOLUTION_BUCKET = ITEMS.registerItem("nutrient_solution_bucket",
            p -> new BucketItem(ModFluids.NUTRIENT_SOLUTION.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> BIODIESEL_BUCKET = ITEMS.registerItem("biodiesel_bucket",
            p -> new BucketItem(ModFluids.BIODIESEL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> BRINE_BUCKET = ITEMS.registerItem("brine_bucket",
            p -> new BucketItem(ModFluids.BRINE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LITHIUM_BRINE_BUCKET = ITEMS.registerItem("lithium_brine_bucket",
            p -> new BucketItem(ModFluids.LITHIUM_BRINE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LYE_BUCKET = ITEMS.registerItem("lye_bucket",
            p -> new BucketItem(ModFluids.LYE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> HYDROCHLORIC_ACID_BUCKET = ITEMS.registerItem("hydrochloric_acid_bucket",
            p -> new BucketItem(ModFluids.HYDROCHLORIC_ACID.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SODIUM_SILICATE_BUCKET = ITEMS.registerItem("sodium_silicate_bucket",
            p -> new BucketItem(ModFluids.SODIUM_SILICATE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> TRICHLOROSILANE_BUCKET = ITEMS.registerItem("trichlorosilane_bucket",
            p -> new BucketItem(ModFluids.TRICHLOROSILANE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    // --- Salt ---

    public static final DeferredItem<Item> SALT = ITEMS.registerSimpleItem("salt");
    public static final DeferredItem<BlockItem> SALT_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.SALT_BLOCK);
    public static final DeferredItem<BlockItem> THERMAL_EVAPORATOR_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.THERMAL_EVAPORATOR_CASING);
    public static final DeferredItem<BlockItem> THERMAL_EVAPORATOR_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.THERMAL_EVAPORATOR_CONTROLLER);
    // The oilfield.
    public static final DeferredItem<BlockItem> DERRICK_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.DERRICK_CASING);
    public static final DeferredItem<BlockItem> DERRICK_FRAME = ITEMS.registerSimpleBlockItem(ModBlocks.DERRICK_FRAME);
    public static final DeferredItem<BlockItem> OIL_DERRICK_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_DERRICK_CONTROLLER);
    public static final DeferredItem<BlockItem> WELL_HEAD = ITEMS.registerSimpleBlockItem(ModBlocks.WELL_HEAD);
    public static final DeferredItem<BlockItem> PUMPJACK_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.PUMPJACK_CASING);
    public static final DeferredItem<BlockItem> PUMPJACK_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.PUMPJACK_CONTROLLER);
    // The refinery's downstream units.
    public static final DeferredItem<BlockItem> CATALYTIC_REFORMER = ITEMS.registerSimpleBlockItem(ModBlocks.CATALYTIC_REFORMER);
    public static final DeferredItem<BlockItem> COKER = ITEMS.registerSimpleBlockItem(ModBlocks.COKER);
    public static final DeferredItem<BlockItem> LUBRICANT_BLENDER = ITEMS.registerSimpleBlockItem(ModBlocks.LUBRICANT_BLENDER);
    public static final DeferredItem<BlockItem> CRACKING_UNIT_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.CRACKING_UNIT_CASING);
    public static final DeferredItem<BlockItem> CRACKING_UNIT_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.CRACKING_UNIT_CONTROLLER);
    public static final DeferredItem<BlockItem> STEAM_CRACKER_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_CRACKER_CASING);
    public static final DeferredItem<BlockItem> STEAM_CRACKER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_CRACKER_CONTROLLER);
    public static final DeferredItem<BlockItem> DIESEL_GENERATOR_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.DIESEL_GENERATOR_CASING);
    public static final DeferredItem<BlockItem> DIESEL_GENERATOR_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.DIESEL_GENERATOR_CONTROLLER);
    public static final DeferredItem<BlockItem> FLARE_STACK = ITEMS.registerSimpleBlockItem(ModBlocks.FLARE_STACK);
    public static final DeferredItem<BlockItem> HYDROTREATER = ITEMS.registerSimpleBlockItem(ModBlocks.HYDROTREATER);
    public static final DeferredItem<BlockItem> SULFUR_RECOVERY_UNIT = ITEMS.registerSimpleBlockItem(ModBlocks.SULFUR_RECOVERY_UNIT);
    public static final DeferredItem<BlockItem> STEAM_METHANE_REFORMER = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_METHANE_REFORMER);
    public static final DeferredItem<BlockItem> GAS_WELLHEAD = ITEMS.registerSimpleBlockItem(ModBlocks.GAS_WELLHEAD);
    public static final DeferredItem<Item> STEEL_PIPE = ITEMS.registerSimpleItem("steel_pipe");
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SeismicSurveyToolItem> SEISMIC_SURVEY_TOOL = ITEMS.registerItem(
            "seismic_survey_tool", net.zagdrath.arcforge.item.tool.SeismicSurveyToolItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.SurveyRecordItem> SURVEY_RECORD = ITEMS.registerItem(
            "survey_record", net.zagdrath.arcforge.item.tool.SurveyRecordItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<BucketItem> LATEX_BUCKET = ITEMS.registerItem("latex_bucket",
            p -> new BucketItem(ModFluids.LATEX.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> SEAWATER_BUCKET = ITEMS.registerItem("seawater_bucket",
            p -> new BucketItem(ModFluids.SEAWATER.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> LIQUID_EXPERIENCE_BUCKET = ITEMS.registerItem("liquid_experience_bucket",
            p -> new BucketItem(ModFluids.LIQUID_EXPERIENCE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    // Halite and what it gives: Rock Salt (the Arc Crusher makes Salt of it) and its raw block.
    public static final DeferredItem<BlockItem> HALITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.HALITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_HALITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_HALITE_ORE);
    public static final DeferredItem<Item> ROCK_SALT = ITEMS.registerSimpleItem("rock_salt");
    public static final DeferredItem<BlockItem> RAW_ROCK_SALT_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_ROCK_SALT_BLOCK);

    // Spodumene (the lithium ore) and what it gives, and the lithium chemistry and battery parts.
    public static final DeferredItem<BlockItem> SPODUMENE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SPODUMENE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SPODUMENE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SPODUMENE_ORE);
    public static final DeferredItem<Item> RAW_SPODUMENE = ITEMS.registerSimpleItem("raw_spodumene");
    public static final DeferredItem<BlockItem> RAW_SPODUMENE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_SPODUMENE_BLOCK);
    public static final DeferredItem<Item> SPODUMENE_DUST = ITEMS.registerSimpleItem("spodumene_dust");
    public static final DeferredItem<Item> LITHIUM_HYDROXIDE = ITEMS.registerSimpleItem("lithium_hydroxide");
    public static final DeferredItem<Item> GRAPHITE = ITEMS.registerSimpleItem("graphite");
    public static final DeferredItem<BlockItem> GRAPHITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.GRAPHITE_BLOCK);
    public static final DeferredItem<Item> LFP_CATHODE = ITEMS.registerSimpleItem("lfp_cathode");
    public static final DeferredItem<Item> GRAPHITE_ANODE = ITEMS.registerSimpleItem("graphite_anode");
    public static final DeferredItem<Item> CELL_SEPARATOR = ITEMS.registerSimpleItem("cell_separator");

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
    public static final DeferredItem<BlockItem> IOLITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.IOLITE_ORE);
    public static final DeferredItem<Item> IOLITE_DUST = ITEMS.registerSimpleItem("iolite_dust");
    public static final DeferredItem<Item> IOLITE_CRYSTAL = ITEMS.registerSimpleItem("iolite_crystal");
    public static final DeferredItem<BlockItem> IOLITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.IOLITE_BLOCK);
    public static final DeferredItem<Item> SILVER_PLATE = ITEMS.registerSimpleItem("silver_plate");
    public static final DeferredItem<Item> SILVER_GEAR = ITEMS.registerSimpleItem("silver_gear");
    public static final DeferredItem<Item> SILVER_ROD = ITEMS.registerSimpleItem("silver_rod");
    public static final DeferredItem<Item> NICKEL_PLATE = ITEMS.registerSimpleItem("nickel_plate");
    public static final DeferredItem<Item> NICKEL_GEAR = ITEMS.registerSimpleItem("nickel_gear");
    public static final DeferredItem<Item> NICKEL_ROD = ITEMS.registerSimpleItem("nickel_rod");
    public static final DeferredItem<Item> TUNGSTEN_PLATE = ITEMS.registerSimpleItem("tungsten_plate");
    public static final DeferredItem<Item> TUNGSTEN_GEAR = ITEMS.registerSimpleItem("tungsten_gear");
    public static final DeferredItem<Item> TUNGSTEN_ROD = ITEMS.registerSimpleItem("tungsten_rod");
    public static final DeferredItem<Item> INVAR_PLATE = ITEMS.registerSimpleItem("invar_plate");
    public static final DeferredItem<Item> INVAR_GEAR = ITEMS.registerSimpleItem("invar_gear");
    public static final DeferredItem<Item> INVAR_ROD = ITEMS.registerSimpleItem("invar_rod");
    public static final DeferredItem<Item> INVAR_DUST = ITEMS.registerSimpleItem("invar_dust");
    public static final DeferredItem<Item> INVAR_INGOT = ITEMS.registerSimpleItem("invar_ingot");
    public static final DeferredItem<Item> TUNGSTEN_HEATING_COIL = ITEMS.registerSimpleItem("tungsten_heating_coil");
    public static final DeferredItem<Item> THERMOCOUPLE = ITEMS.registerSimpleItem("thermocouple");
    public static final DeferredItem<Item> ARCITE_TUNGSTEN_COMPOSITE = ITEMS.registerSimpleItem("arcite_tungsten_composite");

    // --- Nuclear (see the Nuclear chapter of the Handbook; all on the Nuclear creative tab) ---

    // Uraninite, Galena and lead, Heavy Mineral Sand and what the Sifter finds in it.
    public static final DeferredItem<BlockItem> URANINITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.URANINITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANINITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_URANINITE_ORE);
    public static final DeferredItem<Item> RAW_URANINITE = ITEMS.registerSimpleItem("raw_uraninite");
    public static final DeferredItem<BlockItem> RAW_URANINITE_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_URANINITE_BLOCK);
    public static final DeferredItem<Item> URANINITE_DUST = ITEMS.registerSimpleItem("uraninite_dust");
    public static final DeferredItem<BlockItem> GALENA_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.GALENA_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_GALENA_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_GALENA_ORE);
    public static final DeferredItem<Item> RAW_GALENA = ITEMS.registerSimpleItem("raw_galena");
    public static final DeferredItem<BlockItem> RAW_GALENA_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_GALENA_BLOCK);
    public static final DeferredItem<Item> LEAD_DUST = ITEMS.registerSimpleItem("lead_dust");
    public static final DeferredItem<Item> LEAD_INGOT = ITEMS.registerSimpleItem("lead_ingot");
    public static final DeferredItem<BlockItem> LEAD_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.LEAD_BLOCK);
    public static final DeferredItem<Item> LEAD_PLATE = ITEMS.registerSimpleItem("lead_plate");
    public static final DeferredItem<BlockItem> HEAVY_MINERAL_SAND = ITEMS.registerSimpleBlockItem(ModBlocks.HEAVY_MINERAL_SAND);
    public static final DeferredItem<Item> MONAZITE = ITEMS.registerSimpleItem("monazite");
    public static final DeferredItem<Item> MONAZITE_DUST = ITEMS.registerSimpleItem("monazite_dust");
    public static final DeferredItem<Item> ZIRCON = ITEMS.registerSimpleItem("zircon");
    public static final DeferredItem<Item> ZIRCON_DUST = ITEMS.registerSimpleItem("zircon_dust");

    // The uranium line: leached, precipitated, calcined and converted (see the chemistry recipes), and the fluids'
    // buckets.
    public static final DeferredItem<Item> AMMONIUM_DIURANATE = ITEMS.registerSimpleItem("ammonium_diuranate");
    public static final DeferredItem<Item> YELLOWCAKE = ITEMS.registerSimpleItem("yellowcake");
    public static final DeferredItem<Item> URANIUM_DIOXIDE = ITEMS.registerSimpleItem("uranium_dioxide");
    public static final DeferredItem<Item> URANIUM_TETRAFLUORIDE = ITEMS.registerSimpleItem("uranium_tetrafluoride");
    public static final DeferredItem<BucketItem> URANYL_SULFATE_SOLUTION_BUCKET = ITEMS.registerItem("uranyl_sulfate_solution_bucket",
            p -> new BucketItem(ModFluids.URANYL_SULFATE_SOLUTION.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BucketItem> HYDROFLUORIC_ACID_BUCKET = ITEMS.registerItem("hydrofluoric_acid_bucket",
            p -> new BucketItem(ModFluids.HYDROFLUORIC_ACID.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    // Depleted uranium, from the cascade's tails.
    public static final DeferredItem<Item> DEPLETED_URANIUM_DUST = ITEMS.registerSimpleItem("depleted_uranium_dust");
    public static final DeferredItem<Item> DEPLETED_URANIUM_INGOT = ITEMS.registerSimpleItem("depleted_uranium_ingot");
    public static final DeferredItem<BlockItem> DEPLETED_URANIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.DEPLETED_URANIUM_BLOCK);

    // Radiation protection: the meters, the Hazmat Suit (see HazmatSuit), the Lead Box and the shielding blocks.
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.GeigerCounterItem> GEIGER_COUNTER = ITEMS.registerItem("geiger_counter",
            net.zagdrath.arcforge.item.tool.GeigerCounterItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.DosimeterItem> DOSIMETER = ITEMS.registerItem("dosimeter",
            net.zagdrath.arcforge.item.tool.DosimeterItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> HAZMAT_HELMET = hazmat("hazmat_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> HAZMAT_CHESTPLATE = hazmat("hazmat_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> HAZMAT_LEGGINGS = hazmat("hazmat_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> HAZMAT_BOOTS = hazmat("hazmat_boots", ArmorType.BOOTS);
    public static final DeferredItem<net.zagdrath.arcforge.item.storage.LeadBoxItem> LEAD_BOX = ITEMS.registerItem("lead_box",
            net.zagdrath.arcforge.item.storage.LeadBoxItem::new, p -> p.stacksTo(1)
                    .component(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY));
    public static final DeferredItem<BlockItem> LEAD_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.LEAD_BRICKS);
    public static final DeferredItem<BlockItem> CONCRETE_SHIELDING = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_SHIELDING);

    // The Gas Centrifuge Cascade (a Gas Centrifuge places both its blocks).
    public static final DeferredItem<BlockItem> CENTRIFUGE_CASCADE_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.CENTRIFUGE_CASCADE_CONTROLLER);
    public static final DeferredItem<BlockItem> CENTRIFUGE_CASCADE_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.CENTRIFUGE_CASCADE_CASING);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> GAS_CENTRIFUGE = ITEMS.registerItem("gas_centrifuge",
            p -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.GAS_CENTRIFUGE.get(), p), p -> p.useBlockDescriptionPrefix());

    // Fuel fabrication: enriched UO2 (a Uranium Dioxide carrying its assay) pressed into green pellets with the Pellet
    // Die, sintered into Fuel Pellets in the Induction Furnace; Zirconium from Zircon, alloyed into Zircaloy and drawn
    // into tubes; a Fuel Rod is a Zircaloy Tube of Fuel Pellets (see FuelRod). Control Rods (silver alloy), and Heavy
    // Water.
    public static final DeferredItem<net.zagdrath.arcforge.item.tool.DieItem> PELLET_DIE = ITEMS.registerItem("pellet_die",
            net.zagdrath.arcforge.item.tool.DieItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> GREEN_FUEL_PELLET = ITEMS.registerSimpleItem("green_fuel_pellet");
    public static final DeferredItem<Item> FUEL_PELLET = ITEMS.registerSimpleItem("fuel_pellet");
    public static final DeferredItem<Item> ZIRCONIUM_TETRACHLORIDE = ITEMS.registerSimpleItem("zirconium_tetrachloride");
    public static final DeferredItem<Item> ZIRCONIUM_DUST = ITEMS.registerSimpleItem("zirconium_dust");
    public static final DeferredItem<Item> ZIRCONIUM_INGOT = ITEMS.registerSimpleItem("zirconium_ingot");
    public static final DeferredItem<Item> ZIRCALOY_INGOT = ITEMS.registerSimpleItem("zircaloy_ingot");
    public static final DeferredItem<Item> ZIRCALOY_TUBE = ITEMS.registerSimpleItem("zircaloy_tube");
    public static final DeferredItem<net.zagdrath.arcforge.item.nuclear.FuelRodItem> FUEL_ROD = ITEMS.registerItem("fuel_rod",
            net.zagdrath.arcforge.item.nuclear.FuelRodItem::new);
    public static final DeferredItem<Item> CONTROL_ROD = ITEMS.registerSimpleItem("control_rod");
    public static final DeferredItem<BucketItem> HEAVY_WATER_BUCKET = ITEMS.registerItem("heavy_water_bucket",
            p -> new BucketItem(ModFluids.HEAVY_WATER.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    // The Fission Reactor, the Steam Generator and the Heavy Water Tower.
    public static final DeferredItem<BlockItem> REACTOR_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.REACTOR_CONTROLLER);
    public static final DeferredItem<BlockItem> REACTOR_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.REACTOR_CASING);
    public static final DeferredItem<BlockItem> REACTOR_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.REACTOR_GLASS);
    public static final DeferredItem<BlockItem> FUEL_CHANNEL = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_CHANNEL);
    public static final DeferredItem<BlockItem> CONTROL_ROD_CHANNEL = ITEMS.registerSimpleBlockItem(ModBlocks.CONTROL_ROD_CHANNEL);
    public static final DeferredItem<BlockItem> GRAPHITE_MODERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.GRAPHITE_MODERATOR);
    public static final DeferredItem<BlockItem> STEEL_REFLECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_REFLECTOR);
    public static final DeferredItem<BlockItem> STEAM_GENERATOR_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_GENERATOR_CONTROLLER);
    public static final DeferredItem<BlockItem> STEAM_GENERATOR_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_GENERATOR_CASING);
    public static final DeferredItem<BlockItem> STEAM_GENERATOR_TUBES = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_GENERATOR_TUBES);
    public static final DeferredItem<BlockItem> NUCLEAR_TURBINE_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.NUCLEAR_TURBINE_CASING);
    public static final DeferredItem<BlockItem> HEAVY_WATER_TOWER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.HEAVY_WATER_TOWER_CONTROLLER);
    public static final DeferredItem<BlockItem> HEAVY_WATER_TOWER_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.HEAVY_WATER_TOWER_CASING);
    public static final DeferredItem<BlockItem> EXCHANGE_TRAY = ITEMS.registerSimpleBlockItem(ModBlocks.EXCHANGE_TRAY);

    // Phase three of the nuclear tier. Reprocessing and MOX: Nitric Acid, Recovered Uranium, Plutonium Oxide (only ever
    // made into MOX fuel), Depleted Uranium Dioxide, the MOX chain. Thorium: Thorium Dioxide (with Rare Earth Dust from
    // Monazite), Thorium Blanket Rods, U-233 Oxide bred in them and the U-233 fuel chain. The advanced cores: Lead-Bismuth
    // Ingots, Lithium Fluoride and Thorium Tetrafluoride (for Fuel Salt). Waste: HEPA Filters, Waste Drums, Vitrified
    // Waste Logs.
    public static final DeferredItem<BucketItem> NITRIC_ACID_BUCKET = ITEMS.registerItem("nitric_acid_bucket",
            p -> new BucketItem(ModFluids.NITRIC_ACID.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<Item> RECOVERED_URANIUM = ITEMS.registerSimpleItem("recovered_uranium");
    public static final DeferredItem<Item> PLUTONIUM_OXIDE = ITEMS.registerSimpleItem("plutonium_oxide");
    public static final DeferredItem<Item> DEPLETED_URANIUM_DIOXIDE = ITEMS.registerSimpleItem("depleted_uranium_dioxide");
    public static final DeferredItem<Item> MOX_BLEND = ITEMS.registerSimpleItem("mox_blend");
    public static final DeferredItem<Item> GREEN_MOX_PELLET = ITEMS.registerSimpleItem("green_mox_pellet");
    public static final DeferredItem<Item> MOX_FUEL_PELLET = ITEMS.registerSimpleItem("mox_fuel_pellet");
    public static final DeferredItem<net.zagdrath.arcforge.item.nuclear.FuelRodItem> MOX_FUEL_ROD = ITEMS.registerItem("mox_fuel_rod",
            p -> new net.zagdrath.arcforge.item.nuclear.FuelRodItem(net.zagdrath.arcforge.nuclear.FuelRod.FuelType.MOX, p));
    public static final DeferredItem<Item> THORIUM_DIOXIDE = ITEMS.registerSimpleItem("thorium_dioxide");
    public static final DeferredItem<Item> RARE_EARTH_DUST = ITEMS.registerSimpleItem("rare_earth_dust");
    public static final DeferredItem<Item> GREEN_THORIUM_PELLET = ITEMS.registerSimpleItem("green_thorium_pellet");
    public static final DeferredItem<Item> THORIUM_PELLET = ITEMS.registerSimpleItem("thorium_pellet");
    public static final DeferredItem<net.zagdrath.arcforge.item.nuclear.FuelRodItem> THORIUM_BLANKET_ROD = ITEMS.registerItem("thorium_blanket_rod",
            p -> new net.zagdrath.arcforge.item.nuclear.FuelRodItem(net.zagdrath.arcforge.nuclear.FuelRod.FuelType.THORIUM, p));
    public static final DeferredItem<Item> URANIUM_233_OXIDE = ITEMS.registerSimpleItem("uranium_233_oxide");
    public static final DeferredItem<Item> U233_FUEL_BLEND = ITEMS.registerSimpleItem("u233_fuel_blend");
    public static final DeferredItem<Item> GREEN_U233_PELLET = ITEMS.registerSimpleItem("green_u233_pellet");
    public static final DeferredItem<Item> U233_FUEL_PELLET = ITEMS.registerSimpleItem("u233_fuel_pellet");
    public static final DeferredItem<net.zagdrath.arcforge.item.nuclear.FuelRodItem> U233_FUEL_ROD = ITEMS.registerItem("u233_fuel_rod",
            p -> new net.zagdrath.arcforge.item.nuclear.FuelRodItem(net.zagdrath.arcforge.nuclear.FuelRod.FuelType.U233, p));
    public static final DeferredItem<Item> LEAD_BISMUTH_INGOT = ITEMS.registerSimpleItem("lead_bismuth_ingot");
    public static final DeferredItem<Item> LITHIUM_FLUORIDE = ITEMS.registerSimpleItem("lithium_fluoride");
    public static final DeferredItem<Item> THORIUM_TETRAFLUORIDE = ITEMS.registerSimpleItem("thorium_tetrafluoride");
    public static final DeferredItem<Item> HEPA_FILTER = ITEMS.registerSimpleItem("hepa_filter");
    public static final DeferredItem<Item> USED_HEPA_FILTER = ITEMS.registerSimpleItem("used_hepa_filter");
    public static final DeferredItem<Item> WASTE_DRUM = ITEMS.registerSimpleItem("waste_drum");
    public static final DeferredItem<net.zagdrath.arcforge.item.nuclear.WasteLogItem> VITRIFIED_WASTE_LOG = ITEMS.registerItem("vitrified_waste_log",
            net.zagdrath.arcforge.item.nuclear.WasteLogItem::new, p -> p.stacksTo(1));

    // The containment, the safety systems, spent fuel and waste storage, the waste and reprocessing machines.
    public static final DeferredItem<BlockItem> CONTAINMENT_CONCRETE = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINMENT_CONCRETE);
    public static final DeferredItem<BlockItem> STEEL_LINER = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_LINER);
    public static final DeferredItem<BlockItem> CONTAINMENT_DOME = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINMENT_DOME);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> AIRLOCK = ITEMS.registerItem("airlock",
            p -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.AIRLOCK.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> EQUIPMENT_HATCH = ITEMS.registerSimpleBlockItem(ModBlocks.EQUIPMENT_HATCH);
    public static final DeferredItem<BlockItem> PENETRATION = ITEMS.registerSimpleBlockItem(ModBlocks.PENETRATION);
    public static final DeferredItem<BlockItem> CORE_CATCHER = ITEMS.registerSimpleBlockItem(ModBlocks.CORE_CATCHER);
    public static final DeferredItem<BlockItem> HYDROGEN_RECOMBINER = ITEMS.registerSimpleBlockItem(ModBlocks.HYDROGEN_RECOMBINER);
    public static final DeferredItem<BlockItem> RELIEF_VALVE = ITEMS.registerSimpleBlockItem(ModBlocks.RELIEF_VALVE);
    public static final DeferredItem<BlockItem> ECCS_ACCUMULATOR = ITEMS.registerSimpleBlockItem(ModBlocks.ECCS_ACCUMULATOR);
    public static final DeferredItem<BlockItem> ECCS_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.ECCS_PUMP);
    public static final DeferredItem<BlockItem> SOLIDIFIED_CORIUM = ITEMS.registerSimpleBlockItem(ModBlocks.SOLIDIFIED_CORIUM);
    public static final DeferredItem<BlockItem> DECONTAMINATION_SHOWER = ITEMS.registerSimpleBlockItem(ModBlocks.DECONTAMINATION_SHOWER);
    public static final DeferredItem<BlockItem> SPENT_FUEL_RACK = ITEMS.registerSimpleBlockItem(ModBlocks.SPENT_FUEL_RACK);
    public static final DeferredItem<BlockItem> DRY_CASK = ITEMS.registerItem("dry_cask", p -> new BlockItem(ModBlocks.DRY_CASK.get(), p),
            p -> p.useBlockDescriptionPrefix().stacksTo(1));
    public static final DeferredItem<BlockItem> GEOLOGICAL_REPOSITORY = ITEMS.registerSimpleBlockItem(ModBlocks.GEOLOGICAL_REPOSITORY);
    public static final DeferredItem<BlockItem> LOW_LEVEL_WASTE_DRUM = ITEMS.registerSimpleBlockItem(ModBlocks.LOW_LEVEL_WASTE_DRUM);
    public static final DeferredItem<BlockItem> WASTE_COMPACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.WASTE_COMPACTOR);
    public static final DeferredItem<BlockItem> VITRIFICATION_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.VITRIFICATION_PLANT);
    public static final DeferredItem<BlockItem> PUREX_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.PUREX_CONTROLLER);
    public static final DeferredItem<BlockItem> HOT_CELL_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.HOT_CELL_CASING);
    public static final DeferredItem<BlockItem> HOT_CELL_WINDOW = ITEMS.registerSimpleBlockItem(ModBlocks.HOT_CELL_WINDOW);
    public static final DeferredItem<BlockItem> SALT_PROCESSING_UNIT = ITEMS.registerSimpleBlockItem(ModBlocks.SALT_PROCESSING_UNIT);
    public static final DeferredItem<BlockItem> FREEZE_PLUG = ITEMS.registerSimpleBlockItem(ModBlocks.FREEZE_PLUG);
    public static final DeferredItem<BlockItem> DRAIN_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.DRAIN_TANK);

    private static DeferredItem<Item> hazmat(String name, ArmorType type) {
        return ITEMS.registerItem(name, p -> new Item(p) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                    java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
                builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.arcforge.hazmat.piece").withStyle(net.minecraft.ChatFormatting.GRAY));
                builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.arcforge.hazmat.set").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }, p -> p.humanoidArmor(net.zagdrath.arcforge.nuclear.HazmatSuit.ARMOR, type));
    }

    // Everything on the Nuclear creative tab, in order.
    public static List<DeferredItem<? extends Item>> nuclear() {
        return List.of(URANINITE_ORE, DEEPSLATE_URANINITE_ORE, RAW_URANINITE, RAW_URANINITE_BLOCK, URANINITE_DUST,
                GALENA_ORE, DEEPSLATE_GALENA_ORE, RAW_GALENA, RAW_GALENA_BLOCK, LEAD_DUST, LEAD_INGOT, LEAD_BLOCK, LEAD_PLATE,
                HEAVY_MINERAL_SAND, MONAZITE, MONAZITE_DUST, ZIRCON, ZIRCON_DUST,
                URANYL_SULFATE_SOLUTION_BUCKET, AMMONIUM_DIURANATE, YELLOWCAKE, URANIUM_DIOXIDE, HYDROFLUORIC_ACID_BUCKET, URANIUM_TETRAFLUORIDE,
                CENTRIFUGE_CASCADE_CONTROLLER, CENTRIFUGE_CASCADE_CASING, GAS_CENTRIFUGE,
                DEPLETED_URANIUM_DUST, DEPLETED_URANIUM_INGOT, DEPLETED_URANIUM_BLOCK,
                GEIGER_COUNTER, DOSIMETER, HAZMAT_HELMET, HAZMAT_CHESTPLATE, HAZMAT_LEGGINGS, HAZMAT_BOOTS, LEAD_BOX, LEAD_BRICKS, CONCRETE_SHIELDING,
                PELLET_DIE, GREEN_FUEL_PELLET, FUEL_PELLET, ZIRCONIUM_TETRACHLORIDE, ZIRCONIUM_DUST, ZIRCONIUM_INGOT, ZIRCALOY_INGOT, ZIRCALOY_TUBE,
                FUEL_ROD, CONTROL_ROD, HEAVY_WATER_BUCKET,
                HEAVY_WATER_TOWER_CONTROLLER, HEAVY_WATER_TOWER_CASING, EXCHANGE_TRAY,
                REACTOR_CONTROLLER, REACTOR_CASING, REACTOR_GLASS, FUEL_CHANNEL, CONTROL_ROD_CHANNEL, GRAPHITE_MODERATOR, STEEL_REFLECTOR,
                STEAM_GENERATOR_CONTROLLER, STEAM_GENERATOR_CASING, STEAM_GENERATOR_TUBES, NUCLEAR_TURBINE_CASING,
                CONTAINMENT_CONCRETE, STEEL_LINER, CONTAINMENT_DOME, AIRLOCK, EQUIPMENT_HATCH, PENETRATION,
                RELIEF_VALVE, ECCS_ACCUMULATOR, ECCS_PUMP, HYDROGEN_RECOMBINER, CORE_CATCHER, SOLIDIFIED_CORIUM,
                DECONTAMINATION_SHOWER, HEPA_FILTER, USED_HEPA_FILTER,
                SPENT_FUEL_RACK, DRY_CASK,
                NITRIC_ACID_BUCKET, PUREX_CONTROLLER, HOT_CELL_CASING, HOT_CELL_WINDOW, RECOVERED_URANIUM, PLUTONIUM_OXIDE,
                DEPLETED_URANIUM_DIOXIDE, MOX_BLEND, GREEN_MOX_PELLET, MOX_FUEL_PELLET, MOX_FUEL_ROD,
                RARE_EARTH_DUST, THORIUM_DIOXIDE, GREEN_THORIUM_PELLET, THORIUM_PELLET, THORIUM_BLANKET_ROD,
                URANIUM_233_OXIDE, U233_FUEL_BLEND, GREEN_U233_PELLET, U233_FUEL_PELLET, U233_FUEL_ROD,
                VITRIFICATION_PLANT, VITRIFIED_WASTE_LOG, GEOLOGICAL_REPOSITORY, WASTE_COMPACTOR, WASTE_DRUM, LOW_LEVEL_WASTE_DRUM,
                LEAD_BISMUTH_INGOT, LITHIUM_FLUORIDE, THORIUM_TETRAFLUORIDE, SALT_PROCESSING_UNIT, FREEZE_PLUG, DRAIN_TANK);
    }

    // The ore items in creative tab order: per ore its ore, deepslate ore, raw item, raw block, dust,
    // ingot or crystal and block; then Invar's dust and ingot.
    // Sulfuric Acid and the slurries, for the Fluids tab.
    public static List<DeferredItem<BucketItem>> chemicalBuckets() {
        List<DeferredItem<BucketItem>> buckets = new ArrayList<>();
        buckets.add(SULFURIC_ACID_BUCKET);
        buckets.add(HYDROCHLORIC_ACID_BUCKET);
        buckets.add(SEAWATER_BUCKET);
        buckets.add(BRINE_BUCKET);
        buckets.add(LYE_BUCKET);
        buckets.add(LITHIUM_BRINE_BUCKET);
        buckets.add(SODIUM_SILICATE_BUCKET);
        buckets.add(TRICHLOROSILANE_BUCKET);
        for (OreSlurry slurry : OreSlurry.values()) {
            buckets.add(slurryBucket(slurry));
        }
        return buckets;
    }

    public static List<DeferredItem<? extends Item>> ores() {
        return List.of(SILVER_ORE, DEEPSLATE_SILVER_ORE, RAW_SILVER, RAW_SILVER_BLOCK, SILVER_DUST, SILVER_INGOT, SILVER_BLOCK, NICKEL_ORE, DEEPSLATE_NICKEL_ORE, RAW_NICKEL, RAW_NICKEL_BLOCK, NICKEL_DUST, NICKEL_INGOT, NICKEL_BLOCK, WOLFRAMITE_ORE, DEEPSLATE_WOLFRAMITE_ORE, RAW_WOLFRAMITE, RAW_WOLFRAMITE_BLOCK, TUNGSTEN_DUST, TUNGSTEN_INGOT, TUNGSTEN_BLOCK, FLUORITE_ORE, DEEPSLATE_FLUORITE_ORE, RAW_FLUORITE, RAW_FLUORITE_BLOCK, FLUORITE_DUST, FLUORITE_CRYSTAL, FLUORITE_BLOCK, BISMUTH_ORE, DEEPSLATE_BISMUTH_ORE, RAW_BISMUTH, RAW_BISMUTH_BLOCK, BISMUTH_DUST, BISMUTH_INGOT, BISMUTH_BLOCK, ARCITE_ORE, DEEPSLATE_ARCITE_ORE, RAW_ARCITE, RAW_ARCITE_BLOCK, ARCITE_DUST, ARCITE_CRYSTAL, ARCITE_BLOCK, NETHER_SULFUR_ORE, IOLITE_ORE, IOLITE_DUST, IOLITE_CRYSTAL, IOLITE_BLOCK, INVAR_DUST, INVAR_INGOT);
    }

    // What's made from the ores, in creative tab order (Components): each metal's plate, gear and rod, then the
    // components.
    public static List<DeferredItem<Item>> oreComponents() {
        return List.of(SILVER_PLATE, SILVER_GEAR, SILVER_ROD, NICKEL_PLATE, NICKEL_GEAR, NICKEL_ROD, TUNGSTEN_PLATE, TUNGSTEN_GEAR, TUNGSTEN_ROD, INVAR_PLATE, INVAR_GEAR, INVAR_ROD, TUNGSTEN_HEATING_COIL, THERMOCOUPLE, ARCITE_TUNGSTEN_COMPOSITE);
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
    // Fertilizers: each adds its nutrients (config farming.growing.fertilizers) to Loam Farmland.
    public static final DeferredItem<FertilizerItem> COMPOST = ITEMS.registerItem("compost",
            p -> new FertilizerItem(ArcforgeConfig.COMPOST_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> WOOD_ASH = ITEMS.registerItem("wood_ash",
            p -> new FertilizerItem(ArcforgeConfig.WOOD_ASH_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> BASIC_SLAG = ITEMS.registerItem("basic_slag",
            p -> new FertilizerItem(ArcforgeConfig.BASIC_SLAG_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<FertilizerItem> MIXED_FERTILIZER = ITEMS.registerItem("mixed_fertilizer",
            p -> new FertilizerItem(ArcforgeConfig.MIXED_FERTILIZER_NUTRIENTS::getAsInt, p));

    // Crops. Seeds that plant a crop are block items named as items ("Flax Seeds", not the crop). Compost Bins take
    // them at vanilla's chances: 30% for seeds, 65% for the harvest.
    public static final DeferredItem<BlockItem> FLAX_SEEDS = ITEMS.registerItem("flax_seeds",
            p -> new BlockItem(ModBlocks.FLAX.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<Item> FLAX_FIBRE = ITEMS.registerSimpleItem("flax_fibre", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> LINEN = ITEMS.registerSimpleItem("linen");
    public static final DeferredItem<BlockItem> RAPESEEDS = ITEMS.registerItem("rapeseeds",
            p -> new BlockItem(ModBlocks.RAPESEED.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<BlockItem> SORGHUM_SEEDS = ITEMS.registerItem("sorghum_seeds",
            p -> new BlockItem(ModBlocks.SORGHUM.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    // Soybeans are their own seed, like vanilla carrots: the item plants the crop and is named as an item.
    public static final DeferredItem<BlockItem> SOYBEANS = ITEMS.registerItem("soybeans",
            p -> new BlockItem(ModBlocks.SOYBEANS.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)));
    // Rubber Dandelion: seeds plant it; its roots press into Latex.
    public static final DeferredItem<BlockItem> RUBBER_DANDELION_SEEDS = ITEMS.registerItem("rubber_dandelion_seeds",
            p -> new BlockItem(ModBlocks.RUBBER_DANDELION.get(), p.useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
    public static final DeferredItem<Item> RUBBER_DANDELION_ROOTS = ITEMS.registerSimpleItem("rubber_dandelion_roots", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    // Pine Resin, from a Resin Tap on a spruce (or slowly, any) log: the Infuser takes it in place of Creosote.
    public static final DeferredItem<Item> PINE_RESIN = ITEMS.registerSimpleItem("pine_resin");
    public static final DeferredItem<Item> SORGHUM_STALKS = ITEMS.registerSimpleItem("sorghum_stalks", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    // Hop Seeds are planted by using them on a Trellis (TrellisBlock), so they're a plain item.
    public static final DeferredItem<Item> HOP_SEEDS = ITEMS.registerSimpleItem("hop_seeds", p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
    public static final DeferredItem<Item> HOP_CONES = ITEMS.registerSimpleItem("hop_cones", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> TRELLIS = ITEMS.registerSimpleBlockItem(ModBlocks.TRELLIS);
    public static final DeferredItem<BlockItem> WILD_FLAX = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_FLAX, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_RAPESEED = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_RAPESEED, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_SORGHUM = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_SORGHUM, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_HOPS = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_HOPS, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_SOYBEANS = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_SOYBEANS, p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<BlockItem> WILD_RUBBER_DANDELION = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_RUBBER_DANDELION,
            p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

    // Rustic farming machines and tools. Durability is fixed here (it's set before the config loads); the areas are
    // config.
    public static final DeferredItem<BlockItem> PLANTER = ITEMS.registerSimpleBlockItem(ModBlocks.PLANTER);
    public static final DeferredItem<BlockItem> HARVESTER = ITEMS.registerSimpleBlockItem(ModBlocks.HARVESTER);
    public static final DeferredItem<BlockItem> FERTILIZER_SPREADER = ITEMS.registerSimpleBlockItem(ModBlocks.FERTILIZER_SPREADER);
    public static final DeferredItem<BlockItem> COPPER_SPRINKLER = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_SPRINKLER);
    public static final DeferredItem<BlockItem> RESIN_TAP = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_TAP);
    public static final DeferredItem<DoubleHighBlockItem> SCARECROW = ITEMS.registerItem("scarecrow",
            p -> new DoubleHighBlockItem(ModBlocks.SCARECROW.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<SickleItem> IRON_SICKLE = ITEMS.registerItem("iron_sickle",
            p -> new SickleItem(ArcforgeConfig.SICKLE_RADIUS::getAsInt, p), p -> p.durability(250).repairable(ItemTags.IRON_TOOL_MATERIALS).enchantable(14));
    public static final DeferredItem<SickleItem> STEEL_SICKLE = ITEMS.registerItem("steel_sickle",
            p -> new SickleItem(ArcforgeConfig.SICKLE_RADIUS::getAsInt, p), p -> p.durability(500).repairable(ModItemTags.STEEL_INGOTS).enchantable(12));
    public static final DeferredItem<SickleItem> STEEL_SCYTHE = ITEMS.registerItem("steel_scythe",
            p -> new SickleItem(ArcforgeConfig.SCYTHE_RADIUS::getAsInt, p), p -> p.durability(1_000).repairable(ModItemTags.STEEL_INGOTS).enchantable(12));

    // Farm processing: the machines and what they make. Flour smelts into bread; Seed Meal is a light fertilizer; Press
    // Cake, the dried crops and Dried Hops go in a Compost Bin like the crops they came from.
    public static final DeferredItem<BlockItem> MILLSTONE = ITEMS.registerSimpleBlockItem(ModBlocks.MILLSTONE);
    public static final DeferredItem<BlockItem> MILL = ITEMS.registerSimpleBlockItem(ModBlocks.MILL);
    public static final DeferredItem<BlockItem> OIL_PRESS = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_PRESS);
    public static final DeferredItem<BlockItem> SEED_EXTRACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.SEED_EXTRACTOR);
    public static final DeferredItem<BlockItem> GRAIN_DRYER = ITEMS.registerSimpleBlockItem(ModBlocks.GRAIN_DRYER);
    public static final DeferredItem<BlockItem> VULCANIZER = ITEMS.registerSimpleBlockItem(ModBlocks.VULCANIZER);
    public static final DeferredItem<BlockItem> HYDROTHERMAL_CARBONIZER = ITEMS.registerSimpleBlockItem(ModBlocks.HYDROTHERMAL_CARBONIZER);
    public static final DeferredItem<Item> FLOUR = ITEMS.registerSimpleItem("flour");
    public static final DeferredItem<FertilizerItem> SEED_MEAL = ITEMS.registerItem("seed_meal",
            p -> new FertilizerItem(ArcforgeConfig.SEED_MEAL_NUTRIENTS::getAsInt, p));
    public static final DeferredItem<Item> PRESS_CAKE = ITEMS.registerSimpleItem("press_cake", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> DRIED_HOPS = ITEMS.registerSimpleItem("dried_hops", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> DRIED_GRAIN = ITEMS.registerSimpleItem("dried_grain", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> DRIED_SORGHUM = ITEMS.registerSimpleItem("dried_sorghum", p -> p.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

    // Farm chemistry: the machines, and the fertilizers they lead to. NPK Fertilizer fills Loam Farmland and enriches
    // it (crops on it grow faster than on any other fertilizer); Digestate, the Biogas Digester's by-product, is a
    // middling one.
    public static final DeferredItem<BlockItem> AIR_SEPARATOR = ITEMS.registerSimpleBlockItem(ModBlocks.AIR_SEPARATOR);
    public static final DeferredItem<BlockItem> HABER_REACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.HABER_REACTOR);
    public static final DeferredItem<BlockItem> BIOGAS_DIGESTER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.BIOGAS_DIGESTER_CONTROLLER);
    public static final DeferredItem<BlockItem> DIGESTER_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.DIGESTER_CASING);
    public static final DeferredItem<FertilizerItem> NPK_FERTILIZER = ITEMS.registerItem("npk_fertilizer",
            p -> new FertilizerItem(ArcforgeConfig.NPK_NUTRIENTS::getAsInt, true, p));
    public static final DeferredItem<FertilizerItem> DIGESTATE = ITEMS.registerItem("digestate",
            p -> new FertilizerItem(ArcforgeConfig.DIGESTATE_NUTRIENTS::getAsInt, p));

    // Automated farms.
    public static final DeferredItem<BlockItem> GLASS_CLOCHE = ITEMS.registerSimpleBlockItem(ModBlocks.GLASS_CLOCHE);
    public static final DeferredItem<BlockItem> GROW_CHAMBER = ITEMS.registerSimpleBlockItem(ModBlocks.GROW_CHAMBER);
    public static final DeferredItem<BlockItem> HYDROPONIC_CELL = ITEMS.registerSimpleBlockItem(ModBlocks.HYDROPONIC_CELL);

    // The Greenhouse Array.
    public static final DeferredItem<BlockItem> GREENHOUSE_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.GREENHOUSE_CONTROLLER);
    public static final DeferredItem<BlockItem> GREENHOUSE_FRAME = ITEMS.registerSimpleBlockItem(ModBlocks.GREENHOUSE_FRAME);
    public static final DeferredItem<BlockItem> PLANTING_BED = ITEMS.registerSimpleBlockItem(ModBlocks.PLANTING_BED);
    public static final DeferredItem<BlockItem> GROW_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.GROW_LAMP);

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

    // The creative storage blocks' items, named in the creative pink.
    public static final DeferredItem<StorageBlockItem> CREATIVE_FLUID_TANK = ITEMS.registerItem("creative_fluid_tank",
            p -> new StorageBlockItem(ModBlocks.CREATIVE_FLUID_TANK.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));
    public static final DeferredItem<StorageBlockItem> CREATIVE_PRESSURIZED_CYLINDER = ITEMS.registerItem("creative_pressurized_cylinder",
            p -> new StorageBlockItem(ModBlocks.CREATIVE_PRESSURIZED_CYLINDER.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));
    public static final DeferredItem<StorageBlockItem> CREATIVE_ENERGY_CELL = ITEMS.registerItem("creative_energy_cell",
            p -> new StorageBlockItem(ModBlocks.CREATIVE_ENERGY_CELL.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));
    public static final DeferredItem<VaultBlockItem> CREATIVE_VAULT = ITEMS.registerItem("creative_vault",
            p -> new VaultBlockItem(ModBlocks.CREATIVE_VAULT.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));

    // Lithium Cells then Power Regulators, by tier.
    private static final List<DeferredItem<BatteryPartItem>> BATTERY_PARTS = new ArrayList<>();

    static {
        for (ConduitTier tier : ConduitTier.values()) {
            BATTERY_PARTS.add(ITEMS.registerItem(tier.getSerializedName() + "_lithium_cell",
                    p -> new BatteryPartItem(ModBlocks.lithiumCell(tier).get(), p), p -> p.useBlockDescriptionPrefix()));
        }
        for (ConduitTier tier : ConduitTier.values()) {
            BATTERY_PARTS.add(ITEMS.registerItem(tier.getSerializedName() + "_power_regulator",
                    p -> new BatteryPartItem(ModBlocks.powerRegulator(tier).get(), p), p -> p.useBlockDescriptionPrefix()));
        }
    }

    public static List<DeferredItem<BatteryPartItem>> batteryParts() {
        return BATTERY_PARTS;
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
        // Removed with the Evaporation Pond.
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_liner"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator_casing"));
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_outlet"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator_controller"));
        // The Reservoir became the Fluid Reservoir in 2.2.
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "reservoir"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "fluid_reservoir"));
        // Conduit Covers became Conduit Facades (one panel a side); saved Cover items load as blank facades.
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_cover"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_facade"));
        ITEMS.register(modEventBus);
    }
}
