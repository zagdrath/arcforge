/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.tag.ModItemTags;

// The Foundry Suit: fire-resistant armour of Rock Wool, Slag Wool and Steel. Each piece cuts fire and hot-block
// damage (#arcforge:foundry_resists) by fireReductionPerPiece; the full set makes the wearer immune to it and puts
// out fire, and shields them from lava for lavaShieldTicks (the shield refills lavaCooldownTicks after they leave
// the lava). The shield's state lives on the player (LAVA_SHIELD), synced to them for the HUD.
public final class FoundrySuit {
    public static final ArmorMaterial ARMOR = new ArmorMaterial(10,
            Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
            15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ModItemTags.REPAIRS_FOUNDRY_SUIT,
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "foundry")));

    public static final TagKey<DamageType> RESISTS = TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "foundry_resists"));

    // How much lava protection is left and how long since the wearer left the lava, in ticks.
    public record LavaShield(int remaining, int cooldown) {
        public static final Codec<LavaShield> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("remaining").forGetter(LavaShield::remaining),
                Codec.INT.fieldOf("cooldown").forGetter(LavaShield::cooldown))
                .apply(i, LavaShield::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, LavaShield> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LavaShield::remaining,
                ByteBufCodecs.VAR_INT, LavaShield::cooldown,
                LavaShield::new);

        public static LavaShield full() {
            return new LavaShield(ArcforgeConfig.FOUNDRY_LAVA_SHIELD_TICKS.getAsInt(), 0);
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arcforge.MODID);
    // Saved, not kept through death, and synced only to its own player.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LavaShield>> LAVA_SHIELD = ATTACHMENT_TYPES.register("foundry_lava",
            () -> AttachmentType.builder(LavaShield::full)
                    .serialize(LavaShield.CODEC.fieldOf("shield"))
                    .sync((holder, player) -> holder == player, LavaShield.STREAM_CODEC)
                    .build());

    // The server's exact count; the attachment (and so the client) only follows it every SYNC_STEP ticks.
    private static final Map<UUID, LavaShield> LIVE = new HashMap<>();
    private static final int SYNC_STEP = 20;

    private FoundrySuit() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static int pieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            count += entity.getItemBySlot(slot).is(ModItemTags.FOUNDRY_SUIT) ? 1 : 0;
        }
        return count;
    }

    public static boolean fullSet(LivingEntity entity) {
        return pieces(entity) == 4;
    }

    // The damage left after the suit: fire and hot blocks cut per piece (none with the full set), lava none while the
    // full set's shield lasts. Anything else is untouched.
    public static float adjust(LivingEntity entity, DamageSource source, float amount) {
        int pieces = pieces(entity);
        if (pieces == 0) {
            return amount;
        }
        if (source.is(RESISTS)) {
            return pieces == 4 ? 0.0F : amount * (float) Math.max(0.0, 1.0 - ArcforgeConfig.FOUNDRY_FIRE_REDUCTION.getAsDouble() * pieces);
        }
        if (source.is(DamageTypes.LAVA) && pieces == 4 && shield(entity).remaining() > 0) {
            return 0.0F;
        }
        return amount;
    }

    public static LavaShield shield(LivingEntity entity) {
        LavaShield live = LIVE.get(entity.getUUID());
        return live != null ? live : entity.getData(LAVA_SHIELD);
    }

    // One tick of the full suit: no burning, and the shield drains in lava and refills after the cooldown out of it.
    public static void tick(Player player, boolean inLava) {
        if (!fullSet(player)) {
            return;
        }
        player.setRemainingFireTicks(0);
        int max = ArcforgeConfig.FOUNDRY_LAVA_SHIELD_TICKS.getAsInt();
        LavaShield before = shield(player);
        LavaShield after;
        if (inLava) {
            after = new LavaShield(Math.max(0, before.remaining() - 1), 0);
        } else if (before.remaining() < max) {
            int cooldown = before.cooldown() + 1;
            after = cooldown >= ArcforgeConfig.FOUNDRY_LAVA_COOLDOWN_TICKS.getAsInt() ? new LavaShield(max, 0) : new LavaShield(before.remaining(), cooldown);
        } else {
            after = before;
        }
        LIVE.put(player.getUUID(), after);
        LavaShield synced = player.getData(LAVA_SHIELD);
        boolean crossedZero = (synced.remaining() > 0) != (after.remaining() > 0);
        boolean refilled = after.remaining() == max && synced.remaining() != max;
        if (crossedZero || refilled || Math.abs(synced.remaining() - after.remaining()) >= SYNC_STEP
                || (after.cooldown() > 0 && Math.abs(synced.cooldown() - after.cooldown()) >= SYNC_STEP) || (after.cooldown() == 0) != (synced.cooldown() == 0)) {
            player.setData(LAVA_SHIELD, after);
        }
    }

    public static void forget(Player player) {
        LavaShield live = LIVE.remove(player.getUUID());
        if (live != null) {
            player.setData(LAVA_SHIELD, live);
        }
    }
}
