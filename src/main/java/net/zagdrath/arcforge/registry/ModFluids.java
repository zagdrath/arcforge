/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;

// Creosote: the Carbonizer's by-product. A slow, oily liquid; not flammable in the world (yet). The
// Distillation Array splits it into Naphtha (thin, and flammable in the world), Light Oil and Heavy Oil
// (thick, slow as lava). Their textures and fog colours are registered on the client in ArcforgeClient.
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Arcforge.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Arcforge.MODID);

    public static final DeferredHolder<FluidType, FluidType> CREOSOTE_TYPE = FLUID_TYPES.register("creosote", () -> new LiquidType(FluidType.Properties.create()
            .descriptionId("fluid_type.arcforge.creosote")
            .density(1_200)
            .viscosity(3_000)
            .temperature(300)
            .motionScale(0.007)
            .canSwim(true)
            .canDrown(true)
            .canPushEntity(true)
            .canExtinguish(false)
            .canConvertToSource(false)
            .supportsBoating(false)
            .fallDistanceModifier(0.0F)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.02F, 0.65, 0.8));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREOSOTE = FLUIDS.register("creosote",
            () -> new BaseFlowingFluid.Source(creosoteProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CREOSOTE = FLUIDS.register("flowing_creosote",
            () -> new BaseFlowingFluid.Flowing(creosoteProperties()));

    // The Distillation Array's fractions, lightest first. Naphtha runs like water; Heavy Oil creeps like lava.
    public static final DeferredHolder<FluidType, FluidType> NAPHTHA_TYPE = liquidType("naphtha", 700, 500, 0.02F, 0.8, 0.8);
    public static final DeferredHolder<FluidType, FluidType> LIGHT_OIL_TYPE = liquidType("light_oil", 850, 1_500, 0.02F, 0.7, 0.8);
    public static final DeferredHolder<FluidType, FluidType> HEAVY_OIL_TYPE = liquidType("heavy_oil", 1_050, 6_000, 0.015F, 0.5, 0.6);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NAPHTHA = FLUIDS.register("naphtha",
            () -> new BaseFlowingFluid.Source(naphthaProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_NAPHTHA = FLUIDS.register("flowing_naphtha",
            () -> new BaseFlowingFluid.Flowing(naphthaProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIGHT_OIL = FLUIDS.register("light_oil",
            () -> new BaseFlowingFluid.Source(lightOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LIGHT_OIL = FLUIDS.register("flowing_light_oil",
            () -> new BaseFlowingFluid.Flowing(lightOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEAVY_OIL = FLUIDS.register("heavy_oil",
            () -> new BaseFlowingFluid.Source(heavyOilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HEAVY_OIL = FLUIDS.register("flowing_heavy_oil",
            () -> new BaseFlowingFluid.Flowing(heavyOilProperties()));

    // Steam in three grades (see SteamGrade). Gases: lighter than air, with no world block and no bucket,
    // so they only exist in tanks, machines and Pressurized Conduits.
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = gasType("steam", 373);
    public static final DeferredHolder<FluidType, FluidType> HIGH_PRESSURE_STEAM_TYPE = gasType("high_pressure_steam", 773);
    public static final DeferredHolder<FluidType, FluidType> SUPERHEATED_STEAM_TYPE = gasType("superheated_steam", 1_173);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STEAM = FLUIDS.register("flowing_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HIGH_PRESSURE_STEAM = FLUIDS.register("high_pressure_steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.HIGH_PRESSURE_STEAM_TYPE, ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HIGH_PRESSURE_STEAM = FLUIDS.register("flowing_high_pressure_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.HIGH_PRESSURE_STEAM_TYPE, ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SUPERHEATED_STEAM = FLUIDS.register("superheated_steam",
            () -> new BaseFlowingFluid.Source(gasProperties(ModFluids.SUPERHEATED_STEAM_TYPE, ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SUPERHEATED_STEAM = FLUIDS.register("flowing_superheated_steam",
            () -> new BaseFlowingFluid.Flowing(gasProperties(ModFluids.SUPERHEATED_STEAM_TYPE, ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM)));

    private ModFluids() {}

    // Negative density marks a gas (see Gases); temperature in kelvin.
    private static DeferredHolder<FluidType, FluidType> gasType(String name, int kelvin) {
        return FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type.arcforge." + name)
                .density(-500)
                .viscosity(200)
                .temperature(kelvin)
                .canSwim(false)
                .canDrown(false)
                .canPushEntity(false)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false)));
    }

    // No block and no bucket: a gas can't be placed or carried.
    private static BaseFlowingFluid.Properties gasProperties(DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, ? extends Fluid> source, DeferredHolder<Fluid, ? extends Fluid> flowing) {
        return new BaseFlowingFluid.Properties(type, source, flowing);
    }

    // A liquid like creosote: swimmable, carried in buckets. speed and the drags set how it moves entities (see LiquidType).
    private static DeferredHolder<FluidType, FluidType> liquidType(String name, int density, int viscosity, float speed, double horizontalDrag, double verticalDrag) {
        return FLUID_TYPES.register(name, () -> new LiquidType(FluidType.Properties.create()
                .descriptionId("fluid_type.arcforge." + name)
                .density(density)
                .viscosity(viscosity)
                .temperature(300)
                .motionScale(0.007)
                .canSwim(true)
                .canDrown(true)
                .canPushEntity(true)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false)
                .fallDistanceModifier(0.0F)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), speed, horizontalDrag, verticalDrag));
    }

    // Thin: spreads 4 blocks, every 5 ticks, like water.
    private static BaseFlowingFluid.Properties naphthaProperties() {
        return new BaseFlowingFluid.Properties(NAPHTHA_TYPE, NAPHTHA, FLOWING_NAPHTHA)
                .bucket(ModItems.NAPHTHA_BUCKET)
                .block(ModBlocks.NAPHTHA)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    private static BaseFlowingFluid.Properties lightOilProperties() {
        return new BaseFlowingFluid.Properties(LIGHT_OIL_TYPE, LIGHT_OIL, FLOWING_LIGHT_OIL)
                .bucket(ModItems.LIGHT_OIL_BUCKET)
                .block(ModBlocks.LIGHT_OIL)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(1)
                .tickRate(15)
                .explosionResistance(100.0F);
    }

    // Slow like lava.
    private static BaseFlowingFluid.Properties heavyOilProperties() {
        return new BaseFlowingFluid.Properties(HEAVY_OIL_TYPE, HEAVY_OIL, FLOWING_HEAVY_OIL)
                .bucket(ModItems.HEAVY_OIL_BUCKET)
                .block(ModBlocks.HEAVY_OIL)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

    // Slow like lava: spreads 2 blocks, every 30 ticks.
    private static BaseFlowingFluid.Properties creosoteProperties() {
        return new BaseFlowingFluid.Properties(CREOSOTE_TYPE, CREOSOTE, FLOWING_CREOSOTE)
                .bucket(ModItems.CREOSOTE_BUCKET)
                .block(ModBlocks.CREOSOTE)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100.0F);
    }

    // Entities in a modded fluid only move if its type moves them (NeoForge applies water or lava
    // movement to those fluids alone), so without this they freeze in place. speed is how hard they
    // swim, and the drags how much of their movement is kept each tick (water keeps 0.8): creosote is
    // thick, slower than water and a little freer than lava. Entities sink slowly, can swim up and
    // climb out at an edge.
    private static class LiquidType extends FluidType {
        private final float speed;
        private final double horizontalDrag;
        private final double verticalDrag;

        LiquidType(Properties properties, float speed, double horizontalDrag, double verticalDrag) {
            super(properties);
            this.speed = speed;
            this.horizontalDrag = horizontalDrag;
            this.verticalDrag = verticalDrag;
        }

        @Override
        public boolean move(LivingEntity entity, Vec3 input, double gravity) {
            boolean falling = entity.getDeltaMovement().y <= 0.0;
            double oldY = entity.getY();
            entity.moveRelative(speed, input);
            entity.move(MoverType.SELF, entity.getDeltaMovement());
            Vec3 movement = entity.getDeltaMovement().multiply(horizontalDrag, verticalDrag, horizontalDrag);
            entity.setDeltaMovement(entity.getFluidFallingAdjustedMovement(gravity, falling, movement));
            // Pushing against a ledge while in the fluid hops the entity out, as in water and lava.
            Vec3 after = entity.getDeltaMovement();
            if (entity.horizontalCollision && entity.isFree(after.x, after.y + 0.6 - entity.getY() + oldY, after.z)) {
                entity.setDeltaMovement(after.x, 0.3, after.z);
            }
            return true;
        }
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
