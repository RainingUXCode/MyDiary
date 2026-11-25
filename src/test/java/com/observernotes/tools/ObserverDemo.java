package com.observernotes.tools;

import com.observernotes.application.TaskService;
import com.observernotes.domain.AchievementLedger;
import com.observernotes.domain.TaskBoard;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskCreated;
import com.observernotes.domain.event.TaskDeleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.observer.Subscription;
import com.observernotes.observer.SynchronousEventBus;
import com.observernotes.observer.impl.AchievementObserver;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryObserver;
import java.time.Clock;

/**
 * Demonstração em console do padrão Observer, para a apresentação.
 * Execute: {@code java com.observernotes.tools.ObserverDemo}.
 */
public final class ObserverDemo {

    private ObserverDemo() { }

    public static void main(String[] args) {
        SynchronousEventBus<DomainEvent> bus = new SynchronousEventBus<>();
        HistoryObserver history = new HistoryObserver();
        CounterObserver counter = new CounterObserver();
        AchievementObserver achievements = new AchievementObserver();

        // Observador extra só para mostrar o fluxo de eventos.
        bus.subscribe(e -> System.out.println("      evento -> " + describe(e)));
        bus.subscribe(history);
        Subscription counterSub = bus.subscribe(counter);
        bus.subscribe(achievements);

        TaskService service = new TaskService(new TaskBoard(), new AchievementLedger(), bus, Clock.systemDefaultZone());

        step("1. Criar duas tarefas");
        TaskView a = service.createTask("Estudar o Observer");
        TaskView b = service.createTask("Desenhar o diagrama");
        show(counter, achievements, history);

        step("2. Concluir 'Estudar o Observer' (primeira conclusão: ponto + marco)");
        service.complete(a.id());
        show(counter, achievements, history);

        step("3. Desmarcar e marcar de novo (NÃO pontua outra vez)");
        service.uncomplete(a.id());
        service.complete(a.id());
        show(counter, achievements, history);

        step("4. Excluir a tarefa concluída (conquista permanece, contador desce)");
        service.delete(a.id());
        show(counter, achievements, history);

        step("5. Desligar o observador Contador (cancelar inscrição) e concluir outra tarefa");
        counterSub.cancel();
        service.complete(b.id());
        System.out.println("      Contador (desligado, congelado): " + counter.label());
        System.out.println("      Conquistas: total " + achievements.progress().totalScored());
    }

    private static void step(String title) {
        System.out.println("\n" + title);
    }

    private static void show(CounterObserver c, AchievementObserver a, HistoryObserver h) {
        System.out.println("      Contador: " + c.label()
                + " | Conquistas (histórico): " + a.progress().totalScored() + "/16"
                + " | Histórico: " + h.entries().size() + " entradas");
    }

    private static String describe(DomainEvent e) {
        if (e instanceof TaskCreated t) {
            return "TaskCreated(" + t.task().title() + ")";
        } else if (e instanceof TaskCompleted t) {
            return "TaskCompleted(" + t.task().title() + ", inédita=" + t.firstCompletion() + ")";
        } else if (e instanceof TaskUncompleted t) {
            return "TaskUncompleted(" + t.task().title() + ")";
        } else if (e instanceof TaskDeleted t) {
            return "TaskDeleted(" + t.task().title() + ")";
        } else if (e instanceof MilestoneUnlocked m) {
            return "MilestoneUnlocked(" + m.milestone() + ", total=" + m.totalScored() + ")";
        }
        return e.toString();
    }
}
