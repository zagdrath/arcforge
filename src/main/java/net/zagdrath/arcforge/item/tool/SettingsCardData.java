/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// What a Settings Card holds (data component arcforge:settings_card): the kind of block it was copied from (a block
// id, or a structure's block entity type) and its name, a structure's size in its own frame, the settings the block
// wrote, and the tooltip lines describing them.
public record SettingsCardData(Identifier kind, Component name, Optional<StructureSize> size, CompoundTag settings, List<Component> summary) {
    // A structure's extent across (right), up and along (forward) its own facing, so rotated copies match.
    public record StructureSize(int width, int height, int depth) {
        public static final Codec<StructureSize> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("width").forGetter(StructureSize::width),
                Codec.INT.fieldOf("height").forGetter(StructureSize::height),
                Codec.INT.fieldOf("depth").forGetter(StructureSize::depth))
                .apply(i, StructureSize::new));
        public static final StreamCodec<ByteBuf, StructureSize> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StructureSize::width,
                ByteBufCodecs.VAR_INT, StructureSize::height,
                ByteBufCodecs.VAR_INT, StructureSize::depth,
                StructureSize::new);

        public Component describe() {
            return Component.translatable("tooltip.arcforge.settings_card.size", depth);
        }
    }

    public static final Codec<SettingsCardData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("kind").forGetter(SettingsCardData::kind),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(SettingsCardData::name),
            StructureSize.CODEC.optionalFieldOf("size").forGetter(SettingsCardData::size),
            CompoundTag.CODEC.fieldOf("settings").forGetter(SettingsCardData::settings),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("summary", List.of()).forGetter(SettingsCardData::summary))
            .apply(i, SettingsCardData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsCardData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SettingsCardData::kind,
            ComponentSerialization.STREAM_CODEC, SettingsCardData::name,
            ByteBufCodecs.optional(StructureSize.STREAM_CODEC), SettingsCardData::size,
            ByteBufCodecs.COMPOUND_TAG, SettingsCardData::settings,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), SettingsCardData::summary,
            SettingsCardData::new);
}
