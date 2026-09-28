/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.conduit;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.registry.ModDataComponents;

// The Conduit Filter: right-click a connection (an arm facing a machine) of an item, fluid or pressurized
// conduit to install it there. Its settings (FilterSettings) stay on the item, so a filter taken off with
// the Wrench can be put back anywhere without setting it up again. Right-click the filtered connection
// with an empty hand to open its settings.
public class ConduitFilterItem extends Item {
    public ConduitFilterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConduitBlock) || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (!conduit.acceptsFilters()) {
            if (!level.isClientSide()) {
                tell(player, Component.translatable("message.arcforge.conduit_filter.wrong_conduit"));
            }
            return InteractionResult.PASS;
        }
        // The server does the work; the client only swings.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction side = ConduitBlock.sideAt(pos, context.getClickLocation());
        if (!ConduitBlock.mode(state, side).isPort()) {
            tell(player, Component.translatable("message.arcforge.conduit_filter.not_a_connection"));
        } else if (conduit.hasFilter(side)) {
            tell(player, Component.translatable("message.arcforge.conduit_filter.occupied"));
        } else {
            ItemStack held = context.getItemInHand();
            conduit.setFilter(side, held.copyWithCount(1));
            if (player == null || !player.hasInfiniteMaterials()) {
                held.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.2F);
            tell(player, Component.translatable("message.arcforge.conduit_filter.installed",
                    Component.translatable("conduit_side.arcforge." + side.getSerializedName())));
        }
        return InteractionResult.SUCCESS;
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.sendOverlayMessage(message);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FilterSettings settings = stack.get(ModDataComponents.CONDUIT_FILTER.get());
        if (settings == null) {
            builder.accept(Component.translatable("item.arcforge.conduit_filter.tooltip.unset").withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(Component.translatable("item.arcforge.conduit_filter.tooltip.mode", settings.listName(), settings.flow().displayName(),
                Component.translatable("item.arcforge.conduit_filter.tooltip.entries", settings.count())).withStyle(ChatFormatting.GRAY));
        for (FilterSettings.Entry entry : settings.entries()) {
            if (!entry.isEmpty()) {
                builder.accept(Component.literal("- ").append(describe(entry)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    // An entry's name, e.g. "Iron Ingot" or "Water", or its tag, e.g. "#c:ingots".
    public static Component describe(FilterSettings.Entry entry) {
        if (entry.tag().isPresent()) {
            return Component.literal("#" + entry.tag().get());
        }
        if (entry.item().isPresent()) {
            return entry.item().get().create().getHoverName();
        }
        return entry.fluid().map(fluid -> fluid.getFluidType().getDescription()).orElse(Component.empty());
    }
}
