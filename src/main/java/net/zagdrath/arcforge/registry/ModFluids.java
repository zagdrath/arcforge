/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.chemistry.OreSlurry;

// Creosote: the Carbonizer's by-product. A slow, oily liquid; not flammable in the world (yet). The
// Distillation Array splits it into Naphtha (thin, and flammable in the world), Light Oil and Heavy Oil
// (thick, slow as lava). Sulfuric Acid and the ore slurries are the Chemical Reactor's. Their textures and
// fog colours are registered on the client in ArcforgeClient.
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Arcforge.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Arcforge.MODID);

    public static final DeferredHolder<FluidType, FluidType> CREOSOTE_TYPE = FLUID_TYPES.register("creosote", () -> new LiquidType(FluidType.Properties.create()
            .descriptionId("fluid_type.arcforge.creosote")
            .density(1_200)
            .viscosity(3_000)
            .temperature(300)
            .motionScale(0.007)
            .canSwim(true)
            .canDrown(true)
            .canPushEntity(true)
            .canExtinguish(false)
            .canConvertToSource(false)
            .supportsBoating(false)
            .fallDistanceModifier(0.0F)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.02F, 0.65, 0.8));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREOSOTE = FLUIDS.register("creosote",
            () -> new BaseFlowingFluid.Source(creosoteProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CREOSOTE = FLUIDS.register("flowing_creosote",
            () -> new BaseFlowingFluid.Flowing(creosoteProperties()));

    // The Distillation Array's fractions, lightest first. Naphtha runs like water; Heavy Oil creeps like lava.
    public static final DeferredHolder<FluidType, FluidType> NAPHTHA_TYPE = liquidType("naphtha", 700, 500, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<FluidType, FluidType> LIGHT_OIL_TYPE = liquidType("light_oil", 850, 1_500, 0.02F, 0.7, 0.8);
    public static final DeferredHolder<FluidType, FluidType> HEAVY_OIL_TYPE = liquidType("heavy_oil", 1_050, 6_000, 0.015F, 0.5, 0.6);

    // The Fermenter's product: pale, thin and flammable.
    public static final DeferredHolder<FluidType, FluidType> ETHANOL_TYPE = liquidType("ethanol", 790, 1_200, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHANOL = FLUIDS.register("ethanol",
            () -> new BaseFlowingFluid.Source(ethanolProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_ETHANOL = FLUIDS.register("flowing_ethanol",
            () -> new BaseFlowingFluid.Flowing(ethanolProperties()));

    // The Oil Press's product: a pressed vegetable oil, thicker than water. It lubricates turbines like Heavy Oil
    // (#arcforge:lubricants).
    public static final DeferredHolder<FluidType, FluidType> SEED_OIL_TYPE = liquidType("seed_oil", 920, 3_000, 0.018F, 0.6, 0.7);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SEED_OIL = FLUIDS.register("seed_oil",
            () -> new BaseFlowingFluid.Source(seedOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SEED_OIL = FLUIDS.register("flowing_seed_oil",
            () -> new BaseFlowingFluid.Flowing(seedOilProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NAPHTHA = FLUIDS.register("naphtha",
            () -> new BaseFlowingFluid.Source(naphthaProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_NAPHTHA = FLUIDS.register("flowing_naphtha",
            () -> new BaseFlowingFluid.Flowing(naphthaProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIGHT_OIL = FLUIDS.register("light_oil",
            () -> new BaseFlowingFluid.Source(lightOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LIGHT_OIL = FLUIDS.register("flowing_light_oil",
            () -> new BaseFlowingFluid.Flowing(lightOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEAVY_OIL = FLUIDS.register("heavy_oil",
            () -> new BaseFlowingFluid.Source(heavyOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HEAVY_OIL = FLUIDS.register("flowing_heavy_oil",
            () -> new BaseFlowingFluid.Flowing(heavyOilProperties()));

    // The Chemical Reactor's acid: dense and thickish, and it burns (see ModBlocks.SULFURIC_ACID).
    public static final DeferredHolder<FluidType, FluidType> SULFURIC_ACID_TYPE = liquidType("sulfuric_acid", 1_830, 2_500, 0.02F, 0.7, 0.8);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFURIC_ACID = FLUIDS.register("sulfuric_acid",
            () -> new BaseFlowingFluid.Source(sulfuricAcidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SULFURIC_ACID = FLUIDS.register("flowing_sulfuric_acid",
            () -> new BaseFlowingFluid.Flowing(sulfuricAcidProperties()));

    // One slurry per leached metal (see OreSlurry): thick and slow, a little freer than Heavy Oil.
    public record Slurry(DeferredHolder<FluidType, FluidType> type, DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {}

    private static final Map<OreSlurry, Slurry> SLURRIES = registerSlurries();

    // Spent steam from a Steam Turbine Array's Exhaust ports, for a Condenser Array to turn back into water.
    // A gas, but not a steam grade: nothing burns or boils it, and turbines don't take it.
    public static final DeferredHolder<FluidType, FluidType> EXHAUST_STEAM_TYPE = gasType("exhaust_steam", 373);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> EXHAUST_STEAM = FLUIDS.register("exhaust_steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.EXHAUST_STEAM_TYPE, ModFluids.EXHAUST_STEAM, ModFluids.FLOWING_EXHAUST_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_EXHAUST_STEAM = FLUIDS.register("flowing_exhaust_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.EXHAUST_STEAM_TYPE, ModFluids.EXHAUST_STEAM, ModFluids.FLOWING_EXHAUST_STEAM)));

    // Hydrogen and Oxygen, from the Electrolyzer. Gases, but not steam grades: hydrogen burns in the Fuel Burner,
    // oxygen speeds up the Arcforge Furnace.
    public static final DeferredHolder<FluidType, FluidType> HYDROGEN_TYPE = gasType("hydrogen", 293);
    public static final DeferredHolder<FluidType, FluidType> OXYGEN_TYPE = gasType("oxygen", 293);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN = FLUIDS.register("hydrogen",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.HYDROGEN_TYPE, ModFluids.HYDROGEN, ModFluids.FLOWING_HYDROGEN)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HYDROGEN = FLUIDS.register("flowing_hydrogen",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.HYDROGEN_TYPE, ModFluids.HYDROGEN, ModFluids.FLOWING_HYDROGEN)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OXYGEN = FLUIDS.register("oxygen",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.OXYGEN_TYPE, ModFluids.OXYGEN, ModFluids.FLOWING_OXYGEN)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_OXYGEN = FLUIDS.register("flowing_oxygen",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.OXYGEN_TYPE, ModFluids.OXYGEN, ModFluids.FLOWING_OXYGEN)));

    // Carbon Dioxide, given off by the Fermenter. A gas with nothing to burn it; it goes out of Gas Output faces, or into
    // the air when it can't.
    public static final DeferredHolder<FluidType, FluidType> CARBON_DIOXIDE_TYPE = gasType("carbon_dioxide", 293);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CARBON_DIOXIDE = FLUIDS.register("carbon_dioxide",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.CARBON_DIOXIDE_TYPE, ModFluids.CARBON_DIOXIDE, ModFluids.FLOWING_CARBON_DIOXIDE)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CARBON_DIOXIDE = FLUIDS.register("flowing_carbon_dioxide",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.CARBON_DIOXIDE_TYPE, ModFluids.CARBON_DIOXIDE, ModFluids.FLOWING_CARBON_DIOXIDE)));

    // Farm chemistry. Nitrogen (the Air Separator's), Ammonia (the Haber Reactor's) and Biogas (the Biogas Digester's)
    // are gases; Biogas burns in the Fuel Burner and the Gas Turbine Array (the arcforge:burner_fuels data map).
    public static final DeferredHolder<FluidType, FluidType> NITROGEN_TYPE = gasType("nitrogen", 293);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NITROGEN = FLUIDS.register("nitrogen",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.NITROGEN_TYPE, ModFluids.NITROGEN, ModFluids.FLOWING_NITROGEN)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_NITROGEN = FLUIDS.register("flowing_nitrogen",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.NITROGEN_TYPE, ModFluids.NITROGEN, ModFluids.FLOWING_NITROGEN)));
    public static final DeferredHolder<FluidType, FluidType> AMMONIA_TYPE = gasType("ammonia", 293);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> AMMONIA = FLUIDS.register("ammonia",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.AMMONIA_TYPE, ModFluids.AMMONIA, ModFluids.FLOWING_AMMONIA)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_AMMONIA = FLUIDS.register("flowing_ammonia",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.AMMONIA_TYPE, ModFluids.AMMONIA, ModFluids.FLOWING_AMMONIA)));
    public static final DeferredHolder<FluidType, FluidType> BIOGAS_TYPE = gasType("biogas", 308);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BIOGAS = FLUIDS.register("biogas",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.BIOGAS_TYPE, ModFluids.BIOGAS, ModFluids.FLOWING_BIOGAS)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_BIOGAS = FLUIDS.register("flowing_biogas",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.BIOGAS_TYPE, ModFluids.BIOGAS, ModFluids.FLOWING_BIOGAS)));

    // The Chemical Reactor's farm liquids: Nutrient Solution (NPK Fertilizer in water, for hydroponics) runs like water;
    // Biodiesel (Seed Oil and Ethanol) is a Fuel Burner fuel, a little thinner than the oil it came from.
    public static final DeferredHolder<FluidType, FluidType> NUTRIENT_SOLUTION_TYPE = liquidType("nutrient_solution", 1_050, 1_000, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NUTRIENT_SOLUTION = FLUIDS.register("nutrient_solution",
            () -> new BaseFlowingFluid.Source(nutrientSolutionProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_NUTRIENT_SOLUTION = FLUIDS.register("flowing_nutrient_solution",
            () -> new BaseFlowingFluid.Flowing(nutrientSolutionProperties()));
    public static final DeferredHolder<FluidType, FluidType> BIODIESEL_TYPE = liquidType("biodiesel", 880, 2_000, 0.018F, 0.7, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BIODIESEL = FLUIDS.register("biodiesel",
            () -> new BaseFlowingFluid.Source(biodieselProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_BIODIESEL = FLUIDS.register("flowing_biodiesel",
            () -> new BaseFlowingFluid.Flowing(biodieselProperties()));

    // Salt and chlor-alkali chemistry. Brine (Salt in water, from the Chemical Reactor) runs like water; the Electrolyzer
    // splits it into Hydrogen, Chlorine (a gas) and Lye (Sodium Hydroxide solution, caustic). Hydrochloric Acid (Chlorine
    // and Hydrogen) leaches ores like Sulfuric Acid, and burns like it.
    // Seawater: what an Electric Pump draws from water in ocean and beach biomes. It runs like water; the Thermal
    // Evaporator Array boils it down into Brine.
    public static final DeferredHolder<FluidType, FluidType> SEAWATER_TYPE = liquidType("seawater", 1_025, 1_050, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SEAWATER = FLUIDS.register("seawater",
            () -> new BaseFlowingFluid.Source(seawaterProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SEAWATER = FLUIDS.register("flowing_seawater",
            () -> new BaseFlowingFluid.Flowing(seawaterProperties()));
    public static final DeferredHolder<FluidType, FluidType> BRINE_TYPE = liquidType("brine", 1_200, 1_100, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BRINE = FLUIDS.register("brine",
            () -> new BaseFlowingFluid.Source(brineProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_BRINE = FLUIDS.register("flowing_brine",
            () -> new BaseFlowingFluid.Flowing(brineProperties()));
    // Lithium Brine: lithium salts in water, from Spodumene Dust leached in Sulfuric Acid or left over when the Thermal
    // Evaporator Array boils Brine down to Salt. Runs like Brine; Lye turns it into Lithium Hydroxide.
    public static final DeferredHolder<FluidType, FluidType> LITHIUM_BRINE_TYPE = liquidType("lithium_brine", 1_250, 1_150, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LITHIUM_BRINE = FLUIDS.register("lithium_brine",
            () -> new BaseFlowingFluid.Source(lithiumBrineProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LITHIUM_BRINE = FLUIDS.register("flowing_lithium_brine",
            () -> new BaseFlowingFluid.Flowing(lithiumBrineProperties()));
    public static final DeferredHolder<FluidType, FluidType> LYE_TYPE = liquidType("lye", 1_300, 1_500, 0.02F, 0.75, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LYE = FLUIDS.register("lye",
            () -> new BaseFlowingFluid.Source(lyeProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LYE = FLUIDS.register("flowing_lye",
            () -> new BaseFlowingFluid.Flowing(lyeProperties()));
    public static final DeferredHolder<FluidType, FluidType> HYDROCHLORIC_ACID_TYPE = liquidType("hydrochloric_acid", 1_180, 1_200, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROCHLORIC_ACID = FLUIDS.register("hydrochloric_acid",
            () -> new BaseFlowingFluid.Source(hydrochloricAcidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HYDROCHLORIC_ACID = FLUIDS.register("flowing_hydrochloric_acid",
            () -> new BaseFlowingFluid.Flowing(hydrochloricAcidProperties()));
    public static final DeferredHolder<FluidType, FluidType> CHLORINE_TYPE = gasType("chlorine", 293);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CHLORINE = FLUIDS.register("chlorine",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.CHLORINE_TYPE, ModFluids.CHLORINE, ModFluids.FLOWING_CHLORINE)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CHLORINE = FLUIDS.register("flowing_chlorine",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.CHLORINE_TYPE, ModFluids.CHLORINE, ModFluids.FLOWING_CHLORINE)));

    // Rubber and bio-plastics. Latex: the milky sap of the Rubber Dandelion's roots (the Oil Press) and of jungle trees
    // (the Resin Tap), thick and slow; the Grain Dryer dries it into Raw Rubber. Ethylene: a gas, from Ethanol dehydrated
    // over Sulfuric Acid in the Chemical Reactor; it polymerises into Plastic Sheet, and with Chlorine into PVC Sheet.
    public static final DeferredHolder<FluidType, FluidType> LATEX_TYPE = liquidType("latex", 980, 4_000, 0.016F, 0.6, 0.7);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LATEX = FLUIDS.register("latex",
            () -> new BaseFlowingFluid.Source(latexProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LATEX = FLUIDS.register("flowing_latex",
            () -> new BaseFlowingFluid.Flowing(latexProperties()));
    public static final DeferredHolder<FluidType, FluidType> ETHYLENE_TYPE = gasType("ethylene", 293);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHYLENE = FLUIDS.register("ethylene",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.ETHYLENE_TYPE, ModFluids.ETHYLENE, ModFluids.FLOWING_ETHYLENE)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_ETHYLENE = FLUIDS.register("flowing_ethylene",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.ETHYLENE_TYPE, ModFluids.ETHYLENE, ModFluids.FLOWING_ETHYLENE)));

    // Liquid Experience: experience as a fluid, 20 mB per point (the c:experience convention, so other mods' liquid
    // experience works with it). Pale green and glowing like experience orbs; it runs like water. The XP Drain and the
    // Vacuum Collector make it, the XP Shower gives it back to players.
    public static final DeferredHolder<FluidType, FluidType> LIQUID_EXPERIENCE_TYPE = FLUID_TYPES.register("liquid_experience",
            () -> new LiquidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.arcforge.liquid_experience")
                    .density(800)
                    .viscosity(800)
                    .temperature(300)
                    .lightLevel(10)
                    .motionScale(0.007)
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .supportsBoating(false)
                    .fallDistanceModifier(0.0F)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.02F, 0.8, 0.8));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_EXPERIENCE = FLUIDS.register("liquid_experience",
            () -> new BaseFlowingFluid.Source(liquidExperienceProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LIQUID_EXPERIENCE = FLUIDS.register("flowing_liquid_experience",
            () -> new BaseFlowingFluid.Flowing(liquidExperienceProperties()));

    // Steam in three grades (see SteamGrade). Gases: lighter than air, with no world block and no bucket,
    // so they only exist in tanks, machines and Pressurized Conduits.
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = gasType("steam", 373);
    public static final DeferredHolder<FluidType, FluidType> HIGH_PRESSURE_STEAM_TYPE = gasType("high_pressure_steam", 773);
    public static final DeferredHolder<FluidType, FluidType> SUPERHEATED_STEAM_TYPE = gasType("superheated_steam", 1_173);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STEAM = FLUIDS.register("flowing_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HIGH_PRESSURE_STEAM = FLUIDS.register("high_pressure_steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.HIGH_PRESSURE_STEAM_TYPE, ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HIGH_PRESSURE_STEAM = FLUIDS.register("flowing_high_pressure_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.HIGH_PRESSURE_STEAM_TYPE, ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SUPERHEATED_STEAM = FLUIDS.register("superheated_steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.SUPERHEATED_STEAM_TYPE, ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SUPERHEATED_STEAM = FLUIDS.register("flowing_superheated_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.SUPERHEATED_STEAM_TYPE, ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM)));

    private ModFluids() {}

    public static Slurry slurry(OreSlurry slurry) {
        return SLURRIES.get(slurry);
    }

    private static Map<OreSlurry, Slurry> registerSlurries() {
        Map<OreSlurry, Slurry> slurries = new EnumMap<>(OreSlurry.class);
        for (OreSlurry slurry : OreSlurry.values()) {
            String name = slurry.fluidName();
            slurries.put(slurry, new Slurry(liquidType(name, 1_600, 4_000, 0.015F, 0.6, 0.7),
                    FLUIDS.register(name, () -> new BaseFlowingFluid.Source(slurryProperties(slurry))),
                    FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(slurryProperties(slurry)))));
        }
        return Collections.unmodifiableMap(slurries);
    }

    // Negative density marks a gas (see Gases); temperature in kelvin.
    private static DeferredHolder<FluidType, FluidType> gasType(String name, int kelvin) {
        return FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type.arcforge." + name)
                .density(-500)
                .viscosity(200)
                .temperature(kelvin)
                .canSwim(false)
                .canDrown(false)
                .canPushEntity(false)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false)));
    }

    // No block and no bucket: a gas can't be placed or carried.
    private static BaseFlowingFluid.Properties gasProperties(DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, ? extends Fluid> source, DeferredHolder<Fluid, ? extends Fluid> flowing) {
        return new BaseFlowingFluid.Properties(type, source, flowing);
    }

    // A liquid like creosote: swimmable, carried in buckets. speed and the drags set how it moves entities (see LiquidType).
    private static DeferredHolder<FluidType, FluidType> liquidType(String name, int density, int viscosity, float speed, double horizontalDrag, double verticalDrag) {
        return FLUID_TYPES.register(name, () -> new LiquidType(FluidType.Properties.create()
                .descriptionId("fluid_type.arcforge." + name)
                .density(density)
                .viscosity(viscosity)
                .temperature(300)
                .motionScale(0.007)
                .canSwim(true)
                .canDrown(true)
                .canPushEntity(true)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false)
                .fallDistanceModifier(0.0F)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), speed, horizontalDrag, verticalDrag));
    }

    // Thin: spreads 4 blocks, every 5 ticks, like water.
    private static BaseFlowingFluid.Properties naphthaProperties() {
        return new BaseFlowingFluid.Properties(NAPHTHA_TYPE, NAPHTHA, FLOWING_NAPHTHA)
                .bucket(ModItems.NAPHTHA_BUCKET)
                .block(ModBlocks.NAPHTHA)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    private static BaseFlowingFluid.Properties ethanolProperties() {
        return new BaseFlowingFluid.Properties(ETHANOL_TYPE, ETHANOL, FLOWING_ETHANOL)
                .bucket(ModItems.ETHANOL_BUCKET)
                .block(ModBlocks.ETHANOL)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // Slower than water: spreads 3 blocks, every 15 ticks, like Light Oil.
    private static BaseFlowingFluid.Properties seedOilProperties() {
        return new BaseFlowingFluid.Properties(SEED_OIL_TYPE, SEED_OIL, FLOWING_SEED_OIL)
                .bucket(ModItems.SEED_OIL_BUCKET)
                .block(ModBlocks.SEED_OIL)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(15)
                .explosionResistance(100.0F);
    }

    // Like water: spreads 4 blocks, every 5 ticks.
    private static BaseFlowingFluid.Properties nutrientSolutionProperties() {
        return new BaseFlowingFluid.Properties(NUTRIENT_SOLUTION_TYPE, NUTRIENT_SOLUTION, FLOWING_NUTRIENT_SOLUTION)
                .bucket(ModItems.NUTRIENT_SOLUTION_BUCKET)
                .block(ModBlocks.NUTRIENT_SOLUTION)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // Between water and Seed Oil: spreads 3 blocks, every 10 ticks.
    private static BaseFlowingFluid.Properties biodieselProperties() {
        return new BaseFlowingFluid.Properties(BIODIESEL_TYPE, BIODIESEL, FLOWING_BIODIESEL)
                .bucket(ModItems.BIODIESEL_BUCKET)
                .block(ModBlocks.BIODIESEL)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(10)
                .explosionResistance(100.0F);
    }

    private static BaseFlowingFluid.Properties lightOilProperties() {
        return new BaseFlowingFluid.Properties(LIGHT_OIL_TYPE, LIGHT_OIL, FLOWING_LIGHT_OIL)
                .bucket(ModItems.LIGHT_OIL_BUCKET)
                .block(ModBlocks.LIGHT_OIL)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(15)
                .explosionResistance(100.0F);
    }

    // Slow like lava.
    private static BaseFlowingFluid.Properties heavyOilProperties() {
        return new BaseFlowingFluid.Properties(HEAVY_OIL_TYPE, HEAVY_OIL, FLOWING_HEAVY_OIL)
                .bucket(ModItems.HEAVY_OIL_BUCKET)
                .block(ModBlocks.HEAVY_OIL)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

    // Like water: spreads 4 blocks, every 5 ticks.
    // Thick, like Seed Oil: spreads 3 blocks, every 15 ticks.
    private static BaseFlowingFluid.Properties latexProperties() {
        return new BaseFlowingFluid.Properties(LATEX_TYPE, LATEX, FLOWING_LATEX)
                .bucket(ModItems.LATEX_BUCKET)
                .block(ModBlocks.LATEX)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(15)
                .explosionResistance(100.0F);
    }

    private static BaseFlowingFluid.Properties seawaterProperties() {
        return new BaseFlowingFluid.Properties(SEAWATER_TYPE, SEAWATER, FLOWING_SEAWATER)
                .bucket(ModItems.SEAWATER_BUCKET)
                .block(ModBlocks.SEAWATER)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // Like water: spreads 4 blocks, every 5 ticks.
    private static BaseFlowingFluid.Properties liquidExperienceProperties() {
        return new BaseFlowingFluid.Properties(LIQUID_EXPERIENCE_TYPE, LIQUID_EXPERIENCE, FLOWING_LIQUID_EXPERIENCE)
                .bucket(ModItems.LIQUID_EXPERIENCE_BUCKET)
                .block(ModBlocks.LIQUID_EXPERIENCE)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // Like water: spreads 4 blocks, every 5 ticks.
    private static BaseFlowingFluid.Properties brineProperties() {
        return new BaseFlowingFluid.Properties(BRINE_TYPE, BRINE, FLOWING_BRINE)
                .bucket(ModItems.BRINE_BUCKET)
                .block(ModBlocks.BRINE)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // Like Brine.
    private static BaseFlowingFluid.Properties lithiumBrineProperties() {
        return new BaseFlowingFluid.Properties(LITHIUM_BRINE_TYPE, LITHIUM_BRINE, FLOWING_LITHIUM_BRINE)
                .bucket(ModItems.LITHIUM_BRINE_BUCKET)
                .block(ModBlocks.LITHIUM_BRINE)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    // A little thicker than water: spreads 3 blocks, every 10 ticks.
    private static BaseFlowingFluid.Properties lyeProperties() {
        return new BaseFlowingFluid.Properties(LYE_TYPE, LYE, FLOWING_LYE)
                .bucket(ModItems.LYE_BUCKET)
                .block(ModBlocks.LYE)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(10)
                .explosionResistance(100.0F);
    }

    // Spreads 3 blocks, every 10 ticks, like Sulfuric Acid.
    private static BaseFlowingFluid.Properties hydrochloricAcidProperties() {
        return new BaseFlowingFluid.Properties(HYDROCHLORIC_ACID_TYPE, HYDROCHLORIC_ACID, FLOWING_HYDROCHLORIC_ACID)
                .bucket(ModItems.HYDROCHLORIC_ACID_BUCKET)
                .block(ModBlocks.HYDROCHLORIC_ACID)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(10)
                .explosionResistance(100.0F);
    }

    // Spreads 3 blocks, every 10 ticks.
    private static BaseFlowingFluid.Properties sulfuricAcidProperties() {
        return new BaseFlowingFluid.Properties(SULFURIC_ACID_TYPE, SULFURIC_ACID, FLOWING_SULFURIC_ACID)
                .bucket(ModItems.SULFURIC_ACID_BUCKET)
                .block(ModBlocks.SULFURIC_ACID)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(10)
                .explosionResistance(100.0F);
    }

    // Spreads 2 blocks, every 20 ticks.
    private static BaseFlowingFluid.Properties slurryProperties(OreSlurry slurry) {
        Slurry fluids = slurry(slurry);
        return new BaseFlowingFluid.Properties(fluids.type(), fluids.source(), fluids.flowing())
                .bucket(ModItems.slurryBucket(slurry))
                .block(ModBlocks.slurryBlock(slurry))
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(20)
                .explosionResistance(100.0F);
    }

    // Slow like lava: spreads 2 blocks, every 30 ticks.
    private static BaseFlowingFluid.Properties creosoteProperties() {
        return new BaseFlowingFluid.Properties(CREOSOTE_TYPE, CREOSOTE, FLOWING_CREOSOTE)
                .bucket(ModItems.CREOSOTE_BUCKET)
                .block(ModBlocks.CREOSOTE)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

    // Entities in a modded fluid only move if its type moves them (NeoForge applies water or lava
    // movement to those fluids alone), so without this they freeze in place. speed is how hard they
    // swim, and the drags how much of their movement is kept each tick (water keeps 0.8): creosote is
    // thick, slower than water and a little freer than lava. Entities sink slowly, can swim up and
    // climb out at an edge.
    private static class LiquidType extends FluidType {
        private final float speed;
        private final double horizontalDrag;
        private final double verticalDrag;

        LiquidType(Properties properties, float speed, double horizontalDrag, double verticalDrag) {
            super(properties);
            this.speed = speed;
            this.horizontalDrag = horizontalDrag;
            this.verticalDrag = verticalDrag;
        }

        @Override
        public boolean move(LivingEntity entity, Vec3 input, double gravity) {
            boolean falling = entity.getDeltaMovement().y <= 0.0;
            double oldY = entity.getY();
            entity.moveRelative(speed, input);
            entity.move(MoverType.SELF, entity.getDeltaMovement());
            Vec3 movement = entity.getDeltaMovement().multiply(horizontalDrag, verticalDrag, horizontalDrag);
            entity.setDeltaMovement(entity.getFluidFallingAdjustedMovement(gravity, falling, movement));
            // Pushing against a ledge while in the fluid hops the entity out, as in water and lava.
            Vec3 after = entity.getDeltaMovement();
            if (entity.horizontalCollision && entity.isFree(after.x, after.y + 0.6 - entity.getY() + oldY, after.z)) {
                entity.setDeltaMovement(after.x, 0.3, after.z);
            }
            return true;
        }
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
