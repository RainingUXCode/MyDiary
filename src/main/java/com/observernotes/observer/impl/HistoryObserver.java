package com.observernotes.observer.impl;

import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.observer.Observer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/** Registra criação, conclusão e exclusão. Os demais eventos não lhe dizem respeito. */
public final class HistoryObserver implements Observer<DomainEvent> {

    public static final int MAX_ENTRIES = 200;

    private final Deque<HistoryEntry> entries = new ArrayDeque<>();

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof TaskCreated e) {
            add(HistoryEntry.Kind.CREATED, e.occurredAt(), e.task());
        } else if (event instanceof TaskCompleted e) {
            add(HistoryEntry.Kind.COMPLETED, e.occurredAt(), e.task());
        } else if (event instanceof TaskDeleted e) {
            add(HistoryEntry.Kind.DELETED, e.occurredAt(), e.task());
        }
    }

    /** Substitui o conteúdo por entradas restauradas (mais recente primeiro). Não gera eventos. */
    public void load(Collection<HistoryEntry> restored) {
        entries.clear();
        for (HistoryEntry e : restored) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            entries.addLast(e);
        }
    }

    /** Mais recente primeiro. */
    public List<HistoryEntry> entries() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }

    private void add(HistoryEntry.Kind kind, Instant at, TaskView task) {
        entries.addFirst(new HistoryEntry(at, kind, task.id(), task.title()));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }
}
