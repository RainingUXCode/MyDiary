package com.observernotes.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/** Texto com Java2D: centralizado, ajustado à largura e com contorno colorido (estilo Flash). */
public final class TextPainter {

    private TextPainter() { }

    /**
     * As fontes originais não têm alguns símbolos (✓, ♪): esses caracteres saem numa fonte do sistema do mesmo
     * tamanho, em vez de virarem quadradinhos.
     */
    private record Run(String text, Font font) { }

    private static java.util.List<Run> runs(String text, Font font) {
        if (font.canDisplayUpTo(text) == -1) {
            return java.util.List.of(new Run(text, font));
        }
        Font fallback = new Font(Font.SANS_SERIF, font.getStyle(), 1).deriveFont(font.getSize2D());
        java.util.List<Run> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Font curFont = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Font f = font.canDisplay(cp) ? font : fallback;
            if (curFont != null && f != curFont) {
                out.add(new Run(cur.toString(), curFont));
                cur.setLength(0);
            }
            curFont = f;
            cur.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        if (cur.length() > 0) {
            out.add(new Run(cur.toString(), curFont));
        }
        return out;
    }

    public static double width(Graphics2D g, String text, Font font) {
        double w = 0;
        for (Run r : runs(text, font)) {
            w += r.font().getStringBounds(r.text(), g.getFontRenderContext()).getWidth();
        }
        return w;
    }

    /** Reduz a fonte, se necessário, para o texto caber em {@code maxWidth}. */
    public static Font fit(Graphics2D g, String text, Font font, double maxWidth) {
        double w = width(g, text, font);
        if (w <= maxWidth || w == 0) {
            return font;
        }
        return font.deriveFont((float) (font.getSize2D() * maxWidth / w));
    }

    /** Texto já ajustado à largura: a fonte a usar e o texto (com "…" se foi cortado). */
    public record Fitted(Font font, String text, boolean truncated) { }

    /**
     * Mantém o tamanho da fonte e corta com "…" quando o texto não cabe; só reduz a fonte (até {@code minScale} do
     * tamanho original) antes de cortar, para não ficar pequeno demais.
     */
    public static Fitted fitEllipsis(Graphics2D g, String text, Font font, double maxWidth, double minScale) {
        Font f = fit(g, text, font, maxWidth);
        float min = (float) (font.getSize2D() * minScale);
        if (f.getSize2D() < min) {
            f = font.deriveFont(min);
        }
        String t = ellipsize(g, text, f, maxWidth);
        return new Fitted(f, t, !t.equals(text));
    }

    /** Corta o texto com "…" para caber em {@code maxWidth}, sem mudar a fonte. */
    public static String ellipsize(Graphics2D g, String text, Font font, double maxWidth) {
        if (text == null || width(g, text, font) <= maxWidth) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {                                           // maior prefixo que cabe com "…"
            int mid = (lo + hi + 1) >>> 1;
            if (width(g, text.substring(0, mid).stripTrailing() + "…", font) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo > 0 && Character.isHighSurrogate(text.charAt(lo - 1))) {
            lo--;
        }
        return lo == 0 ? "…" : text.substring(0, lo).stripTrailing() + "…";
    }

    /** Texto centralizado na caixa; contorno opcional ({@code outline != null}). */
    public static void centered(Graphics2D g, String text, Font font, Rectangle2D box,
                                Color fill, Color outline, double outlineWidth) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font f = fit(g, text, font, box.getWidth() * 0.94);
        double w = width(g, text, f);
        double x = box.getCenterX() - w / 2;
        draw(g, text, f, x, centerBaseline(g, f, box.getCenterY()), fill, outline, outlineWidth);
    }

    /** Texto alinhado à esquerda, com o centro vertical em {@code centerY}. */
    public static void left(Graphics2D g, String text, Font font, double x, double centerY, Color fill) {
        if (text == null || text.isEmpty()) {
            return;
        }
        draw(g, text, font, x, centerBaseline(g, font, centerY), fill, null, 0);
    }

    public static void draw(Graphics2D g, String text, Font font, double x, double baseline,
                            Color fill, Color outline, double outlineWidth) {
        java.awt.geom.Path2D.Double shape = new java.awt.geom.Path2D.Double();
        double cx = x;
        for (Run r : runs(text, font)) {
            GlyphVector gv = r.font().createGlyphVector(g.getFontRenderContext(), r.text());
            shape.append(gv.getOutline((float) cx, (float) baseline), false);
            cx += r.font().getStringBounds(r.text(), g.getFontRenderContext()).getWidth();
        }
        if (outline != null && outlineWidth > 0) {
            var old = g.getStroke();
            g.setColor(outline);
            g.setStroke(new BasicStroke((float) outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
            g.setStroke(old);
        }
        g.setColor(fill);
        g.fill(shape);
    }

    /** Linha de base que deixa o texto visualmente centralizado em {@code centerY}. */
    public static double centerBaseline(Graphics2D g, Font font, double centerY) {
        FontMetrics fm = g.getFontMetrics(font);
        return centerY + (fm.getAscent() - fm.getDescent()) / 2.0;
    }

    /**
     * Linha de base que centraliza o contorno realmente desenhado. Diferentemente da métrica nominal
     * da fonte, considera automaticamente ascendentes e descendentes presentes no texto atual.
     */
    public static double visualCenterBaseline(Graphics2D g, String text, Font font, double centerY) {
        if (text == null || text.isEmpty()) {
            return centerBaseline(g, font, centerY);
        }
        Rectangle2D visual = null;
        double x = 0;
        for (Run run : runs(text, font)) {
            GlyphVector glyphs = run.font().createGlyphVector(g.getFontRenderContext(), run.text());
            Rectangle2D b = glyphs.getVisualBounds();
            Rectangle2D shifted = new Rectangle2D.Double(x + b.getX(), b.getY(), b.getWidth(), b.getHeight());
            visual = visual == null ? shifted : visual.createUnion(shifted);
            x += run.font().getStringBounds(run.text(), g.getFontRenderContext()).getWidth();
        }
        return visual == null || visual.isEmpty() ? centerBaseline(g, font, centerY)
                : centerY - visual.getCenterY();
    }

    /** Risco horizontal sobre o texto (tarefa concluída). */
    public static void strike(Graphics2D g, double x, double centerY, double width, Color color, double thickness) {
        var old = g.getStroke();
        g.setColor(color);
        g.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x, centerY + 1, x + width, centerY + 1));
        g.setStroke(old);
    }

    /** Aplica um deslocamento ao desenho de sombra de texto. */
    public static AffineTransform offset(double dx, double dy) {
        return AffineTransform.getTranslateInstance(dx, dy);
    }
}
