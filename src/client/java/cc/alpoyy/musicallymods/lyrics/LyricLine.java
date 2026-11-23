package cc.alpoyy.musicallymods.lyrics;

/** One lyric line. {@code timeMs} is -1 when the source has no timing data. */
public record LyricLine(long timeMs, String text) {
    public LyricLine {
        text = text == null ? "" : text.trim();
    }

    public boolean timed() {
        return timeMs >= 0;
    }
}
