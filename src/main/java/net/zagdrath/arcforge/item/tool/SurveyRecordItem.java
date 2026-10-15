/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.zagdrath.arcforge.oil.SurveyRecord;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModMapDecorations;

// A Survey Record: a sheet of Paper the Seismic Survey Tool wrote one survey on (see SurveyRecord), all of it in the
// tooltip. Carried onto a filled map in the inventory and right-clicked, it marks the deposit on the map with a small oil
// derrick for its kind (the map's tooltip names each marker).
public class SurveyRecordItem extends Item {
    public SurveyRecordItem(Item.Properties properties) {
        super(properties);
    }

    // Carried onto a filled map and right-clicked: marks it.
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack map = slot.getItem();
        SurveyRecord survey = self.get(ModDataComponents.SURVEY_RECORD.get());
        if (!map.is(Items.FILLED_MAP) || survey == null || survey.kind().isEmpty()) {
            return false;
        }
        mark(map, survey);
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
        return true;
    }

    // Puts a deposit's marker on a filled map (once per spot). Public for the GameTests.
    public static void mark(ItemStack map, SurveyRecord survey) {
        List<SurveyRecord> markers = new ArrayList<>(map.getOrDefault(ModDataComponents.OIL_MARKERS.get(), List.of()));
        if (markers.stream().anyMatch(m -> m.x() == survey.x() && m.z() == survey.z())) {
            return;
        }
        markers.add(survey);
        map.set(ModDataComponents.OIL_MARKERS.get(), List.copyOf(markers));
        MapItemSavedData.addTargetDecoration(map, new BlockPos(survey.x(), 0, survey.z()), "arcforge_oil_" + survey.x() + "_" + survey.z(),
                ModMapDecorations.oil(survey.kind().get()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        SurveyRecord survey = stack.get(ModDataComponents.SURVEY_RECORD.get());
        if (survey != null) {
            builder.accept(Component.translatable("tooltip.arcforge.survey_record.place", survey.x(), survey.z(), survey.dimension())
                    .withStyle(ChatFormatting.GRAY));
            if (survey.kind().isEmpty()) {
                builder.accept(Component.translatable("message.arcforge.survey.none").withStyle(ChatFormatting.GRAY));
            } else {
                builder.accept(survey.kind().get().getDescription().copy().withStyle(ChatFormatting.GOLD));
                builder.accept(Component.translatable("tooltip.arcforge.survey_record.left", String.format(Locale.ROOT, "%,d", survey.buckets()), survey.y())
                        .withStyle(ChatFormatting.GRAY));
                builder.accept(SeismicSurveyToolItem.traits(survey).copy().withStyle(ChatFormatting.GRAY));
                builder.accept(Component.translatable("tooltip.arcforge.survey_record.map").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        super.appendHoverText(stack, context, display, builder, flag);
    }

    // A filled map's tooltip: the deposits marked on it (see ArcforgeClient's tooltip event).
    public static void mapTooltip(ItemStack map, List<Component> lines) {
        List<SurveyRecord> markers = map.get(ModDataComponents.OIL_MARKERS.get());
        if (markers == null || markers.isEmpty() || !map.has(DataComponents.MAP_ID)) {
            return;
        }
        for (SurveyRecord marker : markers) {
            marker.kind().ifPresent(kind -> lines.add(Component.translatable("tooltip.arcforge.oil_marker", kind.getDescription(), marker.x(), marker.z())
                    .withStyle(ChatFormatting.GOLD)));
        }
    }
}
