/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Every player's security profile, set at a Security Terminal: the mode their blocks default to, and the players
// they trust (with the name each had when added). Saved once for the whole server. Checks read it live, so a
// change applies to all of a player's blocks at once.
public class SecurityProfiles extends SavedData {
    public record Profile(SecurityMode defaultMode, Map<UUID, String> trusted) {
        public static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
                SecurityMode.CODEC.fieldOf("default_mode").forGetter(Profile::defaultMode),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).fieldOf("trusted").forGetter(Profile::trusted))
                .apply(i, Profile::new));

        public boolean trusts(UUID player) {
            return trusted.containsKey(player);
        }
    }

    private static final Codec<SecurityProfiles> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Profile.CODEC)
            .xmap(SecurityProfiles::new, profiles -> profiles.profiles);
    public static final SavedDataType<SecurityProfiles> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "security"), SecurityProfiles::new, CODEC);

    private final Map<UUID, Profile> profiles;

    public SecurityProfiles() {
        this(Map.of());
    }

    private SecurityProfiles(Map<UUID, Profile> profiles) {
        this.profiles = new HashMap<>(profiles);
    }

    public static SecurityProfiles get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // A player's profile; one who never set one gets the configured default and trusts no one.
    public Profile profile(UUID player) {
        Profile profile = profiles.get(player);
        return profile != null ? profile : new Profile(ArcforgeConfig.SECURITY_DEFAULT_MODE.get(), Map.of());
    }

    public void setDefault(UUID player, SecurityMode mode) {
        profiles.put(player, new Profile(mode, profile(player).trusted()));
        setDirty();
    }

    public void trust(UUID player, UUID trusted, String name) {
        Map<UUID, String> list = new LinkedHashMap<>(profile(player).trusted());
        list.put(trusted, name);
        profiles.put(player, new Profile(profile(player).defaultMode(), list));
        setDirty();
    }

    public void untrust(UUID player, UUID trusted) {
        Map<UUID, String> list = new LinkedHashMap<>(profile(player).trusted());
        list.remove(trusted);
        profiles.put(player, new Profile(profile(player).defaultMode(), list));
        setDirty();
    }
}
