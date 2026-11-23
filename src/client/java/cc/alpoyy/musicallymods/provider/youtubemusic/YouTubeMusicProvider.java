package cc.alpoyy.musicallymods.provider.youtubemusic;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.provider.MusicProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class YouTubeMusicProvider implements MusicProvider {
	private boolean connected = false;

	@Override
	public String getId() {
		return "youtubemusic";
	}

	@Override
	public String getDisplayName() {
		return "YouTube Music";
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void connect() {
		MusicallyMods.LOGGER.info("YouTube Music connect requested. This build is a scaffold; integrate a vetted library/OAuth flow carefully (no credential scraping).");
		connected = false;
	}

	@Override
	public void disconnect() {
		connected = false;
		MusicallyMods.LOGGER.info("YouTube Music disconnected (local state cleared in scaffold).");
	}

	@Override
	public CompletableFuture<List<TrackInfo>> search(String query, SearchType type) {
		return CompletableFuture.supplyAsync(() -> connected ? new ArrayList<>() : Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<TrackInfo>> getLibraryTracks() {
		return CompletableFuture.supplyAsync(() -> connected ? new ArrayList<>() : Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<PlaylistInfo>> getPlaylists() {
		return CompletableFuture.supplyAsync(() -> connected ? new ArrayList<>() : Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<TrackInfo>> getPlaylistTracks(String playlistId) {
		return CompletableFuture.supplyAsync(() -> connected ? new ArrayList<>() : Collections.emptyList());
	}

	@Override
	public Optional<TrackInfo> getCurrentTrack() {
		return Optional.empty();
	}

	@Override
	public PlaybackState getPlaybackState() {
		return new PlaybackState(false, false, "off", 0, 0);
	}

	@Override
	public void play() { MusicallyMods.LOGGER.debug("YTMusic play (stub)."); }

	@Override
	public void pause() { MusicallyMods.LOGGER.debug("YTMusic pause (stub)."); }

	@Override
	public void togglePlayPause() {
		if (getPlaybackState().playing()) pause(); else play();
	}

	@Override
	public void next() { MusicallyMods.LOGGER.debug("YTMusic next (stub)."); }

	@Override
	public void previous() { MusicallyMods.LOGGER.debug("YTMusic previous (stub)."); }

	@Override
	public void seek(long positionMs) { MusicallyMods.LOGGER.debug("YTMusic seek (stub)."); }

	@Override
	public void setShuffle(boolean enabled) { MusicallyMods.LOGGER.debug("YTMusic shuffle {} (stub).", enabled); }

	@Override
	public void setRepeat(String mode) { MusicallyMods.LOGGER.debug("YTMusic repeat {} (stub).", mode); }

	@Override
	public boolean supportsDirectPlayback() {
		return false;
	}

	@Override
	public String getCapabilitiesDescription() {
		return "YouTube Music scaffold: no official public API; any integration must use vetted open-source libraries with care for ToS/rate limits. Not fully implemented in this release build.";
	}
}
