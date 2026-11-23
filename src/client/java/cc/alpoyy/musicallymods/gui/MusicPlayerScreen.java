package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.audio.PlaybackSnapshot;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.lyrics.LyricsManager;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceManager;
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

/** Real player GUI: async search, service status, playback controls, artwork and lyrics status. */
public final class MusicPlayerScreen extends Screen {
    private static final int ROW_HEIGHT = 40;
    private static final int MARGIN = 12;

    private String query = "";
    private boolean editingQuery;
    private List<Track> results = List.of();
    private String status = "Choose a service, then search. Only free/key-less integrations are enabled.";
    private boolean searching;
    private long searchGeneration;
    private int scrollRow;
    private int selectedRow = -1;
    private Track lastTrack;

    private Ui.Rect searchRect;
    private Ui.Rect searchButtonRect;
    private Ui.Rect previousServiceRect;
    private Ui.Rect nextServiceRect;
    private Ui.Rect settingsRect;
    private Ui.Rect resultsRect;
    private Ui.Rect playPauseRect;
    private Ui.Rect stopRect;
    private Ui.Rect previousRect;
    private Ui.Rect nextRect;
    private Ui.Rect lyricsRect;
    private Ui.Rect volumeRect;
    private Ui.Rect volumeDownRect;
    private Ui.Rect volumeUpRect;
    private final List<Ui.Rect> rowRects = new ArrayList<>();

    public MusicPlayerScreen() {
        super(Component.translatable("screen.musicallymods.title"));
    }

    @Override
    protected void init() {
        int x = MARGIN;
        int right = width - MARGIN;
        previousServiceRect = new Ui.Rect(right - 176, 10, 22, 18);
        nextServiceRect = new Ui.Rect(right - 150, 10, 22, 18);
        settingsRect = new Ui.Rect(right - 82, 10, 82, 18);
        searchRect = new Ui.Rect(x, 32, Math.max(80, width - 222), 20);
        searchButtonRect = new Ui.Rect(searchRect.x() + searchRect.width() + 6, 32, 68, 20);
        int panelTop = 78;
        int playerHeight = Math.min(148, Math.max(120, height / 3));
        int panelBottom = Math.max(panelTop + 60, height - playerHeight - 8);
        resultsRect = new Ui.Rect(x, panelTop, width - MARGIN * 2, panelBottom - panelTop - 6);
        int py = height - playerHeight;
        int buttonY = py + 72;
        playPauseRect = new Ui.Rect(x + 80, buttonY, 68, 20);
        previousRect = new Ui.Rect(x + 152, buttonY, 20, 20);
        nextRect = new Ui.Rect(x + 176, buttonY, 20, 20);
        stopRect = new Ui.Rect(x + 200, buttonY, 44, 20);
        lyricsRect = new Ui.Rect(x + 248, buttonY, 62, 20);
        volumeDownRect = new Ui.Rect(x + 80, py + 100, 20, 16);
        volumeRect = new Ui.Rect(x + 104, py + 100, Math.max(40, width - MARGIN * 2 - 190), 16);
        volumeUpRect = new Ui.Rect(volumeRect.x() + volumeRect.width() + 4, py + 100, 20, 16);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ModConfig cfg = ModConfig.get();
        ModConfig.Colors c = cfg.colors;
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, c.mainBg);

        MusicService service = ServiceManager.active();
        Ui.label(graphics, font, "MusicallyMods", MARGIN, 12, c.textPrimary);
        Ui.label(graphics, font, "Service: " + Ui.fit(font, service.displayName(), 160), MARGIN + 96, 12, c.textSecondary);
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

        int lineY = 60;
        Ui.label(graphics, font, Ui.fit(font, status, width - MARGIN * 2), MARGIN, lineY, c.textSecondary);
        lineY += 10;
        List<String> note = Ui.wrap(font, service.capabilities().note(), width - MARGIN * 2 - 10, 2);
        if (!note.isEmpty()) Ui.label(graphics, font, Ui.fit(font, note.get(0), width - MARGIN * 2),
                MARGIN, lineY, c.textSecondary);

