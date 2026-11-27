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
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.fusion.FusionModuleBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.LithiumIsotopeSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.TritiumExtractionUnitBlockEntity;
import net.zagdrath.arcforge.fusion.FusionAlarm;
import net.zagdrath.arcforge.fusion.Lithium6;
import net.zagdrath.arcforge.fusion.PlasmaSim;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The fusion tier in Jade. The Fusion Reactor, from its controller or any block of it (its cryostat, coils, wall,
// blanket, the air in its vessel): Tokamak or Stellarator and its major radius; while a plasma burns, its fusion power,
// Q, temperature and the pulse left; each alarm that's up; its status. A fusion module: what it holds, its FE, and
// whether it's on a formed reactor's wall. The Lithium Isotope Separator: the Li-6 share of what it holds and its
// progress. The Tritium Extraction Unit: its temperature against what it needs, and its Tritium and Helium. The Remote
// Handling Arm: what it's doing and the parts it has replaced. The server builds the lines; Client draws them.
public enum FusionProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "fusion");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        switch (accessor.getBlockEntity()) {
            case FusionModuleBlockEntity module -> module(lines, module, accessor);
            case LithiumIsotopeSeparatorBlockEntity separator -> separator(lines, separator);
            case TritiumExtractionUnitBlockEntity unit -> extraction(lines, unit);
            case RemoteHandlingArmBlockEntity arm -> arm(lines, arm);
            case FusionReactorBlockEntity reactor -> reactor(lines, reactor);
            case null, default -> {
                FusionReactorBlockEntity reactor = TokamakStructure.findController(accessor.getLevel(), accessor.getPosition());
                if (reactor == null) {
                    return null;
                }
                reactor(lines, reactor);
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static void reactor(List<Component> lines, FusionReactorBlockEntity reactor) {
        if (reactor.isFormed()) {
            lines.add(Component.translatable("jade.arcforge.fusion_reactor.shape",
                    Component.translatable(reactor.isStellarator() ? "jade.arcforge.fusion_reactor.stellarator" : "jade.arcforge.fusion_reactor.tokamak"),
                    reactor.getRadius()).withStyle(ChatFormatting.GRAY));
            PlasmaSim plasma = reactor.getSim();
            if (plasma != null && plasma.alive) {
                lines.add(Component.translatable("jade.arcforge.fusion_reactor.power", String.format(Locale.ROOT, "%,.0f", plasma.pFusion),
                        String.format(Locale.ROOT, "%.1f", Math.min(9_999.0, plasma.q()))).withStyle(ChatFormatting.AQUA));
                Component pulse = plasma.stellarator ? Component.translatable("jade.arcforge.fusion_reactor.steady")
                        : Component.translatable("jade.arcforge.fusion_reactor.pulse_left", grouped((long) plasma.pulseLeft()));
                lines.add(Component.translatable("jade.arcforge.fusion_reactor.plasma", String.format(Locale.ROOT, "%.1f", plasma.t), pulse)
                        .withStyle(ChatFormatting.GRAY));
            }
            int alarms = reactor.getAlarms();
            for (FusionAlarm alarm : FusionAlarm.values()) {
                if ((alarms & alarm.bit) != 0) {
                    lines.add(alarm.description().copy().withStyle(ChatFormatting.RED));
                }
            }
        }
        lines.add(reactor.getStatus().getDescription());
    }

    private static void tank(List<Component> lines, @Nullable FilteredFluidTank tank) {
        if (tank != null && tank.getAmount() > 0) {
            lines.add(Component.translatable("jade.arcforge.fusion.tank", tank.getResource(0).getFluid().getFluidType().getDescription(),
                    grouped(tank.getAmount())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void module(List<Component> lines, FusionModuleBlockEntity module, BlockAccessor accessor) {
        FusionReactorBlockEntity reactor = TokamakStructure.findAdjacent(accessor.getLevel(), accessor.getPosition());
        lines.add(Component.translatable(reactor != null ? "jade.arcforge.fusion_module.attached" : "jade.arcforge.fusion_module.detached")
                .withStyle(reactor != null ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        for (FilteredFluidTank input : module.getInputs()) {
            tank(lines, input);
        }
        tank(lines, module.getExhaust());
        if (module.getEnergy() != null) {
            lines.add(Component.translatable("jade.arcforge.fusion.energy", grouped(module.getEnergy().getAmountAsInt()),
                    grouped(module.getEnergy().getCapacityAsInt())).withStyle(ChatFormatting.GRAY));
        }
        if (module.getGenerator() != null) {
            lines.add(Component.translatable("jade.arcforge.fusion.energy", grouped(module.getGenerator().getAmountAsInt()),
                    grouped(module.getGenerator().getCapacityAsInt())).withStyle(ChatFormatting.GRAY));
            if (module.isRunning()) {
                lines.add(Component.translatable("jade.arcforge.fusion_module.converting", String.format(Locale.ROOT, "%.1f", module.lastMegawatts()),
                        grouped(module.fePerTick())).withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        if (module.isRunning()) {
            lines.add(Component.translatable("jade.arcforge.fusion_module.running", String.format(Locale.ROOT, "%.1f", module.lastMegawatts()))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static void separator(List<Component> lines, LithiumIsotopeSeparatorBlockEntity separator) {
        ItemStack input = separator.getItems().getStack(LithiumIsotopeSeparatorBlockEntity.SLOT_INPUT);
        if (!input.isEmpty()) {
            lines.add(Component.translatable("jade.arcforge.lithium_isotope_separator.feed", Lithium6.percent(Lithium6.assay(input)))
                    .withStyle(ChatFormatting.GREEN));
        }
        if (separator.getTotal() > 0 && separator.getProgress() > 0) {
            lines.add(Component.translatable("jade.arcforge.fusion.progress", separator.getProgress() * 100 / separator.getTotal())
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(separator.getStatus().getDescription());
    }

    private static void extraction(List<Component> lines, TritiumExtractionUnitBlockEntity unit) {
        lines.add(Component.translatable("gui.arcforge.celsius_needs", unit.getHeat().getTemperature(),
                Component.translatable("gui.arcforge.min_temp", net.zagdrath.arcforge.config.ArcforgeConfig.EXTRACTION_TEMPERATURE.getAsInt()))
                .withStyle(ChatFormatting.GOLD));
        tank(lines, unit.getTritium());
        tank(lines, unit.getHelium());
        lines.add(unit.getStatus().getDescription());
    }

    private static void arm(List<Component> lines, RemoteHandlingArmBlockEntity arm) {
        lines.add(Component.translatable("jade.arcforge.remote_handling_arm.replaced", arm.getReplaced()).withStyle(ChatFormatting.GRAY));
        lines.add(arm.getStatus().getDescription());
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
            FusionProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
