/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api;

/**
 * Constants describing the Arcforge public API.
 *
 * <p>The API follows <a href="https://semver.org/">semantic versioning</a>, independently of the Arcforge mod version:
 * <ul>
 *   <li>a <b>patch</b> bump fixes documentation or behaviour without changing any type or method;</li>
 *   <li>a <b>minor</b> bump adds types, methods or enum constants in a backwards compatible way (consumers built
 *       against an older minor version keep working);</li>
 *   <li>a <b>major</b> bump changes or removes a type or method.</li>
 * </ul>
 * Consumers can compare {@link #API_VERSION_MAJOR} at runtime with the major version they were built against.
 */
public final class ArcforgeApi {
    /** The Arcforge mod id, which is also the namespace of every Arcforge identifier. */
    public static final String MOD_ID = "arcforge";

    /** The full API version, {@code MAJOR.MINOR.PATCH}. This is also the version of the published API jar. */
    public static final String API_VERSION = "1.1.0";

    /** The major part of {@link #API_VERSION}. */
    public static final int API_VERSION_MAJOR = 1;

    /** The minor part of {@link #API_VERSION}. */
    public static final int API_VERSION_MINOR = 1;

    /** The patch part of {@link #API_VERSION}. */
    public static final int API_VERSION_PATCH = 0;

    private ArcforgeApi() {}
}
