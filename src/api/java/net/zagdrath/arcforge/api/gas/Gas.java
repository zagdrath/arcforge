/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * One gas: the identity of a gas resource, with the properties consumers need to show and sort it.
 *
 * <p>Arcforge stores gases as fluids: a gas is a source {@link Fluid} that {@link GasRegistry#isGas(Fluid)} accepts.
 * A {@code Gas} wraps that fluid, so two instances for the same gas are {@linkplain #equals(Object) equal} and can
 * key a map. Save a gas with {@link #CODEC} or by its {@link #id()}, and send it to a client with
 * {@link #STREAM_CODEC}.
 *
 * <p>Get one from {@link GasRegistry#get(Identifier)}, {@link GasRegistry#of(Fluid)} or
 * {@link GasRegistry#all()}, or from a {@link GasHandler}'s tanks. Amounts of gas are in millibuckets (mB), like
 * fluids. The properties work on both the server and the client once a world is loaded (they read the fluid
 * registry, the {@code #arcforge:gases} tag and the {@link GasRegistry#PROPERTIES} data map, which is synced).
 *
 * @param fluid the gas's source fluid; a flowing form is replaced by its source
 */
public record Gas(Fluid fluid) {
    /**
     * The colour {@link #color()} gives a gas with no {@link GasRegistry#PROPERTIES} entry: opaque white,
     * {@code 0xFFFFFFFF}.
     */
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    /** Saves a gas as its fluid id, e.g. {@code "arcforge:hydrogen"}. Decoding fails for an id that isn't a gas. */
    public static final Codec<Gas> CODEC = BuiltInRegistries.FLUID.byNameCodec().comapFlatMap(
            fluid -> GasRegistry.of(fluid).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> BuiltInRegistries.FLUID.getKey(fluid) + " is not a gas")),
            Gas::fluid);

    /** Sends a gas over the network as its fluid's registry id. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Gas> STREAM_CODEC =
            ByteBufCodecs.registry(Registries.FLUID).map(Gas::new, Gas::fluid);

    /**
     * Wraps a gas fluid.
     *
     * @param fluid the gas's source or flowing fluid
     * @throws IllegalArgumentException if the fluid is not a gas
     */
    public Gas {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid instanceof FlowingFluid flowing) {
            fluid = flowing.getSource();
        }
        if (!GasRegistry.isGas(fluid)) {
            throw new IllegalArgumentException(BuiltInRegistries.FLUID.getKey(fluid) + " is not a gas");
        }
    }

    /**
     * Returns the gas's id, which is its fluid's registry id.
     *
     * @return the id, e.g. {@code arcforge:hydrogen}
     */
    public Identifier id() {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    /**
     * Returns the gas's name, as Arcforge's GUIs show it.
     *
     * @return the translatable display name, e.g. "Hydrogen"
     */
    public Component displayName() {
        return fluid.getFluidType().getDescription();
    }

    /**
     * Returns the colour Arcforge draws the gas in, for gauges, tank bars and icons.
     *
     * @return the colour as {@code 0xAARRGGBB}, from {@link GasRegistry#PROPERTIES}, or {@link #DEFAULT_COLOR}
     *         if the gas has no entry there
     */
    public int color() {
        GasProperties properties = BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(GasRegistry.PROPERTIES);
        return properties != null ? properties.color() : DEFAULT_COLOR;
    }

    /**
     * Returns the gas's temperature, from its fluid type.
     *
     * @return the temperature in kelvin, e.g. 293 for Hydrogen and 1,173 for Superheated Steam
     */
    public int temperature() {
        return fluid.getFluidType().getTemperature();
    }

    /**
     * Returns the steam grade this gas is, if it is one of Arcforge's three steams. Arcforge has no pressure value of
     * its own; the steam grades stand for it, the hotter grades being at higher pressure.
     *
     * @return the grade, or empty for any other gas (including Exhaust Steam)
     */
    public Optional<SteamGrade> steamGrade() {
        return SteamGrade.of(id());
    }

    /**
     * Returns this gas as a NeoForge fluid resource, for use with NeoForge's fluid capabilities.
     *
     * @return the fluid resource
     */
    public FluidResource toResource() {
        return FluidResource.of(fluid);
    }

    /**
     * Returns an amount of this gas as a fluid stack.
     *
     * @param amount the amount in mB
     * @return a new fluid stack
     */
    public FluidStack toStack(int amount) {
        return new FluidStack(fluid, amount);
    }
}
