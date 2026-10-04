/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.recipe.AirSeparatingRecipe;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.CulturingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;
import net.zagdrath.arcforge.recipe.DigestingRecipe;
import net.zagdrath.arcforge.recipe.SynthesizingRecipe;
import net.zagdrath.arcforge.upgrade.UpgradeType;
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.CrushingRecipe;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.FermentingRecipe;
import net.zagdrath.arcforge.recipe.FiberizingRecipe;
import net.zagdrath.arcforge.recipe.InfusingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.MeltingRecipe;
import net.zagdrath.arcforge.recipe.PressingRecipe;
import net.zagdrath.arcforge.recipe.DryingRecipe;
import net.zagdrath.arcforge.recipe.MillingRecipe;
import net.zagdrath.arcforge.recipe.OilPressingRecipe;
import net.zagdrath.arcforge.recipe.SeedExtractingRecipe;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.SteamGrade;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import net.zagdrath.arcforge.recipe.ClocheRecipe;

// JEI categories for Arcforge's machines: what goes in, what comes out, and what it takes.
final class MachineCategories {
    // Fluids show in a 16x16 slot, measured against a bucket.
    private static final int FLUID_SLOT_CAPACITY = 1_000;

    private MachineCategories() {}

    static void fluid(IRecipeLayoutBuilder builder, boolean input, int x, int y, FluidStackTemplate fluid) {
        (input ? builder.addInputSlot(x, y) : builder.addOutputSlot(x, y))
                .setStandardSlotBackground()
                .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, fluid.amount()), false, 16, 16)
                .add(fluid.fluid().value(), fluid.amount());
    }

    static void fluid(IRecipeLayoutBuilder builder, boolean input, int x, int y, Fluid fluid, int amount) {
        (input ? builder.addInputSlot(x, y) : builder.addOutputSlot(x, y))
                .setStandardSlotBackground()
                .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, amount), false, 16, 16)
                .add(fluid, amount);
    }

    // --- Arc Crusher / Arc Crushing Array ---

    static final class Crushing extends ArcforgeCategory<RecipeHolder<CrushingRecipe>> {
        static final IRecipeHolderType<CrushingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CRUSHING.get());

        Crushing(IGuiHelper gui) {
            super(TYPE, "crushing", ModBlocks.ARC_CRUSHER.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CrushingRecipe> holder, IFocusGroup focuses) {
            CrushingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            // A bonus-only recipe (netherrack) shows just the chance slot.
            recipe.result().ifPresent(result -> builder.addOutputSlot(61, 5).setStandardSlotBackground().add(result));
            recipe.bonus().ifPresent(bonus -> builder.addOutputSlot(83, 5).setStandardSlotBackground().add(bonus)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.bonusChance() * 100)))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<CrushingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<CrushingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, seconds(holder.value().time()), 0, 30);
            if (holder.value().ore()) {
                textRight(graphics, Component.translatable("jei.arcforge.crushing.ore"), 116, 30);
            }
        }
    }

    // --- Fiberizer ---

    static final class Fiberizing extends ArcforgeCategory<RecipeHolder<FiberizingRecipe>> {
        static final IRecipeHolderType<FiberizingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.FIBERIZING.get());

        Fiberizing(IGuiHelper gui) {
            super(TYPE, "fiberizing", ModBlocks.FIBERIZER.get(), gui, 150, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FiberizingRecipe> holder, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(holder.value().ingredient());
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(holder.value().result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<FiberizingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<FiberizingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            FiberizingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.fiberizing.cost", recipe.fePerTick(), recipe.huPerTick(), recipe.minTemp()), 0, 30);
            textRight(graphics, seconds(recipe.time()), getWidth(), 9);
        }
    }

    // --- Infuser ---

    static final class Infusing extends ArcforgeCategory<RecipeHolder<InfusingRecipe>> {
        static final IRecipeHolderType<InfusingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.INFUSING.get());

        Infusing(IGuiHelper gui) {
            super(TYPE, "infusing", ModBlocks.INFUSER.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<InfusingRecipe> holder, IFocusGroup focuses) {
            InfusingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            // The fluid, or the additive (Pine Resin) that stands in for it.
            recipe.fluid().ifPresent(fluid -> fluid(builder, true, 21, 5, fluid));
            recipe.additive().ifPresent(additive -> builder.addInputSlot(21, 5).setStandardSlotBackground()
                    .addItemStacks(additive.ingredient().items().map(item -> new ItemStack(item, additive.count())).toList())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.infusing.additive"))));
            builder.addOutputSlot(81, 5).setStandardSlotBackground().add(recipe.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<InfusingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(RecipeHolder<InfusingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, seconds(holder.value().time()), 0, 30);
        }
    }

    // --- Metal Press / Metal Pressing Array ---

    static final class Pressing extends ArcforgeCategory<RecipeHolder<PressingRecipe>> {
        static final IRecipeHolderType<PressingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.PRESSING.get());

        Pressing(IGuiHelper gui) {
            super(TYPE, "pressing", ModBlocks.METAL_PRESS.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<PressingRecipe> holder, IFocusGroup focuses) {
            PressingRecipe recipe = holder.value();
            // The die is needed but never used up.
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(new ItemStack(recipe.die()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.pressing.die")));
            builder.addInputSlot(21, 5).setStandardSlotBackground().addItemStacks(recipe.ingredient().items()
                    .map(item -> new ItemStack(item, recipe.count())).toList());
            builder.addOutputSlot(81, 5).setStandardSlotBackground().add(recipe.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<PressingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(RecipeHolder<PressingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, seconds(holder.value().time()), 0, 30);
        }
    }

    // --- Carbonizer ---

    static final class Carbonizing extends ArcforgeCategory<RecipeHolder<CarbonizingRecipe>> {
        static final IRecipeHolderType<CarbonizingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CARBONIZING.get());

        Carbonizing(IGuiHelper gui) {
            super(TYPE, "carbonizing", ModBlocks.CARBONIZER.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CarbonizingRecipe> holder, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(holder.value().ingredient());
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(holder.value().result());
            holder.value().byproduct().ifPresent(fluid -> fluid(builder, false, 83, 5, fluid));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<CarbonizingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<CarbonizingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, seconds(holder.value().time()), 0, 30);
        }
    }

    // --- Arc Melter ---

    static final class Melting extends ArcforgeCategory<RecipeHolder<MeltingRecipe>> {
        static final IRecipeHolderType<MeltingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.MELTING.get());

        Melting(IGuiHelper gui) {
            super(TYPE, "melting", ModBlocks.ARC_MELTER.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MeltingRecipe> holder, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(holder.value().ingredient());
            fluid(builder, false, 83, 5, holder.value().result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MeltingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().baseTicks()).setPosition(26, 5);
        }

        // Time and FE at the configured rates, before upgrades.
        @Override
        public void draw(RecipeHolder<MeltingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            MeltingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.baseTicks()),
                    String.format(Locale.ROOT, "%,d", recipe.totalEnergy())), 0, 30);
        }
    }

    // --- Fermenter ---

    static final class Fermenting extends ArcforgeCategory<RecipeHolder<FermentingRecipe>> {
        static final IRecipeHolderType<FermentingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.FERMENTING.get());

        Fermenting(IGuiHelper gui) {
            super(TYPE, "fermenting", ModBlocks.FERMENTER.get(), gui, 130, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FermentingRecipe> holder, IFocusGroup focuses) {
            FermentingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            fluid(builder, true, 21, 5, recipe.fluidInput().fluid(), recipe.fluidInput().amount());
            // The Ethanol, with the Dried Hops bonus on its tooltip.
            builder.addOutputSlot(79, 5).setStandardSlotBackground()
                    .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, recipe.result().amount()), false, 16, 16)
                    .add(recipe.result().fluid().value(), recipe.result().amount())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.fermenting.hops",
                            Math.round(ArcforgeConfig.FERMENTER_ADDITIVE_BONUS.getAsDouble() * 100))));
            recipe.byproduct().ifPresent(byproduct -> builder.addOutputSlot(99, 5).setStandardSlotBackground().add(byproduct.create())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.byproductChance() * 100)))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<FermentingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        // Time and FE at the configured rates, before upgrades.
        @Override
        public void draw(RecipeHolder<FermentingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            FermentingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.totalEnergy())), 0, 30);
        }
    }

    // --- Fermenter cultures (Slimeballs) ---

    static final class Culturing extends ArcforgeCategory<RecipeHolder<CulturingRecipe>> {
        static final IRecipeHolderType<CulturingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CULTURING.get());

        Culturing(IGuiHelper gui) {
            super(TYPE, "culturing", ModBlocks.FERMENTER.get(), gui, 130, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CulturingRecipe> holder, IFocusGroup focuses) {
            CulturingRecipe recipe = holder.value();
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                var input = recipe.ingredients().get(i);
                builder.addInputSlot(1 + i * 18, 5).setStandardSlotBackground()
                        .addItemStacks(input.ingredient().items().map(item -> new ItemStack(item, input.count())).toList());
            }
            fluid(builder, true, 37, 5, recipe.fluidInput().fluid(), recipe.fluidInput().amount());
            // The starter culture, in the additive slot: never used up.
            builder.addInputSlot(19, 23).setStandardSlotBackground().add(recipe.culture())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.culturing.starter")));
            builder.addOutputSlot(95, 5).setStandardSlotBackground().add(recipe.result().create());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<CulturingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(62, 5);
        }

        // Time and FE at the configured rates, before upgrades.
        @Override
        public void draw(RecipeHolder<CulturingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            CulturingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.totalEnergy())), 41, 28);
        }
    }

    // --- Millstone and Mill ---

    static final class Milling extends ArcforgeCategory<RecipeHolder<MillingRecipe>> {
        static final IRecipeHolderType<MillingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.MILLING.get());

        Milling(IGuiHelper gui) {
            super(TYPE, "milling", ModBlocks.MILLSTONE.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MillingRecipe> holder, IFocusGroup focuses) {
            MillingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(recipe.result());
            recipe.bonus().ifPresent(bonus -> builder.addOutputSlot(83, 5).setStandardSlotBackground().add(bonus)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.bonusChance() * 100)))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MillingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(millTicks(holder.value())).setPosition(26, 5);
        }

        // Turns on the Millstone, and the time per lane on the Mill (before upgrades).
        @Override
        public void draw(RecipeHolder<MillingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            MillingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.milling.cost", recipe.turns(), seconds(millTicks(recipe))), 0, 30);
        }

        private static int millTicks(MillingRecipe recipe) {
            return Math.max(1, (int) Math.round(recipe.time() * ArcforgeConfig.MILL_TIME_MULTIPLIER.getAsDouble()));
        }
    }

    // --- Oil Press ---

    static final class OilPressing extends ArcforgeCategory<RecipeHolder<OilPressingRecipe>> {
        static final IRecipeHolderType<OilPressingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.OIL_PRESSING.get());

        OilPressing(IGuiHelper gui) {
            super(TYPE, "oil_pressing", ModBlocks.OIL_PRESS.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<OilPressingRecipe> holder, IFocusGroup focuses) {
            OilPressingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            fluid(builder, false, 61, 5, recipe.result());
            recipe.byproduct().ifPresent(cake -> builder.addOutputSlot(83, 5).setStandardSlotBackground().add(cake.create())
                    .addRichTooltipCallback((view, tooltip) -> {
                        if (recipe.byproductChance() < 1.0F) {
                            tooltip.add(Component.translatable("jei.arcforge.chance", Math.round(recipe.byproductChance() * 100)));
                        }
                    }));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<OilPressingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        // Time and FE at the configured rates, before upgrades.
        @Override
        public void draw(RecipeHolder<OilPressingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            OilPressingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.totalEnergy())), 0, 30);
        }
    }

    // --- Seed Extractor ---

    static final class SeedExtracting extends ArcforgeCategory<RecipeHolder<SeedExtractingRecipe>> {
        static final IRecipeHolderType<SeedExtractingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.SEED_EXTRACTING.get());

        SeedExtracting(IGuiHelper gui) {
            super(TYPE, "seed_extracting", ModBlocks.SEED_EXTRACTOR.get(), gui, 116, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<SeedExtractingRecipe> holder, IFocusGroup focuses) {
            SeedExtractingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(recipe.result());
            recipe.bonus().ifPresent(bonus -> builder.addOutputSlot(83, 5).setStandardSlotBackground().add(bonus)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.bonusChance() * 100)))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<SeedExtractingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<SeedExtractingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, seconds(holder.value().time()), 0, 30);
        }
    }

    // --- Grain Dryer ---

    static final class Drying extends ArcforgeCategory<RecipeHolder<DryingRecipe>> {
        static final IRecipeHolderType<DryingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.DRYING.get());

        Drying(IGuiHelper gui) {
            super(TYPE, "drying", ModBlocks.GRAIN_DRYER.get(), gui, 130, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<DryingRecipe> holder, IFocusGroup focuses) {
            DryingRecipe recipe = holder.value();
            recipe.ingredient().ifPresent(ingredient -> builder.addInputSlot(1, 5).setStandardSlotBackground().add(ingredient));
            // A fluid from the dryer's tank (Latex): every fluid the input names.
            recipe.fluid().ifPresent(input -> {
                var slot = builder.addInputSlot(1, 5).setStandardSlotBackground()
                        .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, input.amount()), false, 16, 16);
                input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
            });
            builder.addOutputSlot(61, 5).setStandardSlotBackground().add(recipe.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<DryingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        // The heat per tick and the temperature it needs, and the time, before upgrades.
        @Override
        public void draw(RecipeHolder<DryingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            DryingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.drying.cost", recipe.huPerTick(), ArcforgeConfig.GRAIN_DRYER_MIN_TEMPERATURE.getAsInt()), 0, 30);
            textRight(graphics, seconds(recipe.time()), getWidth(), 9);
        }
    }

    // --- Hydrothermal Carbonizer ---

    // The biomass (with its count) and the water in, the arrow, the Bio-Coal and the water given back; the heat, the
    // temperature it needs and the time below, before upgrades.
    static final class HydrothermalCarbonizing extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe> TYPE =
                IRecipeHolderType.create(ModRecipes.HYDROTHERMAL_CARBONIZING.get());

        HydrothermalCarbonizing(IGuiHelper gui) {
            super(TYPE, "hydrothermal_carbonizing", ModBlocks.HYDROTHERMAL_CARBONIZER.get(), gui, 140, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe> holder,
                IFocusGroup focuses) {
            var recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .addItemStacks(recipe.ingredient().items().map(item -> new ItemStack(item, recipe.inputCount())).toList());
            if (recipe.water() > 0) {
                fluid(builder, true, 21, 5, net.minecraft.world.level.material.Fluids.WATER, recipe.water());
            }
            builder.addOutputSlot(71, 5).setStandardSlotBackground().add(recipe.result());
            if (recipe.waterReturn() > 0) {
                fluid(builder, false, 91, 5, net.minecraft.world.level.material.Fluids.WATER, recipe.waterReturn());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe> holder,
                IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().ticks()).setPosition(44, 5);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe> holder, IRecipeSlotsView slots,
                GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.hydrothermal_carbonizing.heat", String.format(Locale.ROOT, "%,d", recipe.heatPerOperation()),
                    ArcforgeConfig.HYDROTHERMAL_MIN_TEMPERATURE.getAsInt()), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.hydrothermal_carbonizing.time", seconds(recipe.ticks())), 0, 40);
        }
    }

    // --- Sifter and Diamond Press ---

    // The block sifted, the arrow, and every possible find with its chance (with a Steel and with a Tungsten Mesh); the
    // time and FE below, before the mesh and upgrades.
    static final class Sifting extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.SiftingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.SiftingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.SIFTING.get());

        Sifting(IGuiHelper gui) {
            super(TYPE, "sifting", ModBlocks.SIFTER.get(), gui, 140, 56);
        }

        private static String percent(double chance) {
            return String.format(Locale.ROOT, "%.1f", chance * 100).replaceAll("\\.0$", "");
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.SiftingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            builder.addInputSlot(1, 10).setStandardSlotBackground().add(recipe.ingredient());
            var outputs = recipe.outputs();
            for (int i = 0; i < outputs.size(); i++) {
                var output = outputs.get(i);
                double steel = Math.min(1.0, output.chance() * ArcforgeConfig.SIFTER_STEEL_CHANCE.getAsDouble());
                double tungsten = Math.min(1.0, output.chance() * ArcforgeConfig.SIFTER_TUNGSTEN_CHANCE.getAsDouble());
                builder.addOutputSlot(51 + i % 4 * 18, 1 + i / 4 * 18).setStandardSlotBackground().add(output.item())
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.sifting.chance", percent(steel),
                                percent(tungsten)).withStyle(ChatFormatting.GRAY)));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.SiftingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().ticks()).setPosition(24, 10);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.SiftingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.ticks()),
                    String.format(Locale.ROOT, "%,d", recipe.ticks() * recipe.baseEnergyPerTick())), 0, 40);
        }
    }

    // The Graphite (with its count), the arrow and the Diamond; the FE, the heat, the temperature it needs and the time
    // below, before upgrades.
    static final class DiamondPressing extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.DiamondPressingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.DiamondPressingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.DIAMOND_PRESSING.get());

        DiamondPressing(IGuiHelper gui) {
            super(TYPE, "diamond_pressing", ModBlocks.DIAMOND_PRESS.get(), gui, 140, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.DiamondPressingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .addItemStacks(recipe.ingredient().items().map(item -> new ItemStack(item, recipe.inputCount())).toList());
            builder.addOutputSlot(71, 5).setStandardSlotBackground().add(recipe.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.DiamondPressingRecipe> holder,
                IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().ticks()).setPosition(44, 5);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.DiamondPressingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.diamond_pressing.cost", String.format(Locale.ROOT, "%,d", recipe.energyPerOperation()),
                    String.format(Locale.ROOT, "%,d", recipe.heatPerOperation())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.diamond_pressing.heat", String.format(Locale.ROOT, "%,d", recipe.minTemperatureOrDefault()),
                    seconds(recipe.ticks())), 0, 40);
        }
    }

    // --- Carbon capture ---

    // Carbon Dioxide and Hydrogen in, the arrow, the Carbon Dust and the Water out; the FE below (before Energy upgrades;
    // never less than the balance floor).
    static final class CarbonReclaiming extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CARBON_RECLAIMING.get());

        CarbonReclaiming(IGuiHelper gui) {
            super(TYPE, "carbon_reclaiming", ModBlocks.CARBON_RECLAIMER.get(), gui, 130, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            fluid(builder, true, 1, 5, net.zagdrath.arcforge.registry.ModFluids.CARBON_DIOXIDE.get(), recipe.carbonDioxideAmount());
            if (recipe.hydrogenAmount() > 0) {
                fluid(builder, true, 21, 5, net.zagdrath.arcforge.registry.ModFluids.HYDROGEN.get(), recipe.hydrogenAmount());
            }
            builder.addOutputSlot(71, 5).setStandardSlotBackground().add(recipe.result());
            if (recipe.waterAmount() > 0) {
                fluid(builder, false, 91, 5, net.minecraft.world.level.material.Fluids.WATER, recipe.waterAmount());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe> holder,
                IFocusGroup focuses) {
            int ticks = Math.max(1, holder.value().totalEnergy() / Math.max(1, ArcforgeConfig.RECLAIMER_ENERGY_PER_TICK.getAsInt()));
            builder.addAnimatedRecipeArrowWidget(ticks).setPosition(44, 5);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.carbon_reclaiming.energy", String.format(Locale.ROOT, "%,d", holder.value().totalEnergy())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.carbon_reclaiming.floor"), 0, 40);
        }
    }

    // The fuel (with its count) and the Steam in, the arrow, the Syngas and a chance of ash out; the heat, the temperature
    // it needs and the time below, before upgrades.
    static final class Gasifying extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.GasifyingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.GasifyingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.GASIFYING.get());

        Gasifying(IGuiHelper gui) {
            super(TYPE, "gasifying", ModBlocks.GASIFIER.get(), gui, 140, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.GasifyingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .addItemStacks(recipe.ingredient().items().map(item -> new ItemStack(item, recipe.count())).toList());
            if (recipe.steamAmount() > 0) {
                fluid(builder, true, 21, 5, net.zagdrath.arcforge.registry.ModFluids.STEAM.get(), recipe.steamAmount());
            }
            fluid(builder, false, 71, 5, recipe.result());
            double chance = recipe.ashChanceOrDefault();
            if (chance > 0) {
                builder.addOutputSlot(91, 5).setStandardSlotBackground()
                        .add(recipe.ash().map(ash -> ash.create()).orElseGet(() -> new ItemStack(net.zagdrath.arcforge.registry.ModItems.WOOD_ASH.get())))
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                                Math.round(chance * 100)).withStyle(ChatFormatting.GRAY)));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.GasifyingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().ticks()).setPosition(44, 5);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.GasifyingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.gasifying.heat", String.format(Locale.ROOT, "%,d", recipe.heatPerOperation()),
                    String.format(Locale.ROOT, "%,d", ArcforgeConfig.GASIFIER_MIN_TEMPERATURE.getAsInt())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.gasifying.time", seconds(recipe.ticks())), 0, 40);
        }
    }

    // Syngas and a catalyst (Iron or Nickel Dust, worn down slowly) in, the arrow, the four products out; the FE, heat and
    // temperature window below, before upgrades.
    static final class FischerTropsch extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.FischerTropschRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.FischerTropschRecipe> TYPE = IRecipeHolderType.create(ModRecipes.FISCHER_TROPSCH.get());

        FischerTropsch(IGuiHelper gui) {
            super(TYPE, "fischer_tropsch", ModBlocks.FISCHER_TROPSCH_REACTOR.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.FischerTropschRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            ChemicalReactingRecipe.FluidInput input = recipe.input();
            var slot = builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, input.amount()), false, 16, 16);
            input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
            builder.addInputSlot(21, 5).setStandardSlotBackground()
                    .addItemStacks(List.of(new ItemStack(net.zagdrath.arcforge.registry.ModItems.IRON_DUST.get()),
                            new ItemStack(net.zagdrath.arcforge.registry.ModItems.NICKEL_DUST.get())))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.fischer_tropsch.catalyst",
                            ArcforgeConfig.FT_IRON_CATALYST_OPERATIONS.getAsInt(), ArcforgeConfig.FT_NICKEL_CATALYST_OPERATIONS.getAsInt())
                            .withStyle(ChatFormatting.GRAY)));
            net.minecraft.world.level.material.Fluid[] fluids = { net.zagdrath.arcforge.registry.ModFluids.NAPHTHA.get(),
                    net.zagdrath.arcforge.registry.ModFluids.LIGHT_OIL.get(), net.zagdrath.arcforge.registry.ModFluids.HEAVY_OIL.get(),
                    net.minecraft.world.level.material.Fluids.WATER };
            int[] amounts = recipe.products();
            int x = 71;
            for (int i = 0; i < fluids.length; i++) {
                if (amounts[i] > 0) {
                    fluid(builder, false, x, 5, fluids[i], amounts[i]);
                    x += 20;
                }
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.FischerTropschRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().ticks()).setPosition(44, 5);
        }

        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.FischerTropschRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.ticks()),
                    String.format(Locale.ROOT, "%,d", recipe.ticks() * recipe.baseEnergyPerTick())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.fischer_tropsch.heat", recipe.baseHeatPerTick(),
                    ArcforgeConfig.FT_MIN_TEMPERATURE.getAsInt(), ArcforgeConfig.FT_MAX_TEMPERATURE.getAsInt()), 0, 40);
        }
    }

    // --- Tree Cutter ---

    // A sapling the Tree Cutter plants (with Bone Meal to speed it), and the log and leaves of its tree.
    record TreeCuttingRecipe(net.minecraft.world.item.Item sapling, ItemStack log, ItemStack leaves) {}

    static final class TreeCutting extends ArcforgeCategory<TreeCuttingRecipe> {
        static final IRecipeType<TreeCuttingRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "tree_cutting", TreeCuttingRecipe.class);

        TreeCutting(IGuiHelper gui) {
            super(TYPE, "tree_cutting", ModBlocks.TREE_CUTTER.get(), gui, 140, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, TreeCuttingRecipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(new ItemStack(recipe.sapling()));
            builder.addInputSlot(21, 5).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.BONE_MEAL));
            int x = 71;
            for (ItemStack out : List.of(recipe.log(), recipe.leaves(), new ItemStack(recipe.sapling()))) {
                if (!out.isEmpty()) {
                    builder.addOutputSlot(x, 5).setStandardSlotBackground().add(out);
                    x += 20;
                }
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, TreeCuttingRecipe recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(44, 5);
        }

        @Override
        public void draw(TreeCuttingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.tree_cutting.cost", ArcforgeConfig.TREE_CUTTER_ENERGY_PER_LOG.getAsInt()), 0, 30);
        }
    }

    // --- Vulcanizer ---

    static final class Vulcanizing extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.VulcanizingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.VulcanizingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.VULCANIZING.get());

        Vulcanizing(IGuiHelper gui) {
            super(TYPE, "vulcanizing", ModBlocks.VULCANIZER.get(), gui, 130, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.VulcanizingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            int x = 1;
            for (ChemicalReactingRecipe.ItemInput input : List.of(recipe.input(), recipe.secondInput())) {
                builder.addInputSlot(x, 5).setStandardSlotBackground()
                        .addItemStacks(input.ingredient().items().map(item -> new ItemStack(item, input.count())).toList());
                x += 20;
            }
            builder.addOutputSlot(81, 5).setStandardSlotBackground().add(recipe.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.VulcanizingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        // The heat per tick and the temperature it needs, and the time, before upgrades.
        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.VulcanizingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            var recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.vulcanizing.cost", recipe.huPerTick(), ArcforgeConfig.VULCANIZER_MIN_TEMPERATURE.getAsInt()), 0, 30);
            textRight(graphics, seconds(recipe.time()), getWidth(), 25);
        }
    }

    // --- Resin Tap: what each kind of log gives (config farming.cropProcessing.resinTap) ---

    record ResinTapping(ItemStack log, boolean latex, boolean anyLog) {
        static List<ResinTapping> all() {
            return List.of(
                    new ResinTapping(new ItemStack(net.minecraft.world.item.Items.JUNGLE_LOG), true, false),
                    new ResinTapping(new ItemStack(net.minecraft.world.item.Items.SPRUCE_LOG), false, false),
                    new ResinTapping(new ItemStack(net.minecraft.world.item.Items.OAK_LOG), false, true));
        }

        double chance() {
            return latex ? 1.0 : anyLog ? ArcforgeConfig.RESIN_TAP_OTHER_CHANCE.getAsDouble() : ArcforgeConfig.RESIN_TAP_SPRUCE_CHANCE.getAsDouble();
        }
    }

    static final class ResinTap extends ArcforgeCategory<ResinTapping> {
        static final IRecipeType<ResinTapping> TYPE = IRecipeType.create(Arcforge.MODID, "resin_tapping", ResinTapping.class);

        ResinTap(IGuiHelper gui) {
            super(TYPE, "resin_tapping", ModBlocks.RESIN_TAP.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ResinTapping recipe, IFocusGroup focuses) {
            var log = builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.log());
            if (recipe.anyLog()) {
                log.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.resin_tapping.any_log")));
            }
            builder.addInputSlot(21, 5).setStandardSlotBackground().add(new ItemStack(ModItems.RESIN_TAP.get()));
            if (recipe.latex()) {
                fluid(builder, false, 71, 5, ModFluids.LATEX.get(), ArcforgeConfig.RESIN_TAP_LATEX_PER_DRIP.getAsInt());
            } else {
                builder.addOutputSlot(71, 5).setStandardSlotBackground().add(new ItemStack(ModItems.PINE_RESIN.get()))
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                                Math.round(recipe.chance() * 100))));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, ResinTapping recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(43, 5);
        }

        // Per drip, every so many seconds, on a living tree.
        @Override
        public void draw(ResinTapping recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Component gain = recipe.latex()
                    ? Component.translatable("jei.arcforge.resin_tapping.latex", ArcforgeConfig.RESIN_TAP_LATEX_PER_DRIP.getAsInt())
                    : Component.translatable("jei.arcforge.resin_tapping.resin", Math.round(recipe.chance() * 100));
            text(graphics, gain, 0, 28);
            text(graphics, Component.translatable("jei.arcforge.resin_tapping.every",
                    String.format(Locale.ROOT, "%.0f", ArcforgeConfig.RESIN_TAP_INTERVAL.getAsInt() / 20.0)), 0, 39);
        }
    }

    // --- Farm chemistry ---

    // Air -> the primary and secondary gases. Where it works (not the End), the FE and the time, before upgrades.
    static final class AirSeparating extends ArcforgeCategory<RecipeHolder<AirSeparatingRecipe>> {
        static final IRecipeHolderType<AirSeparatingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.AIR_SEPARATING.get());

        AirSeparating(IGuiHelper gui) {
            super(TYPE, "air_separating", ModBlocks.AIR_SEPARATOR.get(), gui, 130, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AirSeparatingRecipe> holder, IFocusGroup focuses) {
            AirSeparatingRecipe recipe = holder.value();
            fluid(builder, false, 61, 5, recipe.primary());
            recipe.secondary().ifPresent(secondary -> fluid(builder, false, 81, 5, secondary));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<AirSeparatingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(26, 5);
        }

        @Override
        public void draw(RecipeHolder<AirSeparatingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            AirSeparatingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.air_separating.air"), 0, 9);
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.time() * recipe.baseEnergyPerTick())), 0, 30);
            if (!recipe.excludedDimensions().isEmpty() || !recipe.dimensions().isEmpty()) {
                text(graphics, Component.translatable(recipe.dimensions().isEmpty() ? "jei.arcforge.air_separating.not_in" : "jei.arcforge.air_separating.only_in",
                        dimensionNames(recipe.dimensions().isEmpty() ? recipe.excludedDimensions() : recipe.dimensions())), 0, 40);
            }
        }

        private static Component dimensionNames(List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dimensions) {
            return Component.literal(String.join(", ", dimensions.stream().map(key -> {
                String path = key.identifier().getPath();
                String name = path.startsWith("the_") ? path.substring(4) : path;
                return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
            }).toList()));
        }
    }

    // One or two gases -> a gas (the Haber Reactor), with the FE, heat and temperature it needs.
    static final class Synthesizing extends ArcforgeCategory<RecipeHolder<SynthesizingRecipe>> {
        static final IRecipeHolderType<SynthesizingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.SYNTHESIZING.get());

        Synthesizing(IGuiHelper gui) {
            super(TYPE, "synthesizing", ModBlocks.HABER_REACTOR.get(), gui, 130, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<SynthesizingRecipe> holder, IFocusGroup focuses) {
            SynthesizingRecipe recipe = holder.value();
            for (int i = 0; i < recipe.inputs().size(); i++) {
                ChemicalReactingRecipe.FluidInput input = recipe.inputs().get(i);
                var slot = builder.addInputSlot(1 + 20 * i, 5).setStandardSlotBackground()
                        .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, input.amount()), false, 16, 16);
                input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
            }
            fluid(builder, false, 81, 5, recipe.output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<SynthesizingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(RecipeHolder<SynthesizingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            SynthesizingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.time() * recipe.baseEnergyPerTick())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.synthesizing.heat", recipe.heatPerTick(), recipe.minTemperatureOrDefault()), 0, 40);
        }
    }

    // Plant matter + water -> Biogas, and a chance of Digestate (the Biogas Digester), with the time a lane takes.
    static final class Digesting extends ArcforgeCategory<RecipeHolder<DigestingRecipe>> {
        static final IRecipeHolderType<DigestingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.DIGESTING.get());

        Digesting(IGuiHelper gui) {
            super(TYPE, "digesting", ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get(), gui, 130, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<DigestingRecipe> holder, IFocusGroup focuses) {
            DigestingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.ingredient());
            if (recipe.water() > 0) {
                fluid(builder, true, 21, 5, net.minecraft.world.level.material.Fluids.WATER, recipe.water());
            }
            fluid(builder, false, 81, 5, recipe.result());
            recipe.byproduct().ifPresent(byproduct -> builder.addOutputSlot(101, 5).setStandardSlotBackground().add(byproduct)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.byproductChance() * 100)))));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<DigestingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(RecipeHolder<DigestingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.digesting.warm", seconds(holder.value().time()),
                    ArcforgeConfig.DIGESTER_MIN_TEMPERATURE.getAsInt()), 0, 28);
        }
    }

    // --- Automated farms ---

    // A seed in a soil -> its harvest, each output with its chance, and how long a harvest takes in each farm (before
    // soil, fertilizer and upgrades). Hydroponic-only recipes have no soil.
    static final class Cloche extends ArcforgeCategory<RecipeHolder<ClocheRecipe>> {
        static final IRecipeHolderType<ClocheRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CLOCHE.get());

        Cloche(IGuiHelper gui) {
            super(TYPE, "cloche", ModBlocks.GLASS_CLOCHE.get(), gui, 160, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ClocheRecipe> holder, IFocusGroup focuses) {
            ClocheRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.seed());
            if (!recipe.hydroponicOnly()) {
                recipe.soil().ifPresent(soil -> builder.addInputSlot(21, 5).setStandardSlotBackground().add(soil));
            }
            int x = 76;
            for (ClocheRecipe.Output output : recipe.results()) {
                var slot = builder.addOutputSlot(x, 5).setStandardSlotBackground().add(output.item().create());
                if (output.chance() < 1.0F) {
                    slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(output.chance() * 100))));
                }
                x += 20;
            }
            if (recipe.seedOutput() > 0 && x <= 136) {
                // The planted item itself (flowers): the seed slot's items, seed_output of each.
                builder.addOutputSlot(x, 5).setStandardSlotBackground()
                        .addItemStacks(recipe.seed().items().map(item -> new ItemStack(item, recipe.seedOutput())).toList());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ClocheRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(RecipeHolder<ClocheRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            ClocheRecipe recipe = holder.value();
            int cell = (int) Math.ceil(recipe.time() / ArcforgeConfig.HYDROPONIC_CELL_SPEED.getAsDouble());
            if (recipe.hydroponicOnly()) {
                text(graphics, Component.translatable("jei.arcforge.cloche.hydroponic_only", seconds(cell)), 0, 28);
                return;
            }
            int cloche = (int) Math.ceil(recipe.time() / ArcforgeConfig.GLASS_CLOCHE_SPEED.getAsDouble());
            int chamber = (int) Math.ceil(recipe.time() / ArcforgeConfig.GROW_CHAMBER_SPEED.getAsDouble());
            text(graphics, Component.translatable("jei.arcforge.cloche.time", seconds(cloche)), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.cloche.faster", seconds(chamber), seconds(cell)), 0, 38);
        }
    }

    // --- The Greenhouse Array ---

    // A Planting Bed's crop: seed and soil -> the harvest, with how long a bed takes on water alone and what the systems
    // add. Every arcforge:cloche recipe that isn't hydroponic-only.
    record GreenhousePlanting(RecipeHolder<ClocheRecipe> holder) {}

    static final class Greenhouse extends ArcforgeCategory<GreenhousePlanting> {
        static final IRecipeType<GreenhousePlanting> TYPE = IRecipeType.create(Arcforge.MODID, "greenhouse", GreenhousePlanting.class);

        Greenhouse(IGuiHelper gui) {
            super(TYPE, "greenhouse", ModBlocks.GREENHOUSE_CONTROLLER.get(), gui, 160, 50);
        }

        static List<GreenhousePlanting> plantings(List<RecipeHolder<ClocheRecipe>> recipes) {
            return recipes.stream().filter(holder -> !holder.value().hydroponicOnly()).map(GreenhousePlanting::new).toList();
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, GreenhousePlanting planting, IFocusGroup focuses) {
            ClocheRecipe recipe = planting.holder().value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.seed());
            recipe.soil().ifPresent(soil -> builder.addInputSlot(21, 5).setStandardSlotBackground().add(soil));
            int x = 76;
            for (ClocheRecipe.Output output : recipe.results()) {
                var slot = builder.addOutputSlot(x, 5).setStandardSlotBackground().add(output.item().create());
                if (output.chance() < 1.0F) {
                    slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(output.chance() * 100))));
                }
                x += 20;
            }
            if (recipe.seedOutput() > 0 && x <= 136) {
                builder.addOutputSlot(x, 5).setStandardSlotBackground()
                        .addItemStacks(recipe.seed().items().map(item -> new ItemStack(item, recipe.seedOutput())).toList());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, GreenhousePlanting planting, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(planting.holder().value().time()).setPosition(46, 5);
        }

        @Override
        public void draw(GreenhousePlanting planting, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            int time = (int) Math.ceil(planting.holder().value().time() / ArcforgeConfig.GREENHOUSE_BASE_SPEED.getAsDouble());
            text(graphics, Component.translatable("jei.arcforge.greenhouse.time", seconds(time)), 0, 28);
            text(graphics, Component.translatable("jei.arcforge.greenhouse.faster"), 0, 38);
        }
    }

    // --- Conduit dyeing (a crafting-table recipe) ---

    record ConduitDyeing(net.minecraft.world.item.Item conduit) {}

    // Eight conduits, a Plastic Sheet and a dye make eight sheathed conduits; the dye and result cycle together.
    static final class ConduitDyeingCategory extends ArcforgeCategory<ConduitDyeing> {
        static final IRecipeType<ConduitDyeing> TYPE = IRecipeType.create(Arcforge.MODID, "conduit_dyeing", ConduitDyeing.class);

        ConduitDyeingCategory(IGuiHelper gui) {
            super(TYPE, "conduit_dyeing", ModItems.PLASTIC_SHEET.get(), gui, 116, 22);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ConduitDyeing recipe, IFocusGroup focuses) {
            builder.addInputSlot(1, 3).setStandardSlotBackground().add(new ItemStack(recipe.conduit(), 8));
            builder.addInputSlot(19, 3).setStandardSlotBackground().add(new ItemStack(ModItems.PLASTIC_SHEET.get()));
            List<ItemStack> dyes = new java.util.ArrayList<>();
            List<ItemStack> results = new java.util.ArrayList<>();
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                dyes.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(color.getSerializedName() + "_dye"))));
                ItemStack result = new ItemStack(recipe.conduit(), 8);
                result.set(net.zagdrath.arcforge.registry.ModDataComponents.CONDUIT_COLOR.get(), color);
                results.add(result);
            }
            var dye = builder.addInputSlot(37, 3).setStandardSlotBackground().addItemStacks(dyes);
            var output = builder.addOutputSlot(95, 3).setStandardSlotBackground().addItemStacks(results);
            builder.createFocusLink(dye, output);
            builder.setShapeless();
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, ConduitDyeing recipe, IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(62, 3);
        }
    }

    // --- Electrolyzer ---

    static final class Electrolyzing extends ArcforgeCategory<RecipeHolder<ElectrolyzingRecipe>> {
        static final IRecipeHolderType<ElectrolyzingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.ELECTROLYZING.get());

        Electrolyzing(IGuiHelper gui) {
            super(TYPE, "electrolyzing", ModBlocks.ELECTROLYZER.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ElectrolyzingRecipe> holder, IFocusGroup focuses) {
            ElectrolyzingRecipe recipe = holder.value();
            var input = builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, recipe.input().amount()), false, 16, 16);
            // A tag lists every fluid in it.
            recipe.input().fluids().forEach(fluid -> input.add(fluid, recipe.input().amount()));
            fluid(builder, false, 61, 5, recipe.primary());
            recipe.secondary().ifPresent(secondary -> fluid(builder, false, 81, 5, secondary));
            recipe.tertiary().ifPresent(tertiary -> fluid(builder, false, 101, 5, tertiary));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ElectrolyzingRecipe> holder, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        // FE before upgrades, and the least Energy upgrades can bring it to (the balance floor).
        @Override
        public void draw(RecipeHolder<ElectrolyzingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            ElectrolyzingRecipe recipe = holder.value();
            int total = recipe.totalEnergy();
            int least = Math.max(EnergyBalance.ceil(total * UpgradeType.energyCostMultiplier(UpgradeType.MAX_PER_MACHINE)),
                    EnergyBalance.minEnergyFor(recipe));
            text(graphics, Component.translatable("jei.arcforge.electrolyzing.energy", String.format(Locale.ROOT, "%,d", total)), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.electrolyzing.floor", String.format(Locale.ROOT, "%,d", least)), 0, 40);
        }
    }

    // --- Thermal Evaporator Array ---

    // The fluid the tower boils down, what it gives (a fluid and/or an item), the water it returns and its by-product, with
    // its heat and the temperature it needs.
    static final class Evaporating extends ArcforgeCategory<RecipeHolder<net.zagdrath.arcforge.recipe.EvaporatingRecipe>> {
        static final IRecipeHolderType<net.zagdrath.arcforge.recipe.EvaporatingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.EVAPORATING.get());

        Evaporating(IGuiHelper gui) {
            super(TYPE, "evaporating", ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.EvaporatingRecipe> holder, IFocusGroup focuses) {
            var recipe = holder.value();
            var input = builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, recipe.input().amount()), false, 16, 16);
            // A tag lists every fluid in it.
            recipe.input().fluids().forEach(fluid -> input.add(fluid, recipe.input().amount()));
            int x = 61;
            if (recipe.fluidResult().isPresent()) {
                fluid(builder, false, x, 5, recipe.fluidResult().get());
                x += 20;
            }
            if (recipe.itemResult().isPresent()) {
                builder.addOutputSlot(x, 5).setStandardSlotBackground().add(recipe.itemResult().get().create());
                x += 20;
            }
            if (recipe.water() > 0) {
                fluid(builder, false, x, 5, net.minecraft.world.level.material.Fluids.WATER, recipe.water());
                x += 20;
            }
            // The by-product (Lithium Brine from Brine), into its own tank.
            if (recipe.byproduct().isPresent()) {
                fluid(builder, false, x, 5, recipe.byproduct().get());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<net.zagdrath.arcforge.recipe.EvaporatingRecipe> holder, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        // The heat an operation takes, and the temperature it needs (full speed from fullSpeedTemperature).
        @Override
        public void draw(RecipeHolder<net.zagdrath.arcforge.recipe.EvaporatingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.evaporating.heat", String.format(Locale.ROOT, "%,d", holder.value().heat())), 0, 30);
            text(graphics, Component.translatable("jei.arcforge.evaporating.temperature", ArcforgeConfig.EVAPORATOR_MIN_TEMPERATURE.getAsInt(),
                    ArcforgeConfig.EVAPORATOR_FULL_SPEED_TEMPERATURE.getAsInt()), 0, 40);
        }
    }

    // --- Chemical Reactor ---

    // Inputs left to right (items, then fluids), the arrow after them, then the outputs.
    static final class ChemicalReacting extends ArcforgeCategory<RecipeHolder<ChemicalReactingRecipe>> {
        static final IRecipeHolderType<ChemicalReactingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CHEMICAL_REACTING.get());
        private static final int WIDTH = 172;

        ChemicalReacting(IGuiHelper gui) {
            super(TYPE, "chemical_reacting", ModBlocks.CHEMICAL_REACTOR.get(), gui, WIDTH, 60);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ChemicalReactingRecipe> holder, IFocusGroup focuses) {
            ChemicalReactingRecipe recipe = holder.value();
            int x = 1;
            for (ChemicalReactingRecipe.ItemInput input : recipe.itemInputs()) {
                builder.addInputSlot(x, 5).setStandardSlotBackground()
                        .addItemStacks(input.ingredient().items().map(item -> new ItemStack(item, input.count())).toList());
                x += 20;
            }
            // Recipes with one item keep their fluids where they always were.
            x = Math.max(x, 21);
            for (int i = 0; i < recipe.fluidInputs().size(); i++) {
                ChemicalReactingRecipe.FluidInput input = recipe.fluidInputs().get(i);
                var slot = builder.addInputSlot(x, 5).setStandardSlotBackground()
                        .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, input.amount()), false, 16, 16);
                // A tag lists every fluid in it.
                input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
                x += 20;
            }
            int out = arrowX(recipe) + 29;
            recipe.itemOutput().ifPresent(output -> builder.addOutputSlot(out, 5).setStandardSlotBackground().add(output));
            recipe.byproduct().ifPresent(byproduct -> builder.addOutputSlot(out + 20, 5).setStandardSlotBackground().add(byproduct)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.byproductChance() * 100)))));
            recipe.fluidOutput().ifPresent(output -> fluid(builder, false, out + 40, 5, output));
            // The liquid by-product, into the reactor's by-product tank.
            recipe.fluidByproduct().ifPresent(output -> fluid(builder, false, out + 60, 5, output));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ChemicalReactingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(arrowX(holder.value()), 5);
        }

        // The arrow sits after the inputs: where it always was (62) unless a second item pushes it along.
        private static int arrowX(ChemicalReactingRecipe recipe) {
            int inputs = Math.max(recipe.itemInputs().size(), 1) + recipe.fluidInputs().size();
            return Math.max(62, 1 + 20 * inputs + 1);
        }

        // Time and FE before upgrades, the category, and for leaching what a raw ore or ore block comes to.
        @Override
        public void draw(RecipeHolder<ChemicalReactingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            ChemicalReactingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.time() * recipe.baseEnergyPerTick())), 0, 30);
            // The category gets its own line; right-aligned beside the cost the two ran together.
            int y = 40;
            if (!recipe.category().equals("general")) {
                text(graphics, Component.translatable("jei.arcforge.chemical_reacting." + recipe.category()), 0, y);
                y += 10;
            }
            int[] yield = leachingYield(recipe);
            if (yield != null) {
                text(graphics, Component.translatable("jei.arcforge.yield", yield[0], yield[1]), 0, y);
            }
        }

        // For a leaching recipe of an ore the Arc Crushing Array multiplies: the dust it comes to here (its
        // slurry over the slurry one dust takes to precipitate) and in the Array. Null otherwise (raw blocks).
        private static int @Nullable [] leachingYield(ChemicalReactingRecipe recipe) {
            if (recipe.itemInput().isEmpty() || recipe.fluidOutput().isEmpty()) {
                return null;
            }
            var recipes = MachineRecipes.clientRecipes();
            FluidResource slurry = FluidResource.of(recipe.fluidOutput().get());
            var precipitating = recipes.byType(ModRecipes.CHEMICAL_REACTING.get()).stream().map(RecipeHolder::value)
                    .filter(other -> other.itemOutput().isPresent() && other.itemInput().isEmpty())
                    .flatMap(other -> other.fluidInputs().stream().filter(input -> input.test(slurry)))
                    .findFirst();
            ItemStack sample = recipe.itemInput().get().ingredient().items().findFirst().map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
            var crushing = recipes.byType(ModRecipes.CRUSHING.get()).stream().map(RecipeHolder::value)
                    .filter(other -> other.ore() && other.result().isPresent() && other.ingredient().test(sample))
                    .findFirst();
            if (precipitating.isEmpty() || crushing.isEmpty()) {
                return null;
            }
            int dust = recipe.fluidOutput().get().amount() / precipitating.get().amount();
            int array = crushing.get().result().get().count() * ArcforgeConfig.ARRAY_ORE_YIELD.getAsInt();
            return new int[] { dust, array };
        }
    }

    // --- Arcforge Furnace ---

    static final class ArcforgeSmelting extends ArcforgeCategory<RecipeHolder<ArcforgeSmeltingRecipe>> {
        static final IRecipeHolderType<ArcforgeSmeltingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.ARCFORGE_SMELTING.get());

        ArcforgeSmelting(IGuiHelper gui) {
            super(TYPE, "arcforge_smelting", ModBlocks.ARCFORGE_FURNACE_PORT.get(), gui, 158, 50);
        }

        // Metal, the two additives (empty for steel) and coke, as in the furnace.
        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ArcforgeSmeltingRecipe> holder, IFocusGroup focuses) {
            ArcforgeSmeltingRecipe recipe = holder.value();
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStacks(counted(recipe.metal()));
            var additive = builder.addInputSlot(19, 5).setStandardSlotBackground();
            recipe.additive().ifPresent(needed -> additive.addItemStacks(counted(needed)));
            var additive2 = builder.addInputSlot(37, 5).setStandardSlotBackground();
            recipe.additive2().ifPresent(needed -> additive2.addItemStacks(counted(needed)));
            builder.addInputSlot(55, 5).setStandardSlotBackground().add(new ItemStack(ModItems.COAL_COKE.get(), Math.max(1, recipe.coke())));
            builder.addOutputSlot(113, 5).setStandardSlotBackground().add(recipe.result());
            recipe.byproduct().ifPresent(byproduct -> builder.addOutputSlot(137, 5).setStandardSlotBackground().add(byproduct));
        }

        private static List<ItemStack> counted(ArcforgeSmeltingRecipe.Counted ingredient) {
            return ingredient.ingredient().items().map(item -> new ItemStack(item, ingredient.count())).toList();
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ArcforgeSmeltingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(80, 5);
        }

        @Override
        public void draw(RecipeHolder<ArcforgeSmeltingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.arcforge_smelting.heat", holder.value().minHeat()), 0, 30);
            textRight(graphics, seconds(holder.value().time()), 158, 30);
            text(graphics, Component.translatable("jei.arcforge.arcforge_smelting.oxygen", ArcforgeConfig.FURNACE_OXYGEN_PER_SMELT.getAsInt(),
                    ArcforgeFurnaceMenu.boostText(ArcforgeConfig.FURNACE_OXYGEN_SPEED.getAsDouble())), 0, 40);
        }
    }

    // --- Steam: what each grade takes to boil and gives in a turbine ---

    record SteamRecipe(SteamGrade grade) {}

    static final class Steam extends ArcforgeCategory<SteamRecipe> {
        static final IRecipeType<SteamRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "steam", SteamRecipe.class);

        Steam(IGuiHelper gui) {
            super(TYPE, "steam", ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), gui, 150, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SteamRecipe recipe, IFocusGroup focuses) {
            fluid(builder, true, 1, 5, Fluids.WATER, 1_000);
            fluid(builder, false, 61, 5, recipe.grade().fluid(), 1_000);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, SteamRecipe recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        @Override
        public void draw(SteamRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            SteamGrade grade = recipe.grade();
            // What the Steam Boiler Array pays per mB (after its heat cost multiplier) and the Steam Turbine Array makes.
            String huPerMb = String.format(Locale.ROOT, "%.0f", grade.huPerMb() * ArcforgeConfig.BOILER_ARRAY_HEAT_COST.getAsDouble());
            text(graphics, Component.translatable("jei.arcforge.steam.boil", grade.minCelsius(), huPerMb), 0, 30);
            // Times the power multiplier, as the turbine makes it.
            String fePerMb = String.format(Locale.ROOT, "%.1f", grade.arrayFePerMb() * PowerGeneration.multiplier()).replaceAll("\\.0$", "");
            text(graphics, Component.translatable("jei.arcforge.steam.turbine", fePerMb), 0, 40);
        }
    }

    // --- Superheater Array: each grade step, its HU per mB and the temperature it needs ---

    record SuperheatingRecipe(SteamGrade from, SteamGrade to) {
        static List<SuperheatingRecipe> all() {
            return List.of(new SuperheatingRecipe(SteamGrade.STEAM, SteamGrade.HIGH_PRESSURE),
                    new SuperheatingRecipe(SteamGrade.HIGH_PRESSURE, SteamGrade.SUPERHEATED),
                    new SuperheatingRecipe(SteamGrade.STEAM, SteamGrade.SUPERHEATED));
        }
    }

    static final class Superheating extends ArcforgeCategory<SuperheatingRecipe> {
        static final IRecipeType<SuperheatingRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "superheating", SuperheatingRecipe.class);

        Superheating(IGuiHelper gui) {
            super(TYPE, "superheating", ModBlocks.SUPERHEATER_ARRAY_CASING.get(), gui, 150, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SuperheatingRecipe recipe, IFocusGroup focuses) {
            fluid(builder, true, 1, 5, recipe.from().fluid(), 1_000);
            fluid(builder, false, 61, 5, recipe.to().fluid(), 1_000);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, SuperheatingRecipe recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        @Override
        public void draw(SuperheatingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            String cost = String.format(Locale.ROOT, "%.0f", SuperheaterArrayBlockEntity.cost(recipe.from(), recipe.to()));
            text(graphics, Component.translatable("jei.arcforge.superheating.cost", cost, recipe.to().minCelsius()), 0, 30);
        }
    }

    // --- Condenser Array: Exhaust Steam back to water, 1:1 ---

    record CondensingRecipe() {}

    static final class Condensing extends ArcforgeCategory<CondensingRecipe> {
        static final IRecipeType<CondensingRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "condensing", CondensingRecipe.class);

        Condensing(IGuiHelper gui) {
            super(TYPE, "condensing", ModBlocks.CONDENSER_ARRAY_CASING.get(), gui, 150, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CondensingRecipe recipe, IFocusGroup focuses) {
            fluid(builder, true, 1, 5, ModFluids.EXHAUST_STEAM.get(), 1_000);
            fluid(builder, false, 61, 5, Fluids.WATER, 1_000);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, CondensingRecipe recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
        }

        // From open air up to the most any cooling gives.
        @Override
        public void draw(CondensingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.arcforge.condensing.rate", ArcforgeConfig.CONDENSER_BASE_RATE_PER_CUBE.getAsInt(),
                    ArcforgeConfig.CONDENSER_MAX_RATE_PER_CUBE.getAsInt()), 0, 30);
        }
    }

    // --- Fuel Burner fuels (the arcforge:burner_fuels data map) ---

    // --- Distillation Array: one page per column height ---

    record DistillingPage(DistillingRecipe recipe, int height) {
        static List<DistillingPage> of(RecipeHolder<DistillingRecipe> holder) {
            return holder.value().byHeight().keySet().stream().sorted().map(height -> new DistillingPage(holder.value(), height)).toList();
        }
    }

    static final class Distilling extends ArcforgeCategory<DistillingPage> {
        static final IRecipeType<DistillingPage> TYPE = IRecipeType.create(Arcforge.MODID, "distilling", DistillingPage.class);

        Distilling(IGuiHelper gui) {
            super(TYPE, "distilling", ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get(), gui, 150, 58);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, DistillingPage page, IFocusGroup focuses) {
            DistillingRecipe recipe = page.recipe();
            fluid(builder, true, 1, 5, recipe.input(), recipe.amount());
            int x = 56;
            for (var output : recipe.outputs(page.height(), 0.0F).entrySet()) {
                fluid(builder, false, x, 5, output.getKey(), output.getValue());
                x += 20;
            }
            int items = recipe.items(page.height());
            if (items > 0) {
                builder.addOutputSlot(x, 5).setStandardSlotBackground().add(new ItemStack(recipe.itemOutput(), items));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, DistillingPage page, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(ticks(page)).setPosition(24, 5);
        }

        private static int ticks(DistillingPage page) {
            int feed = ArcforgeConfig.DISTILLATION_FEED_RATE.getAsInt() * page.height() / 4;
            return (int) Math.ceil((double) page.recipe().amount() / Math.max(1, feed));
        }

        @Override
        public void draw(DistillingPage page, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            DistillingRecipe recipe = page.recipe();
            text(graphics, Component.translatable("jei.arcforge.distilling.column", page.height(), seconds(ticks(page))), 0, 26);
            text(graphics, Component.translatable("jei.arcforge.distilling.heat", recipe.heat(), recipe.minTemp()), 0, 36);
            // Steam stripping on its own line: beside the column text it ran into it.
            recipe.steamStripping().ifPresent(stripping -> text(graphics, Component.translatable("jei.arcforge.distilling.steam",
                    Math.round(recipe.bonusFor(SteamGrade.SUPERHEATED) * 100), stripping.boosts().getFluidType().getDescription()), 0, 46));
        }
    }

    record BurnerFuelRecipe(Fluid fluid, BurnerFuel fuel) {}

    static final class BurnerFuels extends ArcforgeCategory<BurnerFuelRecipe> {
        static final IRecipeType<BurnerFuelRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "burner_fuel", BurnerFuelRecipe.class);

        BurnerFuels(IGuiHelper gui) {
            super(TYPE, "burner_fuel", ModBlocks.FUEL_BURNER.get(), gui, 140, 51);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BurnerFuelRecipe recipe, IFocusGroup focuses) {
            fluid(builder, true, 1, 7, recipe.fluid(), 1_000);
        }

        @Override
        public void draw(BurnerFuelRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            BurnerFuel fuel = recipe.fuel();
            text(graphics, Component.translatable("jei.arcforge.burner_fuel.value", fuel.huPerMb()), 24, 5);
            text(graphics, Component.translatable("jei.arcforge.burner_fuel.rate",
                    String.format(Locale.ROOT, "%.2f", fuel.mbPerTick()).replaceAll("0+$", "").replaceAll("\\.$", ""),
                    Math.round(fuel.huPerMb() * fuel.mbPerTick())), 24, 16);
            text(graphics, Component.translatable("gui.arcforge.burns_at",
                    fuel.burnTemperature(ArcforgeConfig.FUEL_BURNER_MAX_TEMPERATURE.getAsInt())), 24, 27);
            if (!fuel.gasTurbine()) {
                text(graphics, Component.translatable("jei.arcforge.gas_turbine.not_accepted"), 24, 38);
            }
        }
    }

    // --- Battery Array components ---

    // A Lithium Cell (what it stores) or a Power Regulator (the transfer it adds), by tier.
    record BatteryComponentRecipe(net.minecraft.world.item.Item item, net.zagdrath.arcforge.conduit.ConduitTier tier, boolean cell) {}

    static final class BatteryComponents extends ArcforgeCategory<BatteryComponentRecipe> {
        static final IRecipeType<BatteryComponentRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "battery_component", BatteryComponentRecipe.class);

        BatteryComponents(IGuiHelper gui) {
            super(TYPE, "battery_component", ModBlocks.BATTERY_ARRAY_CONTROLLER.get(), gui, 150, 30);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BatteryComponentRecipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(1, 6).setStandardSlotBackground().add(new ItemStack(recipe.item()));
        }

        @Override
        public void draw(BatteryComponentRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            if (recipe.cell()) {
                text(graphics, Component.translatable("jei.arcforge.battery_component.capacity",
                        String.format(Locale.ROOT, "%,d", ArcforgeConfig.lithiumCellCapacity(recipe.tier()))), 24, 5);
                text(graphics, Component.translatable("jei.arcforge.battery_component.cell_hint"), 24, 16);
            } else {
                text(graphics, Component.translatable("jei.arcforge.battery_component.transfer",
                        String.format(Locale.ROOT, "%,d", ArcforgeConfig.powerRegulatorTransfer(recipe.tier()))), 24, 5);
                text(graphics, Component.translatable("jei.arcforge.battery_component.regulator_hint",
                        String.format(Locale.ROOT, "%,d", ArcforgeConfig.BATTERY_BASE_TRANSFER.getAsInt())), 24, 16);
            }
        }
    }

    // --- Gas Turbine Array fuels ---

    static final class GasTurbineFuels extends ArcforgeCategory<BurnerFuelRecipe> {
        static final IRecipeType<BurnerFuelRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "gas_turbine_fuel", BurnerFuelRecipe.class);

        GasTurbineFuels(IGuiHelper gui) {
            super(TYPE, "gas_turbine_fuels", ModBlocks.GAS_TURBINE_ARRAY_CASING.get(), gui, 150, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BurnerFuelRecipe recipe, IFocusGroup focuses) {
            fluid(builder, true, 1, 7, recipe.fluid(), 1_000);
        }

        @Override
        public void draw(BurnerFuelRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            BurnerFuel fuel = recipe.fuel();
            int celsius = GasTurbineArrayBlockEntity.burnTemperature(fuel);
            String fePerMb = String.format(Locale.ROOT, "%.1f", GasTurbineArrayBlockEntity.fePerMb(fuel)).replaceAll("\\.0$", "");
            text(graphics, Component.translatable("jei.arcforge.gas_turbine.fe_per_mb", fePerMb,
                    Math.round(GasTurbineArrayBlockEntity.efficiency(celsius) * 100)), 24, 5);
            text(graphics, Component.translatable("jei.arcforge.gas_turbine.exhaust",
                    Math.round(fuel.huPerMb() * ArcforgeConfig.GAS_TURBINE_EXHAUST_FRACTION.getAsDouble()),
                    GasTurbineArrayBlockEntity.exhaustCelsius(fuel)), 24, 16);
            text(graphics, Component.translatable("gui.arcforge.burns_at", celsius), 24, 27);
        }
    }
}
