/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.sound.ArcToolSound;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.WrenchItem;
import net.zagdrath.arcforge.network.ToolModuleScrollPayload;

// The Arc Drill and Arc Saw on the client: whether each is running (a player mining with a charged one), which
// plays its loop (ArcToolSound) and switches its held model to the animated one (the arcforge:arc_tool_running
// item model condition); and sneak + scroll to switch modules.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ArcToolClient {
    private static final double HEARING_RANGE = 24.0;
    private static final Map<Integer, ArcToolSound> PLAYING = new HashMap<>();

    private ArcToolClient() {}

    // Mining with a charged Arc tool in the main hand. The local player counts while attacking a block; others
    // while their arm swings (a player breaking a block swings every tick).
    public static boolean isRunning(@Nullable LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof ArcToolItem) || (ArcToolItem.energy(stack) <= 0 && !player.hasInfiniteMaterials())) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player) {
            return minecraft.gameMode != null && (minecraft.gameMode.isDestroying()
                    || (player.isSwinging() && minecraft.options.keyAttack.isDown() && minecraft.hitResult != null
                            && minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK));
        }
        return player.isSwinging();
    }

    // The item model condition: true for the main-hand tool of a running player, so the animated model shows.
    public record RunningProperty() implements ConditionalItemModelProperty {
        public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_tool_running");
        public static final MapCodec<RunningProperty> MAP_CODEC = MapCodec.unit(new RunningProperty());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
            return owner != null && owner.getItemInHand(InteractionHand.MAIN_HAND) == stack && isRunning(owner);
        }

        @Override
        public MapCodec<RunningProperty> type() {
            return MAP_CODEC;
        }
    }

    @SubscribeEvent
    static void registerItemProperties(RegisterConditionalItemModelPropertyEvent event) {
        event.register(RunningProperty.ID, RunningProperty.MAP_CODEC);
    }

    // Starts a loop for each player nearby who starts running a tool (the loop stops itself when they stop).
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer self = minecraft.player;
        if (level == null || self == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.values().removeIf(ArcToolSound::isStopped);
        for (Player player : level.players()) {
            if (player.distanceToSqr(self) > HEARING_RANGE * HEARING_RANGE || PLAYING.containsKey(player.getId()) || !isRunning(player)) {
                continue;
            }
            ArcToolItem tool = (ArcToolItem) player.getMainHandItem().getItem();
            ArcToolSound sound = new ArcToolSound(player, tool.kind());
            PLAYING.put(player.getId(), sound);
            minecraft.getSoundManager().play(sound);
        }
    }

    // Sneak + scroll switches the next module (the Wrench handles its own; they can't both be in hand).
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.gui.screen() != null || player == null || !player.isShiftKeyDown()) {
            return;
        }
        var item = player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
        if (!(item instanceof ArcToolItem) || item instanceof WrenchItem) {
            return;
        }
        int direction = (int) Math.signum(event.getScrollDeltaY());
        if (direction == 0) {
            return;
        }
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(new ToolModuleScrollPayload(direction));
    }
}
