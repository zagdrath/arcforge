/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.TurbineHitbox;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.TurbineShapes;

// Clicks on the parts of a Steam Turbine Array's model deep in its hollow core, which no block's shape can reach (see
// TurbineShapes): when the player uses, attacks or picks, the sight line is also tested against the whole model, and if
// it meets the model before whatever vanilla found, the click goes to the array's last shell block along the sight line
// on the way in (so its GUI opens, as clicking any of its blocks does), at a point inside that block.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class TurbinePick {
    private static final double PX = 16.0;
    // How far apart the blocks along the sight line are looked at (blocks).
    private static final double WALK = 0.05;
    // How much nearer the model must be than vanilla's hit to take the click (blocks).
    private static final double MARGIN = 0.05;

    private TurbinePick() {}

    @SubscribeEvent
    static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() || event.isAttack() || event.isPickBlock()) {
            refine(Minecraft.getInstance());
        }
    }

    private static void refine(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        HitResult hit = minecraft.hitResult;
        if (player == null || level == null || camera == null || hit != null && hit.getType() == HitResult.Type.ENTITY) {
            return;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        double reach = hit != null && hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : player.blockInteractionRange();
        // The first Steam Turbine Array along the sight line.
        SteamTurbineArrayBlockEntity master = null;
        for (double d = 0.0; d <= reach && master == null; d += WALK) {
            master = masterAt(level, BlockPos.containing(eye.add(look.scale(d))));
        }
        ShellStructure.Shell shell = master != null ? master.getShell() : null;
        if (master == null || shell == null) {
            return;
        }
        double at = clip(TurbineHitbox.model(shell, master), shell, eye, look, reach - MARGIN);
        if (at < 0.0) {
            return;
        }
        Vec3 point = eye.add(look.scale(at));
        // The last of the array's shell blocks the sight line passes through on its way to the model.
        BlockPos block = null;
        for (double d = 0.0; d <= at; d += WALK) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (shell.contains(pos) && !shell.isCore(pos)) {
                block = pos;
            }
        }
        if (block == null) {
            return;
        }
        Vec3 inside = new Vec3(Math.clamp(point.x, block.getX(), block.getX() + 1.0), Math.clamp(point.y, block.getY(), block.getY() + 1.0),
                Math.clamp(point.z, block.getZ(), block.getZ() + 1.0));
        Direction face = Direction.getApproximateNearest(-look.x, -look.y, -look.z);
        minecraft.hitResult = new BlockHitResult(inside, face, block, false);
    }

    // The formed Steam Turbine Array the block at pos is a casing or window of, by its master.
    private static @Nullable SteamTurbineArrayBlockEntity masterAt(ClientLevel level, BlockPos pos) {
        ShellMultiblockBlockEntity found = level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part
                ? part.getShell() != null && level.getBlockEntity(part.getShell().min()) instanceof ShellMultiblockBlockEntity owner ? owner : null
                : PressureGlassBlock.findMaster(level, pos);
        return found instanceof SteamTurbineArrayBlockEntity turbine && turbine.isMaster() ? turbine : null;
    }

    // How far (blocks) along the sight line the model is, within max; -1 when it isn't. The ray in the turbine's frame:
    // along Z, x is world X and u world Z; along X, u is world X and x runs toward world -Z.
    private static double clip(TurbineShapes model, ShellStructure.Shell shell, Vec3 eye, Vec3 look, double max) {
        BlockPos min = shell.min();
        double ex = eye.x - min.getX(), ey = eye.y - min.getY(), ez = eye.z - min.getZ();
        boolean alongX = shell.axis() == Direction.Axis.X;
        double[] from = alongX
                ? new double[] { (shell.size(Direction.Axis.Z) - ez) * PX, ey * PX, ex * PX }
                : new double[] { ex * PX, ey * PX, ez * PX };
        double[] dir = alongX ? new double[] { -look.z, look.y, look.x } : new double[] { look.x, look.y, look.z };
        double t = model.clip(from, dir, max * PX);
        return t < 0.0 ? -1.0 : t / PX;
    }
}
