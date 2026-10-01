/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.chemistry.OreSlurry;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Sulfur, the Chemical Reactor and the leach-and-precipitate ore chain.
public final class ChemicalGameTests {
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private ChemicalGameTests() {}

    private static ChemicalReactorBlockEntity reactor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        return reactor;
    }

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int amount(FilteredFluidTank tank, Fluid fluid) {
        return tank.contains(fluid) ? tank.getAmount() : 0;
    }

    // Empties the output tank into the input tanks, as a pipe from one reactor to the next would.
    private static void moveOutputToInputs(ChemicalReactorBlockEntity reactor) {
        FilteredFluidTank output = reactor.getOutputTank();
        FluidResource fluid = output.getResource(0);
        int amount = output.getAmount();
        try (Transaction tx = Transaction.openRoot()) {
            output.extract(0, fluid, amount, tx);
            reactor.getRouter().insert(fluid, amount, tx);
            tx.commit();
        }
    }

    private static int count(ChemicalReactorBlockEntity reactor, int slot, Item item) {
        ItemStack stack = reactor.getItems().getStack(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    // 1 Sulfur Dust + 500 mB water make 500 mB Sulfuric Acid in 100 ticks, using up both.
    static void sulfuricAcid(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, POS);
        helper.assertTrue(CrushingGameTests.insert(reactor.getItemHandler(Direction.UP), ModItems.SULFUR_DUST.get(), 1) == 1, "Top face refused Sulfur Dust");
        helper.assertTrue(fill(reactor.getRouter(), Fluids.WATER, 500) == 500, "Router refused water");
        helper.startSequence()
                .thenIdle(105)
                .thenExecute(() -> {
                    helper.assertTrue(amount(reactor.getOutputTank(), ModFluids.SULFURIC_ACID.get()) == 500,
                            "Output holds " + reactor.getOutputTank().getAmount() + " mB, expected 500 mB of acid");
                    helper.assertTrue(reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT).isEmpty(), "Sulfur Dust wasn't used up");
                    helper.assertTrue(reactor.getInputA().getAmount() == 0 && reactor.getInputB().getAmount() == 0, "Water wasn't used up");
                })
                .thenSucceed();
    }

    // An Arc Crusher turns 1 gunpowder into 2 Sulfur Dust.
    static void crushGunpowder(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(POS, ArcCrusherBlockEntity.class);
        CrushingGameTests.charge(crusher.getEnergy(), 20_000);
        CrushingGameTests.insert(crusher.getItemHandler(Direction.UP), Items.GUNPOWDER, 1);
        helper.startSequence()
                .thenIdle(105)
                .thenExecute(() -> {
                    ItemStack out = crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(ModItems.SULFUR_DUST.get()) && out.getCount() == 2, "Gunpowder gave " + out);
                })
                .thenSucceed();
    }

    // Netherrack has no main result: all 20 are crushed, the main output stays empty, and Sulfur Dust only ever
    // turns up as the bonus (15% each, so at most 20). Speed upgrades keep it short, Energy upgrades
    // keep it within one charge.
    static void crushNetherrack(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(POS, ArcCrusherBlockEntity.class);
        CrushingGameTests.install(crusher.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        CrushingGameTests.install(crusher.getItems(), ModItems.ENERGY_UPGRADE.get(), 8);
        CrushingGameTests.charge(crusher.getEnergy(), 20_000);
        helper.assertTrue(CrushingGameTests.insert(crusher.getItemHandler(Direction.UP), Items.NETHERRACK, 20) == 20, "Crusher refused netherrack");
        helper.onEachTick(() -> helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).isEmpty(),
                "Netherrack made a main output: " + crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT)));
        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).isEmpty(), "Netherrack left over");
                    ItemStack bonus = crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_BONUS);
                    helper.assertTrue(bonus.isEmpty() || bonus.is(ModItems.SULFUR_DUST.get()) && bonus.getCount() <= 20, "Bonus slot holds " + bonus);
                })
                .thenSucceed();
    }

    // Water goes to tank A (again and again, never spilling into B), acid to B, a third fluid to C; lava is refused (no
    // recipe takes it), and a fourth fluid is refused while all three tanks are in use.
    static void tankRouting(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, POS);
        ResourceHandler<FluidResource> input = reactor.getFluidHandler(Direction.UP);
        helper.assertTrue(input != null, "Top face has no fluid input");
        helper.assertTrue(fill(input, Fluids.WATER, 1_000) == 1_000 && amount(reactor.getInputA(), Fluids.WATER) == 1_000, "Water didn't go to tank A");
        helper.assertTrue(fill(input, Fluids.WATER, 500) == 500 && amount(reactor.getInputA(), Fluids.WATER) == 1_500, "More water didn't join tank A");
        int overflow = fill(input, Fluids.WATER, 10_000);
        helper.assertTrue(reactor.getInputB().getAmount() == 0, "Water spilled into tank B (" + overflow + " mB went in)");
        helper.assertTrue(fill(input, ModFluids.SULFURIC_ACID.get(), 500) == 500 && amount(reactor.getInputB(), ModFluids.SULFURIC_ACID.get()) == 500,
                "Acid didn't go to tank B");
        helper.assertTrue(fill(input, Fluids.LAVA, 1_000) == 0, "Took lava");
        helper.assertTrue(fill(input, OreSlurry.IRON.fluid(), 100) == 100 && amount(reactor.getInputC(), OreSlurry.IRON.fluid()) == 100,
                "A third fluid didn't go to tank C");
        helper.assertTrue(fill(input, ModFluids.ETHANOL.get(), 100) == 0, "Took a fourth fluid");
        helper.succeed();
    }

    // 1 raw iron + 250 mB acid make 300 mB Iron Slurry.
    static void leachRawIron(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, POS);
        CrushingGameTests.insert(reactor.getItemHandler(null), Items.RAW_IRON, 1);
        fill(reactor.getRouter(), ModFluids.SULFURIC_ACID.get(), 250);
        helper.startSequence()
                .thenIdle(170)
                .thenExecute(() -> {
                    int slurry = amount(reactor.getOutputTank(), OreSlurry.IRON.fluid());
                    helper.assertTrue(slurry == 300, "Output holds " + slurry + " mB Iron Slurry, expected 300");
                    helper.assertTrue(reactor.getInputA().getAmount() == 0, "Acid left over");
                })
                .thenSucceed();
    }

    // 300 mB Iron Slurry + 300 mB water make 3 iron dust (3 x 40 ticks), with 0-3 Slag.
    static void precipitateIron(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, POS);
        fill(reactor.getRouter(), OreSlurry.IRON.fluid(), 300);
        fill(reactor.getRouter(), Fluids.WATER, 300);
        helper.startSequence()
                .thenIdle(125)
                .thenExecute(() -> {
                    int dust = count(reactor, ChemicalReactorBlockEntity.SLOT_OUTPUT, ModItems.IRON_DUST.get());
                    int slag = count(reactor, ChemicalReactorBlockEntity.SLOT_BYPRODUCT, ModItems.SLAG.get());
                    helper.assertTrue(dust == 3, "Made " + dust + " iron dust, expected 3");
                    helper.assertTrue(slag <= 3, "Made " + slag + " Slag");
                    helper.assertTrue(reactor.getInputA().getAmount() == 0 && reactor.getInputB().getAmount() == 0, "Fluid left over");
                })
                .thenSucceed();
    }

    // The whole chain, one reactor each, driven step by step: sulfur to acid, then 2 raw iron (or 1 iron ore) to
    // 600 mB slurry, then that to dust. Both come to exactly 6 iron dust: 3 per raw ore, 6 per ore block.
    static void chainYieldTriple(GameTestHelper helper) {
        ChemicalReactorBlockEntity raw = reactor(helper, new BlockPos(0, 1, 0));
        ChemicalReactorBlockEntity ore = reactor(helper, new BlockPos(2, 1, 0));
        List<ChemicalReactorBlockEntity> both = List.of(raw, ore);
        for (ChemicalReactorBlockEntity reactor : both) {
            CrushingGameTests.insert(reactor.getItemHandler(null), ModItems.SULFUR_DUST.get(), 1);
            fill(reactor.getRouter(), Fluids.WATER, 500);
        }
        helper.startSequence()
                .thenIdle(105)
                .thenExecute(() -> {
                    for (ChemicalReactorBlockEntity reactor : both) {
                        helper.assertTrue(amount(reactor.getOutputTank(), ModFluids.SULFURIC_ACID.get()) == 500, "No acid made");
                        moveOutputToInputs(reactor);
                        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
                    }
                    CrushingGameTests.insert(raw.getItemHandler(null), Items.RAW_IRON, 2);
                    CrushingGameTests.insert(ore.getItemHandler(null), Items.IRON_ORE, 1);
                })
                .thenIdle(330)
                .thenExecute(() -> {
                    for (ChemicalReactorBlockEntity reactor : both) {
                        int slurry = amount(reactor.getOutputTank(), OreSlurry.IRON.fluid());
                        helper.assertTrue(slurry == 600, "Leaching made " + slurry + " mB slurry, expected 600");
                        moveOutputToInputs(reactor);
                        fill(reactor.getRouter(), Fluids.WATER, 600);
                        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
                    }
                })
                .thenIdle(250)
                .thenExecute(() -> {
                    helper.assertTrue(count(raw, ChemicalReactorBlockEntity.SLOT_OUTPUT, ModItems.IRON_DUST.get()) == 6,
                            "2 raw iron gave " + raw.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT) + ", expected 6 iron dust");
                    helper.assertTrue(count(ore, ChemicalReactorBlockEntity.SLOT_OUTPUT, ModItems.IRON_DUST.get()) == 6,
                            "1 iron ore gave " + ore.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT) + ", expected 6 iron dust");
                })
                .thenSucceed();
    }

    private static ItemStack first(String tag) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", tag));
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private static @Nullable ChemicalReactingRecipe find(GameTestHelper helper, ChemicalReactorInput input) {
        return MachineRecipes.chemicalReacting(helper.getLevel(), input).map(holder -> holder.value()).orElse(null);
    }

    // Every leached metal leaches from its raw ore, ore block and raw block, and its slurry precipitates into its
    // dust. Fluorite and arcite (gems) don't leach.
    static void everyMetalHasAChain(GameTestHelper helper) {
        FluidResource acid = FluidResource.of(ModFluids.SULFURIC_ACID.get());
        for (OreSlurry slurry : OreSlurry.values()) {
            for (String tag : List.of("raw_materials/" + slurry.metal(), "ores/" + slurry.metal(), "storage_blocks/raw_" + slurry.metal())) {
                ItemStack item = first(tag);
                helper.assertTrue(!item.isEmpty(), "Nothing is tagged c:" + tag);
                ChemicalReactingRecipe recipe = find(helper, new ChemicalReactorInput(item, acid, 8_000, FluidResource.EMPTY, 0));
                helper.assertTrue(recipe != null && recipe.fluidOutput().isPresent() && recipe.fluidOutput().get().fluid().value() == slurry.fluid(),
                        item + " (c:" + tag + ") doesn't leach into " + slurry.fluidName());
            }
            ChemicalReactingRecipe precipitating = find(helper, new ChemicalReactorInput(ItemStack.EMPTY, FluidResource.of(slurry.fluid()), 8_000,
                    FluidResource.of(Fluids.WATER), 8_000));
            helper.assertTrue(precipitating != null && precipitating.itemOutput().isPresent() && precipitating.itemOutput().get().create().is(slurry.dust()),
                    slurry.fluidName() + " doesn't precipitate into " + slurry.dust());
        }
        for (Item gem : List.of(ModItems.RAW_FLUORITE.get(), ModItems.RAW_ARCITE.get())) {
            helper.assertTrue(find(helper, new ChemicalReactorInput(new ItemStack(gem), acid, 8_000, FluidResource.EMPTY, 0)) == null, gem + " leaches");
        }
        helper.succeed();
    }

    // 60 FE/t base; 8 Energy upgrades take it to 11 or less; 8 Speed upgrades make acid (100 ticks) take
    // UpgradeType.time(100, 8) = 6 ticks.
    static void upgrades(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper, POS);
        int base = reactor.energyPerTick();
        CrushingGameTests.install(reactor.getItems(), ModItems.ENERGY_UPGRADE.get(), 8);
        int efficient = reactor.energyPerTick();
        helper.assertTrue(base == 60 && efficient <= 11, "FE/t is " + base + " base, " + efficient + " with 8 Energy upgrades");
        CrushingGameTests.install(reactor.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        CrushingGameTests.insert(reactor.getItemHandler(null), ModItems.SULFUR_DUST.get(), 1);
        fill(reactor.getRouter(), Fluids.WATER, 500);
        int expected = UpgradeType.time(100, 8);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(reactor.getTotal() == expected, "Acid takes " + reactor.getTotal() + " ticks, expected " + expected))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(amount(reactor.getOutputTank(), ModFluids.SULFURIC_ACID.get()) == 500, "No acid after an upgraded run"))
                .thenSucceed();
    }

    // With a stone pickaxe the ore drops 2-4 Sulfur Dust; with Silk Touch, itself.
    static void netherSulfurOreDrops(GameTestHelper helper) {
        BlockState state = ModBlocks.NETHER_SULFUR_ORE.get().defaultBlockState();
        BlockPos pos = helper.absolutePos(POS);
        for (int i = 0; i < 10; i++) {
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, null, null, new ItemStack(Items.STONE_PICKAXE));
            int dust = drops.stream().filter(stack -> stack.is(ModItems.SULFUR_DUST.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(drops.size() == 1 && dust >= 2 && dust <= 4, "Stone pickaxe dropped " + drops);
        }
        ItemStack silk = new ItemStack(Items.STONE_PICKAXE);
        silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, null, null, silk);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModItems.NETHER_SULFUR_ORE.get()), "Silk Touch dropped " + drops);
        helper.succeed();
    }
}
