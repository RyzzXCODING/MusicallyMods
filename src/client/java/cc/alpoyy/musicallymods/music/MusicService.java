package cc.alpoyy.musicallymods.music;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MusicService {
    String id();

    String displayName();

    ServiceCapabilities capabilities();

    /** Short, honest status line shown in the player. */
    String statusText();

    /** Explicit integration/auth/playback state. Overridden for unavailable services. */
    default ServiceStatus status() {
        ServiceCapabilities caps = capabilities();
        return new ServiceStatus(
                ServiceStatus.IntegrationLevel.IMPLEMENTED,
                caps.playback() ? ServiceStatus.PlaybackSupport.NATIVE : ServiceStatus.PlaybackSupport.UNSUPPORTED,
                ServiceStatus.AuthRequirement.NONE,
                statusText(),
                caps.note());
    }

    /** All service calls are asynchronous and must never block Minecraft's threads. */
    CompletableFuture<ServiceResult<List<Track>>> search(String query);
}
