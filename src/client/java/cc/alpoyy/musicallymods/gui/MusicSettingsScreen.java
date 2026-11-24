package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceManager;
import cc.alpoyy.musicallymods.music.ServiceStatus;
import cc.alpoyy.musicallymods.music.impl.YtDlpManager;
import cc.alpoyy.musicallymods.util.Errors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Settings GUI with a stable header/footer and independently scrolling pages. */
public final class MusicSettingsScreen extends Screen {
    private static final String[] PAGE_NAMES = {"Appearance", "Lyrics", "Services", "Playback"};
    private static final String[] COLOR_LABELS = {
            "Main background", "Panel background", "Accent", "Primary text", "Secondary text",
            "Progress", "Controls", "Lyrics background", "Lyrics text", "Lyrics highlight"
    };
    private static final String[] POSITIONS = {
            "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "CENTER_LEFT", "CENTER", "CENTER_RIGHT",
            "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT", "MANUAL"
    };
    private static final String[] ALIGNMENTS = {"LEFT", "CENTER"};
    private static final int MARGIN = 12;
    private static final int SCROLL_STEP = 48;

    private final Screen parent;
    private final GuiNav nav = new GuiNav();
    private final List<Action> actions = new ArrayList<>();
    private final int[] scrollByPage = new int[PAGE_NAMES.length];
    private final int[] contentHeights = new int[PAGE_NAMES.length];
    private int page;
    private int selectedColor = -1;
    private String colorText = "";
    private boolean editingColor;
    private boolean confirmReset;
    private String status = "Changes are saved automatically to config/musicallymods.json.";
    private String ytDlpDetection;
    private Ui.Rect viewport;
    private Ui.Rect scrollbarRect;

    private record Action(Ui.Rect rect, Runnable onClick) {
    }

    public MusicSettingsScreen(Screen parent) {
        this(parent, 0);
    }

