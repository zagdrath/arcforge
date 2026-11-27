/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.fusion.Lithium6;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.CryogenicDistillingRecipe;
import net.zagdrath.arcforge.recipe.LiquefyingRecipe;
import net.zagdrath.arcforge.recipe.LithiumEnrichingRecipe;
import net.zagdrath.arcforge.recipe.TritiumExtractingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModRecipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;

// JEI categories for the fusion tier: the Lithium Isotope Separator, the Tritium Extraction Unit, the Cryogenic
// Distillation Column and the Cryogenic Plant (their data-driven recipes), and two pages of their own: the fuels a
// Fusion Reactor burns and what a Tritium Plant splits Fusion Exhaust into.
final class FusionCategories {
    private FusionCategories() {}

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    // A fluid recipe input (a fluid or a tag) in a 16x16 slot.
    private static void fluidInput(IRecipeLayoutBuilder builder, int x, int y, ChemicalReactingRecipe.FluidInput input) {
        var slot = builder.addInputSlot(x, y).setStandardSlotBackground().setFluidRenderer(Math.max(1_000, input.amount()), false, 16, 16);
        input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
    }

    // --- Lithium Isotope Separator ---

    // Natural (or partly enriched) Lithium Fluoride, a pass at a time, to Enriched Lithium Fluoride and at last Lithium-6
    // Fluoride: the feed a pass takes, both products, and how far each pass goes.
    static final class LithiumEnriching extends ArcforgeCategory<RecipeHolder<LithiumEnrichingRecipe>> {
        static final IRecipeHolderType<LithiumEnrichingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.LITHIUM_ENRICHING.get());

