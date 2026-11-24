package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.audio.PlaybackSnapshot;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.lyrics.LyricsManager;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceManager;
import cc.alpoyy.musicallymods.music.ServiceStatus;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.ArtworkCache;
import cc.alpoyy.musicallymods.util.Errors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Responsive player GUI with a fixed header/player and an independently scrolling result list. */
public final class MusicPlayerScreen extends Screen {
    private static final String DEFAULT_STATUS = "Choose a service, then search. Only legal, key-less sources are enabled.";

    private String query = "";
    private boolean editingQuery;
    private List<Track> results = List.of();
    private String status = DEFAULT_STATUS;
    private boolean searching;
    private long searchGeneration;
    private int scrollRow;
    private int visibleRows = 1;
    private int selectedRow = -1;
    private Track lastTrack;
    private boolean showNote;
    private boolean compact;
    private boolean ultraCompact;
    private boolean showProgress;
    private boolean showVolume;
    private int titleY;
    private int artistY;
    private int stateY;
    private int barY;
    private int timeY;

    private Ui.Rect searchRect;
    private Ui.Rect searchButtonRect;
    private Ui.Rect previousServiceRect;
    private Ui.Rect nextServiceRect;
    private Ui.Rect settingsRect;
    private Ui.Rect resultsRect;
    private Ui.Rect resultsViewport;
    private Ui.Rect scrollbarRect;
    private Ui.Rect playerRect;
    private Ui.Rect artRect;
    private Ui.Rect playPauseRect;
    private Ui.Rect stopRect;
    private Ui.Rect previousRect;
    private Ui.Rect nextRect;
    private Ui.Rect lyricsRect;
    private Ui.Rect volumeRect;
    private Ui.Rect volumeDownRect;
    private Ui.Rect volumeUpRect;
    private final List<Ui.Rect> rowRects = new ArrayList<>();
    private final GuiNav nav = new GuiNav();

    public MusicPlayerScreen() {
        super(Component.translatable("screen.musicallymods.title"));
    }

