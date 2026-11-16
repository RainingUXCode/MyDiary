package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** Fontes originais empacotadas, detecção da versão DEMO sem glifos e símbolos ausentes (✓, ♪). */
class FontsTest {

    @Test
    void everyTextUsesCloudSoftBold() {
        Fonts f = Fonts.load();
        assertTrue(f.usesOriginalFonts());
        assertTrue(f.display(30).getFontName().contains("CloudSoft-Bold"));
        assertTrue(f.bodyFontName().contains("CloudSoft-Bold"));
        assertTrue(f.secondary(20).getFontName().contains("CloudSoft-Bold"));
        assertTrue(Fonts.isFullyDrawn(f.secondary(30)));
        assertTrue(Fonts.isFullyDrawn(f.body(30)));
        assertTrue(Fonts.isFullyDrawn(f.display(30)));
        assertEquals(30f * 0.92f, f.body(30).getSize2D(), 0.001f);
    }

    @Test
    void aFontWhoseVowelsAreBlankIsRejected() {
        assertTrue(Fonts.isFullyDrawn(new Font(Font.SANS_SERIF, Font.PLAIN, 20)));
        assertFalse(Fonts.isFullyDrawn(new Font(Font.SANS_SERIF, Font.PLAIN, 20) {
            @Override
            public boolean canDisplay(char c) {
                return c != 'a';
            }
        }));
    }

    @Test
    void symbolsMissingFromTheFontUseAFallbackInsteadOfTofu() {
        Fonts f = Fonts.load();
        BufferedImage img = new BufferedImage(400, 80, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        Font font = f.body(40);
        double plain = TextPainter.width(g, "Concluida", font);
        double withCheck = TextPainter.width(g, "✓ Concluida", font);
        assertTrue(withCheck > plain);
        TextPainter.draw(g, "✓ Concluída ♪", font, 10, 50, java.awt.Color.BLACK, null, 0);
        g.dispose();
        boolean drawn = false;
        for (int y = 0; y < 80 && !drawn; y++) {
            for (int x = 0; x < 400; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    drawn = true;
                    break;
                }
            }
        }
        assertTrue(drawn);
    }

    @Test
    void taskRowBasesShareTheSameStructureInEveryState() throws Exception {
        for (String name : new String[] {"base-tarefa-pendente.png", "base-tarefa-concluida.png"}) {
            try (var in = FontsTest.class.getResourceAsStream("/assets/ui/" + name)) {
                BufferedImage img = javax.imageio.ImageIO.read(in);
                assertEquals(504, img.getWidth());
                assertEquals(96, img.getHeight());
            }
        }
    }
}
