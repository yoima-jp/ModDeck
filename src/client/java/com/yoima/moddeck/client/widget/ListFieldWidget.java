package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ListOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/** Compact list editor; semicolons delimit elements and a backslash escapes delimiters. */
public final class ListFieldWidget<T> extends EditBox {
    private static final int TEXT_INSET = 11;
    public ListFieldWidget(Font font, int x, int y, int width, ListOption<T> option, Runnable onChanged) {
        super(font, x, y, width, 28, option.displayNameText().component());
        setBordered(false);
        setMaxLength(4096);
        setValue(option.value().stream().map(option::encodeElement).map(ListFieldWidget::escape)
                .reduce((left, right) -> left + "; " + right).orElse(""));
        setResponder(value -> {
            List<String> encoded = split(value).stream().map(String::strip).toList();
            if (option.tryDecodeAndSet(encoded)) onChanged.run();
        });
        active = option.editable();
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        graphics.pose().pushMatrix();
        graphics.pose().translate(TEXT_INSET, (getHeight() - 8) / 2.0f);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().popMatrix();
    }

    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        super.onClick(new net.minecraft.client.input.MouseButtonEvent(
                event.x() - TEXT_INSET, event.y(), event.buttonInfo()), doubleClick);
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace(";", "\\;"); }

    private static List<String> split(String value) {
        if (value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) { current.append(character); escaped = false; }
            else if (character == '\\') escaped = true;
            else if (character == ';') { result.add(current.toString()); current.setLength(0); }
            else current.append(character);
        }
        if (escaped) current.append('\\');
        result.add(current.toString());
        return result;
    }
}
