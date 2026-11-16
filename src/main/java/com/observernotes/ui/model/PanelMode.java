package com.observernotes.ui.model;

/** Qual conteúdo o painel central mostra. */
public enum PanelMode {
    /** Lista de tarefas (botão LIST). */
    TASKS("Tarefas"),
    /** Observadores com seus interruptores (botão ALERTS). */
    OBSERVERS("Observadores"),
    /** Histórico de eventos (botão RECORD). */
    HISTORY("Histórico");

    private final String title;

    PanelMode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
