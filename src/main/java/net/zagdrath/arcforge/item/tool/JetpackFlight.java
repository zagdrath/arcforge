/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.network.JetpackStatePayload;

// Jetpack flight, run each tick for every player wearing one (see JetpackEvents). The server is in charge: it
// burns the fuel, clears fall distance and tells nearby clients how hard each jetpack fires (for the sound and
// exhaust). The local client runs the same motion from its own keys so flying doesn't rubber-band.
// Normal: jump thrusts (up to the tier's climb speed) and steers with the movement keys.
// Hover: while airborne it holds altitude; jump climbs, sneak sinks; it burns hoverFuelMultiplier times as much.
public final class JetpackFlight {
    public record Input(boolean jump, boolean sneak) {
        public static final Input NONE = new Input(false, false);
    }

    // The server's view of each player: their keys, the output last sent, and the one-time messages shown.
    private static final class State {
        Input input = Input.NONE;
        float sentOutput;
        boolean emptyShown;
        boolean disabledShown;
    }

    private static final double GRAVITY = 0.08;
    private static final double SINK_SPEED = -0.3;
    // How far away others see and hear a jetpack fire.
    private static final double EFFECT_RANGE = 64.0;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private JetpackFlight() {}

    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static void setInput(Player player, boolean jump, boolean sneak) {
        state(player).input = new Input(jump, sneak);
    }

    public static void forget(Player player) {
        STATES.remove(player.getUUID());
    }

    public static ItemStack worn(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof JetpackItem ? chest : ItemStack.EMPTY;
    }

    // One server tick: fly on the player's last sent keys and tell trackers when the output changes.
    public static void serverTick(ServerPlayer player) {
        State state = state(player);
        ItemStack stack = worn(player);
        if (stack.isEmpty()) {
            state.disabledShown = false;
        } else if (!ArcforgeConfig.ENABLE_JETPACKS.getAsBoolean() && !state.disabledShown && state.input.jump()) {
            state.disabledShown = true;
            player.sendOverlayMessage(Component.translatable("message.arcforge.jetpack.disabled"));
        }
        float output = tick(player, state.input.jump(), state.input.sneak(), true);
        JetpackFuel fuel = stack.isEmpty() ? null : fuelOf(stack);
        if (Math.abs(output - state.sentOutput) > 0.05F || (output == 0.0F) != (state.sentOutput == 0.0F)) {
            state.sentOutput = output;
            byte exhaust = output <= 0.0F || fuel == null ? 0 : (byte) (fuel.exhaust() == JetpackFuel.Exhaust.FLAME ? 2 : 1);
            broadcast(player, new JetpackStatePayload(player.getId(), output, exhaust));
        }
    }

    // To the wearer and players near enough to see them, if their client knows the payload (a vanilla client,
    // or a test's mock player, doesn't).
    private static void broadcast(ServerPlayer wearer, JetpackStatePayload payload) {
        for (ServerPlayer player : wearer.level().players()) {
            if (player.distanceToSqr(wearer) <= EFFECT_RANGE * EFFECT_RANGE && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static JetpackFuel fuelOf(ItemStack stack) {
        FluidStack fluid = JetpackItem.fluid(stack);
        return fluid.isEmpty() ? null : JetpackFuel.of(fluid.getFluid());
    }

    // Moves the player for one tick and returns the output (0 to 1). `server` burns fuel and clears the flight
    // kick; the client only predicts the motion (and steers, from its movement keys).
    public static float tick(Player player, boolean jump, boolean sneak, boolean server) {
        ItemStack stack = worn(player);
        if (stack.isEmpty() || !(stack.getItem() instanceof JetpackItem jetpack) || !ArcforgeConfig.ENABLE_JETPACKS.getAsBoolean()
                || player.isSpectator() || player.getAbilities().flying) {
            return 0.0F;
        }
        JetpackMode mode = JetpackItem.mode(stack);
        JetpackFuel fuel = fuelOf(stack);
        if (mode == JetpackMode.OFF || fuel == null) {
            return 0.0F;
        }
        boolean hover = mode == JetpackMode.HOVER;
        boolean firing = hover ? !player.onGround() || jump : jump;
        if (!firing) {
            return 0.0F;
        }
        int burn = hover ? Mth.ceil(fuel.mbPerTick() * ArcforgeConfig.JETPACK_HOVER_FUEL_MULTIPLIER.getAsDouble()) : fuel.mbPerTick();
        FluidStack held = JetpackItem.fluid(stack);
        if (held.getAmount() < burn) {
            if (server) {
                State state = state(player);
                if (!state.emptyShown) {
                    state.emptyShown = true;
                    player.sendOverlayMessage(Component.translatable("message.arcforge.jetpack.empty"));
                }
            }
            return 0.0F;
        }

        Vec3 motion = player.getDeltaMovement();
        double vy = motion.y;
        if (!hover || jump) {
            vy = Math.min(vy + fuel.thrust(), jetpack.maxRise());
        } else if (sneak) {
            vy = Math.max(vy - GRAVITY, SINK_SPEED);
        } else {
            vy = 0.0;
        }
        double vx = motion.x;
        double vz = motion.z;
        if (!server) {
            // Steer from the movement keys, rotated to where the player faces (as vanilla's input vector is).
            double strafe = player.xxa;
            double forward = player.zza;
            double length = Math.sqrt(strafe * strafe + forward * forward);
            if (length > 1.0E-4) {
                strafe /= Math.max(1.0, length);
                forward /= Math.max(1.0, length);
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                double sin = Mth.sin(yaw);
                double cos = Mth.cos(yaw);
                vx += (strafe * cos - forward * sin) * jetpack.airSpeed();
                vz += (forward * cos + strafe * sin) * jetpack.airSpeed();
            }
        }
        player.setDeltaMovement(vx, vy, vz);
        player.resetFallDistance();
        if (server) {
            JetpackItem.setFluid(stack, held.copyWithAmount(held.getAmount() - burn));
            state(player).emptyShown = false;
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.resetFlyingTicks();
            }
        }
        return fuel.output();
    }
}
