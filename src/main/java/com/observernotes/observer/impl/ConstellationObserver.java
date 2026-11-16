package com.observernotes.observer.impl;

import com.observernotes.domain.TaskId;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.observer.Observer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uma estrela por tarefa, na ordem de criação; acesa quando a tarefa está concluída. */
public final class ConstellationObserver implements Observer<DomainEvent> {

    private final Map<TaskId, Boolean> stars = new LinkedHashMap<>();

    public ConstellationObserver() { }

    public ConstellationObserver(Collection<TaskView> initial) {
        resync(initial);
    }

    /** Refaz a projeção a partir do estado atual (restauração ou religar). */
    public void resync(Collection<TaskView> tasks) {
        stars.clear();
        tasks.forEach(t -> stars.put(t.id(), t.completed()));
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof TaskCreated e) {
            stars.put(e.task().id(), e.task().completed());
        } else if (event instanceof TaskCompleted e) {
            stars.put(e.task().id(), true);
        } else if (event instanceof TaskUncompleted e) {
            stars.put(e.task().id(), false);
        } else if (event instanceof TaskDeleted e) {
            stars.remove(e.task().id());
        }
    }

    public List<ConstellationStar> stars() {
        List<ConstellationStar> out = new ArrayList<>(stars.size());
        stars.forEach((id, lit) -> out.add(new ConstellationStar(id, lit)));
        return List.copyOf(out);
    }

    public int litCount() {
        return (int) stars.values().stream().filter(Boolean::booleanValue).count();
    }

    public int starCount() {
        return stars.size();
    }
}
