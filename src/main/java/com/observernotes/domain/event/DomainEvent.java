package com.observernotes.domain.event;

import java.time.Instant;

/** Evento imutável do domínio. A hierarquia é fechada: observadores conhecem todos os casos. */
public sealed interface DomainEvent
        permits TaskCreated, TaskCompleted, TaskUncompleted, TaskDeleted, MilestoneUnlocked {

    Instant occurredAt();
}
