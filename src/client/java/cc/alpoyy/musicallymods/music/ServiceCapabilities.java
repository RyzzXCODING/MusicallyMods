package cc.alpoyy.musicallymods.music;

/** Explicit capability declaration; the UI enables controls only for true values. */
public record ServiceCapabilities(
        boolean search,
        boolean metadata,
        boolean artwork,
        boolean playback,
        boolean pauseResume,
        boolean skip,
        boolean playlist,
        boolean lyrics,
        String note
) {
    public static final ServiceCapabilities NONE = new ServiceCapabilities(
            false, false, false, false, false, false, false, false, "Not integrated");

    public String summary() {
        StringBuilder b = new StringBuilder();
        b.append(search ? "Search" : "No search");
        b.append(metadata ? " · Metadata" : "");
        b.append(artwork ? " · Artwork" : "");
        b.append(playback ? " · Playback" : " · No playback");
        b.append(pauseResume ? " · Pause/Resume" : "");
        b.append(skip ? " · Skip" : "");
        b.append(playlist ? " · Playlists" : "");
        b.append(lyrics ? " · Lyrics lookup" : "");
        return b.toString();
    }
}
