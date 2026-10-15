/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.registry.ModFluids;

// How the Cracking Unit cracks, chosen by the player in its GUI (saved with it, never worked out from its tanks): an
// arcforge:cracking recipe's "mode" says which it belongs to. Catalytic cracking wears the Cracking Catalyst and makes
// more Naphtha and Refinery Gas; hydrocracking takes Hydrogen instead and makes mostly Diesel and Kerosene. Both fill
// the same four product tanks in order: Naphtha, Diesel, then Refinery Gas or Kerosene, then Light Oil.
public enum CrackingMode implements StringRepresentable {
    CATALYTIC("catalytic", () -> List.of(ModFluids.NAPHTHA.get(), ModFluids.DIESEL.get(), ModFluids.REFINERY_GAS.get(), ModFluids.LIGHT_OIL.get())),
    HYDROCRACKING("hydrocracking", () -> List.of(ModFluids.NAPHTHA.get(), ModFluids.DIESEL.get(), ModFluids.KEROSENE.get(), ModFluids.LIGHT_OIL.get()));

    public static final Codec<CrackingMode> CODEC = StringRepresentable.fromEnum(CrackingMode::values);

    private final String name;
    private final Supplier<List<Fluid>> products;

    CrackingMode(String name, Supplier<List<Fluid>> products) {
        this.name = name;
        this.products = products;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    // What its product tanks show while empty, in order.
    public List<Fluid> products() {
        return products.get();
    }

    public Component getDescription() {
        return Component.translatable("cracking_mode.arcforge." + name);
    }

    public static CrackingMode byOrdinal(int ordinal) {
        CrackingMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : CATALYTIC;
    }

    public static CrackingMode byName(String name) {
        for (CrackingMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return CATALYTIC;
    }
}
