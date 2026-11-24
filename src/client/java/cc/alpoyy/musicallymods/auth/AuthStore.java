package cc.alpoyy.musicallymods.auth;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.util.Errors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

/**
 * Separate, consent-gated local auth file. Provider credentials and tokens are never written to the
 * main config, never logged, and never bundled in source. Corrupt provider entries are skipped
 * without discarding other providers.
 */
public final class AuthStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile Data loaded;

    private AuthStore() {
    }

    public static final class Session {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAtEpochMs;
        public final String accountId;
        public final String displayName;
        public final String scopes;

        public Session(String accessToken, String refreshToken, long expiresAtEpochMs,
                       String accountId, String displayName, String scopes) {
            this.accessToken = accessToken == null ? "" : accessToken;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.accountId = accountId == null ? "" : accountId;
            this.displayName = displayName == null ? "" : displayName;
            this.scopes = scopes == null ? "" : scopes;
        }

        public boolean expired() {
            return expiresAtEpochMs <= System.currentTimeMillis() + 30_000L;
        }

        public boolean canRefresh() {
            return !refreshToken.isBlank();
        }

        public String accessToken() {
            return accessToken;
        }

        public String refreshToken() {
            return refreshToken;
        }

        public long expiresAtEpochMs() {
            return expiresAtEpochMs;
        }

        public String accountId() {
            return accountId;
        }

        public String displayName() {
            return displayName;
        }

        public String scopes() {
            return scopes;
        }
    }

    public static final class Data {
        public boolean storageConsent;
        public boolean corrupted;
        public String spotifyClientId = "";
        public String soundCloudClientId = "";
        public String soundCloudClientSecret = "";
        public final Map<String, Session> sessions = new HashMap<>();
    }

    public static Path path() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("musicallymods-auth.json");
    }

    public static Data get() {
        Data current = loaded;
        if (current == null) {
            current = load();
            loaded = current;
        }
        return current;
    }

    public static Data load() {
        Path path = path();
        Data data = new Data();
        if (!Files.isRegularFile(path)) return data;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                data.corrupted = true;
                return data;
            }
            JsonObject root = parsed.getAsJsonObject();
            data.storageConsent = bool(root, "storageConsent", false);
            data.spotifyClientId = bounded(root, "spotifyClientId", 256);
            data.soundCloudClientId = bounded(root, "soundCloudClientId", 256);
            data.soundCloudClientSecret = bounded(root, "soundCloudClientSecret", 256);
            JsonObject sessions = object(root, "sessions");
            for (Map.Entry<String, JsonElement> entry : sessions.entrySet()) {
                try {
                    Session session = session(entry.getValue());
                    if (session != null) data.sessions.put(entry.getKey(), session);
                } catch (RuntimeException ignored) {
                    // One broken provider entry must not discard the others.
                    data.corrupted = true;
                }
            }
        } catch (Exception e) {
            data.corrupted = true;
            MusicallyMods.LOGGER.warn("MusicallyMods auth file could not be read; starting with no saved sessions.");
        }
        return data;
    }

    public static synchronized void save(Data data) {
        if (data == null) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("storageConsent", data.storageConsent);
            root.addProperty("spotifyClientId", data.spotifyClientId == null ? "" : data.spotifyClientId);
            root.addProperty("soundCloudClientId", data.soundCloudClientId == null ? "" : data.soundCloudClientId);
            root.addProperty("soundCloudClientSecret",
                    data.soundCloudClientSecret == null ? "" : data.soundCloudClientSecret);
            JsonObject sessions = new JsonObject();
            for (Map.Entry<String, Session> entry : data.sessions.entrySet()) {
                Session s = entry.getValue();
                if (s == null || s.accessToken.isBlank()) continue;
                JsonObject item = new JsonObject();
                item.addProperty("accessToken", s.accessToken);
                item.addProperty("refreshToken", s.refreshToken);
                item.addProperty("expiresAtEpochMs", s.expiresAtEpochMs);
                item.addProperty("accountId", s.accountId);
                item.addProperty("displayName", s.displayName);
                item.addProperty("scopes", s.scopes);
                sessions.add(entry.getKey(), item);
            }
            root.add("sessions", sessions);

            Path path = path();
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), "musicallymods-auth-", ".tmp");
            try {
                restrict(temp);
                Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                restrict(path);
            } finally {
                Files.deleteIfExists(temp);
            }
            loaded = data;
        } catch (Exception e) {
            MusicallyMods.LOGGER.error("Could not save MusicallyMods auth file: {}", Errors.describe(e));
        }
    }

    /** Clears only the named provider and leaves every other saved session untouched. */
    public static synchronized void clearSession(String provider) {
        Data data = get();
        data.sessions.remove(provider);
        save(data);
    }

    public static synchronized Session session(String provider) {
        return get().sessions.get(provider);
    }

    /** Describes the real storage limitation shown to users before they consent. */
    public static String storageNote() {
        return "OS keychain/credential-manager integration is not available in this mod's dependency set. "
                + "Sessions are stored in " + path().getFileName() + " with owner-only permissions where the "
                + "file system supports them. Keep this file private and exclude it from config backups.";
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (RuntimeException ignored) {
            // Non-POSIX file systems cannot enforce Unix permissions here.
        } catch (java.io.IOException e) {
            MusicallyMods.LOGGER.debug("Could not restrict auth file permissions: {}", Errors.describe(e));
        }
    }

    private static Session session(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String token = bounded(object, "accessToken", 8192);
        if (token.isBlank()) return null;
        long expires = 0L;
        try {
            JsonElement raw = object.get("expiresAtEpochMs");
            if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                expires = raw.getAsLong();
            }
        } catch (RuntimeException ignored) {
            // Expiry defaults to already expired, which forces a reconnect.
        }
        return new Session(token, bounded(object, "refreshToken", 8192), expires,
                bounded(object, "accountId", 256), bounded(object, "displayName", 256),
                bounded(object, "scopes", 1024));
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                return value.getAsBoolean();
            }
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        return fallback;
    }

    private static String bounded(JsonObject object, String key, int max) {
        try {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (text != null) return text.length() <= max ? text : text.substring(0, max);
            }
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        return "";
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
}
