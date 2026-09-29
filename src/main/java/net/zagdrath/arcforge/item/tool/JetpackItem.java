/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.SteamGrade;

// A Jetpack, worn in the chest slot, in three tiers. It holds one gas at a time (FLUID_CONTENTS), any that is a
// key of the arcforge:jetpack_fuels data map, and flies on it (see JetpackFlight). It's filled in a Pressurized
// Cylinder's slots or by clicking a Gas Cartridge onto it; smithing a Steel Chestplate onto it plates it
// (JETPACK_PLATING, see JetpackPlatingRecipe).
public class JetpackItem extends Item {
    public static final ResourceKey<EquipmentAsset> HARNESS = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_harness"));
    private static final int FALLBACK_COLOR = 0xFFDCEBF2;

    private final ConduitTier tier;

    public JetpackItem(ConduitTier tier, Item.Properties properties) {
        super(properties.stacksTo(1).component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.CHEST).setAsset(HARNESS).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).build()));
        this.tier = tier;
    }

    public ConduitTier tier() {
        return tier;
    }

    // 0 for Tempered, 1 Hardened, 2 Arcforged: the index into the per-tier config lists.
    public static int tierIndex(ConduitTier tier) {
        return Math.max(0, tier.ordinal() - ConduitTier.TEMPERED.ordinal());
    }

    public int capacity() {
        return ArcforgeConfig.perTier(ArcforgeConfig.JETPACK_TANK, tierIndex(tier));
    }

    public double maxRise() {
        return ArcforgeConfig.perTierDouble(ArcforgeConfig.JETPACK_MAX_RISE, tierIndex(tier));
    }

    public double airSpeed() {
        return ArcforgeConfig.perTierDouble(ArcforgeConfig.JETPACK_AIR_SPEED, tierIndex(tier));
    }

    public static FluidStack fluid(ItemStack stack) {
        return PortableStorageItem.fluid(stack);
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            stack.remove(ModDataComponents.FLUID_CONTENTS.get());
        } else {
            stack.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(fluid));
        }
    }

    public static JetpackMode mode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.JETPACK_MODE.get(), JetpackMode.NORMAL);
    }

    public static boolean isPlated(ItemStack stack) {
        return stack.has(ModDataComponents.JETPACK_PLATING.get());
    }

    // Whether it takes this gas: a jetpack fuel, and the one it already holds if it isn't empty.
    public static boolean accepts(ItemStack stack, Fluid fluid) {
        if (JetpackFuel.of(fluid) == null) {
            return false;
        }
        FluidStack held = fluid(stack);
        return held.isEmpty() || held.getFluid() == fluid;
    }

    // A Gas Cartridge clicked onto it pours in as much of an accepted gas as fits.
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
        if (!PortableStorageItem.is(other, PortableStorageItem.Kind.GAS_CARTRIDGE) || !slot.allowModification(player)) {
            return false;
        }
        FluidStack gas = PortableStorageItem.fluid(other);
        if (gas.isEmpty() || !accepts(self, gas.getFluid())) {
            return false;
        }
        FluidStack held = fluid(self);
        int moved = Math.min(gas.getAmount(), capacity() - held.getAmount());
        if (moved <= 0) {
            return false;
        }
        setFluid(self, gas.copyWithAmount(held.getAmount() + moved));
        setFluid(other, gas.copyWithAmount(gas.getAmount() - moved));
        player.playSound(SoundEvents.BUCKET_EMPTY, 0.6F, 1.0F);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !fluid(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Mth.clamp((float) fluid(stack).getAmount() / capacity(), 0.0F, 1.0F) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        FluidStack fluid = fluid(stack);
        SteamGrade grade = fluid.isEmpty() ? null : SteamGrade.of(fluid.getFluid());
        int tint = grade != null ? grade.tint() : fluid.isEmpty() ? -1 : PortableStorageItem.fluidTint.applyAsInt(fluid);
        return tint != -1 ? tint : FALLBACK_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FluidStack fluid = fluid(stack);
        String capacity = String.format("%,d", capacity());
        builder.accept((fluid.isEmpty()
                ? Component.translatable("tooltip.arcforge.jetpack.empty", capacity)
                : Component.translatable("tooltip.arcforge.jetpack.fuel", fluid.getHoverName(), String.format("%,d", fluid.getAmount()), capacity))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("message.arcforge.jetpack.mode", mode(stack).displayName()).withStyle(ChatFormatting.GRAY));
        if (isPlated(stack)) {
            builder.accept(Component.translatable("tooltip.arcforge.jetpack.plated").withStyle(ChatFormatting.GRAY));
        }
    }
}
