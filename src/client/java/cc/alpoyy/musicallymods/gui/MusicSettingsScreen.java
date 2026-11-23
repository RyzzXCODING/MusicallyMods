package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Settings GUI for theme, colors, lyrics overlay layout, services and keybind information. */
public final class MusicSettingsScreen extends Screen {
    private static final String[] PAGE_NAMES = {"Appearance", "Lyrics", "Services"};
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

    private final Screen parent;
    private final List<Action> actions = new ArrayList<>();
    private int page;
    private int serviceScroll;
    private int selectedColor = -1;
    private String colorText = "";
    private boolean editingColor;
    private boolean confirmReset;
    private String status = "Changes are saved automatically to config/musicallymods.json.";

    private record Action(Ui.Rect rect, Runnable onClick) {
    }

    public MusicSettingsScreen(Screen parent) {
        super(Component.translatable("screen.musicallymods.settings"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ModConfig cfg = ModConfig.get();
        ModConfig.Colors c = cfg.colors;
        actions.clear();
        graphics.fill(0, 0, width, height, c.mainBg);

        Ui.label(graphics, font, "MusicallyMods Settings", MARGIN, 10, c.textPrimary);
        int navX = MARGIN + 160;
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            Ui.Rect rect = new Ui.Rect(navX + i * 92, 8, 86, 20);
            addButton(graphics, font, rect, PAGE_NAMES[i], true, () -> page = i, c, mouseX, mouseY);
        }
        addButton(graphics, font, new Ui.Rect(width - MARGIN - 70, 8, 70, 20), "Done", true,
                this::closeToParent, c, mouseX, mouseY);

        Ui.Rect content = new Ui.Rect(MARGIN, 34, width - MARGIN * 2, height - 74);
        Ui.panel(graphics, content, c.secondaryBg, c.accent);
        if (page == 0) renderAppearance(graphics, content, cfg, c, mouseX, mouseY);
        else if (page == 1) renderLyrics(graphics, content, cfg, c, mouseX, mouseY);
        else renderServices(graphics, content, cfg, c, mouseX, mouseY);

        Ui.label(graphics, font, Ui.fit(font, status, width - MARGIN * 2 - 80),
                MARGIN, height - 30, c.textSecondary);
        addButton(graphics, font, new Ui.Rect(width - MARGIN - 74, height - 32, 74, 22),
                confirmReset ? "Confirm?" : "Reset", true, this::resetConfig, c, mouseX, mouseY);
    }

    private void renderAppearance(GuiGraphics graphics, Ui.Rect content, ModConfig cfg,
                                  ModConfig.Colors c, int mouseX, int mouseY) {
        int x = content.x() + 8;
        int y = content.y() + 8;
        Ui.label(graphics, font, "Theme preset", x, y, c.textPrimary);
        y += 14;
        String[] themes = {"DARK", "BLACK", "GRAY"};
        for (int i = 0; i < themes.length; i++) {
            Ui.Rect rect = new Ui.Rect(x + i * 94, y, 88, 20);
            String theme = themes[i];
            addButton(graphics, font, rect, theme, true, () -> {
                ModConfig.applyTheme(theme);
                status = "Theme applied: " + theme + ". Individual colors are still editable.";
            }, c, mouseX, mouseY);
        }
        y += 28;

        Ui.label(graphics, font, "Colors (click Edit, type #AARRGGBB, Apply)", x, y, c.textPrimary);
        y += 14;
        boolean editorOpen = selectedColor >= 0;
        int available = content.y() + content.height() - y - (editorOpen ? 84 : 8);
        int rows = Math.max(1, Math.min(COLOR_LABELS.length, available / 22));
        for (int i = 0; i < rows; i++) {
            int value = colorValue(i);
            graphics.fill(x, y + 1, x + 18, y + 17, value);
            graphics.renderOutline(x, y + 1, 18, 16, c.accent);
            Ui.label(graphics, font, COLOR_LABELS[i], x + 24, y + 3, c.textSecondary);
            Ui.label(graphics, font, Ui.colorLabel(value), x + 168, y + 3, c.textPrimary);
            final int index = i;
            addButton(graphics, font, new Ui.Rect(x + 268, y, 52, 20), "Edit", true,
                    () -> {
                        selectedColor = index;
                        colorText = Ui.colorLabel(colorValue(index));
                        editingColor = true;
                        status = "Editing " + COLOR_LABELS[index] + ".";
                    }, c, mouseX, mouseY);
            y += 22;
        }
        if (editorOpen) {
            int ey = content.y() + content.height() - 78;
            Ui.panel(graphics, new Ui.Rect(content.x() + 6, ey, content.width() - 12, 72), c.controls, c.accent);
            Ui.label(graphics, font, COLOR_LABELS[selectedColor], x, ey + 6, c.textPrimary);
            Ui.Rect field = new Ui.Rect(x, ey + 24, 150, 20);
            Ui.panel(graphics, field, c.mainBg, c.accent);
            Ui.label(graphics, font, editingColor ? colorText + "_" : colorText,
                    field.x() + 4, field.y() + 5, c.textPrimary);
            actions.add(new Action(field, () -> editingColor = true));
            addButton(graphics, font, new Ui.Rect(x + 158, ey + 24, 60, 20), "Apply", true,
                    this::applyColor, c, mouseX, mouseY);
            addButton(graphics, font, new Ui.Rect(x + 222, ey + 24, 60, 20), "Cancel", true,
                    () -> {
                        selectedColor = -1;
                        editingColor = false;
                    }, c, mouseX, mouseY);
        }
    }

    private void renderLyrics(GuiGraphics graphics, Ui.Rect content, ModConfig cfg,
                              ModConfig.Colors c, int mouseX, int mouseY) {
        ModConfig.Lyrics l = cfg.lyrics;
        int x = content.x() + 8;
        int y = content.y() + 8;
        int w = content.width() - 16;

        addToggle(graphics, font, new Ui.Rect(x, y, 74, 20), "Enabled", l.enabled,
                () -> l.enabled = !l.enabled, c, mouseX, mouseY);
        y += 26;
        step(graphics, font, x, y, w, "Position", l.position,
                () -> cycleString(value -> l.position = value, POSITIONS, l.position, -1),
                () -> cycleString(value -> l.position = value, POSITIONS, l.position, 1), c, mouseX, mouseY);
        y += 24;
        step(graphics, font, x, y, w, "Alignment", l.alignment,
                () -> cycleString(value -> l.alignment = value, ALIGNMENTS, l.alignment, -1),
                () -> cycleString(value -> l.alignment = value, ALIGNMENTS, l.alignment, 1), c, mouseX, mouseY);
        y += 26;
        addToggle(graphics, font, new Ui.Rect(x, y, 96, 20), "Show title", l.showTitle,
                () -> l.showTitle = !l.showTitle, c, mouseX, mouseY);
        addToggle(graphics, font, new Ui.Rect(x + 104, y, 104, 20), "Show artist", l.showArtist,
                () -> l.showArtist = !l.showArtist, c, mouseX, mouseY);
        addToggle(graphics, font, new Ui.Rect(x + 216, y, 116, 20), "Show artwork", l.showArtwork,
                () -> l.showArtwork = !l.showArtwork, c, mouseX, mouseY);
        y += 28;

        stepNumber(graphics, font, x, y, w, "Font size", String.format("%.2f", l.fontSize),
                () -> l.fontSize = clamp(l.fontSize - 0.1f, 0.7f, 2f),
                () -> l.fontSize = clamp(l.fontSize + 0.1f, 0.7f, 2f), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Scale", String.format("%.2f", l.scale),
                () -> l.scale = clamp(l.scale - 0.1f, 0.5f, 2f),
                () -> l.scale = clamp(l.scale + 0.1f, 0.5f, 2f), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Width", l.width + " px",
                () -> l.width = clamp(l.width - 20, 120, 1400),
                () -> l.width = clamp(l.width + 20, 120, 1400), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Height", l.height + " px",
                () -> l.height = clamp(l.height - 10, 40, 900),
                () -> l.height = clamp(l.height + 10, 40, 900), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Max lines", String.valueOf(l.maxLines),
                () -> l.maxLines = clamp(l.maxLines - 1, 1, 20),
                () -> l.maxLines = clamp(l.maxLines + 1, 1, 20), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Padding", l.padding + " px",
                () -> l.padding = clamp(l.padding - 2, 0, 40),
                () -> l.padding = clamp(l.padding + 2, 0, 40), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Line spacing", l.lineSpacing + " px",
                () -> l.lineSpacing = clamp(l.lineSpacing - 1, 0, 16),
                () -> l.lineSpacing = clamp(l.lineSpacing + 1, 0, 16), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Transparency", l.transparency + "/255",
                () -> l.transparency = clamp(l.transparency - 16, 0, 255),
                () -> l.transparency = clamp(l.transparency + 16, 0, 255), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Manual X", l.manualX + " px",
                () -> l.manualX = clamp(l.manualX - 10, 0, 4000),
                () -> l.manualX = clamp(l.manualX + 10, 0, 4000), c, mouseX, mouseY);
        y += 24;
        stepNumber(graphics, font, x, y, w, "Manual Y", l.manualY + " px",
                () -> l.manualY = clamp(l.manualY - 10, 0, 4000),
                () -> l.manualY = clamp(l.manualY + 10, 0, 4000), c, mouseX, mouseY);
        y += 28;
        addButton(graphics, font, new Ui.Rect(x, y, 110, 20), "Reset layout", true,
                () -> {
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
    }

    private void renderServices(GuiGraphics graphics, Ui.Rect content, ModConfig cfg,
                                ModConfig.Colors c, int mouseX, int mouseY) {
        int x = content.x() + 8;
        int y = content.y() + 8;
        int w = content.width() - 16;
        Ui.label(graphics, font, "Keybinds are edited in Minecraft Controls.", x, y, c.textPrimary);
        y += 14;
        ModConfig.Keybinds keys = cfg.keybinds;
        Ui.label(graphics, font, "Open: " + keys.open + "    Lyrics: " + keys.lyrics
                + "    Play/Pause: " + keys.playPause, x, y, c.textSecondary);
        y += 12;
        Ui.label(graphics, font, "Next: " + keys.next + "    Previous: " + keys.previous, x, y, c.textSecondary);
        y += 20;

        List<MusicService> services = ServiceManager.all();
        int rowHeight = 56;
        int visible = Math.max(1, (content.y() + content.height() - y - 8) / rowHeight);
        serviceScroll = Math.max(0, Math.min(serviceScroll, Math.max(0, services.size() - visible)));
        for (int i = 0; i < visible && serviceScroll + i < services.size(); i++) {
            MusicService service = services.get(serviceScroll + i);
            boolean active = service.id().equals(cfg.selectedService);
            Ui.panel(graphics, new Ui.Rect(x, y, w, rowHeight - 3),
                    active ? c.controls : c.mainBg, active ? c.accent : 0);
            Ui.label(graphics, font, (active ? "* " : "  ") + service.displayName(), x + 4, y + 3, c.textPrimary);
            List<String> summary = Ui.wrap(font, service.capabilities().summary(), w - 8, 1);
            if (!summary.isEmpty()) Ui.label(graphics, font, summary.get(0), x + 4, y + 16, c.textSecondary);
            List<String> note = Ui.wrap(font, service.capabilities().note(), w - 8, 2);
            for (int j = 0; j < note.size(); j++) {
                Ui.label(graphics, font, note.get(j), x + 4, y + 29 + j * 10, c.textSecondary);
            }
            final MusicService selected = service;
            addButton(graphics, font, new Ui.Rect(x + w - 66, y + 2, 62, 18), "Select", !active,
                    () -> {
                        ServiceManager.select(selected.id());
                        status = "Active service: " + selected.displayName();
                    }, c, mouseX, mouseY);
            y += rowHeight;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        for (Action action : actions) {
            if (action.rect().contains(event.x(), event.y())) {
                action.onClick().run();
                ModConfig.markDirty();
                return true;
            }
        }
        editingColor = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (page == 2) {
            serviceScroll = Math.max(0, serviceScroll - (int) Math.signum(amountY));
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

    private void addButton(GuiGraphics graphics, net.minecraft.client.gui.Font font, Ui.Rect rect,
                           String label, boolean enabled, Runnable onClick, ModConfig.Colors c,
                           int mouseX, int mouseY) {
        Ui.button(graphics, font, rect, label, c.controls, c.accent, c.textPrimary, enabled,
                rect.contains(mouseX, mouseY));
        if (enabled) actions.add(new Action(rect, onClick));
    }

    private void addToggle(GuiGraphics graphics, net.minecraft.client.gui.Font font, Ui.Rect rect,
                           String label, boolean value, Runnable onClick, ModConfig.Colors c,
                           int mouseX, int mouseY) {
        addButton(graphics, font, rect, label + (value ? ": ON" : ": OFF"), true, onClick, c, mouseX, mouseY);
    }

    private void step(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int w,
                      String label, String value, Runnable decrease, Runnable increase,
                      ModConfig.Colors c, int mouseX, int mouseY) {
        Ui.label(graphics, font, label + ": " + value, x, y + 4, c.textSecondary);
        addButton(graphics, font, new Ui.Rect(x + w - 50, y, 20, 20), "-", true, decrease, c, mouseX, mouseY);
        addButton(graphics, font, new Ui.Rect(x + w - 26, y, 20, 20), "+", true, increase, c, mouseX, mouseY);
    }

    private void stepNumber(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int w,
                            String label, String value, Runnable decrease, Runnable increase,
                            ModConfig.Colors c, int mouseX, int mouseY) {
        step(graphics, font, x, y, w, label, value, decrease, increase, c, mouseX, mouseY);
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
