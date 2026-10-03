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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.heat.FlueGas;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Carbon capture in Jade. The burners (Combustion Plant, Firebox, Fuel Burner, Firebox Array, Gas Turbine Array): the
// Carbon Dioxide in their flue and what they give off a tick, while they have any. The Carbon Reclaimer: its gases, its
// operation's FE and its status. The Gasifier: its temperature against the 800°C it needs, its progress, its Steam and
// Syngas, its status. The Fischer-Tropsch Reactor: its temperature against its window, its Syngas, its catalyst and its
// status. The server builds the lines; Client draws them.
public enum CarbonCaptureProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "carbon_capture");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static @Nullable FlueGas flueOf(BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (be instanceof BurnerBlockEntity burner) {
            return burner.getFlue();
        }
        if (be instanceof FuelBurnerBlockEntity burner) {
            return burner.getFlue();
        }
        if (be instanceof GasTurbineArrayBlockEntity casing) {
            return casing.getMaster() instanceof GasTurbineArrayBlockEntity turbine ? turbine.getFlue() : null;
        }
        FireboxArrayBlockEntity array = be instanceof FireboxArrayBlockEntity own ? own
                : FireboxArrayStructure.findController(accessor.getLevel(), accessor.getPosition());
        return array != null ? array.getFlue() : null;
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        BlockEntity be = accessor.getBlockEntity();
        if (be instanceof CarbonReclaimerBlockEntity reclaimer) {
            lines.add(Component.translatable("jade.arcforge.carbon_reclaimer.gases", grouped(reclaimer.getCarbonDioxide().getAmount()),
                    grouped(reclaimer.getHydrogen().getAmount())).withStyle(ChatFormatting.AQUA));
            if (reclaimer.getCost() > 0) {
                lines.add(Component.translatable("jade.arcforge.carbon_reclaimer.progress", grouped(reclaimer.getSpent()), grouped(reclaimer.getCost())));
            }
            lines.add(reclaimer.getStatus().getDescription());
            return lines;
        }
        if (be instanceof GasifierBlockEntity gasifier) {
            int celsius = gasifier.getHeat().getTemperature();
            int needed = GasifierBlockEntity.minTemperature();
            lines.add(Component.translatable("jade.arcforge.gasifier.temperature", grouped(celsius), grouped(needed))
                    .withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            if (gasifier.getTotal() > 0) {
                lines.add(Component.translatable("jade.arcforge.gasifier.progress", Math.round(100.0 * gasifier.getProgress() / gasifier.getTotal())));
            }
            lines.add(Component.translatable("jade.arcforge.gasifier.tanks", grouped(gasifier.getSteam().getAmount()),
                    grouped(gasifier.getSyngas().getAmount())).withStyle(ChatFormatting.AQUA));
            lines.add(gasifier.getStatus().getDescription());
            return lines;
        }
        if (be instanceof FischerTropschReactorBlockEntity reactor) {
            int celsius = reactor.getHeat().getTemperature();
            boolean inWindow = celsius >= FischerTropschReactorBlockEntity.minTemperature() && celsius <= FischerTropschReactorBlockEntity.maxTemperature();
            lines.add(Component.translatable("jade.arcforge.fischer_tropsch_reactor.temperature", celsius, FischerTropschReactorBlockEntity.minTemperature(),
                    FischerTropschReactorBlockEntity.maxTemperature()).withStyle(inWindow ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            lines.add(Component.translatable("jade.arcforge.fischer_tropsch_reactor.syngas", grouped(reactor.getSyngas().getAmount()))
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("jade.arcforge.fischer_tropsch_reactor.catalyst", reactor.catalystLeft()).withStyle(ChatFormatting.GRAY));
            lines.add(reactor.getStatus().getDescription());
            return lines;
        }
        FlueGas flue = flueOf(accessor);
        if (flue == null || flue.getTank().getAmount() <= 0 && flue.getMadeLastTick() <= 0) {
            return null;
        }
        lines.add(Component.translatable("jade.arcforge.flue_gas", grouped(flue.getTank().getAmount()), flue.getMadeLastTick())
                .withStyle(ChatFormatting.GRAY));
        return lines;
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
            CarbonCaptureProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
