package com.observernotes.domain.event;

import com.observernotes.domain.TaskView;
import java.time.Instant;

public record TaskUncompleted(TaskView task, Instant occurredAt) implements DomainEvent { }
