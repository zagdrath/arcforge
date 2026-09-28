/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// An Arc Melter in Jade, while it has something to melt: "Melting: 42%" and "Cobblestone → 250 mB lava".
// Its energy and tank show through Jade's own views. This half gathers the data on the server; Client draws it.
public enum ArcMelterProvider implements StreamServerDataProvider<BlockAccessor, ArcMelterProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_melter");

    public record Data(int percent, ItemStack input, int amount) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::percent,
                ItemStack.STREAM_CODEC, Data::input,
                ByteBufCodecs.VAR_INT, Data::amount,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ArcMelterBlockEntity melter) || melter.getMelting().isEmpty() || melter.getTotal() <= 0) {
            return null;
        }
        return new Data(100 * melter.getProgress() / melter.getTotal(), melter.getMelting(), melter.getMeltingAmount());
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    // Jade needs the tooltip half as its own provider, under the same uid.
    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            ArcMelterProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                tooltip.add(Component.translatable("jade.arcforge.arc_melter.progress", data.percent()));
                tooltip.add(Component.translatable("jade.arcforge.arc_melter.recipe", data.input().getHoverName(),
                        IDisplayHelper.get().humanReadableNumber(data.amount(), "", false)));
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
