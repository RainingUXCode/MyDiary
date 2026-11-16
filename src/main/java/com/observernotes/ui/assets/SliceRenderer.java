package com.observernotes.ui.assets;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Desenha uma imagem em qualquer largura mantendo a altura proporcional: as pontas são
 * escaladas uniformemente e só o miolo se adapta (esticado ou repetido).
 */
final class SliceRenderer {

    private SliceRenderer() { }

    static BufferedImage render(BufferedImage src, SliceSpec spec, int targetW, int targetH) {
        double scale = targetH / (double) src.getHeight();
        int capL = (int) Math.round(spec.capLeft() * scale);
        int capR = (int) Math.round(spec.capRight() * scale);
        if (capL + capR > targetW) {          // alvo mais estreito que as pontas: reduz as duas
            double k = targetW / (double) (capL + capR);
            capL = (int) Math.floor(capL * k);
            capR = targetW - capL - (int) Math.floor(capR * k) < 0 ? 0 : (int) Math.floor(capR * k);
        }
        int midW = Math.max(0, targetW - capL - capR);

        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        try {
            if (capL > 0) {
                g.drawImage(ImageScaler.scale(src.getSubimage(0, 0, spec.capLeft(), src.getHeight()), capL, targetH),
                        0, 0, null);
            }
            if (capR > 0) {
                g.drawImage(ImageScaler.scale(
                        src.getSubimage(src.getWidth() - spec.capRight(), 0, spec.capRight(), src.getHeight()),
                        capR, targetH), targetW - capR, 0, null);
            }
            int srcMidW = src.getWidth() - spec.capLeft() - spec.capRight();
            if (midW > 0 && srcMidW > 0) {
                if (spec.tileWidth() > 0 && spec.tileWidth() <= srcMidW) {
                    tile(g, src, spec, capL, midW, targetH, scale);
                } else {
                    BufferedImage mid = src.getSubimage(spec.capLeft(), 0, srcMidW, src.getHeight());
                    g.drawImage(ImageScaler.scale(mid, midW, targetH), capL, 0, null);
                }
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void tile(Graphics2D g, BufferedImage src, SliceSpec spec, int capL, int midW, int targetH,
                             double scale) {
        BufferedImage strip = src.getSubimage(spec.capLeft(), 0, spec.tileWidth(), src.getHeight());
        int n = Math.max(1, (int) Math.round(midW / (spec.tileWidth() * scale)));
        int x = capL;
        for (int i = 0; i < n; i++) {
            int next = capL + (int) Math.round(midW * (i + 1) / (double) n);   // sem frestas entre as repetições
            int w = Math.max(1, next - x);
            g.drawImage(ImageScaler.scale(strip, w, targetH), x, 0, null);
            x = next;
        }
    }
}
