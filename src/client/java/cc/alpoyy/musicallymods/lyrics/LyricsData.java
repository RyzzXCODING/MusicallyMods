package cc.alpoyy.musicallymods.lyrics;

import java.util.List;

/** Lyrics for one track. Synchronization is true only when real timestamps were parsed. */
public record LyricsData(String title, String artist, boolean synced, List<LyricLine> lines,
                         String source, String error) {
    public LyricsData {
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        lines = lines == null ? List.of() : List.copyOf(lines);
        source = source == null ? "" : source;
        error = error == null ? "" : error;
    }

    public static LyricsData empty(String error) {
        return new LyricsData("", "", false, List.of(), "", error);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
