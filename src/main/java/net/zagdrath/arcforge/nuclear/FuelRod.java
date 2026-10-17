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

// What a Fuel Rod is (the arcforge:fuel_rod component): its fuel type, the enrichment it was made at (pcm of its
// fissile uranium, as UF6's assay; civil fuel only, so never above 20%), its burnup (0 fresh to 1 spent), the fissile
// plutonium (or, in thorium, U-233) and fission products it has built up, and, once it has been in a running core, the
// decay heat it came out with (HU/t) and when (game time), which fades as decay heat does (see decayHeat). A rod caught
// in a melted core, or whose cladding failed in a dry pool, is damaged: it can't be loaded again. A MOX rod also keeps
// the plutonium it was made with (startPlutonium).
public record FuelRod(FuelType type, int enrichment, float burnup, float plutonium, float fissionProducts, float dischargeHeat, long discharged,
        boolean damaged, float startPlutonium) {
    // Uranium oxide (U-235 in U-238); mixed oxide (plutonium in depleted uranium); a thorium blanket rod (fertile
    // thorium that breeds U-233); thorium-U-233 fuel (U-233 bred in a blanket, back in thorium).
    public enum FuelType implements StringRepresentable {
        UOX("uox"), MOX("mox"), THORIUM("thorium"), U233("u233");

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
            Codec.BOOL.optionalFieldOf("damaged", false).forGetter(FuelRod::damaged),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("start_plutonium", 0.0F).forGetter(FuelRod::startPlutonium))
            .apply(i, FuelRod::new));

    // (By the codec: nine fields is more than StreamCodec.composite takes.)
    public static final StreamCodec<RegistryFriendlyByteBuf, FuelRod> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // A fresh rod of this enrichment (pcm, clamped to the civil range).
    public static FuelRod fresh(int pcm) {
        return fresh(FuelType.UOX, pcm, 0.0F);
    }

    // A fresh rod of a type: its fissile uranium (pcm, clamped to the civil range) and, for MOX, its plutonium.
    public static FuelRod fresh(FuelType type, int pcm, float plutonium) {
        float pu = Math.max(0.0F, Math.min(0.2F, plutonium));
        return new FuelRod(type, Math.max(1, Math.min(Enrichment.MAX_PCM, pcm)), 0.0F, pu, 0.0F, 0.0F, 0L, false, pu);
    }

    public FuelRod damage() {
        return new FuelRod(type, enrichment, burnup, plutonium, fissionProducts, dischargeHeat, discharged, true, startPlutonium);
    }

    // Spent at this burnup (for showing what a rod comes out as).
    public FuelRod burnt(float to) {
        float fp = Math.min(1.0F, to);
        float pu = type == FuelType.THORIUM ? 0.02F * to : startPlutonium * (1.0F - 0.4F * to) + 0.004F * to;
        return new FuelRod(type, enrichment, to, pu, fp, 0.0F, 0L, damaged, startPlutonium);
    }

    // The fissile uranium left (pcm): what reprocessing recovers.
    public int remainingUranium() {
        double used = type == FuelType.THORIUM ? 0.0 : 0.45 * burnup;
        return (int) Math.round(enrichment * (1.0 - used));
    }

    public boolean isThoriumBlanket() {
        return type == FuelType.THORIUM;
    }

    // A stack's rod; one without the component is fresh of its item's type (natural uranium, or the type's usual
    // make-up: MOX at 7% plutonium in depleted uranium, a thorium blanket, 3% U-233 in thorium).
    public static FuelRod of(ItemStack stack) {
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        if (rod != null) {
            return rod;
        }
        FuelType type = stack.getItem() instanceof net.zagdrath.arcforge.item.nuclear.FuelRodItem item ? item.type() : FuelType.UOX;
        return switch (type) {
            case UOX -> fresh(Enrichment.NATURAL_PCM);
            case MOX -> fresh(FuelType.MOX, Enrichment.DEFAULT_TAILS_PCM, 0.07F);
            case THORIUM -> fresh(FuelType.THORIUM, 1, 0.0F);
            case U233 -> fresh(FuelType.U233, 3_000, 0.0F);
        };
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
        ReactorSim.Rod rod = new ReactorSim.Rod(type == FuelType.THORIUM ? 0.0 : enrichmentFraction());
        rod.burnup = burnup;
        rod.plutonium = plutonium;
        rod.plutonium0 = startPlutonium;
        rod.fissionProducts = fissionProducts;
        rod.thorium = type == FuelType.THORIUM || type == FuelType.U233;
        return rod;
    }

    public static FuelRod fromSim(FuelRod was, ReactorSim.Rod rod, double decayHeat, long now, boolean damaged) {
        return new FuelRod(was.type(), was.enrichment(), (float) Math.min(1.0, rod.burnup), (float) Math.min(1.0, rod.plutonium),
                (float) Math.min(1.0, rod.fissionProducts), (float) Math.max(0.0, decayHeat), now, was.damaged() || damaged, was.startPlutonium());
    }

    // A rod coming out of a dry pool (or anywhere else) with its decay heat kept going from now.
    public FuelRod withHeat(double heat, long now) {
        return new FuelRod(type, enrichment, burnup, plutonium, fissionProducts, (float) Math.max(0.0, heat), now, damaged, startPlutonium);
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
