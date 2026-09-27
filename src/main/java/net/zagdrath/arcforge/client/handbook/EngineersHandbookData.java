/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.handbook;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;

// The Engineer's Handbook's contents, read from assets/arcforge/engineers_handbook/: book.json lists the chapters
// (with their entries in order), and each entry is engineers_handbook/entries/<id>.json with its title, icon,
// featured items and pages. Pages are "text" (paragraphs; a line starting "# " is a heading, "- " a bullet)
// or "multiblock" (a build viewer for one of MultiblockBlueprints). Read fresh each time the book opens,
// so resource packs can change it.
public final class EngineersHandbookData {
    public record Book(List<Chapter> chapters) {}

    public record Chapter(String id, Component title, ItemStack icon, Component description, List<Entry> entries) {}

    public record Entry(String id, Component title, ItemStack icon, List<ItemStack> items, List<Page> pages) {}

    public sealed interface Page permits TextPage, MultiblockPage {}

    public record TextPage(String text) implements Page {}

    public record MultiblockPage(MultiblockBlueprints.Blueprint blueprint) implements Page {}

    private static final String ROOT = "engineers_handbook/";

    private EngineersHandbookData() {}

    public static Book load(ResourceManager resources) {
        JsonObject book = read(resources, ROOT + "book.json");
        List<Chapter> chapters = new ArrayList<>();
        if (book != null) {
            for (JsonElement element : GsonHelper.getAsJsonArray(book, "chapters")) {
                JsonObject chapter = element.getAsJsonObject();
                List<Entry> entries = new ArrayList<>();
                for (JsonElement entryId : GsonHelper.getAsJsonArray(chapter, "entries")) {
                    Entry entry = loadEntry(resources, entryId.getAsString());
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                chapters.add(new Chapter(GsonHelper.getAsString(chapter, "id"),
                        Component.literal(GsonHelper.getAsString(chapter, "title")),
                        item(GsonHelper.getAsString(chapter, "icon", "arcforge:engineers_handbook")),
                        Component.literal(GsonHelper.getAsString(chapter, "description", "")),
                        entries));
            }
        }
        return new Book(chapters);
    }

    private static @Nullable Entry loadEntry(ResourceManager resources, String id) {
        JsonObject json = read(resources, ROOT + "entries/" + id + ".json");
        if (json == null) {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        if (json.has("items")) {
            for (JsonElement item : GsonHelper.getAsJsonArray(json, "items")) {
                items.add(item(item.getAsString()));
            }
        }
        List<Page> pages = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "pages")) {
            JsonObject page = element.getAsJsonObject();
            switch (GsonHelper.getAsString(page, "type", "text")) {
                case "multiblock" -> {
                    String structure = GsonHelper.getAsString(page, "structure");
                    MultiblockBlueprints.all().stream().filter(blueprint -> blueprint.id().equals(structure)).findFirst()
                            .ifPresentOrElse(blueprint -> pages.add(new MultiblockPage(blueprint)),
                                    () -> Arcforge.LOGGER.warn("Handbook entry {} names unknown multiblock {}", id, structure));
                }
                default -> pages.add(new TextPage(GsonHelper.getAsString(page, "text")));
            }
        }
        return new Entry(id, Component.literal(GsonHelper.getAsString(json, "title")),
                item(GsonHelper.getAsString(json, "icon", "arcforge:engineers_handbook")), items, pages);
    }

    private static ItemStack item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null ? new ItemStack(BuiltInRegistries.ITEM.getValue(identifier)) : new ItemStack(Items.BARRIER);
    }

    private static @Nullable JsonObject read(ResourceManager resources, String path) {
        Optional<Resource> resource = resources.getResource(Identifier.fromNamespaceAndPath(Arcforge.MODID, path));
        if (resource.isEmpty()) {
            Arcforge.LOGGER.warn("Missing handbook file {}", path);
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            Arcforge.LOGGER.error("Couldn't read handbook file {}", path, e);
            return null;
        }
    }
}