    public MusicSettingsScreen(Screen parent, int initialPage) {
        super(Component.translatable("screen.musicallymods.settings"));
        this.parent = parent;
        this.page = Math.max(0, Math.min(PAGE_NAMES.length - 1, initialPage));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ModConfig cfg = ModConfig.get();
        ModConfig.Colors c = cfg.colors;
        actions.clear();
        graphics.fill(0, 0, width, height, c.mainBg);

        nav.render(graphics, font, 4, 2, Math.max(0, width - 8), 18, width, height,
                page == 1 ? GuiNav.Tab.LYRICS : GuiNav.Tab.SETTINGS, c, mouseX, mouseY);

        String title = width < 700 ? "Settings" : "MusicallyMods Settings";
        Ui.label(graphics, font, title, MARGIN, 24, c.textPrimary);
        int doneWidth = width < 700 ? 58 : 70;
        Ui.Rect doneRect = new Ui.Rect(width - MARGIN - doneWidth, 22, doneWidth, 20)
                .intersection(new Ui.Rect(0, 0, width, height));
        Ui.button(graphics, font, doneRect, "Done", c.controls, c.accent, c.textPrimary, true,
                doneRect.contains(mouseX, mouseY));
        actions.add(new Action(doneRect, this::closeToParent));

        int navY = 46;
        int navStart = MARGIN;
        int navRight = Math.max(navStart, width - MARGIN);
        int contentTop = 72;
        boolean compactHeader = width < 700;
        int gap = 4;
        int availableNav = Math.max(0, navRight - navStart - gap * (PAGE_NAMES.length - 1));
        int navWidth = compactHeader
                ? Math.max(24, Math.min(64, availableNav / PAGE_NAMES.length))
                : Math.min(86, Math.max(48, availableNav / PAGE_NAMES.length));
        String[] names = PAGE_NAMES;
        if (compactHeader && navWidth < 62) names = new String[]{"UI", "Lyrics", "Svc", "Play"};
        for (int i = 0; i < names.length; i++) {
            final int pageIndex = i;
            Ui.Rect rect = new Ui.Rect(navStart + i * (navWidth + gap), navY, navWidth, 20)
                    .intersection(new Ui.Rect(0, 0, width, height));
            boolean active = i == page;
            Ui.button(graphics, font, rect, names[i], active ? c.controls : c.mainBg, c.accent, c.textPrimary,
                    true, rect.contains(mouseX, mouseY));
            actions.add(new Action(rect, () -> {
                page = pageIndex;
                editingColor = false;
            }));
        }

        Ui.Rect contentPanel = new Ui.Rect(MARGIN, contentTop,
                Math.max(0, width - MARGIN * 2), Math.max(0, height - contentTop - 40));
        Ui.panel(graphics, contentPanel, c.secondaryBg, c.accent);
        int scrollWidth = ModConfig.get().ui.showScrollbars && contentPanel.width() > 100 ? 6 : 0;
        viewport = new Ui.Rect(contentPanel.x() + 4, contentPanel.y() + 4,
                Math.max(0, contentPanel.width() - 8 - scrollWidth),
                Math.max(0, contentPanel.height() - 8));
        scrollbarRect = new Ui.Rect(contentPanel.right() - scrollWidth - 2, viewport.y(),
                Math.max(0, scrollWidth - 2), viewport.height());

        scrollByPage[page] = Ui.clampScroll(scrollByPage[page],
                Math.max(0, contentHeights[page] - viewport.height()));
        try (Ui.Scissor ignored = Ui.scissor(graphics, viewport)) {
            if (page == 0) renderAppearance(graphics, cfg, c, mouseX, mouseY);
            else if (page == 1) renderLyrics(graphics, cfg, c, mouseX, mouseY);
            else if (page == 2) renderServices(graphics, cfg, c, mouseX, mouseY);
            else renderPlayback(graphics, cfg, c, mouseX, mouseY);
        }
        scrollByPage[page] = Ui.clampScroll(scrollByPage[page],
                Math.max(0, contentHeights[page] - viewport.height()));
        if (ModConfig.get().ui.showScrollbars) {
            Ui.scrollbar(graphics, scrollbarRect, scrollByPage[page],
                    Math.max(0, contentHeights[page] - viewport.height()), viewport.height(),
                    contentHeights[page], dimColor(c.controls), c.accent);
        }

        int footerY = height - 28;
        Ui.label(graphics, font, Ui.fit(font, status, Math.max(0, width - MARGIN * 2 - 86)),
                MARGIN, footerY, c.textSecondary);
        Ui.Rect resetRect = new Ui.Rect(width - MARGIN - 74, footerY - 2, 74, 20)
                .intersection(new Ui.Rect(0, 0, width, height));
        Ui.button(graphics, font, resetRect, confirmReset ? "Confirm?" : "Reset", c.controls, c.accent,
                c.textPrimary, true, resetRect.contains(mouseX, mouseY));
        actions.add(new Action(resetRect, this::resetConfig));
    }

