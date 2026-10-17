/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A Dry Cask: a steel canister in a thick concrete overpack, holding up to SLOTS spent fuel rods once they've cooled
// (a rod's decay heat under nuclear.spentFuel.dryCaskMaxHeat) and shielding them (dryCaskTransmission of their
// radiation gets out), anywhere. Broken, it keeps its rods (minecraft:container), so a full cask can be moved, or
// stored in a Geological Repository.
public class DryCaskBlockEntity extends NuclearBlockEntity implements RadiationSource {
    public static final int SLOTS = 7;

    private final ItemStack[] rods = new ItemStack[SLOTS];

    public DryCaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DRY_CASK.get(), pos, state);
        java.util.Arrays.fill(rods, ItemStack.EMPTY);
    }

    // Whether a rod is cool enough to go in.
    public static boolean accepts(ItemStack stack, long now) {
        return FuelRodItem.isFuelRod(stack) && FuelRod.of(stack).decayHeat(now) < ArcforgeConfig.DRY_CASK_MAX_HEAT.getAsDouble();
    }

    public int count() {
        return nonEmpty(rods).size();
    }

    public boolean insert(ItemStack rod, long now) {
        if (!accepts(rod, now)) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (rods[i].isEmpty()) {
                rods[i] = rod.copyWithCount(1);
                sync();
                return true;
            }
        }
        return false;
    }

    public ItemStack extract() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!rods[i].isEmpty()) {
                ItemStack out = rods[i];
                rods[i] = ItemStack.EMPTY;
                sync();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public List<ItemStack> contents() {
        return nonEmpty(rods);
    }

    @Override
    public double radiationRate() {
        double rate = 0.0;
        for (ItemStack stack : rods) {
            rate += RadiationField.rate(stack);
        }
        return rate * ArcforgeConfig.DRY_CASK_TRANSMISSION.getAsDouble();
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
    }

    // --- Item form: the rods go with it ---

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        List<ItemStack> stacks = new ArrayList<>(List.of(rods));
        ItemContainerContents contents = ItemContainerContents.fromItems(stacks);
        if (!contents.equals(ItemContainerContents.EMPTY)) {
            components.set(DataComponents.CONTAINER, contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ItemContainerContents contents = components.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> stacks = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            contents.copyInto(stacks);
            for (int i = 0; i < SLOTS; i++) {
                rods[i] = stacks.get(i);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("rods");
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        readSlots(in, "rods", rods);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        storeSlots(out, "rods", rods);
    }
}
