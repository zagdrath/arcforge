/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.nuclear.NuclearEntityBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Spent Fuel Rack: a steel grid holding up to SLOTS fuel rods in a Spent Fuel Pool. Under water (waterlogged) the
// pool carries the rods' decay heat away, and with nuclear.spentFuel.poolDepth blocks of water over it the water
// shields them too (RadiationField traces through it like any block); hot rods glow Cherenkov blue (the renderer,
// full-bright, while their decay heat is over rackGlowHeat). Out of the water the rack and its rods heat up by their
// decay heat (rackHeatCapacity HU a °C, losing rackAirCooling to the air), and at the cladding's failure temperature
// (nuclear.reactor.claddingFailure) the rods fail like a core's cladding: damaged, letting their fission products out
// (into a sealed containment's air, else as fallout).
public class SpentFuelRackBlockEntity extends NuclearBlockEntity implements RadiationSource, NuclearEntityBlock.ServerTicking {
    public static final int SLOTS = 4;

    private final ItemStack[] rods = new ItemStack[SLOTS];
    private double temperature = 20.0;
    private boolean glowing;

    public SpentFuelRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SPENT_FUEL_RACK.get(), pos, state);
        java.util.Arrays.fill(rods, ItemStack.EMPTY);
    }

    public ItemStack rod(int slot) {
        return rods[slot];
    }

    public int count() {
        return nonEmpty(rods).size();
    }

    public double getTemperature() {
        return temperature;
    }

    public boolean isGlowing() {
        return glowing;
    }

    // Puts a rod in the first free place; returns whether it went.
    public boolean insert(ItemStack rod) {
        if (!FuelRodItem.isFuelRod(rod)) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (rods[i].isEmpty()) {
                rods[i] = rod.copyWithCount(1);
                sync();
                return true;
            }
        }
        return false;
    }

    // Takes the last rod out (empty if none).
    public ItemStack extract() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!rods[i].isEmpty()) {
                ItemStack out = rods[i];
                rods[i] = ItemStack.EMPTY;
                sync();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    // The rods' decay heat (HU/t) now.
    public double decayHeat(long now) {
        double heat = 0.0;
        for (ItemStack stack : rods) {
            if (!stack.isEmpty()) {
                heat += FuelRod.of(stack).decayHeat(now);
            }
        }
        return heat;
    }

    // How many blocks of water stand over it, counting its own (up to the depth it needs and one more).
    public static int waterDepth(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED)) {
            return 0;
        }
        int depth = 0;
        int need = ArcforgeConfig.POOL_DEPTH.getAsInt();
        for (int up = 1; up <= need + 1; up++) {
            if (!level.getFluidState(pos.above(up)).is(Fluids.WATER)) {
                break;
            }
            depth++;
        }
        return depth;
    }

    public boolean isUnderwater() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        double heat = decayHeat(now);
        boolean wet = isUnderwater();
        if (wet) {
            // The pool takes the heat: the rack stays a little over the water's temperature.
            temperature = 20.0 + Math.min(70.0, heat * 0.5);
        } else {
            double loss = ArcforgeConfig.RACK_AIR_COOLING.getAsDouble() * (temperature - 20.0);
            temperature += (heat - loss) * 20.0 / ArcforgeConfig.RACK_HEAT_CAPACITY.getAsDouble();
            if (temperature > ArcforgeConfig.REACTOR_CLAD_FAILURE.getAsDouble()) {
                fail(level, pos);
            }
        }
        boolean glow = wet && heat > ArcforgeConfig.RACK_GLOW_HEAT.getAsDouble();
        if (glow != glowing) {
            glowing = glow;
            sync();
        } else {
            setChanged();
        }
        if (wet && count() > 0 && waterDepth(level, pos, state) >= ArcforgeConfig.POOL_DEPTH.getAsInt() && heat > 0.0) {
            ArcforgeAdvancements.produced(this, rods[0], null, "spent_fuel_pool");
        }
    }

    // The rods' cladding fails in the hot air: damaged, their fission products let out.
    private void fail(ServerLevel level, BlockPos pos) {
        double released = 0.0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = rods[i];
            if (stack.isEmpty()) {
                continue;
            }
            FuelRod rod = FuelRod.of(stack);
            if (!rod.damaged()) {
                stack.set(ModDataComponents.FUEL_ROD.get(), rod.damage());
                released += ArcforgeConfig.FALLOUT_PER_CHANNEL.getAsDouble() * 0.25 * (0.2 + rod.fissionProducts());
            }
        }
        if (released > 0.0) {
            Fallout.release(level, Vec3.atCenterOf(pos), released);
            sync();
        }
    }

    @Override
    public double radiationRate() {
        double rate = 0.0;
        for (ItemStack stack : rods) {
            rate += RadiationField.rate(stack);
        }
        return rate;
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
    }

    public java.util.List<ItemStack> contents() {
        return nonEmpty(rods);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        readSlots(in, "rods", rods);
        temperature = in.getDoubleOr("temperature", 20.0);
        glowing = in.getBooleanOr("glowing", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        storeSlots(out, "rods", rods);
        out.putDouble("temperature", temperature);
        out.putBoolean("glowing", glowing);
    }
}
