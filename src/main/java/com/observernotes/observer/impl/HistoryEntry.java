package com.observernotes.observer.impl;

import com.observernotes.domain.TaskId;
import java.time.Instant;

/** Linha do histórico. Guarda o título como texto: a tarefa pode já ter sido excluída. */
public record HistoryEntry(Instant at, Kind kind, TaskId taskId, String taskTitle) {

    public enum Kind { CREATED, COMPLETED, DELETED }
}
