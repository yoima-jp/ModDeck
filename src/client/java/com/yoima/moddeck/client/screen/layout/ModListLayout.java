package com.yoima.moddeck.client.screen.layout;

import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.option.DescriptionOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Pure layout helpers for {@code ModListScreen}. Keeping all measurement logic here makes it
 * unit-testable with deterministic font metrics, and guarantees that drawing, widget placement,
 * scrolling, and hit-testing can all be driven by the same snapshot of row heights and tab widths.
 */
public final class ModListLayout {

    private ModListLayout() {}

    /**
     * The two {@link Font} operations the layout actually needs. Passing this interface instead of
     * a real {@code Font} lets tests supply deterministic lambdas and avoids constructing glyph
     * providers outside of a running client.
     */
    public interface FontMetrics {
        int width(FormattedText text);
        List<FormattedCharSequence> split(FormattedText text, int maxWidth);

        /** Wraps a real Minecraft {@link Font} so callers do not need an anonymous class. */
        static FontMetrics of(Font font) {
            return new FontMetrics() {
                @Override public int width(FormattedText text) { return font.width(text); }
                @Override public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
                    return font.split(text, maxWidth);
                }
            };
        }

        /** Deterministic metrics where every character contributes {@code charWidth} pixels. */
        static FontMetrics fixed(int charWidth) {
            return new FontMetrics() {
                @Override public int width(FormattedText text) {
                    return text.getString().length() * charWidth;
                }
                @Override public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
                    String value = text.getString();
                    int charsPerLine = Math.max(1, maxWidth / charWidth);
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    for (int i = 0; i < value.length(); i += charsPerLine) {
                        lines.add(FormattedCharSequence.forward(value.substring(i,
                                Math.min(value.length(), i + charsPerLine)), net.minecraft.network.chat.Style.EMPTY));
                    }
                    if (lines.isEmpty()) lines.add(FormattedCharSequence.forward("", net.minecraft.network.chat.Style.EMPTY));
                    return List.copyOf(lines);
                }
            };
        }
    }

    /**
     * Something that has a translated display label and a unique id. Shared by categories and
     * options so the same width/distribution helpers can serve both tab labels and option labels.
     */
    public interface Label {
        String id();
        FormattedText displayText();
    }

    /**
     * Category tab layout result. Contains the widths to use when the tabs are drawn, whether the
     * row overflows the full available width, and the natural total. A separate
     * {@link #viewportWidth(int, int)} method lets callers obtain the smaller viewport used once
     * overflow is known.
     */
    public record TabLayout(List<Integer> widths, boolean overflow, int totalNaturalWidth,
                            int availableWidth, int arrowSize, int arrowGap) {
        /**
         * Width left for tabs after reserving space for overflow arrows. In the non-overflow case
         * this equals the full width that was passed to categoryTabs. The minWidth/horizontalPadding
         * arguments are only used as a floor so an empty category list cannot collapse to zero.
         */
        public int viewportWidth(int minWidth, int horizontalPadding) {
            int reserved = overflow ? (arrowSize + arrowGap) * 2 : 0;
            return Math.max(minWidth, Math.max(horizontalPadding * 2, availableWidth - reserved));
        }
    }

    /**
     * Lays out category tabs across the full available width (without reserving arrow space).
     * <ul>
     *   <li>If every tab's natural width fits, leftover space is distributed as evenly as possible
     *       so the tab row spans the full available width. The minimum extra pixel is given to the
     *       leftmost tabs to keep the remainder math simple and deterministic.</li>
     *   <li>If the tabs do not fit, each tab keeps its natural width and {@code overflow} is true so
     *       the caller can render arrow buttons and a scrolling viewport.</li>
     * </ul>
     *
     * <p>Why the full width is used for the overflow decision: this layout is recomputed every
     * frame in {@code ModListScreen#init}. If it instead subtracted arrow space first, a previous
     * overflow state could make the next frame artificially narrow and keep {@code overflow} true
     * even when the categories actually fit. Deciding overflow against the full width makes the
     * result depend only on the current categories, font, and total space, not on history.
     *
     * <p>Why natural width is the floor: translated labels vary in length between languages. Capping
     * every tab at an equal share would clip short labels while wasting space on long ones when
     * the total fits. Using each label's measured width as a floor, then splitting only the surplus,
     * keeps all text visible in the common case where the full row fits on screen.
     */
    public static TabLayout categoryTabs(
            List<? extends Label> categories,
            FontMetrics metrics,
            int fullWidth,
            int minWidth,
            int horizontalPadding,
            int arrowSize,
            int arrowGap) {
        if (categories.isEmpty() || fullWidth <= 0) {
            return new TabLayout(List.of(), false, 0, Math.max(0, fullWidth), arrowSize, arrowGap);
        }

        List<Integer> naturalWidths = new ArrayList<>(categories.size());
        int naturalTotal = 0;
        for (Label category : categories) {
            int natural = Math.max(minWidth, metrics.width(category.displayText()) + horizontalPadding * 2);
            naturalWidths.add(natural);
            naturalTotal += natural;
        }

        if (naturalTotal > fullWidth) {
            return new TabLayout(List.copyOf(naturalWidths), true, naturalTotal, fullWidth, arrowSize, arrowGap);
        }

        int surplus = fullWidth - naturalTotal;
        int baseExtra = surplus / categories.size();
        int remainder = surplus % categories.size();
        // Distribute the remainder one pixel at a time from the left so the extra width is spread
        // as evenly as possible. A one-pixel difference is imperceptible and avoids floating-point
        // or fractional scaling that could drift across languages.
        List<Integer> distributed = new ArrayList<>(categories.size());
        for (int i = 0; i < categories.size(); i++) {
            int extra = baseExtra + (i < remainder ? 1 : 0);
            distributed.add(naturalWidths.get(i) + extra);
        }
        return new TabLayout(List.copyOf(distributed), false, naturalTotal, fullWidth, arrowSize, arrowGap);
    }

    /**
     * Computes how many tabs starting at {@code firstVisible} fit inside {@code viewportWidth} when
     * the tabs overflow. Uses the widths produced by {@link #categoryTabs} so the drawing and click
     * code agree on which categories are visible.
     */
    public static int visibleTabCount(TabLayout layout, int firstVisible, int viewportWidth) {
        List<Integer> widths = layout.widths();
        if (widths.isEmpty()) return 0;
        int total = 0;
        int count = 0;
        for (int i = firstVisible; i < widths.size(); i++) {
            int tabWidth = widths.get(i);
            if (total + tabWidth > viewportWidth && count > 0) break;
            total += tabWidth;
            count++;
        }
        return Math.max(1, count);
    }

    /**
     * Returns destination widths for every tab that intersects the viewport. The last width may
     * be smaller than its natural width, allowing the next category to peek into otherwise empty
     * space while navigation continues to count only fully visible tabs.
     */
    public static List<Integer> renderedTabWidths(TabLayout layout, int firstVisible, int viewportWidth) {
        if (viewportWidth <= 0 || firstVisible < 0 || firstVisible >= layout.widths().size()) {
            return List.of();
        }
        List<Integer> rendered = new ArrayList<>();
        int remaining = viewportWidth;
        for (int index = firstVisible; index < layout.widths().size() && remaining > 0; index++) {
            int width = Math.min(layout.widths().get(index), remaining);
            rendered.add(width);
            remaining -= width;
        }
        return List.copyOf(rendered);
    }

    /**
     * Computes per-option row heights from the same wrapped text that drawing uses. The returned
     * list is always the same length as {@code options}; callers can therefore safely index it
     * together with the option list without worrying about mid-frame option list changes.
     */
    public static List<Integer> rowHeights(
            List<? extends ConfigOption<?>> options,
            FontMetrics metrics,
            int labelWrapWidth,
            int descWrapWidth,
            int descriptionLineHeight) {
        List<Integer> heights = new ArrayList<>(options.size());
        for (ConfigOption<?> option : options) {
            if (option instanceof DescriptionOption) {
                int lines = metrics.split(option.displayNameText().component(), labelWrapWidth).size();
                heights.add(Math.max(26, lines * descriptionLineHeight + 14));
            } else {
                int descLines = option.descriptionText().isEmpty() && option.validationError().isEmpty()
                        ? 0
                        : metrics.split(option.descriptionText().isEmpty()
                                ? option.validationError().orElseThrow().component()
                                : option.descriptionText().component(), descWrapWidth).size();
                // No fixed upper cap: a very long description must expand the row so every line
                // is visible and later rows are pushed down instead of overlapping.
                heights.add(Math.max(42, 13 + Math.max(1, descLines) * descriptionLineHeight + 8));
            }
        }
        return heights;
    }

    /**
     * Total content height for scrolling, including top/bottom padding. Uses the same row height
     * calculation as widget placement and drawing so the scroll maximum stays consistent with the
     * rendered rows.
     */
    public static int contentHeight(
            List<? extends ConfigOption<?>> options,
            FontMetrics metrics,
            int labelWrapWidth,
            int descWrapWidth,
            int descriptionLineHeight) {
        int total = rowHeights(options, metrics, labelWrapWidth, descWrapWidth, descriptionLineHeight)
                .stream().mapToInt(Integer::intValue).sum();
        return total + 16;
    }
}
