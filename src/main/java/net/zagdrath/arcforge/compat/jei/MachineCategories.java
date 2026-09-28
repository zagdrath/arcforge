/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

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
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.CrushingRecipe;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.FiberizingRecipe;
import net.zagdrath.arcforge.recipe.InfusingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.MeltingRecipe;
import net.zagdrath.arcforge.recipe.PressingRecipe;
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
            super(TYPE, "fiberizing", ModBlocks.FIBERIZER.get(), gui, 116, 40);
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
            textRight(graphics, seconds(recipe.time()), 116, 9);
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
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(holder.value().ingredient());
            fluid(builder, true, 21, 5, holder.value().fluid());
            builder.addOutputSlot(81, 5).setStandardSlotBackground().add(holder.value().result());
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

    // --- Chemical Reactor ---

    static final class ChemicalReacting extends ArcforgeCategory<RecipeHolder<ChemicalReactingRecipe>> {
        static final IRecipeHolderType<ChemicalReactingRecipe> TYPE = IRecipeHolderType.create(ModRecipes.CHEMICAL_REACTING.get());
        private static final int WIDTH = 152;

        ChemicalReacting(IGuiHelper gui) {
            super(TYPE, "chemical_reacting", ModBlocks.CHEMICAL_REACTOR.get(), gui, WIDTH, 50);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ChemicalReactingRecipe> holder, IFocusGroup focuses) {
            ChemicalReactingRecipe recipe = holder.value();
            recipe.itemInput().ifPresent(input -> builder.addInputSlot(1, 5).setStandardSlotBackground()
                    .addItemStacks(input.ingredient().items().map(item -> new ItemStack(item, input.count())).toList()));
            for (int i = 0; i < recipe.fluidInputs().size(); i++) {
                ChemicalReactingRecipe.FluidInput input = recipe.fluidInputs().get(i);
                var slot = builder.addInputSlot(21 + 20 * i, 5).setStandardSlotBackground()
                        .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, input.amount()), false, 16, 16);
                // A tag lists every fluid in it.
                input.fluids().forEach(fluid -> slot.add(fluid, input.amount()));
            }
            recipe.itemOutput().ifPresent(output -> builder.addOutputSlot(91, 5).setStandardSlotBackground().add(output));
            recipe.byproduct().ifPresent(byproduct -> builder.addOutputSlot(111, 5).setStandardSlotBackground().add(byproduct)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.arcforge.chance",
                            Math.round(recipe.byproductChance() * 100)))));
            recipe.fluidOutput().ifPresent(output -> fluid(builder, false, 131, 5, output));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ChemicalReactingRecipe> holder, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(holder.value().time()).setPosition(62, 5);
        }

        // Time and FE before upgrades, the category, and for leaching what a raw ore or ore block comes to.
        @Override
        public void draw(RecipeHolder<ChemicalReactingRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            ChemicalReactingRecipe recipe = holder.value();
            text(graphics, Component.translatable("jei.arcforge.melting.cost", seconds(recipe.time()),
                    String.format(Locale.ROOT, "%,d", recipe.time() * recipe.baseEnergyPerTick())), 0, 30);
            if (!recipe.category().equals("general")) {
                textRight(graphics, Component.translatable("jei.arcforge.chemical_reacting." + recipe.category()), WIDTH, 30);
            }
            int[] yield = leachingYield(recipe);
            if (yield != null) {
                text(graphics, Component.translatable("jei.arcforge.yield", yield[0], yield[1]), 0, 40);
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
            super(TYPE, "arcforge_smelting", ModBlocks.ARCFORGE_FURNACE_PORT.get(), gui, 158, 40);
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
        }
    }

    // --- Steam: what each grade takes to boil and gives in a turbine ---

    record SteamRecipe(SteamGrade grade) {}

    static final class Steam extends ArcforgeCategory<SteamRecipe> {
        static final IRecipeType<SteamRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "steam", SteamRecipe.class);

        Steam(IGuiHelper gui) {
            super(TYPE, "steam", ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), gui, 150, 46);
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
            text(graphics, Component.translatable("jei.arcforge.steam.boil", grade.minCelsius(), huPerMb), 84, 5);
            text(graphics, Component.translatable("jei.arcforge.steam.turbine", grade.arrayFePerMb()), 0, 30);
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
            super(TYPE, "superheating", ModBlocks.SUPERHEATER_ARRAY_CASING.get(), gui, 150, 30);
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
            text(graphics, Component.translatable("jei.arcforge.superheating.cost", cost, recipe.to().minCelsius()), 84, 9);
        }
    }

    // --- Condenser Array: Exhaust Steam back to water, 1:1 ---

    record CondensingRecipe() {}

    static final class Condensing extends ArcforgeCategory<CondensingRecipe> {
        static final IRecipeType<CondensingRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "condensing", CondensingRecipe.class);

        Condensing(IGuiHelper gui) {
            super(TYPE, "condensing", ModBlocks.CONDENSER_ARRAY_CASING.get(), gui, 150, 30);
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
            text(graphics, Component.translatable("jei.arcforge.condensing.rate", ArcforgeConfig.CONDENSER_BASE_RATE.getAsInt(),
                    ArcforgeConfig.CONDENSER_MAX_RATE.getAsInt()), 84, 9);
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
            super(TYPE, "distilling", ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get(), gui, 150, 52);
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
            if (recipe.steamStripping().isPresent()) {
                int best = Math.round(recipe.bonusFor(SteamGrade.SUPERHEATED) * 100);
                textRight(graphics, Component.translatable("jei.arcforge.distilling.steam", best), 150, 26);
            }
        }
    }

    record BurnerFuelRecipe(Fluid fluid, BurnerFuel fuel) {}

    static final class BurnerFuels extends ArcforgeCategory<BurnerFuelRecipe> {
        static final IRecipeType<BurnerFuelRecipe> TYPE = IRecipeType.create(Arcforge.MODID, "burner_fuel", BurnerFuelRecipe.class);

        BurnerFuels(IGuiHelper gui) {
            super(TYPE, "burner_fuel", ModBlocks.FUEL_BURNER.get(), gui, 140, 30);
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
            textRight(graphics, Component.translatable("gui.arcforge.burns_at",
                    fuel.burnTemperature(ArcforgeConfig.FUEL_BURNER_MAX_TEMPERATURE.getAsInt())), 140, 5);
        }
    }
}
