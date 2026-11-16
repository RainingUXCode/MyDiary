package com.observernotes.ui.assets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Carrega os PNGs do classpath uma única vez e entrega versões já escaladas,
 * guardadas em cache. Pintar um sprite escalado vira só uma cópia de pixels.
 * Deve ser usado apenas na thread de eventos do Swing.
 */
public final class AssetCatalog {

    private record SizeKey(Asset asset, int w, int h) { }

    private final Map<Asset, BufferedImage> originals = new EnumMap<>(Asset.class);
    private final Map<SizeKey, BufferedImage> scaled = new HashMap<>();

    /** Imagem original, sem alteração. */
    public BufferedImage original(Asset asset) {
        return originals.computeIfAbsent(asset, AssetCatalog::load);
    }

    /** Imagem no tamanho pedido, mantida em cache. */
    public BufferedImage scaled(Asset asset, int width, int height) {
        BufferedImage orig = original(asset);
        if (orig.getWidth() == width && orig.getHeight() == height) {
            return orig;
        }
        return scaled.computeIfAbsent(new SizeKey(asset, width, height),
                k -> ImageScaler.scale(orig, width, height));
    }

    /** Escala pela largura, preservando a proporção. */
    public BufferedImage scaledToWidth(Asset asset, int width) {
        BufferedImage orig = original(asset);
        int h = Math.max(1, Math.round(orig.getHeight() * (width / (float) orig.getWidth())));
        return scaled(asset, width, h);
    }

    /** Descarta as versões escaladas (por exemplo, quando a janela é redimensionada). */
    public void clearScaledCache() {
        scaled.clear();
    }

    private static BufferedImage load(Asset asset) {
        String resource = "/assets/" + asset.path();
        try (InputStream in = AssetCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Asset não encontrado no classpath: " + resource);
            }
            BufferedImage img = ImageIO.read(in);
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            var g = argb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return argb;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + resource, e);
        }
    }
}
