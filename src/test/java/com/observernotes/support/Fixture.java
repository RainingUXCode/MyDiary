package com.observernotes.support;

import com.observernotes.application.TaskService;
import com.observernotes.domain.AchievementLedger;
import com.observernotes.domain.TaskBoard;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.observer.Observer;
import com.observernotes.observer.SynchronousEventBus;
import com.observernotes.observer.impl.AchievementObserver;
import com.observernotes.observer.impl.ConstellationObserver;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryObserver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Monta o domínio completo com os quatro observadores já inscritos, para os testes. */
public final class Fixture {

    public final List<DomainEvent> events = new ArrayList<>();
    public final List<RuntimeException> failures = new ArrayList<>();

    public final SynchronousEventBus<DomainEvent> bus =
            new SynchronousEventBus<>((observer, event, failure) -> failures.add(failure));
    public final TaskBoard board = new TaskBoard();
    public final AchievementLedger ledger = new AchievementLedger();
    public final TaskService service = new TaskService(board, ledger, bus,
            Clock.fixed(Instant.parse("2026-11-13T21:40:00Z"), ZoneOffset.UTC));

    public final HistoryObserver history = new HistoryObserver();
    public final CounterObserver counter = new CounterObserver();
    public final ConstellationObserver constellation = new ConstellationObserver();
    public final AchievementObserver achievements = new AchievementObserver();

    public Fixture() {
        Observer<DomainEvent> recorder = events::add;
        bus.subscribe(recorder);
        bus.subscribe(history);
        bus.subscribe(counter);
        bus.subscribe(constellation);
        bus.subscribe(achievements);
    }

    public TaskView create(String title) {
        return service.createTask(title);
    }

    /** Cria, conclui e exclui uma tarefa: soma exatamente um ponto e libera a vaga. */
    public void scoreOneNewTask() {
        TaskView t = create("tarefa");
        service.complete(t.id());
        service.delete(t.id());
    }

    public <T extends DomainEvent> List<T> eventsOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (DomainEvent e : events) {
            if (type.isInstance(e)) {
                out.add(type.cast(e));
            }
        }
        return out;
    }
}
