/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.sound.JetpackSound;
import net.zagdrath.arcforge.item.tool.JetpackFlight;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.network.JetpackInputPayload;
import net.zagdrath.arcforge.network.JetpackModePayload;
import net.zagdrath.arcforge.network.JetpackStatePayload;
import net.zagdrath.arcforge.registry.ModFluids;

// Jetpacks on the client: the Jetpack Mode key, sending the jump and sneak keys to the server, predicting the
// local player's flight, the fuel gauge beside the hotbar, and each firing jetpack's exhaust and thrust sound.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class JetpackClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arcforge"));
    public static final KeyMapping MODE_KEY = new KeyMapping("key.arcforge.jetpack_mode", InputConstants.KEY_H, CATEGORY);

    private static final Identifier GAUGE = sprite("gauge");
    private static final Identifier FILL_EMPTY = sprite("fill_empty");
    private static final int GAUGE_W = 8, GAUGE_H = 34, FILL_W = 4, FILL_H = 30, MODE_SIZE = 7;
    // Re-send the keys this often even when they don't change, in case one went missing.
    private static final int RESEND_TICKS = 20;

    // What each firing jetpack is doing, from the server.
    private record State(float output, int exhaust) implements JetpackSound.Output {}

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, JetpackSound> SOUNDS = new HashMap<>();
    private static JetpackInputPayload lastSent = new JetpackInputPayload(false, false);
    private static int sinceSent;

    private JetpackClient() {}

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "hud/jetpack/" + name);
    }

    public static void onState(JetpackStatePayload payload) {
        if (payload.output() <= 0.0F) {
            STATES.remove(payload.entityId());
        } else {
            STATES.put(payload.entityId(), new State(payload.output(), payload.exhaust()));
        }
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(MODE_KEY);
    }

    @SubscribeEvent
    static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack"), JetpackClient::drawHud);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        SOUNDS.clear();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        while (MODE_KEY.consumeClick()) {
            if (!JetpackFlight.worn(player).isEmpty()) {
                ClientPacketDistributor.sendToServer(new JetpackModePayload());
            }
        }
        boolean worn = !JetpackFlight.worn(player).isEmpty();
        JetpackInputPayload input = worn && minecraft.gui.screen() == null
                ? new JetpackInputPayload(minecraft.options.keyJump.isDown(), minecraft.options.keyShift.isDown())
                : new JetpackInputPayload(false, false);
        sinceSent++;
        if (!input.equals(lastSent) || (worn && sinceSent >= RESEND_TICKS)) {
            ClientPacketDistributor.sendToServer(input);
            lastSent = input;
            sinceSent = 0;
        }
        effects(minecraft, level);
    }

    // Predict the local player's flight from their own keys, right after vanilla moves them.
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            JetpackFlight.tick(player, lastSent.jump(), lastSent.sneak(), false);
        }
    }

    // Exhaust at both nozzles, and a thrust sound per firing jetpack.
    private static void effects(Minecraft minecraft, ClientLevel level) {
        SOUNDS.values().removeIf(JetpackSound::isStopped);
        for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            State state = entry.getValue();
            JetpackSound sound = SOUNDS.get(entity.getId());
            if (sound == null || sound.exhaust() != state.exhaust()) {
                int id = entity.getId();
                sound = new JetpackSound(entity, state.exhaust(), new JetpackSound.Output() {
                    @Override
                    public float output() {
                        State now = STATES.get(id);
                        return now != null ? now.output() : 0.0F;
                    }

                    @Override
                    public int exhaust() {
                        State now = STATES.get(id);
                        return now != null ? now.exhaust() : 0;
                    }
                });
                SOUNDS.put(entity.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
            if (minecraft.options.getCameraType().isFirstPerson() && entity == minecraft.player) {
                continue;
            }
            float yaw = living.yBodyRot * Mth.DEG_TO_RAD;
            for (int side : new int[] { -1, 1 }) {
                // Body-relative (±2.5, 11.5, 5) pixels down from the shoulders, turned with the body.
                double localX = side * 2.5 / 16.0;
                double localZ = -5.0 / 16.0;
                double worldX = entity.getX() + localX * Mth.cos(yaw) - localZ * Mth.sin(yaw);
                double worldZ = entity.getZ() + localX * Mth.sin(yaw) + localZ * Mth.cos(yaw);
                double worldY = entity.getY() + (24.0 - 11.5) / 16.0 * (living.isCrouching() ? 0.85 : 1.0);
                Vec3 motion = entity.getDeltaMovement();
                if (state.exhaust() == 2) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, worldX, worldY, worldZ, motion.x, motion.y - 0.15, motion.z);
                    if (level.getRandom().nextFloat() < 0.3F) {
                        level.addParticle(ParticleTypes.SMOKE, worldX, worldY - 0.1, worldZ, motion.x, motion.y - 0.1, motion.z);
                    }
                } else {
                    int count = state.output() > 0.6F ? 2 : 1;
                    for (int i = 0; i < count; i++) {
                        level.addParticle(ParticleTypes.CLOUD, worldX, worldY, worldZ, motion.x * 0.5,
                                motion.y - 0.12 - level.getRandom().nextFloat() * 0.02 * state.output(), motion.z * 0.5);
                    }
                }
            }
        }
    }

    // The fuel gauge left of the hotbar (further left past the offhand slot), with the mode icon above it.
    private static void drawHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        ItemStack stack = JetpackFlight.worn(player);
        if (!(stack.getItem() instanceof JetpackItem jetpack)) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int x = width / 2 - 91 - 12;
        if (!player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT) {
            x -= 29;
        }
        int y = height - 35;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GAUGE, x, y, GAUGE_W, GAUGE_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILL_EMPTY, x + 2, y + 2, FILL_W, FILL_H);
        FluidStack fluid = JetpackItem.fluid(stack);
        int fill = Math.round((float) FILL_H * fluid.getAmount() / jetpack.capacity());
        if (fill > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("fill_" + fillName(fluid.getFluid())), FILL_W, FILL_H, 0, FILL_H - fill,
                    x + 2, y + 2 + FILL_H - fill, FILL_W, fill);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("mode_" + JetpackItem.mode(stack).getSerializedName()),
                x + (GAUGE_W - MODE_SIZE) / 2, y - 9, MODE_SIZE, MODE_SIZE);
    }

    private static String fillName(Fluid fluid) {
        if (fluid == ModFluids.HYDROGEN.get()) {
            return "hydrogen";
        }
        if (fluid == ModFluids.HIGH_PRESSURE_STEAM.get()) {
            return "high_pressure_steam";
        }
        if (fluid == ModFluids.SUPERHEATED_STEAM.get()) {
            return "superheated_steam";
        }
        return "steam";
    }
}
