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
import net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Sifter and the Diamond Press in Jade. The Sifter: its mesh and how worn it is, its progress and status. The Diamond
// Press: its temperature against the 1,400°C it needs, its progress and status. The server builds the lines; Client
// draws them.
public enum MineralsProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "minerals");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        if (accessor.getBlockEntity() instanceof SifterBlockEntity sifter) {
            ItemStack mesh = sifter.getItems().getStack(SifterBlockEntity.SLOT_MESH);
            lines.add(mesh.isEmpty() ? Component.translatable("jade.arcforge.sifter.no_mesh").withStyle(ChatFormatting.RED)
                    : Component.translatable("jade.arcforge.sifter.mesh", mesh.getHoverName(), mesh.getMaxDamage() - mesh.getDamageValue(), mesh.getMaxDamage())
                            .withStyle(ChatFormatting.GRAY));
            if (sifter.getTotal() > 0 && sifter.getProgress() > 0) {
                lines.add(Component.translatable("jade.arcforge.sifter.progress", Math.round(100.0 * sifter.getProgress() / sifter.getTotal())));
            }
            lines.add(sifter.getStatus().getDescription());
            return lines;
        }
        if (accessor.getBlockEntity() instanceof DiamondPressBlockEntity press) {
            int celsius = press.getHeat().getTemperature();
            int needed = press.getNeededTemperature();
            lines.add(Component.translatable("jade.arcforge.diamond_press.temperature", String.format(Locale.ROOT, "%,d", celsius),
                    String.format(Locale.ROOT, "%,d", needed)).withStyle(celsius >= needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            if (press.getTotal() > 0 && press.getProgress() > 0) {
                lines.add(Component.translatable("jade.arcforge.diamond_press.progress", Math.round(100.0 * press.getProgress() / press.getTotal())));
            }
            lines.add(press.getStatus().getDescription());
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
            MineralsProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
