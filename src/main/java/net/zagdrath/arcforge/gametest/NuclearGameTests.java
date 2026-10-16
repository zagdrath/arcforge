/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadeControllerBlock;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadePart;
import net.zagdrath.arcforge.block.multiblock.GasCentrifugeBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirKind;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.nuclear.Corrosion;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.HazmatSuit;
import net.zagdrath.arcforge.nuclear.RadiationDose;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SiftingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.worldgen.ModWorldgen;

// The nuclear tier: the three ores (drops, crushing, Galena's silver, sifting Heavy Mineral Sand, the tags and the
// veins); the uranium chemistry (leaching to UF6, Hydrofluoric Acid and Fluorine, Depleted Uranium); the separative
// work numbers and the 20% cap; the Gas Centrifuge Cascade forming and enriching; UF6 refused by unlined storage and
// kept apart by assay; and radiation (inverse square, shielding, the Lead Box, the Hazmat Suit and the dose thresholds).
public final class NuclearGameTests {
    private NuclearGameTests() {}

    private static int fill(ResourceHandler<FluidResource> handler, FluidResource resource, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static FluidResource uf6(int pcm) {
        return Enrichment.withAssay(ModFluids.URANIUM_HEXAFLUORIDE.get(), pcm);
    }

    private static boolean tagged(Item item, String path) {
        return new ItemStack(item).is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path)));
    }

    private static boolean crushesInto(GameTestHelper helper, Item input, Item dust, int count) {
        return MachineRecipes.crushing(helper.getLevel(), new ItemStack(input))
                .flatMap(holder -> holder.value().result()).map(r -> r.create().is(dust) && r.count() == count).orElse(false);
    }

    private static boolean gives(SiftingRecipe recipe, Item item) {
        return recipe.outputs().stream().anyMatch(output -> output.item().create().is(item));
    }

    // --- Ores ---

    // The ores crush into two dust (the Arc Crushing Array doubles them, as "ore" recipes), raw ore and raw blocks crush
    // too, Galena gives Silver Dust one time in ten, Heavy Mineral Sand sifts into Monazite and Zircon and they crush
    // into dust; the c: tags hold everything.
    static void ores(GameTestHelper helper) {
        var level = helper.getLevel();
        for (Item ore : List.of(ModItems.URANINITE_ORE.get(), ModItems.DEEPSLATE_URANINITE_ORE.get())) {
            helper.assertTrue(crushesInto(helper, ore, ModItems.URANINITE_DUST.get(), 2), ore + " doesn't crush into 2 Uraninite Dust");
            helper.assertTrue(MachineRecipes.crushing(level, new ItemStack(ore)).orElseThrow().value().ore(), ore + " isn't doubled by the array");
        }
        helper.assertTrue(crushesInto(helper, ModItems.RAW_URANINITE.get(), ModItems.URANINITE_DUST.get(), 1)
                || crushesInto(helper, ModItems.RAW_URANINITE.get(), ModItems.URANINITE_DUST.get(), 2), "Raw Uraninite doesn't crush");
        var galena = MachineRecipes.crushing(level, new ItemStack(ModItems.GALENA_ORE.get())).orElseThrow().value();
        helper.assertTrue(galena.result().map(r -> r.create().is(ModItems.LEAD_DUST.get())).orElse(false), "Galena Ore doesn't crush into Lead Dust");
        helper.assertTrue(galena.bonus().map(b -> b.create().is(ModItems.SILVER_DUST.get())).orElse(false)
                && Math.abs(galena.bonusChance() - 0.1F) < 1.0E-4F, "Galena's bonus isn't a 10% chance of Silver Dust");
        var sand = MachineRecipes.sifting(level, new ItemStack(ModItems.HEAVY_MINERAL_SAND.get())).orElseThrow().value();
        helper.assertTrue(gives(sand, ModItems.MONAZITE.get()) && gives(sand, ModItems.ZIRCON.get()), "Heavy Mineral Sand doesn't sift into Monazite and Zircon");
        helper.assertTrue(crushesInto(helper, ModItems.MONAZITE.get(), ModItems.MONAZITE_DUST.get(), 1), "Monazite doesn't crush into dust");
        helper.assertTrue(crushesInto(helper, ModItems.ZIRCON.get(), ModItems.ZIRCON_DUST.get(), 1), "Zircon doesn't crush into dust");
        // Lead goes the way of the other metals: raw to ingot, ingot to plate and block.
        var ingot = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.LEAD_DUST.get()), ItemStack.EMPTY);
        var smelted = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ModItems.RAW_GALENA.get())), level);
        helper.assertTrue(smelted.isPresent() || ingot.isPresent(), "Raw Galena doesn't smelt into Lead");
        var plate = MachineRecipes.pressing(level, new ItemStack(ModItems.PLATE_DIE.get()), new ItemStack(ModItems.LEAD_INGOT.get()));
        helper.assertTrue(plate.isPresent() && plate.get().value().result().create().is(ModItems.LEAD_PLATE.get()), "A Lead Ingot doesn't press into a plate");
        // Tags.
        helper.assertTrue(tagged(ModItems.URANINITE_ORE.get(), "ores/uranium") && tagged(ModItems.DEEPSLATE_URANINITE_ORE.get(), "ores/uranium"),
                "Uraninite Ore isn't c:ores/uranium");
        helper.assertTrue(tagged(ModItems.GALENA_ORE.get(), "ores/lead") && tagged(ModItems.RAW_GALENA.get(), "raw_materials/lead")
                && tagged(ModItems.RAW_GALENA_BLOCK.get(), "storage_blocks/raw_lead"), "Galena's tags are wrong");
        helper.assertTrue(tagged(ModItems.RAW_URANINITE.get(), "raw_materials/uranium") && tagged(ModItems.RAW_URANINITE_BLOCK.get(), "storage_blocks/raw_uranium"),
                "Raw Uraninite's tags are wrong");
        helper.assertTrue(tagged(ModItems.LEAD_INGOT.get(), "ingots/lead") && tagged(ModItems.LEAD_DUST.get(), "dusts/lead")
                && tagged(ModItems.LEAD_PLATE.get(), "plates/lead") && tagged(ModItems.LEAD_BLOCK.get(), "storage_blocks/lead"), "Lead's tags are wrong");
        helper.assertTrue(tagged(ModItems.URANINITE_DUST.get(), "dusts/uraninite") && tagged(ModItems.MONAZITE.get(), "gems/monazite")
                && tagged(ModItems.ZIRCON.get(), "gems/zircon") && tagged(ModItems.DEPLETED_URANIUM_INGOT.get(), "ingots/depleted_uranium"),
                "The dust and gem tags are wrong");
        helper.succeed();
    }

    // The three veins are config ores: Uraninite into deepslate, Galena into stone, Heavy Mineral Sand into sand.
    static void veins(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0), max = new BlockPos(6, 7, 6);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        place(helper, "ore_galena");
        helper.assertTrue(count(helper, min, max, ModBlocks.GALENA_ORE.get()) > 0, "No Galena Ore placed");
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            helper.setBlock(pos.immutable(), Blocks.DEEPSLATE);
        }
        place(helper, "ore_uraninite");
        helper.assertTrue(count(helper, min, max, ModBlocks.DEEPSLATE_URANINITE_ORE.get()) > 0, "No Deepslate Uraninite Ore placed");
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            helper.setBlock(pos.immutable(), pos.getY() == 1 ? Blocks.STONE : Blocks.SAND);
        }
        place(helper, "ore_heavy_mineral_sand");
        helper.assertTrue(count(helper, min, max, ModBlocks.HEAVY_MINERAL_SAND.get()) > 0, "No Heavy Mineral Sand placed");
        for (String ore : List.of("uraninite", "galena", "heavyMineralSand")) {
            helper.assertTrue(ArcforgeConfig.ORES.get(ore) != null && new ModWorldgen.OreEnabled(ore).test(null), ore + " isn't on by default");
        }
        helper.succeed();
    }

    private static void place(GameTestHelper helper, String id) {
        ServerLevel level = helper.getLevel();
        var feature = level.registryAccess().lookupOrThrow(Registries.FEATURE)
                .getOrThrow(ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, id)));
        helper.assertTrue(feature.value() instanceof ModWorldgen.ConfigOre, id + " isn't a config ore feature");
        boolean placed = false;
        for (int attempt = 0; attempt < 8 && !placed; attempt++) {
            placed = feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(new BlockPos(3, 4, 3)));
        }
    }

    private static int count(GameTestHelper helper, BlockPos min, BlockPos max, net.minecraft.world.level.block.Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            found += helper.getBlockState(pos.immutable()).is(block) ? 1 : 0;
        }
        return found;
    }

    // --- Chemistry ---

    private static ChemicalReactingRecipe react(GameTestHelper helper, ChemicalReactorInput input, String what) {
        var recipe = MachineRecipes.chemicalReacting(helper.getLevel(), input);
        helper.assertTrue(recipe.isPresent(), "No recipe: " + what);
        return recipe.get().value();
    }

    private static boolean makesFluid(ChemicalReactingRecipe recipe, Fluid fluid) {
        return recipe.fluidOutput().map(out -> out.fluid().value() == fluid).orElse(false);
    }

    private static boolean makesItem(ChemicalReactingRecipe recipe, Item item) {
        return recipe.itemOutput().map(out -> out.create().is(item)).orElse(false);
    }

    // Uraninite Dust + Sulfuric Acid -> Uranyl Sulfate Solution; + Ammonia -> Ammonium Diuranate; calcined -> Yellowcake;
    // + Hydrogen -> UO2; + HF -> UF4; + Fluorine -> UF6. Fluorite Dust + Sulfuric Acid -> HF; HF electrolyses into
    // Hydrogen and Fluorine. Depleted UF6 + Hydrogen + Steam -> Depleted Uranium Dust (and HF back).
    static void chemistry(GameTestHelper helper) {
        var level = helper.getLevel();
        FluidResource acid = FluidResource.of(ModFluids.SULFURIC_ACID.get());
        var leach = react(helper, new ChemicalReactorInput(new ItemStack(ModItems.URANINITE_DUST.get()), acid, 1_000, FluidResource.EMPTY, 0), "leaching");
        helper.assertTrue(makesFluid(leach, ModFluids.URANYL_SULFATE_SOLUTION.get()), "Uraninite Dust doesn't leach into Uranyl Sulfate Solution");
        var adu = react(helper, new ChemicalReactorInput(ItemStack.EMPTY, FluidResource.of(ModFluids.URANYL_SULFATE_SOLUTION.get()), 1_000,
                FluidResource.of(ModFluids.AMMONIA.get()), 1_000), "precipitating");
        helper.assertTrue(makesItem(adu, ModItems.AMMONIUM_DIURANATE.get()), "Uranyl Sulfate and Ammonia don't make Ammonium Diuranate");
        var calcined = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ModItems.AMMONIUM_DIURANATE.get())), level);
        helper.assertTrue(calcined.isPresent() && calcined.get().value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(
                new ItemStack(ModItems.AMMONIUM_DIURANATE.get()))).is(ModItems.YELLOWCAKE.get()), "Ammonium Diuranate doesn't calcine into Yellowcake");
        FluidResource hydrogen = FluidResource.of(ModFluids.HYDROGEN.get());
        var dioxide = react(helper, new ChemicalReactorInput(new ItemStack(ModItems.YELLOWCAKE.get()), hydrogen, 1_000, FluidResource.EMPTY, 0), "UO2");
        helper.assertTrue(makesItem(dioxide, ModItems.URANIUM_DIOXIDE.get()), "Yellowcake and Hydrogen don't make Uranium Dioxide");
        var tetra = react(helper, new ChemicalReactorInput(new ItemStack(ModItems.URANIUM_DIOXIDE.get()), FluidResource.of(ModFluids.HYDROFLUORIC_ACID.get()),
                1_000, FluidResource.EMPTY, 0), "UF4");
        helper.assertTrue(makesItem(tetra, ModItems.URANIUM_TETRAFLUORIDE.get()), "UO2 and HF don't make Uranium Tetrafluoride");
        var hex = react(helper, new ChemicalReactorInput(new ItemStack(ModItems.URANIUM_TETRAFLUORIDE.get()), FluidResource.of(ModFluids.FLUORINE.get()),
                1_000, FluidResource.EMPTY, 0), "UF6");
        helper.assertTrue(makesFluid(hex, ModFluids.URANIUM_HEXAFLUORIDE.get()), "UF4 and Fluorine don't make UF6");
        var hf = react(helper, new ChemicalReactorInput(new ItemStack(ModItems.FLUORITE_DUST.get()), acid, 1_000, FluidResource.EMPTY, 0), "HF");
        helper.assertTrue(makesFluid(hf, ModFluids.HYDROFLUORIC_ACID.get()), "Fluorite Dust and Sulfuric Acid don't make Hydrofluoric Acid");
        var electrolysis = MachineRecipes.electrolyzing(level, FluidResource.of(ModFluids.HYDROFLUORIC_ACID.get())).orElse(null);
        helper.assertTrue(electrolysis != null && electrolysis.value().primary().fluid().value() == ModFluids.HYDROGEN.get()
                && electrolysis.value().secondary().map(s -> s.fluid().value() == ModFluids.FLUORINE.get()).orElse(false),
                "Hydrofluoric Acid doesn't electrolyse into Hydrogen and Fluorine");
        var du = react(helper, new ChemicalReactorInput(ItemStack.EMPTY, ItemStack.EMPTY,
                Enrichment.withAssay(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get(), Enrichment.DEFAULT_TAILS_PCM), 1_000, hydrogen, 1_000,
                FluidResource.of(ModFluids.STEAM.get()), 1_000), "Depleted Uranium");
        helper.assertTrue(makesItem(du, ModItems.DEPLETED_URANIUM_DUST.get()), "Depleted UF6, Hydrogen and Steam don't make Depleted Uranium Dust");
        var duIngot = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.DEPLETED_URANIUM_DUST.get()), ItemStack.EMPTY);
        var duSmelted = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ModItems.DEPLETED_URANIUM_DUST.get())), level);
        helper.assertTrue(duIngot.isPresent() || duSmelted.isPresent(), "Depleted Uranium Dust doesn't smelt into an ingot");
        helper.succeed();
    }

    // --- Enrichment ---

    // The separative work numbers for natural feed and 0.25% tails, the assay component, and the 20% cap.
    static void enrichmentMath(GameTestHelper helper) {
        double[][] expected = { { 3_000, 6.0, 3.8 }, { 5_000, 10.3, 7.9 }, { 20_000, 42.8, 41.6 } };
        for (double[] row : expected) {
            int product = (int) row[0];
            double feed = Enrichment.feedPerProduct(product, Enrichment.NATURAL_PCM, Enrichment.DEFAULT_TAILS_PCM);
            double swu = Enrichment.swuPerProduct(product, Enrichment.NATURAL_PCM, Enrichment.DEFAULT_TAILS_PCM);
            helper.assertTrue(Math.abs(feed - row[1]) < 0.06 && Math.abs(swu - row[2]) < 0.06,
                    Enrichment.percent(product) + ": " + feed + " feed and " + swu + " SWU a unit");
        }
        helper.assertTrue(Enrichment.assay(FluidResource.of(ModFluids.URANIUM_HEXAFLUORIDE.get())) == Enrichment.NATURAL_PCM,
                "UF6 without an assay isn't natural");
        helper.assertTrue(Enrichment.assay(uf6(30_000)) == Enrichment.MAX_PCM, "An assay over 20% wasn't clamped");
        helper.assertTrue(Enrichment.MAX_PCM == 20_000 && CentrifugeCascadeBlockEntity.maxTarget() <= Enrichment.MAX_PCM, "The cap is over 20%");
        helper.assertTrue(!uf6(3_000).equals(uf6(5_000)), "Two assays are the same resource");
        helper.succeed();
    }

    // A 3x3 hall: casings for the floor, the controller in the middle of the front, four centrifuges.
    static BlockPos buildCascade(GameTestHelper helper, BlockPos min) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(min.offset(x, 0, z), ModBlocks.CENTRIFUGE_CASCADE_CASING.get());
            }
        }
        BlockPos controller = min.offset(1, 0, 0);
        helper.setBlock(controller, ModBlocks.CENTRIFUGE_CASCADE_CONTROLLER.get().defaultBlockState()
                .setValue(CentrifugeCascadeControllerBlock.FACING, Direction.NORTH));
        for (BlockPos at : List.of(min.offset(1, 1, 1), min.offset(0, 1, 2), min.offset(1, 1, 2), min.offset(2, 1, 2))) {
            helper.setBlock(at, ModBlocks.GAS_CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(at.above(), ModBlocks.GAS_CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, DoubleBlockHalf.UPPER));
        }
        return controller;
    }

    // It forms with four centrifuges (their SWU summed), takes natural UF6, and makes product at the target and tails
    // at the tails assay; a centrifuge over the controller or a block inside breaks it.
    static void cascadeEnriches(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        BlockPos controller = buildCascade(helper, min);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CentrifugeCascadeBlockEntity cascade = helper.getBlockEntity(controller, CentrifugeCascadeBlockEntity.class);
                    helper.assertTrue(cascade.isFormed(), "The 3x3 hall didn't form");
                    helper.assertTrue(cascade.getCentrifuges() == 4, cascade.getCentrifuges() + " centrifuges");
                    helper.assertTrue(Math.abs(cascade.swuPerTick() - 4 * ArcforgeConfig.CASCADE_SWU_PER_CENTRIFUGE.getAsDouble()) < 1.0E-9,
                            "SWU " + cascade.swuPerTick());
                    helper.assertTrue(helper.getBlockState(min.offset(0, 0, 2)).getValue(CentrifugeCascadePart.FORMED), "A casing isn't formed");
                    cascade.setTarget(3_000);
                    cascade.setTailsAssay(Enrichment.DEFAULT_TAILS_PCM);
                    cascade.setTarget(99_000);
                    helper.assertTrue(cascade.getTarget() <= Enrichment.MAX_PCM, "The target went to " + cascade.getTarget());
                    cascade.setTarget(3_000);
                    helper.assertTrue(fill(cascade.getFeed(), uf6(Enrichment.NATURAL_PCM), 2_000) == 2_000, "It refused natural UF6");
                    helper.assertTrue(fill(cascade.getFeed(), FluidResource.of(ModFluids.HYDROGEN.get()), 100) == 0, "It took Hydrogen as feed");
                    CrushingGameTests.charge(cascade.getEnergy(), ArcforgeConfig.CASCADE_ENERGY_CAPACITY.getAsInt());
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, CentrifugeCascadeBlockEntity.class).getProduct().getAmount() > 0,
                        "No product yet"))
                .thenExecute(() -> {
                    CentrifugeCascadeBlockEntity cascade = helper.getBlockEntity(controller, CentrifugeCascadeBlockEntity.class);
                    helper.assertTrue(cascade.getStatus() == MachineStatus.ENRICHING, "Status " + cascade.getStatus());
                    helper.assertTrue(Enrichment.assay(cascade.getProduct().getResource(0)) == 3_000,
                            "Product at " + Enrichment.percent(Enrichment.assay(cascade.getProduct().getResource(0))));
                    helper.assertTrue(cascade.getTails().getResource(0).is(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get())
                            && Enrichment.assay(cascade.getTails().getResource(0)) == Enrichment.DEFAULT_TAILS_PCM, "The tails are wrong");
                    int product = cascade.getProduct().getAmount();
                    int used = 2_000 - cascade.getFeed().getAmount();
                    helper.assertTrue(Math.abs(used - product * 6.0) <= 7.0, used + " mB feed for " + product + " mB product");
                    helper.assertTrue(helper.getBlockState(controller).getValue(CentrifugeCascadeControllerBlock.LIT), "The controller isn't lit");
                    // Stone inside the hall breaks it.
                    helper.setBlock(min.offset(0, 1, 1), Blocks.STONE);
                })
                .thenExecute(() -> {
                    // The hall looks again every 2 seconds (a block placed in it isn't a part, so nothing asks sooner).
                    CentrifugeCascadeBlockEntity cascade = helper.getBlockEntity(controller, CentrifugeCascadeBlockEntity.class);
                    cascade.checkNow();
                    helper.assertFalse(cascade.isFormed(), "Still formed with stone inside");
                })
                .thenSucceed();
    }

    // A 5x4 floor forms as one 5x4 hall, not a 3x3 inside it: the centrifuges on its outer column and back row form too.
    static void cascadeFormsWholeFloor(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 1);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                helper.setBlock(min.offset(x, 0, z), ModBlocks.CENTRIFUGE_CASCADE_CASING.get());
            }
        }
        BlockPos controller = min.offset(2, 0, 0);
        helper.setBlock(controller, ModBlocks.CENTRIFUGE_CASCADE_CONTROLLER.get().defaultBlockState()
                .setValue(CentrifugeCascadeControllerBlock.FACING, Direction.NORTH));
        List<BlockPos> centrifuges = List.of(min.offset(1, 1, 1), min.offset(4, 1, 0), min.offset(4, 1, 2), min.offset(0, 1, 3), min.offset(4, 1, 3));
        for (BlockPos at : centrifuges) {
            helper.setBlock(at, ModBlocks.GAS_CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(at.above(), ModBlocks.GAS_CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, DoubleBlockHalf.UPPER));
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CentrifugeCascadeBlockEntity cascade = helper.getBlockEntity(controller, CentrifugeCascadeBlockEntity.class);
                    helper.assertTrue(cascade.isFormed(), "The 5x4 hall didn't form");
                    helper.assertTrue(cascade.getBox().sizeX() == 5 && cascade.getBox().sizeZ() == 4, "It formed " + cascade.getBox().sizeText());
                    helper.assertTrue(cascade.getCentrifuges() == centrifuges.size(), cascade.getCentrifuges() + " centrifuges");
                    for (BlockPos at : centrifuges) {
                        helper.assertTrue(helper.getBlockState(at).getValue(CentrifugeCascadePart.FORMED), "The centrifuge at " + at + " is loose");
                    }
                    helper.assertTrue(helper.getBlockState(min.offset(4, 0, 3)).getValue(CentrifugeCascadePart.FORMED), "The far corner isn't formed");
                })
                .thenSucceed();
    }

    // --- Corrosive gases and storage ---

    // UF6 is corrosive: a Gas Reservoir, a Gas Cartridge and an unlined Pressurized Cylinder refuse it; lined, the cylinder
    // takes it, and then holds one assay only.
    static void corrosiveStorage(GameTestHelper helper) {
        helper.assertTrue(Corrosion.isCorrosive(ModFluids.URANIUM_HEXAFLUORIDE.get()) && Corrosion.isCorrosive(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get())
                && !Corrosion.isCorrosive(ModFluids.HYDROGEN.get()), "The corrosive tag is wrong");
        helper.assertTrue(!ReservoirKind.GAS.accepts(uf6(Enrichment.NATURAL_PCM)) && ReservoirKind.GAS.accepts(FluidResource.of(ModFluids.HYDROGEN.get())),
                "A Gas Reservoir's acceptance is wrong");
        ItemStack cartridge = new ItemStack(ModItems.portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.TEMPERED));
        ResourceHandler<FluidResource> items = ItemAccess.forStack(cartridge).getCapability(Capabilities.Fluid.ITEM);
        helper.assertTrue(items != null && fill(items, uf6(Enrichment.NATURAL_PCM), 100) == 0, "A Gas Cartridge took UF6");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(pos, PressurizedCylinderBlockEntity.class);
        helper.assertTrue(!cylinder.isLined() && fill(cylinder.getTank(), uf6(Enrichment.NATURAL_PCM), 1_000) == 0, "An unlined cylinder took UF6");
        cylinder.line();
        helper.assertTrue(cylinder.isLined() && fill(cylinder.getTank(), uf6(3_000), 1_000) == 1_000, "A lined cylinder refused UF6");
        helper.assertTrue(fill(cylinder.getTank(), uf6(5_000), 1_000) == 0, "UF6 at 5% went in with UF6 at 3%");
        helper.assertTrue(fill(cylinder.getTank(), uf6(3_000), 500) == 500, "More UF6 at 3% didn't go in");
        helper.succeed();
    }

    // --- Radiation ---

    private static Vec3 centre(GameTestHelper helper, BlockPos relative) {
        return Vec3.atCenterOf(helper.absolutePos(relative));
    }

    // A block of Raw Uraninite gives its rate at one block, a quarter at two; a Lead Block between lets a tenth
    // through; a Lead Box lets nothing out where a Shulker Box does.
    static void radiationField(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = new BlockPos(2, 2, 2);
        helper.setBlock(source, ModBlocks.RAW_URANINITE_BLOCK.get());
        double rate = RadiationField.rate(ModBlocks.RAW_URANINITE_BLOCK.get().defaultBlockState());
        helper.assertTrue(rate > 0.0, "Raw Uraninite blocks aren't radioactive");
        RadiationField.forget(level);
        double near = RadiationField.doseRateAt(level, centre(helper, source.south(2)), null);
        double far = RadiationField.doseRateAt(level, centre(helper, source.south(4)), null);
        helper.assertTrue(Math.abs(near - rate / 4.0) < rate * 0.01, "At two blocks: " + near + ", expected " + rate / 4.0);
        helper.assertTrue(Math.abs(near / far - 4.0) < 0.05, "Not the inverse square: " + near + " at 2, " + far + " at 4");
        helper.setBlock(source.south(), ModBlocks.LEAD_BLOCK.get());
        RadiationField.forget(level);
        double shielded = RadiationField.doseRateAt(level, centre(helper, source.south(2)), null);
        helper.assertTrue(Math.abs(shielded - near * 0.1) < near * 0.01, "Behind lead: " + shielded + " of " + near);
        helper.assertTrue(Math.abs(RadiationField.transmission(level, helper.absolutePos(source.south())) - 0.1) < 1.0E-6, "Lead isn't 0.1");
        // Carried: a Lead Box blocks its contents, a Shulker Box doesn't.
        ItemContainerContents ore = ItemContainerContents.fromItems(List.of(new ItemStack(ModItems.RAW_URANINITE.get(), 16)));
        ItemStack box = new ItemStack(ModItems.LEAD_BOX.get());
        box.set(DataComponents.CONTAINER, ore);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ore);
        double loose = RadiationField.rate(new ItemStack(ModItems.RAW_URANINITE.get(), 16));
        helper.assertTrue(loose > 0.0 && RadiationField.rate(box) == 0.0, "A Lead Box lets " + RadiationField.rate(box) + " out");
        helper.assertTrue(Math.abs(RadiationField.rate(shulker) - loose) < 1.0E-9, "A Shulker Box of ore gives " + RadiationField.rate(shulker));
        helper.succeed();
    }

    // A full Hazmat Suit lets the configured 5% through, part of one some; and the dose thresholds.
    static void hazmatAndDose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = new BlockPos(3, 2, 3);
        helper.setBlock(source, ModBlocks.RAW_URANINITE_BLOCK.get());
        ArmorStand bare = helper.spawn(EntityTypes.ARMOR_STAND, source.west(2).below());
        ArmorStand suited = helper.spawn(EntityTypes.ARMOR_STAND, source.east(2).below());
        suited.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HAZMAT_HELMET.get()));
        helper.assertTrue(HazmatSuit.pieces(suited) == 1 && HazmatSuit.transmission(suited) < 1.0
                && HazmatSuit.transmission(suited) > ArcforgeConfig.HAZMAT_TRANSMISSION.getAsDouble(), "One piece lets " + HazmatSuit.transmission(suited));
        suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_CHESTPLATE.get()));
        suited.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_LEGGINGS.get()));
        suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.HAZMAT_BOOTS.get()));
        helper.assertTrue(HazmatSuit.pieces(suited) == 4
                && Math.abs(HazmatSuit.transmission(suited) - ArcforgeConfig.HAZMAT_TRANSMISSION.getAsDouble()) < 1.0E-9, "The full suit lets "
                        + HazmatSuit.transmission(suited));
        RadiationField.forget(level);
        double outside = RadiationField.doseRate(level, bare);
        double inside = RadiationField.doseRate(level, suited);
        helper.assertTrue(outside > 0.0 && Math.abs(inside / outside - ArcforgeConfig.HAZMAT_TRANSMISSION.getAsDouble()) < 0.01,
                "Suited " + inside + ", bare " + outside);
        helper.assertTrue(RadiationDose.level(ArcforgeConfig.RADIATION_NAUSEA_DOSE.getAsDouble() - 1) == 0
                && RadiationDose.level(ArcforgeConfig.RADIATION_NAUSEA_DOSE.getAsDouble()) == 1
                && RadiationDose.level(ArcforgeConfig.RADIATION_WEAKNESS_DOSE.getAsDouble()) == 2
                && RadiationDose.level(ArcforgeConfig.RADIATION_WITHER_DOSE.getAsDouble()) == 3
                && RadiationDose.level(ArcforgeConfig.RADIATION_LETHAL_DOSE.getAsDouble()) == 4, "The dose thresholds are wrong");
        helper.succeed();
    }
}
