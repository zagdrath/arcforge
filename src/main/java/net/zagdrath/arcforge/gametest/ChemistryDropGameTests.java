/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.machine.ExperienceBank;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The Chemical Reactor Array, the silica line, the Electric Enchanter and banked Liquid Experience. Machines face north.
public final class ChemistryDropGameTests {
    private ChemistryDropGameTests() {}

    private static Holder<Enchantment> enchantment(GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static void fill(net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank tank, FluidResource fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            tank.insert(0, fluid, amount, tx);
            tx.commit();
        }
    }

    // A bank keeps fractions until they make a point (20 mB), and once full takes nothing more, handing the rest back.
    static void experienceBankBanks(GameTestHelper helper) {
        ExperienceBank bank = new ExperienceBank(100, () -> {});
        helper.assertTrue(bank.bank(0.7F) == 0.0F && bank.getAmount() == 0, "A fraction was banked as a point");
        helper.assertTrue(bank.bank(0.7F) == 0.0F && bank.getAmount() == LiquidExperience.MB_PER_POINT,
                "1.4 points banked " + bank.getAmount() + " mB, expected 20");
        float rest = bank.bank(10.0F);
        helper.assertTrue(bank.getAmount() == 100, "A full bank holds " + bank.getAmount() + " mB");
        helper.assertTrue(Math.abs(rest - 6.4F) < 0.01F, "A full bank handed back " + rest + " points, expected 6.4");
        helper.succeed();
    }

