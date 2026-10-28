/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModTriggers;

// Arcforge's Hibiscus Quests chapters (data/arcforge/hibiscusquests, written by tools/hibiscusquests): every chapter
// reads, its ids, groups, dependencies, gates and items exist, and every criterion task parses with its trigger's codec
// (as Hibiscus Quests parses it); the Arcforge triggers fire for the pack's own criteria; and the Wrench's early
// recipe, which Getting Started counts on. Hibiscus Quests itself isn't in this run.
public final class QuestPackGameTests {
    private QuestPackGameTests() {}

    private static final String DIRECTORY = "hibiscusquests";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    // The SNBT files under data/arcforge/<path>, by path relative to it.
    private static Map<String, CompoundTag> read(GameTestHelper helper, String path, List<String> errors) {
        ResourceManager resources = helper.getLevel().getServer().getResourceManager();
        Map<String, CompoundTag> files = new TreeMap<>();
        Map<Identifier, Resource> found = resources.listResources(path,
                id -> id.getNamespace().equals(Arcforge.MODID) && id.getPath().endsWith(".snbt"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            String name = entry.getKey().getPath().substring(DIRECTORY.length() + 1);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                files.put(name, TagParser.parseCompoundFully(reader.lines().collect(Collectors.joining("\n"))));
            } catch (IOException | CommandSyntaxException e) {
                errors.add(name + " doesn't read: " + e.getMessage());
            }
        }
        return files;
    }

