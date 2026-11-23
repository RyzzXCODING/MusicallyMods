package cc.alpoyy.musicallymods.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Defensive helpers for third-party JSON, where any field may be missing or malformed. */
public final class JsonUtil {
    private JsonUtil() {
    }

    public static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static String string(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    public static long number(JsonObject object, String key, long fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static String nestedString(JsonObject object, String objectKey, String field) {
        return string(object(object == null ? null : object.get(objectKey)), field);
    }
}
