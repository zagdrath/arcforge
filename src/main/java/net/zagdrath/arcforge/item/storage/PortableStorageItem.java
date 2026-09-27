/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.SteamGrade;

// Batteries (FE), Canisters (liquids), Gas Cartridges (gases) and Thermal Capsules (heat), in four tiers.
// Contents live in the same data components the storage blocks use as items (ENERGY, FLUID_CONTENTS,
// HEAT), so upgrading one keeps what it holds. They are filled and emptied in the item slots of the
// Energy Cell, Fluid Tank, Pressurized Cylinder and Heat Cell. Energy and fluids are exposed through the
// standard item capabilities (see ModCapabilities); heat, which has none, through the helpers here.
// A Thermal Capsule leaks like the Heat Cell of its tier while it's carried.
public class PortableStorageItem extends Item {
    public enum Kind {
        BATTERY, CANISTER, GAS_CARTRIDGE, THERMAL_CAPSULE
    }

    private static final int BATTERY_COLOR = 0xFFE5483C;
    private static final int CANISTER_FALLBACK = 0xFF3F76E4;
    private static final int GAS_FALLBACK = 0xFFDCEBF2;
    private static final int LEAK_INTERVAL = 20;

    // The client sets this to read a fluid's tint from its model, for the fill bar.
    public static ToIntFunction<FluidStack> fluidTint = stack -> -1;

    private final Kind kind;
    private final ConduitTier tier;

    public PortableStorageItem(Kind kind, ConduitTier tier, Item.Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public Kind kind() {
        return kind;
    }

    public ConduitTier tier() {
        return tier;
    }

    public static boolean is(ItemStack stack, Kind kind) {
        return stack.getItem() instanceof PortableStorageItem item && item.kind == kind;
    }

    public int capacity() {
        return switch (kind) {
            case BATTERY -> tier.batteryCapacity();
            case CANISTER -> tier.canisterCapacity();
            case GAS_CARTRIDGE -> tier.gasCartridgeCapacity();
            case THERMAL_CAPSULE -> tier.thermalCapsuleCapacity();
        };
    }

    // Most it takes in or gives out per tick.
    public int rate() {
        return switch (kind) {
            case BATTERY -> tier.batteryRate();
            case CANISTER -> tier.canisterRate();
            case GAS_CARTRIDGE -> tier.gasCartridgeRate();
            case THERMAL_CAPSULE -> tier.thermalCapsuleRate();
        };
    }

    // How much it holds, in FE, mB or HU.
    public int amount(ItemStack stack) {
        return switch (kind) {
            case BATTERY -> stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
            case CANISTER, GAS_CARTRIDGE -> fluid(stack).getAmount();
            case THERMAL_CAPSULE -> stack.getOrDefault(ModDataComponents.HEAT.get(), 0);
        };
    }

    public static FluidStack fluid(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
    }

    // A full one of this item, for the creative tab (fluid items take the fluid to fill with).
    public ItemStack filled(@Nullable FluidStack fluid) {
        ItemStack stack = new ItemStack(this);
        switch (kind) {
            case BATTERY -> stack.set(ModDataComponents.ENERGY.get(), capacity());
            case THERMAL_CAPSULE -> stack.set(ModDataComponents.HEAT.get(), capacity());
            case CANISTER, GAS_CARTRIDGE -> {
                if (fluid != null && !fluid.isEmpty()) {
                    stack.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(fluid.copyWithAmount(capacity())));
                }
            }
        }
        return stack;
    }

    // --- Heat (Thermal Capsules) ---

    public int temperature(ItemStack stack) {
        return temperatureAt(amount(stack));
    }

    // The temperature it would be at holding this much heat: 20°C empty up to its tier's maximum full.
    public int temperatureAt(int heat) {
        return HeatBuffer.temperature(heat, capacity(), tier.thermalCapsuleMaxTemperature());
    }

    public static void setHeat(ItemStack stack, int heat) {
        DataComponentType<Integer> component = ModDataComponents.HEAT.get();
        if (heat > 0) {
            stack.set(component, heat);
        } else {
            stack.remove(component);
        }
    }

    // How much of up to `max` HU can move from a source to a target before the source would be the
    // colder of the two: heat only flows from hot to cold. The arguments give each side's temperature
    // after `amount` has moved.
    public static int heatFlow(int max, IntUnaryOperator sourceAfter, IntUnaryOperator targetAfter) {
        if (max <= 0 || sourceAfter.applyAsInt(0) <= targetAfter.applyAsInt(0)) {
            return 0;
        }
        int low = 0, high = max;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (sourceAfter.applyAsInt(mid) >= targetAfter.applyAsInt(mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    // Carried capsules leak like the Heat Cell of their tier, once a second.
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (kind != Kind.THERMAL_CAPSULE || level.getGameTime() % LEAK_INTERVAL != 0) {
            return;
        }
        int heat = amount(stack);
        if (heat <= 0) {
            return;
        }
        double perSecond = heat * tier.heatCellLeakPercentPerMinute() / 100.0 / 60.0;
        int lost = (int) perSecond;
        // The fraction is lost now and then, so small amounts still drain away.
        if (level.getRandom().nextDouble() < perSecond - lost) {
            lost++;
        }
        if (lost > 0) {
            setHeat(stack, heat - lost);
        }
    }

    // --- Display ---

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return amount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Mth.clamp((float) amount(stack) / capacity(), 0.0F, 1.0F) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return switch (kind) {
            case BATTERY -> BATTERY_COLOR;
            case THERMAL_CAPSULE -> HeatScale.color(temperature(stack));
            case CANISTER, GAS_CARTRIDGE -> {
                FluidStack fluid = fluid(stack);
                SteamGrade grade = fluid.isEmpty() ? null : SteamGrade.of(fluid.getFluid());
                int tint = grade != null ? grade.tint() : fluid.isEmpty() ? -1 : fluidTint.applyAsInt(fluid);
                yield tint != -1 ? tint : kind == Kind.CANISTER ? CANISTER_FALLBACK : GAS_FALLBACK;
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String amount = String.format("%,d", amount(stack));
        String capacity = String.format("%,d", capacity());
        switch (kind) {
            case BATTERY -> builder.accept(Component.translatable("gui.arcforge.fe_stored", amount, capacity).withStyle(ChatFormatting.GRAY));
            case CANISTER, GAS_CARTRIDGE -> {
                FluidStack fluid = fluid(stack);
                builder.accept(fluid.isEmpty()
                        ? Component.translatable("gui.arcforge.empty").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("tooltip.arcforge.portable.fluid", fluid.getHoverName(), amount, capacity).withStyle(ChatFormatting.GRAY));
            }
            case THERMAL_CAPSULE -> {
                builder.accept(Component.translatable("gui.arcforge.hu_stored", amount, capacity).withStyle(ChatFormatting.GRAY));
                builder.accept(Component.translatable("gui.arcforge.celsius", temperature(stack)).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
