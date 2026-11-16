package com.observernotes.ui.render;

import com.observernotes.ui.assets.Asset;
import com.observernotes.ui.assets.SliceSpec;
import com.observernotes.ui.assets.SpriteCache;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Superfície de desenho em coordenadas <em>lógicas</em> (o espaço fixo do mockup).
 * Bitmaps são desenhados em pixels reais da tela, já escalados, para ficarem nítidos;
 * formas e textos usam a transformação lógica e são vetoriais.
 */
public final class Surface {

    private final Graphics2D device;
    private final double scale;
    private final int offX;
    private final int offY;
    private final SpriteCache sprites;
    private float globalAlpha = 1f;

    public Surface(Graphics2D device, double scale, int offX, int offY, SpriteCache sprites) {
        this.device = device;
        this.scale = scale;
        this.offX = offX;
        this.offY = offY;
        this.sprites = sprites;
    }

    /** Transparência aplicada a todos os bitmaps seguintes (1 = opaco). Texto e formas usam o Graphics2D do chamador. */
    public void setAlpha(float alpha) {
        this.globalAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    private void blit(BufferedImage img, int x, int y, float alpha) {
        float a = alpha * globalAlpha;
        if (a <= 0f) {
            return;
        }
        if (a >= 1f) {
            device.drawImage(img, x, y, null);
            return;
        }
        var old = device.getComposite();
        device.setComposite(java.awt.AlphaComposite.SrcOver.derive(a));
        device.drawImage(img, x, y, null);
        device.setComposite(old);
    }

    public double scale() {
        return scale;
    }

    /** Novo Graphics2D com a transformação lógica aplicada (o chamador deve chamar dispose). */
    public Graphics2D logical() {
        Graphics2D g = (Graphics2D) device.create();
        g.translate(offX, offY);
        g.scale(scale, scale);
        return g;
    }

    private int px(double logical) {
        return (int) Math.round(logical * scale);
    }

    /** Tamanho original do sprite (para calcular proporções). */
    public java.awt.geom.Dimension2D spriteSize(Asset asset) {
        BufferedImage o = sprites.catalog().original(asset);
        return new java.awt.Dimension(o.getWidth(), o.getHeight());
    }

    /** Sprite esticado por inteiro até o retângulo. */
    public void sprite(Asset asset, Rectangle2D r, SpriteCache.Tint tint) {
        int x0 = offX + px(r.getX());
        int y0 = offY + px(r.getY());
        int w = Math.max(1, offX + px(r.getMaxX()) - x0);
        int h = Math.max(1, offY + px(r.getMaxY()) - y0);
        blit(sprites.fit(asset, w, h, tint), x0, y0, 1f);
    }

    public void sprite(Asset asset, Rectangle2D r) {
        sprite(asset, r, SpriteCache.Tint.NORMAL);
    }

    /** Sprite centralizado em (cx, cy), com a largura dada e a proporção original. */
    public Rectangle2D spriteByWidth(Asset asset, double cx, double cy, double width) {
        return spriteByWidth(asset, cx, cy, width, 1f);
    }

    public Rectangle2D spriteByWidth(Asset asset, double cx, double cy, double width, float alpha) {
        BufferedImage o = sprites.catalog().original(asset);
        double h = width * o.getHeight() / o.getWidth();
        Rectangle2D r = new Rectangle2D.Double(cx - width / 2, cy - h / 2, width, h);
        spriteAlpha(asset, r, alpha);
        return r;
    }

    /** Sprite esticado até o retângulo, com transparência (observador desligado, efeitos). */
    public void spriteAlpha(Asset asset, Rectangle2D r, float alpha) {
        int x0 = offX + px(r.getX());
        int y0 = offY + px(r.getY());
        int w = Math.max(1, offX + px(r.getMaxX()) - x0);
        int h = Math.max(1, offY + px(r.getMaxY()) - y0);
        blit(sprites.fit(asset, w, h, SpriteCache.Tint.NORMAL), x0, y0, alpha);
    }

    /** Sprite com pontas preservadas (3-slice). */
    public void sliced(Asset asset, SliceSpec spec, Rectangle2D r, SpriteCache.Tint tint) {
        int x0 = offX + px(r.getX());
        int y0 = offY + px(r.getY());
        int w = Math.max(1, offX + px(r.getMaxX()) - x0);
        int h = Math.max(1, offY + px(r.getMaxY()) - y0);
        blit(sprites.sliced(asset, spec, w, h, tint), x0, y0, 1f);
    }

    public void sliced(Asset asset, SliceSpec spec, Rectangle2D r) {
        sliced(asset, spec, r, SpriteCache.Tint.NORMAL);
    }
}
