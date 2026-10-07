/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.worldgen.ModWorldgen;

// The ores drop: crushing, smelting and storage, Invar, the Arcforge Furnace's second additive slot and its
// save migration, the Thermoelectric Efficiency Upgrade, arcite's pickaxe tier, the tags, and the ore toggles.
public final class OreGameTests {
    private OreGameTests() {}

    // --- Recipe helpers ---

    private static ItemStack cook(GameTestHelper helper, RecipeType<? extends net.minecraft.world.item.crafting.AbstractCookingRecipe> type, Item input) {
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(new ItemStack(input)), level)
                .map(holder -> holder.value().assemble(new SingleRecipeInput(new ItemStack(input)))).orElse(ItemStack.EMPTY);
    }

    // What a 3x3 grid (rows of up to 3, null for empty) crafts, or EMPTY.
    private static ItemStack craft(GameTestHelper helper, Item[][] rows) {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length; x++) {
                if (rows[y][x] != null) {
                    grid.set(y * 3 + x, new ItemStack(rows[y][x]));
                }
            }
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    private static Item[][] nine(Item item) {
        return new Item[][] { { item, item, item }, { item, item, item }, { item, item, item } };
    }

    private static void check(GameTestHelper helper, ItemStack made, Item item, int count, String what) {
        helper.assertTrue(made.is(item) && made.getCount() == count, what + " gave " + made + ", expected " + count + " " + item);
    }

    // --- Tests ---

    // 1. An Arc Crusher makes 2 silver dust from silver ore; an Arc Crushing Array makes 4.
    static void crushingDoubles(GameTestHelper helper) {
        BlockPos crusherPos = new BlockPos(0, 1, 0);
        helper.setBlock(crusherPos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(crusherPos, ArcCrusherBlockEntity.class);
        FiberGameTests.charge(crusher.getEnergy(), 20_000);
        crusher.getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(ModItems.SILVER_ORE.get()));
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(3, 1, 0), new BlockPos(5, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        BlockPos centre = new BlockPos(4, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    FiberGameTests.charge(array.getEnergy(), 100_000);
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(0), new ItemStack(ModItems.SILVER_ORE.get()));
                })
                .thenIdle(205)
                .thenExecute(() -> {
                    ItemStack single = crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                    check(helper, single, ModItems.SILVER_DUST.get(), 2, "Arc Crusher on silver ore");
                    ItemStack doubled = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class).getItems()
                            .getStack(ArcCrushingArrayBlockEntity.outputSlot(0));
                    check(helper, doubled, ModItems.SILVER_DUST.get(), 4, "Arc Crushing Array on silver ore");
                })
                .thenSucceed();
    }

    // 2. Raw nickel and nickel dust smelt into nickel ingots: in an Induction Furnace, and by furnace and blast furnace recipes.
    static void rawToIngot(GameTestHelper helper) {
        for (Item input : List.of(ModItems.RAW_NICKEL.get(), ModItems.NICKEL_DUST.get(), ModItems.NICKEL_ORE.get())) {
            check(helper, cook(helper, RecipeType.SMELTING, input), ModItems.NICKEL_INGOT.get(), 1, "Smelting " + input);
            check(helper, cook(helper, RecipeType.BLASTING, input), ModItems.NICKEL_INGOT.get(), 1, "Blasting " + input);
        }
        BlockPos[] pos = { new BlockPos(0, 1, 0), new BlockPos(2, 1, 0) };
        Item[] inputs = { ModItems.RAW_NICKEL.get(), ModItems.NICKEL_DUST.get() };
        for (int i = 0; i < 2; i++) {
            helper.setBlock(pos[i], ModBlocks.INDUCTION_FURNACE.get());
            InductionFurnaceBlockEntity furnace = helper.getBlockEntity(pos[i], InductionFurnaceBlockEntity.class);
            FiberGameTests.charge(furnace.getEnergy(), 20_000);
            furnace.getItems().setStack(InductionFurnaceBlockEntity.SLOT_INPUT, new ItemStack(inputs[i]));
        }
        helper.startSequence()
                .thenIdle(110)
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) {
                        ItemStack out = helper.getBlockEntity(pos[i], InductionFurnaceBlockEntity.class).getItems().getStack(InductionFurnaceBlockEntity.SLOT_OUTPUT);
                        check(helper, out, ModItems.NICKEL_INGOT.get(), 1, "Induction Furnace on " + inputs[i]);
                    }
                })
                .thenSucceed();
    }

    // 3. Fluorite and arcite dust (and raw and ore) smelt into crystals.
    static void crystals(GameTestHelper helper) {
        check(helper, cook(helper, RecipeType.SMELTING, ModItems.FLUORITE_DUST.get()), ModItems.FLUORITE_CRYSTAL.get(), 1, "Fluorite dust");
        check(helper, cook(helper, RecipeType.SMELTING, ModItems.RAW_FLUORITE.get()), ModItems.FLUORITE_CRYSTAL.get(), 1, "Raw fluorite");
        check(helper, cook(helper, RecipeType.SMELTING, ModItems.ARCITE_DUST.get()), ModItems.ARCITE_CRYSTAL.get(), 1, "Arcite dust");
        check(helper, cook(helper, RecipeType.BLASTING, ModItems.ARCITE_ORE.get()), ModItems.ARCITE_CRYSTAL.get(), 1, "Arcite ore");
        helper.succeed();
    }

    // 4. Nine ingots, crystals or raw items make a block, and the block gives nine back.
    static void storageRoundTrip(GameTestHelper helper) {
        Item[][] pairs = {
                { ModItems.SILVER_INGOT.get(), ModItems.SILVER_BLOCK.get() }, { ModItems.NICKEL_INGOT.get(), ModItems.NICKEL_BLOCK.get() },
                { ModItems.TUNGSTEN_INGOT.get(), ModItems.TUNGSTEN_BLOCK.get() }, { ModItems.BISMUTH_INGOT.get(), ModItems.BISMUTH_BLOCK.get() },
                { ModItems.FLUORITE_CRYSTAL.get(), ModItems.FLUORITE_BLOCK.get() }, { ModItems.ARCITE_CRYSTAL.get(), ModItems.ARCITE_BLOCK.get() },
                { ModItems.RAW_SILVER.get(), ModItems.RAW_SILVER_BLOCK.get() }, { ModItems.RAW_WOLFRAMITE.get(), ModItems.RAW_WOLFRAMITE_BLOCK.get() },
                { ModItems.RAW_ARCITE.get(), ModItems.RAW_ARCITE_BLOCK.get() } };
        for (Item[] pair : pairs) {
            check(helper, craft(helper, nine(pair[0])), pair[1], 1, "Nine " + pair[0]);
            check(helper, craft(helper, new Item[][] { { pair[1] } }), pair[0], 9, "A " + pair[1]);
        }
        helper.succeed();
    }

    // 5. 2 iron dust + nickel dust make 3 invar dust, which smelts into invar ingots, which press into plates.
    static void invarMix(GameTestHelper helper) {
        check(helper, craft(helper, new Item[][] { { ModItems.IRON_DUST.get(), ModItems.IRON_DUST.get(), ModItems.NICKEL_DUST.get() } }),
                ModItems.INVAR_DUST.get(), 3, "Iron and nickel dust");
        check(helper, cook(helper, RecipeType.SMELTING, ModItems.INVAR_DUST.get()), ModItems.INVAR_INGOT.get(), 1, "Invar dust");
        ItemStack plate = MachineRecipes.pressing(helper.getLevel(), new ItemStack(ModItems.PLATE_DIE.get()), new ItemStack(ModItems.INVAR_INGOT.get()))
                .map(holder -> holder.value().result().create()).orElse(ItemStack.EMPTY);
        helper.assertTrue(plate.is(ModItems.INVAR_PLATE.get()), "An invar ingot presses into " + plate);
        helper.succeed();
    }

    // 6. Fluorite fluxes steel: 4 iron + fluorite + 4 coke -> 4 steel + 6 slag in 1,000 ticks. Without the
    //    fluorite the plain steel recipe runs.
    static void fluoriteFlux(GameTestHelper helper) {
        ArcforgeSmeltingRecipe flux = AlloyGameTests.smelt(helper, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(ModItems.FLUORITE_CRYSTAL.get()), ItemStack.EMPTY);
        helper.assertTrue(flux != null && flux.result().create().is(ModItems.STEEL_INGOT.get()) && flux.result().create().getCount() == 4 && flux.coke() == 4
                && flux.time() == 1_000 && flux.byproduct().map(slag -> slag.create().getCount() == 6).orElse(false), "Fluorite doesn't flux steel as set");
        ArcforgeSmeltingRecipe plain = AlloyGameTests.smelt(helper, new ItemStack(Items.IRON_INGOT, 4), ItemStack.EMPTY, ItemStack.EMPTY);
        helper.assertTrue(plain != null && plain.additive().isEmpty() && plain.time() == 400, "Plain steel doesn't run without the fluorite");
        helper.succeed();
    }

    // 7. Hardened Alloy forms with amethyst and a nickel plate in the two additive slots, either way round, and not
    //    with only one of them. Input ports put the two additives in separate slots.
    static void twoAdditives(GameTestHelper helper) {
        ItemStack steel = new ItemStack(ModItems.STEEL_INGOT.get(), 2);
        ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD, 2);
        ItemStack nickel = new ItemStack(ModItems.NICKEL_PLATE.get());
        for (ItemStack[] slots : new ItemStack[][] { { amethyst, nickel }, { nickel, amethyst } }) {
            ArcforgeSmeltingRecipe recipe = AlloyGameTests.smelt(helper, steel, slots[0], slots[1]);
            helper.assertTrue(recipe != null && recipe.result().create().is(ModItems.HARDENED_ALLOY.get()),
                    "Hardened Alloy doesn't form with " + slots[0] + " then " + slots[1]);
        }
        helper.assertTrue(AlloyGameTests.smelt(helper, steel, amethyst, ItemStack.EMPTY) == null, "Hardened Alloy forms without the nickel plate");
        helper.assertTrue(AlloyGameTests.smelt(helper, steel, ItemStack.EMPTY, nickel) == null, "Hardened Alloy forms without the amethyst");

        BlockPos port = new BlockPos(1, 1, 0);
        MultiblockGameTests.buildFurnace(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(port, ArcforgeFurnaceBlockEntity.class);
                    BlockPos input = MultiblockGameTests.setFurnaceInputPort(helper, furnace);
                    ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, input, Direction.UP);
                    helper.assertTrue(handler != null, "Input port takes no items");
                    try (Transaction tx = Transaction.openRoot()) {
                        handler.insert(ItemResource.of(Items.AMETHYST_SHARD), 4, tx);
                        handler.insert(ItemResource.of(ModItems.NICKEL_PLATE.get()), 2, tx);
                        handler.insert(ItemResource.of(Items.AMETHYST_SHARD), 2, tx);
                        handler.insert(ItemResource.of(ModItems.STEEL_INGOT.get()), 4, tx);
                        tx.commit();
                    }
                    var items = furnace.getItems();
                    helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE).is(Items.AMETHYST_SHARD)
                            && items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE).getCount() == 6, "First additive slot holds " + items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE));
                    helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2).is(ModItems.NICKEL_PLATE.get()),
                            "Second additive slot holds " + items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2));
                    helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_METAL).is(ModItems.STEEL_INGOT.get()), "Steel didn't go to the metal slot");
                })
                .thenSucceed();
    }

    // 8. Hardened Alloy + Carbon Fiber + the Arcite-Tungsten Composite make Arcforged Alloy; the composite is a
    //    tungsten plate and two arcite crystals, shapeless.
    static void arcforgedAlloy(GameTestHelper helper) {
        ArcforgeSmeltingRecipe recipe = AlloyGameTests.smelt(helper, new ItemStack(ModItems.HARDENED_ALLOY.get(), 2), new ItemStack(ModItems.ARCITE_TUNGSTEN_COMPOSITE.get()),
                new ItemStack(ModItems.CARBON_FIBER.get(), 2));
        helper.assertTrue(recipe != null && recipe.result().create().is(ModItems.ARCFORGED_ALLOY.get()), "Arcforged Alloy doesn't form (composite first)");
        check(helper, craft(helper, new Item[][] { { ModItems.ARCITE_CRYSTAL.get(), ModItems.TUNGSTEN_PLATE.get(), ModItems.ARCITE_CRYSTAL.get() } }),
                ModItems.ARCITE_TUNGSTEN_COMPOSITE.get(), 1, "Tungsten plate and two arcite");
        helper.succeed();
    }

    // 9. A furnace saved with one additive slot (layout 2) loads with its coke, output and slag moved up a slot.
    static void saveMigration(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ARCFORGE_FURNACE_PORT.get());
        ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(pos, ArcforgeFurnaceBlockEntity.class);
        ServerLevel level = helper.getLevel();
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "saveMigration", Arcforge.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            ItemStacksResourceHandler old = new ItemStacksResourceHandler(5);
            ItemStack[] stacks = { new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.COAL_COKE.get(), 7),
                    new ItemStack(ModItems.STEEL_INGOT.get(), 2), new ItemStack(ModItems.SLAG.get(), 4) };
            for (int i = 0; i < stacks.length; i++) {
                old.set(i, ItemResource.of(stacks[i]), stacks[i].getCount());
            }
            old.serialize(output.child("items"));
            output.putInt("slot_layout", 2);
            tag = output.buildResult();
            furnace.loadCustomOnly(TagValueInput.create(reporter, level.registryAccess(), tag));
        }
        var items = furnace.getItems();
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_METAL).is(Items.IRON_INGOT), "Metal moved: " + items.getStack(ArcforgeFurnaceBlockEntity.SLOT_METAL));
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE).is(Items.GOLD_INGOT), "Additive moved");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2).isEmpty(), "Second additive slot isn't empty");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE).is(ModItems.COAL_COKE.get())
                && items.getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE).getCount() == 7, "Coke is not in slot 3: " + items.getStack(ArcforgeFurnaceBlockEntity.SLOT_COKE));
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_OUTPUT).is(ModItems.STEEL_INGOT.get()), "Output lost");
        helper.assertTrue(items.getStack(ArcforgeFurnaceBlockEntity.SLOT_BYPRODUCT).is(ModItems.SLAG.get()), "Slag lost");
        helper.succeed();
    }

    // 10. Eight Thermoelectric Efficiency Upgrades give the plant 50% more FE per HU; other machines refuse the card.
    static void thermoelectricUpgrade(GameTestHelper helper) {
        BlockPos[] pos = { new BlockPos(0, 1, 0), new BlockPos(2, 1, 0) };
        ThermoelectricPlantBlockEntity[] plants = new ThermoelectricPlantBlockEntity[2];
        for (int i = 0; i < 2; i++) {
            helper.setBlock(pos[i], ModBlocks.THERMOELECTRIC_PLANT.get());
            plants[i] = helper.getBlockEntity(pos[i], ThermoelectricPlantBlockEntity.class);
            plants[i].getHeat().add(plants[i].getHeat().storedAt(900));
        }
        var items = plants[1].getItems();
        ItemResource card = ItemResource.of(ModItems.THERMOELECTRIC_UPGRADE.get());
        helper.assertTrue(items.isValid(items.getFirstUpgradeSlot(), card), "The plant refuses the card");
        items.setStack(items.getFirstUpgradeSlot(), new ItemStack(ModItems.THERMOELECTRIC_UPGRADE.get(), 8));
        helper.setBlock(new BlockPos(4, 1, 0), ModBlocks.ARC_CRUSHER.get());
        var crusher = helper.getBlockEntity(new BlockPos(4, 1, 0), ArcCrusherBlockEntity.class).getItems();
        helper.assertTrue(!crusher.isValid(crusher.getFirstUpgradeSlot(), card), "The Arc Crusher takes the card");
        float ratio = plants[1].upgradedEfficiency() / plants[0].upgradedEfficiency();
        helper.assertTrue(Math.abs(ratio - 1.5F) < 0.001F, "Eight cards give x" + ratio);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    float fe = plants[1].getFePerTick() / (float) Math.max(1, plants[0].getFePerTick());
                    helper.assertTrue(plants[0].getFePerTick() > 0 && fe > 1.4F && fe < 1.6F,
                            "FE/t " + plants[0].getFePerTick() + " without, " + plants[1].getFePerTick() + " with 8 cards");
                })
                .thenSucceed();
    }

    // 11. Arcite needs a diamond pickaxe; with one it drops raw arcite.
    static void arcitePickaxe(GameTestHelper helper) {
        for (Block ore : List.of(ModBlocks.ARCITE_ORE.get(), ModBlocks.DEEPSLATE_ARCITE_ORE.get())) {
            BlockState state = ore.defaultBlockState();
            helper.assertTrue(!new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "An iron pickaxe can mine " + ore);
            helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), "A diamond pickaxe can't mine " + ore);
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(!drops.isEmpty() && drops.stream().allMatch(drop -> drop.is(ModItems.RAW_ARCITE.get())), ore + " dropped " + drops);
        }
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ModBlocks.WOLFRAMITE_ORE.get().defaultBlockState()),
                "An iron pickaxe can't mine wolframite");
        helper.succeed();
    }

    // 12. The common tags hold the right items, and every changed recipe loaded (none has a missing tag).
    static void tags(GameTestHelper helper) {
        tag(helper, "ingots/silver", ModItems.SILVER_INGOT.get());
        tag(helper, "ingots/tungsten", ModItems.TUNGSTEN_INGOT.get());
        tag(helper, "ingots/invar", ModItems.INVAR_INGOT.get());
        tag(helper, "gems/arcite", ModItems.ARCITE_CRYSTAL.get());
        tag(helper, "gems/fluorite", ModItems.FLUORITE_CRYSTAL.get());
        tag(helper, "storage_blocks/raw_tungsten", ModItems.RAW_WOLFRAMITE_BLOCK.get());
        tag(helper, "raw_materials/tungsten", ModItems.RAW_WOLFRAMITE.get());
        tag(helper, "dusts/nickel", ModItems.NICKEL_DUST.get());
        tag(helper, "plates/silver", ModItems.SILVER_PLATE.get());
        tag(helper, "ores/arcite", ModItems.DEEPSLATE_ARCITE_ORE.get());
        for (String recipe : List.of("trough_mirror", "wrought_battery", "tempered_battery", "hardened_battery", "arcforged_battery", "hardened_heat_cell",
                "hardened_thermal_conduit", "arcforged_energy_conduit", "arcforged_thermal_conduit", "arcforged_canister", "steam_turbine_array_casing",
                "induction_furnace", "induction_furnace_array_casing", "pressure_glass", "thermoelectric_plant", "thermoelectric_upgrade",
                "tungsten_heating_coil", "thermocouple", "arcite_tungsten_composite")) {
            helper.assertTrue(helper.getLevel().recipeAccess().recipeMap().byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/" + recipe))) != null, "crafting/" + recipe + " didn't load");
        }
        helper.succeed();
    }

    private static void tag(GameTestHelper helper, String path, Item item) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
        helper.assertTrue(new ItemStack(item).is(tag), item + " is not in #c:" + path);
    }

    // 14. The silver vein feature (arcforge:config_ore) places silver ore into stone, sized by the config.
    static void veinGenerates(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0), max = new BlockPos(6, 7, 6);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        ServerLevel level = helper.getLevel();
        var feature = level.registryAccess().lookupOrThrow(Registries.FEATURE)
                .getOrThrow(ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "ore_silver")));
        helper.assertTrue(feature.value() instanceof ModWorldgen.ConfigOre, "ore_silver isn't a config ore feature");
        boolean placed = false;
        for (int attempt = 0; attempt < 5 && !placed; attempt++) {
            placed = feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(new BlockPos(3, 4, 3)));
        }
        int ores = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (helper.getBlockState(pos.immutable()).is(ModBlocks.SILVER_ORE.get())) {
                ores++;
            }
        }
        helper.assertTrue(ores > 0, "No silver ore placed");
        helper.succeed();
    }

    // 13. An ore's toggle decides its biome modifier's condition; arcite has no toggle and no condition.
    static void oreToggle(GameTestHelper helper) {
        ModWorldgen.OreEnabled silver = new ModWorldgen.OreEnabled("silver");
        var enabled = ArcforgeConfig.ORES.get("silver").enabled();
        helper.assertTrue(enabled != null && silver.test(null), "Silver isn't on by default");
        enabled.set(false);
        try {
            helper.assertTrue(!silver.test(null), "Silver still generates when turned off");
        } finally {
            enabled.set(true);
        }
        helper.assertTrue(ArcforgeConfig.ORES.get("arcite").enabled() == null, "Arcite has a toggle");
        helper.assertTrue(conditions(helper, "ore_silver").isPresent(), "Silver's biome modifier has no condition");
        helper.assertTrue(conditions(helper, "ore_arcite").isEmpty(), "Arcite's biome modifier has a condition");
        helper.succeed();
    }

    // 15. Nether sulfur is placed in every Nether biome in UNDERGROUND_ORES, never UNDERGROUND_DECORATION: there it
    // sorts against BetterNether's ores, which are inline in its own biomes but added to vanilla's after ours, and the
    // feature order cycle crashes Nether generation. UNDERGROUND_ORES is otherwise empty in the Nether.
    static void netherSulfurStep(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var sulfur = registries.lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "ore_nether_sulfur")));
        int biomes = 0;
        for (var biome : registries.lookupOrThrow(Registries.BIOME).getOrThrow(BiomeTags.IS_NETHER)) {
            var steps = biome.value().getGenerationSettings().features();
            String name = biome.getRegisteredName();
            helper.assertTrue(steps.size() > GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                    && steps.get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()).contains(sulfur), "Sulfur isn't in " + name + "'s underground_ores");
            helper.assertTrue(!steps.get(GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()).contains(sulfur),
                    "Sulfur is in " + name + "'s underground_decoration");
            biomes++;
        }
        helper.assertTrue(biomes > 0, "No Nether biomes");
        helper.succeed();
    }

    private static Optional<Object> conditions(GameTestHelper helper, String modifier) {
        Identifier id = Identifier.fromNamespaceAndPath(Arcforge.MODID, "neoforge/biome_modifier/" + modifier + ".json");
        var resource = helper.getLevel().getServer().getResourceManager().getResource(id);
        helper.assertTrue(resource.isPresent(), "No biome modifier " + id);
        try (var reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has("neoforge:conditions") ? Optional.of(json.get("neoforge:conditions")) : Optional.empty();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
