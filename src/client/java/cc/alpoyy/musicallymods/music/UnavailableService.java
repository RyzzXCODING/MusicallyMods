package cc.alpoyy.musicallymods.music;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A non-interactive service entry that explains its exact status instead of showing buttons that
 * would do nothing or pretend to play. No credentials, cookies or extraction are involved.
 */
public final class UnavailableService implements MusicService {
    private final String id;
    private final String displayName;
    private final ServiceStatus status;

    public UnavailableService(String id, String displayName, ServiceStatus status) {
        this.id = id;
        this.displayName = displayName;
        this.status = status;
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
        return status.summary();
    }

    @Override
    public ServiceStatus status() {
        return status;
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        return CompletableFuture.completedFuture(ServiceResult.failure(status.detail()));
    }
}
