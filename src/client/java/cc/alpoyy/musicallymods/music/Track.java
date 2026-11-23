package cc.alpoyy.musicallymods.music;

/** Immutable track/station representation used by every service and the UI. */
public record Track(
        String id,
        String title,
        String artist,
        String album,
        String artworkUrl,
        String streamUrl,
        long durationMs,
        String sourceId,
        String note,
        boolean playable
) {
    public Track {
        id = emptyIfNull(id);
        title = emptyIfNull(title);
        artist = emptyIfNull(artist);
        album = emptyIfNull(album);
        artworkUrl = emptyIfNull(artworkUrl);
        streamUrl = emptyIfNull(streamUrl);
        note = emptyIfNull(note);
        sourceId = emptyIfNull(sourceId);
        durationMs = Math.max(0L, durationMs);
    }

    public String displayTitle() {
        return title.isEmpty() ? "Untitled" : title;
    }

    public String displayArtist() {
        return artist.isEmpty() ? "Unknown artist" : artist;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
