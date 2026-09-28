/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.multiblock.ArcforgeFurnaceMenu;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// The Arcforge Furnace's port in Jade, while it holds oxygen: "Oxygen: 1,250 mB (×1.5)". This half gathers the
// data on the server; Client draws it.
public enum ArcforgeFurnaceProvider implements StreamServerDataProvider<BlockAccessor, ArcforgeFurnaceProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "arcforge_furnace");

    // The oxygen speed-up is x100.
    public record Data(int oxygen, int speed) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::oxygen,
                ByteBufCodecs.VAR_INT, Data::speed,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ArcforgeFurnaceBlockEntity furnace) || furnace.getOxygen().getAmount() <= 0 && !furnace.isBoosted()) {
            return null;
        }
        return new Data(furnace.getOxygen().getAmount(), (int) Math.round(ArcforgeConfig.FURNACE_OXYGEN_SPEED.getAsDouble() * 100.0));
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
            ArcforgeFurnaceProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> tooltip.add(Component.translatable(
                    "jade.arcforge.furnace.oxygen", IDisplayHelper.get().humanReadableNumber(data.oxygen(), "", false),
                    ArcforgeFurnaceMenu.boostText(data.speed() / 100.0))));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
