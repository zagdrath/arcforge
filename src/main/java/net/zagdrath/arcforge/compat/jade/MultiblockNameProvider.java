/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

// Looking at any block of a formed multiblock (a casing, a brick, the port, a pane of Pressure Glass),
// Jade names the machine it belongs to, e.g. "Steam Boiler Array 3×3×7", instead of the block.
public enum MultiblockNameProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "multiblock_name");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Component name = structureName(accessor.getLevel(), accessor.getPosition(), accessor.getBlock());
        if (name != null) {
            tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(name));
        }
    }

    // The name of the formed machine the block at pos is part of, or null.
    static @Nullable Component structureName(Level level, BlockPos pos, Block block) {
        MultiblockController controller = null;
        if (block instanceof MultiblockPart part) {
            controller = part.findController(level, pos);
        } else if (block instanceof PressureGlassBlock) {
            controller = WindowProviders.master(level, pos);
            if (controller == null) {
                // A pane in a Greenhouse Array's wall or roof.
                controller = GreenhouseStructure.findController(level, pos);
            }
            if (controller == null) {
                // A window up a Thermal Evaporator Array.
                controller = ThermalEvaporatorStructure.findController(level, pos);
            }
            if (controller == null) {
                // A window in a Firebox Array.
                controller = net.zagdrath.arcforge.multiblock.FireboxArrayStructure.findController(level, pos);
            }
        }
        return controller != null && controller.isFormed() && controller instanceof MenuProvider menu ? menu.getDisplayName() : null;
    }

    // Right after Jade's own name, so there is one to replace.
    @Override
    public int getDefaultPriority() {
        return TooltipPosition.HEAD - 99;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
