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

/** Mod Deck-styled list editor with per-cell validation, insertion, deletion, and reordering. */
public final class ListEditorScreen<T> extends Screen {
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

    @Override protected void init() {
        int panelWidth = Math.min(560, width - 40);
        int x = (width - panelWidth) / 2;
        int top = 55;
        int visibleRows = visibleRows();
        int fieldWidth = panelWidth - 146;
        for (int row = 0; row < visibleRows && firstVisible + row < encoded.size(); row++) {
            int index = firstVisible + row;
            int y = top + 42 + row * 36;
            EditBox field = new EditBox(font, x + 18, y, fieldWidth, 28,
                    Component.translatable("moddeck.list.element", index + 1));
            field.setValue(encoded.get(index));
            field.setMaxLength(4096);
            field.setResponder(value -> encoded.set(index, value));
            addRenderableWidget(field);
            addRenderableWidget(new DeckButton(x + panelWidth - 120, y, 26, 28, Component.literal("↑"),
                    DeckButton.Style.THEME, () -> move(index, -1)));
            addRenderableWidget(new DeckButton(x + panelWidth - 90, y, 26, 28, Component.literal("↓"),
                    DeckButton.Style.THEME, () -> move(index, 1)));
            DeckButton remove = new DeckButton(x + panelWidth - 60, y, 42, 28, Component.literal("−"),
                    DeckButton.Style.THEME, () -> remove(index));
            remove.active = option.deletionAllowed() && encoded.size() > option.minimumSize();
            addRenderableWidget(remove);
        }
        int bottom = height - 45;
        DeckButton add = new DeckButton(x + 18, bottom, 72, 28, Component.translatable("moddeck.list.add"),
                DeckButton.Style.THEME, this::addElement);
        add.active = option.insertionAllowed() && encoded.size() < option.maximumSize();
        addRenderableWidget(add);
        addRenderableWidget(new DeckButton(x + panelWidth - 190, bottom, 82, 28,
                Component.translatable("moddeck.cancel"), DeckButton.Style.THEME, this::onClose));
        addRenderableWidget(new DeckButton(x + panelWidth - 100, bottom, 82, 28,
                Component.translatable("moddeck.apply"), DeckButton.Style.PRIMARY, this::apply));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, DeckTheme.BACKGROUND);
        int panelWidth = Math.min(560, width - 40);
        DeckTheme.roundedRect(graphics, (width - panelWidth) / 2, 24, panelWidth, height - 48, 9, DeckTheme.PANEL);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(560, width - 40);
        int x = (width - panelWidth) / 2;
        graphics.text(DeckFonts.ui(), getTitle(), x + 18, 42, DeckTheme.TEXT, true);
        graphics.text(DeckFonts.ui(), Component.translatable("moddeck.list.count", encoded.size(), option.maximumSize()),
                x + 18, 61, DeckTheme.TEXT_SECONDARY, false);
        if (!error.getString().isEmpty()) {
            graphics.text(DeckFonts.ui(), error, x + 102, height - 35, 0xFFFF9E9E, false);
        }
        for (var child : children()) if (child instanceof Renderable renderable) {
            renderable.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, encoded.size() - visibleRows());
        int next = Math.max(0, Math.min(maximum, firstVisible - (int) Math.signum(scrollY)));
        if (next == firstVisible) return false;
        firstVisible = next;
        rebuildWidgets();
        return true;
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    private int visibleRows() { return Math.max(1, (height - 150) / 36); }

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
}
