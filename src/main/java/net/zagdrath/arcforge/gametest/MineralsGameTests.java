/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.SifterMeshItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SiftingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Sifter, the Diamond Press and biogas sulfur: the sifting recipes (what each block gives, nothing ever Arcite, and
// every chance well below mining even with the best mesh), Deepslate Gravel from the Arc Crusher, Graphite into a
// Diamond; the Sifter needing a mesh, sifting with one and wearing it out; the Diamond Press needing 1,400°C, then
// pressing a Diamond; the Biogas Digester scrubbing Sulfur Dust out of its Biogas and giving it out of a Sulfur port.
public final class MineralsGameTests {
    private MineralsGameTests() {}

    private static boolean gives(SiftingRecipe recipe, net.minecraft.world.item.Item item) {
        return recipe.outputs().stream().anyMatch(output -> output.item().create().is(item));
    }

    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var gravel = MachineRecipes.sifting(level, new ItemStack(Items.GRAVEL)).orElseThrow().value();
        for (var item : new net.minecraft.world.item.Item[] { Items.FLINT, ModItems.IRON_DUST.get(), ModItems.COPPER_DUST.get(), ModItems.GOLD_DUST.get(),
                ModItems.SILVER_DUST.get(), ModItems.NICKEL_DUST.get() }) {
            helper.assertTrue(gives(gravel, item), "Gravel doesn't sift into " + item);
        }
        for (var sand : new net.minecraft.world.item.Item[] { Items.SAND, Items.RED_SAND }) {
            var recipe = MachineRecipes.sifting(level, new ItemStack(sand)).orElseThrow().value();
            for (var item : new net.minecraft.world.item.Item[] { ModItems.GOLD_DUST.get(), Items.QUARTZ, ModItems.FLUORITE_DUST.get(),
                    ModItems.BISMUTH_DUST.get() }) {
                helper.assertTrue(gives(recipe, item), sand + " doesn't sift into " + item);
            }
        }
        var deep = MachineRecipes.sifting(level, new ItemStack(ModItems.DEEPSLATE_GRAVEL.get())).orElseThrow().value();
        helper.assertTrue(gives(deep, ModItems.TUNGSTEN_DUST.get()), "Deepslate Gravel doesn't sift into tungsten dust");
        double best = Math.max(ArcforgeConfig.SIFTER_TUNGSTEN_CHANCE.getAsDouble(), ArcforgeConfig.SIFTER_INVAR_CHANCE.getAsDouble());
        for (var holder : level.recipeAccess().recipeMap().byType(ModRecipes.SIFTING.get())) {
            double metal = 0;
            for (var output : holder.value().outputs()) {
                var id = BuiltInRegistries.ITEM.getKey(output.item().create().getItem());
                helper.assertTrue(!id.getPath().contains("arcite"), holder.id() + " sifts Arcite");
                if (id.getPath().endsWith("_dust")) {
                    metal += Math.min(1.0, output.chance() * best);
                }
            }
            // Well below mining: an ore gives two dust; a block sifted with the best mesh gives under half a dust.
            helper.assertTrue(metal < 0.5, holder.id() + " gives " + metal + " dust a block");
        }
        var crushed = MachineRecipes.crushing(level, new ItemStack(Items.COBBLED_DEEPSLATE));
        helper.assertTrue(crushed.isPresent() && crushed.get().value().result().map(r -> r.create().is(ModItems.DEEPSLATE_GRAVEL.get())).orElse(false),
                "Cobbled deepslate doesn't crush into Deepslate Gravel");
        var diamond = MachineRecipes.diamondPressing(level, new ItemStack(ModItems.GRAPHITE.get()));
        helper.assertTrue(diamond.isPresent() && diamond.get().value().result().create().is(Items.DIAMOND), "Graphite doesn't press into a Diamond");
        helper.assertTrue(diamond.get().value().minTemperatureOrDefault() >= 1_400, "The Diamond Press works under 1,400°C");
        helper.succeed();
    }

    // No mesh, no sifting; with a Tungsten Mesh it sifts four gravel, wearing the mesh a point each; a worn-out mesh breaks.
    static void sifterSifts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.SIFTER.get());
        SifterBlockEntity sifter = helper.getBlockEntity(pos, SifterBlockEntity.class);
        CrushingGameTests.charge(sifter.getEnergy(), 20_000);
        helper.assertTrue(CrushingGameTests.insert(sifter.getItemHandler(Direction.UP), Items.GRAVEL, 4) == 4, "It refused gravel");
        helper.assertTrue(CrushingGameTests.insert(sifter.getItemHandler(Direction.UP), Items.COBBLESTONE, 1) == 0, "It took cobblestone");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(sifter.getStatus() == MachineStatus.NO_MESH, "Without a mesh it's " + sifter.getStatus());
                    helper.assertTrue(CrushingGameTests.insert(sifter.getItemHandler(Direction.UP), ModItems.TUNGSTEN_MESH.get(), 1) == 1,
                            "The mesh didn't go in through the top");
                    helper.assertTrue(SifterMeshItem.tierOf(sifter.getItems().getStack(SifterBlockEntity.SLOT_MESH)) == SifterMeshItem.Tier.TUNGSTEN,
                            "The mesh isn't in the mesh slot");
                })
                .thenWaitUntil(() -> helper.assertTrue(sifter.getItems().getStack(SifterBlockEntity.SLOT_INPUT).isEmpty(), "Gravel left"))
                .thenExecute(() -> {
                    ItemStack mesh = sifter.getItems().getStack(SifterBlockEntity.SLOT_MESH);
                    if (ArcforgeConfig.SIFTER_MESH_WEAR_CHANCE.getAsDouble() >= 1.0) {
                        helper.assertTrue(mesh.getDamageValue() == 4, "The mesh wore " + mesh.getDamageValue() + ", not 4");
                    }
                    for (int i = 0; i < SifterBlockEntity.OUTPUT_SLOTS; i++) {
                        ItemStack found = sifter.getItems().getStack(SifterBlockEntity.FIRST_OUTPUT + i);
                        helper.assertTrue(found.isEmpty() || found.getCount() <= 4, "Too many finds: " + found);
                    }
                    // One point from breaking: the next block breaks it.
                    mesh = mesh.copy();
                    mesh.setDamageValue(mesh.getMaxDamage() - 1);
                    sifter.getItems().setStack(SifterBlockEntity.SLOT_MESH, mesh);
                    sifter.getItems().setStack(SifterBlockEntity.SLOT_INPUT, new ItemStack(Items.GRAVEL));
                })
                .thenWaitUntil(() -> helper.assertTrue(sifter.getItems().getStack(SifterBlockEntity.SLOT_INPUT).isEmpty(), "The last gravel is left"))
                .thenExecute(() -> {
                    if (ArcforgeConfig.SIFTER_MESH_WEAR_CHANCE.getAsDouble() >= 1.0) {
                        helper.assertTrue(sifter.getItems().getStack(SifterBlockEntity.SLOT_MESH).isEmpty(), "The worn-out mesh didn't break");
                    }
                })
                .thenSucceed();
    }

    // Cold it waits; kept fed with FE and heat past 1,400°C (8 Speed upgrades), it presses 4 Graphite into a Diamond.
    static void diamondPress(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.DIAMOND_PRESS.get());
        DiamondPressBlockEntity press = helper.getBlockEntity(pos, DiamondPressBlockEntity.class);
        CrushingGameTests.install(press.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        var recipe = MachineRecipes.diamondPressing(helper.getLevel(), new ItemStack(ModItems.GRAPHITE.get())).orElseThrow().value();
        press.getItems().setStack(DiamondPressBlockEntity.SLOT_INPUT, new ItemStack(ModItems.GRAPHITE.get(), recipe.inputCount()));
        boolean[] heating = new boolean[1];
        helper.onEachTick(() -> {
            CrushingGameTests.charge(press.getEnergy(), ArcforgeConfig.DIAMOND_PRESS_MAX_INPUT.getAsInt());
            if (heating[0]) {
                press.getHeat().add(press.getHeat().getCapacity());
            }
        });
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(press.getStatus() == MachineStatus.TOO_COLD, "A cold Diamond Press is " + press.getStatus());
                    heating[0] = true;
                })
                .thenWaitUntil(() -> helper.assertTrue(press.getItems().getStack(DiamondPressBlockEntity.SLOT_OUTPUT).is(Items.DIAMOND),
                        "No Diamond yet (" + press.getStatus() + ")"))
                .thenExecute(() -> helper.assertTrue(press.getItems().getStack(DiamondPressBlockEntity.SLOT_INPUT).isEmpty(), "It didn't use the Graphite"))
                .thenSucceed();
    }

    // 4,000 mB of Biogas scrubbed gives 2 Sulfur Dust (at the default 0.5 per 1,000 mB), taken out through a Sulfur port.
    static void biogasSulfur(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        helper.setBlock(pos, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get());
        BiogasDigesterBlockEntity digester = helper.getBlockEntity(pos, BiogasDigesterBlockEntity.class);
        double rate = ArcforgeConfig.DIGESTER_SULFUR_PER_THOUSAND_MB.getAsDouble();
        digester.scrub(4_000);
        int expected = (int) (4.0 * rate);
        ItemStack sulfur = digester.getSulfur();
        helper.assertTrue(expected == 0 ? sulfur.isEmpty() : sulfur.is(ModItems.SULFUR_DUST.get()) && sulfur.getCount() == expected,
                "Scrubbed " + sulfur + ", expected " + expected + " Sulfur Dust");
        var port = digester.getItemHandler(SideMode.SULFUR);
        helper.assertTrue(port != null, "No Sulfur port handler");
        if (expected > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(port.extract(ItemResource.of(ModItems.SULFUR_DUST.get()), expected, tx) == expected, "The Sulfur port doesn't give it out");
            }
        }
        helper.assertTrue(SideMode.SULFUR.isOutput(), "Sulfur ports don't auto-eject");
        helper.succeed();
    }
}
