/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.zagdrath.arcforge.block.storage.CrateBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModBlocks;

// Tempered, Hardened and Arcforged Storage Upgrades: right-click a placed Crate or Vault of the tier before to turn
// it into this tier's where it stands, keeping everything it holds, its facing, face settings and name. (Crafting the
// block with the next tier's alloy does the same to one in item form; see the tier_upgrade recipes.)
public class StorageUpgradeItem extends Item {
    private final ConduitTier target;

    public StorageUpgradeItem(ConduitTier target, Item.Properties properties) {
        super(properties);
        this.target = target;
    }

    public ConduitTier getTarget() {
        return target;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrateBlock) && !(state.getBlock() instanceof VaultBlock)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        ConduitTier from = ((StorageBlock) state.getBlock()).getTier();
        if (from != target.previous()) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.storage_upgrade.wrong_tier", target.previous().getDisplayName()));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState upgraded = upgrade(level, pos, state, target);
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.storage_upgrade.done", upgraded.getBlock().getName()));
        }
        return InteractionResult.SUCCESS;
    }

    // Swaps the Crate or Vault at pos for the `tier` one, moving its block entity's data and components across. Returns the new state.
    public static BlockState upgrade(Level level, BlockPos pos, BlockState state, ConduitTier tier) {
        BlockEntity old = level.getBlockEntity(pos);
        CompoundTag saved = old != null ? old.saveWithoutMetadata(level.registryAccess()) : new CompoundTag();
        DataComponentMap components = old != null ? old.components() : DataComponentMap.EMPTY;
        if (old instanceof StorageBlockEntity storage) {
            // Its contents move to the new block, so none drop as the old one goes.
            storage.markUpgrading();
        }
        BlockState next = state.getBlock() instanceof VaultBlock
                ? ModBlocks.vault(tier).get().defaultBlockState()
                        .setValue(VaultBlock.FACING, state.getValue(VaultBlock.FACING))
                        .setValue(VaultBlock.LOCKED, state.getValue(VaultBlock.LOCKED))
                        .setValue(VaultBlock.VOID, state.getValue(VaultBlock.VOID))
                : ModBlocks.crate(tier).get().defaultBlockState().setValue(CrateBlock.FACING, state.getValue(CrateBlock.FACING));
        level.setBlock(pos, next, Block.UPDATE_ALL);
        BlockEntity replacement = level.getBlockEntity(pos);
        if (replacement != null) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(replacement.problemPath(), LogUtils.getLogger())) {
                replacement.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
            }
            replacement.setComponents(components);
            replacement.setChanged();
        }
        level.sendBlockUpdated(pos, next, next, Block.UPDATE_ALL);
        // Conduits and hoppers ask for the new, bigger handler.
        level.invalidateCapabilities(pos);
        level.updateNeighbourForOutputSignal(pos, next.getBlock());
        return next;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.storage_upgrade", target.previous().getDisplayName()).withStyle(ChatFormatting.GRAY));
    }
}
