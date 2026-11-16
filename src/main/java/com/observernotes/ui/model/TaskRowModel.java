package com.observernotes.ui.model;

import java.util.Objects;

/**
 * O que a interface precisa saber para desenhar uma linha de tarefa.
 *
 * @param id chave opaca da tarefa (o clique devolve esta chave, nunca a posição da linha,
 *           então a ação sempre atinge a tarefa certa mesmo que a lista mude)
 */
public record TaskRowModel(String id, String title, boolean done) {
    public TaskRowModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
    }

    /** Atalho para telas de exemplo e testes visuais: a chave é o próprio título. */
    public TaskRowModel(String title, boolean done) {
        this(title, title, done);
    }
}
