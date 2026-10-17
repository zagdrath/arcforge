/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.PurexMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.multiblock.VesselStructures;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.ReprocessingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The PUREX Plant (see VesselStructures.PUREX): a shielded hot cell where spent fuel is reprocessed
// (arcforge:reprocessing recipes). A rod from its input slots is chopped and dissolved in Nitric Acid, and a solvent
// (Kerosene carrying tributyl phosphate) pulls the uranium and plutonium out of the acid and apart:
//  - PUREX (uranium and MOX rods): Recovered Uranium (the rod's uranium left, its U-235 assay what's left of the rod's
//    enrichment; back to UF6 and the Gas Centrifuge Cascade), Plutonium Oxide (only ever made into MOX fuel) and
//    High-Level Liquid Waste (the fission products, more the more spent the rod);
//  - THOREX (thorium blanket and U-233 rods): Thorium Dioxide back, the bred U-233 as U-233 Oxide, and the waste.
// The fissile oxide comes out as whole items, the fractions carried over to the next rod. Rods must have cooled (decay
// heat under nuclear.spentFuel.dryCaskMaxHeat) before they can be dissolved. It takes nuclear.reprocessing.purexEnergy
// FE a tick. Acid and solvent come in through Input ports, the waste goes out through Output ports (pushed), and the
// products out of its output slots (Output ports give them). Its rods and waste are radiation sources, mostly held in
// by its walls (SHIELDING gets out).
public class PurexBlockEntity extends VesselControllerBlockEntity implements RadiationSource {
    public static final int INPUT_SLOTS = 4, OUTPUT_SLOTS = 6, MACHINE_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final double SHIELDING = 0.02;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.OUTPUT);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank acid;
    private final FilteredFluidTank solvent;
    private final FilteredFluidTank waste;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<FluidResource> wasteOutput;
    private final ResourceHandler<FluidResource> allFluids;
    private final ResourceHandler<ItemResource> rodInput;
    private final ResourceHandler<ItemResource> productOutput;
    private final ContainerData data;
    private ReprocessingRecipe.Mode mode = ReprocessingRecipe.Mode.PUREX;
    // Fissile oxide owed (fractions of an item) for each mode.
    private double plutoniumOwed, uranium233Owed;
    private int progress, total;
    private int usage;
    public static Consumer<PurexBlockEntity> clientHook = plant -> {};

    public PurexBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PUREX_PLANT.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> slot < INPUT_SLOTS && FuelRodItem.isFuelRod(resource.toStack(1)),
                SIDE_MODES);
        int fe = Math.max(1, ArcforgeConfig.PUREX_ENERGY.getAsInt());
        this.energy = new ConsumerEnergyHandler(fe * 1_000, fe * 10, this::setChanged);
        this.acid = new FilteredFluidTank(16_000, resource -> resource.is(ModFluids.NITRIC_ACID.get()), this::setChanged);
        this.solvent = new FilteredFluidTank(8_000, resource -> MachineRecipes.isReprocessingFluid(level, resource) && !resource.is(ModFluids.NITRIC_ACID.get()),
                this::setChanged);
        this.waste = new FilteredFluidTank(16_000, resource -> resource.is(ModFluids.HIGH_LEVEL_WASTE.get()), this::setChanged);
        this.fluidInput = new CombinedResourceHandler<>(new SidedResourceHandler<>(acid, true, false, Integer.MAX_VALUE),
                new SidedResourceHandler<>(solvent, true, false, Integer.MAX_VALUE));
        this.wasteOutput = new SidedResourceHandler<>(waste, false, true, Integer.MAX_VALUE);
        this.allFluids = new CombinedResourceHandler<>(fluidInput, wasteOutput);
        this.rodInput = new AutomationResourceHandler<>(items, slot -> slot < INPUT_SLOTS, slot -> false);
        this.productOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot >= INPUT_SLOTS && slot < MACHINE_SLOTS);
        this.data = new WideIntContainerData(PurexMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case PurexMenu.DATA_STATUS -> status.ordinal();
                    case PurexMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case PurexMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case PurexMenu.DATA_SIDE_CONFIG -> packedSides();
                    case PurexMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case PurexMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case PurexMenu.DATA_ACID -> acid.getAmount();
                    case PurexMenu.DATA_ACID_CAPACITY -> acid.getCapacity();
                    case PurexMenu.DATA_SOLVENT -> solvent.getAmount();
                    case PurexMenu.DATA_SOLVENT_FLUID -> solvent.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(solvent.getResource(0).getFluid()) : -1;
                    case PurexMenu.DATA_SOLVENT_CAPACITY -> solvent.getCapacity();
                    case PurexMenu.DATA_WASTE -> waste.getAmount();
                    case PurexMenu.DATA_WASTE_CAPACITY -> waste.getCapacity();
                    case PurexMenu.DATA_PROGRESS -> progress;
                    case PurexMenu.DATA_TOTAL -> total;
                    case PurexMenu.DATA_MODE -> mode.ordinal();
                    case PurexMenu.DATA_OWED -> (int) Math.round((mode == ReprocessingRecipe.Mode.PUREX ? plutoniumOwed : uranium233Owed) * 100.0);
                    case PurexMenu.DATA_USAGE -> usage;
                    default -> 0;
                };
            }
        };
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> slot < INPUT_SLOTS, Set.of(), () -> {});
    }

    @Override
    public VesselStructure structure() {
        return VesselStructures.PUREX;
    }

    public ReprocessingRecipe.Mode getMode() {
        return mode;
    }

    public void toggleMode() {
        mode = mode == ReprocessingRecipe.Mode.PUREX ? ReprocessingRecipe.Mode.THOREX : ReprocessingRecipe.Mode.PUREX;
        progress = 0;
        setChanged();
        sync();
    }

    public void setMode(ReprocessingRecipe.Mode mode) {
        if (this.mode != mode) {
            toggleMode();
        }
    }

    public int getProgress() {
        return progress;
    }

    public int getUsage() {
        return usage;
    }

    public int getTotal() {
        return total;
    }

    @Override
    protected void work(ServerLevel level) {
        usage = 0;
        if (!controlState.isEnabled() || !redstoneMode.canRun(redstone > 0)) {
            status = stoppedStatus();
        } else {
            status = reprocess(level);
        }
        setLit(status == MachineStatus.REPROCESSING);
        MultiblockAutomation.pushFluids(level, this, PUSHED, 16_000);
        MultiblockAutomation.pushOutputs(level, this);
    }

    private int nextRodSlot() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (!items.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private MachineStatus reprocess(ServerLevel level) {
        int slot = nextRodSlot();
        if (slot < 0) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        ItemStack rod = items.getStack(slot);
        RecipeHolder<ReprocessingRecipe> holder = MachineRecipes.reprocessing(level, rod, mode).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return MachineRecipes.reprocessing(level, rod, null).isPresent() ? MachineStatus.WRONG_MODE : MachineStatus.IDLE;
        }
        ReprocessingRecipe recipe = holder.value();
        total = recipe.time();
        FuelRod fuel = FuelRod.of(rod);
        if (fuel.decayHeat(level.getGameTime()) >= ArcforgeConfig.DRY_CASK_MAX_HEAT.getAsDouble()) {
            return MachineStatus.ROD_TOO_HOT;
        }
        if (!recipe.acid().test(acid.getResource(0), acid.getAmount())) {
            return MachineStatus.NO_ACID;
        }
        if (!recipe.solvent().test(solvent.getResource(0), solvent.getAmount())) {
            return MachineStatus.NO_SOLVENT;
        }
        List<ItemStack> products = products(recipe, fuel, false);
        int wasteMade = wasteFor(recipe, fuel);
        if (!fitsOutputs(products) || waste.getSpace() < wasteMade) {
            return MachineStatus.OUTPUT_FULL;
        }
        int fe = ArcforgeConfig.PUREX_ENERGY.getAsInt();
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            products = products(recipe, fuel, true);
            take(acid, recipe.acid().amount());
            take(solvent, recipe.solvent().amount());
            if (wasteMade > 0) {
                waste.set(0, FluidResource.of(recipe.waste().fluid().value()), waste.getAmount() + wasteMade);
            }
            items.setStack(slot, rod.copyWithCount(rod.getCount() - 1));
            for (ItemStack product : products) {
                addOutput(product);
            }
            controlState.completed(new ArrayList<>(products), List.of(new FluidStack(recipe.waste().fluid().value(), wasteMade)), 1,
                    recipe.acid().amount() + recipe.solvent().amount());
            ArcforgeAdvancements.produced(this, products.isEmpty() ? ItemStack.EMPTY : products.get(products.size() - 1), null,
                    mode == ReprocessingRecipe.Mode.PUREX ? "purex" : "thorex");
            progress = 0;
        }
        setChanged();
        return MachineStatus.REPROCESSING;
    }

    // What a rod gives: the heavy metal (Recovered Uranium at the rod's remaining assay; or Thorium Dioxide) and the
    // whole fissile oxide items owed (taking them off what's owed when commit).
    private List<ItemStack> products(ReprocessingRecipe recipe, FuelRod fuel, boolean commit) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack heavy = recipe.heavyMetal().create();
        if (mode == ReprocessingRecipe.Mode.PUREX) {
            // A uranium rod's uranium keeps what's left of its U-235; a MOX rod's was depleted uranium to begin with.
            int pcm = fuel.type() == FuelRod.FuelType.MOX ? fuel.enrichment() : fuel.remainingUranium();
            heavy.set(ModDataComponents.U235_ASSAY.get(), Math.max(1, Math.min(net.zagdrath.arcforge.nuclear.Enrichment.MAX_PCM, pcm)));
        }
        out.add(heavy);
        double owed = (mode == ReprocessingRecipe.Mode.PUREX ? plutoniumOwed : uranium233Owed) + fuel.plutonium() * recipe.perFraction();
        int whole = (int) Math.floor(owed);
        if (whole > 0) {
            ItemStack fissile = recipe.fissile().create();
            out.add(fissile.copyWithCount(Math.min(fissile.getMaxStackSize(), whole * fissile.getCount())));
        }
        if (commit) {
            if (mode == ReprocessingRecipe.Mode.PUREX) {
                plutoniumOwed = owed - whole;
            } else {
                uranium233Owed = owed - whole;
            }
        }
        return out;
    }

    // The waste a rod leaves: the recipe's amount for a fully spent rod, less for a fresher one (but never none).
    private static int wasteFor(ReprocessingRecipe recipe, FuelRod fuel) {
        return (int) Math.round(recipe.waste().amount() * (0.2 + 0.8 * Math.min(1.0, fuel.fissionProducts())));
    }

    private boolean fitsOutputs(List<ItemStack> products) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            slots.add(items.getStack(slot).copy());
        }
        for (ItemStack product : products) {
            boolean placed = false;
            for (int i = 0; i < slots.size() && !placed; i++) {
                ItemStack in = slots.get(i);
                if (in.isEmpty()) {
                    slots.set(i, product.copy());
                    placed = true;
                } else if (ItemStack.isSameItemSameComponents(in, product) && in.getCount() + product.getCount() <= in.getMaxStackSize()) {
                    in.grow(product.getCount());
                    placed = true;
                }
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private void addOutput(ItemStack product) {
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            ItemStack in = items.getStack(slot);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, product) && in.getCount() + product.getCount() <= in.getMaxStackSize()) {
                items.setStack(slot, in.copyWithCount(in.getCount() + product.getCount()));
                return;
            }
        }
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            if (items.getStack(slot).isEmpty()) {
                items.setStack(slot, product);
                return;
            }
        }
    }

    private static void take(FilteredFluidTank tank, int amount) {
        int left = tank.getAmount() - amount;
        tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
    }

    public FilteredFluidTank getAcid() {
        return acid;
    }

    public FilteredFluidTank getSolvent() {
        return solvent;
    }

    public FilteredFluidTank getWaste() {
        return waste;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // --- Radiation: the rods and the waste, behind the hot cell's walls ---

    @Override
    public double radiationRate() {
        double rate = 0.0;
        for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
            rate += RadiationField.rate(items.getStack(slot));
        }
        if (waste.getAmount() > 0) {
            rate += RadiationField.rate(waste.getResource(0).toStack(waste.getAmount()));
        }
        return rate * SHIELDING;
    }

    @Override
    public Vec3 radiationCentre() {
        return box != null ? box.aabb().getCenter() : Vec3.atCenterOf(worldPosition);
    }

    // --- Capabilities ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return new CombinedResourceHandler<>(rodInput, productOutput);
        }
        return switch (mode) {
            case INPUT -> rodInput;
            case OUTPUT -> productOutput;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return allFluids;
        }
        return switch (mode) {
            case INPUT -> fluidInput;
            case OUTPUT -> wasteOutput;
            default -> null;
        };
    }

    // FE in through Energy ports.
    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (box == null) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    public void clientTick() {
        clientHook.accept(this);
    }

    // --- GUI and saving ---

    @Override
    public Component getDisplayName() {
        return box != null ? Component.translatable("container.arcforge.purex_plant.sized", box.sizeText())
                : Component.translatable("container.arcforge.purex_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PurexMenu(containerId, inventory, worldPosition, items, data);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        acid.deserialize(in.childOrEmpty("acid"));
        solvent.deserialize(in.childOrEmpty("solvent"));
        waste.deserialize(in.childOrEmpty("waste"));
        mode = in.getIntOr("mode", 0) == 1 ? ReprocessingRecipe.Mode.THOREX : ReprocessingRecipe.Mode.PUREX;
        plutoniumOwed = in.getDoubleOr("plutonium_owed", 0.0);
        uranium233Owed = in.getDoubleOr("uranium_233_owed", 0.0);
        progress = in.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
        acid.serialize(out.child("acid"));
        solvent.serialize(out.child("solvent"));
        waste.serialize(out.child("waste"));
        out.putInt("mode", mode.ordinal());
        out.putDouble("plutonium_owed", plutoniumOwed);
        out.putDouble("uranium_233_owed", uranium233Owed);
        out.putInt("progress", progress);
    }

    public static boolean isSolvent(@Nullable Level level, FluidResource resource) {
        return MachineRecipes.isReprocessingFluid(level, resource) && !resource.is(ModFluids.NITRIC_ACID.get());
    }
}
