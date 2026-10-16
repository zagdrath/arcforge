/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.registry.ModDataComponents;

// What a Fuel Rod is (the arcforge:fuel_rod component): its fuel type, the enrichment it was made at (pcm, as UF6's
// assay; civil fuel only, so never above 20%), its burnup (0 fresh to 1 spent), the fissile plutonium and fission
// products it has built up, and, once it has been in a running core, the decay heat it came out with (HU/t) and when
// (game time), which fades as decay heat does (see decayHeat). A rod caught in a melted core is damaged: it can't be
// loaded again.
public record FuelRod(FuelType type, int enrichment, float burnup, float plutonium, float fissionProducts, float dischargeHeat, long discharged,
        boolean damaged) {
    // UOX now; MOX and thorium fuels come later.
    public enum FuelType implements StringRepresentable {
        UOX("uox");

        public static final Codec<FuelType> CODEC = StringRepresentable.fromEnum(FuelType::values);
        private final String name;

        FuelType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<FuelRod> CODEC = RecordCodecBuilder.create(i -> i.group(
            FuelType.CODEC.optionalFieldOf("type", FuelType.UOX).forGetter(FuelRod::type),
            Codec.intRange(1, Enrichment.MAX_PCM).fieldOf("enrichment").forGetter(FuelRod::enrichment),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("burnup", 0.0F).forGetter(FuelRod::burnup),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("plutonium", 0.0F).forGetter(FuelRod::plutonium),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("fission_products", 0.0F).forGetter(FuelRod::fissionProducts),
            Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("discharge_heat", 0.0F).forGetter(FuelRod::dischargeHeat),
            Codec.LONG.optionalFieldOf("discharged", 0L).forGetter(FuelRod::discharged),
            Codec.BOOL.optionalFieldOf("damaged", false).forGetter(FuelRod::damaged))
            .apply(i, FuelRod::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelRod> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> FuelType.values()[index], FuelType::ordinal), FuelRod::type,
            ByteBufCodecs.VAR_INT, FuelRod::enrichment,
            ByteBufCodecs.FLOAT, FuelRod::burnup,
            ByteBufCodecs.FLOAT, FuelRod::plutonium,
            ByteBufCodecs.FLOAT, FuelRod::fissionProducts,
            ByteBufCodecs.FLOAT, FuelRod::dischargeHeat,
            ByteBufCodecs.VAR_LONG, FuelRod::discharged,
            ByteBufCodecs.BOOL, FuelRod::damaged,
            FuelRod::new);

    // A fresh rod of this enrichment (pcm, clamped to the civil range).
    public static FuelRod fresh(int pcm) {
        return new FuelRod(FuelType.UOX, Math.max(1, Math.min(Enrichment.MAX_PCM, pcm)), 0.0F, 0.0F, 0.0F, 0.0F, 0L, false);
    }

    public static FuelRod of(ItemStack stack) {
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        return rod != null ? rod : fresh(Enrichment.NATURAL_PCM);
    }

    public boolean isSpent() {
        return burnup >= 1.0F;
    }

    public boolean isFresh() {
        return burnup <= 0.0F && dischargeHeat <= 0.0F;
    }

    public double enrichmentFraction() {
        return enrichment / 100_000.0;
    }

    // Into the reactor model, and back out of it (when the rod comes out, with its share of the decay heat).
    public ReactorSim.Rod toSim() {
        ReactorSim.Rod rod = new ReactorSim.Rod(enrichmentFraction());
        rod.burnup = burnup;
        rod.plutonium = plutonium;
        rod.fissionProducts = fissionProducts;
        return rod;
    }

    public static FuelRod fromSim(FuelRod was, ReactorSim.Rod rod, double decayHeat, long now, boolean damaged) {
        return new FuelRod(was.type(), was.enrichment(), (float) Math.min(1.0, rod.burnup), (float) Math.min(1.0, rod.plutonium),
                (float) Math.min(1.0, rod.fissionProducts), (float) Math.max(0.0, decayHeat), now, was.damaged() || damaged);
    }

    // The decay heat it gives off now (HU/t), falling as the core's does (see ReactorParams.decayTicks).
    public double decayHeat(long now) {
        if (dischargeHeat <= 0.0F) {
            return 0.0;
        }
        double t = Math.max(0L, now - discharged);
        double tau = ArcforgeConfig.REACTOR_SPENT_FUEL_COOLING.getAsDouble();
        return dischargeHeat * (0.6 * Math.exp(-t / tau) + 0.4 * Math.exp(-t / (tau * 10.0)));
    }

    // Whether it's still too hot to hold safely.
    public boolean isHot(long now) {
        return decayHeat(now) > ArcforgeConfig.REACTOR_HOT_ROD_HEAT.getAsDouble();
    }

    // Its dose rate (mSv/h at a block): fresh fuel a little by its enrichment; irradiated fuel by its fission products,
    // far more.
    public double doseRate() {
        double fresh = ArcforgeConfig.REACTOR_FRESH_ROD_DOSE.getAsDouble() * Enrichment.activityFactor(enrichment);
        double spent = ArcforgeConfig.REACTOR_SPENT_ROD_DOSE.getAsDouble() * Math.sqrt(Math.max(0.0, fissionProducts));
        return damaged ? spent * 2.0 + fresh : fresh + spent;
    }

    public String burnupText() {
        return String.format(Locale.ROOT, "%.1f%%", burnup * 100.0);
    }
}
