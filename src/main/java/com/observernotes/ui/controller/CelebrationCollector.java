package com.observernotes.ui.controller;

import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.observer.Observer;
import java.util.ArrayList;
import java.util.List;

/**
 * Observador da interface que anota os marcos <em>realmente</em> desbloqueados (eventos
 * publicados pelo domínio). Como restaurar o estado não publica eventos, uma comemoração
 * nunca se repete ao reiniciar o app.
 */
public final class CelebrationCollector implements Observer<DomainEvent> {

    private final List<MilestoneUnlocked> pending = new ArrayList<>();

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof MilestoneUnlocked m) {
            pending.add(m);
        }
    }

    /** Entrega e esquece o que foi anotado desde a última chamada. */
    public List<MilestoneUnlocked> drain() {
        List<MilestoneUnlocked> out = List.copyOf(pending);
        pending.clear();
        return out;
    }
}
