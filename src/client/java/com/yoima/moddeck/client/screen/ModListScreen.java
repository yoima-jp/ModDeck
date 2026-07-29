package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.*;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.option.ListOption;
import com.yoima.moddeck.api.option.DescriptionOption;
import com.yoima.moddeck.api.option.SubcategoryOption;
import com.yoima.moddeck.api.storage.ConfigStorage;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.theme.ModIconTextures;
import com.yoima.moddeck.client.screen.ListEditorPane;
import com.yoima.moddeck.client.screen.layout.ModListLayout;
import com.yoima.moddeck.client.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Integrated hub matching the desktop-like Mod Deck reference while remaining a native Screen. */
public final class ModListScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(ModListScreen.class.getName());
    private static final int MARGIN = 14;
    private static final int HEADER_HEIGHT = 42;
    // The bundled font is roughly 9px tall; 13px leaves a small, readable gap between lines.
    private static final int DESCRIPTION_LINE_HEIGHT = 13;
    private static final int CATEGORY_ARROW_SIZE = 24;
    private static final int CATEGORY_ARROW_GAP = 8;
    private static final int CATEGORY_TAB_HORIZONTAL_PADDING = 16;
    private static final int CATEGORY_TAB_MIN_WIDTH = 48;
    private final Screen parent;
    private final String initialModId;
    private final Font uiFont = DeckFonts.ui();
    private List<ConfigDefinition> definitions = List.of();
    private ConfigDefinition selected;
    private String modQuery = "";
    private String optionQuery = "";
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
    private int categoryTabsTop;
    private int footerActionsLeft;
    private Component status = Component.empty();
    private DeckButton saveButton;
    private SearchFieldWidget modSearchField;
    private SearchFieldWidget optionSearchField;
    private float uiScale = 1.0f;
    private int uiWidth;
    private int uiHeight;
    private boolean initialSelectionApplied;
    private Language renderedLanguage;
    private boolean refocusModSearch;
    private boolean refocusOptionSearch;
    private boolean statusError;
    private ConfigOption<?> hoveredOption;
    private boolean pendingWidgetRebuild;
    private final Map<AbstractWidget, ConfigOption<?>> optionWidgets = new IdentityHashMap<>();
    private List<ConfigOption<?>> renderedOptions = List.of();
    private List<Integer> renderedRowHeights = List.of();
    private List<String> renderedOptionIds = List.of();
    private ModListLayout.TabLayout tabLayout = new ModListLayout.TabLayout(List.of(), false, 0, 0,
            CATEGORY_ARROW_SIZE, CATEGORY_ARROW_GAP);
    private ListOption<?> editingList;
    private ConfigCategory editingListCategory;
    private ListEditorPane listEditorPane;

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
        footerActionsLeft = mainX + mainWidth;
        int descriptionBottom = mainTop + 51 + modDescriptionLines().size() * DESCRIPTION_LINE_HEIGHT;
        categoryTabsTop = Math.max(mainTop + 68, descriptionBottom + 6);
        contentTop = selected != null && selected.categories().size() > 1
                ? categoryTabsTop + 34 : Math.max(mainTop + 72, descriptionBottom + 8);
        contentBottom = mainBottom - 44;

        modSearchField = addRenderableWidget(new SearchFieldWidget(uiFont, MARGIN, 55, sidebarWidth,
                modQuery, Component.translatable("moddeck.search.mods"), this::updateModQuery));
        if (refocusModSearch) {
            // Restore Screen-level focus; EditBox#setFocused alone does not always re-establish
            // this child as the Screen's focused element after a full rebuild.
            setFocused(modSearchField);
            modSearchField.moveCursorToEnd(false);
            refocusModSearch = false;
        }
        addRenderableWidget(new DeckButton(uiWidth - 40, 8, 28, 28, Component.literal("×"),
                DeckButton.Style.ICON, this::onClose));

        if (selected != null) {
            int optionSearchWidth = optionSearchWidth();
            optionSearchField = addRenderableWidget(new SearchFieldWidget(uiFont,
                    mainX + mainWidth - optionSearchWidth - 18, mainTop + 15, optionSearchWidth,
                    optionQuery, Component.translatable("moddeck.search.settings"), this::updateOptionQuery));
            if (refocusOptionSearch) {
                // Restore Screen-level focus so keyboard input reaches the recreated search field
                // immediately instead of staying on whatever child happened to be rebuilt last.
                setFocused(optionSearchField);
                optionSearchField.moveCursorToEnd(false);
                refocusOptionSearch = false;
            }
            // The overflow decision must be made against the full category-row width, not the
            // arrow-shrunk viewport. Otherwise a previous overflow state would shrink the
            // available width and could keep overflow true even when the current categories fit.
            tabLayout = ModListLayout.categoryTabs(
                    selected.categories().stream().map(ModListScreen::categoryLabel).toList(),
                    ModListLayout.FontMetrics.of(uiFont),
                    categoryRowWidth(),
                    CATEGORY_TAB_MIN_WIDTH,
                    CATEGORY_TAB_HORIZONTAL_PADDING,
                    CATEGORY_ARROW_SIZE,
                    CATEGORY_ARROW_GAP);
            if (tabLayout.overflow()) {
                addRenderableWidget(new CategoryScrollButton(mainX + 20, categoryTabsTop, -1));
                addRenderableWidget(new CategoryScrollButton(mainX + mainWidth - 44, categoryTabsTop, 1));
            }
            ConfigCategory category = selected.categories().get(Math.min(activeCategory, selected.categories().size() - 1));
            List<ConfigOption<?>> options = visibleOptions(category);
            renderedOptions = List.copyOf(options);
            renderedOptionIds = renderedOptions.stream().map(ConfigOption::id).toList();
            renderedRowHeights = ModListLayout.rowHeights(
                    renderedOptions,
                    ModListLayout.FontMetrics.of(uiFont),
                    Math.max(90, mainWidth - 60),
                    Math.max(90, mainWidth / 2 - 45),
                    DESCRIPTION_LINE_HEIGHT);
            if (editingList != null) {
                int paneX = mainX + 14;
                int paneY = contentTop;
                int paneWidth = mainWidth - 28;
                int paneHeight = contentBottom - contentTop;
                listEditorPane = new ListEditorPane(uiFont, editingList, editingListCategory,
                        paneX, paneY, paneWidth, paneHeight,
                        value -> {
                            @SuppressWarnings("unchecked")
                            List<Object> cast = (List<Object>) value;
                            @SuppressWarnings({"rawtypes", "unchecked"})
                            boolean success = ((ListOption) editingList).trySetDraftValue(cast);
                            if (!success) {
                                status = Component.translatable("moddeck.list.invalid_element");
                                statusError = true;
                            }
                }, this::markDirty, this::stopListEditing);
                listEditorPane.refresh();
                renderedOptionIds = List.of(editingList.id());
            } else {
                int availableHeight = Math.max(1, contentBottom - contentTop - 8);
                int widgetWidth = Math.max(150, Math.min(250, mainWidth * 42 / 100));
                int widgetX = mainX + mainWidth - widgetWidth - 28;
                int y = contentTop + 8 - scrollOffset;
                for (int index = 0; index < renderedOptions.size(); index++) {
                    ConfigOption<?> option = renderedOptions.get(index);
                    int rowHeight = renderedRowHeights.get(index);
                    // A small tolerance absorbs rounding introduced by the virtual-canvas scale so
                    // the final row remains visible instead of requiring a meaningless 4–6px scroll.
                    if (y >= contentTop && y + rowHeight <= contentBottom) {
                        if (option instanceof DescriptionOption) {
                            y += rowHeight;
                            continue;
                        }
                        // Rebuilding while Minecraft is iterating child listeners can invalidate that
                        // iteration. Defer structural changes such as expanding a subcategory or
                        // resetting an option to the next tick.
                        Runnable changed = option instanceof SubcategoryOption ? this::requestWidgetRebuild : this::markDirty;
                        AbstractWidget widget = OptionWidgetRegistry.create(uiFont, widgetX,
                                y, widgetWidth, option,
                                changed, false);
                        widget.setY(y + Math.max(0, (rowHeight - widget.getHeight()) / 2));
                        widget.active = option.editable() && option.isEnabled();
                        if (widget instanceof ExpandableOptionWidget popup) {
                            popup.setPopupViewport(contentTop + 3, contentBottom - 3);
                        }
                        addRenderableWidget(widget);
                        optionWidgets.put(widget, option);
                        if (!(option instanceof SubcategoryOption)) {
                            OptionResetWidget reset = new OptionResetWidget(widgetX - 26,
                                    y + Math.max(0, (rowHeight - 22) / 2), option,
                                    this::requestWidgetRebuildAndMarkDirty);
                            reset.refreshState();
                            addRenderableWidget(reset);
                        }
                    }
                    y += rowHeight;
                }
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
            int resetX = saveX - resetWidth - 8;
            addRenderableWidget(new DeckButton(resetX, buttonY,
                    resetWidth, 27, resetText, DeckButton.Style.SECONDARY, this::reset));
            footerActionsLeft = resetX;
            if (!selected.presets().isEmpty()) {
                int presetWidth = Math.max(110, Math.min(150, mainWidth / 4));
                int presetX = resetX - presetWidth - 8;
                PresetSelectorWidget presetSelector = new PresetSelectorWidget(
                        presetX, buttonY, presetWidth, selected.presets(), this::applyPreset);
                presetSelector.setPopupViewport(contentTop + 3, mainBottom - 3);
                addRenderableWidget(presetSelector);
                footerActionsLeft = presetX;
            }
        }
        // Keep the search field at its original position, but append the theme selector after it
        // so the selector and its popup are rendered above the overlapping header search area.
        addRenderableWidget(new ThemeSelectorWidget(uiWidth - 148, 8, 100, this::changeTheme));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The shell is deliberately not mod-customizable: the recognizable Mod Deck workspace is
        // part of navigation consistency, while per-mod accent and save policy remain safe options.
        graphics.fill(0, 0, uiWidth, uiHeight, DeckTheme.BACKGROUND);
        graphics.fill(0, 0, uiWidth, HEADER_HEIGHT, DeckTheme.BACKGROUND_TOP);
        DeckTheme.roundedRect(graphics, MARGIN, 91, sidebarWidth, Math.max(40, uiHeight - 101), 7, DeckTheme.PANEL);
        if (mainWidth > 0) DeckTheme.roundedRect(graphics, mainX, mainTop, mainWidth, mainBottom - mainTop, 8, DeckTheme.PANEL);
        if (selected != null) {
            if (selected.categories().size() > 1) {
                // Category navigation belongs to the fixed screen chrome, not the scrolling option
                // list. Giving it its own surface makes that boundary visible and ensures labels,
                // arrows, and hit targets stay anchored while the content beneath them scrolls.
                DeckTheme.roundedRect(graphics, mainX + 14, categoryTabsTop - 2,
                        mainWidth - 28, 34, 5, DeckTheme.PANEL_RAISED);
                graphics.fill(mainX + 20, categoryTabsTop + 31,
                        mainX + mainWidth - 20, categoryTabsTop + 32, DeckTheme.DIVIDER);
            }
            DeckTheme.roundedRect(graphics, mainX + 14, contentTop, mainWidth - 28,
                    Math.max(20, contentBottom - contentTop), 7, DeckTheme.CARD);
            graphics.fill(mainX + 14, contentTop - 7, mainX + mainWidth - 14, contentTop - 6, DeckTheme.DIVIDER);
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
        if (listEditorPane != null) {
            listEditorPane.render(graphics, virtualMouseX, virtualMouseY, delta);
        }
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
        if (listEditorPane != null) {
            if (listEditorPane.mouseClicked(transformed, doubleClick)) return true;
        }
        for (var child : children()) {
            if (child instanceof KeybindWidget keybind && keybind.isListening()) {
                return keybind.captureMouse(transformed);
            }
        }
        for (var child : children()) {
            if (child instanceof ThemeSelectorWidget selector
                    && selector.handleExpandedClick(transformed.x(), transformed.y())) {
                syncThemePopupState();
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
        boolean handled = super.mouseClicked(transformed, doubleClick);
        syncThemePopupState();
        if (handled) return true;
        double mouseX = transformed.x();
        double mouseY = transformed.y();
        int y = 119;
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (mouseX >= MARGIN && mouseX < MARGIN + sidebarWidth && mouseY >= y && mouseY < y + 42) {
                selected = definition;
                activeCategory = 0;
                optionQuery = "";
                scrollOffset = 0;
                status = Component.empty();
                statusError = false;
                rebuildWidgets();
                return true;
            }
            y += 44;
        }
        if (selected != null && selected.categories().size() > 1
                && mouseY >= categoryTabsTop && mouseY < categoryTabsTop + 32) {
            int tabX = categoryTabsX();
            List<Integer> renderedWidths = renderedTabWidths();
            for (int visibleIndex = 0; visibleIndex < renderedWidths.size(); visibleIndex++) {
                int categoryIndex = firstVisibleCategory + visibleIndex;
                if (categoryIndex >= selected.categories().size()) break;
                int tabWidth = renderedWidths.get(visibleIndex);
                if (mouseX >= tabX && mouseX < tabX + tabWidth) {
                    stopListEditing();
                    activeCategory = categoryIndex;
                    scrollOffset = 0;
                    status = Component.empty();
                    statusError = false;
                    rebuildWidgets();
                    return true;
                }
                tabX += tabWidth;
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (listEditorPane != null && listEditorPane.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        // Expanded selector popups with large choice lists own the scroll gesture while open.
        for (var child : children()) {
            if (child instanceof ExpandableOptionWidget popup
                    && popup.handleExpandedScroll(mouseX, mouseY, scrollY)) return true;
        }
        if (selected != null && selected.categories().size() > 1
                && mouseX >= categoryTabsX() && mouseX < categoryTabsX() + categoryTabsWidth()
                && mouseY >= categoryTabsTop && mouseY < categoryTabsTop + 33) {
            int maximum = Math.max(0, selected.categories().size() - visibleTabCount());
            int next = Math.max(0, Math.min(maximum, firstVisibleCategory - (int) Math.signum(scrollY)));
            if (next != firstVisibleCategory) { firstVisibleCategory = next; ensureActiveCategoryVisible(); return true; }
        }
        if (selected == null || mouseX < mainX || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // Scroll range must come from the same renderedOptions/renderedRowHeights snapshot that
        // drawing and widget placement use. Recomputing visibleOptions here would allow a
        // subcategory expansion or displayedWhen toggle to change the option count mid-frame,
        // making the maximum disagree with the actual rendered rows.
        int contentHeight = renderedRowHeights.stream().mapToInt(Integer::intValue).sum() + 16;
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

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (listEditorPane != null && listEditorPane.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (listEditorPane != null && listEditorPane.keyPressed(event)) return true;
        for (var child : children()) {
            if (child instanceof KeybindWidget keybind && keybind.isListening()) {
                return keybind.captureKey(event);
            }
        }
        return super.keyPressed(event);
    }

    @Override public void tick() {
        super.tick();
        // List editing owns its own widget lifecycle; Screen's id-comparison rebuild would
        // recreate the pane and all row widgets every tick, destroying drag state and field focus.
        if (editingList != null) return;
        optionWidgets.forEach((widget, option) -> widget.active = option.editable() && option.isEnabled());
        children().stream().filter(OptionResetWidget.class::isInstance)
                .map(OptionResetWidget.class::cast).forEach(OptionResetWidget::refreshState);
        syncThemePopupState();
        List<String> currentIds = selected == null ? List.of()
                : visibleOptions(selected.categories().get(activeCategory)).stream().map(ConfigOption::id).toList();
        if (renderedLanguage != Language.getInstance() || pendingWidgetRebuild
                || !currentIds.equals(renderedOptionIds)) rebuildWidgets();
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent transformed = transform(event);
        for (var child : children()) if (child instanceof ExpandableOptionWidget popup) {
            popup.handleExpandedRelease();
        }
        if (listEditorPane != null && listEditorPane.mouseReleased(transformed)) return true;
        return super.mouseReleased(transformed);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent transformed = transform(event);
        if (listEditorPane != null && listEditorPane.mouseDragged(transformed, dragX / uiScale, dragY / uiScale)) return true;
        for (var child : children()) {
            if (child instanceof ExpandableOptionWidget popup
                    && popup.handleExpandedDrag(transformed.x(), transformed.y())) return true;
        }
        return super.mouseDragged(transformed, dragX / uiScale, dragY / uiScale);
    }

    private void drawStaticContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // The product mark identifies Mod Deck itself; selected-mod icons belong in the sidebar
        // and detail header where they describe the current configuration owner.
        if (!ModIconTextures.draw(graphics, "moddeck", MARGIN + 5, 11, 28)) {
            DeckTheme.logo(graphics, MARGIN + 5, 11, 28);
        }
        graphics.text(uiFont, "Mod Deck", MARGIN + 41, 16, DeckTheme.TEXT, false);
        graphics.text(uiFont, Component.translatable("moddeck.subtitle"), MARGIN + 41, 29,
                DeckTheme.TEXT_SECONDARY, false);
        graphics.text(uiFont, Component.translatable("moddeck.installed"), MARGIN + 10, 101,
                DeckTheme.TEXT_SECONDARY, false);
        String count = Integer.toString(filteredDefinitions().size());
        DeckTheme.roundedRect(graphics, MARGIN + sidebarWidth - 29, 98, 20, 15, 7, DeckTheme.FIELD);
        DeckTheme.centeredText(graphics, uiFont, count, MARGIN + sidebarWidth - 19, 103, DeckTheme.TEXT_SECONDARY);
        drawSidebarDefinitions(graphics);

        if (selected == null) {
            DeckTheme.centeredText(graphics, uiFont, Component.translatable("moddeck.empty"),
                    mainX + mainWidth / 2, uiHeight / 2, DeckTheme.TEXT_MUTED);
            return;
        }
        drawModIcon(graphics, selected.modId(), mainX + 22, mainTop + 15, 38);
        graphics.text(uiFont, fit(selected.titleText().component(),
                Math.max(80, mainWidth - optionSearchWidth() - 106)), mainX + 72, mainTop + 22, DeckTheme.TEXT, false);
        List<FormattedCharSequence> descLines = modDescriptionLines();
        for (int i = 0; i < descLines.size(); i++) {
            graphics.text(uiFont, descLines.get(i), mainX + 72, mainTop + 40 + i * DESCRIPTION_LINE_HEIGHT,
                    DeckTheme.TEXT_SECONDARY, false);
        }
        drawTabs(graphics);
        drawOptionLabels(graphics, mouseX, mouseY);
        Component displayedStatus = !status.getString().isEmpty() ? status
                : selected.isDirty() ? Component.translatable("moddeck.unsaved") : Component.empty();
        if (!displayedStatus.getString().isEmpty()) {
            int statusColor = statusError ? 0xFFFF9E9E
                    : selected.isDirty() ? 0xFFFFCC66 : DeckTheme.SUCCESS;
            graphics.text(uiFont, fit(displayedStatus,
                            Math.max(40, footerActionsLeft - mainX - 28)),
                    mainX + 18, mainBottom - 26, statusColor, false);
        }
    }

    private void drawSelectedSidebarBackground(GuiGraphicsExtractor graphics) {
        int y = 119;
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (definition == selected) {
                DeckTheme.roundedRect(graphics, MARGIN + 1, y, sidebarWidth - 2, 42, 6, DeckTheme.ACCENT_MUTED);
            }
            y += 44;
        }
    }

    private void drawSidebarDefinitions(GuiGraphicsExtractor graphics) {
        int y = 119;
        int bottom = uiHeight - 16;
        graphics.enableScissor(MARGIN, 115, MARGIN + sidebarWidth, bottom);
        for (ConfigDefinition definition : filteredDefinitions()) {
            if (y + 42 > bottom) break;
            drawModIcon(graphics, definition.modId(), MARGIN + 11, y + 7, 28);
            graphics.text(uiFont, highlightedText(definition.titleText().component().getString(), modQuery),
                    MARGIN + 48, y + 17, DeckTheme.TEXT, false);
            if (definition == selected) DeckIcons.draw(graphics, DeckIcons.Icon.CHEVRON_RIGHT,
                    MARGIN + sidebarWidth - 24, y + 12, 16, DeckTheme.TEXT);
            y += 44;
        }
        graphics.disableScissor();
    }

    private void drawTabs(GuiGraphicsExtractor graphics) {
        if (selected == null || selected.categories().size() <= 1) return;
        int tabX = categoryTabsX();
        int tabY = categoryTabsTop + 3;
        List<Integer> renderedWidths = renderedTabWidths();
        List<Integer> widths = tabLayout.widths();
        if (tabLayout.overflow()) {
            graphics.enableScissor(categoryTabsX(), categoryTabsTop,
                    categoryTabsX() + categoryTabsViewportWidth(), categoryTabsTop + 30);
        }
        for (int visibleIndex = 0; visibleIndex < renderedWidths.size(); visibleIndex++) {
            int categoryIndex = firstVisibleCategory + visibleIndex;
            if (categoryIndex >= selected.categories().size()) break;
            ConfigCategory category = selected.categories().get(categoryIndex);
            int color = categoryIndex == activeCategory ? DeckTheme.ACCENT : DeckTheme.TEXT_MUTED;
            int naturalWidth = widths.get(categoryIndex);
            int effectiveWidth = renderedWidths.get(visibleIndex);
            Component label = category.displayNameText().component();
            Component highlightedLabel = highlightedText(label.getString(), optionQuery);
            graphics.text(uiFont, highlightedLabel,
                    tabX + naturalWidth / 2 - uiFont.width(highlightedLabel) / 2,
                    tabY + 4, color, false);
            if (categoryIndex == activeCategory) {
                graphics.fill(tabX, categoryTabsTop + 26, tabX + effectiveWidth - 8,
                        categoryTabsTop + 28, DeckTheme.ACCENT);
            }
            tabX += effectiveWidth;
        }
        if (tabLayout.overflow()) graphics.disableScissor();
    }

    private void drawModIcon(GuiGraphicsExtractor graphics, String modId, int x, int y, int size) {
        if (!ModIconTextures.draw(graphics, modId, x, y, size)) {
            DeckTheme.modCube(graphics, x, y, size);
        }
    }

    private void drawOptionLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hoveredOption = null;
        if (editingList != null) return;
        if (renderedOptions.isEmpty()) {
            DeckTheme.centeredText(graphics, uiFont, Component.translatable("moddeck.category.empty"),
                    mainX + mainWidth / 2, contentTop + (contentBottom - contentTop) / 2,
                    DeckTheme.TEXT_MUTED);
            return;
        }
        int y = contentTop + 8 - scrollOffset;
        graphics.enableScissor(mainX + 14, contentTop, mainX + mainWidth - 14, contentBottom);
        for (int index = 0; index < renderedOptions.size(); index++) {
            ConfigOption<?> option = renderedOptions.get(index);
            int rowHeight = renderedRowHeights.get(index);
            if (y + rowHeight > contentTop && y < contentBottom) {
                if (option instanceof DescriptionOption) {
                    List<FormattedCharSequence> lines = uiFont.split(
                            highlightedText(option.displayNameText().component().getString(), optionQuery),
                            Math.max(90, mainWidth - 60));
                    int textHeight = lines.size() * DESCRIPTION_LINE_HEIGHT;
                    int textY = y + Math.max(0, (rowHeight - textHeight) / 2);
                    for (int line = 0; line < lines.size(); line++) {
                        graphics.text(uiFont, lines.get(line), mainX + 30,
                                textY + line * DESCRIPTION_LINE_HEIGHT, DeckTheme.TEXT_SECONDARY, false);
                    }
                } else {
                int labelColor = option.isEnabled() ? DeckTheme.TEXT : DeckTheme.TEXT_MUTED;
                Component optionLabel = highlightedText(option.displayNameText().component().getString(), optionQuery);
                graphics.text(uiFont, optionLabel, mainX + 30, y + 13, labelColor, false);
                if (option.isRestartRequired()) {
                    graphics.text(uiFont, Component.translatable("moddeck.requires_restart"),
                            mainX + 36 + uiFont.width(optionLabel), y + 13,
                            DeckTheme.TEXT_MUTED, false);
                }
                int descY = y + 28;
                if (option.validationError().isPresent()) {
                    for (FormattedCharSequence line : uiFont.split(option.validationError().orElseThrow().component(),
                            Math.max(90, mainWidth / 2 - 45))) {
                        graphics.text(uiFont, line, mainX + 30, descY, 0xFFFF9E9E, false);
                        descY += DESCRIPTION_LINE_HEIGHT;
                    }
                } else if (!option.descriptionText().isEmpty()) {
                    for (FormattedCharSequence line : uiFont.split(
                            highlightedText(option.descriptionText().component().getString(), optionQuery),
                            Math.max(90, mainWidth / 2 - 45))) {
                        graphics.text(uiFont, line, mainX + 30, descY, DeckTheme.TEXT_SECONDARY, false);
                        descY += DESCRIPTION_LINE_HEIGHT;
                    }
                }
                }
                if (index < renderedOptions.size() - 1) {
                    graphics.fill(mainX + 30, y + rowHeight - 1, mainX + mainWidth - 30,
                            y + rowHeight, DeckTheme.DIVIDER);
                }
                if (mouseX >= mainX + 20 && mouseX < mainX + mainWidth - 20
                        && mouseY >= y && mouseY < y + rowHeight && !option.tooltips().isEmpty()) {
                    hoveredOption = option;
                }
            }
            y += rowHeight;
        }
        graphics.disableScissor();
    }

    private void drawOptionTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredOption == null || children().stream().anyMatch(child ->
                child instanceof ExpandableOptionWidget popup && popup.isExpanded())) return;
        if (children().stream().anyMatch(child -> child instanceof AbstractWidget widget
                && widget.visible && widget.isMouseOver(mouseX, mouseY))) return;
        int maximumTextWidth = Math.min(210, Math.max(120, mainWidth / 3));
        List<FormattedCharSequence> lines = hoveredOption.tooltips().stream()
                .flatMap(text -> uiFont.split(text.component(), maximumTextWidth).stream()).toList();
        if (lines.isEmpty()) return;
        int textWidth = lines.stream().mapToInt(uiFont::width).max().orElse(0);
        int boxWidth = textWidth + 16;
        int boxHeight = lines.size() * DESCRIPTION_LINE_HEIGHT + 12;
        int x = Math.min(uiWidth - boxWidth - 6, mouseX + 10);
        int y = mouseY + 9;
        if (y + boxHeight > uiHeight - 6) y = Math.max(6, mouseY - boxHeight - 9);
        x = Math.max(6, x);
        DeckTheme.border(graphics, x, y, boxWidth, boxHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(uiFont, lines.get(index), x + 8, y + 7 + index * DESCRIPTION_LINE_HEIGHT,
                    DeckTheme.TEXT_SECONDARY, false);
        }
    }

    private List<ConfigDefinition> filteredDefinitions() {
        if (modQuery.isBlank()) return definitions;
        return definitions.stream().filter(definition -> definition.titleText().component().getString()
                        .toLowerCase(Locale.ROOT).contains(modQuery)
                || definition.modId().toLowerCase(Locale.ROOT).contains(modQuery)).toList();
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
        stopListEditing();
        status = Component.translatable("moddeck.reset_ready");
        statusError = false;
        requestWidgetRebuild();
    }

    private void applyPreset(ConfigPreset preset) {
        try {
            selected.applyPreset(preset.id());
            status = Component.translatable("moddeck.preset.applied", preset.displayText().component());
            statusError = false;
            requestWidgetRebuild();
        } catch (IllegalArgumentException exception) {
            status = Component.translatable("moddeck.preset.failed");
            statusError = true;
            LOGGER.log(Level.WARNING,
                    "Could not apply preset " + preset.id() + " for " + selected.modId(), exception);
        }
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

    /**
     * Highlights every case-insensitive query match while leaving unmatched spans unstyled so the
     * caller's normal enabled/disabled text color still applies. Building a Component before line
     * wrapping preserves highlight spans across wrapped descriptions and translated labels.
     */
    private Component highlightedText(String text, String query) {
        if (query == null || query.isBlank() || text.isEmpty()) return Component.literal(text);
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        MutableComponent result = Component.empty();
        int cursor = 0;
        int match;
        while ((match = normalizedText.indexOf(normalizedQuery, cursor)) >= 0) {
            if (match > cursor) result.append(Component.literal(text.substring(cursor, match)));
            int end = match + normalizedQuery.length();
            result.append(Component.literal(text.substring(match, end))
                    .withStyle(style -> style.withColor(DeckTheme.ACCENT)));
            cursor = end;
        }
        if (cursor < text.length()) result.append(Component.literal(text.substring(cursor)));
        return result;
    }

    private void updateModQuery(String value) {
        // Mod filtering is render-time; just keep the normalized query. The live search field
        // widget is already showing the typed text and holds its own focus/caret, so do not
        // call setValue or request a rebuild, which would re-create children and destabilize typing.
        modQuery = value.trim().toLowerCase(Locale.ROOT);
    }

    private void updateOptionQuery(String value) {
        optionQuery = value.trim().toLowerCase(Locale.ROOT);
        if (selected != null && !optionQuery.isBlank()) {
            for (int index = 0; index < selected.categories().size(); index++) {
                ConfigCategory category = selected.categories().get(index);
                if (category.displayNameText().component().getString().toLowerCase(Locale.ROOT).contains(optionQuery)
                        || category.options().stream().anyMatch(this::matchesOptionQuery)) {
                    activeCategory = index;
                    ensureActiveCategoryVisible();
                    break;
                }
            }
        }
        scrollOffset = 0;
        // Request exactly one deferred rebuild; do not rebuild synchronously inside the
        // EditBox responder to avoid recursive widget/focus churn while typing.
        refocusOptionSearch = true;
        requestWidgetRebuild();
    }

    private List<ConfigOption<?>> visibleOptions(ConfigCategory category) {
        List<ConfigOption<?>> flattened = flattenOptions(category.options(), !optionQuery.isBlank());
        boolean categoryMatches = !optionQuery.isBlank() && category.displayNameText().component().getString()
                .toLowerCase(Locale.ROOT).contains(optionQuery);
        return optionQuery.isBlank() || categoryMatches
                ? flattened : flattened.stream().filter(this::matchesOptionQuery).toList();
    }

    private boolean matchesOptionQuery(ConfigOption<?> option) {
        return option.displayNameText().component().getString().toLowerCase(Locale.ROOT).contains(optionQuery)
                || option.descriptionText().component().getString().toLowerCase(Locale.ROOT).contains(optionQuery)
                || option.id().contains(optionQuery)
                || option.searchAliases().stream().map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(alias -> alias.contains(optionQuery))
                || option instanceof SubcategoryOption subcategory
                && flattenOptions(subcategory.children(), true).stream().anyMatch(this::matchesOptionQuery);
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
        if (selected == null || selected.categories().size() <= 1) {
            return selected == null ? 0 : selected.categories().size();
        }
        return tabLayout.overflow()
                ? ModListLayout.visibleTabCount(tabLayout, firstVisibleCategory, categoryTabsViewportWidth())
                : selected.categories().size();
    }

    private List<Integer> renderedTabWidths() {
        if (!tabLayout.overflow()) return tabLayout.widths();
        return ModListLayout.renderedTabWidths(tabLayout, firstVisibleCategory,
                categoryTabsViewportWidth());
    }

    private boolean categoriesOverflow() {
        return tabLayout.overflow();
    }

    private int categoryTabsX() {
        return mainX + 20 + (tabLayout.overflow() ? CATEGORY_ARROW_SIZE + CATEGORY_ARROW_GAP : 0);
    }

    /** Full width available for the category tab row before deciding whether arrows are needed. */
    private int categoryRowWidth() {
        return Math.max(1, mainWidth - 40);
    }

    /** Width left for actual tabs once overflow arrows are confirmed and reserved. */
    private int categoryTabsViewportWidth() {
        return tabLayout.viewportWidth(CATEGORY_TAB_MIN_WIDTH, CATEGORY_TAB_HORIZONTAL_PADDING);
    }

    /** Kept for call sites that already expect the arrow-reserved viewport width. */
    private int categoryTabsWidth() {
        return categoryTabsViewportWidth();
    }

    private void ensureActiveCategoryVisible() {
        int visible = visibleTabCount();
        if (activeCategory < firstVisibleCategory) firstVisibleCategory = activeCategory;
        if (activeCategory >= firstVisibleCategory + visible) firstVisibleCategory = activeCategory - visible + 1;
        int maximum = Math.max(0, selected.categories().size() - visible);
        firstVisibleCategory = Math.max(0, Math.min(maximum, firstVisibleCategory));
    }

    private List<FormattedCharSequence> modDescriptionLines() {
        if (selected == null || selected.descriptionText().isEmpty()) return List.of();
        return uiFont.split(selected.descriptionText().component(), Math.max(80, mainWidth - 104));
    }

    private static ModListLayout.Label categoryLabel(ConfigCategory category) {
        return new ModListLayout.Label() {
            public String id() { return category.id(); }
            public net.minecraft.network.chat.FormattedText displayText() {
                return category.displayNameText().component();
            }
        };
    }

    public void startListEditing(ListOption<?> option) {
        this.editingList = option;
        this.editingListCategory = selected != null ? selected.categories().get(activeCategory) : null;
        this.scrollOffset = 0;
        this.optionQuery = "";
        rebuildWidgets();
    }

    public void stopListEditing() {
        this.editingList = null;
        this.editingListCategory = null;
        this.listEditorPane = null;
        rebuildWidgets();
    }

    private int actionButtonWidth(Component text) {
        // Action buttons include a 16px icon, spacing around the icon, and balanced text padding.
        // Measuring translated text here keeps Japanese and other longer labels from clipping.
        return Math.max(82, uiFont.width(text) + 48);
    }

    private int optionSearchWidth() { return Math.min(210, Math.max(160, mainWidth / 3)); }

    private void requestWidgetRebuild() { pendingWidgetRebuild = true; }

    private void requestWidgetRebuildAndMarkDirty() {
        // Per-entry reset has already mutated the option draft. Mark the screen dirty so the
        // save button reflects the unsaved change, and schedule a deferred rebuild so every
        // option widget is recreated from the updated draft on the next safe tick.
        markDirty();
        requestWidgetRebuild();
    }

    private void syncThemePopupState() {
        if (optionSearchField == null) return;
        boolean themeExpanded = children().stream().anyMatch(child ->
                child instanceof ThemeSelectorWidget theme && theme.isExpanded());
        optionSearchField.visible = !themeExpanded;
        optionSearchField.active = !themeExpanded;
        if (themeExpanded && getFocused() == optionSearchField) {
            optionSearchField.setFocused(false);
            setFocused(null);
        }
    }

    private MouseButtonEvent transform(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, event.buttonInfo());
    }

    private class CategoryScrollButton extends AbstractWidget {
        private final int direction;
        CategoryScrollButton(int x, int y, int direction) {
            super(x, y, CATEGORY_ARROW_SIZE, CATEGORY_ARROW_SIZE, Component.translatable(
                    direction < 0 ? "moddeck.category.previous" : "moddeck.category.next"));
            this.direction = direction;
            active = direction < 0 ? firstVisibleCategory > 0
                    : firstVisibleCategory + visibleTabCount() < selected.categories().size();
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int visible = visibleTabCount();
            int maximum = Math.max(0, selected.categories().size() - visible);
            firstVisibleCategory = Math.max(0, Math.min(maximum, firstVisibleCategory + direction));
            requestWidgetRebuild();
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            DeckTheme.roundedRect(graphics, getX(), getY(), CATEGORY_ARROW_SIZE, CATEGORY_ARROW_SIZE,
                    5, isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD);
            DeckIcons.draw(graphics, direction < 0 ? DeckIcons.Icon.CHEVRON_LEFT : DeckIcons.Icon.CHEVRON_RIGHT,
                    getX() + 4, getY() + 4, 16, DeckTheme.TEXT);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
