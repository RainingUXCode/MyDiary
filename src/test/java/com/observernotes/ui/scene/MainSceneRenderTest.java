package com.observernotes.ui.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.Layout;
import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MainSceneRenderTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static BufferedImage render(ScreenModel model, int w, int h, String typed) {
        NotebookCanvas canvas = new NotebookCanvas(new AssetCatalog());
        canvas.setSize(w, h);
        canvas.setModel(model);
        if (typed != null) {
            canvas.input().setFocused(true);
            canvas.input().paste(typed);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        canvas.paintScene(g, w, h);
        g.dispose();
        return img;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static int differing(BufferedImage a, BufferedImage b) {
        int[] pa = pixels(a);
        int[] pb = pixels(b);
        int n = 0;
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                n++;
            }
        }
        return n;
    }

    @Test
    void rendersEveryPanelModeWithSampleAndEmptyData() {
        for (PanelMode mode : PanelMode.values()) {
            for (ScreenModel m : new ScreenModel[] {MockupSample.screen(mode), ScreenModel.empty().withMode(mode)}) {
                BufferedImage img = render(m, 1397, 742, null);
                assertEquals(1397, img.getWidth());
                assertTrue(Arrays.stream(pixels(img)).distinct().count() > 500);   // não é uma tela em branco
            }
        }
    }

    @Test
    void rendersAtSeveralWindowSizesIncludingExtremeAspectRatios() {
        int[][] sizes = {{700, 400}, {1100, 584}, {1920, 1080}, {500, 900}, {2000, 300}, {1, 1}};
        for (int[] s : sizes) {
            BufferedImage img = render(MockupSample.screen(PanelMode.TASKS), s[0], s[1], "texto");
            assertEquals(s[0], img.getWidth());
        }
    }

    @Test
    void letterboxKeepsTheProportionOfTheNotebook() {
        // Janela muito larga: as laterais ficam só com a cor da mesa, o caderno não se estica.
        BufferedImage img = render(ScreenModel.empty(), 2000, 742, null);
        int desk = img.getRGB(5, 371);
        assertEquals(desk, img.getRGB(1990, 371));
        assertEquals(0xFDE9F0, desk & 0xFFFFFF);
        assertNotEquals(desk, img.getRGB(1000, 371));
    }

    @Test
    void sampleDataChangesTheTaskPanelButNotTheFrame() {
        BufferedImage empty = render(ScreenModel.empty(), 1397, 742, null);
        BufferedImage sample = render(MockupSample.screen(PanelMode.TASKS), 1397, 742, null);
        int n = differing(empty, sample);
        assertTrue(n > 5000);
        // O canto superior esquerdo (moldura pura) é idêntico nos dois.
        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 120; x++) {
                assertEquals(empty.getRGB(x, y), sample.getRGB(x, y));
            }
        }
    }

    @Test
    void panelModesProduceDifferentCentralPanelsButTheSameSummary() {
        BufferedImage tasks = render(MockupSample.screen(PanelMode.TASKS), 1397, 742, null);
        BufferedImage obs = render(MockupSample.screen(PanelMode.OBSERVERS), 1397, 742, null);
        int inPanel = 0;
        var p = Layout.MAIN_PANEL;
        for (int y = (int) p.getY() + 10; y < p.getMaxY() - 10; y++) {
            for (int x = (int) p.getX() + 10; x < p.getMaxX() - 10; x++) {
                if (tasks.getRGB(x, y) != obs.getRGB(x, y)) {
                    inPanel++;
                }
            }
        }
        assertTrue(inPanel > 5000);
        var s = Layout.SUMMARY_PANEL;
        for (int y = (int) s.getY() + 60; y < s.getMaxY() - 5; y += 3) {
            for (int x = (int) s.getX() + 5; x < s.getMaxX() - 5; x += 3) {
                assertEquals(tasks.getRGB(x, y), obs.getRGB(x, y));
            }
        }
    }

    @Test
    void typedTextAppearsInsideTheTextField() {
        BufferedImage idle = render(ScreenModel.empty(), 1397, 742, null);
        BufferedImage typed = render(ScreenModel.empty(), 1397, 742, "Comprar leite");
        int changed = 0;
        var f = Layout.TEXT_FIELD;
        for (int y = (int) f.getY(); y < f.getMaxY(); y++) {
            for (int x = (int) f.getX(); x < f.getMaxX(); x++) {
                if (idle.getRGB(x, y) != typed.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        assertTrue(changed > 500);
        // Fora do campo nada muda (ex.: botão ADD).
        var add = Layout.ADD;
        assertEquals(idle.getRGB((int) add.getCenterX(), (int) add.getCenterY()),
                typed.getRGB((int) add.getCenterX(), (int) add.getCenterY()));
    }

    @Test
    void veryLongTitleDoesNotSpillOutsideItsRow() {
        ScreenModel m = new ScreenModel(PanelMode.TASKS,
                java.util.List.of(new com.observernotes.ui.model.TaskRowModel("W".repeat(80), false)),
                ScreenModel.defaultObservers(), java.util.List.of(), 0, 6, "Conquistas", "nenhuma ainda", com.observernotes.ui.model.AchievementTier.NONE, "", false);
        BufferedImage img = render(m, 1397, 742, null);
        BufferedImage ref = render(ScreenModel.empty(), 1397, 742, null);
        // À direita do painel central (entre painéis) nada deve ter mudado.
        int x0 = (int) Layout.MAIN_PANEL.getMaxX() + 3;
        int x1 = (int) Layout.SUMMARY_PANEL.getX() - 3;
        for (int y = 200; y < 600; y++) {
            for (int x = x0; x < x1; x++) {
                assertEquals(ref.getRGB(x, y), img.getRGB(x, y));
            }
        }
    }

    @Test
    void hitTestMapsOnlyTheRealButtons() {
        var add = new java.awt.geom.Point2D.Double(Layout.ADD.getCenterX(), Layout.ADD.getCenterY());
        assertEquals(com.observernotes.ui.widget.ButtonId.ADD, MainScene.hitTest(add, ScreenModel.empty()));
        assertEquals(null, MainScene.hitTest(new java.awt.geom.Point2D.Double(Layout.PLAYER_PAUSE.getCenterX(),
                Layout.PLAYER_PAUSE.getCenterY()), ScreenModel.empty()));      // player é só visual na 4A
        assertEquals(null, MainScene.hitTest(new java.awt.geom.Point2D.Double(-5, -5), ScreenModel.empty()));
    }

    @Test
    void eachAchievementTierDrawsItsOwnPngInTheTile() {
        var tile = Layout.ACHIEVEMENT_TILE;
        BufferedImage[] imgs = new BufferedImage[com.observernotes.ui.model.AchievementTier.values().length];
        for (var t : com.observernotes.ui.model.AchievementTier.values()) {
            ScreenModel base = MockupSample.screen(PanelMode.TASKS);
            ScreenModel m = new ScreenModel(base.mode(), base.tasks(), base.observers(), base.history(), 2, 7,
                    "Conquistas", "x", t, "", false);
            imgs[t.ordinal()] = render(m, 1397, 742, null);
        }
        for (int i = 0; i < imgs.length; i++) {
            for (int j = i + 1; j < imgs.length; j++) {
                int n = 0;
                for (int y = (int) tile.getY(); y < tile.getMaxY(); y++) {
                    for (int x = (int) tile.getX(); x < tile.getMaxX(); x++) {
                        if (imgs[i].getRGB(x, y) != imgs[j].getRGB(x, y)) {
                            n++;
                        }
                    }
                }
                assertTrue(n > 300);
            }
        }
    }

    @Test
    void emptyRosetteIsOnlyUsedByTheEmptyTier() {
        assertEquals(com.observernotes.ui.assets.Asset.ACH_ROSETTE_EMPTY,
                com.observernotes.ui.model.AchievementTier.NONE.asset());
        for (var t : com.observernotes.ui.model.AchievementTier.values()) {
            if (t != com.observernotes.ui.model.AchievementTier.NONE) {
                assertNotEquals(com.observernotes.ui.assets.Asset.ACH_ROSETTE_EMPTY, t.asset());
            }
        }
        assertEquals(com.observernotes.ui.model.AchievementTier.NONE, ScreenModel.empty().achievementTier());
    }
}
