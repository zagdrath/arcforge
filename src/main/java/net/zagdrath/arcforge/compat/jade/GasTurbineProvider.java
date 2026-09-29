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
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// The Gas Turbine Array in Jade, looking at any casing or window: "8.4k rpm · 4,200 FE/t", the throttle in Throttle mode,
// the exhaust ("Exhaust 700 HU/t at 600°C", plus "Venting to air" when no Heat port takes it) and the status.
// Its fuel, lubricant and FE show through Jade's own views.
public enum GasTurbineProvider implements StreamServerDataProvider<BlockAccessor, GasTurbineProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "gas_turbine");

    // throttle: % in Throttle mode, else -1.
    public record Data(int rpm, int fePerTick, int throttle, int exhaustHu, int exhaustCelsius, boolean venting, int status) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::rpm,
                ByteBufCodecs.VAR_INT, Data::fePerTick,
                ByteBufCodecs.VAR_INT, Data::throttle,
                ByteBufCodecs.VAR_INT, Data::exhaustHu,
                ByteBufCodecs.VAR_INT, Data::exhaustCelsius,
                ByteBufCodecs.BOOL, Data::venting,
                ByteBufCodecs.VAR_INT, Data::status,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        // A casing's master, or the array a window belongs to.
        var master = accessor.getBlockEntity() instanceof GasTurbineArrayBlockEntity casing ? casing.getMaster()
                : WindowProviders.master(accessor.getLevel(), accessor.getPosition());
        if (!(master instanceof GasTurbineArrayBlockEntity turbine)) {
            return null;
        }
        int throttle = turbine.getRedstoneMode() == RedstoneMode.THROTTLE ? (int) Math.round(turbine.throttle() * 100.0) : -1;
        return new Data((int) Math.round(turbine.getRpm()), turbine.getFePerTick(), throttle, turbine.getExhaustHu(), turbine.getExhaustCelsius(),
                turbine.isVenting(), turbine.getStatus().ordinal());
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
            GasTurbineProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                IDisplayHelper display = IDisplayHelper.get();
                tooltip.add(Component.translatable("jade.arcforge.gas_turbine", String.format(java.util.Locale.ROOT, "%.1f", data.rpm() / 1000.0),
                        display.humanReadableNumber(data.fePerTick(), "", false)));
                if (data.throttle() >= 0) {
                    tooltip.add(Component.translatable("gui.arcforge.gas_turbine.throttle", data.throttle()).withStyle(ChatFormatting.GRAY));
                }
                if (data.exhaustHu() > 0) {
                    tooltip.add(Component.translatable("gui.arcforge.gas_turbine.exhaust_tooltip",
                            display.humanReadableNumber(data.exhaustHu(), "", false), data.exhaustCelsius()));
                    if (data.venting()) {
                        tooltip.add(Component.translatable("gui.arcforge.gas_turbine.exhaust_venting").withStyle(ChatFormatting.GOLD));
                    }
                }
                tooltip.add(MachineStatus.byId(data.status()).getDescription().copy().withStyle(ChatFormatting.GRAY));
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
