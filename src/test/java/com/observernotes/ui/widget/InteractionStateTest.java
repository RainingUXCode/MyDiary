package com.observernotes.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InteractionStateTest {

    @Test
    void hoverReportsChangesOnly() {
        InteractionState s = new InteractionState();
        assertTrue(s.setHover(ButtonId.ADD));
        assertFalse(s.setHover(ButtonId.ADD));
        assertTrue(s.isHover(ButtonId.ADD));
        assertTrue(s.setHover(null));
        assertFalse(s.isHover(ButtonId.ADD));
    }

    @Test
    void pressedOnlyShowsWhilePointerStaysOnTheButton() {
        InteractionState s = new InteractionState();
        s.setHover(ButtonId.NAV_LIST);
        s.press(ButtonId.NAV_LIST);
        assertTrue(s.isPressed(ButtonId.NAV_LIST));
        assertFalse(s.isHover(ButtonId.NAV_LIST));      // pressionado tem prioridade sobre hover
        s.setHover(ButtonId.ADD);
        assertFalse(s.isPressed(ButtonId.NAV_LIST));    // saiu de cima: não mostra pressionado
    }

    @Test
    void releaseReturnsWhatWasPressedAndClears() {
        InteractionState s = new InteractionState();
        s.setHover(ButtonId.NAV_RECORD);
        s.press(ButtonId.NAV_RECORD);
        assertEquals(ButtonId.NAV_RECORD, s.release());
        assertNull(s.pressed());
        assertNull(s.release());
    }
}
