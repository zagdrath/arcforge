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

// Creosote: the Carbonizer's by-product. A slow, oily liquid; not flammable in the world (yet).
// Its textures and fog colour are registered on the client in ArcforgeClient.
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Arcforge.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Arcforge.MODID);

    public static final DeferredHolder<FluidType, FluidType> CREOSOTE_TYPE = FLUID_TYPES.register("creosote", () -> new CreosoteType(FluidType.Properties.create()
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
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREOSOTE = FLUIDS.register("creosote",
            () -> new BaseFlowingFluid.Source(creosoteProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CREOSOTE = FLUIDS.register("flowing_creosote",
            () -> new BaseFlowingFluid.Flowing(creosoteProperties()));

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
    // movement to those fluids alone), so without this they freeze in place. Creosote is thick: slower
    // than water, a little freer than lava. Entities sink slowly, can swim up and climb out at an edge.
    private static class CreosoteType extends FluidType {
        private static final float SPEED = 0.02F;
        private static final double HORIZONTAL_DRAG = 0.65;
        private static final double VERTICAL_DRAG = 0.8;

        CreosoteType(Properties properties) {
            super(properties);
        }

        @Override
        public boolean move(LivingEntity entity, Vec3 input, double gravity) {
            boolean falling = entity.getDeltaMovement().y <= 0.0;
            double oldY = entity.getY();
            entity.moveRelative(SPEED, input);
            entity.move(MoverType.SELF, entity.getDeltaMovement());
            Vec3 movement = entity.getDeltaMovement().multiply(HORIZONTAL_DRAG, VERTICAL_DRAG, HORIZONTAL_DRAG);
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
