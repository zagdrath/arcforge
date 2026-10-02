/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.experience;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModFluids;

// Experience as a fluid: 20 mB of Liquid Experience per point, the rate every mod using #c:experience agrees on, so
// any fluid in that tag counts. Players gain and lose it a level at a time.
public final class LiquidExperience {
    public static final int MB_PER_POINT = 20;
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "experience"));

    private LiquidExperience() {}

    public static FluidResource resource() {
        return FluidResource.of(ModFluids.LIQUID_EXPERIENCE.get());
    }

    public static boolean is(FluidResource resource) {
        return !resource.isEmpty() && (resource.getFluid() == ModFluids.LIQUID_EXPERIENCE.get() || resource.getFluid().defaultFluidState().is(TAG));
    }

    // Points from the start of a level to the next (vanilla's curve).
    public static int pointsForLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    // Points the player has into their current level.
    public static int pointsIntoLevel(Player player) {
        return Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    // Points that take the player down one level: what they have into the current one, or, at its start, the whole
    // level below. 0 with no experience.
    public static int pointsToDrop(Player player) {
        int into = pointsIntoLevel(player);
        if (into > 0) {
            return into;
        }
        return player.experienceLevel > 0 ? pointsForLevel(player.experienceLevel - 1) : 0;
    }

    // Points that take the player up to the next level.
    public static int pointsToRise(Player player) {
        return Math.max(1, player.getXpNeededForNextLevel() - pointsIntoLevel(player));
    }

    // Every point an orb carries: its value times how many orbs merged into it. The count isn't public, so it's read from
    // the orb's saved data ("Count", 1 if missing).
    public static int points(ServerLevel level, ExperienceOrb orb) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        orb.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        return orb.getValue() * Math.max(1, tag.getIntOr("Count", 1));
    }
}
