package com.observernotes.application;

import static org.junit.jupiter.api.Assertions.*;

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
import com.observernotes.support.Fixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture();
    }

    // ---- criação e limite ----------------------------------------------------

    @Test
    @DisplayName("criar tarefa publica TaskCreated")
    void createPublishes() {
        TaskView t = f.create("Estudar o Observer");
        assertEquals(1, f.events.size());
        TaskCreated e = (TaskCreated) f.events.get(0);
        assertEquals(t, e.task());
    }

    @Test
    @DisplayName("a sétima tarefa é criada para a segunda página")
    void seventhTaskIsAllowed() {
        for (int i = 0; i < 7; i++) {
            f.create("t" + i);
        }
        assertEquals(7, f.events.size());
        assertEquals(7, f.service.tasks().size());
    }

    // ---- conclusão, desmarcação e pontuação ----------------------------------

    @Test
    @DisplayName("primeira conclusão publica TaskCompleted e depois MilestoneUnlocked(STAR_1)")
    void firstCompletionEvents() {
        TaskView t = f.create("a");
        assertTrue(f.service.complete(t.id()));
        assertEquals(3, f.events.size());
        TaskCompleted done = (TaskCompleted) f.events.get(1);
        MilestoneUnlocked unlocked = (MilestoneUnlocked) f.events.get(2);
        assertTrue(done.firstCompletion());
        assertEquals(Milestone.STAR_1, unlocked.milestone());
        assertEquals(1, unlocked.totalScored());
        assertEquals(t.id(), unlocked.cause());
    }

    @Test
    @DisplayName("desmarcar e marcar de novo nunca pontua outra vez")
    void recompletionDoesNotScore() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        for (int i = 0; i < 5; i++) {
            assertTrue(f.service.uncomplete(t.id()));
            assertTrue(f.service.complete(t.id()));
        }
        assertEquals(1, f.service.achievements().totalScored());
        assertEquals(1, f.eventsOf(MilestoneUnlocked.class).size());
        List<TaskCompleted> completions = f.eventsOf(TaskCompleted.class);
        assertEquals(6, completions.size());
        assertTrue(completions.get(0).firstCompletion());
        for (TaskCompleted c : completions.subList(1, completions.size())) {
            assertFalse(c.firstCompletion());
        }
    }

    @Test
    @DisplayName("cliques rápidos / chamadas repetidas de concluir pontuam uma vez só")
    void rapidRepeatedCompleteCalls() {
        TaskView t = f.create("a");
        int changes = 0;
        for (int i = 0; i < 50; i++) {
            if (f.service.complete(t.id())) {
                changes++;
            }
        }
        assertEquals(1, changes);
        assertEquals(1, f.eventsOf(TaskCompleted.class).size());
        assertEquals(1, f.eventsOf(MilestoneUnlocked.class).size());
        assertEquals(1, f.service.achievements().totalScored());
    }

    @Test
    @DisplayName("alternar muitas vezes seguidas só pontua na primeira marcação")
    void rapidToggling() {
        TaskView t = f.create("a");
        for (int i = 0; i < 101; i++) {
            assertTrue(f.service.toggle(t.id()));
        }
        assertEquals(1, f.service.achievements().totalScored());
        assertTrue(f.service.tasks().get(0).completed());
    }

    @Test
    @DisplayName("chamadas concorrentes de várias threads pontuam uma só vez")
    void concurrentCalls() throws Exception {
        TaskView t = f.create("a");
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int k = 0; k < 200; k++) {
                    f.service.complete(t.id());
                }
            });
            threads[i].start();
        }
        for (Thread th : threads) {
            th.join();
        }
        assertEquals(1, f.service.achievements().totalScored());
        assertEquals(1, f.eventsOf(MilestoneUnlocked.class).size());
    }

    @Test
    @DisplayName("desmarcar publica TaskUncompleted; repetir não publica de novo")
    void uncompleteEvents() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        assertTrue(f.service.uncomplete(t.id()));
        assertFalse(f.service.uncomplete(t.id()));
        assertEquals(1, f.eventsOf(TaskUncompleted.class).size());
    }

    // ---- exclusão -------------------------------------------------------------

    @Test
    @DisplayName("excluir tarefa não apaga as conquistas")
    void deleteKeepsAchievements() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        assertTrue(f.service.delete(t.id()));
        assertEquals(1, f.service.achievements().totalScored());
        assertEquals(1, f.achievements.progress().totalScored());
        assertTrue(f.ledger.hasScored(t.id()));
        TaskDeleted e = f.eventsOf(TaskDeleted.class).get(0);
        assertTrue(e.task().completed());
    }

    @Test
    @DisplayName("tarefa excluída não pode ser concluída nem excluída de novo")
    void deletedTaskIsGone() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        f.service.delete(t.id());
        int events = f.events.size();
        assertFalse(f.service.complete(t.id()));
        assertFalse(f.service.toggle(t.id()));
        assertFalse(f.service.delete(t.id()));
        assertEquals(events, f.events.size());
        assertEquals(1, f.service.achievements().totalScored());
    }

    @Test
    @DisplayName("excluir e criar tarefas gera identificadores novos; só a primeira conclusão de cada uma pontua")
    void deleteAndCreateUsesNewIds() {
        TaskView old = f.create("a");
        f.service.complete(old.id());
        f.service.delete(old.id());
        TaskView fresh = f.create("a");
        assertNotEquals(old.id(), fresh.id());
        assertEquals(1, f.service.achievements().totalScored());
        f.service.complete(fresh.id());
        f.service.uncomplete(fresh.id());
        f.service.complete(fresh.id());
        assertEquals(2, f.service.achievements().totalScored());
    }

    // ---- sequência completa e após o máximo ----------------------------------

    @Test
    @DisplayName("os 16 marcos acontecem exatamente na ordem, um por conclusão inédita")
    void sixteenMilestonesInOrder() {
        for (int i = 1; i <= 16; i++) {
            f.scoreOneNewTask();
            assertEquals(i, f.service.achievements().totalScored());
            assertEquals(i, f.eventsOf(MilestoneUnlocked.class).size());
        }
        List<Milestone> got = new ArrayList<>();
        for (MilestoneUnlocked e : f.eventsOf(MilestoneUnlocked.class)) {
            got.add(e.milestone());
        }
        assertEquals(List.of(Milestone.values()), got);
        assertTrue(f.service.achievements().isMax());
    }

    @Test
    @DisplayName("depois da conquista máxima não há novas recompensas")
    void nothingAfterMax() {
        for (int i = 0; i < 16; i++) {
            f.scoreOneNewTask();
        }
        int unlocked = f.eventsOf(MilestoneUnlocked.class).size();
        for (int i = 0; i < 5; i++) {
            f.scoreOneNewTask();
        }
        TaskView t = f.create("a");
        f.service.complete(t.id());
        f.service.uncomplete(t.id());
        f.service.complete(t.id());
        assertEquals(unlocked, f.eventsOf(MilestoneUnlocked.class).size());
        assertEquals(16, f.service.achievements().totalScored());
        assertEquals(16, f.achievements.progress().totalScored());
        assertEquals(Milestone.MAX_ACHIEVEMENT, f.achievements.lastUnlocked().orElseThrow());
    }

    // ---- total histórico x concluídas agora -----------------------------------

    @Test
    @DisplayName("tarefas concluídas agora e total histórico são grandezas separadas")
    void currentAndHistoricTotalsAreSeparate() {
        TaskView a = f.create("a");
        TaskView b = f.create("b");
        TaskView c = f.create("c");
        f.service.complete(a.id());
        f.service.complete(b.id());
        f.service.complete(c.id());
        assertEquals(3, f.service.completedCount());
        assertEquals(3, f.service.achievements().totalScored());

        f.service.uncomplete(a.id());
        f.service.uncomplete(b.id());
        assertEquals(1, f.service.completedCount());
        assertEquals(3, f.service.achievements().totalScored());

        f.service.delete(c.id());
        assertEquals(0, f.service.completedCount());
        assertEquals(3, f.service.achievements().totalScored());
        assertEquals("0 de 2", f.counter.label());
        assertEquals(3, f.achievements.progress().totalScored());
    }

    // ---- consistência com observador defeituoso -------------------------------

    @Test
    @DisplayName("observador que falha em tudo não impede pontuação nem os outros observadores")
    void failingObserverDoesNotBreakState() {
        Fixture g = new Fixture();
        // inscrito DEPOIS dos três, mas também testamos o caso "antes" em outro teste
        g.bus.subscribe(e -> { throw new IllegalStateException("animação quebrou"); });
        TaskView t = g.create("a");
        assertDoesNotThrow(() -> g.service.complete(t.id()));
        assertEquals(1, g.service.achievements().totalScored());
        assertEquals(1, g.achievements.progress().totalScored());
        assertEquals("1 de 1", g.counter.label());
        assertFalse(g.failures.isEmpty());
    }

    @Test
    @DisplayName("observador defeituoso inscrito primeiro também não bloqueia os demais")
    void failingObserverFirstInLine() {
        TaskBoard board = new TaskBoard();
        com.observernotes.domain.AchievementLedger ledger = new com.observernotes.domain.AchievementLedger();
        List<RuntimeException> failures = new ArrayList<>();
        com.observernotes.observer.SynchronousEventBus<DomainEvent> bus =
                new com.observernotes.observer.SynchronousEventBus<>((o, e, x) -> failures.add(x));
        bus.subscribe(e -> { throw new IllegalStateException("primeiro da fila"); });
        var counter = new com.observernotes.observer.impl.CounterObserver();
        var achievements = new com.observernotes.observer.impl.AchievementObserver();
        bus.subscribe(counter);
        bus.subscribe(achievements);
        TaskService service = new TaskService(board, ledger, bus, java.time.Clock.systemUTC());

        TaskView t = service.createTask("a");
        service.complete(t.id());
        assertEquals("1 de 1", counter.label());
        assertEquals(1, achievements.progress().totalScored());
        assertEquals(1, ledger.totalScored());
        assertEquals(3, failures.size());
    }

    @Test
    @DisplayName("falha ao receber o marco não perde o ponto: o ledger já tinha registrado")
    void failureOnMilestoneKeepsScore() {
        Fixture g = new Fixture();
        g.bus.subscribe(e -> {
            if (e instanceof MilestoneUnlocked) {
                throw new IllegalStateException("animação do marco falhou");
            }
        });
        for (int i = 0; i < 4; i++) {
            g.scoreOneNewTask();
        }
        assertEquals(4, g.service.achievements().totalScored());
        assertEquals(4, g.failures.size());
        assertEquals(4, g.eventsOf(MilestoneUnlocked.class).size());
    }

    @Test
    @DisplayName("ids de tarefas excluídas e criadas continuam distintos ao longo de muitas trocas")
    void manyCyclesKeepIdsDistinct() {
        List<TaskId> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            TaskView t = f.create("t");
            ids.add(t.id());
            f.service.delete(t.id());
        }
        assertEquals(40, ids.stream().distinct().count());
    }
}
