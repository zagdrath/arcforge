/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.FlareStackBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CrackingUnitBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The refinery's downstream units in Jade. On a single-block unit or any block of a Cracking Unit or Steam Cracker: the
// Cracking Unit's mode, the operation's progress, the temperature it needs, the catalyst left and the status (HeatProvider shows the heat, Jade's
// own views the tanks). On any block of a Diesel Generator: its engine, FE/t against its full output, its speed, the
// fuel it burns and the status. On a Gas Wellhead: its flow and the gas left. On a Flare Stack: what it burns. The
// server builds the lines; Client draws them.
public enum RefineryProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "refinery");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        Object target = accessor.getBlockEntity();
        if (target instanceof FlareStackBlockEntity flare) {
            lines.add(flare.getBurnt() > 0
                    ? Component.translatable("jade.arcforge.flare_stack.burning", String.format(Locale.ROOT, "%,d", flare.getBurnt()))
                            .withStyle(ChatFormatting.GOLD)
                    : Component.translatable("jade.arcforge.flare_stack.idle").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (target instanceof net.zagdrath.arcforge.blockentity.machine.GasWellheadBlockEntity wellhead) {
            if (!wellhead.isOnWell()) {
                lines.add(Component.translatable("jade.arcforge.gas_wellhead.no_well").withStyle(ChatFormatting.RED));
                return lines;
            }
            lines.add(Component.translatable("jade.arcforge.gas_wellhead.rate", String.format(Locale.ROOT, "%.1f", wellhead.getRate()))
                    .withStyle(ChatFormatting.GOLD));
            net.zagdrath.arcforge.oil.OilDeposits.Deposit deposit = net.zagdrath.arcforge.oil.OilDeposits.at(level, wellhead.getBlockPos().below());
            if (deposit != null) {
                long left = net.zagdrath.arcforge.oil.OilDeposits.get(level.getServer()).gasRemaining(deposit, level.getSeed());
                lines.add(left > 0 ? Component.translatable("jade.arcforge.gas_wellhead.left",
                        String.format(Locale.ROOT, "%,d", net.zagdrath.arcforge.oil.OilDeposits.roughBuckets(left)))
                        .withStyle(ChatFormatting.GRAY) : Component.translatable("jade.arcforge.gas_wellhead.spent").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (target instanceof RefiningMachineBlockEntity machine) {
            refining(lines, machine.getCore(), machine.getStatus());
            return lines;
        }
        if (!(target instanceof CrackingUnitBlockEntity || target instanceof DieselGeneratorBlockEntity
                || target instanceof net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity)) {
            target = OilfieldStructures.findController(level, accessor.getPosition());
        }
        if (target instanceof net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity steamCracker) {
            if (steamCracker.isFormed()) {
                refining(lines, steamCracker.getCore(), steamCracker.getStatus());
            } else {
                lines.add(steamCracker.getStatus().getDescription());
            }
        } else if (target instanceof CrackingUnitBlockEntity cracker) {
            if (cracker.isFormed()) {
                refining(lines, cracker.getCore(), cracker.getStatus());
            } else {
                lines.add(cracker.getStatus().getDescription());
            }
        } else if (target instanceof DieselGeneratorBlockEntity generator) {
            if (generator.isFormed()) {
                lines.add(Component.translatable("jade.arcforge.diesel_generator.engine", Component.translatable("engine.arcforge."
                        + generator.getEngine().id())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("jade.arcforge.diesel_generator.output", String.format(Locale.ROOT, "%,d", generator.getMade()),
                        String.format(Locale.ROOT, "%,d", generator.maxOutput()), Math.round(generator.getSpeed() * 100))
                        .withStyle(ChatFormatting.GOLD));
                if (generator.getBurned() > 0) {
                    lines.add(Component.translatable("jade.arcforge.diesel_generator.fuel", String.format(Locale.ROOT, "%.2f", generator.getBurned()))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            lines.add(generator.getStatus().getDescription());
        } else {
            return null;
        }
        return lines;
    }

    private static void refining(List<Component> lines, RefiningCore core, MachineStatus status) {
        if (core.process() == net.zagdrath.arcforge.recipe.RefiningProcess.CRACKING) {
            lines.add(Component.translatable("jade.arcforge.cracking_unit.mode", core.mode().getDescription()).withStyle(ChatFormatting.AQUA));
            // After a switch, the other mode's product in a tank holds it up until it's drained.
            for (int i = 0; i < core.productCount(); i++) {
                var tank = core.product(i);
                if (tank.getAmount() > 0 && tank.getResource(0).getFluid() != core.layoutProducts().get(i)) {
                    lines.add(Component.translatable("jade.arcforge.cracking_unit.leftover", tank.getResource(0).getFluid().getFluidType().getDescription())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        if (core.total() > 0) {
            lines.add(Component.translatable("jade.arcforge.refining.progress", Math.round(100.0F * core.progress() / core.total())));
        }
        if (core.process().heated() && core.minTemperature() > 0) {
            lines.add(Component.translatable("jade.arcforge.refining.needs", core.minTemperature()).withStyle(ChatFormatting.GOLD));
        }
        if (core.catalystLife() > 0) {
            lines.add(Component.translatable("jade.arcforge.refining.catalyst", core.catalystLeft(), core.catalystLife()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(status.getDescription());
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, List<Component>> streamCodec() {
        return CODEC;
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
            RefineryProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
