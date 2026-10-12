/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;
import net.zagdrath.arcforge.api.machine.settings.MachineSide;
import net.zagdrath.arcforge.api.machine.settings.SettingResult;
import net.zagdrath.arcforge.api.machine.settings.SideModes;
import net.zagdrath.arcforge.block.logistics.LinkConduitBlock;
import net.zagdrath.arcforge.block.logistics.LinkConduitBlock.Joint;
import net.zagdrath.arcforge.block.logistics.WiredMachineLinkBlock;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.worldgen.ModWorldgen;

// Iolite (the End's ore), the Link Conduit and the Wired Machine Link.
public final class MachineLinkGameTests {
    private MachineLinkGameTests() {}

    // --- Helpers ---

    // Lays Link Conduit along path (each step one block), plugged into whatever is beyond its first and last blocks.
    static void run(GameTestHelper helper, List<BlockPos> path, Direction startPlug, Direction endPlug) {
        for (int i = 0; i < path.size(); i++) {
            BlockState state = ModBlocks.LINK_CONDUIT.get().defaultBlockState();
            Direction back = i == 0 ? startPlug : direction(path.get(i), path.get(i - 1));
            Direction ahead = i == path.size() - 1 ? endPlug : direction(path.get(i), path.get(i + 1));
            state = state.setValue(LinkConduitBlock.joint(back), i == 0 ? Joint.PLUG : Joint.CABLE)
                    .setValue(LinkConduitBlock.joint(ahead), i == path.size() - 1 ? Joint.PLUG : Joint.CABLE);
            helper.setBlock(path.get(i), state);
        }
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.getApproximateNearest(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    // A straight run along +x from `from` to `to` (inclusive), plugged in at both ends.
    static void straight(GameTestHelper helper, BlockPos from, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        for (int x = from.getX(); x <= to.getX(); x++) {
            path.add(new BlockPos(x, from.getY(), from.getZ()));
        }
        run(helper, path, Direction.WEST, Direction.EAST);
    }

    private static WiredMachineLinkBlockEntity link(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, WiredMachineLinkBlockEntity.class);
    }

    // An Arc Crusher facing north at (0,1,0) (its LEFT face is east), Link Conduit from (1,1,0) to (3,1,0), and a Wired
    // Machine Link at (4,1,0).
    private static final BlockPos CRUSHER = new BlockPos(0, 1, 0), LINK = new BlockPos(4, 1, 0);

    private static ArcCrusherBlockEntity crusherSetup(GameTestHelper helper) {
        helper.setBlock(CRUSHER, ModBlocks.ARC_CRUSHER.get());
        helper.setBlock(LINK, ModBlocks.WIRED_MACHINE_LINK.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(CRUSHER, ArcCrusherBlockEntity.class);
        helper.assertTrue(crusher.getFacing() == Direction.NORTH, "The crusher faces " + crusher.getFacing());
        crusher.setSideMode(RelativeSide.LEFT, SideMode.OUTPUT);
        straight(helper, new BlockPos(1, 1, 0), new BlockPos(3, 1, 0));
        return crusher;
    }

    // --- Iolite ---

    // Iolite Ore needs an iron pickaxe (stone won't do), drops one Iolite Crystal (more with Fortune; the ore with Silk
    // Touch), and every tag and recipe is there.
    static void ioliteOre(GameTestHelper helper) {
        BlockState ore = ModBlocks.IOLITE_ORE.get().defaultBlockState();
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ore), "A stone pickaxe can mine Iolite Ore");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ore), "An iron pickaxe can't mine Iolite Ore");
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        List<ItemStack> drops = Block.getDrops(ore, level, at, null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.IOLITE_CRYSTAL.get()) && drops.getFirst().getCount() == 1,
                "Iolite Ore dropped " + drops);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        drops = Block.getDrops(ore, level, at, null, null, silk);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.IOLITE_ORE.get()), "Silk Touch dropped " + drops);
        ItemStack fortune = new ItemStack(Items.IRON_PICKAXE);
        fortune.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
        int most = 0;
        for (int i = 0; i < 40; i++) {
            most = Math.max(most, Block.getDrops(ore, level, at, null, null, fortune).stream().mapToInt(ItemStack::getCount).sum());
        }
        helper.assertTrue(most > 1, "Fortune III never gave more than one crystal");
        tag(helper, "gems/iolite", ModItems.IOLITE_CRYSTAL.get());
        tag(helper, "ores/iolite", ModItems.IOLITE_ORE.get());
        tag(helper, "dusts/iolite", ModItems.IOLITE_DUST.get());
        tag(helper, "storage_blocks/iolite", ModItems.IOLITE_BLOCK.get());
        tag(helper, "ores_in_ground/end_stone", ModItems.IOLITE_ORE.get());
        for (String recipe : List.of("crafting/iolite_block", "crafting/iolite_crystal_from_iolite_block", "crushing/iolite_dust_from_iolite_ore",
                "crushing/iolite_dust_from_iolite_crystal", "iolite_crystal_from_smelting_dust", "crafting/link_conduit", "crafting/wired_machine_link")) {
            helper.assertTrue(level.recipeAccess().recipeMap().byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(Arcforge.MODID, recipe))) != null, recipe + " didn't load");
        }
        helper.succeed();
    }

    private static void tag(GameTestHelper helper, String path, Item item) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
        helper.assertTrue(new ItemStack(item).is(tag), item + " is not in #c:" + path);
    }

    // The Arc Crusher makes 2 Iolite Dust from the ore, the Arc Crushing Array 4.
    static void ioliteCrushing(GameTestHelper helper) {
        BlockPos crusherPos = new BlockPos(0, 1, 0);
        helper.setBlock(crusherPos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(crusherPos, ArcCrusherBlockEntity.class);
        CrushingGameTests.charge(crusher.getEnergy(), 20_000);
        crusher.getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(ModItems.IOLITE_ORE.get()));
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(3, 1, 0), new BlockPos(5, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        BlockPos centre = new BlockPos(4, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    CrushingGameTests.charge(array.getEnergy(), 100_000);
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(0), new ItemStack(ModItems.IOLITE_ORE.get()));
                })
                .thenIdle(205)
                .thenExecute(() -> {
                    ItemStack single = crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(single.is(ModItems.IOLITE_DUST.get()) && single.getCount() == 2, "The Arc Crusher gave " + single);
                    ItemStack doubled = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class).getItems()
                            .getStack(ArcCrushingArrayBlockEntity.outputSlot(0));
                    helper.assertTrue(doubled.is(ModItems.IOLITE_DUST.get()) && doubled.getCount() == 4, "The Arc Crushing Array gave " + doubled);
                })
                .thenSucceed();
    }

    // The Iolite vein features place Iolite Ore into End Stone (and nothing else).
    static void ioliteGenerates(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0), max = new BlockPos(6, 7, 6);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            helper.setBlock(pos.immutable(), pos.getX() < 3 ? Blocks.END_STONE : Blocks.STONE);
        }
        ServerLevel level = helper.getLevel();
        for (String name : List.of("ore_iolite", "ore_iolite_outer")) {
            var feature = level.registryAccess().lookupOrThrow(Registries.FEATURE)
                    .getOrThrow(ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath(Arcforge.MODID, name)));
            helper.assertTrue(feature.value() instanceof ModWorldgen.ConfigOre, name + " isn't a config ore feature");
            for (int attempt = 0; attempt < 8; attempt++) {
                feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(new BlockPos(3, 4, 3)));
            }
        }
        int inEndStone = 0, inStone = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (helper.getBlockState(pos.immutable()).is(ModBlocks.IOLITE_ORE.get())) {
                if (pos.getX() < 3) {
                    inEndStone++;
                } else {
                    inStone++;
                }
            }
        }
        helper.assertTrue(inEndStone > 0, "No Iolite Ore placed in End Stone");
        helper.assertTrue(inStone == 0, inStone + " Iolite Ore placed in stone");
        helper.succeed();
    }

    // --- The Link Conduit ---

    // Placing a cable: it joins a free end and never branches off a run's middle; a cable removed un-joins its
    // neighbours; a free end plugs into a machine set next to it.
    static void conduitJoins(GameTestHelper helper) {
        BlockState plain = ModBlocks.LINK_CONDUIT.get().defaultBlockState();
        helper.setBlock(new BlockPos(1, 1, 1), plain.setValue(LinkConduitBlock.joint(Direction.EAST), Joint.CABLE));
        helper.setBlock(new BlockPos(2, 1, 1), plain.setValue(LinkConduitBlock.joint(Direction.WEST), Joint.CABLE)
                .setValue(LinkConduitBlock.joint(Direction.EAST), Joint.CABLE));
        helper.setBlock(new BlockPos(3, 1, 1), plain.setValue(LinkConduitBlock.joint(Direction.WEST), Joint.CABLE));
        // Beside the middle: it stays apart.
        BlockState branch = placed(helper, new BlockPos(2, 1, 2));
        helper.assertTrue(branch.getValue(LinkConduitBlock.joint(Direction.NORTH)) == Joint.NONE, "A new cable joined a run's middle: " + branch);
        // Beyond the free end: it joins, and the end joins back.
        BlockState extension = placed(helper, new BlockPos(4, 1, 1));
        helper.assertTrue(extension.getValue(LinkConduitBlock.joint(Direction.WEST)) == Joint.CABLE, "A new cable didn't join the free end: " + extension);
        helper.setBlock(new BlockPos(4, 1, 1), extension);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    BlockState end = helper.getBlockState(new BlockPos(3, 1, 1));
                    helper.assertTrue(end.getValue(LinkConduitBlock.joint(Direction.EAST)) == Joint.CABLE, "The end didn't join back: " + end);
                    helper.setBlock(new BlockPos(4, 1, 1), Blocks.AIR);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    BlockState end = helper.getBlockState(new BlockPos(3, 1, 1));
                    helper.assertTrue(end.getValue(LinkConduitBlock.joint(Direction.EAST)) == Joint.NONE, "It still joins a removed cable: " + end);
                    helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.ARC_CRUSHER.get());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    BlockState end = helper.getBlockState(new BlockPos(3, 1, 1));
                    helper.assertTrue(end.getValue(LinkConduitBlock.joint(Direction.EAST)) == Joint.PLUG, "It didn't plug into the machine: " + end);
                })
                .thenSucceed();
    }

    // The state a Link Conduit gets when placed at pos from above (onto a stone block under it).
    private static BlockState placed(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        BlockPos below = helper.absolutePos(pos.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.LINK_CONDUIT.get()), hit);
        return ModBlocks.LINK_CONDUIT.get().getStateForPlacement(context);
    }

    // --- The Wired Machine Link ---

    // Linked to an Arc Crusher: the cable's face reads as the locked Wired Machine Link and offers nothing; the link's
    // faces, set to the crusher's modes, reach its own input slot and FE buffer; the run and the screen light up.
    static void linksAMachine(GameTestHelper helper) {
        ArcCrusherBlockEntity crusher = crusherSetup(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    WiredMachineLinkBlockEntity link = link(helper, LINK);
                    helper.assertTrue(link.isLinked(), "Not linked: " + link.getStatus());
                    helper.assertTrue(link.getDistance() == 3, "Distance " + link.getDistance());
                    helper.assertTrue(link.getMachineSide() == RelativeSide.LEFT, "The cable is on its " + link.getMachineSide() + " face");
                    helper.assertTrue(link.getCableFace() == Direction.WEST, "The cable comes in at " + link.getCableFace());
                    helper.assertTrue(crusher.getSideMode(RelativeSide.LEFT) == SideMode.MACHINE_LINK, "The crusher's face is " + crusher.getSideMode(RelativeSide.LEFT));
                    helper.assertTrue(crusher.getItemHandler(Direction.EAST) == null, "The cable's face still offers items");
                    crusher.setSideMode(RelativeSide.LEFT, SideMode.INPUT);
                    helper.assertTrue(crusher.getSideMode(RelativeSide.LEFT) == SideMode.MACHINE_LINK, "The cable's face changed");
                    // Nor through the machine control API: it reads "machine_link" and refuses a change.
                    MachineSettings settings = MachineControlGameTests.control(helper, CRUSHER).settings();
                    helper.assertTrue(settings.sideMode(MachineSide.LEFT).equals("machine_link"), "The API reads " + settings.sideMode(MachineSide.LEFT));
                    helper.assertTrue(settings.setSideMode(MachineSide.LEFT, SideModes.INPUT) == SettingResult.REJECTED,
                            "The API changed the cable's face");
                    helper.assertTrue(crusher.getSideMode(RelativeSide.LEFT) == SideMode.MACHINE_LINK, "The cable's face changed through the API");
                    helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 0)).getValue(LinkConduitBlock.LIT), "The run isn't lit");
                    link.setFace(Direction.UP, SideMode.INPUT);
                    link.setFace(Direction.DOWN, SideMode.ENERGY);
                    link.setFace(Direction.WEST, SideMode.INPUT);
                    helper.assertTrue(link.getFaceMode(Direction.WEST) == SideMode.MACHINE_LINK, "The link's cable face changed");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BlockPos at = helper.absolutePos(LINK);
                    ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, at, Direction.UP);
                    helper.assertTrue(items != null, "The Input face offers no items");
                    CrushingGameTests.insert(items, ModItems.IOLITE_ORE.get(), 1);
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).is(ModItems.IOLITE_ORE.get()),
                            "The ore didn't reach the crusher: " + crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT));
                    EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, at, Direction.DOWN);
                    helper.assertTrue(energy != null, "The Energy face offers no FE");
                    int before = crusher.getEnergy().getAmountAsInt();
                    CrushingGameTests.charge(energy, 1_000);
                    helper.assertTrue(crusher.getEnergy().getAmountAsInt() > before, "FE didn't reach the crusher");
                    helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, at, Direction.WEST) == null, "The cable's face offers items");
                    helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH) == null, "An unset face offers items");
                })
                .thenSucceed();
    }

    // Breaking the cable unlinks it: "Not linked", the run goes dark, and the machine's face goes back to the mode it had.
    static void unlinkRestoresFace(GameTestHelper helper) {
        ArcCrusherBlockEntity crusher = crusherSetup(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(link(helper, LINK).isLinked(), "Not linked");
                    helper.setBlock(new BlockPos(2, 1, 0), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    WiredMachineLinkBlockEntity link = link(helper, LINK);
                    helper.assertTrue(!link.isLinked() && link.getStatus() == WiredMachineLinkBlockEntity.Status.BROKEN, "Status " + link.getStatus());
                    helper.assertTrue(crusher.getSideMode(RelativeSide.LEFT) == SideMode.OUTPUT, "The face came back as " + crusher.getSideMode(RelativeSide.LEFT));
                    helper.assertTrue(crusher.getItemHandler(Direction.EAST) != null, "The face doesn't offer its output again");
                    helper.assertTrue(!helper.getBlockState(new BlockPos(3, 1, 0)).getValue(LinkConduitBlock.LIT), "The cut run is still lit");
                    helper.assertTrue(!helper.getBlockState(LINK).getValue(WiredMachineLinkBlock.LIT), "The screen is still lit");
                })
                .thenSucceed();
    }

    // A machine takes one Wired Machine Link: a second one cabled to another of its faces says it's taken.
    static void oneLinkPerMachine(GameTestHelper helper) {
        crusherSetup(helper);
        BlockPos second = new BlockPos(0, 1, 4);
        helper.setBlock(second, ModBlocks.WIRED_MACHINE_LINK.get());
        run(helper, List.of(new BlockPos(0, 1, 1), new BlockPos(0, 1, 2), new BlockPos(0, 1, 3)), Direction.NORTH, Direction.SOUTH);
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    WiredMachineLinkBlockEntity first = link(helper, LINK), other = link(helper, second);
                    helper.assertTrue(first.isLinked() != other.isLinked(), "Both or neither linked: " + first.getStatus() + ", " + other.getStatus());
                    WiredMachineLinkBlockEntity loser = first.isLinked() ? other : first;
                    helper.assertTrue(loser.getStatus() == WiredMachineLinkBlockEntity.Status.TAKEN, "The second says " + loser.getStatus());
                })
                .thenSucceed();
    }

    // A cable from any outer face of a formed multiblock links it: the face shows in its Ports as the Wired Machine Link,
    // and a Heat face on the link heats the Superheater Array.
    static void linksAMultiblock(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        MultiblockTestHelpers.buildCube(helper, min, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        BlockPos linkPos = new BlockPos(6, 2, 1);
        helper.setBlock(linkPos, ModBlocks.WIRED_MACHINE_LINK.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> straight(helper, new BlockPos(3, 2, 1), new BlockPos(5, 2, 1)))
                .thenIdle(3)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = MultiblockTestHelpers.centre(helper, min, SuperheaterArrayBlockEntity.class);
                    helper.assertTrue(array.isFormed(), "The Superheater Array didn't form");
                    WiredMachineLinkBlockEntity link = link(helper, linkPos);
                    helper.assertTrue(link.isLinked(), "Not linked: " + link.getStatus());
                    BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 1));
                    helper.assertTrue(array.faceMode(anchor, Direction.EAST) == SideMode.MACHINE_LINK, "The face is " + array.faceMode(anchor, Direction.EAST));
                    helper.assertTrue(MultiblockPorts.shown(helper.getLevel(), array).stream().anyMatch(port -> port.mode() == SideMode.MACHINE_LINK),
                            "The Ports tab doesn't show the link");
                    MultiblockPorts.set(helper.getLevel(), array, anchor, SideMode.HEAT, Direction.EAST);
                    helper.assertTrue(array.faceMode(anchor, Direction.EAST) == SideMode.MACHINE_LINK, "The link's port could be changed");
                    link.setFace(Direction.UP, SideMode.HEAT);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = MultiblockTestHelpers.centre(helper, min, SuperheaterArrayBlockEntity.class);
                    HeatHandler heat = helper.getLevel().getCapability(ModCapabilities.HEAT, helper.absolutePos(linkPos), Direction.UP);
                    helper.assertTrue(heat != null, "The Heat face offers no heat handler");
                    int before = array.getHeat().getStored();
                    int taken = heat.receiveHeat(5_000, false);
                    helper.assertTrue(taken > 0 && array.getHeat().getStored() == before + taken, "Heat didn't reach the array: took " + taken);
                })
                .thenSucceed();
    }
}
