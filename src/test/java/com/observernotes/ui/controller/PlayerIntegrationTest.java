package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.music.FakeBackend;
import com.observernotes.music.JsonPlaylistStore;
import com.observernotes.music.MusicPlayer;
import com.observernotes.support.TempFiles;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O player funcionando junto com tarefas, observadores, conquistas e gravação: nenhum interfere no outro. */
class PlayerIntegrationTest {

    private Path dir;
    private FakeBackend backend;
    private World w;
    private MusicPlayer music;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        backend = new FakeBackend();
        w = new World(dir);
        attach();
    }

    private void attach() {
        music = new MusicPlayer(backend, new JsonPlaylistStore(dir), () -> { });
        w.controller.attachPlayer(new PlayerUi(music, () -> List.of(), w.controller::notifyUser, EffectSink.NONE));
    }

    @AfterEach
    void tearDown() throws Exception {
        music.close();
        TempFiles.deleteRecursively(dir);
    }

    private void load(String... names) throws Exception {
        List<Path> files = new java.util.ArrayList<>();
        for (String n : names) {
            files.add(Files.write(dir.resolve(n), new byte[] {1}));
        }
        music.addFiles(files);
    }

    private void player(UiAction.Type t) {
        w.controller.handle(UiAction.of(t));
    }

    @Test
    void tasksScoringAchievementsAndObserversWorkWhileMusicPlays() throws Exception {
        load("a.wav", "b.wav");
        player(UiAction.Type.PLAYER_TOGGLE);
        assertTrue(w.controller.playerModel().playing());
        for (int i = 1; i <= 4; i++) {
            w.add("t" + i);
            w.toggle(w.model().tasks().size() - 1);
        }
        w.flipObserver("COUNTER");
        w.go(PanelMode.OBSERVERS);
        w.go(PanelMode.HISTORY);
        assertEquals(4, w.core.service().achievements().totalScored());
        assertTrue(w.controller.playerModel().playing());                 // a música não parou
        assertEquals(1, backend.openCount());
    }

    @Test
    void playerActionsNeverTouchTasksHistoryScoreOrTheirSaves() throws Exception {
        w.add("a");
        w.toggle(0);
        int saves = w.state.saves;
        int history = w.core.history().entries().size();
        int total = w.core.service().achievements().totalScored();
        load("a.wav", "b.wav");
        for (UiAction.Type t : new UiAction.Type[] {UiAction.Type.PLAYER_TOGGLE, UiAction.Type.PLAYER_NEXT,
                UiAction.Type.PLAYER_PREVIOUS, UiAction.Type.PLAYER_PANEL, UiAction.Type.PLAYER_REPEAT}) {
            player(t);
        }
        w.controller.handle(new UiAction(UiAction.Type.PLAYER_VOLUME, "-"));
        assertEquals(saves, w.state.saves);
        assertEquals(history, w.core.history().entries().size());
        assertEquals(total, w.core.service().achievements().totalScored());
        assertEquals(1, w.model().tasks().size());
        assertTrue(w.model().tasks().get(0).done());
    }

    @Test
    void musicPlaylistAndTasksAreRestoredIndependentlyOnRestartWithoutAutoplay() throws Exception {
        load("a.wav", "b.wav");
        player(UiAction.Type.PLAYER_NEXT);
        w.add("tarefa");
        w.toggle(0);
        music.close();
        long opened = backend.openCount();
        w.restart();
        attach();
        assertEquals(1, w.model().tasks().size());
        assertEquals(1, w.core.service().achievements().totalScored());
        var pm = w.controller.playerModel();
        assertEquals(2, pm.rows().size());
        assertEquals("b", pm.title());
        assertFalse(pm.playing());
        assertEquals(opened, backend.openCount());                        // nada tocou sozinho
        assertTrue(Files.exists(dir.resolve("music.json")));
        assertTrue(Files.exists(dir.resolve("state.json")));
    }

    @Test
    void navigatingClosesThePlaylistButKeepsPlaying() throws Exception {
        load("a.wav");
        player(UiAction.Type.PLAYER_TOGGLE);
        player(UiAction.Type.PLAYER_PANEL);
        assertTrue(w.controller.playerModel().panelOpen());
        w.go(PanelMode.OBSERVERS);
        assertFalse(w.controller.playerModel().panelOpen());
        assertTrue(w.controller.playerModel().playing());
        w.go(PanelMode.HISTORY);
        w.go(PanelMode.TASKS);
        assertTrue(w.controller.playerModel().playing());
    }

    @Test
    void controllerWithoutPlayerIgnoresPlayerActionsSafely() throws Exception {
        World plain = new World(TempFiles.create());
        try {
            plain.controller.handle(UiAction.of(UiAction.Type.PLAYER_TOGGLE));
            plain.controller.handle(UiAction.of(UiAction.Type.PLAYER_ADD));
            assertFalse(plain.controller.playerModel().available());
            plain.add("a");
            assertEquals(1, plain.model().tasks().size());
        } finally {
            TempFiles.deleteRecursively(plain.dir);
        }
    }

    @Test
    void brokenAudioNeverAffectsTasksOrSaving() throws Exception {
        load("a.wav");
        backend.failing.add("a.wav");
        player(UiAction.Type.PLAYER_TOGGLE);                              // falha ao abrir
        w.add("a");
        w.toggle(0);
        assertEquals(1, w.core.service().achievements().totalScored());
        w.restart();
        assertTrue(w.model().tasks().get(0).done());
    }

    @Test
    void playerMessagesUseTheSameFooterNoticeAsTasks() throws Exception {
        player(UiAction.Type.PLAYER_TOGGLE);                              // playlist vazia
        assertTrue(w.model().feedback().notice().contains("Playlist vazia"));
        w.add("t");                                                       // um novo aviso/ação do usuário segue normal
        assertEquals(1, w.model().tasks().size());
    }
}
