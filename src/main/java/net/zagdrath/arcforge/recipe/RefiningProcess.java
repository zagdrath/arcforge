/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModRecipes;

// The refinery's downstream processes (see RefiningRecipe and RefiningCore) and what each machine is built with:
// whether it draws heat, FE, wears a catalyst or makes an item, and the products it's laid out for (its tanks show
// these while empty). The Cracking Unit is a multiblock; the other three are single blocks.
public enum RefiningProcess implements StringRepresentable {
    // Heavy Oil + heat + a catalyst -> Naphtha, Diesel and Refinery Gas.
    CRACKING("cracking", true, false, true, false, MachineStatus.CRACKING,
            () -> List.of(ModFluids.NAPHTHA.get(), ModFluids.DIESEL.get(), ModFluids.REFINERY_GAS.get()), ArcforgeConfig.CRACKING_TIME::getAsInt),
    // Naphtha + heat -> Gasoline + Hydrogen.
    REFORMING("reforming", true, false, false, false, MachineStatus.REFORMING,
            () -> List.of(ModFluids.GASOLINE.get(), ModFluids.HYDROGEN.get()), ArcforgeConfig.REFORMER_TIME::getAsInt),
    // Bitumen + heat -> Petroleum Coke + Refinery Gas.
    COKING("coking", true, false, false, true, MachineStatus.COKING,
            () -> List.of(ModFluids.REFINERY_GAS.get()), ArcforgeConfig.COKER_TIME::getAsInt),
    // Heavy Oil + FE -> Lubricant.
    BLENDING("lubricant_blending", false, true, false, false, MachineStatus.BLENDING,
            () -> List.of(ModFluids.LUBRICANT.get()), ArcforgeConfig.BLENDER_TIME::getAsInt);

    private final String name;
    private final boolean heated, powered, catalysed, makesItem;
    private final MachineStatus running;
    private final Supplier<List<Fluid>> products;
    private final IntSupplier defaultTime;

    RefiningProcess(String name, boolean heated, boolean powered, boolean catalysed, boolean makesItem, MachineStatus running,
            Supplier<List<Fluid>> products, IntSupplier defaultTime) {
        this.name = name;
        this.heated = heated;
        this.powered = powered;
        this.catalysed = catalysed;
        this.makesItem = makesItem;
        this.running = running;
        this.products = products;
        this.defaultTime = defaultTime;
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
        };
    }

    public RecipeSerializer<?> serializer() {
        return switch (this) {
            case CRACKING -> ModRecipes.CRACKING_SERIALIZER.get();
            case REFORMING -> ModRecipes.REFORMING_SERIALIZER.get();
            case COKING -> ModRecipes.COKING_SERIALIZER.get();
            case BLENDING -> ModRecipes.LUBRICANT_BLENDING_SERIALIZER.get();
        };
    }
}
