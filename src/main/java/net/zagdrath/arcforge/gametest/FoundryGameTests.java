/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.FoundrySuit;
import net.zagdrath.arcforge.registry.ModItems;

// The Foundry Suit. Mock players don't move or swim on the server, so lava time is driven through FoundrySuit.tick.
public final class FoundryGameTests {
    private FoundryGameTests() {}

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    @SuppressWarnings("removal")
    private static ServerPlayer wearing(GameTestHelper helper, int pieces) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack[] suit = { new ItemStack(ModItems.FOUNDRY_HELMET.get()), new ItemStack(ModItems.FOUNDRY_CHESTPLATE.get()),
                new ItemStack(ModItems.FOUNDRY_LEGGINGS.get()), new ItemStack(ModItems.FOUNDRY_BOOTS.get()) };
        for (int i = 0; i < pieces; i++) {
            player.setItemSlot(SLOTS[i], suit[i]);
        }
        return player;
    }

    // 4 fire damage: 3 through one piece, 2 through two, none through the full set (which puts the fire out too);
    // the full set is safe on magma.
    static void damageReduction(GameTestHelper helper) {
        var sources = helper.getLevel().damageSources();
        float[] expected = { 3.0F, 2.0F };
        for (int pieces = 1; pieces <= 2; pieces++) {
            float taken = FoundrySuit.adjust(wearing(helper, pieces), sources.inFire(), 4.0F);
            helper.assertTrue(Math.abs(taken - expected[pieces - 1]) < 1.0E-4, pieces + " pieces take " + taken + " of 4 fire damage");
        }
        ServerPlayer full = wearing(helper, 4);
        helper.assertTrue(FoundrySuit.adjust(full, sources.inFire(), 4.0F) == 0.0F, "The full suit took fire damage");
        helper.assertTrue(FoundrySuit.adjust(full, sources.hotFloor(), 4.0F) == 0.0F, "The full suit took magma damage");
        full.setRemainingFireTicks(100);
        float health = full.getHealth();
        full.hurtServer(helper.getLevel(), sources.onFire(), 4.0F);
        helper.assertTrue(full.getHealth() == health, "Burning hurt the full suit: " + health + " -> " + full.getHealth());
        // The suit puts the fire out each tick.
        FoundrySuit.tick(full, false);
        helper.assertTrue(full.getRemainingFireTicks() <= 0, "The full suit is still on fire");
        DamageSource fall = sources.fall();
        helper.assertTrue(FoundrySuit.adjust(full, fall, 4.0F) == 4.0F, "The suit changed fall damage");
        helper.succeed();
    }

    // The shield lasts lavaShieldTicks in lava, then lava hurts; out of the lava it refills after lavaCooldownTicks.
    static void lavaTimerAndCooldown(GameTestHelper helper) {
        var lava = helper.getLevel().damageSources().lava();
        ServerPlayer player = wearing(helper, 4);
        int shieldTicks = ArcforgeConfig.FOUNDRY_LAVA_SHIELD_TICKS.getAsInt();
        int cooldown = ArcforgeConfig.FOUNDRY_LAVA_COOLDOWN_TICKS.getAsInt();
        for (int tick = 0; tick < 100; tick++) {
            FoundrySuit.tick(player, true);
        }
        helper.assertTrue(FoundrySuit.adjust(player, lava, 4.0F) == 0.0F, "Lava hurt while the shield lasted");
        helper.assertTrue(FoundrySuit.shield(player).remaining() == shieldTicks - 100, "Shield left: " + FoundrySuit.shield(player).remaining());
        for (int tick = 100; tick < shieldTicks; tick++) {
            FoundrySuit.tick(player, true);
        }
        helper.assertTrue(FoundrySuit.adjust(player, lava, 4.0F) == 4.0F, "Lava didn't hurt once the shield ran out");
        for (int tick = 0; tick < cooldown - 1; tick++) {
            FoundrySuit.tick(player, false);
        }
        helper.assertTrue(FoundrySuit.shield(player).remaining() == 0, "The shield refilled early");
        FoundrySuit.tick(player, false);
        helper.assertTrue(FoundrySuit.shield(player).remaining() == shieldTicks, "The shield didn't refill: " + FoundrySuit.shield(player).remaining());
        FoundrySuit.forget(player);
        helper.succeed();
    }
}
