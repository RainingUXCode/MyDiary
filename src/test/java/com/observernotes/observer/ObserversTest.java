package com.observernotes.observer;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.domain.AchievementProgress;
import com.observernotes.domain.Milestone;
import com.observernotes.domain.RewardType;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.observer.impl.ConstellationObserver;
import com.observernotes.observer.impl.ConstellationStar;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryEntry;
import com.observernotes.observer.impl.HistoryObserver;
import com.observernotes.support.Fixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObserversTest {

    private Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture();
    }

    // ---- Histórico ---------------------------------------------------------------

    @Test
    @DisplayName("Histórico registra criação, conclusão e exclusão, mais recente primeiro")
    void historyRecords() {
        TaskView t = f.create("Desenhar o diagrama");
        f.service.complete(t.id());
        f.service.delete(t.id());
        List<HistoryEntry> h = f.history.entries();
        assertEquals(3, h.size());
        assertEquals(HistoryEntry.Kind.DELETED, h.get(0).kind());
        assertEquals(HistoryEntry.Kind.COMPLETED, h.get(1).kind());
        assertEquals(HistoryEntry.Kind.CREATED, h.get(2).kind());
        assertEquals("Desenhar o diagrama", h.get(0).taskTitle());
        assertEquals(t.id(), h.get(2).taskId());
    }

    @Test
    @DisplayName("Histórico ignora desmarcar e marcos")
    void historyIgnoresOtherEvents() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        int before = f.history.entries().size();
        f.service.uncomplete(t.id());
        assertEquals(before, f.history.entries().size());
        assertEquals(2, before); // criada + concluída; o marco não entra
    }

    @Test
    @DisplayName("Histórico guarda no máximo 200 entradas e pode ser limpo")
    void historyIsBoundedAndClearable() {
        for (int i = 0; i < 101; i++) {
            TaskView t = f.create("t");
            f.service.delete(t.id());
        }
        assertEquals(HistoryObserver.MAX_ENTRIES, f.history.entries().size());
        f.history.clear();
        assertTrue(f.history.entries().isEmpty());
    }

    // ---- Contador ----------------------------------------------------------------

    @Test
    @DisplayName("Contador mostra o progresso atual, como '2 de 4'")
    void counterLabel() {
        TaskView a = f.create("a");
        TaskView b = f.create("b");
        f.create("c");
        f.create("d");
        f.service.complete(a.id());
        f.service.complete(b.id());
        assertEquals("2 de 4", f.counter.label());
        assertEquals(2, f.counter.completedCount());
        assertEquals(4, f.counter.taskCount());
    }

    @Test
    @DisplayName("Contador diminui ao desmarcar e ao excluir tarefa concluída")
    void counterGoesDown() {
        TaskView a = f.create("a");
        TaskView b = f.create("b");
        f.service.complete(a.id());
        f.service.complete(b.id());
        f.service.uncomplete(a.id());
        assertEquals("1 de 2", f.counter.label());
        f.service.delete(b.id());
        assertEquals("0 de 1", f.counter.label());
    }

    @Test
    @DisplayName("Contador não conta conquistas: eventos de marco não o alteram")
    void counterIgnoresMilestones() {
        for (int i = 0; i < 5; i++) {
            f.scoreOneNewTask();
        }
        assertEquals("0 de 0", f.counter.label());
        assertEquals(5, f.achievements.progress().totalScored());
    }

    // ---- Constelação -------------------------------------------------------------

    @Test
    @DisplayName("Constelação tem uma estrela por tarefa, acesa quando concluída")
    void constellationStars() {
        TaskView a = f.create("a");
        TaskView b = f.create("b");
        f.service.complete(b.id());
        List<ConstellationStar> stars = f.constellation.stars();
        assertEquals(2, stars.size());
        assertEquals(a.id(), stars.get(0).taskId());
        assertFalse(stars.get(0).lit());
        assertTrue(stars.get(1).lit());
        f.service.uncomplete(b.id());
        assertEquals(0, f.constellation.litCount());
        f.service.delete(a.id());
        assertEquals(1, f.constellation.starCount());
    }

    // ---- Conquistas --------------------------------------------------------------

    @Test
    @DisplayName("Conquistas acompanha o total histórico e o marco mais recente")
    void achievementsTrackTotal() {
        assertEquals(0, f.achievements.progress().totalScored());
        assertTrue(f.achievements.lastUnlocked().isEmpty());
        for (int i = 0; i < 4; i++) {
            f.scoreOneNewTask();
        }
        AchievementProgress p = f.achievements.progress();
        assertEquals(4, p.totalScored());
        assertEquals(Milestone.BRONZE_MEDAL, f.achievements.lastUnlocked().orElseThrow());
        assertEquals(RewardType.BRONZE_MEDAL, p.highestMedal().orElseThrow());
    }

    @Test
    @DisplayName("Conquistas não muda ao desmarcar, remarcar ou excluir")
    void achievementsUnaffectedByTaskChurn() {
        TaskView t = f.create("a");
        f.service.complete(t.id());
        f.service.uncomplete(t.id());
        f.service.complete(t.id());
        f.service.delete(t.id());
        assertEquals(1, f.achievements.progress().totalScored());
    }

    // ---- Independência e inscrição -----------------------------------------------

    @Test
    @DisplayName("cada observador funciona sozinho: não há dependência entre eles")
    void observersAreIndependent() {
        SynchronousEventBus<DomainEvent> bus = new SynchronousEventBus<>();
        CounterObserver onlyCounter = new CounterObserver();
        bus.subscribe(onlyCounter);
        var service = new com.observernotes.application.TaskService(
                new com.observernotes.domain.TaskBoard(),
                new com.observernotes.domain.AchievementLedger(), bus, java.time.Clock.systemUTC());
        TaskView t = service.createTask("a");
        service.complete(t.id());
        assertEquals("1 de 1", onlyCounter.label());
    }

    @Test
    @DisplayName("cancelar a inscrição de um observador congela só ele")
    void unsubscribedObserverStopsReacting() {
        HistoryObserver quiet = new HistoryObserver();
        Subscription s = f.bus.subscribe(quiet);
        f.create("a");
        s.cancel();
        f.create("b");
        assertEquals(1, quiet.entries().size());
        assertEquals(2, f.history.entries().size());
        assertEquals(2, f.counter.taskCount());
    }

    @Test
    @DisplayName("observador religado ressincroniza a partir do estado atual")
    void resyncFromSnapshot() {
        TaskView a = f.create("a");
        f.create("b");
        f.service.complete(a.id());
        CounterObserver late = new CounterObserver(f.service.tasks());
        ConstellationObserver lateStars = new ConstellationObserver(f.service.tasks());
        assertEquals("1 de 2", late.label());
        assertEquals(1, lateStars.litCount());
        assertEquals(2, lateStars.starCount());
    }

    @Test
    @DisplayName("cenário do mockup: 4 tarefas, 2 concluídas")
    void mockupScenario() {
        TaskView a = f.create("Desenhar o diagrama");
        TaskView b = f.create("Estudar o Observer");
        f.create("Ensaiar a apresentação");
        f.create("Escrever o README");
        f.service.complete(b.id());
        f.service.complete(a.id());
        assertEquals("2 de 4", f.counter.label());
        assertEquals(2, f.constellation.litCount());
        assertEquals(2, f.achievements.progress().starsEarned());
        assertEquals(2, f.achievements.progress().starsInCurrentTier());
        assertEquals(HistoryEntry.Kind.COMPLETED, f.history.entries().get(0).kind());
        assertEquals("Desenhar o diagrama", f.history.entries().get(0).taskTitle());
    }
}
