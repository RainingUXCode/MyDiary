package com.observernotes.ui.assets;

import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Redução de imagens com qualidade: divide pela metade enquanto o alvo for
 * menor que metade do tamanho atual e só então faz o ajuste final. Evita as
 * bordas serrilhadas de uma redução direta de 3x ou mais.
 */
final class ImageScaler {

    private ImageScaler() { }

    static BufferedImage scale(BufferedImage src, int targetW, int targetH) {
        if (targetW <= 0 || targetH <= 0) {
            throw new IllegalArgumentException("Tamanho inválido: " + targetW + "x" + targetH);
        }
        BufferedImage current = src;
        int w = current.getWidth();
        int h = current.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = draw(current, w, h);
        }
        if (w != targetW || h != targetH) {
            current = draw(current, targetW, targetH);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
