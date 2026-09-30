/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.TierUpgradeRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;

// Tier alloys, the Arcforge Furnace's additive slot, carbon dust in the Carbonizer, the recipe rework and
// portable storage.
final class AlloyGameTests {
    private AlloyGameTests() {}

    private static PortableStorageItem portable(PortableStorageItem.Kind kind, ConduitTier tier) {
        return ModItems.portable(kind, tier);
    }

    // Carbon dust bakes into coal coke with no creosote; coal still gives creosote.
    static void carbonDustCarbonizes(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack dust = new ItemStack(ModItems.CARBON_DUST.get());
        helper.assertTrue(MachineRecipes.isCarbonizerInput(level, dust), "The Carbonizer doesn't take carbon dust");
        CarbonizingRecipe fromDust = MachineRecipes.carbonizing(level, dust).map(RecipeHolder::value).orElse(null);
        helper.assertTrue(fromDust != null && fromDust.result().create().is(ModItems.COAL_COKE.get()), "Carbon dust doesn't make coal coke");
        helper.assertTrue(fromDust.byproduct().isEmpty(), "Carbon dust makes creosote");
        CarbonizingRecipe fromCoal = MachineRecipes.carbonizing(level, new ItemStack(Items.COAL)).map(RecipeHolder::value).orElse(null);
        helper.assertTrue(fromCoal != null && fromCoal.byproduct().map(fluid -> fluid.amount() == 250).orElse(false), "Coal no longer makes 250 mB of creosote");
        helper.succeed();
    }

    private static ArcforgeSmeltingRecipe smelt(GameTestHelper helper, ItemStack metal, ItemStack additive) {
        return smelt(helper, metal, additive, ItemStack.EMPTY);
    }

    static ArcforgeSmeltingRecipe smelt(GameTestHelper helper, ItemStack metal, ItemStack additive, ItemStack additive2) {
        return MachineRecipes.arcforgeSmelting(helper.getLevel(), metal, additive, additive2).map(RecipeHolder::value).orElse(null);
    }

    // Iron alone makes steel; iron with gold makes Wrought Alloy and never steel; each alloy needs its metal,
    // additive, coke and heat.
    static void furnaceRecipes(GameTestHelper helper) {
        ArcforgeSmeltingRecipe steel = smelt(helper, new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY);
        helper.assertTrue(steel != null && steel.result().create().is(ModItems.STEEL_INGOT.get()) && steel.minHeat() == 1_200, "Iron doesn't make steel");
        helper.assertTrue(smelt(helper, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)) == null,
                "One iron and gold made something (steel needs an empty additive slot, the alloy two iron)");

        ArcforgeSmeltingRecipe wrought = smelt(helper, new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(wrought != null && wrought.result().create().is(ModItems.WROUGHT_ALLOY.get()) && wrought.result().count() == 2,
                "Two iron and gold don't make two Wrought Alloy");
        ArcforgeSmeltingRecipe tempered = smelt(helper, new ItemStack(ModItems.STEEL_INGOT.get(), 2), new ItemStack(Items.COPPER_INGOT, 2));
        helper.assertTrue(tempered != null && tempered.result().create().is(ModItems.TEMPERED_ALLOY.get()) && tempered.minHeat() == 1_300,
                "Steel and copper don't make Tempered Alloy at 1,300°C");
        // Arcforged Alloy takes a second additive, the Arcite-Tungsten Composite; without it nothing matches.
        ArcforgeSmeltingRecipe arcforged = smelt(helper, new ItemStack(ModItems.HARDENED_ALLOY.get(), 2), new ItemStack(ModItems.CARBON_FIBER.get(), 2),
                new ItemStack(ModItems.ARCITE_TUNGSTEN_COMPOSITE.get()));
        helper.assertTrue(arcforged != null && arcforged.result().create().is(ModItems.ARCFORGED_ALLOY.get()) && arcforged.coke() == 2 && arcforged.minHeat() == 1_500,
                "Hardened Alloy, Carbon Fiber and the composite don't make Arcforged Alloy");
        helper.assertTrue(smelt(helper, new ItemStack(ModItems.HARDENED_ALLOY.get(), 2), new ItemStack(ModItems.CARBON_FIBER.get(), 2)) == null,
                "Arcforged Alloy still forms without the composite");
        helper.assertTrue(smelt(helper, new ItemStack(ModItems.HARDENED_ALLOY.get(), 2), new ItemStack(ModItems.ANCIENT_DEBRIS_DUST.get())) == null,
                "Ancient debris dust still makes Arcforged Alloy");

        helper.assertTrue(ArcforgeFurnaceBlockEntity.isItemValid(helper.getLevel(), ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE, ItemResource.of(Items.AMETHYST_SHARD)),
                "The additive slot doesn't take amethyst");
        helper.assertTrue(!ArcforgeFurnaceBlockEntity.isItemValid(helper.getLevel(), ArcforgeFurnaceBlockEntity.SLOT_METAL, ItemResource.of(Items.GOLD_INGOT)),
                "The metal slot takes gold");
        helper.succeed();
    }

