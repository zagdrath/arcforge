/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.redstone.ThrottleLeverBlock;

// Client to server: the player scrolled the mouse wheel while looking at a Throttle Lever
// (see ThrottleLeverClient); it moves by direction (+1 up, -1 down).
public record ThrottleLeverPayload(BlockPos pos, int direction) implements CustomPacketPayload {
    public static final Type<ThrottleLeverPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "throttle_lever"));
    public static final StreamCodec<ByteBuf, ThrottleLeverPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ThrottleLeverPayload::pos,
            ByteBufCodecs.VAR_INT, ThrottleLeverPayload::direction,
            ThrottleLeverPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ThrottleLeverPayload payload, IPayloadContext context) {
        apply(context.player(), payload.pos(), payload.direction());
    }

    // Moves the lever at pos by one step, if the player could reach it.
    public static void apply(Player player, BlockPos pos, int direction) {
        if (direction == 0 || !player.isWithinBlockInteractionRange(pos, 1.0) || player.isSpectator()) {
            return;
        }
        BlockState state = player.level().getBlockState(pos);
        if (state.getBlock() instanceof ThrottleLeverBlock) {
            ThrottleLeverBlock.step(player.level(), pos, state, Integer.signum(direction), player);
        }
    }
}
