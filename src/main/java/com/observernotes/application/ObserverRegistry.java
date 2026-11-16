package com.observernotes.application;

import com.observernotes.domain.event.DomainEvent;
import com.observernotes.observer.Observer;
import com.observernotes.observer.Subject;
import com.observernotes.observer.Subscription;
import com.observernotes.observer.impl.AchievementObserver;
import com.observernotes.observer.impl.ConstellationObserver;
import com.observernotes.observer.impl.CounterObserver;
import com.observernotes.observer.impl.HistoryObserver;
import com.observernotes.settings.ObserverKind;
import com.observernotes.settings.ObserverPreferences;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Liga e desliga os observadores conforme as preferências. Desligar cancela a inscrição;
 * ligar refaz a projeção a partir do estado atual do domínio e só então se inscreve,
 * sem emitir eventos. Nada aqui toca no ledger: mudar um observador nunca altera conquistas.
 */
public final class ObserverRegistry {

    private final Subject<DomainEvent> bus;
    private final TaskService service;
    private final Map<ObserverKind, Observer<DomainEvent>> observers = new EnumMap<>(ObserverKind.class);
    private final Map<ObserverKind, Subscription> subscriptions = new EnumMap<>(ObserverKind.class);
    private final CounterObserver counter;
    private final ConstellationObserver constellation;
    private final AchievementObserver achievements;
    private final Consumer<ObserverPreferences> onChange;
    private volatile ObserverPreferences preferences;

    public ObserverRegistry(Subject<DomainEvent> bus, TaskService service,
                            HistoryObserver history, CounterObserver counter,
                            ConstellationObserver constellation, AchievementObserver achievements,
                            ObserverPreferences initial, Consumer<ObserverPreferences> onChange) {
        this.bus = bus;
        this.service = service;
        this.counter = counter;
        this.constellation = constellation;
        this.achievements = achievements;
        this.onChange = onChange;
        observers.put(ObserverKind.HISTORY, history);
        observers.put(ObserverKind.COUNTER, counter);
        observers.put(ObserverKind.CONSTELLATION, constellation);
        observers.put(ObserverKind.ACHIEVEMENTS, achievements);
        for (ObserverKind kind : ObserverKind.values()) {
            if (initial.isEnabled(kind)) {
                synchronized (service) {
                    activate(kind);
                }
            }
        }
        this.preferences = initial;
    }

    public boolean isEnabled(ObserverKind kind) {
        return preferences.isEnabled(kind);
    }

    public ObserverPreferences preferences() {
        return preferences;
    }

    /** Ativa ou desativa um observador e informa a nova preferência (para ser salva). */
    public void setEnabled(ObserverKind kind, boolean enabled) {
        ObserverPreferences updated;
        // O monitor do serviço é o mesmo dos comandos: nenhum evento escapa entre resync e inscrição.
        synchronized (service) {
            if (preferences.isEnabled(kind) == enabled) {
                return;
            }
            if (enabled) {
                activate(kind);
            } else {
                Subscription s = subscriptions.remove(kind);
                if (s != null) {
                    s.cancel();
                }
            }
            updated = preferences.with(kind, enabled);
            preferences = updated;
        }
        onChange.accept(updated);
    }

    private void activate(ObserverKind kind) {
        switch (kind) {
            case HISTORY -> { /* o histórico é um registro de eventos: não há o que refazer */ }
            case COUNTER -> counter.resync(service.tasks());
            case CONSTELLATION -> constellation.resync(service.tasks());
            case ACHIEVEMENTS -> achievements.resync(service.achievements());
        }
        subscriptions.put(kind, bus.subscribe(observers.get(kind)));
    }
}
