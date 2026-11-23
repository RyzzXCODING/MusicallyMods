package cc.alpoyy.musicallymods.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses standard LRC timestamp tags; metadata tags without numeric times are ignored. */
public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    private LrcParser() {
    }

    public static List<LyricLine> parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return List.of();
        List<LyricLine> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher matcher = TIMESTAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() != end) break;
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = 0;
                if (fraction != null) {
                    millis = switch (fraction.length()) {
                        case 1 -> Long.parseLong(fraction) * 100L;
                        case 2 -> Long.parseLong(fraction) * 10L;
                        default -> Long.parseLong(fraction.substring(0, 3));
                    };
                }
                times.add(minutes * 60_000L + seconds * 1000L + millis);
                end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long time : times) lines.add(new LyricLine(time, text));
        }
        lines.sort(Comparator.comparingLong(LyricLine::timeMs));
        return List.copyOf(lines);
    }
}