    // A furnace saved before the additive slot keeps its iron, coke, steel and slag.
    static void furnaceOldSaveMigrates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ARCFORGE_FURNACE_PORT.get().defaultBlockState().setValue(ArcforgeFurnacePortBlock.FACING, Direction.NORTH));
        ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(pos, ArcforgeFurnaceBlockEntity.class);
        var registries = helper.getLevel().registryAccess();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        ItemStacksResourceHandler old = new ItemStacksResourceHandler(4);
        old.set(0, ItemResource.of(Items.IRON_INGOT), 5);
        old.set(1, ItemResource.of(ModItems.COAL_COKE.get()), 3);
        old.set(2, ItemResource.of(ModItems.STEEL_INGOT.get()), 2);
        old.set(3, ItemResource.of(ModItems.SLAG.get()), 1);
        old.serialize(output.child("items"));
        CompoundTag tag = output.buildResult();
        furnace.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

        var items = furnace.getItems();
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_METAL).is(Items.IRON_INGOT) && items.getStack(ArcforgeFurnaceBlockEntity.SLOT_METAL).getCount() == 5, "Iron lost");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE).isEmpty(), "Something in the additive slot");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE).is(ModItems.COAL_COKE.get()) && items.getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE).getCount() == 3, "Coke lost");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_OUTPUT).is(ModItems.STEEL_INGOT.get()), "Steel lost");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_BYPRODUCT).is(ModItems.SLAG.get()), "Slag lost");

        // And a new save loads back as it was.
        CompoundTag saved = furnace.saveCustomOnly(registries);
        furnace.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(furnace.getItems().getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE).getCount() == 3, "New layout doesn't reload");
        helper.succeed();
    }

    // The reworked recipes are all there, under crafting/ and arcforge_smelting/, and the old ones are gone.
    static void recipesLoaded(GameTestHelper helper) {
        int crafting = 0, smelting = 0;
        for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
            Identifier id = holder.id().identifier();
            if (id.getNamespace().equals(Arcforge.MODID)) {
                crafting += id.getPath().startsWith("crafting/") ? 1 : 0;
                smelting += id.getPath().startsWith("arcforge_smelting/") ? 1 : 0;
            }
        }
        // 72 from the alloy drop, the distillation drop's casing, tray, controller and asphalt, slab and stairs,
        // the Solar Thermal Array's casing, controller, collector, trough mirror and receiver tube, and the ores
        // drop's 29 (storage and raw blocks both ways, invar dust, the components and the upgrade), the Conduit
        // Filter, the Crates and Vaults drop's 11 (two blocks, their six tier upgrades and three Storage Upgrades),
        // the Arc Melter, the Chemical Reactor, the Electrolyzer, the four automation blocks, the Arc Quarry
        // with its two parts, the 11 steel tools and armour pieces, the jetpack and arc tool drop's 16 (three
        // tier-one crafts, six tier upgrades and seven modules), and the security, foundry and gas turbine drop's 11
        // (Settings Card, Security Terminal, Fermenter, four Foundry Suit pieces, conduit dyeing, Turbine Blade Set,
        // Combustor and Gas Turbine Array Casing), the Throttle Lever, the Wrench, Conduit Cover, four Meters and
        // Chargepad, and farming's four (Compost Bin, Loam, Irrigated Loam Farmland and Mixed Fertilizer).
        helper.assertTrue(crafting == 184, crafting + " crafting/ recipes, not 184");
        // Plus the fluorite-fluxed steel.
        helper.assertTrue(smelting == 6, smelting + " arcforge_smelting/ recipes, not 6");
        for (String old : List.of("speed_upgrade", "wrought_heat_cell", "steel_ingot_from_arcforge_smelting", "thermoelectric_plant")) {
            helper.assertTrue(recipe(helper, old) == null, "Old recipe " + old + " is still there");
        }
        helper.assertTrue(recipe(helper, "coal_coke_from_carbonizing") != null, "Coal carbonizing recipe is gone");
        helper.succeed();
    }

    private static RecipeHolder<?> recipe(GameTestHelper helper, String path) {
        return helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, path)));
    }

    // Crafts a 3x3 recipe from rows of keys.
    static ItemStack craft(GameTestHelper helper, String recipe, String[] rows, java.util.Map<Character, ItemStack> key) {
        List<ItemStack> grid = new ArrayList<>();
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                grid.add(c == ' ' ? ItemStack.EMPTY : key.get(c).copy());
            }
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        RecipeHolder<?> holder = recipe(helper, recipe);
        helper.assertTrue(holder != null && holder.value() instanceof TierUpgradeRecipe, recipe + " isn't a tier upgrade");
        CraftingRecipe crafting = (CraftingRecipe) holder.value();
        helper.assertTrue(crafting.matches(input, helper.getLevel()), recipe + " doesn't match its pattern");
        return crafting.assemble(input);
    }

    // Upgrading a charged Energy Cell or Battery keeps the charge.
    static void tierUpgradeKeepsContents(GameTestHelper helper) {
        ItemStack cell = new ItemStack(ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        cell.set(ModDataComponents.ENERGY.get(), 123_456);
        ItemStack upgraded = craft(helper, "crafting/tempered_energy_cell", new String[] { "TpT", "pXp", "TpT" }, java.util.Map.of(
                'T', new ItemStack(ModItems.TEMPERED_ALLOY.get()), 'p', new ItemStack(ModItems.COPPER_PLATE.get()), 'X', cell));
        helper.assertTrue(upgraded.is(ModBlocks.energyCell(ConduitTier.TEMPERED).get().asItem()), "Didn't make a Tempered Energy Cell");
        helper.assertTrue(upgraded.getOrDefault(ModDataComponents.ENERGY.get(), 0) == 123_456, "Charge lost: " + upgraded.getOrDefault(ModDataComponents.ENERGY.get(), 0));

        ItemStack battery = new ItemStack(portable(PortableStorageItem.Kind.BATTERY, ConduitTier.WROUGHT));
        battery.set(ModDataComponents.ENERGY.get(), 5_000);
        ItemStack better = craft(helper, "crafting/tempered_battery", new String[] { " S ", "TXT", " T " }, java.util.Map.of(
                'S', new ItemStack(ModItems.SILVER_PLATE.get()), 'T', new ItemStack(ModItems.TEMPERED_ALLOY.get()), 'X', battery));
        helper.assertTrue(better.is(portable(PortableStorageItem.Kind.BATTERY, ConduitTier.TEMPERED)) && better.getOrDefault(ModDataComponents.ENERGY.get(), 0) == 5_000,
                "Battery upgrade lost its charge");
        helper.succeed();
    }

    private static int insertInto(ItemStack stack, FluidResource fluid, int amount) {
        ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
        scratch.set(0, ItemResource.of(stack), 1);
        ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndexStrict(scratch, 0).getCapability(Capabilities.Fluid.ITEM);
        try (Transaction tx = Transaction.openRoot()) {
            return handler == null ? -1 : handler.insert(fluid, amount, tx);
        }
    }

    // Canisters take liquids only and Gas Cartridges gases only, at most their rate at a time.
    static void portableFluidRules(GameTestHelper helper) {
        FluidResource water = FluidResource.of(Fluids.WATER);
        FluidResource steam = FluidResource.of(SteamGrade.STEAM.fluid());
        ItemStack canister = new ItemStack(portable(PortableStorageItem.Kind.CANISTER, ConduitTier.WROUGHT));
        ItemStack cartridge = new ItemStack(portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.WROUGHT));
        helper.assertTrue(insertInto(canister, steam, 1_000) == 0, "A canister took steam");
        helper.assertTrue(insertInto(canister, water, 5_000) == ConduitTier.WROUGHT.canisterRate(), "A canister took more or less than its rate");
        helper.assertTrue(insertInto(cartridge, water, 1_000) == 0, "A gas cartridge took water");
        helper.assertTrue(insertInto(cartridge, steam, 1_000) == 1_000, "A gas cartridge didn't take steam");
        helper.succeed();
    }

    // A Battery in the charge slot fills from the cell; one in the drain slot empties into it.
    static void batteryInEnergyCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            cell.getEnergyHandler(null).insert(100_000, tx);
            tx.commit();
        }
        Item battery = portable(PortableStorageItem.Kind.BATTERY, ConduitTier.WROUGHT);
        ItemStack full = new ItemStack(battery);
        full.set(ModDataComponents.ENERGY.get(), 50_000);
        cell.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(battery));
        cell.getItems().setStack(StorageBlockEntity.SLOT_IN, full);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    int charged = cell.getItems().getStack(StorageBlockEntity.SLOT_OUT).getOrDefault(ModDataComponents.ENERGY.get(), 0);
                    int drained = cell.getItems().getStack(StorageBlockEntity.SLOT_IN).getOrDefault(ModDataComponents.ENERGY.get(), 0);
                    helper.assertTrue(charged > 0, "The battery in the charge slot didn't charge");
                    helper.assertTrue(drained < 50_000, "The battery in the drain slot didn't drain");
                })
                .thenSucceed();
    }

    // A Canister in the bottom slot fills from the tank at its rate and stays put.
    static void canisterInTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            tank.getInteractionFluidHandler().insert(FluidResource.of(Fluids.WATER), 10_000, tx);
            tx.commit();
        }
        PortableStorageItem canister = portable(PortableStorageItem.Kind.CANISTER, ConduitTier.WROUGHT);
        tank.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(canister));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ItemStack filled = tank.getItems().getStack(StorageBlockEntity.SLOT_OUT);
                    helper.assertTrue(filled.is(canister) && canister.amount(filled) >= 2_000, "Canister has " + canister.amount(filled) + " mB");
                    helper.assertTrue(tank.getFluid().getAmount() == 10_000 - canister.amount(filled), "Fluid appeared or vanished");
                })
                .thenSucceed();
    }

    // A Gas Cartridge in the bottom slot fills with the cylinder's gas.
    static void cartridgeInCylinder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(pos, PressurizedCylinderBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            cylinder.getTank().insert(0, FluidResource.of(SteamGrade.STEAM.fluid()), 20_000, tx);
            tx.commit();
        }
        PortableStorageItem cartridge = portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.WROUGHT);
        cylinder.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(cartridge));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ItemStack filled = cylinder.getItems().getStack(StorageBlockEntity.SLOT_OUT);
                    helper.assertTrue(cartridge.amount(filled) > 0 && PortableStorageItem.fluid(filled).getFluid() == SteamGrade.STEAM.fluid(),
                            "Cartridge didn't fill with steam");
                })
                .thenSucceed();
    }

    // A hot capsule warms a cold cell; a cold capsule in the drain slot takes nothing from a hot one; a cold
    // capsule in the fill slot is warmed by it.
    static void capsuleHeatFlowsHotToCold(GameTestHelper helper) {
        BlockPos coldPos = new BlockPos(1, 1, 1);
        BlockPos hotPos = new BlockPos(3, 1, 1);
        helper.setBlock(coldPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        helper.setBlock(hotPos, ModBlocks.heatCell(ConduitTier.WROUGHT).get());
        HeatCellBlockEntity cold = helper.getBlockEntity(coldPos, HeatCellBlockEntity.class);
        HeatCellBlockEntity hot = helper.getBlockEntity(hotPos, HeatCellBlockEntity.class);
        hot.getHeat().add(40_000);

        PortableStorageItem capsule = portable(PortableStorageItem.Kind.THERMAL_CAPSULE, ConduitTier.WROUGHT);
        cold.getItems().setStack(StorageBlockEntity.SLOT_IN, capsule.filled(null));
        hot.getItems().setStack(StorageBlockEntity.SLOT_IN, new ItemStack(capsule));
        hot.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(capsule));
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(cold.getHeat().getStored() > 0, "A full capsule didn't warm a cold cell");
                    helper.assertTrue(capsule.amount(cold.getItems().getStack(StorageBlockEntity.SLOT_IN)) < capsule.capacity(), "The hot capsule didn't give heat");
                    helper.assertTrue(capsule.amount(hot.getItems().getStack(StorageBlockEntity.SLOT_IN)) == 0, "A cold capsule in the drain slot took heat");
                    ItemStack filling = hot.getItems().getStack(StorageBlockEntity.SLOT_OUT);
                    helper.assertTrue(capsule.amount(filling) > 0, "A cold capsule in the fill slot wasn't warmed");
                    helper.assertTrue(capsule.temperature(filling) <= hot.getHeat().getTemperature(), "The capsule got hotter than the cell");
                })
                .thenSucceed();
    }
}
