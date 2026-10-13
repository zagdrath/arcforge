/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataMaps;
import net.zagdrath.arcforge.registry.ModFluids;

// What a liquid fuel is worth in the Fuel Burner: the heat each mB gives, how fast it burns, and
// optionally how hot it burns (°C; the burner can't get hotter than that, and without it the burner's own
// maximum). Set per fluid in the arcforge:burner_fuels data map
// (data/<namespace>/data_maps/fluid/burner_fuels.json), so fuels can be added without code. Fluids not in
// the map aren't fuel. Gasoline, Diesel, Kerosene and Refinery Gas take their heat and burn temperature from the
// refineryFuels config instead of the map.
public record BurnerFuel(int huPerMb, float mbPerTick, Optional<Integer> burnTemperature, boolean gasTurbine) {
    public static final Codec<BurnerFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("hu_per_mb").forGetter(BurnerFuel::huPerMb),
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("mb_per_tick").forGetter(BurnerFuel::mbPerTick),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("burn_temperature").forGetter(BurnerFuel::burnTemperature),
            Codec.BOOL.optionalFieldOf("gas_turbine", true).forGetter(BurnerFuel::gasTurbine))
            .apply(i, BurnerFuel::new));

    // How hot a burner burning this gets, capped at the burner's own maximum.
    public int burnTemperature(int maxCelsius) {
        return Math.min(maxCelsius, burnTemperature.orElse(maxCelsius));
    }

    public static @Nullable BurnerFuel of(Fluid fluid) {
        BurnerFuel fuel = BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(ModDataMaps.BURNER_FUELS);
        return fuel != null ? refinery(fluid, fuel) : null;
    }

    // The refinery's fuels take their heat and burn temperature from the refineryFuels config, over the data map.
    private static BurnerFuel refinery(Fluid fluid, BurnerFuel fuel) {
        ModConfigSpec.IntValue heat, temperature;
        if (fluid == ModFluids.GASOLINE.get()) {
            heat = ArcforgeConfig.GASOLINE_HU_PER_MB;
            temperature = ArcforgeConfig.GASOLINE_BURN_TEMPERATURE;
        } else if (fluid == ModFluids.DIESEL.get()) {
            heat = ArcforgeConfig.DIESEL_HU_PER_MB;
            temperature = ArcforgeConfig.DIESEL_BURN_TEMPERATURE;
        } else if (fluid == ModFluids.KEROSENE.get()) {
            heat = ArcforgeConfig.KEROSENE_HU_PER_MB;
            temperature = ArcforgeConfig.KEROSENE_BURN_TEMPERATURE;
        } else if (fluid == ModFluids.REFINERY_GAS.get()) {
            heat = ArcforgeConfig.REFINERY_GAS_HU_PER_MB;
            temperature = ArcforgeConfig.REFINERY_GAS_BURN_TEMPERATURE;
        } else {
            return fuel;
        }
        return new BurnerFuel(heat.getAsInt(), fuel.mbPerTick(), Optional.of(temperature.getAsInt()), fuel.gasTurbine());
    }

    public static @Nullable BurnerFuel of(FluidResource resource) {
        return resource.isEmpty() ? null : of(resource.getFluid());
    }
}
