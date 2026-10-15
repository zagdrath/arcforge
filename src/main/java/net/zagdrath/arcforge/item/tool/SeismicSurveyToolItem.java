/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.oil.OilCavern;
import net.zagdrath.arcforge.oil.OilCaverns;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.oil.SurveyRecord;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// The Seismic Survey Tool: thumped on the ground, it tells the player whether there's an oil deposit under this chunk,
// what kind, roughly how much oil is left in it and how deep it lies (see OilDeposits): where the chunk was generated
// with caverns, the top of the cavern under that spot, whether it's sweet or sour and whether it has a gas cap. With
// Paper in the player's inventory it uses a sheet and hands over a Survey Record of the result. Then it rests for
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
        SurveyRecord survey = survey(level, pos);
        player.sendSystemMessage(report(survey));
        recordOnPaper(player, survey);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 0.5F);
        int cooldown = ArcforgeConfig.SURVEY_COOLDOWN.getAsInt();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(context.getItemInHand(), cooldown);
        }
        return InteractionResult.SUCCESS;
    }

    // With Paper in the inventory, one sheet becomes a Survey Record of this survey (given, or dropped if there's no room).
    // Public for the GameTests.
    public static void recordOnPaper(Player player, SurveyRecord survey) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.PAPER)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                ItemStack record = new ItemStack(ModItems.SURVEY_RECORD.get());
                record.set(ModDataComponents.SURVEY_RECORD.get(), survey);
                if (!inventory.add(record)) {
                    player.drop(record, false, Prediction.SERVER_ONLY);
                }
                return;
            }
        }
    }

    // What the survey finds under pos. Public for the GameTests.
    public static SurveyRecord survey(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        OilDeposits.Deposit deposit = OilDeposits.at(level, pos);
        if (deposit == null) {
            return new SurveyRecord(dimension, pos.getX(), pos.getZ(), Optional.empty(), 0L, 0, false, false);
        }
        long left = OilDeposits.get(level.getServer()).remaining(deposit);
        boolean caverns = OilDeposits.generatedWithCaverns(level, pos);
        OilCavern cavern = OilCaverns.cavernAt(level, deposit, pos);
        int top = cavern != null ? cavern.topAt(pos.getX(), pos.getZ()) : Integer.MIN_VALUE;
        int y = top != Integer.MIN_VALUE ? top : deposit.y();
        return new SurveyRecord(dimension, pos.getX(), pos.getZ(), Optional.of(deposit.kind()), left > 0 ? OilDeposits.roughBuckets(left) : 0L, y,
                caverns && deposit.sour(), caverns && deposit.gasCap());
    }

    // The survey as a chat line.
    public static Component report(SurveyRecord survey) {
        if (survey.kind().isEmpty()) {
            return Component.translatable("message.arcforge.survey.none").withStyle(ChatFormatting.GRAY);
        }
        MutableComponent kind = survey.kind().get().getDescription().copy();
        kind.append(" (").append(traits(survey)).append(")");
        if (survey.buckets() <= 0) {
            return Component.translatable("message.arcforge.survey.drained", kind, survey.y()).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.arcforge.survey.found", kind, String.format(Locale.ROOT, "%,d", survey.buckets()), survey.y())
                .withStyle(ChatFormatting.GOLD);
    }

    // "sweet" or "sour", and ", gas cap" when it has one.
    public static Component traits(SurveyRecord survey) {
        MutableComponent traits = Component.translatable(survey.sour() ? "message.arcforge.survey.sour" : "message.arcforge.survey.sweet");
        if (survey.gasCap()) {
            traits.append(Component.translatable("message.arcforge.survey.gas_cap"));
        }
        return traits;
    }
}
