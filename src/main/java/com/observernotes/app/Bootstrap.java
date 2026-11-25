package com.observernotes.app;

import com.observernotes.application.AppCore;
import com.observernotes.application.LittleNotesService;
import com.observernotes.persistence.JsonNotesRepository;
import com.observernotes.persistence.JsonSettingsRepository;
import com.observernotes.persistence.JsonStateRepository;
import com.observernotes.ui.controller.SaveFailureTracker;
import java.nio.file.Path;
import java.time.Clock;

/** Monta o núcleo com os repositórios JSON de uma pasta de dados. Separado do Main para ser testável. */
public final class Bootstrap {

    /** Núcleo pronto e o rastreador de falhas de gravação que ele usa. */
    public record Started(AppCore core, LittleNotesService notes, SaveFailureTracker failures) { }

    private Bootstrap() { }

    public static Started start(Path dataDir, Clock clock) {
        SaveFailureTracker tracker = new SaveFailureTracker();
        AppCore core = AppCore.start(new JsonStateRepository(dataDir), new JsonSettingsRepository(dataDir), clock, tracker);
        LittleNotesService notes = new LittleNotesService(new JsonNotesRepository(dataDir));
        return new Started(core, notes, tracker);
    }
}
