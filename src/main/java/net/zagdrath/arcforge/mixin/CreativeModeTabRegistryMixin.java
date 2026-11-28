/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.mixin;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.zagdrath.arcforge.registry.ModCreativeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Keeps Arcforge's creative tabs next to one another (see ModCreativeTabs.grouped). Only the computed order is
// regrouped: a pack's creative_mode_tab_ordering.json is left as written.
@Mixin(value = CreativeModeTabRegistry.class, remap = false)
public abstract class CreativeModeTabRegistryMixin {
    @ModifyArg(method = "recalculateItemCreativeModeTabs",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CreativeModeTabRegistry;setCreativeModeTabOrder(Ljava/util/List;)V"))
    private static List<CreativeModeTab> arcforge$groupTabs(List<CreativeModeTab> sorted) {
        return ModCreativeTabs.grouped(sorted, tab -> {
            Identifier name = CreativeModeTabRegistry.getName(tab);
            return name == null ? null : name.getNamespace();
        });
    }
}
