package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.option.ListOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.widget.DeckButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Compact Mod Deck card editor for list options with validation, insertion, deletion, and reordering. */
public final class ListEditorScreen<T> extends Screen {
    private static final int CARD_WIDTH = 372;
    private static final int ROW_HEIGHT = 32;
    private static final int MAX_VISIBLE_ROWS = 5;
    private static final int MIN_VISIBLE_ROWS = 1;
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 54;
    private static final int PADDING_X = 14;
    private static final int CARD_MARGIN = 24;
    private final Screen parent;
    private final ListOption<T> option;
    private final Runnable onChanged;
    private final List<String> encoded = new ArrayList<>();
    private int firstVisible;
    private Component error = Component.empty();

    public ListEditorScreen(Screen parent, ListOption<T> option, Runnable onChanged) {
        super(option.displayNameText().component());
        this.parent = parent;
        this.option = option;
        this.onChanged = onChanged;
        option.draftValue().stream().map(option::encodeElement).forEach(encoded::add);
    }

    /** Number of row slots shown right now, derived from list size and available screen space. */
    private int visibleRows() {
        int byContent = Math.min(encoded.size(), MAX_VISIBLE_ROWS);
        int atLeastOne = Math.max(MIN_VISIBLE_ROWS, byContent);
        int availableHeight = height - CARD_MARGIN;
        int maxByScreen = Math.max(MIN_VISIBLE_ROWS,
                (availableHeight - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
        return Math.min(atLeastOne, maxByScreen);
    }

    private int cardHeight() {
        return HEADER_HEIGHT + visibleRows() * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    private int cardWidth() { return Math.min(CARD_WIDTH, width - 24); }

    private int cardX() { return (width - cardWidth()) / 2; }

    private int cardY() { return Math.max(12, (height - cardHeight()) / 2); }

    private int listWidth() { return cardWidth() - PADDING_X * 2; }

    private int fieldWidth() { return listWidth() - 104; }

    private int bodyTop() { return cardY() + HEADER_HEIGHT; }

    private int bodyBottom() { return bodyTop() + visibleRows() * ROW_HEIGHT; }

    private int rowY(int row) { return bodyTop() + row * ROW_HEIGHT + (ROW_HEIGHT - 28) / 2; }

    @Override protected void init() {
        int cx = cardX();
        int fieldW = fieldWidth();
        int visibleRows = visibleRows();
        for (int row = 0; row < visibleRows && firstVisible + row < encoded.size(); row++) {
            int index = firstVisible + row;
            int y = rowY(row);
            EditBox field = new ListCellField(font, cx + PADDING_X, y, fieldW, 28,
                    Component.translatable("moddeck.list.element", index + 1));
            field.setValue(encoded.get(index));
            field.setMaxLength(4096);
            field.setResponder(value -> encoded.set(index, value));
            addRenderableWidget(field);
            int buttonY = y;
            addRenderableWidget(new DeckButton(cx + cardWidth() - PADDING_X - 90, buttonY, 24, 28,
                    Component.literal("↑"), DeckButton.Style.THEME, () -> move(index, -1)));
            addRenderableWidget(new DeckButton(cx + cardWidth() - PADDING_X - 63, buttonY, 24, 28,
                    Component.literal("↓"), DeckButton.Style.THEME, () -> move(index, 1)));
            DeckButton remove = new DeckButton(cx + cardWidth() - PADDING_X - 34, buttonY, 34, 28,
                    Component.literal("−"), DeckButton.Style.THEME, () -> remove(index));
            remove.active = option.deletionAllowed() && encoded.size() > option.minimumSize();
            addRenderableWidget(remove);
        }
        int footerY = cardY() + cardHeight() - FOOTER_HEIGHT + (FOOTER_HEIGHT - 28) / 2;
        DeckButton add = new DeckButton(cx + PADDING_X, footerY, 70, 28,
                Component.translatable("moddeck.list.add"), DeckButton.Style.THEME, this::addElement);
        add.active = option.insertionAllowed() && encoded.size() < option.maximumSize();
        addRenderableWidget(add);
        addRenderableWidget(new DeckButton(cx + cardWidth() - PADDING_X - 170, footerY, 78, 28,
                Component.translatable("moddeck.cancel"), DeckButton.Style.THEME, this::onClose));
        addRenderableWidget(new DeckButton(cx + cardWidth() - PADDING_X - 86, footerY, 78, 28,
                Component.translatable("moddeck.apply"), DeckButton.Style.PRIMARY, this::apply));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, DeckTheme.BACKGROUND);
        int cx = cardX();
        int cy = cardY();
        int cw = cardWidth();
        int ch = cardHeight();
        DeckTheme.roundedRect(graphics, cx, cy, cw, ch, 9, DeckTheme.CARD);
        DeckTheme.roundedRect(graphics, cx + 1, cy + HEADER_HEIGHT - 1, cw - 2,
                bodyBottom() - cy - HEADER_HEIGHT + 1, 8, DeckTheme.PANEL);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        int cx = cardX();
        int cy = cardY();
        var ui = DeckFonts.ui();
        graphics.text(ui, getTitle(), cx + PADDING_X, cy + 19, DeckTheme.TEXT, false);
        graphics.text(ui, Component.translatable("moddeck.list.count", encoded.size(), option.maximumSize()),
                cx + PADDING_X, cy + 35, DeckTheme.TEXT_SECONDARY, false);
        if (!error.getString().isEmpty()) {
            graphics.text(ui, error, cx + PADDING_X + 84, cy + cardHeight() - 18, 0xFFFF9E9E, false);
        }
        for (var child : children()) if (child instanceof Renderable renderable) {
            renderable.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = visibleRows();
        int maximum = Math.max(0, encoded.size() - visibleRows);
        int next = Math.max(0, Math.min(maximum, firstVisible - (int) Math.signum(scrollY)));
        if (next == firstVisible) return false;
        firstVisible = next;
        rebuildWidgets();
        return true;
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    private void addElement() {
        try {
            encoded.add(option.encodeElement(option.newElement()));
            firstVisible = Math.max(0, encoded.size() - visibleRows());
            error = Component.empty();
            rebuildWidgets();
        } catch (RuntimeException exception) {
            error = Component.translatable("moddeck.list.no_default");
        }
    }

    private void remove(int index) {
        if (!option.deletionAllowed() || encoded.size() <= option.minimumSize()) return;
        encoded.remove(index);
        firstVisible = Math.min(firstVisible, Math.max(0, encoded.size() - visibleRows()));
        rebuildWidgets();
    }

    private void move(int index, int direction) {
        int target = index + direction;
        if (target < 0 || target >= encoded.size()) return;
        String value = encoded.remove(index);
        encoded.add(target, value);
        rebuildWidgets();
    }

    private void apply() {
        try {
            List<T> decoded = encoded.stream().map(option::decodeElement).toList();
            if (!option.trySetDraftValue(decoded)) {
                error = option.validationError().orElseThrow().component();
                return;
            }
            onChanged.run();
            minecraft.setScreenAndShow(parent);
        } catch (RuntimeException exception) {
            error = Component.translatable("moddeck.list.invalid_element");
        }
    }

    /** Keeps the vanilla editor behavior while matching Mod Deck's field geometry and hit testing. */
    private static final class ListCellField extends EditBox {
        private static final int TEXT_INSET = 9;

        private ListCellField(net.minecraft.client.gui.Font font, int x, int y, int width, int height,
                Component message) {
            super(font, x, y, width, height, message);
            setBordered(false);
            setTextShadow(false);
            setTextColor(DeckTheme.TEXT);
            setTextColorUneditable(DeckTheme.TEXT_MUTED);
        }

        @Override public void extractWidgetRenderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                    isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER,
                    isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD);
            // Minecraft 26.2 positions unbordered text at the widget origin. Translate only
            // its text/cursor layer so the custom border retains a consistent inner inset.
            graphics.pose().pushMatrix();
            graphics.pose().translate(TEXT_INSET, (getHeight() - 8) / 2.0f);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            graphics.pose().popMatrix();
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            super.onClick(new MouseButtonEvent(
                    event.x() - TEXT_INSET, event.y(), event.buttonInfo()), doubleClick);
        }
    }
}
