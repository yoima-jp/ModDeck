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
    private static final int PANE_PADDING = 12;
    private static final int ROW_HEIGHT = 44;
    private static final int ROW_CARD_HEIGHT = 36;
    private static final int HANDLE_WIDTH = 24;
    private static final int DELETE_WIDTH = 28;
    private static final int GAP = 8;
    private static final int MAX_STRING_LENGTH = 4096;
    private static final int DESCRIPTION_LINE_HEIGHT = 13;
    private static final int BACK_BUTTON_MIN_WIDTH = 80;
    private static final int BACK_BUTTON_MAX_WIDTH = 220;

    /** A row may exist before its text can be decoded into T; its prior value stays published. */
    private record PendingElement(String text, Object fallbackValue) {}

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
        addButton = null;
        widgets.clear();
        buildHeader();
        buildRows();
        buildFooter();
    }

    private void buildHeader() {
        int headerY = contentY + PANE_PADDING;
        // Show the target ListOption's translated item label in the back button instead of the
        // category name. The original separate duplicate label is removed from render(); this keeps
        // the back purpose accessible through the button's message and narration text.
        Component itemLabel = option.displayNameText().component();
        int labelWidth = font.width(itemLabel.getString());
        int available = innerWidth() - 110 - GAP * 2; // reserve space for count badge + margins
        int backWidth = Math.max(BACK_BUTTON_MIN_WIDTH,
                Math.min(BACK_BUTTON_MAX_WIDTH, Math.min(available, labelWidth + 44)));
        backButton = new DeckButton(innerX(), headerY, backWidth, 28,
                Component.translatable("moddeck.list.back_to", itemLabel),
                DeckButton.Style.BACK, onBack);
        widgets.add(backButton);
    }

    private void buildRows() {
        int usableWidth = innerWidth() - HANDLE_WIDTH - DELETE_WIDTH - GAP * 2;
        int valueX = innerX() + HANDLE_WIDTH + GAP;
        int y = rowsTop() - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_CARD_HEIGHT < viewportTop() || rowY > viewportBottom()) continue;
            AbstractWidget widget = createElementWidget(valueX, rowY + 4, usableWidth, index);
            if (widget != null) widgets.add(widget);
        }
    }

    private void buildFooter() {
        if (!option.insertionAllowed() || elements.size() >= option.maximumSize()) return;
        int footerY = footerY();
        addButton = new DeckButton(innerX(), footerY, 110, 28,
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
        if (value instanceof PendingElement pending) {
            return createCodecEditor(id, name, description, pending.text());
        }
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
        return createCodecEditor(id, name, description, raw.encodeElement(value));
    }

    private StringOption createCodecEditor(String id, ConfigText name, ConfigText description,
                                           String encoded) {
        // Decoding is deliberately deferred to commitProxyValue. The text widget must accept
        // intermediate input such as "-" so an incomplete edit survives scrolling/rebuilds.
        return new StringOption(id, name, description, encoded, MAX_STRING_LENGTH);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void commitProxyValue(int index, Object originalValue, ConfigOption<?> proxy) {
        Object draftValue = proxy.draftValue();
        if (!(originalValue instanceof PendingElement)
                && (originalValue instanceof String || originalValue instanceof Boolean
                || originalValue instanceof Enum<?>)) {
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
                keepPendingText(index, originalValue, (String) draftValue);
                error = validation.error().orElseThrow().component();
                return;
            }
            updateElement(index, decoded);
            error = Component.empty();
        } catch (RuntimeException exception) {
            keepPendingText(index, originalValue, (String) draftValue);
            error = Component.translatable("moddeck.list.invalid_element");
        }
    }

    @SuppressWarnings("unchecked")
    private <V> void updateElement(int index, V newValue) {
        elements.set(index, newValue);
        publishElements();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addEntry() {
        if (!option.insertionAllowed() || elements.size() >= option.maximumSize()) return;
        String initialText = "";
        try {
            initialText = option.newElementText();
            ListOption raw = (ListOption) option;
            Object decoded = raw.decodeElement(initialText);
            ValidationResult validation = raw.validateElement(decoded);
            elements.add(validation.valid() ? decoded : new PendingElement(initialText, null));
        } catch (RuntimeException exception) {
            // Blank input commonly cannot be decoded yet (numbers are the usual case). Keeping
            // it local lets the user type a complete value without placing null in the API list.
            elements.add(new PendingElement(initialText, null));
        }
        publishElements();
        scrollOffset = Math.max(0, elements.size() * ROW_HEIGHT - viewportHeight());
        error = Component.empty();
        refresh();
        if (elements.getLast() instanceof PendingElement) focusLastElementWidget();
    }

    private void deleteEntry(int index) {
        if (!option.deletionAllowed() || elements.size() <= option.minimumSize()) return;
        elements.remove(index);
        publishElements();
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
        publishElements();
        draggedIndex = -1;
        dropIndex = -1;
        refresh();
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int headerY = contentY + PANE_PADDING;
        // The list option label is now carried by the back button itself, so do not duplicate it
        // beside the button. The count badge and optional description remain visible.
        Component count = Component.translatable("moddeck.list.count", elements.size(), option.maximumSize());
        int countWidth = font.width(count.getString());
        // Position count immediately to the right of the back button, but never let it overflow
        // the right edge of the inner pane.
        int countX = Math.max(backButton.getX() + backButton.getWidth() + GAP,
                innerX() + innerWidth() - countWidth - 18);
        DeckTheme.roundedRect(graphics, countX, headerY + 4,
                countWidth + 18, 20, 5, DeckTheme.FIELD);
        graphics.text(font, count, countX + 9, headerY + 9,
                DeckTheme.TEXT_SECONDARY, false);
        if (!option.descriptionText().isEmpty()) {
            int descWidth = innerWidth();
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(option.descriptionText().component(), descWidth);
            int descY = contentY + 48;
            for (int i = 0; i < lines.size(); i++) {
                graphics.text(font, lines.get(i), innerX(), descY + i * DESCRIPTION_LINE_HEIGHT,
                        DeckTheme.TEXT_SECONDARY, false);
            }
        }
        if (!error.getString().isEmpty()) {
            graphics.text(font, error, innerX() + 122, footerY() + 10, 0xFFFF9E9E, false);
        }

        graphics.enableScissor(innerX(), viewportTop(), innerX() + innerWidth(), viewportBottom());
        if (elements.isEmpty()) {
            DeckTheme.centeredText(graphics, font, Component.translatable("moddeck.list.empty"),
                    innerX() + innerWidth() / 2, viewportTop() + viewportHeight() / 2,
                    DeckTheme.TEXT_MUTED);
        }

        int y = rowsTop() - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_CARD_HEIGHT < viewportTop() || rowY > viewportBottom()) continue;
            boolean isDragged = draggedIndex == index;
            int alpha = isDragged ? 0x66FFFFFF : 0xFFFFFFFF;
            int tint = DeckTheme.TEXT & alpha;
            DeckTheme.roundedRect(graphics, innerX(), rowY, innerWidth(), ROW_CARD_HEIGHT, 5,
                    isDragged ? DeckTheme.ACCENT_MUTED : DeckTheme.FIELD);
            if (isDragged) {
                graphics.fill(innerX() + 2, rowY + ROW_CARD_HEIGHT / 2 - 1,
                        innerX() + innerWidth() - 2, rowY + ROW_CARD_HEIGHT / 2, DeckTheme.ACCENT);
            }
            if (option.reorderingAllowed()) {
                DeckIcons.draw(graphics, DeckIcons.Icon.GRIP_VERTICAL,
                        innerX() + 3, rowY + (ROW_CARD_HEIGHT - 22) / 2, 18, tint);
            }
            if (option.deletionAllowed() && elements.size() > option.minimumSize()) {
                int delX = innerX() + innerWidth() - DELETE_WIDTH;
                DeckIcons.draw(graphics, DeckIcons.Icon.CLOSE, delX + 6, rowY + (ROW_CARD_HEIGHT - 16) / 2,
                        16, 0xFFFF6B6B);
            }
            if (dropIndex == index && draggedIndex >= 0) {
                graphics.fill(innerX() + HANDLE_WIDTH, rowY - 2,
                        innerX() + innerWidth() - DELETE_WIDTH - GAP, rowY, DeckTheme.ACCENT);
            }
        }
        if (dropIndex == elements.size() && draggedIndex >= 0) {
            int lastY = y + elements.size() * ROW_HEIGHT;
            graphics.fill(innerX() + HANDLE_WIDTH, lastY - 2,
                    innerX() + innerWidth() - DELETE_WIDTH - GAP, lastY, DeckTheme.ACCENT);
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
        int y = rowsTop() - scrollOffset;
        for (int index = 0; index < elements.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            if (rowY + ROW_CARD_HEIGHT < viewportTop() || rowY > viewportBottom()) continue;
            int delX = innerX() + innerWidth() - DELETE_WIDTH;
            if (mouseX >= delX && mouseX < delX + DELETE_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_CARD_HEIGHT) {
                deleteEntry(index);
                return true;
            }
            if (option.reorderingAllowed()
                    && mouseX >= innerX() && mouseX < innerX() + HANDLE_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_CARD_HEIGHT) {
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
            int relativeY = dragMouseY - rowsTop() + scrollOffset;
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
        if (mouseX < innerX() || mouseX > innerX() + innerWidth()
                || mouseY < viewportTop() || mouseY > viewportBottom()) return false;
        int maxScroll = Math.max(0, elements.size() * ROW_HEIGHT - viewportHeight());
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

    private void publishElements() {
        List<Object> committed = new ArrayList<>(elements.size());
        for (Object element : elements) {
            if (element instanceof PendingElement pending) {
                if (pending.fallbackValue() != null) committed.add(pending.fallbackValue());
            } else {
                committed.add(element);
            }
        }
        if (committed.equals(option.draftValue())) return;
        onChanged.accept(committed);
        markDirty.run();
    }

    private void keepPendingText(int index, Object originalValue, String text) {
        Object fallback = originalValue instanceof PendingElement pending
                ? pending.fallbackValue() : originalValue;
        elements.set(index, new PendingElement(text, fallback));
    }

    private void focusLastElementWidget() {
        for (int index = widgets.size() - 1; index >= 0; index--) {
            AbstractWidget widget = widgets.get(index);
            if (widget != addButton && widget != backButton) {
                focusWidget(widget);
                return;
            }
        }
    }

    private int innerX() { return contentX + PANE_PADDING; }
    private int innerWidth() { return Math.max(0, contentWidth - PANE_PADDING * 2); }
    private int viewportTop() {
        int descriptionLines = option.descriptionText().isEmpty() ? 0
                : font.split(option.descriptionText().component(), innerWidth()).size();
        return Math.max(contentY + 68,
                contentY + 55 + descriptionLines * DESCRIPTION_LINE_HEIGHT);
    }
    private int footerY() { return contentY + contentHeight - PANE_PADDING - 28; }
    private int viewportBottom() { return footerY() - GAP; }
    private int viewportHeight() { return Math.max(0, viewportBottom() - viewportTop()); }
    private int rowsTop() { return viewportTop() + GAP; }
}
