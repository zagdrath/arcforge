/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.machine.quarry.BlockFilter;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.menu.machine.ArcQuarryConfigMenu;

// Client to server: a block tag typed into the Arc Quarry settings screen. It fills the first empty filter cell, if
// the tag exists and isn't there already.
public record ArcQuarryTagPayload(int containerId, String tag) implements CustomPacketPayload {
    public static final Type<ArcQuarryTagPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry_tag"));
    public static final StreamCodec<ByteBuf, ArcQuarryTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArcQuarryTagPayload::containerId,
            ByteBufCodecs.stringUtf8(256), ArcQuarryTagPayload::tag,
            ArcQuarryTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ArcQuarryTagPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    // Adds the tag if the player still has that quarry's settings open and it's a real block tag. Returns whether it did.
    public static boolean apply(Player player, ArcQuarryTagPayload payload) {
        Identifier id = Identifier.tryParse(payload.tag().startsWith("#") ? payload.tag().substring(1) : payload.tag());
        if (id == null || !BlockFilter.tagExists(id)
                || !(player.containerMenu instanceof ArcQuarryConfigMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) {
            return false;
        }
        ArcQuarryBlockEntity quarry = menu.quarry(player);
        if (quarry == null) {
            return false;
        }
        QuarrySettings settings = quarry.getSettings();
        if (settings.filter().stream().anyMatch(entry -> entry.item().isEmpty() && entry.tag().filter(id::equals).isPresent())) {
            return false;
        }
        for (int cell = 0; cell < QuarrySettings.FILTER_SIZE; cell++) {
            if (!BlockFilter.isSet(settings.entry(cell))) {
                quarry.setSettings(settings.withEntry(cell, BlockFilter.ofTag(id)));
                return true;
            }
        }
        return false;
    }
}
