/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.HazmatSuit;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Radioactive dust on people and their things, picked up where there's fallout on the ground or radioactive air in a
// containment (nuclear.safety.contaminationPickup of it each second), as a dose rate at a block (mSv/h, like a carried
// item's; see RadiationField.carried):
//  - On a person (SKIN, saved on them): it fades with fallout's short half-life. Each piece of a Hazmat Suit takes its
//    share instead (a full suit keeps nine tenths of it off the skin), and so does what's held in the main hand.
//  - On an item (ModDataComponents.CONTAMINATED, per item): it stays until washed off under a Decontamination Shower,
//    or the item is compacted into a Low-Level Waste Drum. A contaminated item doesn't stack with a clean one.
// Dying washes it all off the person (not their things).
public final class Contamination {
    public record Skin(float level, long time) {
        public static final Skin NONE = new Skin(0.0F, 0L);
        public static final Codec<Skin> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("level", 0.0F).forGetter(Skin::level),
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(Skin::time))
                .apply(i, Skin::new));

        public double at(long now) {
            if (level <= 0.0F || now <= time) {
                return level;
            }
            double days = (now - time) / 24_000.0;
            return level * Math.pow(0.5, days / ArcforgeConfig.FALLOUT_SHORT_HALF_LIFE.getAsDouble());
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arcforge.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Skin>> SKIN = ATTACHMENT_TYPES.register("contamination",
            () -> AttachmentType.builder(() -> Skin.NONE)
                    .serialize(Skin.CODEC.fieldOf("contamination"), skin -> skin.level() > 0.0F)
                    .build());

    // Below this it counts as clean (and is cleared).
    public static final double CLEAN = 1.0E-3;
    private static final EquipmentSlot[] ARMOUR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private Contamination() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static double skin(Player player) {
        return player.getData(SKIN).at(player.level().getGameTime());
    }

    public static void setSkin(Player player, double level) {
        player.setData(SKIN, level < CLEAN ? Skin.NONE : new Skin((float) level, player.level().getGameTime()));
    }

    // An item's contamination, per item (0 when clean).
    public static double of(ItemStack stack) {
        Float level = stack.get(ModDataComponents.CONTAMINATED.get());
        return level != null ? level : 0.0;
    }

    public static void set(ItemStack stack, double level) {
        if (level < CLEAN) {
            stack.remove(ModDataComponents.CONTAMINATED.get());
        } else {
            stack.set(ModDataComponents.CONTAMINATED.get(), (float) level);
        }
    }

    public static boolean isContaminated(ItemStack stack) {
        return stack.has(ModDataComponents.CONTAMINATED.get());
    }

    // What a player picks up in a check (seconds long): from the ground's fallout where they stand and from a
    // containment's air round them. A suit takes it on its pieces; the main hand's item takes a share.
    public static void tick(ServerLevel level, Player player, double seconds) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        Vec3 at = player.position();
        double source = 0.0;
        if (player.onGround() || player.isInWater()) {
            source += Fallout.ambient(level, at);
        }
        source += Containments.airDose(level, player.blockPosition()) * 0.5;
        if (source <= 0.0) {
            return;
        }
        double picked = source * ArcforgeConfig.CONTAMINATION_PICKUP.getAsDouble() * seconds;
        int pieces = HazmatSuit.pieces(player);
        double onSuit = picked * 0.9 * pieces / 4.0;
        if (pieces > 0) {
            for (EquipmentSlot slot : ARMOUR) {
                ItemStack piece = player.getItemBySlot(slot);
                if (!piece.isEmpty()) {
                    set(piece, of(piece) + onSuit / pieces);
                }
            }
        }
        double rest = picked - onSuit;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && held.getCount() == 1) {
            set(held, of(held) + rest * 0.25);
            rest *= 0.75;
        }
        setSkin(player, skin(player) + rest);
    }

    // Washing: the skin, the armour and every item carried lose share of what's on them. Returns how much came off.
    public static double wash(Player player, double share) {
        double removed = skin(player) * share;
        setSkin(player, skin(player) - removed);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            double on = of(stack);
            if (on > 0.0) {
                removed += on * share * stack.getCount();
                set(stack, on * (1.0 - share));
            }
        }
        return removed;
    }
}
