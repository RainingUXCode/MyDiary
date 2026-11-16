package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.anim.EffectLayer;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.HistoryRowModel;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.UiAction;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Efeitos desenhados com relógio manual: aparecem, terminam e deixam a tela idêntica à base. */
class AnimationRenderTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final AtomicLong clock = new AtomicLong(10_000);
    private NotebookCanvas canvas;

    private void start(ScreenModel m) {
        canvas = new NotebookCanvas(new AssetCatalog(), clock::get);
        canvas.setSize(1397, 742);
        canvas.setModel(m);
    }

    private BufferedImage shot() {
        BufferedImage img = new BufferedImage(1397, 742, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        canvas.paintScene(g, 1397, 742);
        g.dispose();
        return img;
    }

    private static int diff(BufferedImage a, BufferedImage b) {
        int[] pa = a.getRGB(0, 0, 1397, 742, null, 0, 1397);
        int[] pb = b.getRGB(0, 0, 1397, 742, null, 0, 1397);
        int n = 0;
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                n++;
            }
        }
        return n;
    }

    private static ScreenModel withHistory(int n, int offset) {
        ScreenModel s = MockupSample.screen(PanelMode.HISTORY);
        List<HistoryRowModel> h = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            h.add(new HistoryRowModel("21:" + (10 + i % 50), "Criada: tarefa " + i));
        }
        return new ScreenModel(s.mode(), s.tasks(), s.observers(), h, s.starsLit(), s.starsTotal(),
                s.achievementLabel(), s.achievementState(), s.achievementTier(), s.footerMessage(),
                s.newAchievement(), s.views(), s.feedback(), offset);
    }

    @Test
    void everyEffectKindDrawsDuringItsLifeAndLeavesNoTraceAfterwards() {
        start(MockupSample.screen(PanelMode.TASKS));
        BufferedImage base = shot();
        assertFalse(canvas.effects().isActive());
        Runnable[] starts = {
            () -> canvas.effects().taskCreated("Escrever o README"),
            () -> canvas.effects().taskCompleted("Escrever o README"),
            () -> canvas.effects().taskDeleted(new TaskRowModel("Fantasma", false), 1),
            () -> canvas.effects().star(1),
            () -> canvas.effects().star(2),
            () -> canvas.effects().star(3),
            () -> canvas.effects().medal(AchievementTier.BRONZE),
            () -> canvas.effects().medal(AchievementTier.SILVER),
            () -> canvas.effects().medal(AchievementTier.GOLD),
            () -> canvas.effects().medal(AchievementTier.MAX),
        };
        for (int k = 0; k < starts.length; k++) {
            canvas.effects().clear();
            starts[k].run();
            assertTrue(canvas.effects().isActive(), "efeito " + k);
            clock.addAndGet(120);
            assertTrue(diff(base, shot()) > 50, "efeito " + k + " deve alterar a tela no início");
            clock.addAndGet(200);
            shot();                                                      // meio da animação, sem exceção
            clock.addAndGet(4_000);                                      // além da maior duração
            assertFalse(canvas.effects().isActive(), "efeito " + k);
            assertEquals(0, diff(base, shot()), "efeito " + k + " deve sumir sem deixar rastro");
        }
    }

    @Test
    void celebrationsDrawOverEveryPanelAndEmptyData() {
        for (PanelMode mode : PanelMode.values()) {
            start(ScreenModel.empty().withMode(mode));
            canvas.effects().medal(AchievementTier.MAX);
            canvas.effects().star(2);
            clock.addAndGet(700);
            shot();
        }
    }

    @Test
    void manyEffectsAreCappedAndStillRender() {
        start(MockupSample.screen(PanelMode.TASKS));
        for (int i = 0; i < 100; i++) {
            canvas.effects().taskCreated("x" + i);
            canvas.effects().star(1 + i % 3);
        }
        assertTrue(canvas.effects().active().size() <= 24);
        shot();
    }

    @Test
    void effectsStopAtTheEndOfTheLayerClock() {
        start(MockupSample.screen(PanelMode.TASKS));
        canvas.effects().taskCompleted("Escrever o README");
        assertEquals(1, canvas.effects().active().size());
        clock.addAndGet(EffectLayer.COMPLETE_MS + 1);
        assertTrue(canvas.effects().active().isEmpty());
    }

    @Test
    void hoverAndKeyboardFocusPaintVisibleIndicators() {
        start(MockupSample.screen(PanelMode.TASKS));
        BufferedImage base = shot();
        canvas.focusRing().set(new UiAction(UiAction.Type.TOGGLE_TASK, "Escrever o README"));
        BufferedImage focused = shot();
        assertTrue(diff(base, focused) > 30, "anel de foco precisa ser visível");
        canvas.focusRing().set(UiAction.of(UiAction.Type.NAV_LIST));
        assertTrue(diff(base, shot()) > 30);
        canvas.focusRing().set(UiAction.of(UiAction.Type.ADD));
        assertTrue(diff(base, shot()) > 30);
        canvas.focusRing().clear();
        assertEquals(0, diff(base, shot()));
        Rectangle2D check = MainScene.checkRect(Layout.row(2));
        double s = Math.min(1397 / (double) Layout.WIDTH, 742 / (double) Layout.HEIGHT);
        int x = (int) Math.round((1397 - Layout.WIDTH * s) / 2 + check.getCenterX() * s);
        int y = (int) Math.round((742 - Layout.HEIGHT * s) / 2 + check.getCenterY() * s);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
        assertTrue(diff(base, shot()) > 10, "hover precisa mudar o desenho");
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
        BufferedImage pressed = shot();
        assertTrue(diff(base, pressed) > 10, "estado pressionado precisa mudar o desenho");
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, 0, 0, 0, 0, 0, false));
    }

    @Test
    void scrollBarAppearsOnlyWithMoreThanSixEntriesAndOffsetChangesRows() {
        start(withHistory(6, 0));
        BufferedImage six = shot();
        start(withHistory(7, 0));
        BufferedImage seven = shot();
        assertTrue(diff(six, seven) > 100);                              // barra + conteúdo
        start(withHistory(30, 0));
        BufferedImage top = shot();
        start(withHistory(30, 12));
        BufferedImage middle = shot();
        start(withHistory(30, 24));
        BufferedImage end = shot();
        assertTrue(diff(top, middle) > 200);
        assertTrue(diff(middle, end) > 200);
    }

    @Test
    void layoutIsUnchangedWhenNothingIsHoveredFocusedOrAnimated() {
        start(MockupSample.screen(PanelMode.TASKS));
        BufferedImage a = shot();
        BufferedImage b = shot();
        assertEquals(0, diff(a, b));
        int[] px = a.getRGB(0, 0, 1397, 742, null, 0, 1397);
        assertTrue(Arrays.stream(px).distinct().count() > 500);
    }
}
