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
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.LithiumCellBlockEntity;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Battery Array in Jade, on its controller, a casing, a window, a cell or a regulator: the FE it holds against its
// capacity (and how full), the FE that came in and went out last tick, its transfer limit, its cells and regulators, and its
// status. A loose Lithium Cell shows the FE it holds. MultiblockNameProvider names the array (with its size). The server
// builds the lines; Client draws them.
public enum BatteryArrayProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "battery_array");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        BatteryArrayBlockEntity array = accessor.getBlockEntity() instanceof BatteryArrayBlockEntity own ? own
                : BatteryArrayStructure.findController(accessor.getLevel(), accessor.getPosition());
        List<Component> lines = new ArrayList<>();
        if (array == null || !array.isFormed()) {
            if (accessor.getBlockEntity() instanceof LithiumCellBlockEntity cell) {
                lines.add(Component.translatable("jade.arcforge.battery_array.stored", grouped(cell.getStored()), grouped(cell.getCapacity()))
                        .withStyle(ChatFormatting.RED));
                return lines;
            }
            if (array == null) {
                return null;
            }
            lines.add(array.getStatus().getDescription());
            return lines;
        }
        long capacity = array.getCapacity();
        lines.add(Component.translatable("jade.arcforge.battery_array.stored", grouped(array.getStored()), grouped(capacity))
                .withStyle(ChatFormatting.RED));
        if (capacity > 0) {
            lines.add(Component.translatable("jade.arcforge.battery_array.fill",
                    String.format(Locale.ROOT, "%.1f", 100.0 * array.getStored() / capacity)));
        }
        lines.add(Component.translatable("jade.arcforge.battery_array.rates", grouped(array.getLastInput()), grouped(array.getLastOutput()),
                grouped(array.transfer())));
        lines.add(Component.translatable("jade.arcforge.battery_array.parts", array.getCellCount(), array.getRegulatorCount())
                .withStyle(ChatFormatting.GRAY));
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
            BatteryArrayProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
