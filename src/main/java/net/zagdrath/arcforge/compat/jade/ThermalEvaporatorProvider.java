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
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Thermal Evaporator Array in Jade, on its controller, any casing or any of its windows: the temperature against the
// 100°C it needs, how fast it's evaporating, what's in its input, Brine and Water tanks, the Salt waiting, and its status.
// MultiblockNameProvider names it. The server builds the lines; Client draws them.
public enum ThermalEvaporatorProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        ThermalEvaporatorBlockEntity evaporator = accessor.getBlockEntity() instanceof ThermalEvaporatorBlockEntity own ? own
                : ThermalEvaporatorStructure.findController(accessor.getLevel(), accessor.getPosition());
        if (evaporator == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        if (evaporator.isFormed()) {
            int celsius = evaporator.getHeat().getTemperature();
            int needed = ThermalEvaporatorBlockEntity.minTemperature();
            lines.add(Component.translatable("jade.arcforge.thermal_evaporator.temperature", celsius, needed)
                    .withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            lines.add(Component.translatable("jade.arcforge.thermal_evaporator.rate", String.format(Locale.ROOT, "%.1f", evaporator.getRate()),
                    Math.round(ThermalEvaporatorBlockEntity.speedAt(celsius) * 100)));
            addTank(lines, "jade.arcforge.thermal_evaporator.input", evaporator.getInput());
            addTank(lines, "jade.arcforge.thermal_evaporator.output", evaporator.getOutput());
            addTank(lines, "jade.arcforge.thermal_evaporator.water", evaporator.getWater());
            ItemStack salt = evaporator.getSalt();
            if (!salt.isEmpty()) {
                lines.add(Component.translatable("jade.arcforge.thermal_evaporator.salt", salt.getCount(), salt.getHoverName()));
            }
        }
        lines.add(evaporator.getStatus().getDescription());
        return lines;
    }

    private static void addTank(List<Component> lines, String key, FilteredFluidTank tank) {
        if (tank.getAmount() > 0) {
            lines.add(Component.translatable(key, String.format(Locale.ROOT, "%,d", tank.getAmount()),
                    tank.getResource(0).getFluid().getFluidType().getDescription()));
        }
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
            ThermalEvaporatorProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
