package cc.alpoyy.musicallymods.util;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.music.Track;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Small bounded cache of downloaded album/station artwork textures. */
public final class ArtworkCache {
    private static final int MAX_ENTRIES = 12;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Map<String, Entry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true));
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private ArtworkCache() {
    }

    private record Entry(Identifier id, int width, int height, DynamicTexture texture) {
    }

    public static void request(Track track) {
        String url = track == null ? "" : track.artworkUrl();
        if (url.isBlank() || CACHE.containsKey(key(url)) || !REQUESTED.add(url)) return;
        NetworkManager.getBytes(url).thenAccept(bytes -> Minecraft.getInstance().execute(() -> install(url, bytes)))
                .exceptionally(throwable -> {
                    REQUESTED.remove(url);
                    MusicallyMods.LOGGER.debug("Artwork download failed for {}: {}", host(url), Errors.describe(throwable));
                    return null;
                });
    }

    public static boolean has(Track track) {
        return track != null && CACHE.containsKey(key(track.artworkUrl()));
    }

    public static void render(GuiGraphics graphics, Track track, int x, int y, int width, int height) {
        if (track == null || track.artworkUrl().isBlank()) return;
        Entry entry = CACHE.get(key(track.artworkUrl()));
        if (entry == null) return;
        graphics.blit(entry.id(), x, y, width, height, 0f, 0f, entry.width(), entry.height());
    }

    public static void clear() {
        Minecraft.getInstance().execute(() -> {
            for (Entry entry : CACHE.values()) {
                release(entry);
            }
            CACHE.clear();
            REQUESTED.clear();
        });
    }

    private static void install(String url, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            REQUESTED.remove(url);
            return;
        }
        NativeImage image = null;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            image = NativeImage.read(input);
            String key = key(url);
            Identifier id = Identifier.fromNamespaceAndPath(
                    MusicallyMods.MOD_ID, "artwork/" + String.format("%08x", url.hashCode()));
            DynamicTexture texture = new DynamicTexture(() -> "MusicallyMods artwork " + key, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            evictIfNeeded();
            CACHE.put(key, new Entry(id, image.getWidth(), image.getHeight(), texture));
            image = null; // Ownership transferred to the DynamicTexture.
        } catch (Exception e) {
            MusicallyMods.LOGGER.debug("Could not decode artwork from {}: {}", host(url), Errors.describe(e));
        } finally {
            if (image != null) image.close();
            REQUESTED.remove(url);
        }
    }

    private static void evictIfNeeded() {
        if (CACHE.size() < MAX_ENTRIES) return;
        String oldest = CACHE.keySet().iterator().next();
        Entry entry = CACHE.remove(oldest);
        if (entry != null) release(entry);
    }

    private static void release(Entry entry) {
        Minecraft.getInstance().getTextureManager().release(entry.id());
        entry.texture().close();
    }

    private static String key(String url) {
        return url == null ? "" : url;
    }

    private static String host(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (RuntimeException e) {
            return "service";
        }
    }
}
