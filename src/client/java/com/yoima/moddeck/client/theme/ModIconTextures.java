package com.yoima.moddeck.client.theme;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Resolves and draws mod icons declared in Fabric metadata. */
public final class ModIconTextures {
    private static final Map<String, Optional<Identifier>> CACHE = new ConcurrentHashMap<>();

    private ModIconTextures() {}

    public static boolean draw(GuiGraphicsExtractor graphics, String modId, int x, int y, int size) {
        Optional<Identifier> texture = CACHE.computeIfAbsent(modId, ModIconTextures::resolve);
        if (texture.isEmpty()) return false;
        // The integer arguments are destination edges, not x/y/width/height. Passing size as the
        // right/bottom edge makes icons near the right side render as a long reversed strip.
        graphics.blit(texture.orElseThrow(), x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
        return true;
    }

    static Optional<Identifier> identifierFromMetadataPath(String path) {
        if (path == null) return Optional.empty();
        String normalized = path.replace('\\', '/');
        if (!normalized.startsWith("assets/")) return Optional.empty();
        int namespaceEnd = normalized.indexOf('/', "assets/".length());
        if (namespaceEnd < 0 || namespaceEnd == normalized.length() - 1) return Optional.empty();
        String namespace = normalized.substring("assets/".length(), namespaceEnd);
        String resourcePath = normalized.substring(namespaceEnd + 1);
        try {
            return Optional.of(Identifier.fromNamespaceAndPath(namespace, resourcePath));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Identifier> resolve(String modId) {
        Optional<Identifier> identifier = FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.getMetadata().getIconPath(64))
                .flatMap(ModIconTextures::identifierFromMetadataPath);
        return identifier.filter(value -> Minecraft.getInstance().getResourceManager()
                .getResource(value).isPresent());
    }
}
