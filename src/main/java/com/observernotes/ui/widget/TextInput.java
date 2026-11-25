package com.observernotes.ui.widget;

/**
 * Campo de texto de uma linha desenhado pelo próprio app: texto, cursor e foco. Não conhece
 * Swing nem o domínio, então é testável. A tecla Enter não é tratada aqui (criar tarefa é 4B).
 */
public final class TextInput {

    /** Limite compartilhado pelos títulos de tarefa e bilhetes do Little Notes. */
    public static final int MAX_LENGTH = 60;

    private final StringBuilder text = new StringBuilder();
    private int caret;
    private boolean focused;

    public String text() {
        return text.toString();
    }

    public int caret() {
        return caret;
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isEmpty() {
        return text.length() == 0;
    }

    /** Insere um caractere imprimível na posição do cursor. @return false se ignorado. */
    public boolean type(char c) {
        if (Character.isISOControl(c) || text.length() >= MAX_LENGTH) {
            return false;
        }
        text.insert(caret++, c);
        return true;
    }

    public boolean backspace() {
        if (caret == 0) {
            return false;
        }
        text.deleteCharAt(--caret);
        return true;
    }

    public boolean delete() {
        if (caret >= text.length()) {
            return false;
        }
        text.deleteCharAt(caret);
        return true;
    }

    public boolean left() {
        if (caret == 0) {
            return false;
        }
        caret--;
        return true;
    }

    public boolean right() {
        if (caret >= text.length()) {
            return false;
        }
        caret++;
        return true;
    }

    public boolean home() {
        boolean changed = caret != 0;
        caret = 0;
        return changed;
    }

    public boolean end() {
        boolean changed = caret != text.length();
        caret = text.length();
        return changed;
    }

    /** Cola texto (quebras de linha viram espaço), respeitando o limite. */
    public boolean paste(String s) {
        boolean changed = false;
        for (char c : s.toCharArray()) {
            changed |= type(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
        }
        return changed;
    }

    public void clear() {
        text.setLength(0);
        caret = 0;
    }

    /** Substitui o conteúdo para editar outro campo com o mesmo widget. */
    public void setText(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("texto inválido para o campo");
        }
        text.setLength(0);
        text.append(value);
        caret = text.length();
    }
}
