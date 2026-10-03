/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// A Sifter Mesh: goes in the Sifter's mesh slot, which won't run without one. Each tier multiplies every output's
// chance and the speed (machines.sifter.<tier>MeshChance / <tier>MeshSpeed), and wears by a point a block sifted
// (with meshWearChance) until it breaks: Steel 256, Invar 512, Tungsten 1,024 points.
public class SifterMeshItem extends Item {
    public enum Tier {
        STEEL(256), INVAR(512), TUNGSTEN(1_024);

        private final int durability;

        Tier(int durability) {
            this.durability = durability;
        }

        public int durability() {
            return durability;
        }

        public double chanceMultiplier() {
            return switch (this) {
                case STEEL -> ArcforgeConfig.SIFTER_STEEL_CHANCE.getAsDouble();
                case INVAR -> ArcforgeConfig.SIFTER_INVAR_CHANCE.getAsDouble();
                case TUNGSTEN -> ArcforgeConfig.SIFTER_TUNGSTEN_CHANCE.getAsDouble();
            };
        }

        public double speedMultiplier() {
            return switch (this) {
                case STEEL -> ArcforgeConfig.SIFTER_STEEL_SPEED.getAsDouble();
                case INVAR -> ArcforgeConfig.SIFTER_INVAR_SPEED.getAsDouble();
                case TUNGSTEN -> ArcforgeConfig.SIFTER_TUNGSTEN_SPEED.getAsDouble();
            };
        }
    }

    private final Tier tier;

    public SifterMeshItem(Tier tier, Item.Properties properties) {
        super(properties.durability(tier.durability()));
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    // The mesh in a stack, or null if it isn't one.
    public static @Nullable Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof SifterMeshItem mesh ? mesh.tier : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.sifter_mesh", trimmed(tier.chanceMultiplier()), trimmed(tier.speedMultiplier()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.sifter_mesh.wear", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String trimmed(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