    @Override
    protected void init() {
        int margin = width < 520 ? 8 : 12;
        int right = Math.max(margin, width - margin);
        int availableWidth = Math.max(0, right - margin);
        int headerY = 24;
        int headerHeight = 16;

        int navTotal = Math.min(124, Math.max(36, availableWidth));
        int settingsWidth = Math.max(24, Math.min(width < 520 ? 58 : 72, navTotal / 2));
        int arrowWidth = Math.max(12, Math.min(20, (navTotal - settingsWidth - 6) / 2));
        settingsRect = new Ui.Rect(right - settingsWidth, headerY, settingsWidth, headerHeight);
        nextServiceRect = new Ui.Rect(settingsRect.x() - 4 - arrowWidth, headerY, arrowWidth, headerHeight);
        previousServiceRect = new Ui.Rect(nextServiceRect.x() - 2 - arrowWidth, headerY, arrowWidth, headerHeight);
        previousServiceRect = previousServiceRect.intersection(new Ui.Rect(0, 0, width, height));
        nextServiceRect = nextServiceRect.intersection(new Ui.Rect(0, 0, width, height));
        settingsRect = settingsRect.intersection(new Ui.Rect(0, 0, width, height));

        int searchY = 44;
        int searchHeight = 20;
        int buttonWidth = Math.max(40, Math.min(width < 520 ? 58 : 68, availableWidth / 3));
        int inputWidth = Math.max(0, availableWidth - buttonWidth - 6);
        searchRect = new Ui.Rect(margin, searchY, inputWidth, searchHeight);
        searchButtonRect = new Ui.Rect(searchRect.right() + 6, searchY, buttonWidth, searchHeight);

        showNote = height >= 330;
        int listTop = showNote ? 92 : (height >= 280 ? 74 : 66);
        int minListHeight = height >= 260 ? 36 : 0;
        int maxPlayerHeight = Math.max(80, Math.min(148, height / 3));
        int playerHeight = Math.min(maxPlayerHeight, Math.max(60, height - listTop - minListHeight - 8));
        int playerY = Math.max(listTop, height - playerHeight - 4);
        playerHeight = Math.max(0, Math.min(playerHeight, height - playerY - 4));
        int listBottom = Math.max(listTop, playerY - 6);
        resultsRect = new Ui.Rect(margin, listTop, availableWidth, Math.max(0, listBottom - listTop));
        int scrollbarWidth = ModConfig.get().ui.showScrollbars && resultsRect.width() > 80 ? 6 : 0;
        resultsViewport = new Ui.Rect(resultsRect.x() + 4, resultsRect.y() + 4,
                Math.max(0, resultsRect.width() - 8 - scrollbarWidth),
                Math.max(0, resultsRect.height() - 8));
        scrollbarRect = new Ui.Rect(resultsRect.right() - scrollbarWidth - 2, resultsRect.y() + 4,
                Math.max(0, scrollbarWidth - 2), resultsViewport.height());

        playerRect = new Ui.Rect(margin, playerY, availableWidth, playerHeight);
        ultraCompact = playerRect.height() < 84;
        compact = width < 520 || playerRect.height() < 128;
        int artSize = !compact && playerRect.height() >= 110 ? 64 : 0;
        artRect = new Ui.Rect(playerRect.x() + 6, playerRect.y() + 6, artSize, artSize);
        int textX = playerRect.x() + 6 + (artSize > 0 ? artSize + 8 : 0);
        int textRight = playerRect.right() - 6;

        if (ultraCompact) {
            titleY = playerRect.y() + 2;
            artistY = playerRect.y() + 15;
            stateY = artistY;
            barY = artistY;
            timeY = artistY;
            showProgress = false;
            showVolume = false;
        } else {
            int top = playerRect.height() >= 130 ? 12 : 6;
            titleY = playerRect.y() + top;
            artistY = titleY + 14;
            stateY = artistY + 14;
            barY = stateY + 14;
            timeY = barY + 7;
            showProgress = true;
            showVolume = true;
        }

        int buttonY = barY + (ultraCompact ? 14 : 20);
        if (!ultraCompact && buttonY + 18 > playerRect.bottom() - 2) {
            buttonY = Math.max(playerRect.y() + 4, playerRect.bottom() - 20);
            showProgress = buttonY > timeY + 8;
        }
        int buttonHeight = ultraCompact ? 16 : 18;
        int[] cursor = {textX};
        playPauseRect = place(cursor, buttonY, 56, buttonHeight, textRight, 4);
        stopRect = place(cursor, buttonY, 42, buttonHeight, textRight, 4);
        lyricsRect = place(cursor, buttonY, compact ? 54 : 68, buttonHeight, textRight, 4);
        previousRect = place(cursor, buttonY, 20, buttonHeight, textRight, 4);
        nextRect = place(cursor, buttonY, 20, buttonHeight, textRight, 4);

        int volumeY = buttonY + buttonHeight + 6;
        showVolume = showVolume && !ultraCompact && volumeY + 16 <= playerRect.bottom() - 2
                && textRight - textX >= 70;
        int stepWidth = 18;
        if (showVolume) {
            volumeDownRect = new Ui.Rect(textX, volumeY, stepWidth, 16);
            int volumeStart = volumeDownRect.right() + 4;
            int volumeWidth = Math.max(24, textRight - (compact ? 0 : 64) - volumeStart - stepWidth - 4);
            volumeRect = new Ui.Rect(volumeStart, volumeY, volumeWidth, 16);
            volumeUpRect = new Ui.Rect(volumeRect.right() + 4, volumeY, stepWidth, 16);
        } else {
            volumeDownRect = new Ui.Rect(0, 0, 0, 0);
            volumeRect = new Ui.Rect(0, 0, 0, 0);
            volumeUpRect = new Ui.Rect(0, 0, 0, 0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ModConfig.Colors c = ModConfig.get().colors;
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, c.mainBg);

        nav.render(graphics, font, 4, 2, Math.max(0, width - 8), 18, width, height,
                GuiNav.Tab.HOME, c, mouseX, mouseY);

        MusicService service = ServiceManager.active();
        ServiceStatus serviceStatus = service.status();
        Ui.label(graphics, font, "MusicallyMods", Math.max(8, width < 520 ? 8 : 12), 27, c.textPrimary);
        if (width >= 560) {
            int labelX = (width < 520 ? 8 : 12) + font.width("MusicallyMods") + 12;
            Ui.label(graphics, font, Ui.fit(font, service.displayName(),
                    previousServiceRect.x() - labelX - 8), labelX, 27, c.textSecondary);
        }
        Ui.button(graphics, font, previousServiceRect, "<", c.controls, c.accent, c.textPrimary, true,
                previousServiceRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, nextServiceRect, ">", c.controls, c.accent, c.textPrimary, true,
                nextServiceRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, settingsRect, "Settings", c.controls, c.accent, c.textPrimary, true,
                settingsRect.contains(mouseX, mouseY));

        Ui.panel(graphics, searchRect, c.secondaryBg, c.accent);
        String shownQuery = editingQuery ? query + "_" : query.isEmpty() ? "Search..." : query;
        Ui.label(graphics, font, Ui.fit(font, shownQuery, searchRect.width() - 10),
                searchRect.x() + 5, searchRect.y() + 5, editingQuery ? c.textPrimary : c.textSecondary);
        Ui.button(graphics, font, searchButtonRect, searching ? "..." : "Search", c.controls, c.accent,
                c.textPrimary, !searching, searchButtonRect.contains(mouseX, mouseY));

        int left = resultsRect.x();
        int lineHeight = font.lineHeight;
        int statusY = searchRect.bottom() + 2;
        int noteY = statusY + lineHeight;
        if (statusY + lineHeight <= resultsRect.y()) {
            Ui.label(graphics, font, Ui.fit(font, service.displayName() + ": " + status,
                    resultsRect.width()), left, statusY, c.textSecondary);
        }
        if (showNote && noteY + lineHeight <= resultsRect.y()) {
            Ui.label(graphics, font, Ui.fit(font, serviceStatus.line(), resultsRect.width()),
                    left, noteY, c.textSecondary);
        }

        Ui.panel(graphics, resultsRect, c.secondaryBg, c.accent);
        drawResults(graphics, mouseX, mouseY, c);
        drawPlayer(graphics, mouseX, mouseY, c);
    }

    private void drawResults(GuiGraphics graphics, int mouseX, int mouseY, ModConfig.Colors c) {
        rowRects.clear();
        int rowHeight = Math.max(32, Math.min(64, ModConfig.get().ui.resultRowHeight));
        visibleRows = Math.max(1, resultsViewport.height() / rowHeight);
        scrollRow = Ui.clampScroll(scrollRow, maxScroll());
        if (selectedRow >= results.size()) selectedRow = results.size() - 1;

        try (Ui.Scissor ignored = Ui.scissor(graphics, resultsViewport)) {
            if (results.isEmpty()) {
                String message = searching ? "Searching..." : "No results. Enter a query and press Search.";
                Ui.label(graphics, font, message, resultsViewport.x() + 4, resultsViewport.y() + 6, c.textSecondary);
            } else {
                int textWidth = resultsViewport.width() - 10;
                for (int i = 0; i < visibleRows && scrollRow + i < results.size(); i++) {
                    int index = scrollRow + i;
                    Track track = results.get(index);
                    Ui.Rect row = new Ui.Rect(resultsViewport.x(), resultsViewport.y() + i * rowHeight,
                            resultsViewport.width(), Math.max(1, rowHeight - 3));
                    if (!row.intersects(resultsViewport)) continue;
                    rowRects.add(row);
                    boolean hovered = row.contains(mouseX, mouseY);
                    boolean selected = index == selectedRow;
                    int fill = selected ? c.controls : (hovered ? dimColor(c.secondaryBg) : c.secondaryBg);
                    Ui.panel(graphics, row, fill, selected ? c.accent : 0);
                    Ui.label(graphics, font, Ui.fit(font, (index + 1) + ". " + track.displayTitle(), textWidth - 44),
                            row.x() + 4, row.y() + 3, c.textPrimary);
                    Ui.label(graphics, font, Ui.fit(font, track.displayArtist() + "  |  " + track.note(), textWidth),
                            row.x() + 4, row.y() + 16, c.textSecondary);
                    String right = track.durationMs() > 0 ? Ui.formatTime(track.durationMs()) : "LIVE";
                    graphics.drawString(font, right, row.right() - font.width(right) - 4,
                            row.y() + 3, c.accent, false);
                }
            }
        }

        if (ModConfig.get().ui.showScrollbars) {
            Ui.scrollbar(graphics, scrollbarRect, scrollRow, maxScroll(), visibleRows, results.size(),
                    dimColor(c.controls), c.accent);
        }
    }

    private void drawPlayer(GuiGraphics graphics, int mouseX, int mouseY, ModConfig.Colors c) {
        PlaybackSnapshot snap = AudioManager.get().snapshot();
        Ui.panel(graphics, playerRect, c.secondaryBg, c.accent);
        try (Ui.Scissor ignored = Ui.scissor(graphics, playerRect)) {
            int artSize = artRect.width();
            if (artSize > 0) {
                if (snap.hasTrack() && !snap.track().artworkUrl().isBlank()) {
                    ArtworkCache.request(snap.track());
                    Ui.panel(graphics, artRect, c.controls, c.accent);
                    if (ArtworkCache.has(snap.track())) {
                        ArtworkCache.render(graphics, snap.track(), artRect.x(), artRect.y(), artSize, artSize);
                    } else {
                        Ui.label(graphics, font, "Art", artRect.x() + artSize / 2 - font.width("Art") / 2,
                                artRect.y() + artSize / 2 - 4, c.textSecondary);
                    }
                } else {
                    Ui.panel(graphics, artRect, c.controls, c.accent);
                    Ui.label(graphics, font, "No art", artRect.x() + 8, artRect.y() + artSize / 2 - 4, c.textSecondary);
                }
            }

            int textX = playPauseRect.width() > 0 ? playPauseRect.x() : playerRect.x() + 6;
            int textWidth = Math.max(0, playerRect.right() - 6 - textX);
            String title = snap.hasTrack() ? snap.track().displayTitle() : "Nothing playing";
            String artist = snap.hasTrack() ? snap.track().displayArtist() : "Play a result to begin";
            Ui.label(graphics, font, Ui.fit(font, title, textWidth), textX, titleY, c.textPrimary);
            Ui.label(graphics, font, Ui.fit(font, artist, textWidth), textX, artistY, c.textSecondary);

            if (!ultraCompact) {
                String note = snap.hasTrack() ? snap.track().note() : "";
                boolean failed = snap.hasError();
                String stateText = failed ? "Error: " + snap.error()
                        : snap.stateLabel() + (note.isBlank() ? "" : "  |  " + note);
                Ui.label(graphics, font, Ui.fit(font, stateText, textWidth),
                        textX, stateY, failed ? c.accent : c.textSecondary);
            }
            if (showProgress) {
                graphics.fill(textX, barY, textX + textWidth, barY + 6, c.controls);
                if (snap.progress() > 0f) {
                    graphics.fill(textX, barY, textX + (int) (textWidth * snap.progress()), barY + 6, c.progress);
                }
                String time = Ui.formatTime(snap.positionMs())
                        + (snap.durationMs() > 0 ? " / " + Ui.formatTime(snap.durationMs()) : " / LIVE");
                Ui.label(graphics, font, Ui.fit(font, time, textWidth), textX, timeY, c.textSecondary);
            }

            String playLabel = playLabel(snap);
            boolean playEnabled = snap.hasTrack() && switch (snap.state()) {
                case PLAYING, BUFFERING, PAUSED -> true;
                case FAILED -> snap.canRetry();
                case LOADING -> false;
                default -> true;
            };
            drawButton(graphics, mouseX, mouseY, c, playPauseRect, playLabel, playEnabled);
            boolean canSkip = results.size() > 1;
            drawButton(graphics, mouseX, mouseY, c, previousRect, "<", canSkip);
            drawButton(graphics, mouseX, mouseY, c, nextRect, ">", canSkip);
            drawButton(graphics, mouseX, mouseY, c, stopRect, "Stop", snap.hasTrack());
            drawButton(graphics, mouseX, mouseY, c, lyricsRect, compact ? "Lyrics" :
                    (ModConfig.get().lyrics.enabled ? "Lyrics ON" : "Lyrics OFF"), true);

            if (showVolume) {
                drawButton(graphics, mouseX, mouseY, c, volumeDownRect, "-", true);
                graphics.fill(volumeRect.x(), volumeRect.y() + 6, volumeRect.right(), volumeRect.y() + 10, c.controls);
                graphics.fill(volumeRect.x(), volumeRect.y() + 6,
                        volumeRect.x() + (int) (volumeRect.width() * snap.volume()), volumeRect.y() + 10, c.progress);
                drawButton(graphics, mouseX, mouseY, c, volumeUpRect, "+", true);
            }

        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        double mx = event.x();
        double my = event.y();
        GuiNav.Tab tab = nav.clicked(mx, my);
        if (tab != null) {
            GuiNav.open(minecraft, tab, this);
            return true;
        }
        editingQuery = searchRect.contains(mx, my);
        if (previousServiceRect.contains(mx, my)) {
            cycleService(-1);
            return true;
        }
        if (nextServiceRect.contains(mx, my)) {
            cycleService(1);
            return true;
        }
        if (settingsRect.contains(mx, my)) {
            minecraft.setScreen(new MusicSettingsScreen(this));
            return true;
        }
        if (searchButtonRect.contains(mx, my)) {
            doSearch();
            return true;
        }
        if (resultsViewport.contains(mx, my)) {
            for (int i = 0; i < rowRects.size(); i++) {
                if (rowRects.get(i).contains(mx, my)) {
                    playResult(scrollRow + i);
                    return true;
                }
            }
            return true;
        }
        if (containsPlayer(playPauseRect, mx, my)) {
            AudioManager.get().togglePlayPause();
            return true;
        }
        if (containsPlayer(stopRect, mx, my)) {
            AudioManager.get().stop();
            LyricsManager.get().clear();
            return true;
        }
        if (containsPlayer(previousRect, mx, my)) {
            AudioManager.get().previous();
            return true;
        }
        if (containsPlayer(nextRect, mx, my)) {
            AudioManager.get().next();
            return true;
        }
        if (containsPlayer(lyricsRect, mx, my)) {
            LyricsManager.get().toggleEnabled();
            return true;
        }
        if (containsPlayer(volumeDownRect, mx, my)) {
            AudioManager.get().setVolume(AudioManager.get().volume() - 0.05f);
            return true;
        }
        if (containsPlayer(volumeUpRect, mx, my)) {
            AudioManager.get().setVolume(AudioManager.get().volume() + 0.05f);
            return true;
        }
        if (containsPlayer(volumeRect, mx, my)) {
            float fraction = (float) ((mx - volumeRect.x()) / (double) volumeRect.width());
            AudioManager.get().setVolume(fraction);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingQuery) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                doSearch();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingQuery = false;
                return true;
            }
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            AudioManager.get().togglePlayPause();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scrollRow = Ui.clampScroll(scrollRow - visibleRows, maxScroll());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollRow = Ui.clampScroll(scrollRow + visibleRows, maxScroll());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && selectedRow >= 0 && selectedRow < results.size()) {
            playResult(selectedRow);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingQuery) {
            int codepoint = event.codepoint();
            if (!Character.isISOControl(codepoint) && query.length() < 160) {
                query += new String(Character.toChars(codepoint));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (resultsViewport != null && resultsViewport.contains(mouseX, mouseY)) {
            scrollRow = Ui.clampScroll(scrollRow - (int) Math.signum(amountY) * 3, maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        PlaybackSnapshot snap = AudioManager.get().snapshot();
        Track track = snap.track();
        if (track != lastTrack) {
            lastTrack = track;
            LyricsManager.get().setTrack(track);
            if (track != null) ArtworkCache.request(track);
        }
        scrollRow = Ui.clampScroll(scrollRow, maxScroll());
        if (selectedRow >= results.size()) selectedRow = results.isEmpty() ? -1 : results.size() - 1;
    }

    @Override
    public void onClose() {
        ModConfig.saveIfDirty();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void doSearch() {
        MusicService service = ServiceManager.active();
        if (!service.capabilities().search()) {
            status = "Search unavailable: " + service.status().detail();
            return;
        }
        if (query.isBlank()) {
            status = "Enter a query first.";
            return;
        }
        searching = true;
        selectedRow = -1;
        scrollRow = 0;
        results = List.of();
        status = "Searching " + service.displayName() + "...";
        long gen = ++searchGeneration;
        ServiceManager.search(query).whenComplete((result, throwable) -> minecraft.execute(() -> {
            if (gen != searchGeneration) return;
            searching = false;
            if (throwable != null) {
                status = "Search failed: " + Errors.describe(throwable);
                return;
            }
            if (result == null || !result.successful()) {
                status = result == null ? "Search failed." : result.error();
                return;
            }
            results = result.value() == null ? List.of() : result.value();
            if (results.isEmpty()) {
                status = "No results found.";
                return;
            }
            AudioManager.get().setQueue(results);
            status = results.size() + " results. Click a row or press Enter to play.";
        }));
    }

    private void playResult(int index) {
        if (index < 0 || index >= results.size()) return;
        selectedRow = index;
        Track track = results.get(index);
        AudioManager.get().playIndex(index);
        LyricsManager.get().setTrack(track);
        ArtworkCache.request(track);
        status = "Selected: " + track.displayTitle();
    }

    private void cycleService(int direction) {
        List<MusicService> services = ServiceManager.all();
        int index = 0;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).id().equals(ServiceManager.active().id())) {
                index = i;
                break;
            }
        }
        int next = (index + direction + services.size()) % services.size();
        ServiceManager.select(services.get(next).id());
        results = List.of();
        selectedRow = -1;
        scrollRow = 0;
        AudioManager.get().clear();
        LyricsManager.get().clear();
        status = services.get(next).status().line();
    }

    private void moveSelection(int direction) {
        if (results.isEmpty()) return;
        selectedRow = Math.max(0, Math.min(results.size() - 1,
                (selectedRow < 0 ? (direction > 0 ? 0 : results.size() - 1) : selectedRow + direction)));
        if (selectedRow < scrollRow) scrollRow = selectedRow;
        if (selectedRow >= scrollRow + visibleRows) scrollRow = selectedRow - visibleRows + 1;
        scrollRow = Ui.clampScroll(scrollRow, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, results.size() - Math.max(1, visibleRows));
    }

    private static String playLabel(PlaybackSnapshot snap) {
        return switch (snap.state()) {
            case PAUSED -> "Play";
            case PLAYING, BUFFERING -> "Pause";
            case LOADING -> "Loading";
            case FAILED -> snap.canRetry()
                    ? "Retry " + (snap.maxRetryAttempts() - snap.retryAttempts()) + " left"
                    : "No retries";
            default -> "Play";
        };
    }

    /** Places a button inside the available width, returning an empty rect when it will not fit. */
    private static Ui.Rect place(int[] cursor, int y, int width, int height, int right, int gap) {
        int x = cursor[0];
        if (x + gap + width > right) return new Ui.Rect(0, 0, 0, 0);
        cursor[0] = x + gap + width;
        return new Ui.Rect(x + gap, y, width, height);
    }

    private boolean containsPlayer(Ui.Rect rect, double mouseX, double mouseY) {
        return rect.intersection(playerRect).contains(mouseX, mouseY);
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY, ModConfig.Colors c,
                            Ui.Rect rect, String label, boolean enabled) {
        Ui.Rect visible = rect.intersection(playerRect);
        if (visible.width() <= 0 || visible.height() <= 0) return;
        Ui.button(graphics, font, visible, label, c.controls, c.accent, c.textPrimary,
                enabled, visible.contains(mouseX, mouseY));
    }

    private static int dimColor(int color) {
        int r = ((color >> 16) & 0xFF) * 70 / 100;
        int g = ((color >> 8) & 0xFF) * 70 / 100;
        int b = (color & 0xFF) * 70 / 100;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
