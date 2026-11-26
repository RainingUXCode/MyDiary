package com.observernotes.ui.scene;

import com.observernotes.ui.Palette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Elementos que não existem como PNG e são desenhados em Java2D no mesmo estilo (flat, contorno
 * escuro, pastel): painéis, barra de progresso, pílulas, lápis, brilhos. Tudo vetorial.
 */
final class ProceduralArt {

    private ProceduralArt() { }

    /** Painel branco-creme com borda escura e uma segunda borda tracejada por dentro. */
    static void panel(Graphics2D g, Rectangle2D r, double arc, boolean innerDashed) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), arc, arc);
        g.setPaint(new GradientPaint(0, (float) r.getY(), Palette.PANEL_TOP, 0, (float) r.getMaxY(), Palette.PANEL_BOTTOM));
        g.fill(shape);
        g.setColor(Palette.BORDER);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        if (innerDashed) {
            double in = 13;
            g.setColor(Palette.BORDER_SOFT);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] {7f, 6f}, 0f));
            g.draw(new RoundRectangle2D.Double(r.getX() + in, r.getY() + in, r.getWidth() - 2 * in,
                    r.getHeight() - 2 * in, arc - 8, arc - 8));
        }
    }

    /** Pílula com degradê lilás e borda roxa (base do player). */
    static void pill(Graphics2D g, Rectangle2D r, Color top, Color bottom, Color border) {
        double arc = r.getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), arc, arc);
        g.setPaint(new GradientPaint(0, (float) r.getY(), top, 0, (float) r.getMaxY(), bottom));
        g.fill(shape);
        g.setColor(border);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(shape);
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(r.getX() + 4, r.getY() + 4, r.getWidth() - 8, r.getHeight() - 8, arc - 8, arc - 8));
    }

    /** Variação delicada para controles compactos da playlist. */
    static void thinPill(Graphics2D g, Rectangle2D r, Color top, Color bottom, Color border) {
        double arc = r.getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), arc, arc);
        g.setPaint(new GradientPaint(0, (float) r.getY(), top, 0, (float) r.getMaxY(), bottom));
        g.fill(shape);
        g.setColor(border);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(new Color(255, 255, 255, 135));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(r.getX() + 3.5, r.getY() + 3.5,
                r.getWidth() - 7, r.getHeight() - 7, arc - 7, arc - 7));
    }

    /** Espessura intermediária para a cápsula principal dos controles do player. */
    static void controlPill(Graphics2D g, Rectangle2D r, Color top, Color bottom, Color border) {
        double arc = r.getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), arc, arc);
        g.setPaint(new GradientPaint(0, (float) r.getY(), top, 0, (float) r.getMaxY(), bottom));
        g.fill(shape);
        g.setColor(border);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(new Color(255, 255, 255, 145));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(r.getX() + 4, r.getY() + 4,
                r.getWidth() - 8, r.getHeight() - 8, arc - 8, arc - 8));
    }

    /** Barra de progresso: trilho claro, preenchimento rosa listrado e borda vinho. */
    static void progressBar(Graphics2D g, Rectangle2D r, double fraction) {
        double arc = r.getHeight();
        RoundRectangle2D track = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), arc, arc);
        g.setColor(Palette.BAR_TRACK);
        g.fill(track);
        double f = Math.max(0, Math.min(1, fraction));
        if (f > 0) {
            var old = g.getClip();
            g.clip(track);
            double w = Math.max(arc * 0.9, r.getWidth() * f);
            Rectangle2D fill = new Rectangle2D.Double(r.getX(), r.getY(), w, r.getHeight());
            g.setColor(Palette.BAR_PINK);
            g.fill(fill);
            g.clip(fill);
            g.setColor(Palette.BAR_LIGHT);
            double step = 22;
            for (double x = r.getX() - r.getHeight(); x < r.getX() + w + r.getHeight(); x += step) {
                Path2D stripe = new Path2D.Double();
                stripe.moveTo(x, r.getMaxY());
                stripe.lineTo(x + 9, r.getMaxY());
                stripe.lineTo(x + 9 + r.getHeight(), r.getY());
                stripe.lineTo(x + r.getHeight(), r.getY());
                stripe.closePath();
                g.fill(stripe);
            }
            g.setClip(old);
        }
        g.setColor(Palette.BORDER);
        g.setStroke(new BasicStroke(2.6f));
        g.draw(track);
    }

    /** Lápis rosa inclinado dentro da caixa (ícone do campo de texto). */
    static void pencil(Graphics2D g, Rectangle2D box) {
        AffineTransform old = g.getTransform();
        g.translate(box.getCenterX(), box.getCenterY());
        g.rotate(Math.toRadians(40));
        double s = box.getHeight() / 30.0;
        g.scale(s, s);
        Rectangle2D body = new Rectangle2D.Double(-4.5, -15, 9, 20);
        g.setColor(new Color(0xF06FA0));
        g.fill(body);
        g.setColor(new Color(0xFFB6D0));
        g.fill(new Rectangle2D.Double(-4.5, -15, 3.2, 20));
        Polygon tip = new Polygon(new int[] {-4, 4, 0}, new int[] {5, 5, 14}, 3);
        g.setColor(new Color(0xFBD9A8));
        g.fill(tip);
        g.setColor(Palette.BORDER);
        g.fill(new Polygon(new int[] {-1, 1, 0}, new int[] {11, 11, 14}, 3));
        g.setColor(new Color(0xFF9CBB));
        g.fill(new Rectangle2D.Double(-4.5, -15, 9, 4));
        g.setColor(Palette.BORDER);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D outline = new Path2D.Double();
        outline.moveTo(-4.5, 5);
        outline.lineTo(-4.5, -15);
        outline.lineTo(4.5, -15);
        outline.lineTo(4.5, 5);
        outline.lineTo(0, 14);
        outline.closePath();
        g.draw(outline);
        g.drawLine(-4, 5, 4, 5);
        g.setTransform(old);
    }

    /** Brilho de quatro pontas. */
    static void sparkle(Graphics2D g, double cx, double cy, double radius, Color color) {
        Path2D p = new Path2D.Double();
        double k = radius * 0.22;
        p.moveTo(cx, cy - radius);
        p.quadTo(cx + k, cy - k, cx + radius, cy);
        p.quadTo(cx + k, cy + k, cx, cy + radius);
        p.quadTo(cx - k, cy + k, cx - radius, cy);
        p.quadTo(cx - k, cy - k, cx, cy - radius);
        g.setColor(color);
        g.fill(p);
    }

    /** Pílula pequena (horário no histórico). */
    static void timePill(Graphics2D g, Rectangle2D r) {
        RoundRectangle2D s = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(),
                r.getHeight(), r.getHeight());
        g.setColor(Palette.TIME_PILL_FILL);
        g.fill(s);
        g.setColor(Palette.TIME_PILL_BORDER);
        g.setStroke(new BasicStroke(1.8f));
        g.draw(s);
    }

    /** Lacinho de brilho usado perto do número de tarefas. */
    static void sparkleCluster(Graphics2D g, double cx, double cy) {
        sparkle(g, cx, cy, 13, new Color(0xFFD64D));
        sparkle(g, cx + 12, cy - 12, 7, new Color(0xFFE48A));
    }

    /** Coração compacto usado no rótulo do mural Little Notes. */
    static void heart(Graphics2D g, double cx, double cy, double radius, Color color) {
        Path2D heart = new Path2D.Double();
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0 * Math.PI * 2;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t)
                    - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            double px = cx + x * radius / 16.0;
            double py = cy - y * radius / 16.0;
            if (i == 0) {
                heart.moveTo(px, py);
            } else {
                heart.lineTo(px, py);
            }
        }
        heart.closePath();
        g.setColor(color);
        g.fill(heart);
    }

    /** Raios em leque (comemoração de medalha). */
    static void rays(Graphics2D g, double cx, double cy, double inner, double outer, int count, double rotation,
                     Color color) {
        g.setColor(color);
        double half = Math.PI / count * 0.55;
        for (int i = 0; i < count; i++) {
            double a = rotation + i * 2 * Math.PI / count;
            Path2D p = new Path2D.Double();
            p.moveTo(cx + Math.cos(a - half * 0.35) * inner, cy + Math.sin(a - half * 0.35) * inner);
            p.lineTo(cx + Math.cos(a - half) * outer, cy + Math.sin(a - half) * outer);
            p.lineTo(cx + Math.cos(a + half) * outer, cy + Math.sin(a + half) * outer);
            p.lineTo(cx + Math.cos(a + half * 0.35) * inner, cy + Math.sin(a + half * 0.35) * inner);
            p.closePath();
            g.fill(p);
        }
    }

    /** Contorno de foco do teclado: anel branco por baixo e roxo por cima, visível em qualquer fundo. */
    static void focusRing(Graphics2D g, Rectangle2D r, double arc) {
        double grow = 3.5;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX() - grow, r.getY() - grow,
                r.getWidth() + 2 * grow, r.getHeight() + 2 * grow, arc + grow, arc + grow);
        g.setColor(new Color(255, 255, 255, 215));                       // halo branco fininho
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(new Color(0x8E6BD6));                                // lavanda, combina com os botões roxos
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    /** Hover delicado: um contorno fininho e translúcido acompanhando a forma (sem círculo preenchido). */
    static void hoverRing(Graphics2D g, Rectangle2D r, double arc) {
        double grow = 2;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.getX() - grow, r.getY() - grow,
                r.getWidth() + 2 * grow, r.getHeight() + 2 * grow, arc + grow, arc + grow);
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setColor(new Color(180, 90, 154, 120));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    /** Triângulo de "tocar" arredondado, centrado em {@code r}. */
    static void playTriangle(Graphics2D g, Rectangle2D r, Color fill, Color outline) {
        double w = r.getWidth() * 0.46;
        double h = r.getHeight() * 0.56;
        double x = r.getCenterX() - w * 0.40;
        double y = r.getCenterY() - h / 2;
        java.awt.geom.Path2D tri = new java.awt.geom.Path2D.Double();
        tri.moveTo(x, y);
        tri.lineTo(x + w, r.getCenterY());
        tri.lineTo(x, y + h);
        tri.closePath();
        g.setColor(fill);
        g.fill(tri);
        g.setColor(outline);
        g.setStroke(new java.awt.BasicStroke((float) Math.max(2, r.getHeight() * 0.07), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.draw(tri);
    }

    /** Setinha para cima ou para baixo dentro de um botão redondo. */
    static void arrow(Graphics2D g, Rectangle2D r, boolean up, Color color) {
        double w = r.getWidth() * 0.46;
        double h = r.getHeight() * 0.30;
        double cx = r.getCenterX();
        double cy = r.getCenterY();
        java.awt.geom.Path2D t = new java.awt.geom.Path2D.Double();
        if (up) {
            t.moveTo(cx - w / 2, cy + h / 2);
            t.lineTo(cx, cy - h / 2);
            t.lineTo(cx + w / 2, cy + h / 2);
        } else {
            t.moveTo(cx - w / 2, cy - h / 2);
            t.lineTo(cx, cy + h / 2);
            t.lineTo(cx + w / 2, cy - h / 2);
        }
        g.setColor(color);
        g.setStroke(new java.awt.BasicStroke((float) Math.max(2.2, r.getHeight() * 0.11), java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.draw(t);
    }

    /** Colcheia (nota musical) desenhada com formas, sem depender de glifos da fonte. */
    static void note(Graphics2D g, double cx, double cy, double size, Color color) {
        double hw = size * 0.34;
        double hh = size * 0.24;
        double hx = cx - size * 0.18;
        double hy = cy + size * 0.30;
        g.setColor(color);
        g.fill(new java.awt.geom.Ellipse2D.Double(hx - hw / 2, hy - hh / 2, hw, hh));
        double sx = hx + hw / 2 - size * 0.04;
        g.setStroke(new java.awt.BasicStroke((float) Math.max(1.6, size * 0.09)));
        g.draw(new java.awt.geom.Line2D.Double(sx, hy, sx, cy - size * 0.42));
        java.awt.geom.Path2D flag = new java.awt.geom.Path2D.Double();
        flag.moveTo(sx, cy - size * 0.42);
        flag.curveTo(sx + size * 0.30, cy - size * 0.36, sx + size * 0.36, cy - size * 0.10, sx + size * 0.18, cy + size * 0.02);
        g.draw(flag);
    }
}
