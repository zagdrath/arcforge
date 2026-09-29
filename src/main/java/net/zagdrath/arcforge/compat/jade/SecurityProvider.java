/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.SecurityMode;
import net.zagdrath.arcforge.security.SecurityRules;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Any owned block (or part of an owned structure) in Jade: "Owner: <name>" and "Security: <mode>", marked
// "(profile)" when it follows its owner's profile. Hidden with no owner or with security off.
public enum SecurityProvider implements StreamServerDataProvider<BlockAccessor, SecurityProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "security");

    public record Data(String owner, SecurityMode mode, boolean fromProfile) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Data::owner,
                SecurityMode.STREAM_CODEC.cast(), Data::mode,
                ByteBufCodecs.BOOL, Data::fromProfile,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!SecurityRules.enabled()) {
            return null;
        }
        Owned owned = SecurityRules.of(accessor.getLevel(), accessor.getPosition()).orElse(null);
        if (owned == null || owned.owner() == null) {
            return null;
        }
        return new Data(SecurityRules.ownerName(owned).getString(), SecurityRules.effectiveMode(owned, accessor.getLevel().getServer()),
                owned.securityOverride().isEmpty());
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            SecurityProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                tooltip.add(Component.translatable("security.arcforge.owner", data.owner()).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(data.fromProfile() ? "security.arcforge.mode_profile" : "security.arcforge.mode",
                        data.mode().displayName()).withStyle(ChatFormatting.GRAY));
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
