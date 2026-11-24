package cc.alpoyy.musicallymods.config;

import cc.alpoyy.musicallymods.MusicallyMods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Persistent client configuration. Every field is read and validated separately so a
 * single bad value never discards the rest of a user's settings.
 */
public final class ModConfig {
    public static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> SERVICES = Set.of(
            "deezer", "radio", "custom", "spotify", "youtubemusic", "soundcloud");
    private static final Set<String> THEMES = Set.of("DARK", "BLACK", "GRAY", "CUSTOM");
    private static final Set<String> POSITIONS = Set.of(
            "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "CENTER_LEFT", "CENTER", "CENTER_RIGHT",
            "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT",
            "MANUAL");
    private static final Set<String> ALIGNMENTS = Set.of("LEFT", "CENTER");
    private static final java.util.regex.Pattern KEY_NAME =
            java.util.regex.Pattern.compile("[a-z0-9_.-]+(\\.[a-z0-9_.-]+)*");

    private static ModConfig instance = new ModConfig();
    private static boolean dirty;

    public int schemaVersion = SCHEMA_VERSION;
    public String selectedService = "deezer";
    public float volume = 0.6f;
    public String theme = "DARK";
    public boolean autoPauseOnClose = false;
    public final Colors colors = new Colors();
    public final Lyrics lyrics = new Lyrics();
    public final Keybinds keybinds = new Keybinds();
    public final Playback playback = new Playback();
    public final Integrations integrations = new Integrations();
    public final UiLayout ui = new UiLayout();

    public static ModConfig get() {
        return instance;
    }

