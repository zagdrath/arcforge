/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModSounds;

// A machine's running loop: plays from the machine (an array's centre block) while it runs (its block is lit),
// fading in when it starts and out when it stops or the block goes.
public class MachineLoopSound extends AbstractTickableSoundInstance {
    // Each machine's loop and how loud it plays (the loops are mastered to the same peak, so the steady ones,
    // the hums and the presses' drone, are turned down).
    private record Loop(Supplier<SoundEvent> sound, float volume) {}

    private static final Map<Block, Loop> LOOPS = new HashMap<>();
    private static final Map<BlockPos, MachineLoopSound> PLAYING = new HashMap<>();
    // How much volume is gained or lost each tick fading in or out (a fade of about half a second).
    private static final float FADE = 0.1F;

    private final Level level;
    private final BlockPos pos;
    private final Block block;
    private final float fullVolume;
    private float fade;

    private MachineLoopSound(Level level, BlockPos pos, Block block, Loop loop) {
        super(loop.sound().get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos.immutable();
        this.block = block;
        this.fullVolume = loop.volume();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    private static void register() {
        if (!LOOPS.isEmpty()) {
            return;
        }
        LOOPS.put(ModBlocks.ARC_CRUSHER.get(), new Loop(ModSounds.ARC_CRUSHER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.ARC_CRUSHING_ARRAY_CASING.get(), new Loop(ModSounds.ARC_CRUSHING_ARRAY_RUN::get, 1.0F));
        LOOPS.put(ModBlocks.INDUCTION_FURNACE.get(), new Loop(ModSounds.INDUCTION_FURNACE_RUN::get, 0.55F));
        LOOPS.put(ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get(), new Loop(ModSounds.INDUCTION_FURNACE_ARRAY_RUN::get, 0.7F));
        LOOPS.put(ModBlocks.METAL_PRESS.get(), new Loop(ModSounds.METAL_PRESS_RUN::get, 0.75F));
        LOOPS.put(ModBlocks.METAL_PRESSING_ARRAY_CASING.get(), new Loop(ModSounds.METAL_PRESSING_ARRAY_RUN::get, 0.85F));
        LOOPS.put(ModBlocks.ARC_MELTER.get(), new Loop(ModSounds.ARC_MELTER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.CHEMICAL_REACTOR.get(), new Loop(ModSounds.CHEMICAL_REACTOR_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.ELECTROLYZER.get(), new Loop(ModSounds.ELECTROLYZER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.ASSEMBLER.get(), new Loop(ModSounds.ASSEMBLER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.BLOCK_BREAKER.get(), new Loop(ModSounds.BLOCK_BREAKER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.BLOCK_PLACER.get(), new Loop(ModSounds.BLOCK_PLACER_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.VACUUM_COLLECTOR.get(), new Loop(ModSounds.VACUUM_COLLECTOR_RUN::get, 0.8F));
        LOOPS.put(ModBlocks.ARC_QUARRY.get(), new Loop(ModSounds.ARC_QUARRY_RUN::get, 1.0F));
        LOOPS.put(ModBlocks.SUPERHEATER_ARRAY_CASING.get(), new Loop(ModSounds.SUPERHEATER_ARRAY_RUN::get, 1.0F));
        LOOPS.put(ModBlocks.CONDENSER_ARRAY_CASING.get(), new Loop(ModSounds.CONDENSER_ARRAY_RUN::get, 0.9F));
    }

    // Called every client tick by each machine that has a loop (see MachineSounds.clientHook).
    public static void keepPlaying(BlockEntity machine) {
        register();
        Level level = machine.getLevel();
        BlockState state = machine.getBlockState();
        Loop loop = LOOPS.get(state.getBlock());
        if (level == null || loop == null || !isRunning(state)) {
            return;
        }
        MachineLoopSound sound = PLAYING.get(machine.getBlockPos());
        if (sound != null && !sound.isStopped()) {
            return;
        }
        sound = new MachineLoopSound(level, machine.getBlockPos(), state.getBlock(), loop);
        PLAYING.put(sound.pos, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    // Lit, and for an array its centre block (the one that runs it).
    private static boolean isRunning(@Nullable BlockState state) {
        if (state == null || !state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) {
            return false;
        }
        return !state.hasProperty(CubeCasingBlock.PART) || state.getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.CENTER;
    }

    @Override
    public void tick() {
        BlockState state = level == Minecraft.getInstance().level ? level.getBlockState(pos) : null;
        boolean running = state != null && state.is(block) && isRunning(state);
        fade = Math.clamp(fade + (running ? FADE : -FADE), 0.0F, 1.0F);
        volume = fullVolume * fade;
        if (!running && fade <= 0.0F) {
            PLAYING.remove(pos, this);
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
