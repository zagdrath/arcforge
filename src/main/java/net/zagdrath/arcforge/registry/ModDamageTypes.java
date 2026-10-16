/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.Arcforge;

// Arcforge's damage types. Each is data (data/arcforge/damage_type/<name>.json), with its death messages in the lang file.
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> SULFURIC_ACID = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "sulfuric_acid"));
    public static final ResourceKey<DamageType> HYDROFLUORIC_ACID = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "hydrofluoric_acid"));
    // Radiation sickness (see RadiationDose): goes through armour.
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "radiation"));
    // A discharged Fuel Rod still hot with decay heat, carried (see FuelRodItem).
    public static final ResourceKey<DamageType> HOT_FUEL = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "hot_fuel"));
    public static final ResourceKey<DamageType> HYDROCHLORIC_ACID = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "hydrochloric_acid"));
    public static final ResourceKey<DamageType> LYE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "lye"));

    private ModDamageTypes() {}

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type));
    }
}
