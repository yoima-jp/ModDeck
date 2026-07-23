package com.yoima.moddeck.api;

import java.util.Objects;

/** Stable external identifier for a registered mod's config screen. */
public record ConfigRoute(String namespace, String path) {
    public ConfigRoute {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid route namespace: " + namespace);
        }
        if (path == null || !path.matches("[a-z0-9/._-]+") || path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Invalid route path: " + path);
        }
    }

    public static ConfigRoute forMod(String modId) {
        Objects.requireNonNull(modId, "modId");
        return new ConfigRoute("moddeck", "config/" + modId);
    }

    public static ConfigRoute parse(String route) {
        int separator = Objects.requireNonNull(route, "route").indexOf(':');
        if (separator <= 0 || separator == route.length() - 1) {
            throw new IllegalArgumentException("Route must use namespace:path syntax: " + route);
        }
        return new ConfigRoute(route.substring(0, separator), route.substring(separator + 1));
    }

    @Override public String toString() { return namespace + ":" + path; }
}
