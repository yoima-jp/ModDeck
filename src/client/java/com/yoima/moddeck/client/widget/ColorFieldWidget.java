package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ColorOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Compact color summary with an in-place Mod Deck hue-wheel popup. */
public final class ColorFieldWidget extends AbstractWidget implements ExpandableOptionWidget {
    private static final int POPUP_WIDTH = 286;
    private static final int POPUP_HEIGHT = 222;
    private static final int WHEEL_CENTER_X = 76;
    private static final int WHEEL_CENTER_Y = 83;
    private static final int OUTER_RADIUS = 59;
    private static final int INNER_RADIUS = 47;
    private static final int SV_SIZE = 62;
    private final ColorOption option;
    private final Runnable onChanged;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;
    private int workingColor;
    private float hue;
    private float saturation;
    private float brightness;
    private int dragMode;
    private int editingChannel = -1;
    private String editingBuffer = "";
    private boolean editingHex;
    private String hexBuffer = "";
    private boolean hexReplaceOnType;
    private int[] hexScratch = new int[4];

    public ColorFieldWidget(Font font, int x, int y, int width, ColorOption option, Runnable onChanged) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
        resetWorkingColor();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (handleExpandedClick(event.x(), event.y())) return true;
        if (isMouseOver(event.x(), event.y())) {
            expanded = !expanded;
            if (expanded) resetWorkingColor();
            setFocused(expanded);
            return true;
        }
        expanded = false;
        return false;
    }

    private boolean hexFieldBounds(int[] out) {
        if (!expanded) return false;
        out[0] = popupX() + 151;
        out[1] = popupY() + 31;
        out[2] = 119;
        out[3] = 27;
        return true;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int swatch = option.alpha() ? option.draftValue() : 0xFF000000 | option.draftValue();
        DeckTheme.roundedRect(graphics, getX(), getY() + 4, 20, 20, 4, swatch);
        DeckTheme.border(graphics, getX() + 26, getY(), getWidth() - 26, getHeight(), 5,
                expanded || isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        graphics.text(DeckFonts.ui(), "#" + option.draftHexValue(), getX() + 38, getY() + 10, DeckTheme.TEXT, false);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        int x = popupX();
        int y = popupY();
        DeckTheme.roundedRect(graphics, x + 3, y + 4, POPUP_WIDTH, POPUP_HEIGHT, 8, 0x66000000);
        DeckTheme.border(graphics, x, y, POPUP_WIDTH, POPUP_HEIGHT, 8, DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        drawHueWheel(graphics, x + WHEEL_CENTER_X, y + WHEEL_CENTER_Y);
        drawSaturationValue(graphics, x + WHEEL_CENTER_X - SV_SIZE / 2,
                y + WHEEL_CENTER_Y - SV_SIZE / 2);
        drawDetails(graphics, x, y, mouseX, mouseY);
    }

    private void drawHueWheel(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        for (int degree = 0; degree < 360; degree += 2) {
            double angle = Math.toRadians(degree - 90);
            int color = 0xFF000000 | hsvToRgb(degree / 360.0f, 1, 1);
            for (int radius = INNER_RADIUS; radius <= OUTER_RADIUS; radius += 2) {
                int px = centerX + (int) Math.round(Math.cos(angle) * radius);
                int py = centerY + (int) Math.round(Math.sin(angle) * radius);
                graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
            }
        }
        double markerAngle = Math.toRadians(hue * 360 - 90);
        int markerX = centerX + (int) Math.round(Math.cos(markerAngle) * (INNER_RADIUS + OUTER_RADIUS) / 2.0);
        int markerY = centerY + (int) Math.round(Math.sin(markerAngle) * (INNER_RADIUS + OUTER_RADIUS) / 2.0);
        DeckTheme.border(graphics, markerX - 4, markerY - 4, 9, 9, 5, DeckTheme.TEXT, 0xFF000000 | hsvToRgb(hue, 1, 1));
    }

    private void drawSaturationValue(GuiGraphicsExtractor graphics, int x, int y) {
        for (int py = 0; py < SV_SIZE; py += 2) {
            float value = 1.0f - py / (float) (SV_SIZE - 1);
            for (int px = 0; px < SV_SIZE; px += 2) {
                float sat = px / (float) (SV_SIZE - 1);
                int color = 0xFF000000 | hsvToRgb(hue, sat, value);
                graphics.fill(x + px, y + py, x + Math.min(SV_SIZE, px + 2), y + Math.min(SV_SIZE, py + 2), color);
            }
        }
        int markerX = x + Math.round(saturation * (SV_SIZE - 1));
        int markerY = y + Math.round((1 - brightness) * (SV_SIZE - 1));
        DeckTheme.border(graphics, markerX - 4, markerY - 4, 9, 9, 5, DeckTheme.TEXT,
                0xFF000000 | hsvToRgb(hue, saturation, brightness));
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        var font = DeckFonts.ui();
        int rightX = x + 151;
        graphics.text(font, option.displayNameText().component(), rightX, y + 13, DeckTheme.TEXT, false);
        DeckTheme.border(graphics, rightX, y + 31, 119, 27, 5, DeckTheme.DIVIDER, DeckTheme.FIELD);
        DeckTheme.roundedRect(graphics, rightX + 6, y + 37, 15, 15, 3, argbWorkingColor());
        String hexDisplay = editingHex ? "#" + hexBuffer + "_" : "#" + workingHex();
        graphics.text(font, hexDisplay, rightX + 28, y + 41, DeckTheme.TEXT, false);
        int[] channels = channels();
        String[] names = option.alpha() ? new String[]{"A", "R", "G", "B"} : new String[]{"R", "G", "B"};
        for (int index = 0; index < names.length; index++) {
            int rowY = y + 66 + index * 27;
            graphics.text(font, names[index], rightX, rowY + 9, DeckTheme.TEXT_SECONDARY, false);
            DeckTheme.border(graphics, rightX + 18, rowY, 101, 23, 5, DeckTheme.DIVIDER, DeckTheme.FIELD);
            boolean minusHover = inside(mouseX, mouseY, rightX + 18, rowY, 25, 23);
            boolean plusHover = inside(mouseX, mouseY, rightX + 94, rowY, 25, 23);
            if (minusHover) DeckTheme.roundedRect(graphics, rightX + 19, rowY + 1, 24, 21, 4, DeckTheme.FIELD_HOVER);
            if (plusHover) DeckTheme.roundedRect(graphics, rightX + 94, rowY + 1, 24, 21, 4, DeckTheme.FIELD_HOVER);
            DeckTheme.centeredText(graphics, font, "−", rightX + 31, rowY + 8, DeckTheme.TEXT_SECONDARY);
            String value = editingChannel == index ? editingBuffer + "_" : Integer.toString(channels[index]);
            DeckTheme.centeredText(graphics, font, value, rightX + 69, rowY + 8, DeckTheme.TEXT);
            DeckTheme.centeredText(graphics, font, "+", rightX + 106, rowY + 8, DeckTheme.TEXT_SECONDARY);
        }
        int buttonY = y + POPUP_HEIGHT - 31;
        int cancelColor = inside(mouseX, mouseY, x + 151, buttonY, 56, 22) ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD;
        int applyColor = inside(mouseX, mouseY, x + 213, buttonY, 57, 22) ? DeckTheme.ACCENT : DeckTheme.ACCENT_DARK;
        DeckTheme.roundedRect(graphics, x + 151, buttonY, 56, 22, 5, cancelColor);
        DeckTheme.roundedRect(graphics, x + 213, buttonY, 57, 22, 5, applyColor);
        DeckTheme.centeredText(graphics, font, Component.translatable("moddeck.cancel"), x + 179, buttonY + 8, DeckTheme.TEXT);
        DeckTheme.centeredText(graphics, font, Component.translatable("moddeck.apply"), x + 241, buttonY + 8, DeckTheme.TEXT);
    }

    @Override public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int x = popupX();
        int y = popupY();
        if (!inside(mouseX, mouseY, x, y, POPUP_WIDTH, POPUP_HEIGHT)) return false;
        int centerX = x + WHEEL_CENTER_X;
        int centerY = y + WHEEL_CENTER_Y;
        double distance = Math.hypot(mouseX - centerX, mouseY - centerY);
        if (distance >= INNER_RADIUS - 3 && distance <= OUTER_RADIUS + 3) {
            commitChannelEdit();
            dragMode = 1;
            updateHue(mouseX, mouseY, centerX, centerY);
            return true;
        }
        int svX = centerX - SV_SIZE / 2;
        int svY = centerY - SV_SIZE / 2;
        if (inside(mouseX, mouseY, svX, svY, SV_SIZE, SV_SIZE)) {
            commitChannelEdit();
            dragMode = 2;
            updateSaturationValue(mouseX, mouseY, svX, svY);
            return true;
        }
        int rightX = x + 151;
        String[] names = option.alpha() ? new String[]{"A", "R", "G", "B"} : new String[]{"R", "G", "B"};
        if (hexFieldBounds(hexScratch) && inside(mouseX, mouseY, hexScratch[0], hexScratch[1], hexScratch[2], hexScratch[3])) {
            commitChannelEdit();
            editingHex = true;
            hexBuffer = workingHex();
            hexReplaceOnType = true;
            setFocused(true);
            return true;
        }
        for (int index = 0; index < names.length; index++) {
            int rowY = y + 66 + index * 27;
            if (inside(mouseX, mouseY, rightX + 18, rowY, 25, 23)) { commitHexEdit(); adjustChannel(index, -1); return true; }
            if (inside(mouseX, mouseY, rightX + 43, rowY, 51, 23)) {
                commitHexEdit();
                editingChannel = index;
                editingBuffer = Integer.toString(channels()[index]);
                setFocused(true);
                return true;
            }
            if (inside(mouseX, mouseY, rightX + 94, rowY, 25, 23)) { commitHexEdit(); adjustChannel(index, 1); return true; }
        }
        int buttonY = y + POPUP_HEIGHT - 31;
        if (inside(mouseX, mouseY, x + 151, buttonY, 56, 22)) {
            expanded = false; editingChannel = -1; editingHex = false; hexBuffer = ""; return true;
        }
        if (inside(mouseX, mouseY, x + 213, buttonY, 57, 22)) {
            commitHexEdit();
            commitChannelEdit();
            if (option.trySetDraftValue(workingColor)) onChanged.run();
            expanded = false;
            return true;
        }
        return true;
    }

    @Override public boolean handleExpandedDrag(double mouseX, double mouseY) {
        if (!expanded || dragMode == 0) return false;
        commitHexEdit();
        int centerX = popupX() + WHEEL_CENTER_X;
        int centerY = popupY() + WHEEL_CENTER_Y;
        if (dragMode == 1) updateHue(mouseX, mouseY, centerX, centerY);
        else updateSaturationValue(mouseX, mouseY, centerX - SV_SIZE / 2, centerY - SV_SIZE / 2);
        return true;
    }

    @Override public void handleExpandedRelease() { dragMode = 0; }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!expanded) return super.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (editingHex) { editingHex = false; hexBuffer = ""; }
            else if (editingChannel >= 0) editingChannel = -1;
            else expanded = false;
            return true;
        }
        if (editingHex) return hexKeyPressed(event);
        if (editingChannel < 0) return true;
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            commitChannelEdit();
        } else if (event.key() == InputConstants.KEY_BACKSPACE && !editingBuffer.isEmpty()) {
            editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
        }
        return true;
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (!expanded) return false;
        if (editingHex) return hexCharTyped(event);
        if (editingChannel < 0) return false;
        String character = event.codepointAsString();
        if (character.matches("[0-9]") && editingBuffer.length() < 3) editingBuffer += character;
        return true;
    }

    private boolean hexKeyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            commitHexEdit();
            editingHex = false;
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE) {
            if (hexReplaceOnType) {
                hexBuffer = "";
                hexReplaceOnType = false;
            } else if (!hexBuffer.isEmpty()) {
                hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE) {
            if (hexReplaceOnType) {
                hexBuffer = "";
                hexReplaceOnType = false;
            } else {
                hexBuffer = "";
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_A && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            hexReplaceOnType = true;
            return true;
        }
        if (event.key() == InputConstants.KEY_HOME) return true;
        if (event.key() == InputConstants.KEY_END) return true;
        int arrow = InputConstants.KEY_LEFT - event.key();
        if (arrow == 0 || event.key() == InputConstants.KEY_RIGHT) {
            // Arrow keys do not move in this single-line hex field; consume them so the
            // parent screen does not try to change focus while the user is mid-edit.
            return true;
        }
        return true;
    }

    private boolean hexCharTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) return false;
        String character = event.codepointAsString();
        if (!character.matches("[0-9a-fA-F]")) return true;
        if (hexReplaceOnType) {
            hexBuffer = character;
            hexReplaceOnType = false;
        } else if (hexBuffer.length() < (option.alpha() ? 8 : 6)) {
            hexBuffer += character;
        }
        return true;
    }

    private void commitHexEdit() {
        if (!editingHex) return;
        String digits = hexBuffer.startsWith("#") ? hexBuffer.substring(1) : hexBuffer;
        int expected = option.alpha() ? 8 : 6;
        if (digits.matches("[0-9a-fA-F]{" + expected + "}")) {
            try {
                int parsed = (int) Long.parseLong(digits, 16);
                workingColor = option.alpha() ? parsed : parsed & 0xFFFFFF;
                float[] hsv = java.awt.Color.RGBtoHSB(workingColor >> 16 & 255, workingColor >> 8 & 255,
                        workingColor & 255, null);
                hue = hsv[0];
                saturation = hsv[1];
                brightness = hsv[2];
            } catch (NumberFormatException ignored) {
                // Transient invalid hex is ignored; last valid workingColor remains.
            }
        }
        editingHex = false;
        hexBuffer = "";
        hexReplaceOnType = false;
    }

    @Override public void collapseIfOutside(double mouseX, double mouseY) {
        if (!expanded) return;
        boolean overPopup = inside(mouseX, mouseY, popupX(), popupY(), POPUP_WIDTH, POPUP_HEIGHT);
        if (!isMouseOver(mouseX, mouseY) && !overPopup) {
            expanded = false; dragMode = 0; editingChannel = -1; editingHex = false; hexBuffer = "";
        }
    }

    @Override public void setPopupViewport(int top, int bottom) { viewportTop = top; viewportBottom = bottom; }
    @Override public boolean isExpanded() { return expanded; }

    private int popupX() { return Math.max(6, getRight() - POPUP_WIDTH); }

    private int popupY() {
        int below = getBottom() + 3;
        if (below + POPUP_HEIGHT <= viewportBottom) return below;
        return Math.max(viewportTop, Math.min(viewportBottom - POPUP_HEIGHT, getY() - POPUP_HEIGHT - 3));
    }

    private void resetWorkingColor() {
        workingColor = option.draftValue();
        float[] hsv = java.awt.Color.RGBtoHSB(workingColor >> 16 & 255, workingColor >> 8 & 255,
                workingColor & 255, null);
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
    }

    private void updateHue(double mouseX, double mouseY, int centerX, int centerY) {
        commitHexEdit();
        hue = (float) ((Math.atan2(mouseY - centerY, mouseX - centerX) + Math.PI / 2) / (Math.PI * 2));
        if (hue < 0) hue += 1;
        updateRgbFromHsv();
    }

    private void updateSaturationValue(double mouseX, double mouseY, int x, int y) {
        commitHexEdit();
        saturation = clamp((float) ((mouseX - x) / (SV_SIZE - 1)));
        brightness = 1 - clamp((float) ((mouseY - y) / (SV_SIZE - 1)));
        updateRgbFromHsv();
    }

    private void updateRgbFromHsv() {
        int alpha = option.alpha() ? workingColor >>> 24 & 255 : 255;
        workingColor = option.alpha() ? alpha << 24 | hsvToRgb(hue, saturation, brightness)
                : hsvToRgb(hue, saturation, brightness);
    }

    private void adjustChannel(int index, int delta) {
        int[] values = channels();
        values[index] = Math.max(0, Math.min(255, values[index] + delta));
        if (option.alpha()) workingColor = values[0] << 24 | values[1] << 16 | values[2] << 8 | values[3];
        else workingColor = values[0] << 16 | values[1] << 8 | values[2];
        float[] hsv = java.awt.Color.RGBtoHSB(workingColor >> 16 & 255, workingColor >> 8 & 255,
                workingColor & 255, null);
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
    }

    private void commitChannelEdit() {
        if (editingChannel < 0) return;
        try {
            int value = Math.max(0, Math.min(255, Integer.parseInt(editingBuffer)));
            int[] values = channels();
            values[editingChannel] = value;
            if (option.alpha()) workingColor = values[0] << 24 | values[1] << 16 | values[2] << 8 | values[3];
            else workingColor = values[0] << 16 | values[1] << 8 | values[2];
            float[] hsv = java.awt.Color.RGBtoHSB(workingColor >> 16 & 255, workingColor >> 8 & 255,
                    workingColor & 255, null);
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
        } catch (NumberFormatException ignored) {
            // Empty transient input is discarded and the last valid channel value is retained.
        }
        editingChannel = -1;
        editingBuffer = "";
    }

    private int[] channels() {
        return option.alpha()
                ? new int[]{workingColor >>> 24 & 255, workingColor >> 16 & 255, workingColor >> 8 & 255, workingColor & 255}
                : new int[]{workingColor >> 16 & 255, workingColor >> 8 & 255, workingColor & 255};
    }

    private int argbWorkingColor() { return option.alpha() ? workingColor : 0xFF000000 | workingColor; }
    private String workingHex() { return String.format(option.alpha() ? "%08X" : "%06X", workingColor); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static int hsvToRgb(float h, float s, float v) { return java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF; }
    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
