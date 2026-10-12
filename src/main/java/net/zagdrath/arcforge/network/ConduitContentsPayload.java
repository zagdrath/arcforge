/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.item.ItemPacket;

// Server to client: what a fluid, pressurized or item conduit holds, for its renderer (see
// ConduitBlockEntity.markContentsChanged). Lighter than a block entity update: no settings, filters or facades, and no
// block update. Packets carry only what's drawn (no destination).
public record ConduitContentsPayload(BlockPos pos, FluidStack fluid, Fluid flowingGas, List<ItemPacket> packets,
        List<ItemStack> storedItems, float itemSpeed) implements CustomPacketPayload {
    public static final Type<ConduitContentsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_contents"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ItemPacket> PACKET_STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, packet -> packet.stack,
            Direction.STREAM_CODEC, packet -> packet.from,
            Direction.STREAM_CODEC, packet -> packet.to,
            ByteBufCodecs.FLOAT, packet -> packet.progress,
            (stack, from, to, progress) -> new ItemPacket(stack, from, to, progress, null, null));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConduitContentsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConduitContentsPayload::pos,
            FluidStack.OPTIONAL_STREAM_CODEC, ConduitContentsPayload::fluid,
            ByteBufCodecs.registry(Registries.FLUID), ConduitContentsPayload::flowingGas,
            PACKET_STREAM_CODEC.apply(ByteBufCodecs.list()), ConduitContentsPayload::packets,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), ConduitContentsPayload::storedItems,
            ByteBufCodecs.FLOAT, ConduitContentsPayload::itemSpeed,
            ConduitContentsPayload::new);

    // A snapshot of the conduit's contents. Copied: in single player the payload reaches the client without being
    // encoded, while the server goes on changing the originals.
    public static ConduitContentsPayload of(ConduitBlockEntity conduit) {
        return new ConduitContentsPayload(conduit.getBlockPos(), conduit.getFluid().copy(), conduit.getFlowingGas(),
                conduit.getPackets().stream().map(packet -> new ItemPacket(packet.stack.copy(), packet.from, packet.to, packet.progress, null, null)).toList(),
                conduit.getStoredItems().stream().map(ItemStack::copy).toList(),
                conduit.getItemSpeed());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ConduitContentsPayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.pos()) instanceof ConduitBlockEntity conduit) {
            // Fresh copies: the client moves its packets along between updates.
            conduit.applyContents(payload.fluid().copy(), payload.flowingGas(),
                    payload.packets().stream().map(packet -> new ItemPacket(packet.stack.copy(), packet.from, packet.to, packet.progress, null, null)).toList(),
                    payload.storedItems().stream().map(ItemStack::copy).toList(),
                    payload.itemSpeed());
        }
    }
}
