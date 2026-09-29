/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.security.SecurityRules;

// Copies a machine's, conduit's, Vault's or multiblock's setup and pastes it onto another of the same kind (and, for
// a structure, the same size): sneak-use a block to copy, use one to paste, sneak-use the air to clear. It never
// copies items, fluids, energy or upgrades (see SettingsCopyable).
public class SettingsCardItem extends Item {
    public SettingsCardItem(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    public static @Nullable SettingsCardData data(ItemStack stack) {
        return stack.get(ModDataComponents.SETTINGS_CARD.get());
    }

    // What decides the settings at pos: a formed structure's controller, or the block entity there.
    public static @Nullable SettingsCopyable target(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MultiblockPart part && part.findController(level, pos) instanceof SettingsCopyable controller) {
            return controller;
        }
        return level.getBlockEntity(pos) instanceof SettingsCopyable copyable ? copyable : null;
    }

    private static Component nameOf(SettingsCopyable target) {
        if (target instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockState().getBlock().getName();
        }
        return Component.empty();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        var owned = SecurityRules.of(level, pos);
        if (owned.isPresent() && !SecurityRules.canAccess(player, owned.get())) {
            SecurityRules.deny(player, owned.get(), false);
            return InteractionResult.FAIL;
        }
        SettingsCopyable target = target(level, pos);
        ItemStack card = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            copy(level, player, card, target);
        } else {
            paste(level, player, card, target, pos);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean copy(Level level, Player player, ItemStack card, @Nullable SettingsCopyable target) {
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.settings_card.nothing"));
            return false;
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        target.writeSettings(output);
        CompoundTag settings = output.buildResult();
        List<Component> summary = target.describe(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), settings));
        Component name = nameOf(target);
        card.set(ModDataComponents.SETTINGS_CARD.get(), new SettingsCardData(target.settingsKind(), name, target.settingsSize(), settings, summary));
        player.sendOverlayMessage(Component.translatable("message.arcforge.settings_card.copied", name));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
        return true;
    }

    // Returns how many settings were skipped, or -1 if nothing was pasted.
    public static int paste(Level level, Player player, ItemStack card, @Nullable SettingsCopyable target, BlockPos pos) {
        SettingsCardData data = data(card);
        if (data == null) {
            return -1;
        }
        if (target == null || !target.settingsKind().equals(data.kind())) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.settings_card.wrong_type", data.name()));
            return -1;
        }
        if (!target.settingsSize().equals(data.size())) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.settings_card.wrong_size",
                    data.size().map(size -> size.depth()).orElse(0), data.name()));
            return -1;
        }
        int skipped = target.readSettings(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data.settings()));
        BlockPos updated = target instanceof MultiblockController controller ? controller.getBlockPos() : pos;
        BlockState state = level.getBlockState(updated);
        level.sendBlockUpdated(updated, state, state, 3);
        player.sendOverlayMessage(skipped > 0 ? Component.translatable("message.arcforge.settings_card.partial", data.name(), skipped)
                : Component.translatable("message.arcforge.settings_card.pasted", data.name()));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
        return skipped;
    }

    // Sneak-use in the air clears it.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack card = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || data(card) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            card.remove(ModDataComponents.SETTINGS_CARD.get());
            player.sendOverlayMessage(Component.translatable("message.arcforge.settings_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        SettingsCardData data = data(stack);
        if (data == null) {
            builder.accept(Component.translatable("tooltip.arcforge.settings_card.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(Component.translatable("tooltip.arcforge.settings_card.stored", data.name()).withStyle(ChatFormatting.GRAY));
        data.size().ifPresent(size -> builder.accept(size.describe().copy().withStyle(ChatFormatting.GRAY)));
        data.summary().forEach(line -> builder.accept(line.copy().withStyle(ChatFormatting.DARK_GRAY)));
        builder.accept(Component.translatable("tooltip.arcforge.settings_card.hint", data.name()).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
