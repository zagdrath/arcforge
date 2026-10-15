/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModRecipes;

// The refinery's downstream processes (see RefiningRecipe and RefiningCore) and what each machine is built with:
// whether it draws heat, FE, wears a catalyst or makes an item, the second fluid it takes (and through which faces),
// and the products it's laid out for (its tanks show these while empty). The Cracking Unit and the Steam Cracker are
// multiblocks; the others are single blocks.
public enum RefiningProcess implements StringRepresentable {
    // Heavy Oil + heat, in the mode the player picks (CrackingMode): over a catalyst -> Naphtha, Diesel, Refinery Gas and
    // Light Oil; or with Hydrogen (its second feed) -> mostly Diesel and Kerosene. Its tanks are laid out for catalytic
    // cracking; the third holds Kerosene while it hydrocracks.
    CRACKING("cracking", true, false, true, false, MachineStatus.CRACKING, CrackingMode.CATALYTIC::products, ArcforgeConfig.CRACKING_TIME::getAsInt,
            SideMode.HYDROGEN),
    // Naphtha + heat -> Gasoline + Hydrogen.
    REFORMING("reforming", true, false, false, false, MachineStatus.REFORMING,
            () -> List.of(ModFluids.GASOLINE.get(), ModFluids.HYDROGEN.get()), ArcforgeConfig.REFORMER_TIME::getAsInt),
    // Bitumen + heat -> Petroleum Coke, Refinery Gas and a little Heavy Oil.
    COKING("coking", true, false, false, true, MachineStatus.COKING,
            () -> List.of(ModFluids.REFINERY_GAS.get(), ModFluids.HEAVY_OIL.get()), ArcforgeConfig.COKER_TIME::getAsInt),
    // Heavy Oil + FE -> Lubricant.
    BLENDING("lubricant_blending", false, true, false, false, MachineStatus.BLENDING,
            () -> List.of(ModFluids.LUBRICANT.get()), ArcforgeConfig.BLENDER_TIME::getAsInt),
    // Sour Kerosene or Sour Diesel + Hydrogen + heat + FE -> Kerosene or Diesel and Hydrogen Sulfide.
    HYDROTREATING("hydrotreating", true, true, false, false, MachineStatus.HYDROTREATING,
            () -> List.of(ModFluids.DIESEL.get(), ModFluids.HYDROGEN_SULFIDE.get()), ArcforgeConfig.HYDROTREATER_TIME::getAsInt, SideMode.HYDROGEN),
    // Hydrogen Sulfide + Oxygen + heat -> Sulfur and Steam (the Claus process).
    SULFUR_RECOVERY("sulfur_recovery", true, false, false, true, MachineStatus.RECOVERING_SULFUR,
            () -> List.of(ModFluids.STEAM.get()), ArcforgeConfig.SULFUR_RECOVERY_TIME::getAsInt, SideMode.OXYGEN),
    // Natural Gas + Steam + heat -> Hydrogen and Carbon Dioxide.
    STEAM_REFORMING("steam_reforming", true, false, false, false, MachineStatus.STEAM_REFORMING,
            () -> List.of(ModFluids.HYDROGEN.get(), ModFluids.CARBON_DIOXIDE.get()), ArcforgeConfig.SMR_TIME::getAsInt, SideMode.STEAM),
    // Naphtha, Refinery Gas or Natural Gas + Steam + heat -> Ethylene, Propylene, a little Hydrogen and a trickle of
    // Heavy Oil (Natural Gas: less Ethylene, more Hydrogen, hardly any Propylene).
    STEAM_CRACKING("steam_cracking", true, false, false, false, MachineStatus.STEAM_CRACKING,
            () -> List.of(ModFluids.ETHYLENE.get(), ModFluids.PROPYLENE.get(), ModFluids.HYDROGEN.get(), ModFluids.HEAVY_OIL.get()),
            ArcforgeConfig.STEAM_CRACKER_TIME::getAsInt, SideMode.STEAM);

