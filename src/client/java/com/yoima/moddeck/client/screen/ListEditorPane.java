package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.ConfigCategory;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ConfigValidator;
import com.yoima.moddeck.api.validation.ValidationResult;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.widget.DeckButton;
import com.yoima.moddeck.client.widget.ExpandableOptionWidget;
import com.yoima.moddeck.client.widget.OptionWidgetRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Sub-page rendered inside ModListScreen's right content area for editing a ListOption.
 * Each row builds a real ConfigOption of the element's type so the existing widget registry
 * (TextFieldWidget, SliderWidget, SwitchWidget, EnumSelectorWidget, etc.) can be reused as-is.
 * Changes are written back to a temporary element list and finally applied to the parent
 * ListOption through the standard draft/save flow used by every other Mod Deck entry.
 */
public final class ListEditorPane {
    private static final int ROW_HEIGHT = 36;
    private static final int HANDLE_WIDTH = 26;
    private static final int DELETE_WIDTH = 28;
    private static final int GAP = 8;
    private static final int MAX_STRING_LENGTH = 4096;

    private final ListOption<?> option;
    private final ConfigCategory category;
    private final Font font;
    private final Consumer<List<?>> onChanged;
    private final Runnable markDirty;
    private final Runnable onBack;
    private final List<Object> elements;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final int contentX;
    private final int contentY;
    private final int contentWidth;
    private final int contentHeight;
    private int scrollOffset;
    private int draggedIndex = -1;
    private int dragMouseY;
    private int dropIndex = -1;
    private Component error = Component.empty();
    private DeckButton backButton;
    private DeckButton addButton;
    // Pane-owned focus: since row widgets are not registered with Screen#addRenderableWidget,
    // Screen-level focus never reaches them. The pane must track and dispatch keyboard/char
    // events to exactly one widget so TextFieldWidget and SliderWidget behave normally.
    private AbstractWidget focusedWidget;

    public ListEditorPane(Font font, ListOption<?> option, ConfigCategory category,
                          int contentX, int contentY, int contentWidth, int contentHeight,
                          Consumer<List<?>> onChanged, Runnable markDirty, Runnable onBack) {
        this.font = font;
        this.option = option;
        this.category = category;
        this.contentX = contentX;
        this.contentY = contentY;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.onChanged = onChanged;
        this.markDirty = markDirty;
        this.onBack = onBack;
        this.elements = new ArrayList<>(option.draftValue());
    }

    public ListOption<?> option() { return option; }
    public ConfigCategory category() { return category; }
    public List<AbstractWidget> widgets() { return widgets; }

    public void refresh() {
        focusedWidget = null;
        widgets.clear();
        buildHeader();
        buildRows();
        buildFooter();
    }

    private void buildHeader() {
        int headerY = contentY + 10;
        backButton = new DeckButton(contentX, headerY, 90, 26,
                Component.translatable("moddeck.list.back_to", category.displayNameText().component()),
                DeckButton.Style.BACK, onBack);
        widgets.add(backButton);
    }