    // Flint crushes to 2 Silica Dust, Quartz to 4; Silica smelts into Glass; Ferrosilicon is a steel flux; the blend
    // sinters into Silicon Carbide.
    static void silicaRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack flint = MachineRecipes.crushing(level, new ItemStack(Items.FLINT)).orElseThrow().value().result().orElseThrow().create();
        helper.assertTrue(flint.is(ModItems.SILICA_DUST.get()) && flint.getCount() == 2, "Flint crushes into " + flint);
        ItemStack quartz = MachineRecipes.crushing(level, new ItemStack(Items.QUARTZ)).orElseThrow().value().result().orElseThrow().create();
        helper.assertTrue(quartz.is(ModItems.SILICA_DUST.get()) && quartz.getCount() == 4, "Quartz crushes into " + quartz);
        ItemStack glass = MachineRecipes.smelting(level, new ItemStack(ModItems.SILICA_DUST.get())).orElseThrow().value()
                .assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ModItems.SILICA_DUST.get())));
        helper.assertTrue(glass.is(Items.GLASS), "Silica Dust smelts into " + glass);
        var steel = MachineRecipes.arcforgeSmelting(level, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(ModItems.FERROSILICON.get()));
        helper.assertTrue(steel.isPresent() && steel.get().value().result().create().is(ModItems.STEEL_INGOT.get()), "Ferrosilicon isn't a steel flux");
        ItemStack carbide = MachineRecipes.sintering(level, new ItemStack(ModItems.SILICON_CARBIDE_BLEND.get())).orElseThrow().value().result().create();
        helper.assertTrue(carbide.is(ModItems.SILICON_CARBIDE.get()), "The blend sinters into " + carbide);
        var silicon = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.SILICA_DUST.get(), 2), ItemStack.EMPTY);
        helper.assertTrue(silicon.isPresent() && silicon.get().value().minHeat() == 1_500
                && silicon.get().value().result().create().is(ModItems.METALLURGICAL_SILICON.get()), "No 1,500°C Metallurgical Silicon smelt");
        helper.succeed();
    }

    // A top-up pays only for the levels it gains; an item that matches pays nothing; clashing targets are left out.
    static void enchanterCosts(GameTestHelper helper) {
        Holder<Enchantment> efficiency = enchantment(helper, Enchantments.EFFICIENCY);
        Holder<Enchantment> silk = enchantment(helper, Enchantments.SILK_TOUCH);
        Holder<Enchantment> fortune = enchantment(helper, Enchantments.FORTUNE);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.enchant(efficiency, 2);
        ElectricEnchanterBlockEntity.Plan plan = ElectricEnchanterBlockEntity.plan(pick, List.of(Map.entry(efficiency, 3)));
        helper.assertTrue(plan.lapis() == 3, "Efficiency II to III costs " + plan.lapis() + " Lapis, expected 3");
        helper.assertTrue(plan.xp() == ElectricEnchanterBlockEntity.xpForLevel(efficiency, 3), "Efficiency II to III costs " + plan.xp() + " mB");
        int points = LiquidExperience.pointsForLevels(efficiency.value().getMinCost(3));
        helper.assertTrue(plan.xp() == points * LiquidExperience.MB_PER_POINT, "Level 3's cost isn't its vanilla level cost at 20 mB a point");
        helper.assertTrue(ElectricEnchanterBlockEntity.plan(pick, List.of(Map.entry(efficiency, 2))).isEmpty(), "A matching item has something to gain");
        pick.enchant(fortune, 1);
        helper.assertTrue(ElectricEnchanterBlockEntity.plan(pick, List.of(Map.entry(silk, 1))).isEmpty(), "Silk Touch planned onto a Fortune pick");
        helper.assertTrue(ElectricEnchanterBlockEntity.plan(new ItemStack(Items.IRON_PICKAXE), List.of(
                Map.entry(enchantment(helper, Enchantments.SHARPNESS), 1))).isEmpty(), "Sharpness planned onto a pickaxe");
        helper.succeed();
    }

    // Set to Efficiency III and Unbreaking II, with FE, Lapis and Liquid Experience: a pickaxe comes out with both (its
    // costs drawn), a Book comes out an Enchanted Book, and a pickaxe that already has them passes straight through.
    static void enchanterEnchants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ELECTRIC_ENCHANTER.get());
        ElectricEnchanterBlockEntity enchanter = helper.getBlockEntity(pos, ElectricEnchanterBlockEntity.class);
        Holder<Enchantment> efficiency = enchantment(helper, Enchantments.EFFICIENCY);
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);
        helper.assertTrue(enchanter.setTarget(Enchantments.EFFICIENCY, 3) && enchanter.setTarget(Enchantments.UNBREAKING, 2), "Target refused");
        helper.assertTrue(!enchanter.setTarget(Enchantments.MENDING, 1), "Mending offered with treasure off");
        FiberGameTests.charge(enchanter.getEnergy(), enchanter.getEnergy().getCapacityAsInt());
        fill(enchanter.getTank(), LiquidExperience.resource(), enchanter.getTank().getCapacity());
        enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_LAPIS, new ItemStack(Items.LAPIS_LAZULI, 64));
        enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_PICKAXE));
        int xpBefore = enchanter.getTank().getAmount();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT).isEmpty(), "Nothing enchanted yet"))
                .thenExecute(() -> {
                    ItemStack out = enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT);
                    var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(out);
                    helper.assertTrue(out.is(Items.IRON_PICKAXE) && enchantments.getLevel(efficiency) == 3 && enchantments.getLevel(unbreaking) == 2,
                            "Made " + out + " " + enchantments);
                    int lapis = 64 - enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_LAPIS).getCount();
                    helper.assertTrue(lapis == 1 + 2 + 3 + 1 + 2, "Used " + lapis + " Lapis, expected 9");
                    int expected = 0;
                    for (int level = 1; level <= 3; level++) {
                        expected += ElectricEnchanterBlockEntity.xpForLevel(efficiency, level);
                    }
                    for (int level = 1; level <= 2; level++) {
                        expected += ElectricEnchanterBlockEntity.xpForLevel(unbreaking, level);
                    }
                    helper.assertTrue(xpBefore - enchanter.getTank().getAmount() == expected,
                            "Used " + (xpBefore - enchanter.getTank().getAmount()) + " mB, expected " + expected);
                    // A pickaxe that has it all goes straight through.
                    enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_INPUT, out.copy());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_INPUT).isEmpty(), "A finished pickaxe didn't pass through");
                    helper.assertTrue(enchanter.getProgress() == 0, "A finished pickaxe was worked on");
                    enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    // Topped up: the book costs as much as the pickaxe did.
                    fill(enchanter.getTank(), LiquidExperience.resource(), enchanter.getTank().getCapacity());
                    enchanter.getItems().setStack(ElectricEnchanterBlockEntity.SLOT_INPUT, new ItemStack(Items.BOOK));
                })
                .thenWaitUntil(() -> helper.assertTrue(enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT).is(Items.ENCHANTED_BOOK),
                        "No Enchanted Book yet"))
                .thenExecute(() -> {
                    ItemStack book = enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(efficiency) == 3, "The book doesn't store Efficiency III");
                    helper.assertTrue(enchanter.getStatus() != MachineStatus.NO_EXPERIENCE, "Ran out of Liquid Experience");
                })
                .thenSucceed();
    }

    // A formed array runs a Brine lane in each of its three lanes at once, at half a reactor's time, off one shared water
    // tank into its shared output tank.
    static void reactorArrayRunsThreeLanes(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 0), ModBlocks.CHEMICAL_REACTOR_ARRAY_CASING.get());
        BlockPos center = new BlockPos(1, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(center).getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.CENTER, "Array did not form");
                    ChemicalReactorArrayBlockEntity array = helper.getBlockEntity(center, ChemicalReactorArrayBlockEntity.class);
                    FiberGameTests.charge(array.getEnergy(), 100_000);
                    fill(array.getTank(0), FluidResource.of(Fluids.WATER), 1_000);
                    for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
                        array.getItems().setStack(ChemicalReactorArrayBlockEntity.slot(lane, ChemicalReactorArrayBlockEntity.INPUT_A),
                                new ItemStack(ModItems.SALT.get()));
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ChemicalReactorArrayBlockEntity array = helper.getBlockEntity(center, ChemicalReactorArrayBlockEntity.class);
                    for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
                        helper.assertTrue(array.getTotal(lane) == 30, "Lane " + lane + " takes " + array.getTotal(lane) + " ticks, expected 30");
                        helper.assertTrue(array.isLaneRunning(lane), "Lane " + lane + " isn't running");
                    }
                    helper.assertTrue(array.getStatus() == MachineStatus.REACTING, "Array is " + array.getStatus());
                })
                .thenWaitUntil(() -> {
                    ChemicalReactorArrayBlockEntity array = helper.getBlockEntity(center, ChemicalReactorArrayBlockEntity.class);
                    helper.assertTrue(array.getTank(3).getAmount() == 750, "Made " + array.getTank(3).getAmount() + " mB of brine, expected 750");
                })
                .thenExecute(() -> {
                    ChemicalReactorArrayBlockEntity array = helper.getBlockEntity(center, ChemicalReactorArrayBlockEntity.class);
                    helper.assertTrue(array.getTank(3).getResource(0).getFluid() == ModFluids.BRINE.get(), "Output isn't brine");
                    helper.assertTrue(array.getTank(0).getAmount() == 250, "Water left: " + array.getTank(0).getAmount() + " mB, expected 250");
                })
                .thenSucceed();
    }
}
