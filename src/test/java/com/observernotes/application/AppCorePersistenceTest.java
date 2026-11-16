package com.observernotes.application;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.domain.Milestone;
import com.observernotes.domain.TaskView;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.domain.event.TaskCompleted;
import com.observernotes.domain.event.TaskUncompleted;
import com.observernotes.persistence.AppSnapshot;
import com.observernotes.persistence.AtomicFileWriter;
import com.observernotes.persistence.JsonSettingsRepository;
import com.observernotes.persistence.JsonStateRepository;
import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import com.observernotes.settings.ObserverKind;
import com.observernotes.support.SpyStateRepository;
import com.observernotes.support.TempFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de ponta a ponta do núcleo: o "reiniciar o aplicativo" é abrir um novo AppCore na mesma pasta. */
class AppCorePersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T21:40:00Z"), ZoneOffset.UTC);

    private Path dir;
    private final List<PersistenceException> saveErrors = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        dir = TempFiles.create();
    }

    @AfterEach
    void tearDown() throws IOException {
        TempFiles.deleteRecursively(dir);
    }

    private JsonStateRepository stateRepo() {
        return new JsonStateRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    private JsonSettingsRepository settingsRepo() {
        return new JsonSettingsRepository(dir, new AtomicFileWriter(), CLOCK);
    }

    /** Abre o aplicativo (como se tivesse acabado de iniciar). */
    private AppCore open() {
        return AppCore.start(stateRepo(), settingsRepo(), CLOCK, saveErrors::add);
    }

    private AppSnapshot onDisk() {
        return stateRepo().load().value();
    }

    /** Cria, conclui e exclui: soma exatamente um ponto e libera a vaga. */
    private static void scoreOne(AppCore app) {
        TaskView t = app.service().createTask("t");
        app.service().complete(t.id());
        app.service().delete(t.id());
    }

    // ---- partida e salvamento automático -----------------------------------------

    @Test
    @DisplayName("sem arquivos, começa do zero sem avisos e sem gravar nada até a primeira alteração")
    void startsFresh() {
        AppCore app = open();
        assertEquals(LoadResult.Status.FRESH, app.stateStatus());
        assertTrue(app.startupMessages().isEmpty());
        assertTrue(app.service().tasks().isEmpty());
        assertEquals(0, app.service().achievements().totalScored());
        assertFalse(Files.exists(dir.resolve("state.json")));
    }

    @Test
    @DisplayName("salva automaticamente após criar, concluir, desmarcar e excluir")
    void autoSavesEveryChange() {
        AppCore app = open();
        TaskView a = app.service().createTask("a");
        assertEquals(1, onDisk().domain().tasks().size());

        app.service().complete(a.id());
        assertTrue(onDisk().domain().tasks().get(0).completed());
        assertTrue(onDisk().domain().tasks().get(0).scored());
        assertEquals(List.of(a.id()), onDisk().domain().scoredTaskIds());

        app.service().uncomplete(a.id());
        assertFalse(onDisk().domain().tasks().get(0).completed());
        assertEquals(1, onDisk().domain().scoredTaskIds().size());

        app.service().delete(a.id());
        assertTrue(onDisk().domain().tasks().isEmpty());
        assertEquals(List.of(a.id()), onDisk().domain().scoredTaskIds());
        assertTrue(saveErrors.isEmpty());
    }

    @Test
    @DisplayName("o ponto já está no arquivo quando a conclusão é gravada (um só salvamento por comando)")
    void milestoneDoesNotTriggerExtraSave() {
        SpyStateRepository spy = new SpyStateRepository(stateRepo());
        AppCore app = AppCore.start(spy, settingsRepo(), CLOCK, saveErrors::add);
        TaskView a = app.service().createTask("a");
        int afterCreate = spy.saves;
        app.service().complete(a.id());
        assertEquals(afterCreate + 1, spy.saves);
        assertEquals(1, onDisk().domain().scoredTaskIds().size());
    }

    // ---- reiniciar ---------------------------------------------------------------

    @Test
    @DisplayName("reiniciar preserva tarefas, UUIDs, títulos e estados")
    void restartPreservesTasks() {
        AppCore first = open();
        TaskView a = first.service().createTask("Estudar o Observer");
        TaskView b = first.service().createTask("Desenhar o diagrama");
        first.service().complete(a.id());
        List<TaskView> before = first.service().tasks();

        AppCore second = open();
        assertEquals(LoadResult.Status.LOADED, second.stateStatus());
        assertEquals(before, second.service().tasks());
        assertEquals(b.id(), second.service().tasks().get(1).id());
    }

    @Test
    @DisplayName("reiniciar preserva conquistas, estrelas e as projeções de todos os observadores")
    void restartPreservesAchievementsAndProjections() {
        AppCore first = open();
        for (int i = 0; i < 3; i++) {
            first.service().complete(first.service().createTask("t" + i).id());
        }
        AppCore second = open();
        assertEquals(3, second.service().achievements().totalScored());
        assertEquals(3, second.achievements().progress().totalScored());
        assertEquals(3, second.achievements().progress().starsInCurrentTier());
        assertEquals(3, second.constellation().litCount());
        assertEquals("3 de 3", second.counter().label());
        assertEquals(first.history().entries(), second.history().entries());
        assertEquals(6, second.history().entries().size());
    }

    @Test
    @DisplayName("reiniciar e concluir de novo uma tarefa já pontuada não concede nada")
    void restartThenRecompleteDoesNotScore() {
        AppCore first = open();
        TaskView a = first.service().createTask("a");
        first.service().complete(a.id());

        AppCore second = open();
        List<DomainEvent> events = new ArrayList<>();
        second.bus().subscribe(events::add);
        assertTrue(second.service().uncomplete(a.id()));
        assertTrue(second.service().complete(a.id()));

        assertEquals(1, second.service().achievements().totalScored());
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof TaskUncompleted);
        assertFalse(((TaskCompleted) events.get(1)).firstCompletion());
        assertTrue(events.stream().noneMatch(e -> e instanceof MilestoneUnlocked));
        assertEquals(1, open().service().achievements().totalScored());
        assertEquals(1, onDisk().domain().scoredTaskIds().size());
    }

    @Test
    @DisplayName("excluir tarefas, reiniciar e continuar: a progressão segue do ponto exato")
    void deleteRestartContinues() {
        AppCore first = open();
        for (int i = 0; i < 3; i++) {
            scoreOne(first);
        }
        AppCore second = open();
        assertTrue(second.service().tasks().isEmpty());
        assertEquals(3, second.service().achievements().totalScored());
        assertEquals(3, second.achievements().progress().totalScored());

        List<DomainEvent> events = new ArrayList<>();
        second.bus().subscribe(events::add);
        scoreOne(second);
        MilestoneUnlocked unlocked = (MilestoneUnlocked) events.stream()
                .filter(e -> e instanceof MilestoneUnlocked).findFirst().orElseThrow();
        assertEquals(Milestone.BRONZE_MEDAL, unlocked.milestone());
        assertEquals(4, open().service().achievements().totalScored());
    }

    @Test
    @DisplayName("restaura a conquista máxima e não concede mais nada depois")
    void restoreMax() {
        AppCore first = open();
        for (int i = 0; i < 16; i++) {
            scoreOne(first);
        }
        assertTrue(first.service().achievements().isMax());

        AppCore second = open();
        assertTrue(second.service().achievements().isMax());
        assertTrue(second.achievements().progress().isMax());
        List<DomainEvent> events = new ArrayList<>();
        second.bus().subscribe(events::add);
        scoreOne(second);
        scoreOne(second);
        assertTrue(events.stream().noneMatch(e -> e instanceof MilestoneUnlocked));
        assertEquals(16, open().service().achievements().totalScored());
    }

    @Test
    @DisplayName("restaurar não emite eventos nem grava: o histórico e as conquistas ficam como estavam")
    void restoreEmitsNothingAndWritesNothing() {
        AppCore first = open();
        for (int i = 0; i < 5; i++) {
            scoreOne(first);
        }
        int historySize = first.history().entries().size();

        SpyStateRepository spy = new SpyStateRepository(stateRepo());
        AppCore second = AppCore.start(spy, settingsRepo(), CLOCK, saveErrors::add);
        assertEquals(0, spy.saves);
        assertEquals(historySize, second.history().entries().size());
        assertTrue(second.achievements().lastUnlocked().isEmpty());
        assertEquals(5, second.service().achievements().totalScored());
    }

    @Test
    @DisplayName("reiniciar várias vezes não altera o resultado")
    void repeatedRestartsAreStable() {
        AppCore app = open();
        app.service().complete(app.service().createTask("a").id());
        AppSnapshot expected = onDisk();
        for (int i = 0; i < 4; i++) {
            app = open();
            assertEquals(expected, onDisk());
        }
        assertEquals(1, app.service().achievements().totalScored());
    }

    // ---- observadores ------------------------------------------------------------

    @Test
    @DisplayName("preferências dos observadores são salvas e restauradas")
    void observerPreferencesSurviveRestart() {
        AppCore first = open();
        first.observers().setEnabled(ObserverKind.COUNTER, false);
        first.observers().setEnabled(ObserverKind.HISTORY, false);

        AppCore second = open();
        assertFalse(second.observers().isEnabled(ObserverKind.COUNTER));
        assertFalse(second.observers().isEnabled(ObserverKind.HISTORY));
        assertTrue(second.observers().isEnabled(ObserverKind.CONSTELLATION));
        assertTrue(second.observers().isEnabled(ObserverKind.ACHIEVEMENTS));

        second.service().createTask("a");
        assertEquals(0, second.counter().taskCount());
        assertEquals(0, second.history().entries().size());
        assertEquals(1, second.constellation().starCount());
    }

    @Test
    @DisplayName("com observadores desligados, as conquistas continuam sendo gravadas e restauradas")
    void disabledObserversNeverAffectAchievements() {
        AppCore first = open();
        for (ObserverKind k : ObserverKind.values()) {
            first.observers().setEnabled(k, false);
        }
        for (int i = 0; i < 3; i++) {
            scoreOne(first);
        }
        assertEquals(3, first.service().achievements().totalScored());

        AppCore second = open();
        assertEquals(3, second.service().achievements().totalScored());
        second.observers().setEnabled(ObserverKind.ACHIEVEMENTS, true);
        assertEquals(3, second.achievements().progress().totalScored());
        assertEquals(3, second.service().achievements().totalScored());
    }

    @Test
    @DisplayName("o histórico gravado inclui o evento atual mesmo que o observador tenha sido religado por último")
    void historyIsSavedAfterAllObserversRan() {
        AppCore app = open();
        app.observers().setEnabled(ObserverKind.HISTORY, false);
        app.observers().setEnabled(ObserverKind.HISTORY, true);
        app.service().createTask("a");
        assertEquals(1, onDisk().history().size());
    }

    @Test
    @DisplayName("limpar o histórico é gravado e não afeta as conquistas")
    void clearHistoryPersists() {
        AppCore app = open();
        app.service().complete(app.service().createTask("a").id());
        app.service().createTask("b");
        assertFalse(app.history().entries().isEmpty());

        app.clearHistory();
        AppCore again = open();
        assertTrue(again.history().entries().isEmpty());
        assertEquals(1, again.service().achievements().totalScored());
        assertEquals(2, again.service().tasks().size());
    }

    // ---- falhas ------------------------------------------------------------------

    @Test
    @DisplayName("falha ao gravar não afeta o domínio; a próxima alteração grava tudo que ficou pendente")
    void failedSaveDoesNotBreakDomainAndRecovers() {
        SpyStateRepository spy = new SpyStateRepository(stateRepo());
        AppCore app = AppCore.start(spy, settingsRepo(), CLOCK, saveErrors::add);
        spy.failing = true;

        TaskView a = app.service().createTask("a");
        assertDoesNotThrow(() -> app.service().complete(a.id()));
        assertEquals(1, app.service().achievements().totalScored());
        assertEquals(1, app.achievements().progress().totalScored());
        assertEquals(2, saveErrors.size());
        assertTrue(app.lastSaveError().isPresent());
        assertFalse(Files.exists(dir.resolve("state.json")));

        spy.failing = false;
        app.service().createTask("b");
        assertTrue(app.lastSaveError().isEmpty());
        AppSnapshot saved = onDisk();
        assertEquals(2, saved.domain().tasks().size());
        assertEquals(List.of(a.id()), saved.domain().scoredTaskIds());
    }

    @Test
    @DisplayName("falha ao gravar as preferências é informada e não impede a mudança em memória")
    void failedSettingsSave() throws IOException {
        Files.createDirectories(dir.resolve("settings.json.tmp"));
        Files.writeString(dir.resolve("settings.json.tmp").resolve("x"), "x"); // bloqueia a escrita
        AppCore app = open();
        app.observers().setEnabled(ObserverKind.COUNTER, false);
        assertFalse(app.observers().isEnabled(ObserverKind.COUNTER));
        assertEquals(1, saveErrors.size());
    }

    @Test
    @DisplayName("estado corrompido: começa do zero, avisa, preserva o arquivo e continua gravando normalmente")
    void corruptStateStartsFresh() throws IOException {
        TempFiles.write(dir.resolve("state.json"), "{ isto está corrompido");
        AppCore app = open();
        assertEquals(LoadResult.Status.DISCARDED, app.stateStatus());
        assertFalse(app.startupMessages().isEmpty());
        assertTrue(app.service().tasks().isEmpty());
        assertEquals(1, TempFiles.countPreserved(dir.resolve("state.json")));

        app.service().createTask("a");
        assertEquals(1, open().service().tasks().size());
        assertEquals("{ isto está corrompido", TempFiles.read(TempFiles.firstPreserved(dir.resolve("state.json"))));
    }

    @Test
    @DisplayName("preferências corrompidas não afetam o estado: tarefas e conquistas são restauradas")
    void corruptSettingsDoNotAffectState() throws IOException {
        AppCore first = open();
        scoreOne(first);
        scoreOne(first);
        TempFiles.write(dir.resolve("settings.json"), "nada de JSON");

        AppCore second = open();
        assertEquals(LoadResult.Status.LOADED, second.stateStatus());
        assertEquals(2, second.service().achievements().totalScored());
        assertTrue(second.observers().isEnabled(ObserverKind.COUNTER));
        assertFalse(second.startupMessages().isEmpty());
        assertEquals(1, TempFiles.countPreserved(dir.resolve("settings.json")));
    }

    @Test
    @DisplayName("dados inconsistentes no arquivo nunca chegam ao domínio")
    void inconsistentFileIsNeverApplied() throws IOException {
        AppCore first = open();
        first.service().complete(first.service().createTask("a").id());
        TempFiles.mutateJson(dir.resolve("state.json"), root ->
                root.getAsJsonObject("achievements").getAsJsonArray("scoredTaskIds").remove(0));
        // sem o backup válido para recuperar, o app começa do zero em vez de aplicar dados inconsistentes
        Files.deleteIfExists(dir.resolve("state.json.bak"));

        AppCore second = open();
        assertEquals(LoadResult.Status.DISCARDED, second.stateStatus());
        assertEquals(0, second.service().achievements().totalScored());
        assertTrue(second.service().tasks().isEmpty());
    }

    @Test
    @DisplayName("recuperado do backup: avisa e regrava o arquivo principal")
    void backupRecoveryHealsMainFile() throws IOException {
        AppCore first = open();
        TaskView a = first.service().createTask("a");
        first.service().complete(a.id());               // o backup guarda o estado logo após a criação
        TempFiles.write(dir.resolve("state.json"), "danificado");

        AppCore second = open();
        assertEquals(LoadResult.Status.RECOVERED_FROM_BACKUP, second.stateStatus());
        assertFalse(second.startupMessages().isEmpty());
        assertEquals(1, second.service().tasks().size());
        assertEquals(LoadResult.Status.LOADED, stateRepo().load().status());   // curado
        assertEquals("danificado", TempFiles.read(TempFiles.firstPreserved(dir.resolve("state.json"))));
    }
}
