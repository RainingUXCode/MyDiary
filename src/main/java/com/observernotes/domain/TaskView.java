package com.observernotes.domain;

/**
 * Foto imutável de uma tarefa em um instante. É o que viaja nos eventos, para
 * que observadores nunca tenham acesso à entidade mutável.
 *
 * @param scored {@code true} se a primeira conclusão desta tarefa já foi consumida
 */
public record TaskView(TaskId id, String title, boolean completed, boolean scored) { }
