package cc.alpoyy.musicallymods.util;

public final class Errors {
    private Errors() {
    }

    public static String describe(Throwable throwable) {
        if (throwable == null) return "unknown error";
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        return message.length() > 220 ? message.substring(0, 217) + "..." : message;
    }
}
