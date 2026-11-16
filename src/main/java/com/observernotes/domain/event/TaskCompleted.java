package com.observernotes.domain.event;

import com.observernotes.domain.TaskView;
import java.time.Instant;

/** @param firstCompletion {@code true} só na primeira conclusão da tarefa (conclusão inédita) */
public record TaskCompleted(TaskView task, boolean firstCompletion, Instant occurredAt) implements DomainEvent { }