    private void buildRows() {
        int usableWidth = contentWidth - HANDLE_WIDTH - DELETE_WIDTH - GAP * 2;
        int valueX = contentX + HANDLE_WIDTH + GAP;
        int y = contentY + 56 - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < contentY + 48 || rowY > contentY + contentHeight - 42) continue;
            AbstractWidget widget = createElementWidget(valueX, rowY + 4, usableWidth, index);
            if (widget != null) widgets.add(widget);
        }
    }

    private void buildFooter() {
        if (!option.insertionAllowed() || elements.size() >= option.maximumSize()) return;
        int footerY = contentY + contentHeight - 34;
        addButton = new DeckButton(contentX + HANDLE_WIDTH + GAP, footerY, 110, 28,
                Component.translatable("moddeck.list.add_entry"), DeckButton.Style.THEME, this::addEntry);
        widgets.add(addButton);
    }

    /**
     * Builds a concrete ConfigOption matching the element's runtime type so the existing
     * OptionWidgetRegistry can create the same widgets used elsewhere in Mod Deck.
     * The option's change callback writes the new value back into our temporary element list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractWidget createElementWidget(int x, int y, int width, int index) {
        Object value = elements.get(index);
        ConfigOption<?> proxy = createElementOption(value);
        if (proxy == null) return null;
        AbstractWidget widget = OptionWidgetRegistry.create(font, x, y, width, proxy,
                () -> {
                    commitProxyValue(index, value, proxy);
                }, true);
        widget.active = option.editable() && proxy.editable();
        if (widget instanceof ExpandableOptionWidget popup) {
            popup.setPopupViewport(contentY + 48, contentY + contentHeight - 42);
        }
        return widget;
    }

    /**
     * Creates a real typed option for the element value. We reuse the same concrete option
     * classes that the rest of the UI uses so widget factories receive exactly the class
     * they expect (e.g. IntegerOption for SliderWidget). String fallback covers custom codecs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfigOption<?> createElementOption(Object value) {
        ConfigText name = option.displayNameText();
        ConfigText description = option.descriptionText();
        String id = option.id() + "_element";
        @SuppressWarnings({"rawtypes", "unchecked"})
        ListOption rawOption = (ListOption) option;
        ConfigValidator rawValidator = v -> rawOption.validateElement(v);
        if (value instanceof String s) {
            StringOption opt = new StringOption(id, name, description, s, MAX_STRING_LENGTH);
            opt.validateWith(rawValidator);
            return opt;
        }
        if (value instanceof Boolean b) {
            BooleanOption opt = new BooleanOption(id, name, description, b);
            opt.validateWith(rawValidator);
            return opt;
        }
        if (value instanceof Enum<?> e) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class enumType = (Class) e.getDeclaringClass();
            @SuppressWarnings({"rawtypes", "unchecked"})
            EnumOption opt = new EnumOption(id, name, description, e, enumType);
            opt.validateWith(rawValidator);
            return opt;
        }
        // Fallback: expose the element as a string using the list's own codec. This keeps any
        // custom codec-backed type editable even when there is no dedicated widget for it.
        ListOption raw = (ListOption) option;
        String encoded = raw.encodeElement(value);
        StringOption opt = new StringOption(id, name, description, encoded, MAX_STRING_LENGTH);
        opt.validateWith(v -> {
            try {
                Object decoded = raw.decodeElement((String) v);
                return raw.validateElement(decoded);
            } catch (RuntimeException exception) {
                return ValidationResult.invalid(ConfigText.translatable("moddeck.list.invalid_element"));
            }
        });
        return opt;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void commitProxyValue(int index, Object originalValue, ConfigOption<?> proxy) {
        Object draftValue = proxy.draftValue();
        if (originalValue instanceof String || originalValue instanceof Boolean || originalValue instanceof Enum<?>) {
            updateElement(index, draftValue);
            error = Component.empty();
            return;
        }

        // Codec-backed values use a StringOption only as an editor. Decode before updating the
        // parent list so numeric and custom element types never leak into draft state as strings.
        try {
            ListOption raw = (ListOption) option;
            Object decoded = raw.decodeElement((String) draftValue);
            ValidationResult validation = raw.validateElement(decoded);
            if (!validation.valid()) {
                error = validation.error().orElseThrow().component();
                return;
            }
            updateElement(index, decoded);
            error = Component.empty();
        } catch (RuntimeException exception) {
            error = Component.translatable("moddeck.list.invalid_element");
        }
    }

    @SuppressWarnings("unchecked")
    private <V> void updateElement(int index, V newValue) {
        elements.set(index, newValue);
        onChanged.accept(List.copyOf(elements));
        markDirty.run();
    }

    private void addEntry() {
        if (!option.insertionAllowed() || elements.size() >= option.maximumSize()) return;
        try {
            Object element = option.newElement();
            elements.add(element);
            onChanged.accept(List.copyOf(elements));
            markDirty.run();
            scrollOffset = Math.max(0, elements.size() * ROW_HEIGHT - (contentHeight - 110));
            refresh();
        } catch (RuntimeException exception) {
            error = Component.translatable("moddeck.list.no_default");
        }
    }

    private void deleteEntry(int index) {
        if (!option.deletionAllowed() || elements.size() <= option.minimumSize()) return;
        elements.remove(index);
        onChanged.accept(List.copyOf(elements));
        markDirty.run();
        refresh();
    }

    private void finishReorder(int from, int to) {
        if (from < 0 || from >= elements.size() || to < 0 || to > elements.size()) {
            draggedIndex = -1;
            dropIndex = -1;
            return;
        }
        if (from == to) {
            draggedIndex = -1;
            dropIndex = -1;
            return;
        }
        Object moved = elements.remove(from);
        // After remove, indices above `from` shift down by 1. Adjust `to` so the element
        // lands at the visual position the user saw, not one past the end of the shrunk list.
        int adjustedTo = to > from ? to - 1 : to;
        if (adjustedTo < 0 || adjustedTo > elements.size()) {
            elements.add(from, moved);
            draggedIndex = -1;
            dropIndex = -1;
            return;
        }
        elements.add(adjustedTo, moved);
        onChanged.accept(List.copyOf(elements));
        markDirty.run();
        draggedIndex = -1;
        dropIndex = -1;
        refresh();
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int headerY = contentY + 10;
        graphics.text(font, option.displayNameText().component(), contentX + 102, headerY + 6,
                DeckTheme.TEXT, false);
        Component count = Component.translatable("moddeck.list.count", elements.size(), option.maximumSize());
        int countWidth = font.width(count.getString());
        DeckTheme.roundedRect(graphics, contentX + contentWidth - countWidth - 28, headerY + 2,
                countWidth + 18, 20, 5, DeckTheme.FIELD);
        graphics.text(font, count, contentX + contentWidth - countWidth - 19, headerY + 7,
                DeckTheme.TEXT_SECONDARY, false);
        if (!option.descriptionText().isEmpty()) {
            graphics.text(font, option.descriptionText().component(), contentX, contentY + 40,
                    DeckTheme.TEXT_SECONDARY, false);
        }
        if (!error.getString().isEmpty()) {
            graphics.text(font, error, contentX, contentY + contentHeight - 20, 0xFFFF9E9E, false);
        }

        graphics.enableScissor(contentX, contentY + 48, contentX + contentWidth, contentY + contentHeight - 42);
        if (elements.isEmpty()) {
            DeckTheme.centeredText(graphics, font, Component.translatable("moddeck.list.empty"),
                    contentX + contentWidth / 2, contentY + 48 + (contentHeight - 90) / 2,
                    DeckTheme.TEXT_MUTED);
        }

        int y = contentY + 56 - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < contentY + 48 || rowY > contentY + contentHeight - 42) continue;
            boolean isDragged = draggedIndex == index;
            int alpha = isDragged ? 0x66FFFFFF : 0xFFFFFFFF;
            int tint = DeckTheme.TEXT & alpha;
            DeckTheme.roundedRect(graphics, contentX, rowY, contentWidth, ROW_HEIGHT - 2, 5,
                    isDragged ? DeckTheme.ACCENT_MUTED : DeckTheme.FIELD);
            if (isDragged) {
                graphics.fill(contentX + 2, rowY + ROW_HEIGHT / 2 - 1,
                        contentX + contentWidth - 2, rowY + ROW_HEIGHT / 2, DeckTheme.ACCENT);
            }
            if (option.reorderingAllowed()) {
                DeckIcons.draw(graphics, DeckIcons.Icon.GRIP_VERTICAL,
                        contentX + 4, rowY + (ROW_HEIGHT - 22) / 2, 18, tint);
            }
            if (option.deletionAllowed() && elements.size() > option.minimumSize()) {
                int delX = contentX + contentWidth - DELETE_WIDTH - 4;
                DeckIcons.draw(graphics, DeckIcons.Icon.CLOSE, delX + 6, rowY + (ROW_HEIGHT - 16) / 2,
                        16, 0xFFFF6B6B);
            }
            if (dropIndex == index && draggedIndex >= 0) {
                graphics.fill(contentX + HANDLE_WIDTH, rowY - 2,
                        contentX + contentWidth - DELETE_WIDTH - GAP, rowY, DeckTheme.ACCENT);
            }
        }
        if (dropIndex == elements.size() && draggedIndex >= 0) {
            int lastY = y + elements.size() * ROW_HEIGHT;
            graphics.fill(contentX + HANDLE_WIDTH, lastY - 2,
                    contentX + contentWidth - DELETE_WIDTH - GAP, lastY, DeckTheme.ACCENT);
        }
        graphics.disableScissor();

        for (AbstractWidget child : widgets) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }
        for (AbstractWidget child : widgets) {
            if (child instanceof ExpandableOptionWidget popup) {
                popup.extractPopupRenderState(graphics, mouseX, mouseY, delta);
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (backButton != null && backButton.isMouseOver(mouseX, mouseY)) {
            backButton.onClick(event, doubleClick);
            return true;
        }
        if (addButton != null && addButton.isMouseOver(mouseX, mouseY)) {
            addButton.onClick(event, doubleClick);
            return true;
        }
        for (AbstractWidget child : widgets) {
            if (child instanceof ExpandableOptionWidget popup
                    && popup.handleExpandedClick(mouseX, mouseY)) return true;
        }
        // Value widgets come after handle/delete so those hot zones win when overlapping.
        for (AbstractWidget child : widgets) {
            if (child.active && child.visible && child.isMouseOver(mouseX, mouseY)
                    && child.mouseClicked(event, doubleClick)) {
                focusWidget(child);
                return true;
            }
        }
        // Click landed on empty pane area; clear focus so no stale field keeps keyboard input.
        focusWidget(null);
        int y = contentY + 56 - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < contentY + 48 || rowY > contentY + contentHeight - 42) continue;
            int delX = contentX + contentWidth - DELETE_WIDTH - 4;
            if (mouseX >= delX && mouseX < delX + DELETE_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                deleteEntry(index);
                return true;
            }
            if (option.reorderingAllowed()
                    && mouseX >= contentX && mouseX < contentX + HANDLE_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                draggedIndex = index;
                dragMouseY = (int) mouseY;
                dropIndex = index;
                return true;
            }
        }
        return false;
    }

    // Click fell through to a value widget. Capture pane-level focus so keyboard input reaches
    // the widget, and let the widget handle the click itself.
    public void focusWidget(AbstractWidget widget) {
        if (focusedWidget != null && focusedWidget != widget) {
            focusedWidget.setFocused(false);
        }
        if (widget != null) widget.setFocused(true);
        focusedWidget = widget;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggedIndex >= 0) {
            dragMouseY = (int) event.y();
            int relativeY = dragMouseY - (contentY + 56) + scrollOffset;
            dropIndex = Math.max(0, Math.min(elements.size(), relativeY / ROW_HEIGHT));
            return true;
        }
        for (AbstractWidget child : widgets) {
            if (child.active && child.visible && child.isMouseOver(event.x(), event.y())
                    && child.mouseDragged(event, dragX, dragY)) return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedIndex >= 0) {
            finishReorder(draggedIndex, dropIndex);
            return true;
        }
        for (AbstractWidget child : widgets) {
            if (child.mouseReleased(event)) return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < contentX || mouseX > contentX + contentWidth
                || mouseY < contentY + 48 || mouseY > contentY + contentHeight - 42) return false;
        int totalHeight = elements.size() * ROW_HEIGHT + 100;
        int maxScroll = Math.max(0, totalHeight - (contentHeight - 90));
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(scrollY * 18)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        refresh();
        return true;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (focusedWidget != null && focusedWidget.keyPressed(event)) return true;
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (focusedWidget != null && focusedWidget.charTyped(event)) return true;
        return false;
    }
}
