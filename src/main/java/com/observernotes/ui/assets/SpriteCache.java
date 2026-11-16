package com.observernotes.ui.assets;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Entrega sprites já no tamanho exato em pixels da tela (cache), em três estados visuais.
 * Pintar vira só uma cópia de pixels, então o desenho fica nítido e rápido.
 */
public final class SpriteCache {

    /** Estado visual de um botão. */
    public enum Tint { NORMAL, HOVER, PRESSED }

    private record Key(Asset asset, SliceSpec spec, int w, int h, Tint tint) { }

    private final AssetCatalog catalog;
    private final Map<Key, BufferedImage> cache = new HashMap<>();

    public SpriteCache(AssetCatalog catalog) {
        this.catalog = catalog;
    }

    public AssetCatalog catalog() {
        return catalog;
    }

    /** Imagem esticada por inteiro para {@code w x h}. */
    public BufferedImage fit(Asset asset, int w, int h, Tint tint) {
        trim();
        return cache.computeIfAbsent(new Key(asset, null, w, h, tint),
                k -> tinted(catalog.scaled(asset, w, h), tint));
    }

    /** Imagem com pontas preservadas e miolo adaptado. */
    public BufferedImage sliced(Asset asset, SliceSpec spec, int w, int h, Tint tint) {
        trim();
        return cache.computeIfAbsent(new Key(asset, spec, w, h, tint),
                k -> tinted(SliceRenderer.render(catalog.original(asset), spec, w, h), tint));
    }

    /** Animações geram tamanhos variados; passado do teto, recomeça o cache (barato e raro). */
    private void trim() {
        if (cache.size() > MAX_ENTRIES) {
            cache.clear();
        }
    }

    static final int MAX_ENTRIES = 600;

    public void clear() {
        cache.clear();
        catalog.clearScaledCache();
    }

    private static BufferedImage tinted(BufferedImage src, Tint tint) {
        if (tint == Tint.NORMAL) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
            g.setComposite(AlphaComposite.SrcAtop.derive(tint == Tint.HOVER ? 0.20f : 0.18f));
            g.setColor(tint == Tint.HOVER ? Color.WHITE : new Color(0x7A1848));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
        } finally {
            g.dispose();
        }
        return out;
    }
}
