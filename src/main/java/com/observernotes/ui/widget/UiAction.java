package com.observernotes.ui.widget;

import java.util.Objects;

/**
 * Pedido do usuário vindo da interface. {@code key} só é usado por ações sobre um item
 * (tarefa ou observador) e é a chave do modelo, nunca um índice de linha.
 */
public record UiAction(Type type, String key) {

    public enum Type {
        NAV_LIST, NAV_ALERTS, NAV_RECORD, ADD, CLEAR, TASK_PAGE_PREVIOUS, TASK_PAGE_NEXT,
        TOGGLE_TASK, DELETE_TASK, TOGGLE_OBSERVER, SUBMIT,
        /** Só identifica o campo de texto na ordem de foco; não é um comando. */
        FOCUS_FIELD,
        /** Dica ao usuário (a chave é o texto), por exemplo "pressione de novo para excluir". */
        HINT,
        /** Rola o histórico; a chave é o deslocamento em linhas, "home" ou "end". */
        SCROLL_HISTORY,
        /** Player musical. A chave é o caminho da faixa (PLAY/REMOVE/UP/DOWN), "+"/"-"/"set:0.55" (VOLUME) ou o deslocamento (SCROLL). */
        PLAYER_PREVIOUS, PLAYER_TOGGLE, PLAYER_NEXT, PLAYER_ADD, PLAYER_PANEL, PLAYER_PLAY, PLAYER_REMOVE,
        PLAYER_UP, PLAYER_DOWN, PLAYER_REPEAT, PLAYER_VOLUME, PLAYER_SCROLL;

        public boolean isPlayer() {
            return name().startsWith("PLAYER_");
        }
    }

    public UiAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
    }

    public static UiAction of(Type type) {
        return new UiAction(type, "");
    }

    /** Itens com hover e pressionado próprios (checkbox, X, interruptor). */
    public boolean isItem() {
        return type == Type.TOGGLE_TASK || type == Type.DELETE_TASK || type == Type.TOGGLE_OBSERVER
                || type == Type.PLAYER_PLAY || type == Type.PLAYER_REMOVE || type == Type.PLAYER_UP
                || type == Type.PLAYER_DOWN || type == Type.PLAYER_REPEAT
                || (type == Type.PLAYER_VOLUME && (key.equals("+") || key.equals("-")));
    }

    public static UiAction of(ButtonId id) {
        return of(switch (id) {
            case NAV_LIST -> Type.NAV_LIST;
            case NAV_ALERTS -> Type.NAV_ALERTS;
            case NAV_RECORD -> Type.NAV_RECORD;
            case ADD -> Type.ADD;
            case CLEAR -> Type.CLEAR;
            case TASK_PAGE_PREVIOUS -> Type.TASK_PAGE_PREVIOUS;
            case TASK_PAGE_NEXT -> Type.TASK_PAGE_NEXT;
            case PLAYER_PREVIOUS -> Type.PLAYER_PREVIOUS;
            case PLAYER_TOGGLE -> Type.PLAYER_TOGGLE;
            case PLAYER_NEXT -> Type.PLAYER_NEXT;
            case NEW_MUSIC -> Type.PLAYER_ADD;
            case PLAYER_PANEL -> Type.PLAYER_PANEL;
        });
    }
}
