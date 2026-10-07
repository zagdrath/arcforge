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
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.steam.TurbineLayout;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// The steam cycle in Jade, looking at any casing: a Steam Boiler Array's "Heat use 3,200 / 3,200 HU/t" (gold at its
// limit, so an undersized boiler behind a heat source shows), a Superheater Array's "Steam → Superheated · 200 mB/t" (or
// "Passing through (420 °C)"), a Condenser Array's "Condensing 287 mB/t", and a Steam Turbine Array's exhaust
// ("Exhaust: 1,200 mB", "Vacuum bonus +10%"), with the part of the turbine-generator set the casing is in ("Section:
// Low-pressure casing") and, on a fitted spot, the port that fits there ("Exhaust neck: fits an Exhaust port"). Their
// tanks and heat show through Jade's own views. This half gathers the data on the server; Client draws it.
public enum SteamCycleProvider implements StreamServerDataProvider<BlockAccessor, SteamCycleProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_cycle");

    public enum Kind { SUPERHEATER, CONDENSER, TURBINE, BOILER }

    // Superheater: a = the input grade (-1 none), b = the grade made (-1 passing through), c = mB/t, d = °C.
    // Condenser: c = mB/t condensed. Turbine: a = 1 with an Exhaust port, b = 1 with the vacuum bonus, c = mB of exhaust,
    // d = the TurbineLayout.Part the block is in, plus 1 (0 unknown), and its TurbineLayout.Spot times 16.
    // Boiler: c = HU/t it boiled with last tick, d = the most HU/t it boils with.
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
        var boilerMaster = blockEntity instanceof SteamBoilerArrayBlockEntity casing ? casing.getMaster() : WindowProviders.master(level, accessor.getPosition());
        if (boilerMaster instanceof SteamBoilerArrayBlockEntity boiler) {
            return new Data(Kind.BOILER.ordinal(), 0, 0, boiler.getCore().getHeatUsed(), boiler.maxHeatPerTick());
        }
        var turbineMaster = blockEntity instanceof SteamTurbineArrayBlockEntity casing ? casing.getMaster() : WindowProviders.master(level, accessor.getPosition());
        if (turbineMaster instanceof SteamTurbineArrayBlockEntity turbine) {
            int where = 0;
            ShellStructure.Shell shell = turbine.getShell();
            if (shell != null && shell.contains(accessor.getPosition())) {
                TurbineLayout layout = TurbineLayout.of(shell, turbine.getFacing());
                where = layout.partAt(TurbineLayout.blockU(shell, accessor.getPosition())).ordinal() + 1
                        | layout.spotAt(shell, accessor.getPosition()).ordinal() << 4;
            }
            return new Data(Kind.TURBINE.ordinal(), turbine.hasExhaust() ? 1 : 0, turbine.isVacuum() ? 1 : 0, turbine.getExhaust().getAmount(), where);
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
                    case BOILER -> tooltip.add(Component.translatable("jade.arcforge.boiler.heat_use", display.humanReadableNumber(data.c(), "", false),
                            display.humanReadableNumber(data.d(), "", false)).withStyle(data.c() >= data.d() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                    case CONDENSER -> tooltip.add(Component.translatable("jade.arcforge.condenser", display.humanReadableNumber(data.c(), "", false)));
                    case TURBINE -> {
                        int part = (data.d() & 15) - 1;
                        if (part >= 0 && part < TurbineLayout.Part.values().length) {
                            tooltip.add(Component.translatable("jade.arcforge.turbine.section",
                                    Component.translatable(TurbineLayout.Part.values()[part].translationKey())).withStyle(ChatFormatting.GRAY));
                        }
                        int spot = data.d() >> 4;
                        if (spot > 0 && spot < TurbineLayout.Spot.values().length) {
                            tooltip.add(Component.translatable(TurbineLayout.Spot.values()[spot].translationKey()).withStyle(ChatFormatting.GRAY));
                        }
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
