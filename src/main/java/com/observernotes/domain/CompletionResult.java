package com.observernotes.domain;

/** Resultado de concluir uma tarefa; {@code firstCompletion} é a única fonte de "conclusão inédita". */
public record CompletionResult(TaskView task, boolean firstCompletion) { }
