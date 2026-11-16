package com.observernotes.ui.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SliceRendererTest {

    /** Imagem sintética 100x20: ponta esquerda vermelha, ponta direita azul, miolo verde opaco. */
    private static BufferedImage source() {
        BufferedImage img = new BufferedImage(100, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.setColor(Color.GREEN);
        g.fillRect(20, 0, 60, 20);
        g.setColor(Color.BLUE);
        g.fillRect(80, 0, 20, 20);
        g.dispose();
        return img;
    }

    @Test
    void outputHasExactlyTheRequestedSize() {
        BufferedImage out = SliceRenderer.render(source(), SliceSpec.stretch(20, 20), 333, 40);
        assertEquals(333, out.getWidth());
        assertEquals(40, out.getHeight());
    }

    @Test
    void capsKeepTheirProportionWhenTheMiddleGrows() {
        // altura 20 -> 40 (escala 2): pontas de 20px viram 40px, independentemente da largura.
        BufferedImage out = SliceRenderer.render(source(), SliceSpec.stretch(20, 20), 600, 40);
        assertTrue(isMostly(out.getRGB(10, 20), Color.RED));
        assertTrue(isMostly(out.getRGB(35, 20), Color.RED));
        assertTrue(isMostly(out.getRGB(300, 20), Color.GREEN));
        assertTrue(isMostly(out.getRGB(600 - 35, 20), Color.BLUE));
        assertTrue(isMostly(out.getRGB(600 - 10, 20), Color.BLUE));
        assertTrue(isMostly(out.getRGB(46, 20), Color.GREEN));
    }

    @Test
    void tiledMiddleLeavesNoTransparentSeams() {
        BufferedImage out = SliceRenderer.render(source(), SliceSpec.tiled(20, 20, 17), 777, 20);
        for (int x = 0; x < out.getWidth(); x++) {
            assertEquals(255, (out.getRGB(x, 10) >>> 24));
        }
    }

    @Test
    void targetNarrowerThanTheCapsStillRenders() {
        BufferedImage out = SliceRenderer.render(source(), SliceSpec.stretch(20, 20), 30, 20);
        assertEquals(30, out.getWidth());
        assertEquals(20, out.getHeight());
    }

    @Test
    void isDeterministic() {
        BufferedImage a = SliceRenderer.render(source(), SliceSpec.tiled(20, 20, 17), 400, 33);
        BufferedImage b = SliceRenderer.render(source(), SliceSpec.tiled(20, 20, 17), 400, 33);
        for (int y = 0; y < 33; y++) {
            for (int x = 0; x < 400; x++) {
                assertEquals(a.getRGB(x, y), b.getRGB(x, y));
            }
        }
    }

    @Test
    void spriteCacheReturnsTheSameInstanceForTheSameRequest() {
        SpriteCache cache = new SpriteCache(new AssetCatalog());
        BufferedImage a = cache.sliced(Asset.BUTTON_LIST, SliceSpec.stretch(88, 88), 220, 76, SpriteCache.Tint.NORMAL);
        BufferedImage b = cache.sliced(Asset.BUTTON_LIST, SliceSpec.stretch(88, 88), 220, 76, SpriteCache.Tint.NORMAL);
        assertTrue(a == b);
        BufferedImage hover = cache.sliced(Asset.BUTTON_LIST, SliceSpec.stretch(88, 88), 220, 76, SpriteCache.Tint.HOVER);
        assertTrue(a != hover);
        assertEquals(a.getWidth(), hover.getWidth());
    }

    private static boolean isMostly(int rgb, Color expected) {
        int dr = Math.abs(((rgb >> 16) & 255) - expected.getRed());
        int dg = Math.abs(((rgb >> 8) & 255) - expected.getGreen());
        int db = Math.abs((rgb & 255) - expected.getBlue());
        return dr < 60 && dg < 60 && db < 60;
    }
}
