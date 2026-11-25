package com.observernotes.application;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.domain.AchievementLedger;
import com.observernotes.domain.Milestone;
import com.observernotes.domain.TaskBoard;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.observer.SynchronousEventBus;
import com.observernotes.observer.impl.AchievementObserver;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryObserver;
import com.observernotes.settings.ObserverKind;
import com.observernotes.settings.ObserverPreferences;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObserverRegistryTest {

    private final List<DomainEvent> events = new ArrayList<>();
    private final List<ObserverPreferences> changes = new ArrayList<>();
    private SynchronousEventBus<DomainEvent> bus;
    private TaskService service;
    private HistoryObserver history;
    private CounterObserver counter;
    private AchievementObserver achievements;

    @BeforeEach
    void setUp() {
        bus = new SynchronousEventBus<>((o, e, f) -> { throw new AssertionError("falha inesperada", f); });
        service = new TaskService(new TaskBoard(), new AchievementLedger(), bus, Clock.systemUTC());
        bus.subscribe(events::add);
        history = new HistoryObserver();
        counter = new CounterObserver();
        achievements = new AchievementObserver();
    }

    private ObserverRegistry registry(ObserverPreferences initial) {
        return new ObserverRegistry(bus, service, history, counter, achievements,
                initial, changes::add);
    }

    @Test
    @DisplayName("preferências iniciais são respeitadas: observador desligado não é inscrito")
    void initialPreferencesAreRespected() {
        registry(ObserverPreferences.allEnabled().with(ObserverKind.COUNTER, false));
        service.createTask("a");
        assertEquals(0, counter.taskCount());
        assertEquals(1, history.entries().size());
    }

    @Test
    @DisplayName("desligar congela o observador; religar refaz a projeção a partir do domínio")
    void disableFreezesAndEnableResyncs() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        TaskView a = service.createTask("a");
        r.setEnabled(ObserverKind.COUNTER, false);
        service.createTask("b");
        service.complete(a.id());
        assertEquals("0 de 1", counter.label());          // congelado
        assertEquals(1, achievements.progress().totalScored()); // os outros seguem atualizando

        r.setEnabled(ObserverKind.COUNTER, true);
        assertEquals("1 de 2", counter.label());          // ressincronizado
        service.createTask("c");
        assertEquals("1 de 3", counter.label());          // e voltou a reagir
    }

    @Test
    @DisplayName("ligar ou desligar observadores nunca altera as conquistas")
    void togglingNeverChangesAchievements() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        for (int i = 0; i < 3; i++) {
            TaskView t = service.createTask("t" + i);
            service.complete(t.id());
        }
        assertEquals(3, service.achievements().totalScored());
        for (int round = 0; round < 3; round++) {
            for (ObserverKind k : ObserverKind.values()) {
                r.setEnabled(k, false);
                r.setEnabled(k, true);
            }
        }
        assertEquals(3, service.achievements().totalScored());
        assertEquals(3, achievements.progress().totalScored());
    }

    @Test
    @DisplayName("com o observador de Conquistas desligado a pontuação continua; ao religar ele alcança o ledger")
    void achievementsKeepScoringWhileObserverIsOff() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        service.complete(service.createTask("a").id());
        r.setEnabled(ObserverKind.ACHIEVEMENTS, false);
        service.complete(service.createTask("b").id());
        service.complete(service.createTask("c").id());
        assertEquals(3, service.achievements().totalScored());   // o domínio pontuou
        assertEquals(1, achievements.progress().totalScored());  // a visão ficou congelada

        r.setEnabled(ObserverKind.ACHIEVEMENTS, true);
        assertEquals(3, achievements.progress().totalScored());
        assertTrue(achievements.lastUnlocked().isEmpty());       // restauração não é uma conquista nova
        service.complete(service.createTask("d").id());
        assertEquals(Milestone.BRONZE_MEDAL, achievements.lastUnlocked().orElseThrow());
    }

    @Test
    @DisplayName("ligar e desligar não emite eventos")
    void togglingEmitsNoEvents() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        service.createTask("a");
        int before = events.size();
        for (ObserverKind k : ObserverKind.values()) {
            r.setEnabled(k, false);
            r.setEnabled(k, true);
        }
        assertEquals(before, events.size());
    }

    @Test
    @DisplayName("a mudança de preferência é informada uma vez; repetir o mesmo valor não informa")
    void onChangeIsReportedOnlyOnChange() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        r.setEnabled(ObserverKind.HISTORY, true);
        assertTrue(changes.isEmpty());
        r.setEnabled(ObserverKind.HISTORY, false);
        r.setEnabled(ObserverKind.HISTORY, false);
        assertEquals(1, changes.size());
        assertFalse(changes.get(0).isEnabled(ObserverKind.HISTORY));
        assertFalse(r.isEnabled(ObserverKind.HISTORY));
        assertEquals(changes.get(0), r.preferences());
    }

    @Test
    @DisplayName("desligar o Histórico para de registrar; religar volta a registrar")
    void historyToggle() {
        ObserverRegistry r = registry(ObserverPreferences.allEnabled());
        service.createTask("a");
        r.setEnabled(ObserverKind.HISTORY, false);
        service.createTask("b");
        assertEquals(1, history.entries().size());
        r.setEnabled(ObserverKind.HISTORY, true);
        service.createTask("c");
        assertEquals(2, history.entries().size());
    }
}
