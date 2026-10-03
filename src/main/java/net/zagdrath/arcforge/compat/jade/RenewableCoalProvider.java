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
import net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Renewable coal in Jade. The Tree Cutter: its area, the last tree's logs and the trees felled, and its status. The
// Hydrothermal Carbonizer: its temperature against the 200°C it needs, its progress, its water tanks and its status.
// The server builds the lines; Client draws them.
public enum RenewableCoalProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "renewable_coal");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        if (accessor.getBlockEntity() instanceof TreeCutterBlockEntity cutter) {
            int size = cutter.radius() * 2 + 1;
            lines.add(Component.translatable("jade.arcforge.tree_cutter.area", size, size));
            if (cutter.getLastLogs() > 0) {
                lines.add(Component.translatable("jade.arcforge.tree_cutter.last", cutter.getLastLogs()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(cutter.getStatus().getDescription());
            return lines;
        }
        if (accessor.getBlockEntity() instanceof HydrothermalCarbonizerBlockEntity carbonizer) {
            int celsius = carbonizer.getHeat().getTemperature();
            int needed = HydrothermalCarbonizerBlockEntity.minTemperature();
            lines.add(Component.translatable("jade.arcforge.hydrothermal_carbonizer.temperature", celsius, needed)
                    .withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            if (carbonizer.getTotal() > 0) {
                lines.add(Component.translatable("jade.arcforge.hydrothermal_carbonizer.progress",
                        Math.round(100.0 * carbonizer.getProgress() / carbonizer.getTotal())));
            }
            lines.add(Component.translatable("jade.arcforge.hydrothermal_carbonizer.water", grouped(carbonizer.getWater().getAmount()),
                    grouped(carbonizer.getReturned().getAmount())).withStyle(ChatFormatting.AQUA));
            lines.add(carbonizer.getStatus().getDescription());
            return lines;
        }
        return null;
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
            RenewableCoalProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
