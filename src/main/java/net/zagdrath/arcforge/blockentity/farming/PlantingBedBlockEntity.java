/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A Planting Bed of the Greenhouse Array: one soil and one seed, put in by hand, and the plant's growth. The greenhouse's
// controller grows it (GreenhouseBlockEntity); on its own a bed does nothing. What grows is ClochePlants' answer for the
// seed in the soil (an arcforge:cloche recipe that isn't hydroponic-only, or a vanilla-style crop in a farmland soil).
// Clients get the soil, seed and growth (on every 1/16 of growth, and when they change) to draw the plant on the bed.
public class PlantingBedBlockEntity extends BlockEntity {
    private static final int SYNC_STEPS = 16;

    private ItemStack soil = ItemStack.EMPTY;
    private ItemStack seed = ItemStack.EMPTY;
    // Growth so far, in ticks at speed 1, towards the plant's time.
    private double progress;
    private int total;
    private @Nullable Identifier plantKey;
    // Whether this harvest's water is paid, and its bonus from nutrients and Carbon Dioxide.
    private boolean cycling;
    private float cycleBonus = 1.0F;
    // Waiting, grown, for room in the greenhouse's output slots.
    private boolean waiting;

    private ClochePlants.@Nullable Plant plant;
    private @Nullable Item plantSeed;
    private @Nullable Item plantSoil;
    private long plantAge;
    private int syncedStep = -1;

    public PlantingBedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLANTING_BED.get(), pos, state);
    }

    // --- Contents ---

    public ItemStack getSoil() {
        return soil;
    }

    public ItemStack getSeed() {
        return seed;
    }

    public void setSoil(ItemStack stack) {
        soil = stack.copyWithCount(stack.isEmpty() ? 0 : 1);
        changed();
    }

    public void setSeed(ItemStack stack) {
        seed = stack.copyWithCount(stack.isEmpty() ? 0 : 1);
        changed();
    }

    private void changed() {
        restart(null);
        setChanged();
        sync();
    }

    // What grows here, or null: looked up again when the seed or soil changes, and every second (for /reload).
    public ClochePlants.@Nullable Plant plant() {
        Item seedItem = seed.isEmpty() ? null : seed.getItem();
        Item soilItem = soil.isEmpty() ? null : soil.getItem();
        long now = level != null ? level.getGameTime() : 0;
        if (seedItem != plantSeed || soilItem != plantSoil || now - plantAge >= 20) {
            plantSeed = seedItem;
            plantSoil = soilItem;
            plantAge = now;
            plant = seed.isEmpty() || soil.isEmpty() ? null : ClochePlants.find(level, seed, soil, false);
        }
        return plant;
    }

    // How fast crops grow in this soil (Loam: 1.25).
    public double soilGrowth() {
        ClocheSoil data = ClocheSoil.of(soil);
        return data != null ? data.growth() : 1.0;
    }

    // --- Growth, driven by the greenhouse ---

    // Starts over (a new plant, or none): progress is lost, but water already paid carries over.
    public void restart(@Nullable Identifier key) {
        if (progress != 0 || total != 0 || waiting || !java.util.Objects.equals(plantKey, key)) {
            setChanged();
        }
        progress = 0;
        total = 0;
        waiting = false;
        plantKey = key;
    }

    public @Nullable Identifier getPlantKey() {
        return plantKey;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public boolean isCycling() {
        return cycling;
    }

    public void startCycle(float bonus) {
        cycling = true;
        cycleBonus = bonus;
        setChanged();
    }

    public float getCycleBonus() {
        return cycleBonus;
    }

    // Grows by rate (capped at the plant's time); true once grown.
    public boolean grow(double rate) {
        progress = Math.min(total, progress + rate);
        setChanged();
        return progress >= total;
    }

    public boolean isGrown() {
        return total > 0 && progress >= total;
    }

    // Harvested: the seed grows again, and the next harvest pays its water again.
    public void harvested() {
        progress = 0;
        cycling = false;
        cycleBonus = 1.0F;
        waiting = false;
        setChanged();
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public boolean isWaiting() {
        return waiting;
    }

    // How grown the plant is, 0..1 (clients see it in 1/16 steps).
    public float growth() {
        return total <= 0 ? 0.0F : (float) Math.min(1.0, progress / total);
    }

    // Tells clients when the drawn stage changes.
    public void syncGrowth() {
        int step = (int) (growth() * SYNC_STEPS);
        if (step != syncedStep) {
            sync();
        }
    }

    private void sync() {
        syncedStep = (int) (growth() * SYNC_STEPS);
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // The soil and seed drop however the bed is removed.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropItemStack(level, pos.getX(), pos.getY() + 0.5, pos.getZ(), seed);
            Containers.dropItemStack(level, pos.getX(), pos.getY() + 0.5, pos.getZ(), soil);
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        soil = input.read("soil", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        seed = input.read("seed", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        progress = input.getDoubleOr("progress", 0.0);
        total = input.getIntOr("total", 0);
        plantKey = input.getString("plant").map(Identifier::tryParse).orElse(null);
        cycling = input.getBooleanOr("cycling", false);
        cycleBonus = input.getFloatOr("cycle_bonus", 1.0F);
        waiting = input.getBooleanOr("waiting", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("soil", ItemStack.OPTIONAL_CODEC, soil);
        output.store("seed", ItemStack.OPTIONAL_CODEC, seed);
        output.putDouble("progress", progress);
        output.putInt("total", total);
        if (plantKey != null) {
            output.putString("plant", plantKey.toString());
        }
        output.putBoolean("cycling", cycling);
        output.putFloat("cycle_bonus", cycleBonus);
        output.putBoolean("waiting", waiting);
    }
}
