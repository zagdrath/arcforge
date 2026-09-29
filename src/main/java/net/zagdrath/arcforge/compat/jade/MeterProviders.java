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
import net.zagdrath.arcforge.blockentity.logistics.ChargepadBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.machine.meter.MeterKind;
import net.zagdrath.arcforge.machine.meter.MeterSettings;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the Meters ("Rate: 12,480 FE/t", "Signal above 10,000 FE/t") and the Chargepad ("Stored: n / 500,000 FE",
// "Charging 2 item(s)").
public final class MeterProviders {
    private MeterProviders() {}

    public enum Meter implements StreamServerDataProvider<BlockAccessor, Meter.Data> {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "meter");

        public record Data(int kind, int rate, int threshold, int mode) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::kind,
                    ByteBufCodecs.VAR_INT, Data::rate,
                    ByteBufCodecs.VAR_INT, Data::threshold,
                    ByteBufCodecs.VAR_INT, Data::mode,
                    Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MeterBlockEntity meter)) {
                return null;
            }
            return new Data(meter.getKind().ordinal(), meter.getRate(), meter.getSettings().threshold(), meter.getSettings().mode().ordinal());
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
                Meter.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    MeterKind kind = MeterKind.values()[Math.floorMod(data.kind(), MeterKind.values().length)];
                    tooltip.add(Component.translatable("jade.arcforge.meter.rate", ArcforgeGui.grouped(data.rate()), kind.unit()));
                    tooltip.add(Component.translatable("jade.arcforge.meter.threshold", MeterSettings.Mode.byId(data.mode()).displayName(),
                            ArcforgeGui.grouped(data.threshold()), kind.unit()).withStyle(ChatFormatting.GRAY));
                });
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    public enum Chargepad implements StreamServerDataProvider<BlockAccessor, Chargepad.Data> {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "chargepad");

        public record Data(int stored, int capacity, int charging) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::stored,
                    ByteBufCodecs.VAR_INT, Data::capacity,
                    ByteBufCodecs.VAR_INT, Data::charging,
                    Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ChargepadBlockEntity pad)) {
                return null;
            }
            return new Data(pad.getEnergy().getAmountAsInt(), pad.getEnergy().getCapacityAsInt(), pad.getItemsCharging());
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
                Chargepad.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    tooltip.add(Component.translatable("jade.arcforge.chargepad.stored", ArcforgeGui.grouped(data.stored()),
                            ArcforgeGui.grouped(data.capacity())));
                    if (data.charging() > 0) {
                        tooltip.add(Component.translatable("jade.arcforge.chargepad.charging", data.charging()).withStyle(ChatFormatting.AQUA));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }
}
