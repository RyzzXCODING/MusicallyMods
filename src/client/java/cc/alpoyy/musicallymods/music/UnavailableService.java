package cc.alpoyy.musicallymods.music;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A service that is deliberately not integrated. It exists only so the player can state
 * limitations plainly instead of showing buttons that do nothing.
 */
public final class UnavailableService implements MusicService {
    private final String id;
    private final String displayName;
    private final String reason;

    public UnavailableService(String id, String displayName, String reason) {
        this.id = id;
        this.displayName = displayName;
        this.reason = reason;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public ServiceCapabilities capabilities() {
        return ServiceCapabilities.NONE;
    }

    @Override
    public String statusText() {
        return reason;
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        return CompletableFuture.completedFuture(ServiceResult.failure(reason));
    }
}