    public static Path getConfigPath() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("musicallymods.json");
    }

    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            instance = new ModConfig();
            dirty = false;
            saveNow();
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException("configuration root is not a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            ModConfig c = new ModConfig();
            c.schemaVersion = SCHEMA_VERSION;
            c.selectedService = oneOf(readString(root, "selectedService", c.selectedService), SERVICES, c.selectedService);
            c.volume = readFloat(root, "volume", c.volume, 0f, 1f);
            c.theme = oneOf(readString(root, "theme", c.theme), THEMES, c.theme);
            c.autoPauseOnClose = readBoolean(root, "autoPauseOnClose", c.autoPauseOnClose);

            JsonObject playbackJson = readObject(root, "playback");
            c.playback.maxRetryAttempts = readInt(playbackJson, "maxRetryAttempts", c.playback.maxRetryAttempts, 0, 5);
            c.playback.requestTimeoutSeconds = readInt(playbackJson, "requestTimeoutSeconds", c.playback.requestTimeoutSeconds, 5, 120);
            c.playback.useMasterGain = readBoolean(playbackJson, "useMasterGain", c.playback.useMasterGain);

            JsonObject integrationsJson = readObject(root, "integrations");
            c.integrations.youTubeMusicBridgeEnabled = readBoolean(integrationsJson,
                    "youTubeMusicBridgeEnabled", c.integrations.youTubeMusicBridgeEnabled);
            c.integrations.youTubeMusicBridgeExecutable = readBoundedString(integrationsJson,
                    "youTubeMusicBridgeExecutable", c.integrations.youTubeMusicBridgeExecutable, 512);

            JsonObject uiJson = readObject(root, "ui");
            c.ui.resultRowHeight = readInt(uiJson, "resultRowHeight", c.ui.resultRowHeight, 32, 64);
            c.ui.showScrollbars = readBoolean(uiJson, "showScrollbars", c.ui.showScrollbars);

            JsonObject colorsJson = readObject(root, "colors");
            c.colors.mainBg = readColor(colorsJson, "mainBg", c.colors.mainBg);
            c.colors.secondaryBg = readColor(colorsJson, "secondaryBg", c.colors.secondaryBg);
            c.colors.accent = readColor(colorsJson, "accent", c.colors.accent);
            c.colors.textPrimary = readColor(colorsJson, "textPrimary", c.colors.textPrimary);
            c.colors.textSecondary = readColor(colorsJson, "textSecondary", c.colors.textSecondary);
            c.colors.progress = readColor(colorsJson, "progress", c.colors.progress);
            c.colors.controls = readColor(colorsJson, "controls", c.colors.controls);
            c.colors.lyricsBg = readColor(colorsJson, "lyricsBg", c.colors.lyricsBg);
            c.colors.lyricsText = readColor(colorsJson, "lyricsText", c.colors.lyricsText);
            c.colors.lyricsHighlight = readColor(colorsJson, "lyricsHighlight", c.colors.lyricsHighlight);

            JsonObject lyricsJson = readObject(root, "lyrics");
            c.lyrics.enabled = readBoolean(lyricsJson, "enabled", c.lyrics.enabled);
            c.lyrics.position = oneOf(readString(lyricsJson, "position", c.lyrics.position), POSITIONS, c.lyrics.position);
            c.lyrics.manualX = readInt(lyricsJson, "manualX", c.lyrics.manualX, 0, 4000);
            c.lyrics.manualY = readInt(lyricsJson, "manualY", c.lyrics.manualY, 0, 4000);
            c.lyrics.width = readInt(lyricsJson, "width", c.lyrics.width, 120, 1400);
            c.lyrics.height = readInt(lyricsJson, "height", c.lyrics.height, 40, 900);
            c.lyrics.fontSize = readFloat(lyricsJson, "fontSize", c.lyrics.fontSize, 0.7f, 2f);
            c.lyrics.scale = readFloat(lyricsJson, "scale", c.lyrics.scale, 0.5f, 2f);
            c.lyrics.padding = readInt(lyricsJson, "padding", c.lyrics.padding, 0, 40);
            c.lyrics.lineSpacing = readInt(lyricsJson, "lineSpacing", c.lyrics.lineSpacing, 0, 16);
            c.lyrics.maxLines = readInt(lyricsJson, "maxLines", c.lyrics.maxLines, 1, 20);
            c.lyrics.transparency = readInt(lyricsJson, "transparency", c.lyrics.transparency, 0, 255);
            c.lyrics.alignment = oneOf(readString(lyricsJson, "alignment", c.lyrics.alignment), ALIGNMENTS, c.lyrics.alignment);
            c.lyrics.showTitle = readBoolean(lyricsJson, "showTitle", c.lyrics.showTitle);
            c.lyrics.showArtist = readBoolean(lyricsJson, "showArtist", c.lyrics.showArtist);
            c.lyrics.showArtwork = readBoolean(lyricsJson, "showArtwork", c.lyrics.showArtwork);

            JsonObject keysJson = readObject(root, "keybinds");
            c.keybinds.open = readKey(keysJson, "open", c.keybinds.open);
            c.keybinds.lyrics = readKey(keysJson, "lyrics", c.keybinds.lyrics);
            c.keybinds.playPause = readKey(keysJson, "playPause", c.keybinds.playPause);
            c.keybinds.next = readKey(keysJson, "next", c.keybinds.next);
            c.keybinds.previous = readKey(keysJson, "previous", c.keybinds.previous);

            instance = c;
            dirty = false;
        } catch (Exception e) {
            MusicallyMods.LOGGER.warn("MusicallyMods configuration could not be read; defaults restored: {}", e.getMessage());
            instance = new ModConfig();
            dirty = false;
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void saveIfDirty() {
        if (dirty) saveNow();
    }

    public static void saveNow() {
        try {
            JsonObject root = new JsonObject();
            ModConfig c = instance;
            root.addProperty("schemaVersion", SCHEMA_VERSION);
            root.addProperty("selectedService", c.selectedService);
            root.addProperty("volume", c.volume);
            root.addProperty("theme", c.theme);
            root.addProperty("autoPauseOnClose", c.autoPauseOnClose);

            JsonObject playbackJson = new JsonObject();
            playbackJson.addProperty("maxRetryAttempts", c.playback.maxRetryAttempts);
            playbackJson.addProperty("requestTimeoutSeconds", c.playback.requestTimeoutSeconds);
            playbackJson.addProperty("useMasterGain", c.playback.useMasterGain);
            root.add("playback", playbackJson);

            JsonObject integrationsJson = new JsonObject();
            integrationsJson.addProperty("youTubeMusicBridgeEnabled", c.integrations.youTubeMusicBridgeEnabled);
            integrationsJson.addProperty("youTubeMusicBridgeExecutable", c.integrations.youTubeMusicBridgeExecutable);
            root.add("integrations", integrationsJson);

            JsonObject uiJson = new JsonObject();
            uiJson.addProperty("resultRowHeight", c.ui.resultRowHeight);
            uiJson.addProperty("showScrollbars", c.ui.showScrollbars);
            root.add("ui", uiJson);

            JsonObject colorsJson = new JsonObject();
            colorsJson.addProperty("mainBg", hex(c.colors.mainBg));
            colorsJson.addProperty("secondaryBg", hex(c.colors.secondaryBg));
            colorsJson.addProperty("accent", hex(c.colors.accent));
            colorsJson.addProperty("textPrimary", hex(c.colors.textPrimary));
            colorsJson.addProperty("textSecondary", hex(c.colors.textSecondary));
            colorsJson.addProperty("progress", hex(c.colors.progress));
            colorsJson.addProperty("controls", hex(c.colors.controls));
            colorsJson.addProperty("lyricsBg", hex(c.colors.lyricsBg));
            colorsJson.addProperty("lyricsText", hex(c.colors.lyricsText));
            colorsJson.addProperty("lyricsHighlight", hex(c.colors.lyricsHighlight));
            root.add("colors", colorsJson);

            JsonObject lyricsJson = new JsonObject();
            lyricsJson.addProperty("enabled", c.lyrics.enabled);
            lyricsJson.addProperty("position", c.lyrics.position);
            lyricsJson.addProperty("manualX", c.lyrics.manualX);
            lyricsJson.addProperty("manualY", c.lyrics.manualY);
            lyricsJson.addProperty("width", c.lyrics.width);
            lyricsJson.addProperty("height", c.lyrics.height);
            lyricsJson.addProperty("fontSize", c.lyrics.fontSize);
            lyricsJson.addProperty("scale", c.lyrics.scale);
            lyricsJson.addProperty("padding", c.lyrics.padding);
            lyricsJson.addProperty("lineSpacing", c.lyrics.lineSpacing);
            lyricsJson.addProperty("maxLines", c.lyrics.maxLines);
            lyricsJson.addProperty("transparency", c.lyrics.transparency);
            lyricsJson.addProperty("alignment", c.lyrics.alignment);
            lyricsJson.addProperty("showTitle", c.lyrics.showTitle);
            lyricsJson.addProperty("showArtist", c.lyrics.showArtist);
            lyricsJson.addProperty("showArtwork", c.lyrics.showArtwork);
            root.add("lyrics", lyricsJson);

            JsonObject keysJson = new JsonObject();
            keysJson.addProperty("open", c.keybinds.open);
            keysJson.addProperty("lyrics", c.keybinds.lyrics);
            keysJson.addProperty("playPause", c.keybinds.playPause);
            keysJson.addProperty("next", c.keybinds.next);
            keysJson.addProperty("previous", c.keybinds.previous);
            root.add("keybinds", keysJson);

            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("musicallymods.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (Exception e) {
            MusicallyMods.LOGGER.error("Failed to save MusicallyMods configuration: {}", e.getMessage());
        }
    }

    public static void reset() {
        instance = new ModConfig();
        markDirty();
    }

    /** Applies a complete muted color theme; individual colors remain editable afterwards. */
    public static void applyTheme(String name) {
        String normalized = oneOf(name, THEMES, "DARK");
        if (normalized.equals("CUSTOM")) {
            instance.theme = normalized;
            markDirty();
            return;
        }
        Colors c = instance.colors;
        switch (normalized) {
            case "BLACK" -> {
                c.mainBg = 0xFF000000;
                c.secondaryBg = 0xFF101010;
                c.controls = 0xFF1C1C1C;
                c.accent = 0xFFB4B4B4;
            }
            case "GRAY" -> {
                c.mainBg = 0xFF222222;
                c.secondaryBg = 0xFF303030;
                c.controls = 0xFF3A3A3A;
                c.accent = 0xFFC2C2C2;
            }
            default -> {
                c.mainBg = 0xFF0D0D0D;
                c.secondaryBg = 0xFF1A1A1A;
                c.controls = 0xFF2A2A2A;
                c.accent = 0xFF9A9A9A;
            }
        }
        c.progress = c.accent;
        instance.theme = normalized;
        markDirty();
    }

    public static final class Colors {
        public int mainBg = 0xFF0D0D0D;
        public int secondaryBg = 0xFF1A1A1A;
        public int accent = 0xFF9A9A9A;
        public int textPrimary = 0xFFF2F2F2;
        public int textSecondary = 0xFFA8A8A8;
        public int progress = 0xFF8FB6C9;
        public int controls = 0xFF2A2A2A;
        public int lyricsBg = 0x99101010;
        public int lyricsText = 0xFFE8E8E8;
        public int lyricsHighlight = 0xFFD9CF8A;
    }

    public static final class Lyrics {
        public boolean enabled = true;
        public String position = "BOTTOM_CENTER";
        public int manualX = 20;
        public int manualY = 100;
        public int width = 420;
        public int height = 160;
        public float fontSize = 1f;
        public float scale = 1f;
        public int padding = 6;
        public int lineSpacing = 2;
        public int maxLines = 8;
        public int transparency = 160;
        public String alignment = "CENTER";
        public boolean showTitle = true;
        public boolean showArtist = true;
        public boolean showArtwork = false;
    }

    public static final class Keybinds {
        public String open = "key.keyboard.m";
        public String lyrics = "key.keyboard.k";
        public String playPause = "key.keyboard.p";
        public String next = "key.keyboard.n";
        public String previous = "key.keyboard.b";
    }

    /** Playback limits and audio preferences; all values are validated on load. */
    public static final class Playback {
        public int maxRetryAttempts = 2;
        public int requestTimeoutSeconds = 20;
        public boolean useMasterGain = true;
    }

    /** Optional, user-controlled external integrations. No credentials or tokens are stored. */
    public static final class Integrations {
        public boolean youTubeMusicBridgeEnabled = false;
        public String youTubeMusicBridgeExecutable = "";
    }

    /** Responsive GUI layout preferences. */
    public static final class UiLayout {
        public int resultRowHeight = 40;
        public boolean showScrollbars = true;
    }

    private static JsonObject readObject(JsonObject parent, String key) {
        try {
            JsonElement e = parent.get(key);
            if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return new JsonObject();
    }

    private static String readString(JsonObject o, String key, String def) {
        try {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                String value = e.getAsString();
                return value == null || value.isBlank() ? def : value;
            }
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return def;
    }

    private static String readBoundedString(JsonObject o, String key, String def, int maxLength) {
        String value = readString(o, key, def);
        if (value == null) return def;
        value = value.replaceAll("\\p{Cntrl}+", "").trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Bounded, syntax-checked Minecraft key identifier; anything else falls back to the default. */
    private static String readKey(JsonObject o, String key, String def) {
        String value = readBoundedString(o, key, def, 64);
        return KEY_NAME.matcher(value).matches() ? value : def;
    }

    private static boolean readBoolean(JsonObject o, String key, boolean def) {
        try {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return def;
    }

    private static int readInt(JsonObject o, String key, int def, int min, int max) {
        try {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return clamp(e.getAsInt(), min, max);
            }
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return def;
    }

    private static float readFloat(JsonObject o, String key, float def, float min, float max) {
        try {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return clamp(e.getAsFloat(), min, max);
            }
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return def;
    }

    private static int readColor(JsonObject o, String key, int def) {
        try {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonPrimitive()) return def;
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isString()) {
                Integer parsed = parseColor(p.getAsString());
                if (parsed != null) return parsed;
            } else if (p.isNumber()) {
                return p.getAsInt();
            }
        } catch (RuntimeException ignored) {
            // Fall through to defaults.
        }
        return def;
    }

    /** Parses #AARRGGBB (or plain AARRGGBB) into an ARGB int, or null when invalid. */
    public static Integer parseColor(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 8) return null;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String oneOf(String value, Set<String> allowed, String def) {
        return value != null && allowed.contains(value) ? value : def;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
