package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.LittleNotesModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.ButtonId;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Eventos sintéticos despachados direto ao canvas (não substitui um teste manual com janela). */
class NotebookCanvasInteractionTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static NotebookCanvas canvas(int w, int h) {
        NotebookCanvas c = new NotebookCanvas(new AssetCatalog());
        c.setSize(w, h);
        return c;
    }

    private static int[] screenOf(NotebookCanvas c, Rectangle2D logical) {
        double s = Math.min(c.getWidth() / (double) Layout.WIDTH, c.getHeight() / (double) Layout.HEIGHT);
        double offX = (c.getWidth() - Layout.WIDTH * s) / 2.0;
        double offY = (c.getHeight() - Layout.HEIGHT * s) / 2.0;
        return new int[] {(int) Math.round(offX + logical.getCenterX() * s), (int) Math.round(offY + logical.getCenterY() * s)};
    }

    private static void mouse(NotebookCanvas c, int id, int x, int y) {
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static void key(NotebookCanvas c, int code, char ch) {
        long t = System.currentTimeMillis();
        // Fora de uma janela visível o KeyboardFocusManager descarta eventos de teclado,
        // então entregamos direto aos ouvintes registrados no canvas.
        for (var l : c.getKeyListeners()) {
            l.keyPressed(new KeyEvent(c, KeyEvent.KEY_PRESSED, t, 0, code, ch));
            if (ch != KeyEvent.CHAR_UNDEFINED && ch >= ' ') {
                l.keyTyped(new KeyEvent(c, KeyEvent.KEY_TYPED, t, 0, KeyEvent.VK_UNDEFINED, ch));
            }
        }
    }

    @Test
    void toLogicalInvertsTheScaleAndLetterbox() {
        NotebookCanvas c = canvas(2000, 742);          // letterbox lateral
        Point2D p = c.toLogical(1000, 371);
        assertTrue(Math.abs(Layout.WIDTH / 2.0 - p.getX()) < 0.01);
        assertTrue(Math.abs(Layout.HEIGHT / 2.0 - p.getY()) < 0.01);
        Point2D origin = c.toLogical((2000 - Layout.WIDTH) / 2, 0);
        assertTrue(Math.abs(origin.getX()) < 1.0);
    }

    @Test
    void clickingTheFieldFocusesItAndTypingEditsIt() {
        NotebookCanvas c = canvas(1100, 584);
        int[] f = screenOf(c, Layout.TEXT_FIELD);
        assertFalse(c.input().focused());
        mouse(c, MouseEvent.MOUSE_PRESSED, f[0], f[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, f[0], f[1]);
        assertTrue(c.input().focused());
        key(c, KeyEvent.VK_A, 'o');
        key(c, KeyEvent.VK_B, 'i');
        assertEquals("oi", c.input().text());
        key(c, KeyEvent.VK_BACK_SPACE, '\b');
        assertEquals("o", c.input().text());
        key(c, KeyEvent.VK_ESCAPE, (char) 27);
        assertFalse(c.input().focused());
        key(c, KeyEvent.VK_X, 'x');
        assertEquals("o", c.input().text());           // sem foco não digita
    }

    @Test
    void clickingElsewhereRemovesFocus() {
        NotebookCanvas c = canvas(1100, 584);
        int[] f = screenOf(c, Layout.TEXT_FIELD);
        mouse(c, MouseEvent.MOUSE_PRESSED, f[0], f[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, f[0], f[1]);
        int[] other = screenOf(c, Layout.MAIN_PANEL);
        mouse(c, MouseEvent.MOUSE_PRESSED, other[0], other[1]);
        assertFalse(c.input().focused());
    }

    @Test
    void completeClickOnAButtonNotifiesTheListenerOnce() {
        NotebookCanvas c = canvas(1397, 742);
        List<com.observernotes.ui.widget.UiAction> clicks = new ArrayList<>();
        c.setActionListener(clicks::add);
        int[] a = screenOf(c, Layout.ADD);
        mouse(c, MouseEvent.MOUSE_MOVED, a[0], a[1]);
        mouse(c, MouseEvent.MOUSE_PRESSED, a[0], a[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, a[0], a[1]);
        assertEquals(List.of(com.observernotes.ui.widget.UiAction.of(ButtonId.ADD)), clicks);
    }

    @Test
    void noteAddUsesClickCursorAndNoteBodyUsesTextCursor() {
        NotebookCanvas c = canvas(1397, 742);
        int[] add = screenOf(c, MainScene.noteAddRect(c.model().littleNotes()));

        mouse(c, MouseEvent.MOUSE_MOVED, add[0], add[1]);

        assertEquals(Cursor.HAND_CURSOR, c.getCursor().getType());

        ScreenModel base = c.model();
        LittleNotesModel notes = new LittleNotesModel(
                List.of(new LittleNotesModel.Item("n1", "Beber água")), "");
        c.setModel(new ScreenModel(base.mode(), base.tasks(), base.observers(), base.history(), notes,
                base.achievementLabel(), base.achievementState(), base.achievementTier(), base.footerMessage(),
                base.newAchievement(), base.views(), base.feedback(), base.historyOffset(), base.taskPages()));
        int[] note = screenOf(c, MainScene.noteRect(0));

        mouse(c, MouseEvent.MOUSE_MOVED, note[0], note[1]);

        assertEquals(Cursor.TEXT_CURSOR, c.getCursor().getType());
    }

    @Test
    void releasingOutsideTheButtonCancelsTheClick() {
        NotebookCanvas c = canvas(1397, 742);
        List<com.observernotes.ui.widget.UiAction> clicks = new ArrayList<>();
        c.setActionListener(clicks::add);
        int[] a = screenOf(c, Layout.ADD);
        int[] away = screenOf(c, Layout.MAIN_PANEL);
        mouse(c, MouseEvent.MOUSE_PRESSED, a[0], a[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, away[0], away[1]);
        assertTrue(clicks.isEmpty());
    }

    @Test
    void withoutAListenerAClickDoesNothingAndDoesNotFail() {
        NotebookCanvas c = canvas(1397, 742);
        int[] n = screenOf(c, Layout.NAV_ALERTS);
        mouse(c, MouseEvent.MOUSE_PRESSED, n[0], n[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, n[0], n[1]);
        assertEquals(com.observernotes.ui.model.PanelMode.TASKS, c.model().mode());   // navegação é 4C
    }

    @Test
    void playerControlsAreVisualOnly() {
        NotebookCanvas c = canvas(1397, 742);
        List<com.observernotes.ui.widget.UiAction> clicks = new ArrayList<>();
        c.setActionListener(clicks::add);
        int[] p = screenOf(c, Layout.PLAYER_PAUSE);
        mouse(c, MouseEvent.MOUSE_PRESSED, p[0], p[1]);
        mouse(c, MouseEvent.MOUSE_RELEASED, p[0], p[1]);
        assertTrue(clicks.isEmpty());
    }
}