        LithiumEnriching(IGuiHelper gui) {
            super(TYPE, "lithium_enriching", ModBlocks.LITHIUM_ISOTOPE_SEPARATOR.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<LithiumEnrichingRecipe> holder, IFocusGroup focuses) {
            LithiumEnrichingRecipe recipe = holder.value();
            int count = Math.max(recipe.input().count(), ArcforgeConfig.LITHIUM_SEPARATOR_FEED.getAsInt());
            builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .addItemStacks(recipe.input().ingredient().items().map(item -> new ItemStack(item, count)).toList());
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(new ItemStack(recipe.intermediate()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.lithium_enriching.intermediate",
                            Lithium6.percent(Lithium6.minimum()))));
            builder.addOutputSlot(83, 5).setStandardSlotBackground().add(new ItemStack(recipe.result()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.lithium_enriching.result",
                            Lithium6.percent(Lithium6.minimum()))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<LithiumEnrichingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(Math.max(holder.value().time(), ArcforgeConfig.LITHIUM_SEPARATOR_TIME.getAsInt())).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<LithiumEnrichingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            LithiumEnrichingRecipe recipe = holder.value();
            int time = Math.max(recipe.time(), ArcforgeConfig.LITHIUM_SEPARATOR_TIME.getAsInt());
            text(graphics, Component.translatable("jei.arcforge.lithium_enriching.pass", Lithium6.percent(Lithium6.step()),
                    Lithium6.percent(Lithium6.NATURAL), Lithium6.percent(Lithium6.max())), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(time), grouped((long) time * recipe.baseEnergyPerTick())), 0, 40);
        }
    }

    // --- Tritium Extraction Unit ---

    // An irradiated Lithium Target Rod baked hot: the empty tube back, and the tritium and helium it held (scaled by how
    // much of the rod was bred, at full burnup here).
    static final class TritiumExtracting extends ArcforgeCategory<RecipeHolder<TritiumExtractingRecipe>> {
        static final IRecipeHolderType<TritiumExtractingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.TRITIUM_EXTRACTING.get());

        TritiumExtracting(IGuiHelper gui) {
            super(TYPE, "tritium_extracting", ModBlocks.TRITIUM_EXTRACTION_UNIT.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<TritiumExtractingRecipe> holder, IFocusGroup focuses) {
            TritiumExtractingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.tritium_extracting.bred")));
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(recipe.result());
            MachineCategories.fluid(builder, false, 83, 5, ModFluids.TRITIUM.get(), recipe.tritium());
            if (recipe.helium() > 0) {
                MachineCategories.fluid(builder, false, 103, 5, ModFluids.HELIUM.get(), recipe.helium());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<TritiumExtractingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<TritiumExtractingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            TritiumExtractingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.tritium_extracting.cost", recipe.heatPerTick(), recipe.minTemp()), 0, 28);
            text(graphics, seconds(recipe.time()), 0, 40);
        }
    }

    // --- Cryogenic Distillation Column ---

    // Its feed (or the air round it when it has none) split by boiling point into up to three products, per stage
    // (each block of the column's inside height) each recipe's time.
    static final class CryogenicDistilling extends ArcforgeCategory<RecipeHolder<CryogenicDistillingRecipe>> {
        static final IRecipeHolderType<CryogenicDistillingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CRYOGENIC_DISTILLING.get());

        CryogenicDistilling(IGuiHelper gui) {
            super(TYPE, "cryogenic_distilling", ModBlocks.CRYOGENIC_COLUMN_CONTROLLER.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CryogenicDistillingRecipe> holder, IFocusGroup focuses) {
            CryogenicDistillingRecipe recipe = holder.value();
            recipe.input().ifPresent(input -> fluidInput(builder, 1, 5, input));
            for (int i = 0; i < recipe.outputs().size(); i++) {
                MachineCategories.fluid(builder, false, 61 + 20 * i, 5, recipe.outputs().get(i));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<CryogenicDistillingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<CryogenicDistillingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            CryogenicDistillingRecipe recipe = holder.value();
            if (recipe.isAir()) {
                text(graphics, Component.translatable("jei.arcforge.cryogenic_distilling.air"), 0, 9);
            }
            text(graphics, Component.translatable("jei.arcforge.cryogenic_distilling.stage", seconds(recipe.time())), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.cryogenic_distilling.energy", grouped(ArcforgeConfig.CRYO_COLUMN_ENERGY.getAsInt())), 0, 40);
        }
    }

    // --- Cryogenic Plant ---

    // A gas liquefied in the cold box: what goes in, what comes out, its FE; the box's size sets the rate.
    static final class Liquefying extends ArcforgeCategory<RecipeHolder<LiquefyingRecipe>> {
        static final IRecipeHolderType<LiquefyingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.LIQUEFYING.get());

        Liquefying(IGuiHelper gui) {
            super(TYPE, "liquefying", ModBlocks.CRYOGENIC_PLANT_CONTROLLER.get(), gui, 130, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<LiquefyingRecipe> holder, IFocusGroup focuses) {
            fluidInput(builder, 1, 5, holder.value().input());
            MachineCategories.fluid(builder, false, 61, 5, holder.value().result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<LiquefyingRecipe> holder, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<LiquefyingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.liquefying.energy", grouped(holder.value().energy())), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.liquefying.rate", grouped(ArcforgeConfig.CRYO_PLANT_RATE.getAsInt())), 0, 40);
        }
    }

    // --- Fusion Reactor fuels ---

    // One fuel cycle the reactor burns: its two fuels and the helium ash, per FUEL_ENERGY of fusion heat (the reaction's
    // energy, 17.6 MeV for D-T and 18.4 MeV for D-He3; 1 mB is 10^20 nuclei).
    record FusionFuel(String id, Fluid first, Fluid second, double mevPerReaction, int neutronPercent) {
        // Joules of fusion heat a page counts: 100 MW for a minute.
        static final double FUEL_ENERGY = 6.0e9;

        int amount() {
            return (int) Math.round(FUEL_ENERGY / (mevPerReaction * 1.602e-13) * 1.0e-20);
        }

        static List<FusionFuel> all() {
            return List.of(new FusionFuel("dt", ModFluids.DEUTERIUM.get(), ModFluids.TRITIUM.get(), 17.59, 80),
                    new FusionFuel("dhe3", ModFluids.DEUTERIUM.get(), ModFluids.HELIUM_3.get(), 18.35, 1));
        }
    }

    static final class FusionFuels extends ArcforgeCategory<FusionFuel> {
        static final IRecipeType<FusionFuel> TYPE = IRecipeType.create(Arcforge.MODID, "fusion", FusionFuel.class);

        FusionFuels(IGuiHelper gui) {
            super(TYPE, "fusion", ModBlocks.FUSION_REACTOR_CONTROLLER.get(), gui, 150, 60);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FusionFuel fuel, IFocusGroup focuses) {
            MachineCategories.fluid(builder, true, 1, 5, fuel.first(), fuel.amount());
            MachineCategories.fluid(builder, true, 21, 5, fuel.second(), fuel.amount());
            MachineCategories.fluid(builder, false, 81, 5, ModFluids.HELIUM.get(), fuel.amount());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, FusionFuel fuel, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(46, 5);
        }

        @Override
        public void draw(FusionFuel fuel, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.fusion." + fuel.id()), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.fusion.energy", fuel.neutronPercent()), 0, 38);
            text(graphics, Component.translatable("jei.arcforge.fusion.exhaust"), 0, 48);
        }
    }

    // --- Tritium Plant ---

    // Fusion Exhaust split into what it holds: its shares follow what the server's reactors burned and bred, so the
    // page shows each product without a fixed amount of its own.
    record ExhaustSeparation() {}

    static final class ExhaustSeparating extends ArcforgeCategory<ExhaustSeparation> {
        static final IRecipeType<ExhaustSeparation> TYPE = IRecipeType.create(Arcforge.MODID, "exhaust_separating", ExhaustSeparation.class);

        ExhaustSeparating(IGuiHelper gui) {
            super(TYPE, "exhaust_separating", ModBlocks.TRITIUM_PLANT_CONTROLLER.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ExhaustSeparation page, IFocusGroup focuses) {
            MachineCategories.fluid(builder, true, 1, 5, ModFluids.FUSION_EXHAUST.get(), 1_000);
            List<Fluid> products = List.of(ModFluids.DEUTERIUM.get(), ModFluids.TRITIUM.get(), ModFluids.HELIUM_3.get(), ModFluids.HELIUM.get());
            for (int i = 0; i < products.size(); i++) {
                builder.addOutputSlot(61 + 20 * i, 5).setStandardSlotBackground().setFluidRenderer(1_000, false, 16, 16).add(products.get(i), 1_000)
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.exhaust_separating.share")));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, ExhaustSeparation page, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        @Override
        public void draw(ExhaustSeparation page, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.exhaust_separating.rate", grouped(ArcforgeConfig.TRITIUM_PLANT_RATE.getAsInt()),
                    grouped(ArcforgeConfig.TRITIUM_PLANT_ENERGY.getAsInt())), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.exhaust_separating.decay"), 0, 40);
        }
    }
}
