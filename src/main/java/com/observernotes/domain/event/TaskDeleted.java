package com.observernotes.domain.event;

import com.observernotes.domain.TaskView;
import java.time.Instant;

/** {@code task} é a foto da tarefa no momento da exclusão (inclui se estava concluída). */
public record TaskDeleted(TaskView task, Instant occurredAt) implements DomainEvent { }