        Ui.panel(graphics, resultsRect, c.secondaryBg, c.accent);
        drawResults(graphics, mouseX, mouseY, c);
        drawPlayer(graphics, mouseX, mouseY, c);
    }

    private void drawResults(GuiGraphics graphics, int mouseX, int mouseY, ModConfig.Colors c) {
        rowRects.clear();
        int x = resultsRect.x() + 4;
        int y = resultsRect.y() + 4;
        int w = resultsRect.width() - 8;
        int visible = Math.max(1, (resultsRect.height() - 10) / ROW_HEIGHT);
        int start = Math.max(0, Math.min(scrollRow, Math.max(0, results.size() - visible)));
        scrollRow = start;
        if (results.isEmpty()) {
            String message = searching ? "Searching..." : "No results. Enter a query and press Search.";
            Ui.label(graphics, font, message, x + 4, y + 6, c.textSecondary);
            return;
        }
        for (int i = 0; i < visible && start + i < results.size(); i++) {
            int index = start + i;
            Track track = results.get(index);
            Ui.Rect row = new Ui.Rect(x, y + i * ROW_HEIGHT, w, ROW_HEIGHT - 3);
            rowRects.add(row);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean selected = index == selectedRow;
            int fill = selected ? c.controls : (hovered ? dimColor(c.secondaryBg) : c.secondaryBg);
            Ui.panel(graphics, row, fill, selected ? c.accent : 0);
            Ui.label(graphics, font, Ui.fit(font, (index + 1) + ". " + track.displayTitle(), w - 10),
                    row.x() + 4, row.y() + 3, c.textPrimary);
            Ui.label(graphics, font, Ui.fit(font, track.displayArtist() + "  |  " + track.note(), w - 10),
                    row.x() + 4, row.y() + 16, c.textSecondary);
            long duration = track.durationMs();
            String right = duration > 0 ? Ui.formatTime(duration) : "LIVE";
            graphics.drawString(font, right, row.x() + row.width() - font.width(right) - 5,
                    row.y() + 3, c.accent, false);
        }
    }

    private void drawPlayer(GuiGraphics graphics, int mouseX, int mouseY, ModConfig.Colors c) {
        PlaybackSnapshot snap = AudioManager.get().snapshot();
        int x = MARGIN;
        int w = width - MARGIN * 2;
        int panelHeight = Math.max(120, Math.min(148, height / 3));
        int py = height - panelHeight;
        Ui.panel(graphics, new Ui.Rect(x, py, w, panelHeight - 6), c.secondaryBg, c.accent);

        if (snap.hasTrack()) {
            ArtworkCache.request(snap.track());
            if (!ArtworkCache.has(snap.track())) {
                Ui.panel(graphics, new Ui.Rect(x + 8, py + 8, 64, 64), c.controls, c.accent);
                Ui.label(graphics, font, "Art", x + 30, py + 34, c.textSecondary);
            } else {
                Ui.panel(graphics, new Ui.Rect(x + 6, py + 6, 68, 68), c.controls, c.accent);
                ArtworkCache.render(graphics, snap.track(), x + 8, py + 8, 64, 64);
            }
        } else {
            Ui.panel(graphics, new Ui.Rect(x + 8, py + 8, 64, 64), c.controls, c.accent);
            Ui.label(graphics, font, "No art", x + 20, py + 34, c.textSecondary);
        }

        int tx = x + 80;
        int tw = w - 88;
        String title = snap.hasTrack() ? snap.track().displayTitle() : "Nothing playing";
        String artist = snap.hasTrack() ? snap.track().displayArtist() : "Play a result to begin";
        Ui.label(graphics, font, Ui.fit(font, title, tw), tx, py + 8, c.textPrimary);
        Ui.label(graphics, font, Ui.fit(font, artist, tw), tx, py + 22, c.textSecondary);
        String stateText = switch (snap.state()) {
            case LOADING -> "Loading...";
            case PLAYING -> "Playing";
            case PAUSED -> "Paused";
            case ERROR -> "Error";
            case STOPPED -> "Stopped";
        };
        Ui.label(graphics, font, stateText + "  |  " + Ui.fit(font, snap.track() == null ? "" : snap.track().note(), tw - 60),
                tx, py + 36, c.textSecondary);

        int barX = tx;
        int barY = py + 54;
        int barW = tw;
        graphics.fill(barX, barY, barX + barW, barY + 8, c.controls);
        if (snap.progress() > 0f) {
            graphics.fill(barX, barY, barX + (int) (barW * snap.progress()), barY + 8, c.progress);
        }
        String time = Ui.formatTime(snap.positionMs()) + (snap.durationMs() > 0 ? " / " + Ui.formatTime(snap.durationMs()) : " / LIVE");
        Ui.label(graphics, font, time, barX + barW - font.width(time), barY + 10, c.textSecondary);

        boolean hasTrack = snap.hasTrack();
        boolean canSkip = results.size() > 1;
        boolean canPause = ServiceManager.active().capabilities().pauseResume();
        Ui.button(graphics, font, playPauseRect, snap.state() == AudioManager.State.PAUSED ? "Play" : "Pause",
                c.controls, c.accent, c.textPrimary, hasTrack && canPause, playPauseRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, previousRect, "<", c.controls, c.accent, c.textPrimary,
                hasTrack && canSkip, previousRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, nextRect, ">", c.controls, c.accent, c.textPrimary,
                hasTrack && canSkip, nextRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, stopRect, "Stop", c.controls, c.accent, c.textPrimary,
                hasTrack, stopRect.contains(mouseX, mouseY));
        Ui.button(graphics, font, lyricsRect, ModConfig.get().lyrics.enabled ? "Lyrics ON" : "Lyrics OFF",
                c.controls, c.accent, c.textPrimary, true, lyricsRect.contains(mouseX, mouseY));

        Ui.button(graphics, font, volumeDownRect, "-", c.controls, c.accent, c.textPrimary,
                true, volumeDownRect.contains(mouseX, mouseY));
        graphics.fill(volumeRect.x(), volumeRect.y() + 6, volumeRect.x() + volumeRect.width(),
                volumeRect.y() + 10, c.controls);
        graphics.fill(volumeRect.x(), volumeRect.y() + 6,
                volumeRect.x() + (int) (volumeRect.width() * snap.volume()), volumeRect.y() + 10, c.progress);
        Ui.button(graphics, font, volumeUpRect, "+", c.controls, c.accent, c.textPrimary,
                true, volumeUpRect.contains(mouseX, mouseY));
        Ui.label(graphics, font, "Volume " + Math.round(snap.volume() * 100) + "%",
                volumeUpRect.x() + volumeUpRect.width() + 6, volumeRect.y(), c.textSecondary);

        if (snap.error() != null && !snap.error().isBlank()) {
            Ui.label(graphics, font, Ui.fit(font, "Error: " + snap.error(), w - 12),
                    x + 6, py + panelHeight - 16, c.accent);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        double mx = event.x();
        double my = event.y();
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
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(mx, my)) {
                int visibleRow = scrollRow + i;
                if (visibleRow >= 0 && visibleRow < results.size()) playResult(visibleRow);
                return true;
            }
        }
        PlaybackSnapshot snap = AudioManager.get().snapshot();
        if (playPauseRect.contains(mx, my)) {
            AudioManager.get().togglePlayPause();
            return true;
        }
        if (stopRect.contains(mx, my)) {
            AudioManager.get().stop();
            LyricsManager.get().clear();
            return true;
        }
        if (previousRect.contains(mx, my)) {
            AudioManager.get().previous();
            return true;
        }
        if (nextRect.contains(mx, my)) {
            AudioManager.get().next();
            return true;
        }
        if (lyricsRect.contains(mx, my)) {
            LyricsManager.get().toggleEnabled();
            return true;
        }
        if (volumeDownRect.contains(mx, my)) {
            AudioManager.get().setVolume(snap.volume() - 0.05f);
            return true;
        }
        if (volumeUpRect.contains(mx, my)) {
            AudioManager.get().setVolume(snap.volume() + 0.05f);
            return true;
        }
        if (volumeRect.contains(mx, my)) {
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
            scrollRow = Math.max(0, scrollRow - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN) {
            scrollRow++;
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
        if (resultsRect != null && resultsRect.contains(mouseX, mouseY)) {
            scrollRow = Math.max(0, scrollRow - (int) Math.signum(amountY));
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
            status = "Search unavailable: " + service.capabilities().note();
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
            status = results.size() + " results. Click a row to play.";
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
        AudioManager.get().stop();
        LyricsManager.get().clear();
        status = services.get(next).displayName() + ": " + services.get(next).statusText();
    }

    private static int dimColor(int color) {
        int r = ((color >> 16) & 0xFF) * 70 / 100;
        int g = ((color >> 8) & 0xFF) * 70 / 100;
        int b = (color & 0xFF) * 70 / 100;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
