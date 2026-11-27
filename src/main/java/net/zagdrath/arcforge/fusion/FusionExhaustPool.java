/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.arcforge.Arcforge;

// What Fusion Exhaust is made of. Every mB of exhaust is the same fluid wherever it is (so tanks and pipes never have to
// keep mixtures apart); its make-up is kept here, for the whole server: how much deuterium, tritium, helium-3 and helium
// ash all the exhaust the Cryopumps have taken carries, less what Tritium Plants have separated. A Tritium Plant splits
// each mB it takes by the shares now. Exhaust lost some other way (vented, burnt in a fire) leaves the shares as they
// were, which is what an even mixture would do.
public final class FusionExhaustPool extends SavedData {
    private record Stored(double deuterium, double tritium, double helium3, double helium) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("deuterium", 0.0).forGetter(Stored::deuterium),
                Codec.DOUBLE.optionalFieldOf("tritium", 0.0).forGetter(Stored::tritium),
                Codec.DOUBLE.optionalFieldOf("helium_3", 0.0).forGetter(Stored::helium3),
                Codec.DOUBLE.optionalFieldOf("helium", 0.0).forGetter(Stored::helium))
                .apply(i, Stored::new));
    }

    private static final Codec<FusionExhaustPool> CODEC = Stored.CODEC.xmap(FusionExhaustPool::new,
            pool -> new Stored(pool.deuterium, pool.tritium, pool.helium3, pool.helium));
    public static final SavedDataType<FusionExhaustPool> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "fusion_exhaust"), FusionExhaustPool::new, CODEC);

    // When nothing has been pumped yet: an even deuterium-tritium mix.
    public static final Shares DEFAULT = new Shares(0.5, 0.5, 0.0, 0.0);

    public record Shares(double deuterium, double tritium, double helium3, double helium) {}

    private double deuterium, tritium, helium3, helium;

    public FusionExhaustPool() {
        this(new Stored(0.0, 0.0, 0.0, 0.0));
    }

    private FusionExhaustPool(Stored stored) {
        this.deuterium = stored.deuterium();
        this.tritium = stored.tritium();
        this.helium3 = stored.helium3();
        this.helium = stored.helium();
    }

    public static FusionExhaustPool get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // Exhaust put into the world, by what's in it (mB).
    public void add(double d, double t, double he3, double he4) {
        deuterium += Math.max(0.0, d);
        tritium += Math.max(0.0, t);
        helium3 += Math.max(0.0, he3);
        helium += Math.max(0.0, he4);
        setDirty();
    }

    public Shares shares() {
        double total = deuterium + tritium + helium3 + helium;
        if (total <= 1.0E-9) {
            return DEFAULT;
        }
        return new Shares(deuterium / total, tritium / total, helium3 / total, helium / total);
    }

    // amount mB of exhaust taken out to be separated: what it held, by the shares now.
    public Shares take(double amount) {
        Shares shares = shares();
        deuterium = Math.max(0.0, deuterium - amount * shares.deuterium());
        tritium = Math.max(0.0, tritium - amount * shares.tritium());
        helium3 = Math.max(0.0, helium3 - amount * shares.helium3());
        helium = Math.max(0.0, helium - amount * shares.helium());
        setDirty();
        return shares;
    }

    // The pool's tritium decays to helium-3 like any other (see Tritium).
    public void decay(double fraction) {
        double decayed = tritium * fraction;
        tritium -= decayed;
        helium3 += decayed;
        setDirty();
    }
}
