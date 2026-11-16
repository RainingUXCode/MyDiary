package com.observernotes.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextInputTest {

    private static TextInput with(String s) {
        TextInput t = new TextInput();
        for (char c : s.toCharArray()) {
            t.type(c);
        }
        return t;
    }

    @Test
    void typesAtTheCaret() {
        TextInput t = with("ac");
        t.left();
        t.type('b');
        assertEquals("abc", t.text());
        assertEquals(2, t.caret());
    }

    @Test
    void backspaceAndDeleteStopAtTheEdges() {
        TextInput t = with("ab");
        assertTrue(t.backspace());
        assertEquals("a", t.text());
        assertFalse(t.delete());
        t.home();
        assertFalse(t.backspace());
        assertTrue(t.delete());
        assertTrue(t.isEmpty());
    }

    @Test
    void controlCharactersAreIgnored() {
        TextInput t = new TextInput();
        assertFalse(t.type('\n'));
        assertFalse(t.type('\u0008'));
        assertFalse(t.type('\t'));
        assertTrue(t.isEmpty());
    }

    @Test
    void respectsTheMaximumLength() {
        TextInput t = with("x".repeat(TextInput.MAX_LENGTH + 20));
        assertEquals(TextInput.MAX_LENGTH, t.text().length());
        assertFalse(t.type('y'));
    }

    @Test
    void homeEndAndArrowsMoveTheCaret() {
        TextInput t = with("abc");
        assertTrue(t.home());
        assertEquals(0, t.caret());
        assertFalse(t.left());
        assertTrue(t.right());
        assertTrue(t.end());
        assertEquals(3, t.caret());
        assertFalse(t.right());
        assertFalse(t.end());
    }

    @Test
    void pasteTurnsLineBreaksIntoSpacesAndIsTruncated() {
        TextInput t = new TextInput();
        t.paste("a\nb\tc");
        assertEquals("a b c", t.text());
        t.clear();
        t.paste("z".repeat(500));
        assertEquals(TextInput.MAX_LENGTH, t.text().length());
    }

    @Test
    void acceptsAccentedCharacters() {
        TextInput t = with("ação ✓");
        assertEquals("ação ✓", t.text());
    }

    @Test
    void focusIsIndependentFromText() {
        TextInput t = with("a");
        assertFalse(t.focused());
        t.setFocused(true);
        t.clear();
        assertTrue(t.focused());
        assertEquals(0, t.caret());
    }
}
