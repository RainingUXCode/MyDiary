package com.observernotes.ui.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssetLoadingTest {

    @Test
    void everyAssetInTheCatalogLoadsAsARealImage() {
        AssetCatalog catalog = new AssetCatalog();
        for (Asset a : Asset.values()) {
            BufferedImage img = catalog.original(a);
            assertNotNull(img);
            assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
        }
    }

    @Test
    void frameKeepsTheLogicalSize() {
        BufferedImage frame = new AssetCatalog().original(Asset.NOTEBOOK_FRAME);
        assertEquals(1397, frame.getWidth());
        assertEquals(742, frame.getHeight());
    }

    @Test
    void assetPathsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Asset a : Asset.values()) {
            assertTrue(seen.add(a.path()));
        }
    }

    @Test
    void everyPngInResourcesIsRegisteredOrIsAKnownUnusedOriginal() {
        Set<String> registered = new HashSet<>();
        for (Asset a : Asset.values()) {
            registered.add(a.path());
        }
        // Peças de apoio de Conquistas ainda não usadas nesta etapa (entram em 4C).
        Set<String> notYet = Set.of("achievements/06_roseta_vazia.png", "achievements/07_estrela_vazia_1.png",
                "achievements/08_estrela_vazia_2.png", "achievements/09_estrela_vazia_3.png");
        File root = new File("src/main/resources/assets");
        for (String dir : new String[] {"ui", "achievements"}) {
            File[] files = new File(root, dir).listFiles((d, n) -> n.endsWith(".png"));
            assertNotNull(files);
            for (File f : files) {
                String p = dir + "/" + f.getName();
                assertTrue(registered.contains(p) || notYet.contains(p));
            }
        }
    }

    @Test
    void cleanedAssetsKeepTheSizeOfTheirOriginals() {
        AssetCatalog c = new AssetCatalog();
        assertEquals(c.original(Asset.TASK_EMPTY_SLOT).getWidth(), c.original(Asset.TEXT_FIELD_CLEAN).getWidth());
        assertEquals(c.original(Asset.TASK_EMPTY_SLOT).getHeight(), c.original(Asset.TEXT_FIELD_CLEAN).getHeight());
        assertEquals(c.original(Asset.OBSERVER_CARD).getWidth(), c.original(Asset.OBSERVER_CARD_CLEAN).getWidth());
        assertEquals(c.original(Asset.OBSERVER_CARD).getHeight(), c.original(Asset.OBSERVER_CARD_CLEAN).getHeight());
    }

    @Test
    void cleanedTextFieldHasNoHeartLeftBehind() {
        // O original traz um coração em (59..135, 50..116); a cópia limpa tem de estar "lisa" ali.
        AssetCatalog c = new AssetCatalog();
        BufferedImage clean = c.original(Asset.TEXT_FIELD_CLEAN);
        BufferedImage orig = c.original(Asset.TASK_EMPTY_SLOT);
        int differing = 0;
        for (int y = 55; y < 112; y++) {
            for (int x = 65; x < 130; x++) {
                if (clean.getRGB(x, y) != orig.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        assertTrue(differing > 200);          // o coração foi realmente apagado
        int redish = 0;
        for (int y = 55; y < 112; y++) {
            for (int x = 65; x < 130; x++) {
                int rgb = clean.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int gr = (rgb >> 8) & 255;
                if (r > 200 && gr < 120) {
                    redish++;
                }
            }
        }
        assertEquals(0, redish);
    }

    @Test
    void cleanedObserverCardHasNoCursorLeftBehind() {
        BufferedImage clean = new AssetCatalog().original(Asset.OBSERVER_CARD_CLEAN);
        int dark = 0;
        for (int y = 40; y < 104; y++) {
            for (int x = 132; x < 136; x++) {
                int rgb = clean.getRGB(x, y);
                int g = (rgb >> 8) & 255;
                if (g < 150) {
                    dark++;
                }
            }
        }
        assertEquals(0, dark);
    }
}
