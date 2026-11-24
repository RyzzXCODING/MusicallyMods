package cc.alpoyy.musicallymods.util;

import cc.alpoyy.musicallymods.MusicallyMods;

import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sanitizes third-party error text before it is shown to a user or written to the log. */
public final class Errors {
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(https?://[^\\s?#:]+(?::\\d+)?[^\\s?#]*)(?:\\?[^\\s#]*)?(?:#\\S*)?");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)\\b(access_token|refresh_token|id_token|client_secret|authorization|api_key|apikey|"
                    + "token|code_verifier|code|state|password|cookie)=([^&\\s]+)");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\b(bearer|oauth)\\s+[A-Za-z0-9._~+/=-]{8,}");

    private Errors() {
    }

    /** Short, redacted description suitable for a GUI label. */
    public static String describe(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = sanitize(cause.getMessage());
        if (message.isBlank()) message = cause.getClass().getSimpleName();
        return limit(message, 220);
    }

    /**
     * Records the exception type and redacted message. Stack traces and raw third-party messages
     * are never logged because they can contain signed URLs, tokens or cookies.
     */
    public static void logTechnical(String context, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        MusicallyMods.LOGGER.debug("[MusicallyMods] {}: {} - {}",
                context, cause.getClass().getName(), describe(cause));
    }

    /** Removes query strings, fragments and credential-shaped key/value pairs. */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String text = raw.replace('\r', ' ').replace('\n', ' ');
        text = URL.matcher(text).replaceAll("$1?<redacted>");
        text = BEARER.matcher(text).replaceAll("$1 <redacted>");
        Matcher fields = SENSITIVE_FIELD.matcher(text);
        StringBuffer out = new StringBuffer();
        while (fields.find()) {
            fields.appendReplacement(out, Matcher.quoteReplacement(fields.group(1) + "=<redacted>"));
        }
        fields.appendTail(out);
        return out.toString().trim();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable == null ? new IllegalStateException("unknown error") : throwable;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
