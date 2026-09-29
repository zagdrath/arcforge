/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.JetpackFlight;
import net.zagdrath.arcforge.item.tool.JetpackFuel;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.item.tool.JetpackMode;
import net.zagdrath.arcforge.item.tool.ModuleType;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The Jetpack, Arc Drill and Arc Saw. Mock players aren't moved by physics on the server, so flight is checked by
// running the server side of it (JetpackFlight.serverTick) directly, a tick at a time.
public final class ArcToolGameTests {
    private ArcToolGameTests() {}

    // --- Helpers ---

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos standRelative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(standRelative);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    // A player two blocks west of `centre`, facing it (in world terms; a player looks where their head points),
    // holding `tool`. Breaks `centre` through the game mode, as mining it would.
    private static ServerPlayer mine(GameTestHelper helper, ItemStack tool, BlockPos centre, boolean sneaking) {
        ServerPlayer player = player(helper, centre.west(2).below());
        BlockPos from = helper.absolutePos(centre.west(2).below());
        BlockPos to = helper.absolutePos(centre);
        float yaw = Direction.getApproximateNearest(to.getX() - from.getX(), 0, to.getZ() - from.getZ()).toYRot();
        player.snapTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, yaw, 0.0F);
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.setShiftKeyDown(sneaking);
        player.gameMode.destroyBlock(to);
        return player;
    }

    private static ItemStack charged(Item item) {
        ItemStack stack = new ItemStack(item);
        ArcToolItem.setEnergy(stack, ((ArcToolItem) item).capacity());
        return stack;
    }

    // Installs modules in order, turning on those listed in `on`.
    private static ItemStack withModules(ItemStack tool, List<ModuleType> installed, Set<ModuleType> on) {
        ToolModules modules = ToolModules.EMPTY;
        for (int index = 0; index < installed.size(); index++) {
            modules = modules.withSlot(index, Optional.of(installed.get(index)));
            if (modules.isOn(index) != on.contains(installed.get(index))) {
                modules = modules.toggle(index);
            }
        }
        tool.set(ModDataComponents.TOOL_MODULES.get(), modules);
        return tool;
    }

    private static int count(GameTestHelper helper, BlockPos centre, int radius, Item item) {
        AABB box = new AABB(helper.absolutePos(centre)).inflate(radius);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void clearDrops(GameTestHelper helper, BlockPos centre, int radius) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(centre)).inflate(radius)).forEach(ItemEntity::discard);
    }

    private static int air(GameTestHelper helper, BlockPos from, BlockPos to) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            count += helper.getBlockState(pos).isAir() ? 1 : 0;
        }
        return count;
    }

    private static void fill(GameTestHelper helper, BlockPos from, BlockPos to, Block block) {
        BlockPos.betweenClosed(from, to).forEach(pos -> helper.setBlock(pos.immutable(), block));
    }

    private static PressurizedCylinderBlockEntity cylinder(GameTestHelper helper, BlockPos pos, Fluid gas, int amount) {
        helper.setBlock(pos, ModBlocks.pressurizedCylinder(ConduitTier.ARCFORGED).get());
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(pos, PressurizedCylinderBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            cylinder.getTank().insert(FluidResource.of(gas), amount, tx);
            tx.commit();
        }
        return cylinder;
    }

    private static ItemStack jetpack(Item item, Fluid gas, int amount) {
        ItemStack stack = new ItemStack(item);
        if (amount > 0) {
            JetpackItem.setFluid(stack, new FluidStack(gas, amount));
        }
        return stack;
    }

    // A mock player wearing `jetpack`, in the air.
    private static ServerPlayer wearer(GameTestHelper helper, ItemStack jetpack) {
        ServerPlayer player = player(helper, new BlockPos(1, 10, 1));
        player.setItemSlot(EquipmentSlot.CHEST, jetpack);
        player.setOnGround(false);
        return player;
    }

    // --- Jetpack ---

    // A 20,000 mB Hydrogen cylinder fills an empty Tempered Jetpack to its 16,000; Oxygen isn't a fuel; a jetpack
    // holding Steam won't take Hydrogen.
    static void jetpackFillsFromCylinder(GameTestHelper helper) {
        PressurizedCylinderBlockEntity hydrogen = cylinder(helper, new BlockPos(0, 1, 0), ModFluids.HYDROGEN.get(), 20_000);
        PressurizedCylinderBlockEntity oxygen = cylinder(helper, new BlockPos(2, 1, 0), ModFluids.OXYGEN.get(), 5_000);
        PressurizedCylinderBlockEntity mixed = cylinder(helper, new BlockPos(4, 1, 0), ModFluids.HYDROGEN.get(), 5_000);
        hydrogen.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(ModItems.TEMPERED_JETPACK.get()));
        oxygen.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(ModItems.TEMPERED_JETPACK.get()));
        mixed.getItems().setStack(StorageBlockEntity.SLOT_OUT, jetpack(ModItems.TEMPERED_JETPACK.get(), ModFluids.STEAM.get(), 100));
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    FluidStack filled = JetpackItem.fluid(hydrogen.getItems().getStack(StorageBlockEntity.SLOT_OUT));
                    helper.assertTrue(filled.is(ModFluids.HYDROGEN.get()) && filled.getAmount() == 16_000, "The jetpack holds " + filled.getAmount() + " mB");
                    helper.assertTrue(hydrogen.getTank().getAmount() == 4_000, "The cylinder kept " + hydrogen.getTank().getAmount() + " mB");
                    helper.assertTrue(JetpackItem.fluid(oxygen.getItems().getStack(StorageBlockEntity.SLOT_OUT)).isEmpty(), "A jetpack took Oxygen");
                    FluidStack steam = JetpackItem.fluid(mixed.getItems().getStack(StorageBlockEntity.SLOT_OUT));
                    helper.assertTrue(steam.is(ModFluids.STEAM.get()) && steam.getAmount() == 100, "A Steam jetpack took Hydrogen: " + steam.getAmount());
                })
                .thenSucceed();
    }

    // A 4,000 mB Hydrogen cartridge clicked onto an empty jetpack pours all of it in.
    static void jetpackFillsFromCartridge(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        ItemStack jetpack = new ItemStack(ModItems.TEMPERED_JETPACK.get());
        PortableStorageItem cartridgeItem = ModItems.portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.TEMPERED);
        ItemStack cartridge = new ItemStack(cartridgeItem);
        cartridge.set(ModDataComponents.FLUID_CONTENTS.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(new FluidStack(ModFluids.HYDROGEN.get(), 4_000)));
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, jetpack);
        boolean handled = jetpack.getItem().overrideOtherStackedOnMe(jetpack, cartridge, new Slot(container, 0, 0, 0), ClickAction.SECONDARY, player, SlotAccess.of(() -> cartridge, stack -> {}));
        helper.assertTrue(handled, "The click wasn't handled");
        helper.assertTrue(JetpackItem.fluid(jetpack).getAmount() == 4_000, "The jetpack holds " + JetpackItem.fluid(jetpack).getAmount() + " mB");
        helper.assertTrue(PortableStorageItem.fluid(cartridge).isEmpty(), "The cartridge kept " + PortableStorageItem.fluid(cartridge).getAmount() + " mB");
        helper.succeed();
    }

    // Holding jump for 20 ticks burns each fuel's data-map rate every tick and pushes the wearer up.
    static void jetpackBurnsPerDataMap(GameTestHelper helper) {
        for (Fluid gas : new Fluid[] { ModFluids.STEAM.get(), ModFluids.HYDROGEN.get() }) {
            JetpackFuel fuel = JetpackFuel.of(gas);
            helper.assertTrue(fuel != null, gas + " isn't a jetpack fuel");
            ServerPlayer player = wearer(helper, jetpack(ModItems.TEMPERED_JETPACK.get(), gas, 1_000));
            JetpackFlight.setInput(player, true, false);
            for (int tick = 1; tick <= 20; tick++) {
                JetpackFlight.serverTick(player);
                if (tick == 5) {
                    helper.assertTrue(player.getDeltaMovement().y > 0, "Not climbing on " + gas + " after 5 ticks: " + player.getDeltaMovement().y);
                }
            }
            int left = JetpackItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
            int expected = 1_000 - 20 * fuel.mbPerTick();
            helper.assertTrue(left == expected, gas + ": " + left + " mB left, not " + expected);
            JetpackFlight.forget(player);
        }
        helper.succeed();
    }

    // Hovering with no keys holds the wearer, clears their fall distance and burns 1.5x Steam's rate; Off doesn't.
    static void jetpackHoverResetsFallDistance(GameTestHelper helper) {
        ItemStack stack = jetpack(ModItems.TEMPERED_JETPACK.get(), ModFluids.STEAM.get(), 1_000);
        stack.set(ModDataComponents.JETPACK_MODE.get(), JetpackMode.HOVER);
        ServerPlayer player = wearer(helper, stack);
        double startY = player.getY();
        player.fallDistance = 15;
        for (int tick = 0; tick < 20; tick++) {
            JetpackFlight.serverTick(player);
        }
        helper.assertTrue(player.fallDistance == 0, "Fall distance is " + player.fallDistance);
        helper.assertTrue(Math.abs(player.getY() - startY) < 0.1, "Moved from " + startY + " to " + player.getY());
        int burned = 1_000 - JetpackItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        int expected = 20 * (int) Math.ceil(JetpackFuel.of(ModFluids.STEAM.get()).mbPerTick() * ArcforgeConfig.JETPACK_HOVER_FUEL_MULTIPLIER.getAsDouble());
        helper.assertTrue(burned == expected, "Hovering burned " + burned + " mB, not " + expected);

        // Control: switched off, it does nothing for a falling wearer.
        player.getItemBySlot(EquipmentSlot.CHEST).set(ModDataComponents.JETPACK_MODE.get(), JetpackMode.OFF);
        player.fallDistance = 15;
        for (int tick = 0; tick < 20; tick++) {
            JetpackFlight.serverTick(player);
        }
        helper.assertTrue(player.fallDistance == 15, "An Off jetpack cleared the fall distance");
        JetpackFlight.forget(player);
        helper.succeed();
    }

    // With jetpacks turned off in the config, jump burns nothing and gives no lift. (All in one tick, so no other
    // test sees the changed setting.)
    static void jetpackDisabledByConfig(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, jetpack(ModItems.TEMPERED_JETPACK.get(), ModFluids.HYDROGEN.get(), 1_000));
        JetpackFlight.setInput(player, true, false);
        boolean was = ArcforgeConfig.ENABLE_JETPACKS.get();
        ArcforgeConfig.ENABLE_JETPACKS.set(false);
        try {
            for (int tick = 0; tick < 20; tick++) {
                JetpackFlight.serverTick(player);
            }
        } finally {
            ArcforgeConfig.ENABLE_JETPACKS.set(was);
        }
        helper.assertTrue(JetpackItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount() == 1_000, "A disabled jetpack burned fuel");
        helper.assertTrue(player.getDeltaMovement().y <= 0, "A disabled jetpack lifted: " + player.getDeltaMovement().y);
        JetpackFlight.forget(player);
        helper.succeed();
    }

    // Upgrading keeps the gas, plating, armour and mode of a jetpack, and the FE and modules of a drill.
    static void tierUpgradeKeepsContents(GameTestHelper helper) {
        ItemStack tempered = jetpack(ModItems.TEMPERED_JETPACK.get(), ModFluids.HYDROGEN.get(), 10_000);
        tempered.set(ModDataComponents.JETPACK_MODE.get(), JetpackMode.HOVER);
        RecipeHolder<?> plating = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "smithing/jetpack_plating")));
        helper.assertTrue(plating != null && plating.value() instanceof SmithingRecipe, "No jetpack plating smithing recipe");
        SmithingRecipeInput smithing = new SmithingRecipeInput(new ItemStack(ModItems.STEEL_PLATE.get()), tempered, new ItemStack(ModItems.STEEL_CHESTPLATE.get()));
        SmithingRecipe recipe = (SmithingRecipe) plating.value();
        helper.assertTrue(recipe.matches(smithing, helper.getLevel()), "Plating doesn't match a Tempered Jetpack and a Steel Chestplate");
        ItemStack plated = recipe.assemble(smithing);
        helper.assertFalse(recipe.matches(new SmithingRecipeInput(new ItemStack(ModItems.STEEL_PLATE.get()), plated,
                new ItemStack(ModItems.STEEL_CHESTPLATE.get())), helper.getLevel()), "A plated jetpack can be plated again");

        ItemStack hardened = AlloyGameTests.craft(helper, "crafting/hardened_jetpack", new String[] { "PAP", "AXA", "PEP" }, Map.of(
                'P', new ItemStack(ModItems.PLASTIC_SHEET.get()), 'A', new ItemStack(ModItems.HARDENED_ALLOY.get()), 'X', plated,
                'E', new ItemStack(ModItems.portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.HARDENED))));
        helper.assertTrue(hardened.is(ModItems.HARDENED_JETPACK.get()), "Didn't make a Hardened Jetpack");
        FluidStack gas = JetpackItem.fluid(hardened);
        helper.assertTrue(gas.is(ModFluids.HYDROGEN.get()) && gas.getAmount() == 10_000, "Gas lost: " + gas.getAmount());
        helper.assertTrue(JetpackItem.isPlated(hardened), "Plating lost");
        double armor = hardened.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
        helper.assertTrue(armor == 7, "Armour is " + armor);
        helper.assertTrue(JetpackItem.mode(hardened) == JetpackMode.HOVER, "Mode is " + JetpackItem.mode(hardened));
        helper.assertTrue(((JetpackItem) hardened.getItem()).capacity() == 64_000, "Capacity is " + ((JetpackItem) hardened.getItem()).capacity());

        ItemStack drill = withModules(new ItemStack(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.AREA, ModuleType.FORTUNE_I), Set.of(ModuleType.AREA));
        ArcToolItem.setEnergy(drill, 55_555);
        ToolModules before = ArcToolItem.modules(drill);
        ItemStack upgradedDrill = AlloyGameTests.craft(helper, "crafting/hardened_arc_drill", new String[] { "PAP", "AXA", "PEP" }, Map.of(
                'P', new ItemStack(ModItems.PLASTIC_SHEET.get()), 'A', new ItemStack(ModItems.HARDENED_ALLOY.get()), 'X', drill,
                'E', new ItemStack(ModItems.portable(PortableStorageItem.Kind.BATTERY, ConduitTier.HARDENED))));
        helper.assertTrue(upgradedDrill.is(ModItems.HARDENED_ARC_DRILL.get()), "Didn't make a Hardened Arc Drill");
        helper.assertTrue(ArcToolItem.energy(upgradedDrill) == 55_555, "FE lost: " + ArcToolItem.energy(upgradedDrill));
        helper.assertTrue(ArcToolItem.modules(upgradedDrill).equals(before), "Modules changed: " + ArcToolItem.modules(upgradedDrill));
        helper.assertTrue(((ArcToolItem) upgradedDrill.getItem()).moduleSlots() == 3, "A Hardened drill has " + ((ArcToolItem) upgradedDrill.getItem()).moduleSlots() + " slots");
        helper.succeed();
    }

    // --- Arc Drill and Arc Saw ---

    // 69 FE for stone, 88 for deepslate, 675 for obsidian; Area and Fortune I on make stone 121.
    static void drillFePerBlock(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 1);
        Block[] blocks = { Blocks.STONE, Blocks.DEEPSLATE, Blocks.OBSIDIAN };
        int[] costs = { 69, 88, 675 };
        for (int i = 0; i < blocks.length; i++) {
            helper.setBlock(centre, blocks[i]);
            ItemStack drill = charged(ModItems.TEMPERED_ARC_DRILL.get());
            int before = ArcToolItem.energy(drill);
            mine(helper, drill, centre, false);
            helper.assertTrue(helper.getBlockState(centre).isAir(), blocks[i] + " didn't break");
            helper.assertTrue(before - ArcToolItem.energy(drill) == costs[i], blocks[i] + " cost " + (before - ArcToolItem.energy(drill)) + " FE, not " + costs[i]);
        }
        helper.setBlock(centre, Blocks.STONE);
        ItemStack drill = withModules(charged(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.AREA, ModuleType.FORTUNE_I),
                Set.of(ModuleType.AREA, ModuleType.FORTUNE_I));
        int before = ArcToolItem.energy(drill);
        mine(helper, drill, centre, false);
        helper.assertTrue(before - ArcToolItem.energy(drill) == 121, "Area and Fortune I stone cost " + (before - ArcToolItem.energy(drill)) + " FE, not 121");
        helper.succeed();
    }

    // Tempered mines to diamond level; Hardened and Arcforged also mine what needs netherite. The Saw is an axe.
    static void drillMiningLevelByTier(GameTestHelper helper) {
        ItemStack tempered = charged(ModItems.TEMPERED_ARC_DRILL.get());
        for (Block block : new Block[] { Blocks.DIAMOND_ORE, Blocks.OBSIDIAN, ModBlocks.ARCITE_ORE.get() }) {
            helper.assertTrue(tempered.isCorrectToolForDrops(block.defaultBlockState()), "A Tempered drill can't mine " + block);
        }
        // Blocks only netherite tools mine (none in vanilla 26.3 today, so this may be empty).
        Set<Block> netheriteOnly = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL) && !state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                netheriteOnly.add(block);
            }
        }
        for (Block block : netheriteOnly) {
            helper.assertFalse(tempered.isCorrectToolForDrops(block.defaultBlockState()), "A Tempered drill mines " + block);
            for (Item item : new Item[] { ModItems.HARDENED_ARC_DRILL.get(), ModItems.ARCFORGED_ARC_DRILL.get() }) {
                helper.assertTrue(charged(item).isCorrectToolForDrops(block.defaultBlockState()), item + " can't mine " + block);
            }
        }
        ItemStack saw = charged(ModItems.TEMPERED_ARC_SAW.get());
        helper.assertTrue(saw.isCorrectToolForDrops(Blocks.OAK_LOG.defaultBlockState()), "The saw can't cut logs");
        helper.assertFalse(saw.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F, "The saw mines stone");
        helper.succeed();
    }

    // The Steel Hammer's layout with a Tempered drill and Area on: the chest and obsidian stay, but the dirt goes
    // (the drill is a shovel too), 7 blocks in all. An Arcforged drill takes a 5x5.
    static void drillAreaFollowsHammerRules(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 1);
        fill(helper, centre.offset(0, -1, -1), centre.offset(0, 1, 1), Blocks.STONE);
        helper.setBlock(centre.offset(0, 1, -1), Blocks.CHEST);
        helper.setBlock(centre.offset(0, 1, 0), Blocks.OBSIDIAN);
        helper.setBlock(centre.offset(0, -1, 1), Blocks.DIRT);
        mine(helper, withModules(charged(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.AREA), Set.of(ModuleType.AREA)), centre, false);
        helper.assertTrue(air(helper, centre.offset(0, -1, -1), centre.offset(0, 1, 1)) == 7, air(helper, centre.offset(0, -1, -1), centre.offset(0, 1, 1)) + " broke, not 7");
        helper.assertBlockPresent(Blocks.CHEST, centre.offset(0, 1, -1));
        helper.assertBlockPresent(Blocks.OBSIDIAN, centre.offset(0, 1, 0));

        BlockPos big = new BlockPos(8, 3, 3);
        fill(helper, big.offset(0, -2, -2), big.offset(0, 2, 2), Blocks.STONE);
        mine(helper, withModules(charged(ModItems.ARCFORGED_ARC_DRILL.get()), List.of(ModuleType.AREA), Set.of(ModuleType.AREA)), big, false);
        helper.assertTrue(air(helper, big.offset(0, -2, -2), big.offset(0, 2, 2)) == 25, air(helper, big.offset(0, -2, -2), big.offset(0, 2, 2)) + " broke, not 25");
        helper.succeed();
    }

    // Vein takes 64 of 100 connected iron ore. A saw fells 32 logs, 256 with Vein, 1 with Felling off. A drill
    // short of FE stops early and never goes below 0.
    static void veinLimits(GameTestHelper helper) {
        BlockPos ore = new BlockPos(2, 1, 1);
        fill(helper, ore, ore.offset(4, 3, 4), Blocks.IRON_ORE);
        mine(helper, withModules(charged(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.VEIN), Set.of(ModuleType.VEIN)), ore, false);
        helper.assertTrue(air(helper, ore, ore.offset(4, 3, 4)) == 64, air(helper, ore, ore.offset(4, 3, 4)) + " iron ore broke, not 64");

        BlockPos trunk = new BlockPos(10, 1, 1);
        int[] expected = { 32, ArcforgeConfig.FELLING_VEIN_LIMIT.getAsInt(), 1 };
        for (int run = 0; run < 3; run++) {
            fill(helper, trunk, trunk.above(299), Blocks.OAK_LOG);
            fill(helper, trunk.offset(1, 10, 0), trunk.offset(5, 10, 0), Blocks.OAK_LOG);
            ItemStack saw = charged(ModItems.ARCFORGED_ARC_SAW.get());
            if (run == 1) {
                withModules(saw, List.of(ModuleType.VEIN), Set.of(ModuleType.VEIN));
            } else if (run == 2) {
                saw.set(ModDataComponents.FELLING.get(), false);
            }
            mine(helper, saw, trunk, false);
            int felled = air(helper, trunk, trunk.above(299)) + air(helper, trunk.offset(1, 10, 0), trunk.offset(5, 10, 0));
            helper.assertTrue(felled == expected[run], "Run " + run + " felled " + felled + ", not " + expected[run]);
            clearDrops(helper, trunk.above(20), 40);
        }
        fill(helper, trunk, trunk.above(299), Blocks.AIR);
        fill(helper, trunk.offset(1, 10, 0), trunk.offset(5, 10, 0), Blocks.AIR);

        BlockPos poor = new BlockPos(2, 1, 8);
        fill(helper, poor, poor.offset(4, 3, 4), Blocks.IRON_ORE);
        ItemStack drill = withModules(new ItemStack(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.VEIN), Set.of(ModuleType.VEIN));
        ArcToolItem.setEnergy(drill, 500);
        mine(helper, drill, poor, false);
        int broke = air(helper, poor, poor.offset(4, 3, 4));
        helper.assertTrue(broke > 1 && broke < 64, broke + " broke on 500 FE");
        helper.assertTrue(ArcToolItem.energy(drill) >= 0, "Energy went to " + ArcToolItem.energy(drill));
        helper.succeed();
    }

    // Empty, a drill digs at speed 0 and a forced break is cancelled; it never takes damage.
    static void noMiningAtZeroFe(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 1);
        helper.setBlock(centre, Blocks.STONE);
        ItemStack drill = new ItemStack(ModItems.TEMPERED_ARC_DRILL.get());
        ServerPlayer player = player(helper, centre.west(2).below());
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        float speed = player.getDestroySpeed(Blocks.STONE.defaultBlockState(), helper.absolutePos(centre));
        helper.assertTrue(speed == 0.0F, "An empty drill digs at " + speed);
        mine(helper, drill, centre, false);
        helper.assertBlockPresent(Blocks.STONE, centre);
        helper.assertFalse(drill.isEmpty(), "The drill broke");
        helper.assertFalse(drill.has(DataComponents.DAMAGE), "The drill took damage");
        helper.succeed();
    }

    // Silk Touch drops the ore itself; Fortune III multiplies diamonds; turning Fortune on turns Silk Touch off.
    static void silkAndFortuneDrops(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 1);
        helper.setBlock(centre, Blocks.DIAMOND_ORE);
        mine(helper, withModules(charged(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.SILK_TOUCH), Set.of(ModuleType.SILK_TOUCH)), centre, false);
        helper.assertTrue(count(helper, centre, 3, Items.DIAMOND_ORE) == 1, "Silk Touch dropped " + count(helper, centre, 3, Items.DIAMOND_ORE) + " diamond ore");
        helper.assertTrue(count(helper, centre, 3, Items.DIAMOND) == 0, "Silk Touch dropped diamonds");
        clearDrops(helper, centre, 3);

        ItemStack fortune = withModules(charged(ModItems.TEMPERED_ARC_DRILL.get()), List.of(ModuleType.FORTUNE_III), Set.of(ModuleType.FORTUNE_III));
        for (int i = 0; i < 50; i++) {
            helper.setBlock(centre, Blocks.DIAMOND_ORE);
            mine(helper, fortune, centre, false);
        }
        int diamonds = count(helper, centre, 3, Items.DIAMOND);
        helper.assertTrue(diamonds > 75, "Fortune III gave " + diamonds + " diamonds from 50 ores");
        clearDrops(helper, centre, 3);

        ToolModules both = ToolModules.EMPTY.withSlot(0, Optional.of(ModuleType.SILK_TOUCH)).withSlot(1, Optional.of(ModuleType.FORTUNE_I));
        helper.assertTrue(both.isOn(0) && !both.isOn(1), "Installing Fortune beside Silk Touch turned it on");
        ToolModules toggled = both.toggle(1);
        helper.assertTrue(toggled.isOn(1) && !toggled.isOn(0), "Turning Fortune on didn't turn Silk Touch off");
        helper.succeed();
    }

    // A Tempered drill in an Energy Cell's charge slot takes 2,000 FE a tick, its charge rate.
    static void chargesInEnergyCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.energyCell(ConduitTier.ARCFORGED).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            cell.getEnergyHandler(null).insert(1_000_000, tx);
            tx.commit();
        }
        cell.getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(ModItems.TEMPERED_ARC_DRILL.get()));
        int[] last = { -1 };
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> last[0] = ArcToolItem.energy(cell.getItems().getStack(StorageBlockEntity.SLOT_OUT)))
                .thenIdle(1)
                .thenExecute(() -> {
                    int now = ArcToolItem.energy(cell.getItems().getStack(StorageBlockEntity.SLOT_OUT));
                    helper.assertTrue(now - last[0] == 2_000, "Charged " + (now - last[0]) + " FE in a tick, not 2,000");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    int now = ArcToolItem.energy(cell.getItems().getStack(StorageBlockEntity.SLOT_OUT));
                    helper.assertTrue(now % 2_000 == 0 && now >= 10_000, "Holds " + now + " FE after 5+ ticks");
                })
                .thenSucceed();
    }
}
