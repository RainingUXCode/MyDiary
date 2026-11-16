package com.observernotes.application;

import com.observernotes.domain.AchievementLedger;
import com.observernotes.domain.AchievementProgress;
import com.observernotes.domain.CompletionResult;
import com.observernotes.domain.DomainState;
import com.observernotes.domain.Milestone;
import com.observernotes.domain.TaskBoard;
import com.observernotes.domain.TaskId;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.observer.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Único ponto de entrada para mudar tarefas e conquistas (a interface não tem
 * outro caminho). Em cada comando:
 * <ol>
 *   <li>o domínio muda de estado e a pontuação é registrada no ledger;</li>
 *   <li>só depois os eventos são publicados.</li>
 * </ol>
 * Assim, nenhum observador, animação ou componente visual pode afetar o que foi
 * pontuado. Os métodos são sincronizados, então chamadas repetidas ou
 * concorrentes são serializadas e continuam idempotentes.
 */
public final class TaskService {

    private final TaskBoard board;
    private final AchievementLedger ledger;
    private final EventPublisher<DomainEvent> events;
    private final Clock clock;

    public TaskService(TaskBoard board, AchievementLedger ledger,
                       EventPublisher<DomainEvent> events, Clock clock) {
        this.board = Objects.requireNonNull(board, "board");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @throws com.observernotes.domain.BoardFullException se o limite de segurança for atingido */
    public synchronized TaskView createTask(String title) {
        TaskView created = board.add(title);
        events.publish(new TaskCreated(created, clock.instant()));
        return created;
    }

    /** @return {@code true} se algo mudou; {@code false} se já estava concluída ou não existe */
    public synchronized boolean complete(TaskId id) {
        Optional<CompletionResult> result = board.complete(id);
        if (result.isEmpty()) {
            return false;
        }
        CompletionResult completion = result.get();
        Instant now = clock.instant();
        // 1) Pontuação decidida e gravada no domínio, antes de qualquer notificação.
        Optional<Milestone> milestone = completion.firstCompletion()
                ? ledger.register(id) : Optional.empty();
        int total = ledger.totalScored();
        // 2) Notificações.
        events.publish(new TaskCompleted(completion.task(), completion.firstCompletion(), now));
        milestone.ifPresent(m -> events.publish(new MilestoneUnlocked(m, total, id, now)));
        return true;
    }

    /** @return {@code true} se algo mudou; {@code false} se já estava pendente ou não existe */
    public synchronized boolean uncomplete(TaskId id) {
        Optional<TaskView> view = board.uncomplete(id);
        view.ifPresent(v -> events.publish(new TaskUncompleted(v, clock.instant())));
        return view.isPresent();
    }

    /** Alterna entre concluída e pendente. @return {@code true} se a tarefa existe */
    public synchronized boolean toggle(TaskId id) {
        Optional<TaskView> current = board.find(id);
        if (current.isEmpty()) {
            return false;
        }
        return current.get().completed() ? uncomplete(id) : complete(id);
    }

    /** Exclui a tarefa. As conquistas já obtidas permanecem. @return {@code false} se não existe */
    public synchronized boolean delete(TaskId id) {
        Optional<TaskView> removed = board.remove(id);
        removed.ifPresent(v -> events.publish(new TaskDeleted(v, clock.instant())));
        return removed.isPresent();
    }

    /** Foto consistente de tudo que precisa ser salvo do domínio. */
    public synchronized DomainState domainState() {
        return new DomainState(board.tasks(), ledger.scoredTaskIds());
    }

    public synchronized List<TaskView> tasks() {
        return board.tasks();
    }

    /** Tarefas concluídas agora. Não é o total histórico de conquistas. */
    public synchronized int completedCount() {
        return board.completedCount();
    }

    /** Total histórico de conclusões inéditas. */
    public synchronized AchievementProgress achievements() {
        return ledger.progress();
    }
}
