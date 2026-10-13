/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.oil.OilDeposits;

// The Seismic Survey Tool: thumped on the ground, it tells the player whether there's an oil deposit under this chunk,
// what kind, roughly how much Crude Oil is left in it and how deep it lies (see OilDeposits). Then it rests for
// world.oilDeposits.surveyCooldown ticks.
public class SeismicSurveyToolItem extends Item {
    public SeismicSurveyToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        player.sendSystemMessage(report(level, pos));
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 0.5F);
        int cooldown = ArcforgeConfig.SURVEY_COOLDOWN.getAsInt();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(context.getItemInHand(), cooldown);
        }
        return InteractionResult.SUCCESS;
    }

    // What the survey finds under pos. Public for the GameTests.
    public static Component report(ServerLevel level, BlockPos pos) {
        OilDeposits.Deposit deposit = OilDeposits.at(level, pos);
        if (deposit == null) {
            return Component.translatable("message.arcforge.survey.none").withStyle(ChatFormatting.GRAY);
        }
        long left = OilDeposits.get(level.getServer()).remaining(deposit);
        if (left <= 0) {
            return Component.translatable("message.arcforge.survey.drained", deposit.kind().getDescription(), deposit.y())
                    .withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.arcforge.survey.found", deposit.kind().getDescription(),
                String.format(Locale.ROOT, "%,d", OilDeposits.roughBuckets(left)), deposit.y()).withStyle(ChatFormatting.GOLD);
    }
}
