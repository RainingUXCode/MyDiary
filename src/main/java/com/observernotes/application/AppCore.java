package com.observernotes.application;

import com.observernotes.domain.DomainRestorer;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.observer.ObserverFailureHandler;
import com.observernotes.observer.SynchronousEventBus;
import com.observernotes.observer.impl.AchievementObserver;
import com.observernotes.observer.impl.ConstellationObserver;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryObserver;
import com.observernotes.persistence.AppSnapshot;
import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import com.observernotes.persistence.SettingsRepository;
import com.observernotes.persistence.StateRepository;
import com.observernotes.settings.ObserverPreferences;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Raiz de composição do núcleo (sem interface gráfica). Na partida:
 * <ol>
 *   <li>carrega o estado e as preferências (sem exceções por arquivo ausente ou inválido);</li>
 *   <li>restaura primeiro o <b>domínio</b>, validado, sem publicar eventos e sem pontuar;</li>
 *   <li>reconstrói as projeções dos observadores a partir do domínio restaurado;</li>
 *   <li>só então inscreve os observadores ativos e liga o salvamento automático.</li>
 * </ol>
 */
public final class AppCore {

    private final TaskService service;
    private final SynchronousEventBus<DomainEvent> bus;
    private final AutoSavePublisher autoSave;
    private final ObserverRegistry registry;
    private final HistoryObserver history = new HistoryObserver();
    private final CounterObserver counter = new CounterObserver();
    private final ConstellationObserver constellation = new ConstellationObserver();
    private final AchievementObserver achievements = new AchievementObserver();
    private final List<String> startupMessages = new ArrayList<>();
    private final LoadResult.Status stateStatus;
    private int taskPage;

    private AppCore(StateRepository stateRepository, SettingsRepository settingsRepository, Clock clock,
                    Consumer<PersistenceException> onSaveFailure, ObserverFailureHandler observerFailures) {
        LoadResult<AppSnapshot> loadedState = stateRepository.load();
        LoadResult<ObserverPreferences> loadedPrefs = settingsRepository.load();
        startupMessages.addAll(loadedState.messages());
        startupMessages.addAll(loadedPrefs.messages());
        this.stateStatus = loadedState.status();

        // 1) Domínio primeiro. Se os dados forem inválidos, lança aqui, antes de aplicar qualquer coisa.
        AppSnapshot snapshot = loadedState.value();
        this.taskPage = snapshot.taskPage();
        DomainRestorer.Restored restored = DomainRestorer.restore(snapshot.domain());
        history.load(snapshot.history());

        // 2) Infraestrutura de eventos e serviço (o serviço usa o publicador com salvamento).
        this.bus = new SynchronousEventBus<>(observerFailures);
        this.autoSave = new AutoSavePublisher(bus, this::snapshot, stateRepository, onSaveFailure);
        this.service = new TaskService(restored.board(), restored.ledger(), autoSave, clock);

        // 3) Projeções refeitas a partir do domínio e observadores ativos inscritos.
        this.registry = new ObserverRegistry(bus, service, history, counter, constellation, achievements,
                loadedPrefs.value(), prefs -> saveSettings(settingsRepository, prefs, onSaveFailure));

        // 4) Se os dados vieram de um backup, regrava o arquivo principal para "curá-lo".
        boolean healed = loadedState.status() == LoadResult.Status.RECOVERED_FROM_BACKUP;
        if (healed) {
            autoSave.saveNow();
        }
        if (loadedPrefs.status() == LoadResult.Status.RECOVERED_FROM_BACKUP) {
            saveSettings(settingsRepository, loadedPrefs.value(), onSaveFailure);
        }
    }

    public static AppCore start(StateRepository stateRepository, SettingsRepository settingsRepository,
                                Clock clock, Consumer<PersistenceException> onSaveFailure) {
        return new AppCore(stateRepository, settingsRepository, clock, onSaveFailure,
                ObserverFailureHandler.logging());
    }

    public static AppCore start(StateRepository stateRepository, SettingsRepository settingsRepository,
                                Clock clock, Consumer<PersistenceException> onSaveFailure,
                                ObserverFailureHandler observerFailures) {
        return new AppCore(stateRepository, settingsRepository, clock, onSaveFailure, observerFailures);
    }

    private AppSnapshot snapshot() {
        return new AppSnapshot(service.domainState(), history.entries(), taskPage);
    }

    public int taskPage() {
        return taskPage;
    }

    /** Persiste navegação visual sem publicar evento de domínio ou alterar pontuação/histórico. */
    public boolean setTaskPage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("página negativa");
        }
        if (taskPage == page) {
            return true;
        }
        taskPage = page;
        return autoSave.saveNow();
    }

    private static void saveSettings(SettingsRepository repo, ObserverPreferences prefs,
                                     Consumer<PersistenceException> onFailure) {
        try {
            repo.save(prefs);
        } catch (PersistenceException e) {
            onFailure.accept(e);
        }
    }

    /** Limpa o histórico exibido (botão CLEAR) e grava. As conquistas não são afetadas. */
    public void clearHistory() {
        history.clear();
        autoSave.saveNow();
    }

    public boolean saveNow() {
        return autoSave.saveNow();
    }

    public TaskService service() {
        return service;
    }

    public ObserverRegistry observers() {
        return registry;
    }

    public HistoryObserver history() {
        return history;
    }

    public CounterObserver counter() {
        return counter;
    }

    public ConstellationObserver constellation() {
        return constellation;
    }

    public AchievementObserver achievements() {
        return achievements;
    }

    public SynchronousEventBus<DomainEvent> bus() {
        return bus;
    }

    /** Avisos da partida (arquivo ausente, inválido, recuperado do backup...). */
    public List<String> startupMessages() {
        return List.copyOf(startupMessages);
    }

    public LoadResult.Status stateStatus() {
        return stateStatus;
    }

    public Optional<PersistenceException> lastSaveError() {
        return autoSave.lastError();
    }
}
