/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDamageTypes;
import net.zagdrath.arcforge.registry.ModItems;

// A living entity's radiation exposure (EXPOSURE, saved on it, not kept through death, synced to its own player for the
// Geiger Counter's clicks): the dose rate it last took (mSv/h, see RadiationField) and the dose it has built up (mSv).
//  - Every radiation.doseCheckInterval ticks the dose grows by the rate for that time (an hour is radiation.ticksPerHour
//    ticks, an in-game hour by default) and falls by radiation.recoveryPerHour. Creative and spectator players take none.
//  - From nauseaDose (1,000 mSv) Nausea, from weaknessDose (2,000) Weakness and Hunger, from witherDose (4,000) Wither,
//    and from lethalDose (8,000) radiation sickness every 2 seconds (lethalDamage, through armour) until it falls or they
//    die. Dying resets it. radiation.playerEffects turns these off (the dose still shows); mobs only take a dose with
//    radiation.affectMobs.
//  - A player carrying a Dosimeter is warned each time their dose crosses a threshold on the way up.
public final class RadiationDose {
    public record Exposure(double dose, float rate) {
        public static final Exposure NONE = new Exposure(0.0, 0.0F);
        public static final Codec<Exposure> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("dose", 0.0).forGetter(Exposure::dose),
                Codec.FLOAT.optionalFieldOf("rate", 0.0F).forGetter(Exposure::rate))
                .apply(i, Exposure::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Exposure> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Exposure::dose,
                ByteBufCodecs.FLOAT, Exposure::rate,
                Exposure::new);
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arcforge.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Exposure>> EXPOSURE = ATTACHMENT_TYPES.register("radiation",
            () -> AttachmentType.builder(() -> Exposure.NONE)
                    .serialize(Exposure.CODEC.fieldOf("exposure"))
                    .sync((holder, player) -> holder == player, Exposure.STREAM_CODEC)
                    .build());

    // The thresholds, lowest first: 1 Nausea, 2 Weakness and Hunger, 3 Wither, 4 lethal.
    public static final int LEVELS = 4;
    private static final int EFFECT_TICKS = 100;
    private static final int LETHAL_INTERVAL = 40;
    // How much the synced rate or dose must move before the client is told again.
    // (a twentieth of the rate, or a thousandth of a mSv/h near nothing; a mSv of dose).
    private static final double SYNC_RATE_STEP = 0.05, SYNC_DOSE_STEP = 1.0;

    // The highest threshold each player has been warned about (cleared as the dose falls back).
    private static final Map<UUID, Integer> WARNED = new HashMap<>();

    private RadiationDose() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    // A player's exact exposure lives here between syncs (the attachment, and so the client, only follows it when it
    // moves enough); a mob's goes straight to its attachment.
    private static final Map<UUID, Exposure> LIVE = new HashMap<>();

    public static Exposure exposure(LivingEntity entity) {
        Exposure live = entity instanceof Player ? LIVE.get(entity.getUUID()) : null;
        return live != null ? live : entity.getData(EXPOSURE);
    }

    // Which threshold a dose has reached (0: none).
    public static int level(double dose) {
        if (dose >= ArcforgeConfig.RADIATION_LETHAL_DOSE.getAsDouble()) {
            return 4;
        }
        if (dose >= ArcforgeConfig.RADIATION_WITHER_DOSE.getAsDouble()) {
            return 3;
        }
        if (dose >= ArcforgeConfig.RADIATION_WEAKNESS_DOSE.getAsDouble()) {
            return 2;
        }
        return dose >= ArcforgeConfig.RADIATION_NAUSEA_DOSE.getAsDouble() ? 1 : 0;
    }

    // One check, every radiation.doseCheckInterval ticks (see RadiationEvents).
    public static void tick(ServerLevel level, LivingEntity entity, int interval) {
        Exposure before = exposure(entity);
        boolean immune = entity instanceof Player player && (player.isCreative() || player.isSpectator());
        double rate = RadiationField.doseRate(level, entity);
        double hours = interval / (double) ArcforgeConfig.RADIATION_TICKS_PER_HOUR.getAsInt();
        double dose = before.dose();
        if (ArcforgeConfig.RADIATION_ENABLED.getAsBoolean() && !immune) {
            dose += rate * hours;
        }
        dose = Math.max(0.0, dose - ArcforgeConfig.RADIATION_RECOVERY.getAsDouble() * hours);
        Exposure after = new Exposure(dose, (float) rate);
        if (entity instanceof Player) {
            LIVE.put(entity.getUUID(), after);
            Exposure synced = entity.getData(EXPOSURE);
            double rateStep = Math.max(SYNC_RATE_STEP * synced.rate(), 0.001);
            if (Math.abs(after.rate() - synced.rate()) >= rateStep || (after.rate() > 0.0F) != (synced.rate() > 0.0F)
                    || Math.abs(after.dose() - synced.dose()) >= SYNC_DOSE_STEP || (after.dose() == 0.0) != (synced.dose() == 0.0)) {
                entity.setData(EXPOSURE, after);
            }
        } else {
            entity.setData(EXPOSURE, after);
        }
        if (entity instanceof ServerPlayer player) {
            warn(player, dose);
        }
        if (ArcforgeConfig.RADIATION_PLAYER_EFFECTS.getAsBoolean() || !(entity instanceof Player)) {
            effects(level, entity, dose);
        }
    }

    // The sickness for the dose reached (renewed while it lasts).
    private static void effects(ServerLevel level, LivingEntity entity, double dose) {
        int reached = level(dose);
        if (reached >= 1) {
            entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, EFFECT_TICKS, 0, true, false, true));
        }
        if (reached >= 2) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 0, true, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_TICKS, 0, true, false, true));
        }
        if (reached >= 3) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_TICKS, 0, true, false, true));
        }
        if (reached >= 4 && entity.tickCount % LETHAL_INTERVAL < ArcforgeConfig.RADIATION_CHECK_INTERVAL.getAsInt()) {
            entity.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.RADIATION), (float) ArcforgeConfig.RADIATION_LETHAL_DAMAGE.getAsDouble());
        }
    }

    // A Dosimeter anywhere on the player warns them as their dose crosses each threshold going up.
    private static void warn(ServerPlayer player, double dose) {
        int reached = level(dose);
        int warned = WARNED.getOrDefault(player.getUUID(), 0);
        if (reached < warned) {
            WARNED.put(player.getUUID(), reached);
        } else if (reached > warned) {
            WARNED.put(player.getUUID(), reached);
            if (player.getInventory().contains(stack -> stack.is(ModItems.DOSIMETER.get()))) {
                player.sendSystemMessage(Component.translatable("message.arcforge.dosimeter.warning." + reached,
                        Math.round(dose)).withStyle(reached >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD));
                player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
            }
        }
    }

    // A player leaving: their exact exposure is written to the attachment (saved with them).
    public static void forget(Player player) {
        WARNED.remove(player.getUUID());
        Exposure live = LIVE.remove(player.getUUID());
        if (live != null) {
            player.setData(EXPOSURE, live);
        }
    }

    // Dying resets the dose (the attachment isn't copied to the new player; this clears the old warnings).
    public static void reset(Player player) {
        WARNED.remove(player.getUUID());
        LIVE.remove(player.getUUID());
        player.setData(EXPOSURE, Exposure.NONE);
    }
}