    private void renderAppearance(GuiGraphics graphics, ModConfig cfg, ModConfig.Colors c,
                                  int mouseX, int mouseY) {
        int y = 4;
        int x = viewport.x() + 4;
        drawLabel(graphics, x, y, "Theme preset", c.textPrimary);
        y += 14;
        String[] themes = {"DARK", "BLACK", "GRAY"};
        for (int i = 0; i < themes.length; i++) {
            final int pageIndex = i;
            addButton(graphics, x + i * 90, y, 86, 20, themes[i], true, () -> {
                ModConfig.applyTheme(themes[pageIndex]);
                status = "Theme applied: " + themes[pageIndex] + ". Individual colors remain editable.";
            }, c, mouseX, mouseY);
        }
        y += 28;

        drawLabel(graphics, x, y, "Colors (Edit, type #AARRGGBB, Apply)", c.textPrimary);
        y += 14;
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            int value = colorValue(i);
            int sy = screenY(y);
            graphics.fill(x, sy + 1, x + 18, sy + 17, value);
            graphics.renderOutline(x, sy + 1, 18, 16, c.accent);
            drawLabel(graphics, x + 24, y, COLOR_LABELS[i], c.textSecondary);
            drawLabel(graphics, x + 168, y, Ui.colorLabel(value), c.textPrimary);
            final int index = i;
            addButton(graphics, x + 268, y, 52, 20, "Edit", true, () -> {
                selectedColor = index;
                colorText = Ui.colorLabel(colorValue(index));
                editingColor = true;
                status = "Editing " + COLOR_LABELS[index] + ".";
            }, c, mouseX, mouseY);
            y += 22;

            if (selectedColor == i) {
                int ey = y;
                Ui.Rect panel = rect(x - 2, ey - 2, Math.min(viewport.width() - 8, 360), 74);
                if (panel.intersects(viewport)) Ui.panel(graphics, panel, c.controls, c.accent);
                drawLabel(graphics, x, ey + 2, COLOR_LABELS[i], c.textPrimary);
                Ui.Rect field = rect(x, ey + 20, 150, 20).intersection(viewport);
                if (field.width() > 0 && field.height() > 0) {
                    Ui.panel(graphics, field, c.mainBg, c.accent);
                    Ui.label(graphics, font, editingColor ? colorText + "_" : colorText,
                            field.x() + 4, field.y() + 5, c.textPrimary);
                    actions.add(new Action(field, () -> editingColor = true));
                }
                addButton(graphics, x + 158, ey + 20, 60, 20, "Apply", true,
                        this::applyColor, c, mouseX, mouseY);
                addButton(graphics, x + 222, ey + 20, 60, 20, "Cancel", true,
                        () -> {
                            selectedColor = -1;
                            editingColor = false;
                        }, c, mouseX, mouseY);
                y += 80;
            }
        }
        y += 8;
        contentHeights[0] = y;
    }

    private void renderLyrics(GuiGraphics graphics, ModConfig cfg, ModConfig.Colors c,
                              int mouseX, int mouseY) {
        ModConfig.Lyrics l = cfg.lyrics;
        int x = viewport.x() + 4;
        int w = Math.max(80, viewport.width() - 8);
        int y = 4;

        addToggle(graphics, x, y, 80, "Enabled", l.enabled, () -> l.enabled = !l.enabled, c, mouseX, mouseY);
        y += 26;
        step(graphics, x, y, w, "Position", l.position,
                () -> cycleString(value -> l.position = value, POSITIONS, l.position, -1),
                () -> cycleString(value -> l.position = value, POSITIONS, l.position, 1), c, mouseX, mouseY);
        y += 24;
        step(graphics, x, y, w, "Alignment", l.alignment,
                () -> cycleString(value -> l.alignment = value, ALIGNMENTS, l.alignment, -1),
                () -> cycleString(value -> l.alignment = value, ALIGNMENTS, l.alignment, 1), c, mouseX, mouseY);
        y += 26;
        addToggle(graphics, x, y, 84, "Show title", l.showTitle, () -> l.showTitle = !l.showTitle, c, mouseX, mouseY);
        addToggle(graphics, x + 90, y, 96, "Show artist", l.showArtist, () -> l.showArtist = !l.showArtist, c, mouseX, mouseY);
        addToggle(graphics, x + 192, y, 104, "Show art", l.showArtwork, () -> l.showArtwork = !l.showArtwork, c, mouseX, mouseY);
        y += 28;

        stepNumber(graphics, x, y, w, "Font size", String.format("%.2f", l.fontSize),
                () -> l.fontSize = clamp(l.fontSize - 0.1f, 0.7f, 2f),
                () -> l.fontSize = clamp(l.fontSize + 0.1f, 0.7f, 2f), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Scale", String.format("%.2f", l.scale),
                () -> l.scale = clamp(l.scale - 0.1f, 0.5f, 2f),
                () -> l.scale = clamp(l.scale + 0.1f, 0.5f, 2f), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Width", l.width + " px",
                () -> l.width = clamp(l.width - 20, 120, 1400),
                () -> l.width = clamp(l.width + 20, 120, 1400), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Height", l.height + " px",
                () -> l.height = clamp(l.height - 10, 40, 900),
                () -> l.height = clamp(l.height + 10, 40, 900), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Max lines", String.valueOf(l.maxLines),
                () -> l.maxLines = clamp(l.maxLines - 1, 1, 20),
                () -> l.maxLines = clamp(l.maxLines + 1, 1, 20), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Padding", l.padding + " px",
                () -> l.padding = clamp(l.padding - 2, 0, 40),
                () -> l.padding = clamp(l.padding + 2, 0, 40), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Line spacing", l.lineSpacing + " px",
                () -> l.lineSpacing = clamp(l.lineSpacing - 1, 0, 16),
                () -> l.lineSpacing = clamp(l.lineSpacing + 1, 0, 16), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Transparency", l.transparency + "/255",
                () -> l.transparency = clamp(l.transparency - 16, 0, 255),
                () -> l.transparency = clamp(l.transparency + 16, 0, 255), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Manual X", l.manualX + " px",
                () -> l.manualX = clamp(l.manualX - 10, 0, 4000),
                () -> l.manualX = clamp(l.manualX + 10, 0, 4000), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Manual Y", l.manualY + " px",
                () -> l.manualY = clamp(l.manualY - 10, 0, 4000),
                () -> l.manualY = clamp(l.manualY + 10, 0, 4000), c, mouseX, mouseY);
        y += 28;
        addButton(graphics, x, y, 110, 20, "Reset layout", true, () -> {
            ModConfig.Lyrics fresh = new ModConfig.Lyrics();
            l.position = fresh.position;
            l.manualX = fresh.manualX;
            l.manualY = fresh.manualY;
            l.width = fresh.width;
            l.height = fresh.height;
            l.fontSize = fresh.fontSize;
            l.scale = fresh.scale;
            l.padding = fresh.padding;
            l.lineSpacing = fresh.lineSpacing;
            l.maxLines = fresh.maxLines;
            l.transparency = fresh.transparency;
            l.alignment = fresh.alignment;
            status = "Lyrics layout reset.";
        }, c, mouseX, mouseY);
        y += 28;
        contentHeights[1] = y;
    }

    private void renderServices(GuiGraphics graphics, ModConfig cfg, ModConfig.Colors c,
                                int mouseX, int mouseY) {
        int x = viewport.x() + 4;
        int w = Math.max(80, viewport.width() - 8);
        int y = 4;
        drawLabel(graphics, x, y, "Keybinds are edited in Minecraft Controls.", c.textPrimary);
        y += 14;
        ModConfig.Keybinds keys = cfg.keybinds;
        drawLabel(graphics, x, y, "Open: " + keys.open + "    Lyrics: " + keys.lyrics
                + "    Play/Pause: " + keys.playPause, c.textSecondary);
        y += 12;
        drawLabel(graphics, x, y, "Next: " + keys.next + "    Previous: " + keys.previous, c.textSecondary);
        y += 20;

        List<MusicService> services = ServiceManager.all();
        int rowHeight = 62;
        for (MusicService service : services) {
            int sy = screenY(y);
            Ui.Rect row = rect(x, sy, w, rowHeight - 4);
            if (row.intersects(viewport)) {
                boolean active = service.id().equals(cfg.selectedService);
                Ui.panel(graphics, row, active ? c.controls : c.mainBg, active ? c.accent : 0);
                Ui.label(graphics, font, (active ? "* " : "  ") + service.displayName(),
                        row.x() + 4, row.y() + 3, c.textPrimary);
                ServiceStatus serviceStatus = service.status();
                Ui.label(graphics, font, Ui.fit(font, serviceStatus.line(), w - 74),
                        row.x() + 4, row.y() + 15, c.accent);
                List<String> detail = Ui.wrap(font, serviceStatus.detail(), w - 74, 2);
                for (int i = 0; i < detail.size(); i++) {
                    Ui.label(graphics, font, detail.get(i), row.x() + 4, row.y() + 27 + i * 10, c.textSecondary);
                }
            }
            final MusicService selected = service;
            addButton(graphics, x + w - 66, y + 2, 62, 18, "Select",
                    !service.id().equals(cfg.selectedService),
                    () -> {
                        ServiceManager.select(selected.id());
                        status = "Active service: " + selected.displayName();
                    }, c, mouseX, mouseY);
            y += rowHeight;
        }
        contentHeights[2] = y + 4;
    }

    private void renderPlayback(GuiGraphics graphics, ModConfig cfg, ModConfig.Colors c,
                                int mouseX, int mouseY) {
        int x = viewport.x() + 4;
        int w = Math.max(80, viewport.width() - 8);
        int y = 4;
        ModConfig.Playback playback = cfg.playback;
        ModConfig.UiLayout ui = cfg.ui;

        stepNumber(graphics, x, y, w, "Max retry attempts", String.valueOf(playback.maxRetryAttempts),
                () -> playback.maxRetryAttempts = clamp(playback.maxRetryAttempts - 1, 0, 5),
                () -> playback.maxRetryAttempts = clamp(playback.maxRetryAttempts + 1, 0, 5), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, x, y, w, "Stream timeout", playback.requestTimeoutSeconds + " s",
                () -> playback.requestTimeoutSeconds = clamp(playback.requestTimeoutSeconds - 5, 5, 120),
                () -> playback.requestTimeoutSeconds = clamp(playback.requestTimeoutSeconds + 5, 5, 120), c, mouseX, mouseY);
        y += 24;
        addToggle(graphics, x, y, 118, "System master gain", playback.useMasterGain,
                () -> playback.useMasterGain = !playback.useMasterGain, c, mouseX, mouseY);
        y += 28;
        stepNumber(graphics, x, y, w, "Result row height", ui.resultRowHeight + " px",
                () -> ui.resultRowHeight = clamp(ui.resultRowHeight - 4, 32, 64),
                () -> ui.resultRowHeight = clamp(ui.resultRowHeight + 4, 32, 64), c, mouseX, mouseY);
        y += 24;
        addToggle(graphics, x, y, 112, "Show scrollbars", ui.showScrollbars,
                () -> ui.showScrollbars = !ui.showScrollbars, c, mouseX, mouseY);
        y += 30;

        drawLabel(graphics, x, y, "Integrations", c.textPrimary);
        y += 14;
        addToggle(graphics, x, y, 150, "YouTube Music bridge", cfg.integrations.youTubeMusicBridgeEnabled,
                () -> cfg.integrations.youTubeMusicBridgeEnabled = !cfg.integrations.youTubeMusicBridgeEnabled,
                c, mouseX, mouseY);
        y += 26;
        drawLabel(graphics, x, y, ytDlpDetection(), c.textSecondary);
        y += 12;
        YtDlpManager.Diagnostic diagnostic = YtDlpManager.diagnostic();
        if (!diagnostic.lastError().isBlank()) {
            for (String line : Ui.wrap(font, diagnostic.lastError(), w, 3)) {
                drawLabel(graphics, x, y, line, c.accent);
                y += 10;
            }
        }
        addButton(graphics, x, y, 78, 18, "Retry", true, () -> {
            ytDlpDetection = null;
            status = "Rechecking yt-dlp...";
            YtDlpManager.refreshAsync().whenComplete((result, throwable) -> minecraft.execute(() -> {
                ytDlpDetection = null;
                status = throwable == null
                        ? "yt-dlp: " + result.status() : "Detection failed: " + Errors.describe(throwable);
            }));
        }, c, mouseX, mouseY);
        addButton(graphics, x + 82, y, 140, 18, "YouTube Music tab", true,
                () -> GuiNav.open(minecraft, GuiNav.Tab.YOUTUBE_MUSIC, this), c, mouseX, mouseY);
        y += 22;
        for (String line : Ui.wrap(font,
                "A configured absolute path can be set with integrations.youTubeMusicBridgeExecutable in "
                        + "config/musicallymods.json. Optional official download with checksum verification is on "
                        + "the YouTube Music tab; nothing installs without confirmation.", w, 3)) {
            drawLabel(graphics, x, y, line, c.textSecondary);
            y += 10;
        }
        y += 8;

        for (MusicService service : ServiceManager.all()) {
            ServiceStatus serviceStatus = service.status();
            if (serviceStatus.integration() == ServiceStatus.IntegrationLevel.NOT_INTEGRATED) {
                drawLabel(graphics, x, y, service.displayName() + ": " + serviceStatus.line(), c.accent);
                y += 12;
                for (String line : Ui.wrap(font, serviceStatus.detail(), w, 4)) {
                    drawLabel(graphics, x, y, line, c.textSecondary);
                    y += 10;
                }
                y += 8;
            }
        }
        contentHeights[3] = y + 4;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        GuiNav.Tab tab = nav.clicked(event.x(), event.y());
        if (tab != null) {
            GuiNav.open(minecraft, tab, parent);
            return true;
        }
        for (Action action : actions) {
            if (action.rect().contains(event.x(), event.y())) {
                action.onClick().run();
                ModConfig.markDirty();
                return true;
            }
        }
        if (viewport != null && scrollbarRect != null && viewport.contains(event.x(), event.y())
                && scrollbarRect.width() > 0 && scrollbarRect.contains(event.x(), event.y())) {
            double fraction = (event.y() - scrollbarRect.y()) / (double) Math.max(1, scrollbarRect.height());
            int max = Math.max(0, contentHeights[page] - viewport.height());
            scrollByPage[page] = Ui.clampScroll((int) (fraction * max), max);
            return true;
        }
        editingColor = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (viewport != null && viewport.contains(mouseX, mouseY)) {
            int max = Math.max(0, contentHeights[page] - viewport.height());
            scrollByPage[page] = Ui.clampScroll(
                    scrollByPage[page] - (int) Math.signum(amountY) * SCROLL_STEP, max);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingColor && selectedColor >= 0) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!colorText.isEmpty()) colorText = colorText.substring(0, colorText.length() - 1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyColor();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingColor = false;
                selectedColor = -1;
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scrollByPage(page, -SCROLL_STEP * 2);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollByPage(page, SCROLL_STEP * 2);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            scrollByPage[page] = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END && viewport != null) {
            scrollByPage[page] = Math.max(0, contentHeights[page] - viewport.height());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingColor && colorText.length() < 9) {
            int codepoint = event.codepoint();
            if ((codepoint >= '0' && codepoint <= '9') || (codepoint >= 'a' && codepoint <= 'f')
                    || (codepoint >= 'A' && codepoint <= 'F') || codepoint == '#') {
                colorText += new String(Character.toChars(codepoint));
                return true;
            }
        }
        return false;
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

    private void closeToParent() {
        ModConfig.saveIfDirty();
        minecraft.setScreen(parent);
    }

    /** Reads the cached diagnostic; health checks only run from Retry or the platform page. */
    private String ytDlpDetection() {
        if (ytDlpDetection == null) {
            YtDlpManager.Diagnostic diagnostic = YtDlpManager.diagnostic();
            ytDlpDetection = "yt-dlp: " + diagnostic.status()
                    + (diagnostic.version().isBlank() ? "" : " " + diagnostic.version())
                    + (diagnostic.path() == null ? "" : " | " + diagnostic.path());
        }
        return ytDlpDetection;
    }

    private void resetConfig() {
        if (!confirmReset) {
            confirmReset = true;
            status = "Click Reset again to restore all defaults.";
            return;
        }
        ModConfig.reset();
        confirmReset = false;
        selectedColor = -1;
        editingColor = false;
        status = "Defaults restored.";
    }

    private void applyColor() {
        if (selectedColor < 0) return;
        Integer parsed = ModConfig.parseColor(colorText);
        if (parsed == null) {
            status = "Invalid color. Use #AARRGGBB (for example #FF202020).";
            return;
        }
        setColorValue(selectedColor, parsed);
        ModConfig.get().theme = "CUSTOM";
        ModConfig.markDirty();
        status = "Color updated: " + COLOR_LABELS[selectedColor] + " = " + Ui.colorLabel(parsed);
        selectedColor = -1;
        editingColor = false;
    }

    private void addToggle(GuiGraphics graphics, int x, int yContent, int width, String label, boolean value,
                           Runnable onClick, ModConfig.Colors c, int mouseX, int mouseY) {
        addButton(graphics, x, yContent, width, 20, label + (value ? ": ON" : ": OFF"), true, onClick,
                c, mouseX, mouseY);
    }

    private void step(GuiGraphics graphics, int x, int yContent, int width, String label, String value,
                      Runnable decrease, Runnable increase, ModConfig.Colors c, int mouseX, int mouseY) {
        drawLabel(graphics, x, yContent + 4, label + ": " + value, c.textSecondary);
        addButton(graphics, x + width - 50, yContent, 20, 20, "-", true, decrease, c, mouseX, mouseY);
        addButton(graphics, x + width - 26, yContent, 20, 20, "+", true, increase, c, mouseX, mouseY);
    }

    private void stepNumber(GuiGraphics graphics, int x, int yContent, int width, String label, String value,
                            Runnable decrease, Runnable increase, ModConfig.Colors c, int mouseX, int mouseY) {
        step(graphics, x, yContent, width, label, value, decrease, increase, c, mouseX, mouseY);
    }

    private void addButton(GuiGraphics graphics, int x, int yContent, int width, int height, String label,
                           boolean enabled, Runnable onClick, ModConfig.Colors c, int mouseX, int mouseY) {
        Ui.Rect rect = rect(x, screenY(yContent), width, height);
        if (viewport == null) return;
        Ui.Rect visible = rect.intersection(viewport);
        if (visible.width() <= 0 || visible.height() <= 0) return;
        Ui.button(graphics, font, visible, label, c.controls, c.accent, c.textPrimary, enabled,
                visible.contains(mouseX, mouseY));
        if (enabled) actions.add(new Action(visible, onClick));
    }

    private void drawLabel(GuiGraphics graphics, int x, int yContent, String text, int color) {
        Ui.label(graphics, font, Ui.fit(font, text, viewport.width()), x, screenY(yContent), color);
    }

    private Ui.Rect rect(int x, int y, int width, int height) {
        return new Ui.Rect(x, y, width, height);
    }

    private int screenY(int contentY) {
        return viewport.y() + contentY - scrollByPage[page];
    }

    private void scrollByPage(int pageIndex, int delta) {
        if (viewport == null) return;
        int max = Math.max(0, contentHeights[pageIndex] - viewport.height());
        scrollByPage[pageIndex] = Ui.clampScroll(scrollByPage[pageIndex] + delta, max);
    }

    private static void cycleString(java.util.function.Consumer<String> setter, String[] values,
                                    String current, int direction) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
                break;
            }
        }
        int next = (index + direction + values.length) % values.length;
        setter.accept(values[next]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int dimColor(int color) {
        int r = ((color >> 16) & 0xFF) * 70 / 100;
        int g = ((color >> 8) & 0xFF) * 70 / 100;
        int b = (color & 0xFF) * 70 / 100;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private int colorValue(int index) {
        ModConfig.Colors c = ModConfig.get().colors;
        return switch (index) {
            case 0 -> c.mainBg;
            case 1 -> c.secondaryBg;
            case 2 -> c.accent;
            case 3 -> c.textPrimary;
            case 4 -> c.textSecondary;
            case 5 -> c.progress;
            case 6 -> c.controls;
            case 7 -> c.lyricsBg;
            case 8 -> c.lyricsText;
            default -> c.lyricsHighlight;
        };
    }

    private void setColorValue(int index, int value) {
        ModConfig.Colors c = ModConfig.get().colors;
        switch (index) {
            case 0 -> c.mainBg = value;
            case 1 -> c.secondaryBg = value;
            case 2 -> c.accent = value;
            case 3 -> c.textPrimary = value;
            case 4 -> c.textSecondary = value;
            case 5 -> c.progress = value;
            case 6 -> c.controls = value;
            case 7 -> c.lyricsBg = value;
            case 8 -> c.lyricsText = value;
            default -> c.lyricsHighlight = value;
        }
    }
}
