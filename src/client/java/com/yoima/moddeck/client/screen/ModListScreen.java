package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.*;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.storage.ConfigStorage;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Integrated hub matching the desktop-like Mod Deck reference while remaining a native Screen. */
public final class ModListScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(ModListScreen.class.getName());
    private static final int MARGIN = 14;
    private static final int HEADER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 50;
    private final Screen parent;
    private final Font uiFont = DeckFonts.ui();
    private List<ConfigDefinition> definitions = List.of();
    private ConfigDefinition selected;
    private String query = "";
    private int activeCategory;
    private int scrollOffset;
    private int sidebarWidth;
    private int mainX;
    private int mainWidth;
    private int mainTop;
    private int mainBottom;
    private int contentTop;
    private int contentBottom;
    private int optionRowHeight;
    private Component status = Component.empty();
    private DeckButton saveButton;
    private float uiScale = 1.0f;
    private int uiWidth;
    private int uiHeight;

    public ModListScreen(Screen parent) {
        super(Component.translatable("moddeck.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        // Minecraft's automatic GUI scale can leave only 480 logical pixels on a 1920px-wide
        // display. A fixed virtual canvas preserves the reference layout without changing the
        // user's global GUI-scale preference; pointer events are transformed by the same factor.
        // Minecraft commonly exposes a 2x GUI-scaled logical canvas here. Using exactly 0.5
        // makes one virtual Mod Deck pixel land on one framebuffer pixel, avoiding the uneven
        // sampling produced by the previous 0.6 fractional scale.
        uiScale = width < 760 ? 0.5f : 1.0f;
        uiWidth = Math.round(width / uiScale);
        uiHeight = Math.round(height / uiScale);
        definitions = ConfigRegistry.getAll();
        if (selected == null || definitions.stream().noneMatch(definition -> definition == selected)) {
            selected = definitions.isEmpty() ? null : definitions.getFirst();
        }
        sidebarWidth = Math.max(174, Math.min(220, uiWidth / 4));
        mainX = MARGIN + sidebarWidth + 16;
        mainWidth = uiWidth - mainX - MARGIN;
        mainTop = 35;
        mainBottom = uiHeight - 10;
        contentTop = mainTop + 102;
        contentBottom = mainBottom - 44;

        addRenderableWidget(new SearchFieldWidget(uiFont, MARGIN, 55, sidebarWidth, query,
                value -> query = value.trim().toLowerCase(Locale.ROOT)));
        addRenderableWidget(new DeckButton(uiWidth - 40, 8, 28, 28, Component.literal("×"),
                DeckButton.Style.ICON, this::onClose));
        addRenderableWidget(new ThemeSelectorWidget(uiWidth - 148, 8, 100, this::changeTheme));

        if (selected != null) {
            ConfigCategory category = selected.categories().get(Math.min(activeCategory, selected.categories().size() - 1));
            List<ConfigOption<?>> options = category.options();
            int availableHeight = Math.max(1, contentBottom - contentTop - 8);
            optionRowHeight = Math.max(42, Math.min(58, availableHeight / Math.max(1, options.size())));
            int widgetWidth = Math.max(150, Math.min(250, mainWidth * 42 / 100));
            int widgetX = mainX + mainWidth - widgetWidth - 28;
            int y = contentTop + 8 - scrollOffset;
            for (int index = 0; index < options.size(); index++) {
                ConfigOption<?> option = options.get(index);
                // A small tolerance absorbs rounding introduced by the virtual-canvas scale so
                // the final row remains visible instead of requiring a meaningless 4–6px scroll.
                if (y >= contentTop && y < contentBottom) {
                    boolean opensUp = y + 30 + 68 > contentBottom;
                    AbstractWidget widget = OptionWidgetRegistry.create(uiFont, widgetX,
                            y, widgetWidth, option,
                            this::markDirty, opensUp);
                    widget.setY(y + Math.max(0, (optionRowHeight - widget.getHeight()) / 2));
                    addRenderableWidget(widget);
                }
                y += optionRowHeight;
            }

            int buttonY = mainBottom - 35;
            int buttonWidth = Math.min(112, Math.max(80, mainWidth / 5));
            saveButton = addRenderableWidget(new DeckButton(mainX + mainWidth - buttonWidth - 16, buttonY,
                    buttonWidth, 27, Component.translatable("moddeck.save"), DeckButton.Style.PRIMARY, this::save));
            addRenderableWidget(new DeckButton(mainX + mainWidth - buttonWidth * 2 - 24, buttonY,
                    buttonWidth, 27, Component.translatable("moddeck.reset"), DeckButton.Style.SECONDARY, this::reset));
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, uiWidth, uiHeight, DeckTheme.BACKGROUND);
        graphics.fill(0, 0, uiWidth, HEADER_HEIGHT, DeckTheme.BACKGROUND_TOP);
        DeckTheme.roundedRect(graphics, MARGIN, 91, sidebarWidth, Math.max(40, uiHeight - 151), 7, DeckTheme.PANEL);
        DeckTheme.roundedRect(graphics, MARGIN, uiHeight - 51, sidebarWidth, 41, 7, DeckTheme.PANEL);
        if (mainWidth > 0) DeckTheme.roundedRect(graphics, mainX, mainTop, mainWidth, mainBottom - mainTop, 8, DeckTheme.PANEL);
        if (selected != null) {
            DeckTheme.roundedRect(graphics, mainX + 14, contentTop, mainWidth - 28,
                    Math.max(20, contentBottom - contentTop), 7, DeckTheme.CARD);
            graphics.fill(mainX + 14, mainTop + 95, mainX + mainWidth - 14, mainTop + 96, DeckTheme.DIVIDER);
            graphics.fill(mainX + 20, contentBottom, mainX + mainWidth - 20, contentBottom + 1, DeckTheme.DIVIDER);
        }
        drawSelectedSidebarBackground(graphics);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Drawing static layers first allows expanded selectors to render above every row label.
        int virtualMouseX = Math.round(mouseX / uiScale);
        int virtualMouseY = Math.round(mouseY / uiScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(uiScale, uiScale);
        extractBackground(graphics, virtualMouseX, virtualMouseY, delta);
        drawStaticContent(graphics);
        for (var child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
            }
        }
        graphics.pose().popMatrix();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent transformed = transform(event);
        for (var child : children()) {
            if (child instanceof ThemeSelectorWidget selector
                    && selector.handleExpandedClick(transformed.x(), transformed.y())) {
                return true;
            }
        }
        for (var child : children()) {
            if (child instanceof EnumSelectorWidget selector
                    && selector.handleExpandedClick(transformed.x(), transformed.y())) {
                return true;
            }
        }
        for (var child : children()) {
            if (child instanceof ThemeSelectorWidget selector) {
                selector.collapseIfOutside(transformed.x(), transformed.y());
            }
            if (child instanceof EnumSelectorWidget selector) {
                selector.collapseIfOutside(transformed.x(), transformed.y());
            }
        }
        if (super.mouseClicked(transformed, doubleClick)) return true;
        double mouseX = transformed.x();
        double mouseY = transformed.y();
        int y = 119;
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (mouseX >= MARGIN && mouseX < MARGIN + sidebarWidth && mouseY >= y && mouseY < y + 42) {
                selected = definition;
                activeCategory = 0;
                scrollOffset = 0;
                status = Component.empty();
                rebuildWidgets();
                return true;
            }
            y += 44;
        }
        if (selected != null && mouseY >= mainTop + 78 && mouseY < mainTop + 105) {
            int tabX = mainX + 20;
            int tabWidth = Math.max(70, Math.min(96, (mainWidth - 40) / 4));
            for (int index = 0; index < Math.min(4, selected.categories().size()); index++) {
                if (mouseX >= tabX + index * tabWidth && mouseX < tabX + (index + 1) * tabWidth) {
                    activeCategory = index;
                    scrollOffset = 0;
                    status = Component.empty();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (selected == null || mouseX < mainX || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int contentHeight = selected.categories().get(activeCategory).options().size() * optionRowHeight;
        int maximum = Math.max(0, contentHeight - (contentBottom - contentTop));
        int next = Math.max(0, Math.min(maximum, scrollOffset - (int) Math.round(scrollY * 26)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        rebuildWidgets();
        return true;
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(transform(event));
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(transform(event), dragX / uiScale, dragY / uiScale);
    }

    private void drawStaticContent(GuiGraphicsExtractor graphics) {
        DeckTheme.logo(graphics, MARGIN + 5, 11, 28);
        graphics.text(uiFont, "Mod Deck", MARGIN + 41, 16, DeckTheme.TEXT, true);
        graphics.text(uiFont, Component.translatable("moddeck.subtitle"), MARGIN + 41, 29,
                DeckTheme.TEXT_SECONDARY, false);
        graphics.text(uiFont, Component.translatable("moddeck.installed"), MARGIN + 10, 101,
                DeckTheme.TEXT_SECONDARY, false);
        String count = Integer.toString(filteredDefinitions().size());
        DeckTheme.roundedRect(graphics, MARGIN + sidebarWidth - 29, 98, 20, 15, 7, DeckTheme.FIELD);
        graphics.centeredText(uiFont, count, MARGIN + sidebarWidth - 19, 103, DeckTheme.TEXT_SECONDARY);
        drawSidebarDefinitions(graphics);

        DeckIcons.draw(graphics, DeckIcons.Icon.SETTINGS, MARGIN + 10, uiHeight - 42,
                20, DeckTheme.TEXT_SECONDARY);
        graphics.text(uiFont, Component.translatable("moddeck.change_key"), MARGIN + 39, uiHeight - 37,
                DeckTheme.TEXT_SECONDARY, false);
        DeckTheme.roundedRect(graphics, MARGIN + sidebarWidth - 42, uiHeight - 43, 30, 25, 5, DeckTheme.FIELD);
        graphics.centeredText(uiFont, "K", MARGIN + sidebarWidth - 27, uiHeight - 34, DeckTheme.TEXT_SECONDARY);

        if (selected == null) {
            graphics.centeredText(uiFont, Component.translatable("moddeck.empty"),
                    mainX + mainWidth / 2, uiHeight / 2, DeckTheme.TEXT_MUTED);
            return;
        }
        DeckTheme.modCube(graphics, mainX + 22, mainTop + 15, 38);
        graphics.text(uiFont, selected.title(), mainX + 72, mainTop + 18, DeckTheme.TEXT, true);
        graphics.text(uiFont, selected.modId(), mainX + 72, mainTop + 34, DeckTheme.TEXT_SECONDARY, false);
        if (!selected.description().isBlank()) {
            graphics.text(uiFont, fit(selected.description(), mainWidth - 104), mainX + 72, mainTop + 51,
                    DeckTheme.TEXT_SECONDARY, false);
        }
        drawTabs(graphics);
        drawOptionLabels(graphics);
        if (!status.getString().isEmpty()) {
            graphics.text(uiFont, status, mainX + 18, mainBottom - 26,
                    status.getString().contains("できません") ? 0xFFFF9E9E : DeckTheme.SUCCESS, false);
        }
    }

    private void drawSelectedSidebarBackground(GuiGraphicsExtractor graphics) {
        int y = 119;
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (definition == selected) {
                DeckTheme.roundedRect(graphics, MARGIN + 1, y, sidebarWidth - 2, 42, 6, DeckTheme.ACCENT_MUTED);
                graphics.fill(MARGIN + 1, y + 5, MARGIN + 3, y + 37, DeckTheme.ACCENT);
            }
            y += 44;
        }
    }

    private void drawSidebarDefinitions(GuiGraphicsExtractor graphics) {
        int y = 119;
        int bottom = uiHeight - 60;
        graphics.enableScissor(MARGIN, 115, MARGIN + sidebarWidth, bottom);
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (y + 42 > bottom) break;
            DeckTheme.modCube(graphics, MARGIN + 11, y + 7, 28);
            graphics.text(uiFont, definition.title(), MARGIN + 48, y + 10, DeckTheme.TEXT,
                    definition == selected);
            graphics.text(uiFont, definition.modId(), MARGIN + 48, y + 25, DeckTheme.TEXT_SECONDARY, false);
            if (definition == selected) DeckIcons.draw(graphics, DeckIcons.Icon.CHEVRON_RIGHT,
                    MARGIN + sidebarWidth - 24, y + 12, 16, DeckTheme.TEXT);
            y += 44;
        }
        graphics.disableScissor();
    }

    private void drawTabs(GuiGraphicsExtractor graphics) {
        String[] fallback = {"一般設定", "表示設定", "詳細設定", "キー設定"};
        DeckIcons.Icon[] icons = {DeckIcons.Icon.HOME, DeckIcons.Icon.MONITOR,
                DeckIcons.Icon.SETTINGS, DeckIcons.Icon.KEYBOARD};
        int tabX = mainX + 20;
        int tabY = mainTop + 71;
        int tabWidth = Math.max(70, Math.min(96, (mainWidth - 40) / 4));
        for (int index = 0; index < 4; index++) {
            String label = index < selected.categories().size()
                    ? selected.categories().get(index).displayName() : fallback[index];
            int color = index == activeCategory ? DeckTheme.ACCENT : DeckTheme.TEXT_MUTED;
            DeckIcons.draw(graphics, icons[index], tabX + index * tabWidth, tabY, 18, color);
            graphics.text(uiFont, label, tabX + index * tabWidth + 25, tabY + 4, color, false);
            if (index == activeCategory) {
                graphics.fill(tabX + index * tabWidth, mainTop + 94,
                        tabX + index * tabWidth + Math.min(70, tabWidth - 8), mainTop + 96, DeckTheme.ACCENT);
            }
        }
    }

    private void drawOptionLabels(GuiGraphicsExtractor graphics) {
        ConfigCategory category = selected.categories().get(activeCategory);
        if (category.options().isEmpty()) {
            graphics.centeredText(uiFont, Component.translatable("moddeck.category.empty"),
                    mainX + mainWidth / 2, contentTop + (contentBottom - contentTop) / 2,
                    DeckTheme.TEXT_MUTED);
            return;
        }
        int y = contentTop + 8 - scrollOffset;
        graphics.enableScissor(mainX + 14, contentTop, mainX + mainWidth - 14, contentBottom);
        for (int index = 0; index < category.options().size(); index++) {
            ConfigOption<?> option = category.options().get(index);
            if (y + optionRowHeight > contentTop && y < contentBottom) {
                graphics.text(uiFont, option.displayName(), mainX + 30, y + 13, DeckTheme.TEXT, false);
                if (!option.description().isBlank()) {
                    graphics.text(uiFont, fit(option.description(), Math.max(90, mainWidth / 2 - 45)),
                            mainX + 30, y + 28, DeckTheme.TEXT_SECONDARY, false);
                }
                if (index < category.options().size() - 1) {
                    graphics.fill(mainX + 30, y + optionRowHeight - 1, mainX + mainWidth - 30,
                            y + optionRowHeight, DeckTheme.DIVIDER);
                }
            }
            y += optionRowHeight;
        }
        graphics.disableScissor();
    }

    private List<ConfigDefinition> filteredDefinitions() {
        if (query.isBlank()) return definitions;
        return definitions.stream().filter(definition -> definition.title().toLowerCase(Locale.ROOT).contains(query)
                || definition.modId().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void markDirty() {
        status = Component.empty();
        if (saveButton != null) saveButton.setMessage(Component.translatable("moddeck.save"));
    }

    private void changeTheme(DeckTheme.Mode mode) {
        DeckTheme.setMode(mode);
        status = Component.empty();
        rebuildWidgets();
    }

    private void reset() {
        selected.reset();
        status = Component.translatable("moddeck.reset_ready");
        rebuildWidgets();
    }

    private void save() {
        Optional<ConfigStorage> storage = ConfigScreenApi.storage();
        if (storage.isEmpty()) {
            status = Component.translatable("moddeck.save_failed");
            LOGGER.warning("No ConfigStorage is installed");
            return;
        }
        try {
            storage.get().save(selected);
            status = Component.translatable("moddeck.saved");
            if (saveButton != null) saveButton.setMessage(Component.translatable("moddeck.saved_short"));
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("moddeck.save_failed");
            LOGGER.log(Level.SEVERE, "Could not save config for " + selected.modId(), exception);
        }
    }

    private String fit(String text, int maximumWidth) {
        if (uiFont.width(text) <= maximumWidth) return text;
        return uiFont.plainSubstrByWidth(text, Math.max(0, maximumWidth - uiFont.width("…"))) + "…";
    }

    private MouseButtonEvent transform(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, event.buttonInfo());
    }
}
