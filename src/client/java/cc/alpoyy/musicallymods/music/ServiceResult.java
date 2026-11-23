package cc.alpoyy.musicallymods.music;

import java.util.Optional;

/** Result wrapper that carries a human-readable failure instead of throwing across threads. */
public record ServiceResult<T>(T value, String error) {
    public static <T> ServiceResult<T> success(T value) {
        return new ServiceResult<>(value, null);
    }

    public static <T> ServiceResult<T> failure(String error) {
        return new ServiceResult<>(null, error == null || error.isBlank() ? "Unknown error" : error);
    }

    public boolean successful() {
        return error == null;
    }

    public Optional<T> valueOptional() {
        return Optional.ofNullable(value);
    }
}
