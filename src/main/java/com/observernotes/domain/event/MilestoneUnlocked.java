package com.observernotes.domain.event;

import com.observernotes.domain.Milestone;
import com.observernotes.domain.TaskId;
import java.time.Instant;

/**
 * Um marco foi conquistado. Já está registrado no ledger quando este evento
 * é publicado: ele apenas comunica o fato.
 */
public record MilestoneUnlocked(Milestone milestone, int totalScored, TaskId cause, Instant occurredAt)
        implements DomainEvent { }
