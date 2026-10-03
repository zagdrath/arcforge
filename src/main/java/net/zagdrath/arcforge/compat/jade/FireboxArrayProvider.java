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
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Firebox Array in Jade, on its controller, any casing or any of its windows: its temperature and the fuel's, the heat
// it's making against its most, the fuel in its tank, its oxygen, and its status. MultiblockNameProvider names it (with its
// size). The server builds the lines; Client draws them.
public enum FireboxArrayProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "firebox_array");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        FireboxArrayBlockEntity array = accessor.getBlockEntity() instanceof FireboxArrayBlockEntity own ? own
                : FireboxArrayStructure.findController(accessor.getLevel(), accessor.getPosition());
        if (array == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        if (array.isFormed()) {
            int celsius = array.getHeat().getTemperature();
            lines.add(array.getBurnTemperature() > 0
                    ? Component.translatable("jade.arcforge.firebox_array.temperature_fuel", String.format(Locale.ROOT, "%,d", celsius),
                            String.format(Locale.ROOT, "%,d", array.getBurnTemperature())).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("jade.arcforge.firebox_array.temperature", String.format(Locale.ROOT, "%,d", celsius)).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("jade.arcforge.firebox_array.output", String.format(Locale.ROOT, "%,d", array.getHeatPerTick()),
                    String.format(Locale.ROOT, "%,d", array.maxHeatPerTick())));
            if (array.getTank().getAmount() > 0) {
                lines.add(Component.translatable("jade.arcforge.firebox_array.fuel", String.format(Locale.ROOT, "%,d", array.getTank().getAmount()),
                        array.getTank().getResource(0).getFluid().getFluidType().getDescription()));
            }
            if (array.getOxyFuel().getTank().getAmount() > 0) {
                lines.add(Component.translatable("jade.arcforge.firebox_array.oxygen", String.format(Locale.ROOT, "%,d", array.getOxyFuel().getTank().getAmount()))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        lines.add(array.getStatus().getDescription());
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
            FireboxArrayProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
