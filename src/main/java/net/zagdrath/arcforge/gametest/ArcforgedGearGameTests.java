/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.ArcforgedGear;
import net.zagdrath.arcforge.item.tool.AreaToolItem;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// Arcforged tools and armour: a tier above netherite, the Smithing Table upgrade (keeping enchantments, name and the
// share of wear), the template and its copy, FE paying for wear and repairing it, charging, and surviving fire and
// explosions as an item.
public final class ArcforgedGearGameTests {
    private ArcforgedGearGameTests() {}

    // The sum of the stack's modifiers to one attribute.
    private static double attribute(ItemStack stack, Holder<Attribute> attribute) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().equals(attribute)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    // Netherite-level mining at speed 11 with 3,072 uses; a 9-damage sword (8 for netherite); the Hammer and Excavator
    // are area tools with three times the uses.
    static void toolLevels(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(ModItems.ARCFORGED_PICKAXE.get());
        for (Block block : new Block[] { Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS, ModBlocks.ARCITE_ORE.get(), Blocks.DIAMOND_ORE }) {
            helper.assertTrue(pickaxe.isCorrectToolForDrops(block.defaultBlockState()), "An Arcforged pickaxe can't mine " + block);
        }
        helper.assertTrue(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 11.0F, "Speed on stone is " + pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()));
        helper.assertTrue(pickaxe.getMaxDamage() == 3_072, "Durability is " + pickaxe.getMaxDamage());
        double sword = 1.0 + attribute(new ItemStack(ModItems.ARCFORGED_SWORD.get()), Attributes.ATTACK_DAMAGE);
        double netherite = 1.0 + attribute(new ItemStack(Items.NETHERITE_SWORD), Attributes.ATTACK_DAMAGE);
        helper.assertTrue(sword == 9.0 && netherite == 8.0, "The sword deals " + sword + " (netherite " + netherite + ")");
        for (Item tool : new Item[] { ModItems.ARCFORGED_AXE.get(), ModItems.ARCFORGED_SHOVEL.get(), ModItems.ARCFORGED_HOE.get() }) {
            helper.assertTrue(new ItemStack(tool).isValidRepairItem(new ItemStack(ModItems.ARCFORGED_ALLOY.get())), tool + " doesn't repair with Arcforged Alloy");
        }
        for (Item tool : new Item[] { ModItems.ARCFORGED_HAMMER.get(), ModItems.ARCFORGED_EXCAVATOR.get() }) {
            helper.assertTrue(tool instanceof AreaToolItem, tool + " isn't an area tool");
            helper.assertTrue(new ItemStack(tool).getMaxDamage() == 9_216, tool + " has " + new ItemStack(tool).getMaxDamage() + " uses");
        }
        helper.succeed();
    }

    // 4 / 9 / 7 / 4 armour, 4 toughness and 0.15 knockback resistance a piece, more durable than netherite, repaired
    // with Arcforged Alloy; trimmable and enchantable.
    static void armorValues(GameTestHelper helper) {
        Item[] pieces = { ModItems.ARCFORGED_HELMET.get(), ModItems.ARCFORGED_CHESTPLATE.get(), ModItems.ARCFORGED_LEGGINGS.get(), ModItems.ARCFORGED_BOOTS.get() };
        Item[] netherite = { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS };
        int[] armor = { 4, 9, 7, 4 };
        for (int i = 0; i < pieces.length; i++) {
            ItemStack stack = new ItemStack(pieces[i]);
            helper.assertTrue(attribute(stack, Attributes.ARMOR) == armor[i], pieces[i] + " gives " + attribute(stack, Attributes.ARMOR) + " armour");
            helper.assertTrue(attribute(stack, Attributes.ARMOR_TOUGHNESS) == 4.0, pieces[i] + " gives " + attribute(stack, Attributes.ARMOR_TOUGHNESS) + " toughness");
            helper.assertTrue(Math.abs(attribute(stack, Attributes.KNOCKBACK_RESISTANCE) - 0.15) < 1.0E-6,
                    pieces[i] + " gives " + attribute(stack, Attributes.KNOCKBACK_RESISTANCE) + " knockback resistance");
            helper.assertTrue(stack.getMaxDamage() > new ItemStack(netherite[i]).getMaxDamage(), pieces[i] + " wears out before netherite");
            helper.assertTrue(stack.isValidRepairItem(new ItemStack(ModItems.ARCFORGED_ALLOY.get())), pieces[i] + " doesn't repair with Arcforged Alloy");
            helper.assertTrue(stack.is(ItemTags.TRIMMABLE_ARMOR), pieces[i] + " isn't trimmable");
            helper.assertTrue(stack.is(ItemTags.ARMOR_ENCHANTABLE), pieces[i] + " isn't enchantable as armour");
        }
        helper.succeed();
    }

    // While charged, wear costs 100 FE a point and no durability; what the charge can't cover wears the item; empty, it
    // wears like any tool. Once a second a damaged piece restores a point from its charge.
    static void feWear(GameTestHelper helper) {
        int per = ArcforgeConfig.ARCFORGED_FE_PER_DURABILITY.getAsInt();
        ItemStack pickaxe = new ItemStack(ModItems.ARCFORGED_PICKAXE.get());
        ArcToolItem.setEnergy(pickaxe, per * 10);
        pickaxe.hurtAndBreak(3, helper.getLevel(), null, broken -> {});
        helper.assertTrue(pickaxe.getDamageValue() == 0, "A charged pickaxe took " + pickaxe.getDamageValue() + " damage");
        helper.assertTrue(ArcToolItem.energy(pickaxe) == per * 7, "Wear took " + (per * 10 - ArcToolItem.energy(pickaxe)) + " FE, not " + per * 3);
        pickaxe.hurtAndBreak(9, helper.getLevel(), null, broken -> {});
        helper.assertTrue(pickaxe.getDamageValue() == 2 && ArcToolItem.energy(pickaxe) == 0,
                "With 7 points of charge, 9 wear left " + pickaxe.getDamageValue() + " damage and " + ArcToolItem.energy(pickaxe) + " FE");
        pickaxe.hurtAndBreak(4, helper.getLevel(), null, broken -> {});
        helper.assertTrue(pickaxe.getDamageValue() == 6, "Empty, 4 wear left " + pickaxe.getDamageValue() + " damage");
        // Two points of charge repair two of the six points of damage (the rate allows more).
        ArcToolItem.setEnergy(pickaxe, per * 2);
        ArcforgedGear.selfRepair(pickaxe, 20);
        helper.assertTrue(pickaxe.getDamageValue() == 4 && ArcToolItem.energy(pickaxe) == 0,
                "Self-repair left " + pickaxe.getDamageValue() + " damage and " + ArcToolItem.energy(pickaxe) + " FE");
        ArcToolItem.setEnergy(pickaxe, per * 2);
        ArcforgedGear.selfRepair(pickaxe, 21);
        helper.assertTrue(pickaxe.getDamageValue() == 4, "Self-repair ran off the second");
        helper.succeed();
    }

    // A chestplate in an Energy Cell's charge slot takes the charge rate a tick, and the bar and item model see it.
    static void chargesInEnergyCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.energyCell(ConduitTier.ARCFORGED).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            cell.getEnergyHandler(null).insert(10_000_000, tx);
            tx.commit();
        }
        ItemStack chestplate = new ItemStack(ModItems.ARCFORGED_CHESTPLATE.get());
        helper.assertTrue(ItemAccess.forStack(chestplate).getCapability(Capabilities.Energy.ITEM) != null, "The chestplate takes no FE");
        cell.getItems().setStack(StorageBlockEntity.SLOT_OUT, chestplate);
        int rate = ArcforgeConfig.ARCFORGED_RECEIVE.getAsInt();
        int[] last = { -1 };
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> last[0] = ArcToolItem.energy(cell.getItems().getStack(StorageBlockEntity.SLOT_OUT)))
                .thenIdle(1)
                .thenExecute(() -> {
                    ItemStack charging = cell.getItems().getStack(StorageBlockEntity.SLOT_OUT);
                    int now = ArcToolItem.energy(charging);
                    helper.assertTrue(now - last[0] == rate, "Charged " + (now - last[0]) + " FE in a tick, not " + rate);
                    helper.assertTrue(charging.has(net.zagdrath.arcforge.registry.ModDataComponents.ENERGY.get()) && ArcforgedGear.barWidth(charging) > 0,
                            "A charging piece has no energy component for its model");
                })
                .thenSucceed();
    }

    // The template, a damaged, enchanted, named netherite pickaxe and an Arcforged Alloy smith into an Arcforged pickaxe
    // with the same enchantment and name and the same share of wear; without the template nothing matches. The Steel
    // Hammer needs two Arcforged Alloy.
    static void smithingUpgrade(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack template = new ItemStack(ModItems.ARCFORGED_UPGRADE_TEMPLATE.get());
        ItemStack alloy = new ItemStack(ModItems.ARCFORGED_ALLOY.get());
        ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);
        Holder<Enchantment> efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        netherite.enchant(efficiency, 4);
        netherite.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        netherite.setDamageValue(netherite.getMaxDamage() / 2);
        SmithingRecipeInput input = new SmithingRecipeInput(template, netherite, alloy);
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
        helper.assertTrue(recipe.isPresent(), "No smithing recipe for a netherite pickaxe");
        ItemStack result = recipe.get().value().assemble(input);
        helper.assertTrue(result.is(ModItems.ARCFORGED_PICKAXE.get()), "Smithing made " + result);
        helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(efficiency) == 4, "Efficiency lost");
        helper.assertTrue(result.getHoverName().getString().equals("Old Faithful"), "Name lost");
        int expected = Math.round(result.getMaxDamage() / 2.0F);
        helper.assertTrue(Math.abs(result.getDamageValue() - expected) <= 1, "Wear " + result.getDamageValue() + " of " + result.getMaxDamage() + ", expected about " + expected);
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, netherite, alloy), level).isEmpty(),
                "The upgrade works without the template");
        ItemStack hammer = new ItemStack(ModItems.STEEL_HAMMER.get());
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(template, hammer, alloy), level).isEmpty(),
                "The Steel Hammer upgrades with one Arcforged Alloy");
        var two = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(template, hammer, alloy.copyWithCount(2)), level);
        helper.assertTrue(two.isPresent() && two.get().value().assemble(new SmithingRecipeInput(template, hammer, alloy.copyWithCount(2)))
                .is(ModItems.ARCFORGED_HAMMER.get()), "Two Arcforged Alloy don't upgrade the Steel Hammer");
        helper.succeed();
    }

    // A template, seven Iolite Crystals and a Block of Steel craft two templates.
    static void templateDuplicates(GameTestHelper helper) {
        var level = helper.getLevel();
        Map<Character, ItemStack> key = Map.of('I', new ItemStack(ModItems.IOLITE_CRYSTAL.get()), 'T', new ItemStack(ModItems.ARCFORGED_UPGRADE_TEMPLATE.get()),
                'S', new ItemStack(ModItems.STEEL_BLOCK.get()));
        List<ItemStack> grid = new ArrayList<>();
        for (String row : new String[] { "ITI", "ISI", "III" }) {
            for (char c : row.toCharArray()) {
                grid.add(key.get(c).copy());
            }
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(), "The template doesn't duplicate");
        ItemStack result = recipe.get().value().assemble(input);
        helper.assertTrue(result.is(ModItems.ARCFORGED_UPGRADE_TEMPLATE.get()) && result.getCount() == 2, "Duplicating made " + result);
        helper.succeed();
    }

    // As items, every piece is fire resistant and survives explosions; piglins don't take it for gold.
    static void survivesFireAndExplosions(GameTestHelper helper) {
        var sources = helper.getLevel().damageSources();
        for (var holder : ModItems.arcforgedGear()) {
            ItemStack stack = new ItemStack(holder.get());
            for (DamageSource source : new DamageSource[] { sources.inFire(), sources.lava(), sources.explosion(null, null) }) {
                helper.assertFalse(stack.getItem().canBeHurtBy(stack, source), holder.get() + " is hurt by " + source.getMsgId());
            }
            helper.assertFalse(stack.is(ItemTags.PIGLIN_LOVED), holder.get() + " is piglin-loved");
            helper.assertFalse(stack.isPiglinCurrency(), holder.get() + " is piglin currency");
        }
        helper.succeed();
    }
}
