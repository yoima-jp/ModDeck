package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.*;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.option.DescriptionOption;
import com.yoima.moddeck.api.option.SubcategoryOption;
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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Integrated hub matching the desktop-like Mod Deck reference while remaining a native Screen. */
public final class ModListScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(ModListScreen.class.getName());
    private static final int MARGIN = 14;
    private static final int HEADER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 50;
    private final Screen parent;
    private final String initialModId;
    private final Font uiFont = DeckFonts.ui();
    private List<ConfigDefinition> definitions = List.of();
    private ConfigDefinition selected;
    private String query = "";
    private int activeCategory;
    private int firstVisibleCategory;
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
    private SearchFieldWidget searchField;
    private float uiScale = 1.0f;
    private int uiWidth;
    private int uiHeight;
    private boolean initialSelectionApplied;
    private Language renderedLanguage;
    private boolean refocusSearch;
    private boolean statusError;
    private ConfigOption<?> hoveredOption;
    private boolean pendingWidgetRebuild;
    private final Map<AbstractWidget, ConfigOption<?>> optionWidgets = new IdentityHashMap<>();
    private List<String> renderedOptionIds = List.of();

    public ModListScreen(Screen parent) {
        this(parent, null);
    }

    public ModListScreen(Screen parent, String initialModId) {
        super(Component.translatable("moddeck.title"));
        this.parent = parent;
        this.initialModId = initialModId;
    }

    @Override protected void init() {
        pendingWidgetRebuild = false;
        optionWidgets.clear();
        // Minecraft's automatic GUI scale can leave only 480 logical pixels on a 1920px-wide
        // display. A fixed virtual canvas preserves the reference layout without changing the
        // user's global GUI-scale preference; pointer events are transformed by the same factor.
        // Minecraft commonly exposes a 2x GUI-scaled logical canvas here. Using exactly 0.5
        // makes one virtual Mod Deck pixel land on one framebuffer pixel, avoiding the uneven
        // sampling produced by the previous 0.6 fractional scale.
        uiScale = width < 760 ? 0.5f : 1.0f;
        uiWidth = Math.round(width / uiScale);
        uiHeight = Math.round(height / uiScale);
        renderedLanguage = Language.getInstance();
        definitions = ConfigRegistry.getAll();
        if (!initialSelectionApplied && initialModId != null) {
            selected = ConfigRegistry.get(initialModId).orElse(null);
            initialSelectionApplied = true;
        }
        if (selected == null || definitions.stream().noneMatch(definition -> definition == selected)) {
            selected = definitions.isEmpty() ? null : definitions.getFirst();
        }
        // Reapply the base theme before the selected screen's optional accent so switching from a
        // customized mod to a default-styled mod cannot leak the previous mod's colors.
        DeckTheme.setMode(DeckTheme.mode());
        if (selected != null) DeckTheme.applyAccent(selected.style().accentColor());
        sidebarWidth = Math.max(174, Math.min(220, uiWidth / 4));
        mainX = MARGIN + sidebarWidth + 16;
        mainWidth = uiWidth - mainX - MARGIN;
        mainTop = 35;
        mainBottom = uiHeight - 10;
        contentTop = mainTop + (selected != null && selected.categories().size() > 1 ? 102 : 72);
        contentBottom = mainBottom - 44;

        searchField = addRenderableWidget(new SearchFieldWidget(uiFont, MARGIN, 55, sidebarWidth, query, this::updateQuery));
        if (refocusSearch) {
            searchField.setFocused(true);
            searchField.moveCursorToEnd(false);
            refocusSearch = false;
        }
        addRenderableWidget(new DeckButton(uiWidth - 40, 8, 28, 28, Component.literal("×"),
                DeckButton.Style.ICON, this::onClose));
        addRenderableWidget(new ThemeSelectorWidget(uiWidth - 148, 8, 100, this::changeTheme));

        if (selected != null) {
            ConfigCategory category = selected.categories().get(Math.min(activeCategory, selected.categories().size() - 1));
            List<ConfigOption<?>> options = visibleOptions(category);
            renderedOptionIds = options.stream().map(ConfigOption::id).toList();
            int availableHeight = Math.max(1, contentBottom - contentTop - 8);
            optionRowHeight = Math.max(42, Math.min(58, availableHeight / Math.max(1, options.size())));
            int widgetWidth = Math.max(150, Math.min(250, mainWidth * 42 / 100));
            int widgetX = mainX + mainWidth - widgetWidth - 28;
            int y = contentTop + 8 - scrollOffset;
            for (int index = 0; index < options.size(); index++) {
                ConfigOption<?> option = options.get(index);
                // A small tolerance absorbs rounding introduced by the virtual-canvas scale so
                // the final row remains visible instead of requiring a meaningless 4–6px scroll.
                if (y >= contentTop && y + optionRowHeight <= contentBottom) {
                    if (option instanceof DescriptionOption) {
                        y += optionRowHeight;
                        continue;
                    }
                    // Rebuilding while Minecraft is iterating child listeners can invalidate that
                    // iteration. Defer structural changes such as expanding a subcategory.
                    Runnable changed = option instanceof SubcategoryOption ? this::requestWidgetRebuild : this::markDirty;
                    AbstractWidget widget = OptionWidgetRegistry.create(uiFont, widgetX,
                            y, widgetWidth, option,
                            changed, false);
                    widget.setY(y + Math.max(0, (optionRowHeight - widget.getHeight()) / 2));
                    widget.active = option.editable() && option.isEnabled();
                    if (widget instanceof ExpandableOptionWidget popup) {
                        popup.setPopupViewport(contentTop + 3, contentBottom - 3);
                    }
                    addRenderableWidget(widget);
                    optionWidgets.put(widget, option);
                    if (!(option instanceof SubcategoryOption)) {
                        OptionResetWidget reset = new OptionResetWidget(widgetX - 26,
                                y + Math.max(0, (optionRowHeight - 22) / 2), option, this::markDirty);
                        reset.refreshState();
                        addRenderableWidget(reset);
                    }
                }
                y += optionRowHeight;
            }

            int buttonY = mainBottom - 35;
            Component saveText = Component.translatable("moddeck.save");
            Component resetText = Component.translatable("moddeck.reset");
            int saveWidth = Math.max(actionButtonWidth(saveText),
                    actionButtonWidth(Component.translatable("moddeck.saved_short")));
            int resetWidth = actionButtonWidth(resetText);
            int saveX = mainX + mainWidth - saveWidth - 16;
            saveButton = addRenderableWidget(new DeckButton(saveX, buttonY,
                    saveWidth, 27, saveText, DeckButton.Style.PRIMARY, this::requestSave));
            addRenderableWidget(new DeckButton(saveX - resetWidth - 8, buttonY,
                    resetWidth, 27, resetText, DeckButton.Style.SECONDARY, this::reset));
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The shell is deliberately not mod-customizable: the recognizable Mod Deck workspace is
        // part of navigation consistency, while per-mod accent and save policy remain safe options.
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
        drawStaticContent(graphics, virtualMouseX, virtualMouseY);
        for (var child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
            }
        }
        // Popup menus must be rendered after ordinary widgets. Otherwise controls from later rows
        // cover the menu and make its visible choices impossible to click.
        for (var child : children()) {
            if (child instanceof ExpandableOptionWidget popup) {
                popup.extractPopupRenderState(graphics, virtualMouseX, virtualMouseY, delta);
            }
        }
        drawOptionTooltip(graphics, virtualMouseX, virtualMouseY);
        graphics.pose().popMatrix();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent transformed = transform(event);
        for (var child : children()) {
            if (child instanceof KeybindWidget keybind && keybind.isListening()) {
                return keybind.captureMouse(transformed);
            }
        }
        for (var child : children()) {
            if (child instanceof ThemeSelectorWidget selector
                    && selector.handleExpandedClick(transformed.x(), transformed.y())) {
                return true;
            }
        }
        for (var child : children()) {
            if (child instanceof ExpandableOptionWidget selector
                    && selector.handleExpandedClick(transformed.x(), transformed.y())) {
                return true;
            }
        }
        for (var child : children()) {
            if (child instanceof ThemeSelectorWidget selector) {
                selector.collapseIfOutside(transformed.x(), transformed.y());
            }
            if (child instanceof ExpandableOptionWidget selector) {
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
                statusError = false;
                rebuildWidgets();
                return true;
            }
            y += 44;
        }
        if (selected != null && selected.categories().size() > 1
                && mouseY >= mainTop + 68 && mouseY < mainTop + 100) {
            int tabX = mainX + 20;
            int visibleCount = visibleTabCount();
            int tabWidth = Math.max(1, (mainWidth - 40) / visibleCount);
            for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
                int categoryIndex = firstVisibleCategory + visibleIndex;
                if (categoryIndex >= selected.categories().size()) break;
                if (mouseX >= tabX + visibleIndex * tabWidth && mouseX < tabX + (visibleIndex + 1) * tabWidth) {
                    activeCategory = categoryIndex;
                    scrollOffset = 0;
                    status = Component.empty();
                    statusError = false;
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
        if (selected != null && selected.categories().size() > 1 && mouseX >= mainX
                && mouseY >= mainTop + 68 && mouseY < mainTop + 101) {
            int maximum = Math.max(0, selected.categories().size() - visibleTabCount());
            int next = Math.max(0, Math.min(maximum, firstVisibleCategory - (int) Math.signum(scrollY)));
            if (next != firstVisibleCategory) { firstVisibleCategory = next; return true; }
        }
        if (selected == null || mouseX < mainX || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // Include the list's top/bottom breathing room in the scroll range. Without it, the last
        // row can stop underneath the fixed footer and never become fully interactive.
        int contentHeight = visibleOptions(selected.categories().get(activeCategory)).size() * optionRowHeight + 16;
        int maximum = Math.max(0, contentHeight - (contentBottom - contentTop));
        int next = Math.max(0, Math.min(maximum, scrollOffset - (int) Math.round(scrollY * 26)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        rebuildWidgets();
        return true;
    }

    @Override public void onClose() {
        if (definitions.stream().noneMatch(ConfigDefinition::isDirty)) {
            minecraft.setScreenAndShow(parent);
            return;
        }
        minecraft.setScreenAndShow(new ConfirmScreen(discard -> {
            if (discard) {
                definitions.stream().filter(ConfigDefinition::isDirty).forEach(ConfigDefinition::discardChanges);
                minecraft.setScreenAndShow(parent);
            } else {
                minecraft.setScreenAndShow(this);
            }
        }, Component.translatable("moddeck.discard.title"),
                Component.translatable("moddeck.discard.message"),
                Component.translatable("moddeck.discard.confirm"),
                Component.translatable("moddeck.discard.cancel")));
    }

    @Override public boolean keyPressed(KeyEvent event) {
        for (var child : children()) {
            if (child instanceof KeybindWidget keybind && keybind.isListening()) {
                return keybind.captureKey(event);
            }
        }
        return super.keyPressed(event);
    }

    @Override public void tick() {
        super.tick();
        optionWidgets.forEach((widget, option) -> widget.active = option.editable() && option.isEnabled());
        children().stream().filter(OptionResetWidget.class::isInstance)
                .map(OptionResetWidget.class::cast).forEach(OptionResetWidget::refreshState);
        List<String> currentIds = selected == null ? List.of()
                : visibleOptions(selected.categories().get(activeCategory)).stream().map(ConfigOption::id).toList();
        if (renderedLanguage != Language.getInstance() || pendingWidgetRebuild
                || !currentIds.equals(renderedOptionIds)) rebuildWidgets();
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(transform(event));
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(transform(event), dragX / uiScale, dragY / uiScale);
    }

    private void drawStaticContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
        graphics.text(uiFont, selected.titleText().component(), mainX + 72, mainTop + 18, DeckTheme.TEXT, true);
        graphics.text(uiFont, selected.modId(), mainX + 72, mainTop + 34, DeckTheme.TEXT_SECONDARY, false);
        if (!selected.descriptionText().isEmpty()) {
            graphics.text(uiFont, fit(selected.descriptionText().component(), mainWidth - 104), mainX + 72, mainTop + 51,
                    DeckTheme.TEXT_SECONDARY, false);
        }
        drawTabs(graphics);
        drawOptionLabels(graphics, mouseX, mouseY);
        Component displayedStatus = !status.getString().isEmpty() ? status
                : selected.isDirty() ? Component.translatable("moddeck.unsaved") : Component.empty();
        if (!displayedStatus.getString().isEmpty()) {
            int statusColor = statusError ? 0xFFFF9E9E
                    : selected.isDirty() ? 0xFFFFCC66 : DeckTheme.SUCCESS;
            graphics.text(uiFont, displayedStatus, mainX + 18, mainBottom - 26, statusColor, false);
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
            graphics.text(uiFont, definition.titleText().component(), MARGIN + 48, y + 10, DeckTheme.TEXT,
                    definition == selected);
            graphics.text(uiFont, definition.modId(), MARGIN + 48, y + 25, DeckTheme.TEXT_SECONDARY, false);
            if (definition == selected) DeckIcons.draw(graphics, DeckIcons.Icon.CHEVRON_RIGHT,
                    MARGIN + sidebarWidth - 24, y + 12, 16, DeckTheme.TEXT);
            y += 44;
        }
        graphics.disableScissor();
    }

    private void drawTabs(GuiGraphicsExtractor graphics) {
        if (selected.categories().size() <= 1) return;
        int tabX = mainX + 20;
        int tabY = mainTop + 71;
        int visibleCount = visibleTabCount();
        int tabWidth = Math.max(1, (mainWidth - 40) / visibleCount);
        for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
            int categoryIndex = firstVisibleCategory + visibleIndex;
            if (categoryIndex >= selected.categories().size()) break;
            ConfigCategory category = selected.categories().get(categoryIndex);
            int color = categoryIndex == activeCategory ? DeckTheme.ACCENT : DeckTheme.TEXT_MUTED;
            Component label = category.displayNameText().component();
            graphics.centeredText(uiFont, fit(label, tabWidth - 12),
                    tabX + visibleIndex * tabWidth + tabWidth / 2, tabY + 4, color);
            if (categoryIndex == activeCategory) {
                graphics.fill(tabX + visibleIndex * tabWidth, mainTop + 94,
                        tabX + (visibleIndex + 1) * tabWidth - 8, mainTop + 96, DeckTheme.ACCENT);
            }
        }
    }

    private void drawOptionLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hoveredOption = null;
        ConfigCategory category = selected.categories().get(activeCategory);
        List<ConfigOption<?>> options = visibleOptions(category);
        if (options.isEmpty()) {
            graphics.centeredText(uiFont, Component.translatable("moddeck.category.empty"),
                    mainX + mainWidth / 2, contentTop + (contentBottom - contentTop) / 2,
                    DeckTheme.TEXT_MUTED);
            return;
        }
        int y = contentTop + 8 - scrollOffset;
        graphics.enableScissor(mainX + 14, contentTop, mainX + mainWidth - 14, contentBottom);
        for (int index = 0; index < options.size(); index++) {
            ConfigOption<?> option = options.get(index);
            if (y + optionRowHeight > contentTop && y < contentBottom) {
                if (option instanceof DescriptionOption) {
                    graphics.text(uiFont, fit(option.displayNameText().component(), mainWidth - 60),
                            mainX + 30, y + 19, DeckTheme.TEXT_SECONDARY, false);
                } else {
                int labelColor = option.isEnabled() ? DeckTheme.TEXT : DeckTheme.TEXT_MUTED;
                graphics.text(uiFont, option.displayNameText().component(), mainX + 30, y + 13, labelColor, false);
                if (option.isRestartRequired()) {
                    graphics.text(uiFont, Component.translatable("moddeck.requires_restart"),
                            mainX + 36 + uiFont.width(option.displayNameText().component()), y + 13,
                            DeckTheme.TEXT_MUTED, false);
                }
                if (option.validationError().isPresent()) {
                    graphics.text(uiFont, fit(option.validationError().orElseThrow().component(),
                                    Math.max(90, mainWidth / 2 - 45)),
                            mainX + 30, y + 28, 0xFFFF9E9E, false);
                } else if (!option.descriptionText().isEmpty()) {
                    graphics.text(uiFont, fit(option.descriptionText().component(), Math.max(90, mainWidth / 2 - 45)),
                            mainX + 30, y + 28, DeckTheme.TEXT_SECONDARY, false);
                }
                }
                if (index < options.size() - 1) {
                    graphics.fill(mainX + 30, y + optionRowHeight - 1, mainX + mainWidth - 30,
                            y + optionRowHeight, DeckTheme.DIVIDER);
                }
                if (mouseX >= mainX + 20 && mouseX < mainX + mainWidth - 20
                        && mouseY >= y && mouseY < y + optionRowHeight && !option.tooltips().isEmpty()) {
                    hoveredOption = option;
                }
            }
            y += optionRowHeight;
        }
        graphics.disableScissor();
    }

    private void drawOptionTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredOption == null || children().stream().anyMatch(child ->
                child instanceof ExpandableOptionWidget popup && popup.isExpanded())) return;
        if (children().stream().anyMatch(child -> child instanceof AbstractWidget widget
                && widget.visible && widget.isMouseOver(mouseX, mouseY))) return;
        int maximumTextWidth = Math.min(210, Math.max(120, mainWidth / 3));
        List<net.minecraft.util.FormattedCharSequence> lines = hoveredOption.tooltips().stream()
                .flatMap(text -> uiFont.split(text.component(), maximumTextWidth).stream()).toList();
        if (lines.isEmpty()) return;
        int textWidth = lines.stream().mapToInt(uiFont::width).max().orElse(0);
        int boxWidth = textWidth + 16;
        int boxHeight = lines.size() * 11 + 12;
        int x = Math.min(uiWidth - boxWidth - 6, mouseX + 10);
        int y = mouseY + 9;
        if (y + boxHeight > uiHeight - 6) y = Math.max(6, mouseY - boxHeight - 9);
        x = Math.max(6, x);
        DeckTheme.border(graphics, x, y, boxWidth, boxHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(uiFont, lines.get(index), x + 8, y + 7 + index * 11,
                    DeckTheme.TEXT_SECONDARY, false);
        }
    }

    private List<ConfigDefinition> filteredDefinitions() {
        if (query.isBlank()) return definitions;
        return definitions.stream().filter(definition -> definition.titleText().component().getString()
                        .toLowerCase(Locale.ROOT).contains(query)
                || definition.modId().toLowerCase(Locale.ROOT).contains(query)
                || definition.categories().stream().flatMap(category -> flattenOptions(category.options(), true).stream())
                .anyMatch(this::matchesQuery)).toList();
    }

    private void markDirty() {
        status = Component.empty();
        statusError = false;
        if (saveButton != null) saveButton.setMessage(Component.translatable("moddeck.save"));
    }

    private void changeTheme(DeckTheme.Mode mode) {
        DeckTheme.setMode(mode);
        if (selected != null) DeckTheme.applyAccent(selected.style().accentColor());
        status = Component.empty();
        statusError = false;
        requestWidgetRebuild();
    }

    private void reset() {
        selected.reset();
        status = Component.translatable("moddeck.reset_ready");
        statusError = false;
        requestWidgetRebuild();
    }

    private void save() {
        Optional<ConfigStorage> storage = ConfigScreenApi.storage();
        if (storage.isEmpty()) {
            status = Component.translatable("moddeck.save_failed");
            statusError = true;
            LOGGER.warning("No ConfigStorage is installed");
            return;
        }
        try {
            ConfigScreenApi.save(selected);
            status = Component.translatable("moddeck.saved");
            statusError = false;
            if (saveButton != null) saveButton.setMessage(Component.translatable("moddeck.saved_short"));
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("moddeck.save_failed");
            statusError = true;
            LOGGER.log(Level.SEVERE, "Could not save config for " + selected.modId(), exception);
        }
    }

    private void requestSave() {
        if (!selected.isValid()) {
            status = Component.translatable("moddeck.validation.fix_errors");
            statusError = true;
            return;
        }
        if (!selected.style().confirmSave()) { save(); return; }
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            minecraft.setScreenAndShow(this);
            if (confirmed) save();
        }, Component.translatable("moddeck.confirm_save.title"),
                Component.translatable("moddeck.confirm_save.message")));
    }

    private String fit(Component component, int maximumWidth) {
        String text = component.getString();
        if (uiFont.width(text) <= maximumWidth) return text;
        return uiFont.plainSubstrByWidth(text, Math.max(0, maximumWidth - uiFont.width("…"))) + "…";
    }

    private void updateQuery(String value) {
        query = value.trim().toLowerCase(Locale.ROOT);
        if (selected != null && !query.isBlank()) {
            for (int index = 0; index < selected.categories().size(); index++) {
                if (selected.categories().get(index).options().stream().anyMatch(this::matchesQuery)) {
                    activeCategory = index;
                    ensureActiveCategoryVisible();
                    break;
                }
            }
        }
        scrollOffset = 0;
        refocusSearch = true;
        requestWidgetRebuild();
    }

    private List<ConfigOption<?>> visibleOptions(ConfigCategory category) {
        boolean screenMatches = query.isBlank()
                || selected.titleText().component().getString().toLowerCase(Locale.ROOT).contains(query)
                || selected.modId().contains(query);
        List<ConfigOption<?>> flattened = flattenOptions(category.options(), !screenMatches);
        return screenMatches ? flattened : flattened.stream().filter(this::matchesQuery).toList();
    }

    private boolean matchesQuery(ConfigOption<?> option) {
        return option.displayNameText().component().getString().toLowerCase(Locale.ROOT).contains(query)
                || option.descriptionText().component().getString().toLowerCase(Locale.ROOT).contains(query)
                || option.id().contains(query)
                || option.searchAliases().stream().map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(alias -> alias.contains(query))
                || option instanceof SubcategoryOption subcategory
                && flattenOptions(subcategory.children(), true).stream().anyMatch(this::matchesQuery);
    }

    private List<ConfigOption<?>> flattenOptions(List<ConfigOption<?>> options, boolean forceExpanded) {
        List<ConfigOption<?>> flattened = new ArrayList<>();
        for (ConfigOption<?> option : options) {
            if (!option.isDisplayed()) continue;
            flattened.add(option);
            if (option instanceof SubcategoryOption subcategory && (forceExpanded || subcategory.draftValue())) {
                flattened.addAll(flattenOptions(subcategory.children(), forceExpanded));
            }
        }
        return List.copyOf(flattened);
    }

    private int visibleTabCount() {
        return Math.min(selected.categories().size(), Math.max(1, (mainWidth - 40) / 90));
    }

    private int actionButtonWidth(Component text) {
        // Action buttons include a 16px icon, spacing around the icon, and balanced text padding.
        // Measuring translated text here keeps Japanese and other longer labels from clipping.
        return Math.max(82, uiFont.width(text) + 48);
    }

    private void requestWidgetRebuild() { pendingWidgetRebuild = true; }

    private void ensureActiveCategoryVisible() {
        int visible = visibleTabCount();
        if (activeCategory < firstVisibleCategory) firstVisibleCategory = activeCategory;
        if (activeCategory >= firstVisibleCategory + visible) firstVisibleCategory = activeCategory - visible + 1;
    }

    private MouseButtonEvent transform(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, event.buttonInfo());
    }
}
