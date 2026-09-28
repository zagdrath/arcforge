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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.CondenserArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SuperheaterArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.steam.SteamGrade;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// The steam cycle in Jade, looking at any casing: a Superheater Array's "Steam → Superheated · 200 mB/t" (or
// "Passing through (420 °C)"), a Condenser Array's "Condensing 287 mB/t", and a Steam Turbine Array's exhaust
// ("Exhaust: 1,200 mB", "Vacuum bonus +10%"). Their tanks and heat show through Jade's own views. This half
// gathers the data on the server; Client draws it.
public enum SteamCycleProvider implements StreamServerDataProvider<BlockAccessor, SteamCycleProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_cycle");

    public enum Kind { SUPERHEATER, CONDENSER, TURBINE }

    // Superheater: a = the input grade (-1 none), b = the grade made (-1 passing through), c = mB/t, d = °C.
    // Condenser: c = mB/t condensed. Turbine: a = 1 with an Exhaust port, b = 1 with the vacuum bonus, c = mB of exhaust.
    public record Data(int kind, int a, int b, int c, int d) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::kind,
                ByteBufCodecs.VAR_INT, Data::a,
                ByteBufCodecs.VAR_INT, Data::b,
                ByteBufCodecs.VAR_INT, Data::c,
                ByteBufCodecs.VAR_INT, Data::d,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        Level level = accessor.getLevel();
        BlockEntity blockEntity = accessor.getBlockEntity();
        SuperheaterArrayBlockEntity superheater = SuperheaterArrayCasingBlock.STRUCTURE.findController(level, accessor.getPosition());
        if (superheater != null) {
            SteamGrade in = SteamGrade.of(superheater.getSteamIn().getResource(0));
            SteamGrade target = superheater.getTarget();
            return new Data(Kind.SUPERHEATER.ordinal(), in != null ? in.ordinal() : -1, target != null ? target.ordinal() : -1,
                    superheater.getFlow(), superheater.getHeat().getTemperature());
        }
        CondenserArrayBlockEntity condenser = CondenserArrayCasingBlock.STRUCTURE.findController(level, accessor.getPosition());
        if (condenser != null) {
            return new Data(Kind.CONDENSER.ordinal(), 0, 0, condenser.getCondensed(), 0);
        }
        if (blockEntity instanceof SteamTurbineArrayBlockEntity casing && casing.getMaster() instanceof SteamTurbineArrayBlockEntity turbine) {
            return new Data(Kind.TURBINE.ordinal(), turbine.hasExhaust() ? 1 : 0, turbine.isVacuum() ? 1 : 0, turbine.getExhaust().getAmount(), 0);
        }
        return null;
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
            SteamCycleProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                IDisplayHelper display = IDisplayHelper.get();
                switch (Kind.values()[Math.clamp(data.kind(), 0, Kind.values().length - 1)]) {
                    case SUPERHEATER -> {
                        if (data.a() < 0) {
                            return;
                        }
                        SteamGrade in = SteamGrade.values()[data.a()];
                        if (data.b() >= 0) {
                            SteamGrade out = SteamGrade.values()[data.b()];
                            tooltip.add(Component.translatable("jade.arcforge.superheater", in.shortName(), out.shortName().copy().withColor(out.guiColor()),
                                    display.humanReadableNumber(data.c(), "", false)));
                        } else {
                            tooltip.add(Component.translatable("jade.arcforge.superheater.passing", data.d()).withStyle(ChatFormatting.GRAY));
                        }
                    }
                    case CONDENSER -> tooltip.add(Component.translatable("jade.arcforge.condenser", display.humanReadableNumber(data.c(), "", false)));
                    case TURBINE -> {
                        if (data.a() == 0) {
                            return;
                        }
                        tooltip.add(Component.translatable("jade.arcforge.turbine.exhaust",
                                Component.translatable("gui.arcforge.mb_amount", display.humanReadableNumber(data.c(), "", false))));
                        if (data.b() != 0) {
                            tooltip.add(Component.translatable("jade.arcforge.turbine.vacuum",
                                    Math.round(ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble() * 100)).withStyle(ChatFormatting.AQUA));
                        }
                    }
                }
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
