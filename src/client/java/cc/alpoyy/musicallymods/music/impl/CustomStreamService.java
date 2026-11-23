package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceCapabilities;
import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.Track;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** User-provided direct audio URLs; no account, no metadata service. */
public final class CustomStreamService implements MusicService {
    private static final ServiceCapabilities CAPABILITIES = new ServiceCapabilities(
            true, false, false, true, true, false, false, false,
            "Paste a direct http(s) audio URL into the search field to play it. Supported: MP3/OGG Vorbis (bundled decoders) and WAV/AU (JDK). No metadata, playlists or stream lyrics.");

    @Override
    public String id() {
        return "custom";
    }

    @Override
    public String displayName() {
        return "Direct Stream URL";
    }

    @Override
    public ServiceCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String statusText() {
        return "Paste a legal direct http(s) audio URL. No credentials required.";
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        String url = query.trim();
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return CompletableFuture.completedFuture(
                        ServiceResult.failure("Enter a full http:// or https:// audio URL."));
            }
            String path = uri.getPath();
            String name = path == null || path.isBlank() ? "Direct stream" : path.substring(path.lastIndexOf('/') + 1);
            if (name.isBlank()) name = "Direct stream";
            Track track = new Track(url, name, "User-provided stream", "Direct URL", "", url, 0L, id(),
                    "Direct stream. Format must be supported by the bundled audio decoders.", true);
            return CompletableFuture.completedFuture(ServiceResult.success(List.of(track)));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(ServiceResult.failure("That is not a valid URL."));
        }
    }
}
