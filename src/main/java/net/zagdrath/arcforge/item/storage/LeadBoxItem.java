/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.menu.tool.LeadBoxMenu;
import net.zagdrath.arcforge.nuclear.RadiationField;

// The Lead Box: nine slots of lead-lined carrying case. Nothing radioactive inside it is felt (see RadiationField),
// wherever the box is: in a pocket, a chest or on the ground. Use it in the main hand to open it; its contents live on
// the item (minecraft:container). It can't hold another Lead Box or anything else that holds items.
public class LeadBoxItem extends Item {
    public static final int SLOTS = 9;

    public LeadBoxItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().getSelectedSlot();
            ItemStack stack = player.getItemInHand(hand);
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LeadBoxMenu(id, inventory, slot), stack.getHoverName()),
                    buf -> buf.writeVarInt(slot));
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    // Boxes and shulker boxes don't go inside.
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    // How many stacks it holds, and the dose they'd give out of it.
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        long stacks = contents.nonEmptyItemCopyStream().count();
        builder.accept(Component.translatable("tooltip.arcforge.lead_box.contents", stacks, SLOTS).withStyle(ChatFormatting.GRAY));
        double shielded = contents.nonEmptyItemCopyStream().mapToDouble(RadiationField::rate).sum();
        if (shielded > 0.0) {
            builder.accept(Component.translatable("tooltip.arcforge.lead_box.shielded", net.zagdrath.arcforge.nuclear.RadiationText.rate(shielded))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