    // Every chapter, by id.
    private static Map<String, CompoundTag> chapters(GameTestHelper helper, List<String> errors) {
        Map<String, CompoundTag> chapters = new TreeMap<>();
        read(helper, DIRECTORY + "/chapters", errors).forEach((name, chapter) -> chapters.put(chapter.getStringOr("id", ""), chapter));
        return chapters;
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key).compoundStream().toList();
    }

    private static boolean isItem(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed);
    }

    // Every chapter file reads, and everything in it points at something real.
    static void packLoads(GameTestHelper helper) {
        List<String> errors = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        read(helper, DIRECTORY, errors).getOrDefault("chapter_groups.snbt", new CompoundTag()).getListOrEmpty("groups")
                .compoundStream().forEach(group -> groups.add(group.getStringOr("id", "")));
        Map<String, CompoundTag> chapters = chapters(helper, errors);
        helper.assertTrue(chapters.size() >= 20, "Only " + chapters.size() + " chapters loaded");
        Set<String> quests = new HashSet<>();
        chapters.forEach((id, chapter) -> compounds(chapter, "quests").forEach(quest -> quests.add(id + "/" + quest.getStringOr("id", ""))));
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        for (Map.Entry<String, CompoundTag> entry : chapters.entrySet()) {
            String chapterId = entry.getKey();
            CompoundTag chapter = entry.getValue();
            if (!chapterId.startsWith(Arcforge.MODID + "_") || !ID.matcher(chapterId).matches()) {
                errors.add("Chapter id \"" + chapterId + "\" isn't arcforge_<id>");
            }
            if (!groups.contains(chapter.getStringOr("group", ""))) {
                errors.add(chapterId + ": unknown group " + chapter.getStringOr("group", ""));
            }
            if (!isItem(chapter.getStringOr("icon", ""))) {
                errors.add(chapterId + ": unknown icon " + chapter.getStringOr("icon", ""));
            }
            for (Tag required : chapter.getListOrEmpty("requires")) {
                String ref = required.asString().orElse("");
                if (!chapters.containsKey(ref) && !quests.contains(ref)) {
                    errors.add(chapterId + " requires unknown " + ref);
                }
            }
            Set<String> seen = new HashSet<>();
            for (CompoundTag quest : compounds(chapter, "quests")) {
                String where = chapterId + "/" + quest.getStringOr("id", "");
                if (!ID.matcher(quest.getStringOr("id", "")).matches() || !seen.add(quest.getStringOr("id", ""))) {
                    errors.add(where + ": missing, bad or repeated id");
                }
                quest.getString("icon").filter(icon -> !isItem(icon)).ifPresent(icon -> errors.add(where + ": unknown icon " + icon));
                for (Tag dependency : quest.getListOrEmpty("dependencies")) {
                    String ref = dependency.asString().orElse("");
                    if (!quests.contains(ref.contains("/") ? ref : chapterId + "/" + ref)) {
                        errors.add(where + ": unknown dependency " + ref);
                    }
                }
                List<CompoundTag> tasks = compounds(quest, "tasks");
                if (tasks.isEmpty()) {
                    errors.add(where + ": no tasks");
                }
                for (CompoundTag task : tasks) {
                    task.getString("icon").filter(icon -> !isItem(icon)).ifPresent(icon -> errors.add(where + ": unknown task icon " + icon));
                    switch (task.getStringOr("type", "")) {
                        case "item" -> {
                            if (!isItem(task.getStringOr("item", ""))) {
                                errors.add(where + ": unknown item " + task.getStringOr("item", ""));
                            }
                        }
                        case "criterion" -> {
                            DataResult<Criterion<?>> parsed = Criterion.CODEC.parse(ops, task.getCompoundOrEmpty("criterion"));
                            parsed.error().ifPresent(error -> errors.add(where + ": criterion won't load: " + error.message()));
                        }
                        default -> errors.add(where + ": unexpected task type " + task.getStringOr("type", ""));
                    }
                }
                for (CompoundTag reward : compounds(quest, "rewards")) {
                    if (reward.getStringOr("type", "").equals("item") && !isItem(reward.getStringOr("item", ""))) {
                        errors.add(where + ": unknown reward " + reward.getStringOr("item", ""));
                    }
                }
            }
        }
        helper.assertTrue(errors.isEmpty(), errors.size() + " problems in the quest pack: " + errors.subList(0, Math.min(8, errors.size())));
        helper.succeed();
    }

    // The criterion of the first task of a quest in the pack that uses this trigger.
    private static Criterion<?> criterion(GameTestHelper helper, String chapter, String quest, String trigger) {
        CompoundTag file = chapters(helper, new ArrayList<>()).get(chapter);
        helper.assertTrue(file != null, "No chapter " + chapter);
        CompoundTag task = compounds(file, "quests").stream().filter(q -> q.getStringOr("id", "").equals(quest))
                .flatMap(q -> compounds(q, "tasks").stream())
                .filter(t -> t.getCompoundOrEmpty("criterion").getStringOr("trigger", "").equals(trigger))
                .findFirst().orElse(null);
        helper.assertTrue(task != null, chapter + "/" + quest + " has no " + trigger + " task");
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        return Criterion.CODEC.parse(ops, task.getCompoundOrEmpty("criterion")).getOrThrow();
    }

    // A stand-in advancement holding just this criterion, listening on the player's triggers the way Hibiscus Quests'
    // criterion tasks do (PlayerAdvancements has no public way to add a listener, hence the reflection).
    private static AdvancementHolder listen(ServerPlayer player, Criterion<?> criterion) {
        Identifier id = Identifier.fromNamespaceAndPath(Arcforge.MODID, "gametest/quest_task");
        AdvancementHolder holder = new AdvancementHolder(id, new Advancement(Optional.empty(), Optional.empty(), AdvancementRewards.EMPTY,
                Map.of("task", criterion), AdvancementRequirements.allOf(List.of("task")), false));
        try {
            Method register = PlayerAdvancements.class.getDeclaredMethod("registerListeners", AdvancementHolder.class);
            register.setAccessible(true);
            register.invoke(player.getAdvancements(), holder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't listen to advancement triggers", e);
        }
        return holder;
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos standRelative) {
        ServerPlayer player = TestFixtures.player(helper);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(standRelative);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    // Forming an Arc Crushing Array next to a player completes the "Three by Three" quest's task.
    static void multiblockFormedTrigger(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(4, 1, 1));
        AdvancementHolder task = listen(player, criterion(helper, "arcforge_processing", "arrays", "arcforge:multiblock_formed"));
        helper.assertFalse(done(player, task), "The task starts done");
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(done(player, task), "Forming an Arc Crushing Array didn't count"))
                .thenSucceed();
    }

    // "Nine-Stage Spin" wants a Steam Turbine Array at least 9 long: a 3-long one at full speed doesn't count, a 9-long does.
    static void turbineFullSpeedNeedsLength(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        AdvancementHolder task = listen(player, criterion(helper, "arcforge_steam", "nine_stage_spin", "arcforge:turbine_full_speed"));
        Identifier turbine = Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine_array");
        ModTriggers.TURBINE_FULL_SPEED.get().trigger(player, turbine, 3, 15);
        helper.assertFalse(done(player, task), "A 3-long turbine counted for nine_stage_spin");
        ModTriggers.TURBINE_FULL_SPEED.get().trigger(player, turbine, 9, 15);
        helper.assertTrue(done(player, task), "A 9-long turbine didn't count for nine_stage_spin");
        helper.succeed();
    }

    // The Wrench is crafted from three iron ingots and a copper ingot (no steel), so it comes before the Carbonizer.
    static void wrenchRecipeIsEarly(GameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/wrench")));
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, "No Wrench recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        ItemStack iron = new ItemStack(Items.IRON_INGOT), copper = new ItemStack(Items.COPPER_INGOT);
        CraftingInput grid = CraftingInput.of(3, 3, List.of(iron, ItemStack.EMPTY, iron, ItemStack.EMPTY, copper, ItemStack.EMPTY,
                ItemStack.EMPTY, iron, ItemStack.EMPTY));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Iron and copper don't make a Wrench");
        helper.assertTrue(recipe.assemble(grid).is(ModItems.WRENCH.get()), "The recipe doesn't make a Wrench");
        ItemStack steel = new ItemStack(ModItems.STEEL_INGOT.get());
        CraftingInput steelGrid = CraftingInput.of(3, 3, List.of(steel, ItemStack.EMPTY, steel, ItemStack.EMPTY, copper, ItemStack.EMPTY,
                ItemStack.EMPTY, steel, ItemStack.EMPTY));
        helper.assertFalse(recipe.matches(steelGrid, helper.getLevel()), "The Wrench recipe takes steel");
        helper.succeed();
    }
}
