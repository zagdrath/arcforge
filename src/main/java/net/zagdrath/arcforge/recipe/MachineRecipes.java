/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.registry.ModRecipes;

// Recipe lookups for Arcforge machines that work on both sides: the server reads its recipe manager,
// the client the recipes the server synced (see ModRecipes), so GUI slots agree on what they accept.
public final class MachineRecipes {
    private static volatile RecipeMap clientRecipes = RecipeMap.EMPTY;

    private MachineRecipes() {}

    // Called by the client when recipes arrive, and with RecipeMap.EMPTY on logout.
    public static void setClientRecipes(RecipeMap recipes) {
        clientRecipes = recipes;
    }

    // The recipes the server synced, for client-side displays (JEI, the Engineer's Handbook).
    public static RecipeMap clientRecipes() {
        return clientRecipes;
    }

    private static RecipeMap recipes(@Nullable Level level) {
        return level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess().recipeMap() : clientRecipes;
    }

    public static Optional<RecipeHolder<CarbonizingRecipe>> carbonizing(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.CARBONIZING.get(), new SingleRecipeInput(input), level);
    }

    public static Optional<RecipeHolder<ArcforgeSmeltingRecipe>> arcforgeSmelting(ServerLevel level, ItemStack metal, ItemStack additive, ItemStack additive2) {
        return level.recipeAccess().getRecipeFor(ModRecipes.ARCFORGE_SMELTING.get(), new ArcforgeSmeltingRecipe.Input(metal, additive, additive2), level);
    }

    // With the second additive slot empty.
    public static Optional<RecipeHolder<ArcforgeSmeltingRecipe>> arcforgeSmelting(ServerLevel level, ItemStack metal, ItemStack additive) {
        return arcforgeSmelting(level, metal, additive, ItemStack.EMPTY);
    }

    public static Optional<RecipeHolder<CrushingRecipe>> crushing(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.CRUSHING.get(), new SingleRecipeInput(input), level);
    }

    public static Optional<RecipeHolder<FiberizingRecipe>> fiberizing(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.FIBERIZING.get(), new SingleRecipeInput(input), level);
    }

    // The infusing recipe for this item, preferring one that the additive slot can supply, then one that uses the fluid
    // in the tank. Returns one for another fluid or additive if that's all there is, so the Infuser can say what's missing.
    public static Optional<RecipeHolder<InfusingRecipe>> infusing(ServerLevel level, ItemStack input, FluidResource fluid, ItemStack additive) {
        List<RecipeHolder<InfusingRecipe>> matches = level.recipeAccess().recipeMap().byType(ModRecipes.INFUSING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .toList();
        return matches.stream().filter(holder -> holder.value().hasAdditive(additive)).findFirst()
                .or(() -> matches.stream().filter(holder -> holder.value().usesFluid(fluid)).findFirst())
                .or(() -> matches.stream().findFirst());
    }

    // With nothing in the additive slot.
    public static Optional<RecipeHolder<InfusingRecipe>> infusing(ServerLevel level, ItemStack input, FluidResource fluid) {
        return infusing(level, input, fluid, ItemStack.EMPTY);
    }

    // The pressing recipe this die makes from this input, if there are enough of it.
    public static Optional<RecipeHolder<PressingRecipe>> pressing(ServerLevel level, ItemStack die, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.PRESSING.get(), new PressingRecipe.Input(die, input), level);
    }

    // Anything some die presses: what a Metal Press input slot takes from a player.
    public static boolean isPressingInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.PRESSING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    // Whether this die presses this item: what automation may put in beside it.
    public static boolean isPressingInput(@Nullable Level level, ItemStack die, ItemStack stack) {
        return !die.isEmpty() && recipes(level).byType(ModRecipes.PRESSING.get()).stream()
                .anyMatch(holder -> holder.value().accepts(die, stack));
    }

    // Vanilla furnace recipes (and any mod's), for the Induction Furnaces.
    public static Optional<RecipeHolder<SmeltingRecipe>> smelting(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    public static boolean isSmeltingInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(RecipeType.SMELTING).stream()
                .anyMatch(holder -> holder.value().input().test(stack));
    }

    public static boolean isCrusherInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.CRUSHING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    // The melting recipe for this item. Works on the client too (null level), for the GUI's tooltip.
    public static Optional<RecipeHolder<MeltingRecipe>> melting(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.MELTING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isMelterInput(@Nullable Level level, ItemStack stack) {
        return melting(level, stack).isPresent();
    }

    public static Optional<RecipeHolder<FermentingRecipe>> fermenting(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.FERMENTING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isFermenterInput(@Nullable Level level, ItemStack stack) {
        return fermenting(level, stack).isPresent();
    }

    // The electrolyzing recipe for this fluid, if any. Works on the client too (null level), for the GUI.
    public static Optional<RecipeHolder<ElectrolyzingRecipe>> electrolyzing(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.ELECTROLYZING.get()).stream()
                .filter(holder -> holder.value().input().test(fluid))
                .findFirst();
    }

    public static boolean isElectrolyzerInput(@Nullable Level level, FluidResource fluid) {
        return electrolyzing(level, fluid).isPresent();
    }

    // The chemical reacting recipe for what the reactor holds, if any.
    // When several match, the one that uses the most inputs wins: Seed Oil, Ethanol and Lye together make the Lye
    // biodiesel, not the plain one that ignores the Lye.
    public static Optional<RecipeHolder<ChemicalReactingRecipe>> chemicalReacting(@Nullable Level level, ChemicalReactorInput input) {
        return recipes(level).byType(ModRecipes.CHEMICAL_REACTING.get()).stream()
                .filter(holder -> holder.value().matches(input, level))
                .max(java.util.Comparator.comparingInt(holder -> holder.value().itemInputs().size() + holder.value().fluidInputs().size()));
    }

    // Anything some chemical reacting recipe takes as its item: what the reactor's input slot takes.
    public static boolean isReactorItem(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.CHEMICAL_REACTING.get()).stream()
                .anyMatch(holder -> holder.value().usesItem(stack));
    }

    // Fluids some chemical reacting recipe takes: the only ones the reactor's input tanks take.
    public static boolean isReactorFluid(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.CHEMICAL_REACTING.get()).stream()
                .anyMatch(holder -> holder.value().usesFluid(fluid));
    }

    public static boolean isFiberizerInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.FIBERIZING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    public static boolean isInfuserInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.INFUSING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    // Items some infusing recipe takes as its additive (Pine Resin): what the additive slot takes.
    public static boolean isInfuserAdditive(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && recipes(level).byType(ModRecipes.INFUSING.get()).stream()
                .anyMatch(holder -> holder.value().usesAdditive(stack));
    }

    // Fluids some infusing recipe uses: the only ones the Infuser's tank takes.
    public static boolean isInfuserFluid(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.INFUSING.get()).stream()
                .anyMatch(holder -> holder.value().usesFluid(fluid));
    }

    // The distilling recipe for this feed, if any.
    public static Optional<RecipeHolder<DistillingRecipe>> distilling(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.DISTILLING.get()).stream()
                .filter(holder -> holder.value().usesFluid(fluid))
                .findFirst();
    }

    // Fluids some distilling recipe takes: the only ones the Distillation Array's feed tank takes.
    public static boolean isDistillingFeed(@Nullable Level level, FluidResource fluid) {
        return distilling(level, fluid).isPresent();
    }

    public static boolean isCarbonizerInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.CARBONIZING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    // The metal of some Arcforge Furnace recipe: what its metal slot takes.
    public static boolean isArcforgeMetal(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.ARCFORGE_SMELTING.get()).stream()
                .anyMatch(holder -> holder.value().metal().is(stack));
    }

    // An additive (either one) of some Arcforge Furnace recipe: what its additive slots take.
    public static boolean isArcforgeAdditive(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.ARCFORGE_SMELTING.get()).stream()
                .anyMatch(holder -> holder.value().isAdditive(stack));
    }

    // --- Farm processing ---

    public static Optional<RecipeHolder<MillingRecipe>> milling(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.MILLING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isMillingInput(@Nullable Level level, ItemStack stack) {
        return milling(level, stack).isPresent();
    }

    public static Optional<RecipeHolder<OilPressingRecipe>> oilPressing(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.OIL_PRESSING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isOilPressInput(@Nullable Level level, ItemStack stack) {
        return oilPressing(level, stack).isPresent();
    }

    public static Optional<RecipeHolder<SeedExtractingRecipe>> seedExtracting(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.SEED_EXTRACTING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isSeedExtractorInput(@Nullable Level level, ItemStack stack) {
        return seedExtracting(level, stack).isPresent();
    }

    public static Optional<RecipeHolder<DryingRecipe>> drying(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.DRYING.get()).stream()
                .filter(holder -> holder.value().testItem(input))
                .findFirst();
    }

    public static boolean isDryerInput(@Nullable Level level, ItemStack stack) {
        return drying(level, stack).isPresent();
    }

    // The drying recipe for the fluid in the Grain Dryer's tank (Latex), if any.
    public static Optional<RecipeHolder<DryingRecipe>> dryingFluid(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.DRYING.get()).stream()
                .filter(holder -> holder.value().testFluid(fluid))
                .findFirst();
    }

    // Fluids some drying recipe takes: the only ones the Grain Dryer's tank takes.
    public static boolean isDryerFluid(@Nullable Level level, FluidResource fluid) {
        return !fluid.isEmpty() && dryingFluid(level, fluid).isPresent();
    }

    // --- Hydrothermal Carbonizer ---

    public static Optional<RecipeHolder<HydrothermalCarbonizingRecipe>> hydrothermalCarbonizing(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.HYDROTHERMAL_CARBONIZING.get()).stream()
                .filter(holder -> holder.value().test(input))
                .findFirst();
    }

    public static boolean isHydrothermalInput(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && hydrothermalCarbonizing(level, stack).isPresent();
    }

    // --- Carbon capture ---

    // The first Carbon Reclaimer recipe the two gas tanks (mB of Carbon Dioxide and Hydrogen) can supply.
    public static Optional<RecipeHolder<CarbonReclaimingRecipe>> carbonReclaiming(@Nullable Level level, CarbonReclaimingRecipe.Input input) {
        return recipes(level).byType(ModRecipes.CARBON_RECLAIMING.get()).stream()
                .filter(holder -> holder.value().matches(input, level))
                .findFirst();
    }

    // The first Carbon Reclaimer recipe, whether or not the tanks can supply it (for the GUI's floor while idle).
    public static Optional<RecipeHolder<CarbonReclaimingRecipe>> anyCarbonReclaiming(@Nullable Level level) {
        return recipes(level).byType(ModRecipes.CARBON_RECLAIMING.get()).stream().findFirst();
    }

    public static Optional<RecipeHolder<GasifyingRecipe>> gasifying(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.GASIFYING.get()).stream()
                .filter(holder -> holder.value().test(input))
                .findFirst();
    }

    public static boolean isGasifierInput(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && gasifying(level, stack).isPresent();
    }

    // The Fischer-Tropsch recipe the Syngas tank can supply, if any.
    public static Optional<RecipeHolder<FischerTropschRecipe>> fischerTropsch(@Nullable Level level, FischerTropschRecipe.Input input) {
        return recipes(level).byType(ModRecipes.FISCHER_TROPSCH.get()).stream()
                .filter(holder -> holder.value().matches(input, level))
                .findFirst();
    }

    // Fluids some Fischer-Tropsch recipe takes: what the reactor's Syngas tank takes.
    public static boolean isFischerTropschInput(@Nullable Level level, FluidResource fluid) {
        return !fluid.isEmpty() && recipes(level).byType(ModRecipes.FISCHER_TROPSCH.get()).stream()
                .anyMatch(holder -> holder.value().takes(fluid));
    }

    // --- Sifter and Diamond Press ---

    public static Optional<RecipeHolder<SiftingRecipe>> sifting(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.SIFTING.get()).stream()
                .filter(holder -> holder.value().test(input))
                .findFirst();
    }

    public static boolean isSifterInput(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && sifting(level, stack).isPresent();
    }

    public static Optional<RecipeHolder<DiamondPressingRecipe>> diamondPressing(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.DIAMOND_PRESSING.get()).stream()
                .filter(holder -> holder.value().test(input))
                .findFirst();
    }

    public static boolean isDiamondPressInput(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && diamondPressing(level, stack).isPresent();
    }

    // --- Vulcanizer ---

    // The vulcanizing recipe the two input slots can supply (either way round), if any.
    public static Optional<RecipeHolder<VulcanizingRecipe>> vulcanizing(@Nullable Level level, ItemStack a, ItemStack b) {
        return recipes(level).byType(ModRecipes.VULCANIZING.get()).stream()
                .filter(holder -> holder.value().slotsFor(a, b) != null)
                .findFirst();
    }

    // Anything some vulcanizing recipe takes: what the Vulcanizer's input slots take.
    public static boolean isVulcanizerInput(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && recipes(level).byType(ModRecipes.VULCANIZING.get()).stream()
                .anyMatch(holder -> holder.value().takes(stack));
    }

    // --- Farm chemistry ---

    // The air separating recipe for the dimension the Air Separator is in, if any (none in the End).
    public static Optional<RecipeHolder<AirSeparatingRecipe>> airSeparating(@Nullable Level level, ResourceKey<Level> dimension) {
        return recipes(level).byType(ModRecipes.AIR_SEPARATING.get()).stream()
                .filter(holder -> holder.value().worksIn(dimension))
                .findFirst();
    }

    // The synthesizing recipe for what the Haber Reactor's input tanks hold, if any.
    public static Optional<RecipeHolder<SynthesizingRecipe>> synthesizing(@Nullable Level level, SynthesizingRecipe.Input input) {
        return recipes(level).byType(ModRecipes.SYNTHESIZING.get()).stream()
                .filter(holder -> holder.value().tanksFor(input) != null)
                .findFirst();
    }

    // Gases some synthesizing recipe takes: the only ones the Haber Reactor's input tanks take.
    public static boolean isSynthesizingInput(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.SYNTHESIZING.get()).stream()
                .anyMatch(holder -> holder.value().usesFluid(fluid));
    }

    public static Optional<RecipeHolder<DigestingRecipe>> digesting(@Nullable Level level, ItemStack input) {
        return recipes(level).byType(ModRecipes.DIGESTING.get()).stream()
                .filter(holder -> holder.value().ingredient().test(input))
                .findFirst();
    }

    public static boolean isDigesterInput(@Nullable Level level, ItemStack stack) {
        return digesting(level, stack).isPresent();
    }

    // --- Thermal Evaporator Array ---

    // The evaporating recipe for what the tower holds, if any.
    public static Optional<RecipeHolder<EvaporatingRecipe>> evaporating(@Nullable Level level, FluidResource fluid) {
        return recipes(level).byType(ModRecipes.EVAPORATING.get()).stream()
                .filter(holder -> holder.value().input().test(fluid))
                .findFirst();
    }

    // Fluids the tower takes: those some evaporating recipe boils down.
    public static boolean isEvaporatingInput(@Nullable Level level, FluidResource fluid) {
        return evaporating(level, fluid).isPresent();
    }

    // --- Automated farms ---

    // The cloche recipe that grows this seed in this soil (any soil hydroponically), if any. See ClochePlants.find for the
    // fallback to vanilla-style crops.
    public static Optional<RecipeHolder<ClocheRecipe>> cloche(@Nullable Level level, ItemStack seed, ItemStack soil, boolean hydroponic) {
        return recipes(level).byType(ModRecipes.CLOCHE.get()).stream()
                .filter(holder -> holder.value().grows(seed, soil, hydroponic))
                .findFirst();
    }

    // Whether some cloche recipe names this seed (in any soil, in any farm).
    public static boolean isClocheSeed(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && recipes(level).byType(ModRecipes.CLOCHE.get()).stream()
                .anyMatch(holder -> holder.value().seed().test(stack));
    }

    // Whether some cloche recipe grows in this soil (or it's a farmland soil, where fallback crops grow).
    public static boolean isClocheSoil(@Nullable Level level, ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ClochePlants.FARMLAND_SOILS) || recipes(level).byType(ModRecipes.CLOCHE.get()).stream()
                .anyMatch(holder -> holder.value().soil().isPresent() && holder.value().soil().get().test(stack)));
    }
}
