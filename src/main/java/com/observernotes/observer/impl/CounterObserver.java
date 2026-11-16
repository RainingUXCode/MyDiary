package com.observernotes.observer.impl;

import com.observernotes.domain.TaskId;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.observer.Observer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Acompanha o progresso <em>atual</em>: quantas tarefas existem e quantas estão
 * concluídas agora ("2 de 4"). Desmarcar ou excluir reduz o valor. Não tem relação
 * com o total histórico de conquistas, que pertence ao {@link AchievementObserver}.
 */
public final class CounterObserver implements Observer<DomainEvent> {

    private final Set<TaskId> existing = new HashSet<>();
    private final Set<TaskId> completed = new HashSet<>();

    public CounterObserver() { }

    /** Inicia a partir de tarefas já existentes (por exemplo, ao religar o observador). */
    public CounterObserver(Collection<TaskView> initial) {
        resync(initial);
    }

    /** Refaz a projeção a partir do estado atual (restauração ou religar). */
    public void resync(Collection<TaskView> tasks) {
        existing.clear();
        completed.clear();
        tasks.forEach(this::track);
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof TaskCreated e) {
            existing.add(e.task().id());
        } else if (event instanceof TaskCompleted e) {
            existing.add(e.task().id());
            completed.add(e.task().id());
        } else if (event instanceof TaskUncompleted e) {
            completed.remove(e.task().id());
        } else if (event instanceof TaskDeleted e) {
            existing.remove(e.task().id());
            completed.remove(e.task().id());
        }
    }

    public int completedCount() {
        return completed.size();
    }

    public int taskCount() {
        return existing.size();
    }

    /** Texto do mockup, por exemplo "2 de 4". */
    public String label() {
        return completed.size() + " de " + existing.size();
    }

    private void track(TaskView t) {
        existing.add(t.id());
        if (t.completed()) {
            completed.add(t.id());
        }
    }
}
