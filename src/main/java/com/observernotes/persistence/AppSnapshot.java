package com.observernotes.persistence;

import com.observernotes.domain.DomainState;
import com.observernotes.observer.impl.HistoryEntry;
import java.util.List;

/** Tudo que é salvo em {@code state.json}: domínio, histórico e a página de tarefas selecionada. */
public record AppSnapshot(DomainState domain, List<HistoryEntry> history, int taskPage) {

    public AppSnapshot {
        history = List.copyOf(history);
        if (taskPage < 0) {
            throw new IllegalArgumentException("página de tarefas negativa");
        }
    }

    /** Compatibilidade com chamadas que não precisam escolher uma página. */
    public AppSnapshot(DomainState domain, List<HistoryEntry> history) {
        this(domain, history, 0);
    }

    public static AppSnapshot empty() {
        return new AppSnapshot(DomainState.empty(), List.of(), 0);
    }
}
