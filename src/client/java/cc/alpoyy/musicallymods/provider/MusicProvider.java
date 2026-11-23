package cc.alpoyy.musicallymods.provider;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MusicProvider {
	String getId();
	String getDisplayName();

	boolean isConnected();
	void connect();
	void disconnect();

	CompletableFuture<List<TrackInfo>> search(String query, SearchType type);
	CompletableFuture<List<TrackInfo>> getLibraryTracks();
	CompletableFuture<List<PlaylistInfo>> getPlaylists();
	CompletableFuture<List<TrackInfo>> getPlaylistTracks(String playlistId);

	Optional<TrackInfo> getCurrentTrack();
	PlaybackState getPlaybackState();

	void play();
	void pause();
	void togglePlayPause();
	void next();
	void previous();
	void seek(long positionMs);
	void setShuffle(boolean enabled);
	void setRepeat(String mode);

	default boolean supportsDirectPlayback() { return false; }
	default String getCapabilitiesDescription() { return "Basic controls"; }

	enum SearchType { TRACK, ARTIST, ALBUM, PLAYLIST }

	record TrackInfo(String id, String title, String artist, String album, String artworkUrl, long durationMs) {}
	record PlaylistInfo(String id, String name, String owner, String artworkUrl, int trackCount) {}
	record PlaybackState(boolean playing, boolean shuffle, String repeatMode, long positionMs, long durationMs) {}
}
