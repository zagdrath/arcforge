/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.CompostBinBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.farming.LoamGrowth;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// Farming: the Compost Bin, tilling Loam, fertilizers and nutrients, irrigation, Wood Ash from the burners (and their
// old saves), and the farming recipes.
public final class FarmingGameTests {
    private FarmingGameTests() {}

    private static ResourceHandler<ItemResource> items(GameTestHelper helper, BlockPos pos, Direction side) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), side);
        helper.assertTrue(handler != null, "No item handler on " + side);
        return handler;
    }

    private static int insert(ResourceHandler<ItemResource> handler, net.minecraft.world.item.Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(ResourceHandler<ItemResource> handler, net.minecraft.world.item.Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(ItemResource.of(item), count, tx);
            tx.commit();
            return extracted;
        }
    }

    private static void use(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(absolute).add(0, 0.5, 0);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, absolute, false)));
    }

    private static int nutrients(GameTestHelper helper, BlockPos pos) {
        return LoamFarmlandBlock.nutrients(helper.getBlockState(pos));
    }

    // --- Compost Bin ---

    // Pumpkin pie always raises the level, so eight fed from the top fill the bin; it then makes Compost, which comes
    // out underneath, and the bin starts again.
    static void compostBinMakesCompost(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.COMPOST_BIN.get());
        helper.assertTrue(insert(items(helper, pos, Direction.UP), Items.PUMPKIN_PIE, 8) == 8, "The bin didn't take the pies");
        helper.assertTrue(insert(items(helper, pos, Direction.UP), Items.COBBLESTONE, 1) == 0, "The bin took cobblestone");
        helper.startSequence()
                .thenIdle(7 * 10 + 40 + 20)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(pos).getValue(CompostBinBlock.LEVEL) == CompostBinBlock.READY,
                            "The bin is at level " + helper.getBlockState(pos).getValue(CompostBinBlock.LEVEL) + ", not ready");
                    CompostBinBlockEntity bin = helper.getBlockEntity(pos, CompostBinBlockEntity.class);
                    helper.assertTrue(bin.getItems().getStack(CompostBinBlockEntity.SLOT_INPUT).getCount() == 1, "The spare pie wasn't kept for later");
                    helper.assertTrue(extract(items(helper, pos, Direction.DOWN), ModItems.COMPOST.get(), 64) == 1, "No Compost came out");
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getValue(CompostBinBlock.LEVEL) <= 1,
                        "The emptied bin didn't start again"))
                .thenSucceed();
    }

    // A player drops plant matter in by hand (one at a time), and takes the Compost with an empty hand.
    static void compostBinByHand(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.COMPOST_BIN.get());
        CompostBinBlockEntity bin = helper.getBlockEntity(pos, CompostBinBlockEntity.class);
        for (int i = 0; i < 7; i++) {
            helper.assertTrue(bin.insertByHand(helper.getLevel(), new ItemStack(Items.PUMPKIN_PIE)), "A pie was refused at level " + i);
        }
        helper.assertTrue(!bin.insertByHand(helper.getLevel(), new ItemStack(Items.PUMPKIN_PIE)), "A full bin took more");
        helper.assertTrue(!bin.insertByHand(helper.getLevel(), new ItemStack(Items.STONE)), "The bin took stone");
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.assertTrue(bin.takeCompost(helper.getLevel(), player), "Nothing to take");
                    helper.assertTrue(player.getInventory().countItem(ModItems.COMPOST.get()) == 1, "The player didn't get the Compost");
                    helper.assertTrue(helper.getBlockState(pos).getValue(CompostBinBlock.LEVEL) == 0, "The bin didn't empty");
                })
                .thenSucceed();
    }

    // --- Loam ---

    // A hoe tills Loam with air above it, but not with a block on top.
    static void loamTills(GameTestHelper helper) {
        BlockPos open = new BlockPos(0, 1, 0), covered = new BlockPos(2, 1, 0);
        helper.setBlock(open, ModBlocks.LOAM.get());
        helper.setBlock(covered, ModBlocks.LOAM.get());
        helper.setBlock(covered.above(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, new ItemStack(Items.IRON_HOE), open);
        use(helper, player, new ItemStack(Items.IRON_HOE), covered);
        helper.assertBlockPresent(ModBlocks.LOAM_FARMLAND.get(), open);
        helper.assertBlockPresent(ModBlocks.LOAM.get(), covered);
        helper.succeed();
    }

    // Fertilizers add their nutrients up to 15 (on the farmland or on the crop above it); at 15 they're refused.
    static void fertilizersAddNutrients(GameTestHelper helper) {
        BlockPos soil = new BlockPos(1, 1, 1);
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get());
        helper.setBlock(soil.above(), Blocks.WHEAT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, new ItemStack(ModItems.COMPOST.get()), soil);
        helper.assertTrue(nutrients(helper, soil) == 2, "Compost gave " + nutrients(helper, soil) + ", expected 2");
        use(helper, player, new ItemStack(ModItems.WOOD_ASH.get()), soil.above());
        helper.assertTrue(nutrients(helper, soil) == 5, "Wood Ash on the crop gave " + nutrients(helper, soil) + ", expected 5");
        use(helper, player, new ItemStack(ModItems.BASIC_SLAG.get()), soil);
        use(helper, player, new ItemStack(ModItems.MIXED_FERTILIZER.get()), soil);
        helper.assertTrue(nutrients(helper, soil) == LoamFarmlandBlock.MAX_NUTRIENTS, "Nutrients " + nutrients(helper, soil) + ", expected the cap");
        ItemStack more = new ItemStack(ModItems.COMPOST.get(), 3);
        use(helper, player, more, soil);
        helper.assertTrue(more.getCount() == 3, "Full soil used up a fertilizer");
        helper.succeed();
    }

    // A growth stage on fed soil uses a nutrient, and sometimes buys a free stage (which uses another); unfed soil is
    // left alone.
    static void growthUsesNutrients(GameTestHelper helper) {
        BlockPos soil = new BlockPos(1, 1, 1), bare = new BlockPos(3, 1, 1);
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, 10));
        helper.setBlock(soil.above(), Blocks.WHEAT);
        helper.setBlock(bare, ModBlocks.LOAM_FARMLAND.get());
        helper.setBlock(bare.above(), Blocks.WHEAT);
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 4; i++) {
            int before = nutrients(helper, soil);
            int age = helper.getBlockState(soil.above()).getValue(CropBlock.AGE);
            int stages = LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(soil.above()), random);
            helper.assertTrue(stages == 1 || stages == 2, "Paid for " + stages + " stages");
            helper.assertTrue(nutrients(helper, soil) == before - stages, "Nutrients went " + before + " -> " + nutrients(helper, soil));
            helper.assertTrue(helper.getBlockState(soil.above()).getValue(CropBlock.AGE) == age + stages - 1, "The bonus stage didn't match");
        }
        helper.assertTrue(LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(bare.above()), random) == 0, "Bare soil paid for growth");
        helper.assertTrue(helper.getBlockState(bare.above()).getValue(CropBlock.AGE) == 0, "The crop on bare soil grew extra");
        BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        helper.assertTrue(LoamGrowth.nextStage(ripe) == null, "Ripe wheat has a next stage");
        helper.succeed();
    }

    // Irrigated farmland wets itself with no water around; plain Loam Farmland, dry with nothing on it, turns back into
    // Loam but not while a crop grows on it.
    static void irrigationAndDrying(GameTestHelper helper) {
        BlockPos irrigated = new BlockPos(0, 1, 0), bare = new BlockPos(2, 1, 0), planted = new BlockPos(4, 1, 0);
        helper.setBlock(irrigated, ModBlocks.IRRIGATED_LOAM_FARMLAND.get().defaultBlockState().setValue(FarmlandBlock.MOISTURE, 0));
        helper.setBlock(bare, ModBlocks.LOAM_FARMLAND.get());
        helper.setBlock(planted, ModBlocks.LOAM_FARMLAND.get());
        helper.setBlock(planted.above(), Blocks.WHEAT);
        RandomSource random = RandomSource.create(7);
        var level = helper.getLevel();
        for (int i = 0; i < 200; i++) {
            for (BlockPos pos : List.of(irrigated, bare, planted)) {
                BlockPos absolute = helper.absolutePos(pos);
                BlockState state = level.getBlockState(absolute);
                if (state.getBlock() instanceof LoamFarmlandBlock) {
                    state.randomTick(level, absolute, random);
                }
            }
        }
        helper.assertTrue(helper.getBlockState(irrigated).getValue(FarmlandBlock.MOISTURE) == 7, "Irrigated farmland isn't moist");
        helper.assertBlockPresent(ModBlocks.LOAM.get(), bare);
        helper.assertBlockPresent(ModBlocks.LOAM_FARMLAND.get(), planted);
        helper.succeed();
    }

    // --- Wood Ash ---

    // Charcoal leaves ash (coal doesn't); the ash slot fills to a stack and empties through an Output face.
    static void fireboxAshSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FIREBOX.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(pos, FireboxBlockEntity.class);
        helper.assertTrue(BurnerBlockEntity.leavesAsh(new ItemStack(Items.CHARCOAL)), "Charcoal doesn't leave ash");
        helper.assertTrue(!BurnerBlockEntity.leavesAsh(new ItemStack(Items.COAL)), "Coal leaves ash");
        helper.assertTrue(firebox.addAsh(70) == 64, "The ash slot doesn't hold a stack");
        helper.assertTrue(!firebox.getItems().isValid(BurnerBlockEntity.SLOT_ASH, ItemResource.of(ModItems.WOOD_ASH.get())),
                "Wood Ash can be put in the ash slot");
        firebox.setSideMode(RelativeSide.BOTTOM, SideMode.OUTPUT);
        helper.assertTrue(extract(items(helper, pos, Direction.DOWN), ModItems.WOOD_ASH.get(), 64) == 64, "The ash didn't come out");
        helper.succeed();
    }

    // A Firebox saved before the ash slot keeps its fuel and moves its upgrades up past the new slot.
    static void burnerOldSaveMigrates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FIREBOX.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(pos, FireboxBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        ItemStacksResourceHandler old = new ItemStacksResourceHandler(5);
        old.set(0, ItemResource.of(Items.CHARCOAL), 12);
        old.set(1, ItemResource.of(ModItems.SPEED_UPGRADE.get()), 3);
        old.set(2, ItemResource.of(ModItems.HEAT_UPGRADE.get()), 2);
        old.serialize(output.child("items"));
        CompoundTag tag = output.buildResult();
        firebox.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        var items = firebox.getItems();
        helper.assertTrue(items.getStack(BurnerBlockEntity.SLOT_FUEL).getCount() == 12, "Fuel lost");
        helper.assertTrue(items.getStack(BurnerBlockEntity.SLOT_ASH).isEmpty(), "Something landed in the ash slot");
        helper.assertTrue(items.getStack(items.getFirstUpgradeSlot()).is(ModItems.SPEED_UPGRADE.get()), "Speed upgrade lost");
        helper.assertTrue(items.getStack(items.getFirstUpgradeSlot() + 1).is(ModItems.HEAT_UPGRADE.get()), "Heat upgrade lost");
        CompoundTag saved = firebox.saveCustomOnly(registries);
        firebox.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(items.getStack(items.getFirstUpgradeSlot()).is(ModItems.SPEED_UPGRADE.get()), "New layout doesn't reload");
        helper.succeed();
    }

    // --- Recipes ---

    // Slag crushes into Basic Slag, and one each of Compost, Wood Ash and Basic Slag make a Mixed Fertilizer.
    static void recipes(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().recipeMap();
        RecipeHolder<?> crushing = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Arcforge.MODID, "crushing/basic_slag_from_slag")));
        helper.assertTrue(crushing != null, "No Basic Slag crushing recipe");
        RecipeHolder<?> mixed = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/mixed_fertilizer")));
        helper.assertTrue(mixed != null && mixed.value() instanceof CraftingRecipe, "No Mixed Fertilizer recipe");
        CraftingRecipe recipe = (CraftingRecipe) mixed.value();
        CraftingInput grid = CraftingInput.of(3, 1, List.of(new ItemStack(ModItems.COMPOST.get()), new ItemStack(ModItems.WOOD_ASH.get()),
                new ItemStack(ModItems.BASIC_SLAG.get())));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Compost, Wood Ash and Basic Slag don't match");
        helper.assertTrue(recipe.assemble(grid).is(ModItems.MIXED_FERTILIZER.get()), "The recipe doesn't make Mixed Fertilizer");
        helper.succeed();
    }

    // The user's steps on a Combustion Plant: it starts on coal, the rest of the coal is taken out, the burning coal
    // burns out, and charcoal put in afterwards burns.
    static void combustionPlantCoalThenCharcoal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.COMBUSTION_PLANT.get());
        var plant = helper.getBlockEntity(pos, net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity.class);
        var items = plant.getItems();
        items.setStack(BurnerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plant.getBurnTime() > 0, "It didn't light the coal"))
                .thenExecute(() -> items.setStack(BurnerBlockEntity.SLOT_FUEL, ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(plant.getBurnTime() == 0, "The last coal is still burning: " + plant.getBurnTime()
                        + " ticks left, " + plant.getStored() + " / " + plant.getCapacity() + " FE, " + plant.getStatus()))
                .thenExecute(() -> items.setStack(BurnerBlockEntity.SLOT_FUEL, new ItemStack(Items.CHARCOAL, 4)))
                .thenWaitUntil(() -> helper.assertTrue(plant.getBurnTime() > 0 && items.getStack(BurnerBlockEntity.SLOT_FUEL).getCount() == 3,
                        "The charcoal didn't light: " + plant.getBurnTime() + " ticks, " + items.getStack(BurnerBlockEntity.SLOT_FUEL)
                                + ", " + plant.getStored() + " / " + plant.getCapacity() + " FE, " + plant.getStatus()))
                .thenSucceed();
    }
}

