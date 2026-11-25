package com.observernotes.ui.controller;

import com.observernotes.application.AppCore;
import com.observernotes.application.LittleNotesService;
import com.observernotes.persistence.JsonNotesRepository;
import com.observernotes.persistence.JsonSettingsRepository;
import com.observernotes.persistence.JsonStateRepository;
import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import com.observernotes.persistence.SettingsRepository;
import com.observernotes.settings.ObserverPreferences;
import com.observernotes.support.SpyStateRepository;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.widget.TextInput;
import com.observernotes.ui.widget.UiAction;
import com.observernotes.sfx.SoundEffects;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Um "app" completo (núcleo + controlador) sobre uma pasta de dados real; {@link #restart()} simula fechar e abrir. */
final class World {

    /** Repositório de preferências que pode falhar. */
    static final class FlakySettings implements SettingsRepository {
        private final SettingsRepository delegate;
        volatile boolean failing;

        FlakySettings(SettingsRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public LoadResult<ObserverPreferences> load() {
            return delegate.load();
        }

        @Override
        public void save(ObserverPreferences preferences) {
            if (failing) {
                throw new PersistenceException("sem permissão (simulado)");
            }
            delegate.save(preferences);
        }
    }

    final Path dir;
    final Clock clock = Clock.fixed(Instant.parse("2026-11-14T21:40:00Z"), ZoneOffset.UTC);
    final List<Runnable> scheduled = new ArrayList<>();
    SpyStateRepository state;
    FlakySettings settings;
    SaveFailureTracker tracker;
    AppCore core;
    LittleNotesService notes;
    TextInput input;
    TextInput noteInput;
    AppController controller;
    int repaints;
    EffectSink sink = EffectSink.NONE;
    SoundEffects sounds = SoundEffects.NONE;

    World(Path dir) {
        this(dir, EffectSink.NONE);
    }

    World(Path dir, EffectSink sink) {
        this(dir, sink, SoundEffects.NONE);
    }

    World(Path dir, EffectSink sink, SoundEffects sounds) {
        this.dir = dir;
        this.sink = sink;
        this.sounds = sounds;
        restart();
    }

    void restart() {
        tracker = new SaveFailureTracker();
        state = new SpyStateRepository(new JsonStateRepository(dir));
        settings = new FlakySettings(new JsonSettingsRepository(dir));
        core = AppCore.start(state, settings, clock, tracker);
        notes = new LittleNotesService(new JsonNotesRepository(dir));
        input = new TextInput();
        noteInput = new TextInput();
        scheduled.clear();
        controller = new AppController(core, notes, tracker, input, noteInput, ZoneOffset.UTC, () -> repaints++,
                (task, ms) -> scheduled.add(task), sink, sounds);
    }

    ScreenModel model() {
        return controller.model();
    }

    void type(String s) {
        input.paste(s);
    }

    void add(String title) {
        input.clear();
        type(title);
        controller.handle(UiAction.of(UiAction.Type.ADD));
    }

    String id(int row) {
        return model().tasks().get(row).id();
    }

    void toggle(int row) {
        controller.handle(new UiAction(UiAction.Type.TOGGLE_TASK, id(row)));
    }

    void delete(int row) {
        controller.handle(new UiAction(UiAction.Type.DELETE_TASK, id(row)));
    }

    void go(PanelMode mode) {
        controller.handle(UiAction.of(switch (mode) {
            case TASKS -> UiAction.Type.NAV_LIST;
            case OBSERVERS -> UiAction.Type.NAV_ALERTS;
            case HISTORY -> UiAction.Type.NAV_RECORD;
        }));
    }

    void flipObserver(String key) {
        controller.handle(new UiAction(UiAction.Type.TOGGLE_OBSERVER, key));
    }

    void runScheduled() {
        List<Runnable> now = new ArrayList<>(scheduled);
        scheduled.clear();
        now.forEach(Runnable::run);
    }
}
