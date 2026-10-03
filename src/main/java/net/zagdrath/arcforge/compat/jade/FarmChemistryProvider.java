/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.multiblock.BiogasDigesterStructure;
import net.zagdrath.arcforge.recipe.AirSeparatingRecipe;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Farm chemistry in Jade (their tanks, FE and heat show through Jade's own views):
//  - Air Separator: what it makes each operation ("80 mB Nitrogen + 20 mB Oxygen"), or that there's no air here.
//  - Haber Reactor: its temperature against the 450°C it needs.
//  - Biogas Digester (the controller or any casing of a formed tank): its temperature against the 35°C it needs, and
//    the lanes working ("Digesting: 2 / 4 lanes").
// Then the machine's status. The server builds the lines; Client draws them.
public enum FarmChemistryProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "farm_chemistry");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        switch (accessor.getBlockEntity()) {
            case AirSeparatorBlockEntity separator -> {
                AirSeparatingRecipe recipe = separator.getShownRecipe();
                if (recipe == null) {
                    lines.add(Component.translatable("jade.arcforge.air_separator.no_air").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.translatable("jade.arcforge.air_separator.makes", recipe.primary().amount(),
                            recipe.secondary().map(secondary -> secondary.amount()).orElse(0)));
                }
                lines.add(separator.getStatus().getDescription());
            }
            case HaberReactorBlockEntity reactor -> {
                int celsius = reactor.getHeat().getTemperature();
                int needed = reactor.getNeededTemperature();
                lines.add(Component.translatable("jade.arcforge.haber_reactor.temperature", celsius, needed)
                        .withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                lines.add(reactor.getStatus().getDescription());
            }
            case BiogasDigesterBlockEntity digester -> addDigester(lines, digester);
            case null, default -> {
                // A Biogas Digester Casing has no block entity: read its tank's controller.
                BiogasDigesterBlockEntity digester = BiogasDigesterStructure.findController(accessor.getLevel(), accessor.getPosition());
                if (digester == null) {
                    return null;
                }
                addDigester(lines, digester);
            }
        }
        return lines;
    }

    private static void addDigester(List<Component> lines, BiogasDigesterBlockEntity digester) {
        if (digester.isFormed()) {
            int celsius = digester.getHeat().getTemperature();
            int needed = BiogasDigesterBlockEntity.minTemperature();
            lines.add(Component.translatable("jade.arcforge.biogas_digester.temperature", celsius, needed)
                    .withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            lines.add(Component.translatable("jade.arcforge.biogas_digester.lanes", digester.busyLanes(), digester.getLanes()));
            if (!digester.getSulfur().isEmpty()) {
                lines.add(Component.translatable("jade.arcforge.biogas_digester.sulfur", digester.getSulfur().getCount()).withStyle(ChatFormatting.YELLOW));
            }
        }
        lines.add(digester.getStatus().getDescription());
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
            FarmChemistryProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
