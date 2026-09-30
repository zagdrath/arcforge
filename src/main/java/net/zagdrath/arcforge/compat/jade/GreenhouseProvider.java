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
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.PlantingBedBlockEntity;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Greenhouse Array in Jade:
//  - a Planting Bed: what grows there and how far ("Wheat Seeds: 45%"), or what it still needs (a soil, a seed);
//  - any part of a formed greenhouse (controller, frame, glass, lamp, bed): its temperature, growth speed, beds growing
//    and status.
// The server builds the lines; Client draws them.
public enum GreenhouseProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "greenhouse");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        if (accessor.getBlockEntity() instanceof PlantingBedBlockEntity bed) {
            if (bed.getSoil().isEmpty()) {
                lines.add(Component.translatable("jade.arcforge.planting_bed.no_soil").withStyle(ChatFormatting.GRAY));
            } else if (bed.getSeed().isEmpty()) {
                lines.add(Component.translatable("jade.arcforge.planting_bed.no_seed", bed.getSoil().getHoverName()).withStyle(ChatFormatting.GRAY));
            } else if (bed.plant() == null) {
                lines.add(Component.translatable("jade.arcforge.planting_bed.cant_grow", bed.getSeed().getHoverName(), bed.getSoil().getHoverName())
                        .withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable("jade.arcforge.cloche.growing", bed.getSeed().getHoverName(), (int) Math.floor(bed.growth() * 100)));
            }
        }
        if (accessor.getBlockState().getBlock() instanceof PressureGlassBlock && !PressureGlassBlock.isFormed(accessor.getBlockState())) {
            return lines.isEmpty() ? null : lines;
        }
        GreenhouseBlockEntity greenhouse = accessor.getBlockEntity() instanceof GreenhouseBlockEntity own ? own
                : GreenhouseStructure.findController(accessor.getLevel(), accessor.getPosition());
        if (greenhouse != null && greenhouse.isFormed()) {
            lines.add(Component.translatable("jade.arcforge.greenhouse.climate", String.format(Locale.ROOT, "%.1f", greenhouse.getTemperature()),
                    String.format(Locale.ROOT, "%.2f", greenhouse.currentSpeed()))
                    .withStyle(greenhouse.temperatureFactor() >= 1.0 ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            lines.add(Component.translatable("jade.arcforge.greenhouse.beds", greenhouse.getGrowing(), greenhouse.getBeds().size()));
            lines.add(greenhouse.getStatus().getDescription());
        } else if (accessor.getBlockEntity() instanceof GreenhouseBlockEntity own) {
            lines.add(own.getStatus().getDescription());
        }
        return lines.isEmpty() ? null : lines;
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
            GreenhouseProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
