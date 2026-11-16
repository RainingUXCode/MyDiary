package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.UiAction;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Teclado, roda do mouse e hover/pressionado com eventos sintéticos (sem janela real). */
class NotebookCanvasKeyboardTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<UiAction> actions = new ArrayList<>();
    private NotebookCanvas canvas;

    private void start(ScreenModel model) {
        canvas = new NotebookCanvas(new AssetCatalog(), clock::get);
        canvas.setSize(1397, 742);
        canvas.setModel(model);
        canvas.setActionListener(actions::add);
    }

    private void press(int code) {
        canvas.handleKey(code, false, false);
    }

    private void typed(int code, char ch) {
        long t = System.currentTimeMillis();
        for (var l : canvas.getKeyListeners()) {
            l.keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, t, 0, code, ch));
            l.keyTyped(new KeyEvent(canvas, KeyEvent.KEY_TYPED, t, 0, KeyEvent.VK_UNDEFINED, ch));
        }
    }

    private static int[] screenOf(NotebookCanvas c, Rectangle2D r) {
        double s = Math.min(c.getWidth() / (double) Layout.WIDTH, c.getHeight() / (double) Layout.HEIGHT);
        double ox = (c.getWidth() - Layout.WIDTH * s) / 2.0;
        double oy = (c.getHeight() - Layout.HEIGHT * s) / 2.0;
        return new int[] {(int) Math.round(ox + r.getCenterX() * s), (int) Math.round(oy + r.getCenterY() * s)};
    }

    private void mouse(int id, Rectangle2D over) {
        int[] p = screenOf(canvas, over);
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), 0, p[0], p[1], 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void tabVisitsControlsInOrderAndWrapsBothWays() {
        start(MockupSample.screen(PanelMode.TASKS));
        List<UiAction> order = com.observernotes.ui.widget.FocusRing.order(canvas.model());
        assertEquals(UiAction.Type.NAV_LIST, order.get(0).type());
        for (UiAction expected : order) {
            press(KeyEvent.VK_TAB);
            assertEquals(expected, canvas.focusRing().current());
        }
        press(KeyEvent.VK_TAB);                                          // dá a volta
        assertEquals(order.get(0), canvas.focusRing().current());
        canvas.handleKey(KeyEvent.VK_TAB, true, false);
        assertEquals(order.get(order.size() - 1), canvas.focusRing().current());
        assertTrue(actions.isEmpty());                                   // Tab nunca aciona nada
    }

    @Test
    void spaceTogglesButEnterOnACheckboxDoesNothing() {
        start(MockupSample.screen(PanelMode.TASKS));
        UiAction toggle = new UiAction(UiAction.Type.TOGGLE_TASK, "Escrever o README");
        canvas.focusRing().set(toggle);
        press(KeyEvent.VK_ENTER);
        assertTrue(actions.isEmpty());
        press(KeyEvent.VK_SPACE);
        assertEquals(List.of(toggle), actions);
    }

    @Test
    void deleteByKeyboardNeedsTwoSpacesAndShowsAHintFirst() {
        start(MockupSample.screen(PanelMode.TASKS));
        UiAction del = new UiAction(UiAction.Type.DELETE_TASK, "Escrever o README");
        canvas.focusRing().set(del);
        press(KeyEvent.VK_ENTER);
        assertTrue(actions.isEmpty());
        press(KeyEvent.VK_SPACE);
        assertEquals(1, actions.size());
        assertEquals(UiAction.Type.HINT, actions.get(0).type());
        press(KeyEvent.VK_SPACE);
        assertEquals(del, actions.get(1));
        press(KeyEvent.VK_SPACE);                                        // confirmação foi consumida
        assertEquals(UiAction.Type.HINT, actions.get(2).type());
        assertEquals(3, actions.size());
    }

    @Test
    void confirmationExpiresAndOtherKeysCancelIt() {
        start(MockupSample.screen(PanelMode.TASKS));
        UiAction del = new UiAction(UiAction.Type.DELETE_TASK, "Escrever o README");
        canvas.focusRing().set(del);
        press(KeyEvent.VK_SPACE);
        clock.addAndGet(com.observernotes.ui.widget.FocusRing.CONFIRM_MS + 1);
        press(KeyEvent.VK_SPACE);
        assertTrue(actions.stream().noneMatch(a -> a.type() == UiAction.Type.DELETE_TASK));
        press(KeyEvent.VK_A);                                            // outra tecla cancela
        press(KeyEvent.VK_SPACE);
        assertTrue(actions.stream().noneMatch(a -> a.type() == UiAction.Type.DELETE_TASK));
    }

    @Test
    void typingInTheFieldNeverActivatesAnythingAndEnterSubmits() {
        start(MockupSample.screen(PanelMode.TASKS));
        press(KeyEvent.VK_TAB);
        press(KeyEvent.VK_TAB);
        press(KeyEvent.VK_TAB);
        press(KeyEvent.VK_TAB);                                          // NAV_LIST, ALERTS, RECORD, FIELD
        assertTrue(canvas.focusRing().isField());
        typed(KeyEvent.VK_A, 'a');
        typed(KeyEvent.VK_SPACE, ' ');
        typed(KeyEvent.VK_B, 'b');
        assertEquals("a b", canvas.input().text());
        assertTrue(actions.isEmpty());
        press(KeyEvent.VK_ENTER);
        assertEquals(List.of(UiAction.of(UiAction.Type.SUBMIT)), actions);
    }

    @Test
    void escapeLeavesTheFieldAndClearsFocus() {
        start(MockupSample.screen(PanelMode.TASKS));
        canvas.focusRing().set(com.observernotes.ui.widget.FocusRing.FIELD);
        press(KeyEvent.VK_ESCAPE);
        assertEquals(null, canvas.focusRing().current());
    }

    @Test
    void navAndAddAreActivatedByKeyboard() {
        start(MockupSample.screen(PanelMode.TASKS));
        canvas.focusRing().set(UiAction.of(UiAction.Type.NAV_ALERTS));
        press(KeyEvent.VK_ENTER);
        canvas.focusRing().set(UiAction.of(UiAction.Type.ADD));
        press(KeyEvent.VK_SPACE);
        assertEquals(List.of(UiAction.of(UiAction.Type.NAV_ALERTS), UiAction.of(UiAction.Type.ADD)), actions);
    }

    @Test
    void historyKeysAndWheelRequestScrollOnlyInHistoryMode() {
        start(MockupSample.screen(PanelMode.TASKS));
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_END);
        assertTrue(actions.isEmpty());
        start(MockupSample.screen(PanelMode.HISTORY));
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_UP);
        press(KeyEvent.VK_PAGE_DOWN);
        press(KeyEvent.VK_PAGE_UP);
        press(KeyEvent.VK_HOME);
        press(KeyEvent.VK_END);
        assertEquals(List.of("1", "-1", "6", "-6", "home", "end"),
                actions.stream().map(UiAction::key).toList());
        assertTrue(actions.stream().allMatch(a -> a.type() == UiAction.Type.SCROLL_HISTORY));
        actions.clear();
        int[] p = screenOf(canvas, Layout.MAIN_PANEL);
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL, 0, 0, p[0], p[1], 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        assertEquals(1, actions.size());
        assertEquals(UiAction.Type.SCROLL_HISTORY, actions.get(0).type());
        assertTrue(Integer.parseInt(actions.get(0).key()) > 0);
    }

    @Test
    void mouseClickHidesKeyboardFocusAndItemActsOnlyOnReleaseOverTheSameItem() {
        start(MockupSample.screen(PanelMode.TASKS));
        canvas.focusRing().set(UiAction.of(UiAction.Type.NAV_LIST));
        Rectangle2D check = MainScene.checkRect(Layout.row(2));
        mouse(MouseEvent.MOUSE_PRESSED, check);
        assertEquals(null, canvas.focusRing().current());
        assertTrue(actions.isEmpty());                                   // pressionar não age
        mouse(MouseEvent.MOUSE_RELEASED, check);
        assertEquals(List.of(new UiAction(UiAction.Type.TOGGLE_TASK, "Escrever o README")), actions);
        actions.clear();
        mouse(MouseEvent.MOUSE_PRESSED, check);                          // arrasta para outra linha
        mouse(MouseEvent.MOUSE_RELEASED, MainScene.checkRect(Layout.row(3)));
        assertTrue(actions.isEmpty());
    }

    @Test
    void hoverAndPressedStatesAreTrackedForItems() {
        start(MockupSample.screen(PanelMode.TASKS));
        UiAction toggle = new UiAction(UiAction.Type.TOGGLE_TASK, "Escrever o README");
        Rectangle2D check = MainScene.checkRect(Layout.row(2));
        mouse(MouseEvent.MOUSE_MOVED, check);
        assertFalse(actions.contains(toggle));
        mouse(MouseEvent.MOUSE_PRESSED, check);
        mouse(MouseEvent.MOUSE_RELEASED, check);
        assertEquals(1, actions.size());
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, 0, 0, 0, 0, 0, false));
    }

    @Test
    void keyboardNeverActsOnAnEmptyFocus() {
        start(MockupSample.screen(PanelMode.TASKS));
        press(KeyEvent.VK_SPACE);
        press(KeyEvent.VK_ENTER);
        assertTrue(actions.isEmpty());
    }
}
