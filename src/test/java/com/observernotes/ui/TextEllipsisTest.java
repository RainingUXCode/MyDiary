package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.scene.MainScene;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** Reticências no rodapé e nos títulos de tarefas, e o texto completo do tooltip. */
class TextEllipsisTest {

    private static java.awt.Graphics2D g() {
        var g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    @Test
    void longTextIsCutWithEllipsisWithoutChangingTheFont() {
        var g = g();
        Font f = Fonts.load().body(26);
        String longText = "♪ Now Playing: um nome de música realmente muito longo, longo, longo, longo, longo, longo";
        String cut = TextPainter.ellipsize(g, longText, f, 300);
        assertTrue(cut.endsWith("…"));
        assertTrue(TextPainter.width(g, cut, f) <= 300);
        assertEquals("curto", TextPainter.ellipsize(g, "curto", f, 300));
        assertEquals("…", TextPainter.ellipsize(g, "abcdef", f, 1));
    }

    @Test
    void taskTitlesShrinkOnlyALittleThenTruncateAndKeepAccents() {
        var g = g();
        Fonts fonts = Fonts.load();
        TextPainter.Fitted ok = MainScene.taskTitleFit(g, fonts, "Comprar pão", 360);
        assertFalse(ok.truncated());
        float base = fonts.body(26).getSize2D();
        assertEquals(base, ok.font().getSize2D(), 0.01f);
        String title = "Preparar a apresentação de conclusão do semestre com todos os gráficos e referências";
        TextPainter.Fitted fit = MainScene.taskTitleFit(g, fonts, title, 360);
        assertTrue(fit.truncated());
        assertTrue(fit.text().endsWith("…"));
        assertTrue(fit.font().getSize2D() >= base * 0.94f - 0.01f, "fonte não fica pequena demais");
        assertTrue(TextPainter.width(g, fit.text(), fit.font()) <= 360);
        assertTrue(fit.text().startsWith("Preparar a apresentação"));
    }

    @Test
    void tooltipTextIsOnlyOfferedForTruncatedTitles() {
        var g = g();
        var m = MockupSample.screen(PanelMode.TASKS);
        Point2D inside = new Point2D.Double(Layout.row(0).getCenterX(), Layout.row(0).getCenterY());
        String t = MainScene.truncatedTaskTitleAt(g, Fonts.load(), inside, m);
        if (t != null) {
            assertEquals(m.tasks().get(0).title(), t);
        }
        assertEquals(null, MainScene.truncatedTaskTitleAt(g, Fonts.load(), new Point2D.Double(5, 5), m));
    }
}
