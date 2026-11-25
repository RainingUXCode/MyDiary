package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutTest {

    private static final Rectangle2D CANVAS = new Rectangle2D.Double(0, 0, Layout.WIDTH, Layout.HEIGHT);

    private static List<Rectangle2D> all() {
        return List.of(Layout.LOGO, Layout.NAV_LIST, Layout.NAV_ALERTS, Layout.NAV_RECORD, Layout.TEXT_FIELD,
                Layout.ADD, Layout.MAIN_PANEL, Layout.MAIN_RIBBON, Layout.MAIN_RIBBON_WIDE, Layout.CLEAR,
                Layout.MAIN_BADGE, Layout.SUMMARY_PANEL, Layout.SUMMARY_RIBBON, Layout.SUMMARY_HEART,
                Layout.SUMMARY_TEXT, Layout.PROGRESS_BAR, Layout.NOTES_AREA, Layout.NOTES_LABEL,
                Layout.ACHIEVEMENT_TILE, Layout.ACHIEVEMENT_LABEL, Layout.PLAYER_PILL, Layout.PLAYER_PREVIOUS,
                Layout.PLAYER_PAUSE, Layout.PLAYER_NEXT, Layout.FOOTER_MESSAGE, Layout.NEW_BADGE,
                Layout.FOOTER_HEART);
    }

    @Test
    void everyAreaIsInsideTheLogicalCanvas() {
        for (Rectangle2D r : all()) {
            assertTrue(CANVAS.contains(r));
            assertTrue(r.getWidth() > 0 && r.getHeight() > 0);
        }
    }

    @Test
    void sixTaskRowsInsideThePanelWithoutOverlap() {
        assertEquals(6, Layout.rowCount());
        for (int i = 0; i < Layout.rowCount(); i++) {
            assertTrue(Layout.MAIN_PANEL.contains(Layout.row(i)));
            if (i > 0) {
                assertTrue(Layout.row(i).getY() >= Layout.row(i - 1).getMaxY());
                assertEquals((int) Layout.row(0).getWidth(), (int) Layout.row(i).getWidth());
            }
        }
    }

    @Test
    void mainAndSummaryPanelsDoNotOverlap() {
        assertFalse(Layout.MAIN_PANEL.intersects(Layout.SUMMARY_PANEL));
    }

    @Test
    void navigationButtonsAreStackedAndSameSize() {
        assertTrue(Layout.NAV_LIST.getMaxY() <= Layout.NAV_ALERTS.getY());
        assertTrue(Layout.NAV_ALERTS.getMaxY() <= Layout.NAV_RECORD.getY());
        assertEquals((int) Layout.NAV_LIST.getWidth(), (int) Layout.NAV_RECORD.getWidth());
    }

    @Test
    void playerControlsSitInsideTheirPillInOrder() {
        for (Rectangle2D r : List.of(Layout.PLAYER_PREVIOUS, Layout.PLAYER_PAUSE, Layout.PLAYER_NEXT)) {
            assertTrue(Layout.PLAYER_PILL.contains(r));
        }
        assertTrue(Layout.PLAYER_PREVIOUS.getMaxX() <= Layout.PLAYER_PAUSE.getX());
        assertTrue(Layout.PLAYER_PAUSE.getMaxX() <= Layout.PLAYER_NEXT.getX());
        assertFalse(Layout.PLAYER_PILL.intersects(Layout.FOOTER_MESSAGE));
    }

    @Test
    void progressBarAndTilesFitInsideTheSummaryPanel() {
        for (Rectangle2D r : List.of(Layout.SUMMARY_HEART, Layout.PROGRESS_BAR, Layout.NOTES_AREA,
                Layout.ACHIEVEMENT_TILE)) {
            assertTrue(Layout.SUMMARY_PANEL.contains(r));
        }
    }

    @Test
    void roundedMockupConversionIsLinear() {
        Rectangle2D a = Layout.mock(100, 100, 200, 200);
        Rectangle2D b = Layout.mock(200, 200, 300, 300);
        assertEquals((int) Math.round(a.getWidth()), (int) Math.round(b.getWidth()));
        assertTrue(Math.abs(b.getX() - a.getX() - a.getWidth()) < 1e-9);
    }

    @Test
    void clearButtonStaysInsideThePanelAwayFromBorderAndFirstRow() {
        var c = Layout.CLEAR;
        assertTrue(c.getX() - Layout.MAIN_PANEL.getX() >= 8);
        assertTrue(Layout.MAIN_PANEL.getMaxX() - c.getMaxX() >= 8);
        assertTrue(c.getY() - Layout.MAIN_PANEL.getY() >= 8);
        assertTrue(c.getMaxY() <= Layout.row(0).getY());
        assertFalse(c.intersects(Layout.MAIN_RIBBON));
    }
}
