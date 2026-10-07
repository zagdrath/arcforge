/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.TurbineLayout;

// Draws a formed Steam Turbine Array as one turbine-generator set along its axis (its blocks draw nothing but their port
// plates): the plinth, the casings, pedestals, crossover and generator from its TurbineLayout, built by TurbineMesh on
// the model sheet block/steam_turbine_array/turbine, with a nozzle from the model out to each port. The still parts are
// built once and again when the ports change (checked every second); the rotor (shaft, couplings and the blades seen
// through each casing's windows) is built each frame at the rotor's angle, so the spin-up and coast-down show on it.
// The rotor turns at up to 36° a tick (see SteamTurbineArrayBlockEntity.advanceAngle).
public class SteamTurbineArrayRenderer implements BlockEntityRenderer<SteamTurbineArrayBlockEntity, SteamTurbineArrayRenderer.State> {
    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/steam_turbine_array/turbine");
    // How often (ticks) the ports are looked at again.
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    // The built model of each turbine, kept until its shape, front or ports change.
    private final Map<SteamTurbineArrayBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final ShellStructure.Shell shell;
        final Direction front;
        final TurbineMesh mesh;
        List<TurbineMesh.Port> ports;
        float[] solid = EMPTY, glass = EMPTY;
        long scanned;

        Built(ShellStructure.Shell shell, Direction front, List<TurbineMesh.Port> ports) {
            this.shell = shell;
            this.front = front;
            this.mesh = new TurbineMesh(TurbineLayout.of(shell, front), shell.axis() == Direction.Axis.X);
            this.ports = ports;
            rebuild();
        }

        void rebuild() {
            float[][] parts = mesh.buildStatic(ports);
            solid = parts[0];
            glass = parts[1];
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public float[] solid = EMPTY, glass = EMPTY, rotor = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public SteamTurbineArrayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SteamTurbineArrayBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(turbine, state, partialTicks, cameraPosition, breakProgress);
        ShellStructure.Shell shell = turbine.getShell();
        Level level = turbine.getLevel();
        state.formed = turbine.isMaster() && shell != null && level != null;
        if (!state.formed || shell == null || level == null) {
            built.remove(turbine);
            return;
        }
        Direction front = turbine.getFacing();
        Built model = built.get(turbine);
        long time = level.getGameTime();
        if (model == null || !model.shell.equals(shell) || model.front != front) {
            model = new Built(shell, front, ports(level, shell));
            model.scanned = time;
            built.put(turbine, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<TurbineMesh.Port> ports = ports(level, shell);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                model.rebuild();
            }
        }
        state.solid = model.solid;
        state.glass = model.glass;
        state.rotor = model.mesh.buildRotor(turbine.advanceAngle(time + partialTicks));
        state.sheet = SteamBoilerArrayRenderer.sprite(SHEET);
        state.light = LightCoordsUtil.getLightCoords(level, shell.centre());
    }

    // The ports on the shell's outer faces, in the turbine's frame.
    private static List<TurbineMesh.Port> ports(Level level, ShellStructure.Shell shell) {
        List<TurbineMesh.Port> ports = new ArrayList<>();
        boolean alongX = shell.axis() == Direction.Axis.X;
        for (BlockPos pos : shell.positions()) {
            if (shell.boundaries(pos) == 0) {
                continue;
            }
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                continue;
            }
            // Only blocks that hold ports (a port left on a casing since replaced by glass doesn't count).
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (faces.get(face) == SideMode.NONE || shell.contains(pos.relative(face))) {
                    continue;
                }
                int nx = alongX ? -face.getStepZ() : face.getStepX();
                int nu = alongX ? face.getStepX() : face.getStepZ();
                ports.add(new TurbineMesh.Port(TurbineLayout.blockU(shell, pos), shell.offset(pos, Direction.Axis.Y), TurbineLayout.blockX(shell, pos),
                        nx, face.getStepY(), nu));
            }
        }
        return ports;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        float[] solid = state.solid, rotor = state.rotor, glass = state.glass;
        int light = state.light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> {
            emit(pose, buffer, solid, sheet, light);
            emit(pose, buffer, rotor, sheet, light);
        });
        if (glass.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), (pose, buffer) -> emit(pose, buffer, glass, sheet, light));
        }
    }

    // TurbineMesh's vertices (x, y, z, u, v across the sheet, normal), the sheet's UVs mapped into the atlas.
    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(-1)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // The whole structure, so it isn't culled when the master corner is off screen.
    @Override
    public AABB getRenderBoundingBox(SteamTurbineArrayBlockEntity turbine) {
        return turbine.getRenderBox();
    }
}
