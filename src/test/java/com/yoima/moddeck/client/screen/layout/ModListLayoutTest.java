package com.yoima.moddeck.client.screen.layout;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.BooleanOption;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.option.DescriptionOption;
import com.yoima.moddeck.api.option.StringOption;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModListLayoutTest {

    private static ModListLayout.Label label(String id, String text) {
        return new ModListLayout.Label() {
            @Override public String id() { return id; }
            @Override public net.minecraft.network.chat.FormattedText displayText() {
                return Component.literal(text);
            }
        };
    }

    @Test void distributesSurplusWidthAcrossAllTabsWhenTheyFit() {
        List<ModListLayout.Label> categories = List.of(
                label("general", "General"),
                label("advanced", "Advanced"),
                label("about", "About"));
        // Each character is 6px; padding 16 each side.
        ModListLayout.FontMetrics metrics = ModListLayout.FontMetrics.fixed(6);
        int available = 300;
        ModListLayout.TabLayout layout = ModListLayout.categoryTabs(categories, metrics, available,
                48, 16, 24, 8);

        assertFalse(layout.overflow(), "All tabs should fit");
        // Natural widths: max(48, 42+32=74), max(48, 48+32=80), max(48, 30+32=62) => 74, 80, 62 = 216
        assertEquals(216, layout.totalNaturalWidth());
        // Surplus 84 distributed: base 28 each, remainder 0.
        assertEquals(List.of(102, 108, 90), layout.widths());
        assertEquals(300, layout.widths().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void givesRemainderPixelsToLeftmostTabs() {
        List<ModListLayout.Label> categories = List.of(
                label("a", "A"),
                label("b", "B"),
                label("c", "C"));
        ModListLayout.FontMetrics metrics = ModListLayout.FontMetrics.fixed(10);
        // Natural widths: max(48, 10+32=42) clamped to 48 each => total 144.
        // Available 150 => surplus 6, baseExtra 2, remainder 0 -> 50 each.
        // Try 151 => surplus 7, base 2 remainder 1 -> first gets 51, rest 50.
        ModListLayout.TabLayout layout = ModListLayout.categoryTabs(categories, metrics, 151, 48, 16, 24, 8);
        assertFalse(layout.overflow());
        assertEquals(List.of(51, 50, 50), layout.widths());
        assertEquals(151, layout.widths().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void reportsOverflowWhenNaturalWidthsExceedAvailableSpace() {
        List<ModListLayout.Label> categories = List.of(
                label("general", "General"),
                label("advanced", "Advanced"),
                label("graphics", "Graphics"));
        ModListLayout.FontMetrics metrics = ModListLayout.FontMetrics.fixed(12);
        ModListLayout.TabLayout layout = ModListLayout.categoryTabs(categories, metrics, 100, 48, 16, 24, 8);

        assertTrue(layout.overflow(), "Tabs should overflow");
        // Natural widths preserved so drawing and scrolling agree on tab geometry.
        assertEquals(List.of(116, 128, 128), layout.widths());
        assertEquals(372, layout.totalNaturalWidth());
    }

    @Test void visibleTabCountStopsAtViewportEdge() {
        // Three tabs, widths 50 each; viewport 120.
        ModListLayout.TabLayout layout = new ModListLayout.TabLayout(List.of(50, 50, 50), true, 150, 184, 24, 8);
        assertEquals(2, ModListLayout.visibleTabCount(layout, 0, 120));
        assertEquals(1, ModListLayout.visibleTabCount(layout, 2, 120));
        // Always at least one visible tab even if it overflows.
        assertEquals(1, ModListLayout.visibleTabCount(layout, 2, 10));
    }

    @Test void renderedTabWidthsIncludeClippedNextTab() {
        ModListLayout.TabLayout layout = new ModListLayout.TabLayout(
                List.of(50, 50, 50), true, 150, 184, 24, 8);

        assertEquals(List.of(50, 50, 20),
                ModListLayout.renderedTabWidths(layout, 0, 120));
        assertEquals(List.of(50, 20),
                ModListLayout.renderedTabWidths(layout, 1, 70));
        assertEquals(List.of(10),
                ModListLayout.renderedTabWidths(layout, 2, 10));
    }

    @Test void overflowDecisionUsesFullWidthNotArrowReservedViewport() {
        // Two categories whose natural widths sum to more than the arrow-reserved viewport but
        // still fit the full width. If categoryTabs subtracted arrow space first, it would
        // incorrectly overflow.
        List<ModListLayout.Label> categories = List.of(label("a", "AA"), label("b", "BB"));
        ModListLayout.FontMetrics metrics = ModListLayout.FontMetrics.fixed(10);
        int minWidth = 48;
        int padding = 16;
        int arrowSize = 24;
        int arrowGap = 8;
        // Full width is 200; arrow-reserved viewport would be 200 - 2*(24+8) = 136.
        // Natural total = 52 + 52 = 104, which fits fullWidth but not the shrunken viewport.
        int fullWidth = 200;
        ModListLayout.TabLayout layout = ModListLayout.categoryTabs(categories, metrics, fullWidth,
                minWidth, padding, arrowSize, arrowGap);

        assertFalse(layout.overflow(), "Tabs that fit the full width should not overflow");
        assertEquals(104, layout.totalNaturalWidth());
        int distributedTotal = layout.widths().stream().mapToInt(Integer::intValue).sum();
        assertEquals(200, distributedTotal);
        // Viewport still reports the full distributed width because there are no arrows.
        assertEquals(200, layout.viewportWidth(minWidth, padding));
    }

    @Test void overflowViewportUsesScreenWidthInsteadOfNaturalTotal() {
        ModListLayout.TabLayout layout = new ModListLayout.TabLayout(
                List.of(120, 130, 140), true, 390, 240, 24, 8);

        assertEquals(176, layout.viewportWidth(48, 16));
    }

    @Test void rowHeightsMatchOptionListLength() {
        BooleanOption enabled = new BooleanOption("enabled", ConfigText.literal("Enabled"),
                ConfigText.literal("Turn on the feature"), true);
        StringOption name = new StringOption("name", ConfigText.literal("Name"),
                ConfigText.empty(), "Player", 32);
        DescriptionOption hint = new DescriptionOption("hint", ConfigText.literal("This is a hint"));
        List<ConfigOption<?>> options = List.of(enabled, name, hint);

        List<Integer> heights = ModListLayout.rowHeights(options,
                ModListLayout.FontMetrics.fixed(6), 200, 100, 11);

        assertEquals(3, heights.size());
        // Description row is based on wrapped line count.
        assertEquals(26, heights.get(2));
        // Other rows include at least one description line slot.
        assertTrue(heights.get(0) >= 42);
        assertTrue(heights.get(1) >= 42);
    }

    @Test void contentHeightIsSumOfHeightsPlusPadding() {
        BooleanOption enabled = new BooleanOption("enabled", ConfigText.literal("Enabled"),
                ConfigText.empty(), true);
        List<ConfigOption<?>> options = List.of(enabled);
        int height = ModListLayout.contentHeight(options, ModListLayout.FontMetrics.fixed(6),
                200, 100, 11);
        List<Integer> rowHeights = ModListLayout.rowHeights(options, ModListLayout.FontMetrics.fixed(6),
                200, 100, 11);
        assertEquals(rowHeights.get(0) + 16, height);
    }
}
