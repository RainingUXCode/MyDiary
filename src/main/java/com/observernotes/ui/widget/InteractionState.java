package com.observernotes.ui.widget;

/** Estado visual de interação: qual botão está sob o mouse e qual está pressionado. */
public final class InteractionState {

    private ButtonId hover;
    private ButtonId pressed;

    /** @return true se algo mudou (precisa repintar) */
    public boolean setHover(ButtonId id) {
        if (id == hover) {
            return false;
        }
        hover = id;
        return true;
    }

    public boolean press(ButtonId id) {
        if (id == pressed) {
            return false;
        }
        pressed = id;
        return true;
    }

    /** Solta o botão; devolve o que estava pressionado (ou null). */
    public ButtonId release() {
        ButtonId was = pressed;
        pressed = null;
        return was;
    }

    public ButtonId hover() {
        return hover;
    }

    public ButtonId pressed() {
        return pressed;
    }

    /** Pressionado só conta enquanto o mouse continua sobre o mesmo botão. */
    public boolean isPressed(ButtonId id) {
        return pressed == id && hover == id;
    }

    public boolean isHover(ButtonId id) {
        return hover == id && pressed == null;
    }

    // ---- itens (checkbox, X, interruptor): identificados por tipo + chave ----

    private UiAction hoverItem;
    private UiAction pressedItem;

    public boolean setHoverItem(UiAction item) {
        if (java.util.Objects.equals(item, hoverItem)) {
            return false;
        }
        hoverItem = item;
        return true;
    }

    public boolean pressItem(UiAction item) {
        if (java.util.Objects.equals(item, pressedItem)) {
            return false;
        }
        pressedItem = item;
        return true;
    }

    /** Solta o item; devolve o que estava pressionado (ou null). */
    public UiAction releaseItem() {
        UiAction was = pressedItem;
        pressedItem = null;
        return was;
    }

    public UiAction pressedItem() {
        return pressedItem;
    }

    public boolean isHoverItem(UiAction item) {
        return item.equals(hoverItem) && pressedItem == null;
    }

    public boolean isPressedItem(UiAction item) {
        return item.equals(pressedItem) && item.equals(hoverItem);
    }
}