    private final String name;
    private final boolean heated, powered, catalysed, makesItem;
    private final MachineStatus running;
    private final Supplier<List<Fluid>> products;
    private final IntSupplier defaultTime;
    private final @Nullable SideMode secondFeed;

    RefiningProcess(String name, boolean heated, boolean powered, boolean catalysed, boolean makesItem, MachineStatus running,
            Supplier<List<Fluid>> products, IntSupplier defaultTime) {
        this(name, heated, powered, catalysed, makesItem, running, products, defaultTime, null);
    }

    RefiningProcess(String name, boolean heated, boolean powered, boolean catalysed, boolean makesItem, MachineStatus running,
            Supplier<List<Fluid>> products, IntSupplier defaultTime, @Nullable SideMode secondFeed) {
        this.name = name;
        this.heated = heated;
        this.powered = powered;
        this.catalysed = catalysed;
        this.makesItem = makesItem;
        this.running = running;
        this.products = products;
        this.defaultTime = defaultTime;
        this.secondFeed = secondFeed;
    }

    // The face mode its second feed comes in through (Hydrogen, Oxygen or Steam), or null when it takes only one fluid.
    public @Nullable SideMode secondFeed() {
        return secondFeed;
    }

    // What it shows while its second feed runs short.
    public MachineStatus missingSecond() {
        return secondFeed == SideMode.HYDROGEN ? MachineStatus.NO_HYDROGEN : secondFeed == SideMode.OXYGEN ? MachineStatus.NO_OXYGEN
                : secondFeed == SideMode.STEAM ? MachineStatus.NO_STEAM : MachineStatus.MISSING_FLUID;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    // Whether its machine has a heat buffer and Heat faces.
    public boolean heated() {
        return heated;
    }

    // Whether its machine has an FE buffer and Energy faces.
    public boolean powered() {
        return powered;
    }

    // Whether its machine has a catalyst slot.
    public boolean catalysed() {
        return catalysed;
    }

    // Whether its machine has an output slot for an item product.
    public boolean makesItem() {
        return makesItem;
    }

    public MachineStatus runningStatus() {
        return running;
    }

    // The product tanks it's laid out for, in order.
    public List<Fluid> products() {
        return products.get();
    }

    public int tanks() {
        return products.get().size();
    }

    public int defaultTime() {
        return defaultTime.getAsInt();
    }

    public RecipeType<RefiningRecipe> type() {
        return switch (this) {
            case CRACKING -> ModRecipes.CRACKING.get();
            case REFORMING -> ModRecipes.REFORMING.get();
            case COKING -> ModRecipes.COKING.get();
            case BLENDING -> ModRecipes.LUBRICANT_BLENDING.get();
            case HYDROTREATING -> ModRecipes.HYDROTREATING.get();
            case SULFUR_RECOVERY -> ModRecipes.SULFUR_RECOVERY.get();
            case STEAM_REFORMING -> ModRecipes.STEAM_REFORMING.get();
            case STEAM_CRACKING -> ModRecipes.STEAM_CRACKING.get();
        };
    }

    public RecipeSerializer<?> serializer() {
        return switch (this) {
            case CRACKING -> ModRecipes.CRACKING_SERIALIZER.get();
            case REFORMING -> ModRecipes.REFORMING_SERIALIZER.get();
            case COKING -> ModRecipes.COKING_SERIALIZER.get();
            case BLENDING -> ModRecipes.LUBRICANT_BLENDING_SERIALIZER.get();
            case HYDROTREATING -> ModRecipes.HYDROTREATING_SERIALIZER.get();
            case SULFUR_RECOVERY -> ModRecipes.SULFUR_RECOVERY_SERIALIZER.get();
            case STEAM_REFORMING -> ModRecipes.STEAM_REFORMING_SERIALIZER.get();
            case STEAM_CRACKING -> ModRecipes.STEAM_CRACKING_SERIALIZER.get();
        };
    }
}
