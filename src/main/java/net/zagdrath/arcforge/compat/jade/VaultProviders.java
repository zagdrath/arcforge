/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.storage.StorageCounts;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

// A Vault in Jade: its item and "Silver Ingot × 12,400 / 65,536" (or "Empty"), then "Locked" and "Void mode" when
// set. The contents are synced with the vault for its front display, so this reads the client's copy. Jade's own
// item view would show the vault as one stack capped at 64, so it's given nothing to show.
public final class VaultProviders {
    private VaultProviders() {}

    public enum Info implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof VaultBlockEntity vault)) {
                return;
            }
            ItemStack template = vault.getTemplate();
            if (vault.getAmount() > 0) {
                tooltip.add(JadeUI.smallItem(template));
                tooltip.append(Component.translatable("jade.arcforge.vault.contents", template.getHoverName(),
                        StorageCounts.grouped(vault.getAmount()), StorageCounts.grouped(vault.getCapacity())));
            } else {
                tooltip.add(Component.translatable("jade.arcforge.vault.empty").withStyle(ChatFormatting.GRAY));
            }
            if (vault.isLocked()) {
                tooltip.add(template.isEmpty()
                        ? Component.translatable("jade.arcforge.vault.locked").withStyle(ChatFormatting.GOLD)
                        : Component.translatable("tooltip.arcforge.vault.locked", template.getHoverName()).withStyle(ChatFormatting.GOLD));
            }
            if (vault.isVoidMode()) {
                tooltip.add(Component.translatable("jade.arcforge.vault.void").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "vault");
        }
    }

    // No item view for vaults: Info shows the real count.
    public enum HideItems implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;

        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            return accessor instanceof BlockAccessor block && block.getBlockEntity() instanceof VaultBlockEntity ? List.of() : null;
        }

        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return List.of();
        }

        @Override
        public Identifier getUid() {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "vault_items");
        }
    }
}
