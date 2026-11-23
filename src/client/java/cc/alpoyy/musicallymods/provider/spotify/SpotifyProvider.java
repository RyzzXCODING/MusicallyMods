package cc.alpoyy.musicallymods.provider.spotify;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.provider.MusicProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SpotifyProvider implements MusicProvider {
	private boolean connected = false;

	@Override
	public String getId() {
		return "spotify";
	}

	@Override
	public String getDisplayName() {
		return "Spotify";
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void connect() {
		// Real OAuth (Authorization Code + PKCE) should be implemented here.
		MusicallyMods.LOGGER.info("Spotify connect requested. This build ships a scaffold; implement OAuth + Web API calls.");
		connected = false;
	}

	@Override
	public void disconnect() {
		connected = false;
		MusicallyMods.LOGGER.info("Spotify disconnected (local state cleared in scaffold).");
	}

	@Override
	public CompletableFuture<List<TrackInfo>> search(String query, SearchType type) {
		return CompletableFuture.supplyAsync(() -> {
			if (!connected) return Collections.emptyList();
			return new ArrayList<>();
		});
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
	public void play() { MusicallyMods.LOGGER.debug("Spotify play (stub)."); }

	@Override
	public void pause() { MusicallyMods.LOGGER.debug("Spotify pause (stub)."); }

	@Override
	public void togglePlayPause() {
		if (getPlaybackState().playing()) pause(); else play();
	}

	@Override
	public void next() { MusicallyMods.LOGGER.debug("Spotify next (stub)."); }

	@Override
	public void previous() { MusicallyMods.LOGGER.debug("Spotify previous (stub)."); }

	@Override
	public void seek(long positionMs) { MusicallyMods.LOGGER.debug("Spotify seek (stub)."); }

	@Override
	public void setShuffle(boolean enabled) { MusicallyMods.LOGGER.debug("Spotify shuffle {} (stub).", enabled); }

	@Override
	public void setRepeat(String mode) { MusicallyMods.LOGGER.debug("Spotify repeat {} (stub).", mode); }

	@Override
	public boolean supportsDirectPlayback() {
		return false;
	}

	@Override
	public String getCapabilitiesDescription() {
		return "Spotify Web API scaffold: OAuth/remote control require your app credentials and an active Spotify Connect device. Not fully implemented in this release build.";
	}
}
