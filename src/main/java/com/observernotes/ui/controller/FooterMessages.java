package com.observernotes.ui.controller;

import com.observernotes.domain.Milestone;
import com.observernotes.domain.RewardType;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.observer.Observer;
import com.observernotes.observer.impl.HistoryEntry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Mantém a última mensagem do rodapé. É um observador próprio da interface: continua
 * funcionando mesmo que o usuário desligue o observador de Histórico.
 */
public final class FooterMessages implements Observer<DomainEvent> {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId zone;
    private String last = "";

    public FooterMessages(ZoneId zone) {
        this.zone = zone;
    }

    public String last() {
        return last;
    }

    /** Mensagem inicial, a partir da entrada mais recente do histórico restaurado. */
    public void startFrom(Optional<HistoryEntry> latest) {
        latest.ifPresent(e -> last = time(e.at()) + " · " + switch (e.kind()) {
            case CREATED -> "Criada: ";
            case COMPLETED -> "✓ Concluída: ";
            case DELETED -> "Excluída: ";
        } + e.taskTitle());
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof TaskCreated e) {
            last = time(e.occurredAt()) + " · Criada: " + e.task().title();
        } else if (event instanceof TaskCompleted e) {
            last = time(e.occurredAt()) + " · ✓ Concluída: " + e.task().title();
        } else if (event instanceof TaskUncompleted e) {
            last = time(e.occurredAt()) + " · Desmarcada: " + e.task().title();
        } else if (event instanceof TaskDeleted e) {
            last = time(e.occurredAt()) + " · Excluída: " + e.task().title();
        } else if (event instanceof MilestoneUnlocked e) {
            last = time(e.occurredAt()) + " · Nova conquista: " + describe(e.milestone()) + " (" + e.totalScored()
                    + " de " + Milestone.COUNT + ")";
        }
    }

    public String time(Instant at) {
        return HOUR.format(at.atZone(zone));
    }

    static String describe(Milestone m) {
        RewardType r = m.reward();
        return switch (r) {
            case STAR -> "estrela";
            case BRONZE_MEDAL -> "medalha de bronze";
            case SILVER_MEDAL -> "medalha de prata";
            case GOLD_MEDAL -> "medalha de ouro";
            case MAX_ACHIEVEMENT -> "conquista máxima";
        };
    }
}
