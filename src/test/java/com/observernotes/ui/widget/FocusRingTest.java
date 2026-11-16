package com.observernotes.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FocusRingTest {

    private final AtomicLong now = new AtomicLong(0);
    private final FocusRing ring = new FocusRing(now::get);
    private final List<String> hints = new ArrayList<>();

    private static ScreenModel tasks(String... ids) {
        return new ScreenModel(PanelMode.TASKS, java.util.Arrays.stream(ids)
                .map(i -> new TaskRowModel(i, "título " + i, false)).toList(), ScreenModel.defaultObservers(),
                List.of(), 0, 0, "Conquistas", "nenhuma ainda", com.observernotes.ui.model.AchievementTier.NONE, "", false);
    }

    private static UiAction toggle(String id) {
        return new UiAction(UiAction.Type.TOGGLE_TASK, id);
    }

    private static UiAction delete(String id) {
        return new UiAction(UiAction.Type.DELETE_TASK, id);
    }

    @Test
    void orderIsNavigationFieldAddThenTheItemsOfThePanel() {
        var order = FocusRing.order(tasks("a", "b"));
        assertEquals(UiAction.Type.NAV_LIST, order.get(0).type());
        assertEquals(FocusRing.FIELD, order.get(3));
        assertEquals(UiAction.Type.ADD, order.get(4).type());
        assertEquals(List.of(toggle("a"), delete("a"), toggle("b"), delete("b")), order.subList(5, 9));
        assertEquals(9, order.size());
    }

    @Test
    void orderChangesWithThePanel() {
        var obs = FocusRing.order(ScreenModel.empty().withMode(PanelMode.OBSERVERS));
        assertEquals(4, obs.stream().filter(a -> a.type() == UiAction.Type.TOGGLE_OBSERVER).count());
        var hist = FocusRing.order(ScreenModel.empty().withMode(PanelMode.HISTORY));
        assertEquals(UiAction.Type.CLEAR, hist.get(hist.size() - 1).type());
        assertEquals(0, hist.stream().filter(a -> a.type() == UiAction.Type.TOGGLE_TASK).count());
    }

    @Test
    void tabWalksForwardAndWrapsAndShiftTabGoesBack() {
        ScreenModel m = tasks("a");
        var order = FocusRing.order(m);
        assertNull(ring.current());
        ring.move(m, true);
        assertEquals(order.get(0), ring.current());
        for (int i = 1; i < order.size(); i++) {
            ring.move(m, true);
            assertEquals(order.get(i), ring.current());
        }
        ring.move(m, true);
        assertEquals(order.get(0), ring.current());                     // volta ao início
        ring.move(m, false);
        assertEquals(order.get(order.size() - 1), ring.current());      // Shift+Tab volta ao fim
    }

    @Test
    void shiftTabWithoutFocusStartsAtTheLastControl() {
        ScreenModel m = tasks("a");
        ring.move(m, false);
        assertEquals(delete("a"), ring.current());
    }

    @Test
    void focusIsDroppedWhenItsItemDisappears() {
        ring.set(toggle("a"));
        ring.retain(tasks("b"));
        assertNull(ring.current());
        ring.set(FocusRing.FIELD);
        ring.retain(tasks());
        assertTrue(ring.isField());
    }

    @Test
    void spaceTogglesButEnterNeverDoes() {
        ScreenModel m = tasks("a");
        ring.set(toggle("a"));
        assertTrue(ring.activate(m, false, hints::add).isNone());
        assertEquals(toggle("a"), ring.activate(m, true, hints::add).action());
        assertTrue(hints.isEmpty());
    }

    @Test
    void deleteByKeyboardNeedsSpaceTwiceAndEnterNeverDeletes() {
        ScreenModel m = tasks("a");
        ring.set(delete("a"));
        assertTrue(ring.activate(m, false, hints::add).isNone());       // Enter: nada
        assertTrue(hints.isEmpty());
        assertTrue(ring.activate(m, true, hints::add).isNone());        // 1º Espaço: só avisa
        assertEquals(1, hints.size());
        assertTrue(hints.get(0).contains("título a"));
        assertEquals(delete("a"), ring.activate(m, true, hints::add).action());   // 2º confirma
        assertTrue(ring.activate(m, true, hints::add).isNone());        // de novo: precisa reconfirmar
    }

    @Test
    void confirmationExpiresAndIsCancelledByOtherKeysOrFocusChange() {
        ScreenModel m = tasks("a");
        ring.set(delete("a"));
        ring.activate(m, true, hints::add);
        now.addAndGet(FocusRing.CONFIRM_MS);
        assertTrue(ring.activate(m, true, hints::add).isNone());        // expirou: avisa de novo
        assertEquals(2, hints.size());
        ring.cancelPending();
        assertTrue(ring.activate(m, true, hints::add).isNone());        // cancelada: avisa de novo
        assertEquals(3, hints.size());
        ring.move(m, true);                                              // mudar o foco também cancela
        assertFalse(ring.hasPending());
    }

    @Test
    void confirmationIsPerItem() {
        ScreenModel m = tasks("a", "b");
        ring.set(delete("a"));
        ring.activate(m, true, hints::add);
        ring.set(delete("b"));
        assertTrue(ring.activate(m, true, hints::add).isNone());        // o aviso de "a" não vale para "b"
    }

    @Test
    void clearHistoryByKeyboardAlsoNeedsConfirmation() {
        ScreenModel m = ScreenModel.empty().withMode(PanelMode.HISTORY);
        ring.set(UiAction.of(UiAction.Type.CLEAR));
        assertTrue(ring.activate(m, false, hints::add).isNone());
        assertEquals(UiAction.Type.CLEAR, ring.activate(m, false, hints::add).action().type());
    }

    @Test
    void navigationAddAndTogglesActivateWithSpaceOrEnter() {
        ScreenModel m = tasks("a");
        for (UiAction a : List.of(UiAction.of(UiAction.Type.NAV_ALERTS), UiAction.of(UiAction.Type.ADD),
                new UiAction(UiAction.Type.TOGGLE_OBSERVER, "COUNTER"))) {
            ring.set(a);
            assertEquals(a, ring.activate(m, true, hints::add).action());
            assertEquals(a, ring.activate(m, false, hints::add).action());
        }
    }

    @Test
    void textFieldAndNoFocusNeverActivateAnything() {
        ScreenModel m = tasks("a");
        assertTrue(ring.activate(m, true, hints::add).isNone());
        ring.set(FocusRing.FIELD);
        assertTrue(ring.activate(m, true, hints::add).isNone());
        assertTrue(ring.activate(m, false, hints::add).isNone());
    }
}
