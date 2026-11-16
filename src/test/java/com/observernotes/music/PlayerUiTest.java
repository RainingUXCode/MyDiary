package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.controller.PlayerUi;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.widget.UiAction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ações da interface sobre o player (sem janela): comandos, painel, efeitos e avisos. */
class PlayerUiTest {

    private static final class Sink implements EffectSink {
        final List<String> log = new ArrayList<>();
        boolean throwing;

        private void rec(String s) {
            log.add(s);
            if (throwing) {
                throw new IllegalStateException("efeito com defeito");
            }
        }

        @Override public void taskCreated(String id) { }
        @Override public void taskCompleted(String id) { }
        @Override public void taskDeleted(TaskRowModel snapshot, int slot) { }
        @Override public void star(int n) { }
        @Override public void medal(AchievementTier tier) { }

        @Override
        public void musicState(boolean playing) {
            rec(playing ? "play" : "pause");
        }

        @Override
        public void musicTrack(String title) {
            rec("track:" + title);
        }
    }

    private Path dir;
    private FakeBackend backend;
    private MusicPlayer music;
    private PlayerUi ui;
    private final Sink sink = new Sink();
    private final List<String> notices = new ArrayList<>();
    private List<Path> picked = List.of();
    private boolean pickerFails;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        backend = new FakeBackend();
        music = new MusicPlayer(backend, new JsonPlaylistStore(dir), () -> { });
        ui = new PlayerUi(music, () -> {
            if (pickerFails) {
                throw new IllegalStateException("sem seletor");
            }
            return picked;
        }, (text, warning) -> notices.add((warning ? "!" : "") + text), sink);
    }

    @AfterEach
    void tearDown() throws Exception {
        music.close();
        TempFiles.deleteRecursively(dir);
    }

    private Path song(String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[] {1});
    }

    private void act(UiAction.Type type, String key) {
        ui.handle(new UiAction(type, key));
    }

    private void load(String... names) throws IOException {
        List<Path> l = new ArrayList<>();
        for (String n : names) {
            l.add(song(n));
        }
        picked = l;
        act(UiAction.Type.PLAYER_ADD, "");
    }

    private String path(String name) {
        return Track.of(dir.resolve(name)).path();
    }

    @Test
    void modelStartsEmptyAvailableAndClosed() {
        PlayerModel m = ui.model();
        assertTrue(m.available());
        assertFalse(m.playing());
        assertFalse(m.panelOpen());
        assertFalse(m.hasTracks());
    }

    @Test
    void addingSeveralFilesOpensThePanelNotifiesAndNeverPlays() throws Exception {
        load("a.mp3", "b.wav");
        assertEquals(2, ui.model().rows().size());
        assertTrue(ui.model().panelOpen());
        assertEquals("2 músicas adicionadas", notices.get(0));
        assertEquals(0, backend.openCount());
        assertEquals("a", ui.model().rows().get(0).title());
    }

    @Test
    void unsupportedDuplicateAndCancelledPicksAreExplained() throws Exception {
        load("a.mp3");
        notices.clear();
        picked = List.of(song("capa.jpg"));
        act(UiAction.Type.PLAYER_ADD, "");
        assertEquals("!Formato não suportado: use MP3 ou WAV", notices.get(0));
        picked = List.of(dir.resolve("a.mp3"));
        act(UiAction.Type.PLAYER_ADD, "");
        assertTrue(notices.get(1).contains("já estão"));
        picked = List.of();
        act(UiAction.Type.PLAYER_ADD, "");
        assertEquals(2, notices.size());                                 // cancelar não avisa nada
        assertEquals(1, ui.model().rows().size());
        pickerFails = true;
        act(UiAction.Type.PLAYER_ADD, "");
        assertTrue(notices.get(2).contains("seletor"));
    }

    @Test
    void transportButtonsDriveTheAudio() throws Exception {
        load("a.wav", "b.wav");
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertTrue(ui.model().playing());
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertTrue(ui.model().paused());
        act(UiAction.Type.PLAYER_NEXT, "");
        assertEquals("b", ui.model().title());
        assertTrue(ui.model().playing());
        act(UiAction.Type.PLAYER_PREVIOUS, "");
        assertEquals("a", ui.model().title());
    }

    @Test
    void panelRowsActByPathNotByPosition() throws Exception {
        load("a.wav", "b.wav", "c.wav");
        act(UiAction.Type.PLAYER_PLAY, path("c.wav"));
        assertEquals("c", ui.model().title());
        act(UiAction.Type.PLAYER_UP, path("c.wav"));
        assertEquals("a,c,b", String.join(",", ui.model().rows().stream().map(PlayerModel.Row::title).toList()));
        assertEquals("c", ui.model().title());                           // continua tocando a mesma música
        act(UiAction.Type.PLAYER_DOWN, path("a.wav"));
        assertEquals("c,a,b", String.join(",", ui.model().rows().stream().map(PlayerModel.Row::title).toList()));
        act(UiAction.Type.PLAYER_REMOVE, path("a.wav"));
        assertEquals(2, ui.model().rows().size());
        long before = backend.openCount();
        act(UiAction.Type.PLAYER_REMOVE, path("sumiu.wav"));             // caminho que não existe mais
        act(UiAction.Type.PLAYER_PLAY, "nada");
        assertEquals(2, ui.model().rows().size());
        assertEquals(before, backend.openCount());
    }

    @Test
    void panelTogglesAndScrollIsClamped() throws Exception {
        load("a.wav", "b.wav", "c.wav", "d.wav", "e.wav", "f.wav", "g.wav", "h.wav");
        assertEquals(3, ui.model().maxOffset());
        act(UiAction.Type.PLAYER_SCROLL, "2");
        assertEquals(2, ui.model().panelOffset());
        act(UiAction.Type.PLAYER_SCROLL, "99");
        assertEquals(3, ui.model().panelOffset());
        act(UiAction.Type.PLAYER_SCROLL, "-99");
        assertEquals(0, ui.model().panelOffset());
        act(UiAction.Type.PLAYER_SCROLL, "end");
        assertEquals(3, ui.model().panelOffset());
        act(UiAction.Type.PLAYER_SCROLL, "home");
        act(UiAction.Type.PLAYER_SCROLL, "lixo");
        assertEquals(0, ui.model().panelOffset());
        act(UiAction.Type.PLAYER_PANEL, "");
        assertFalse(ui.model().panelOpen());
        act(UiAction.Type.PLAYER_PANEL, "");
        assertTrue(ui.model().panelOpen());
        ui.closePanel();
        assertFalse(ui.model().panelOpen());
    }

    @Test
    void removingTracksKeepsTheOffsetValid() throws Exception {
        load("a.wav", "b.wav", "c.wav", "d.wav", "e.wav", "f.wav", "g.wav");
        act(UiAction.Type.PLAYER_SCROLL, "end");
        act(UiAction.Type.PLAYER_REMOVE, path("g.wav"));
        act(UiAction.Type.PLAYER_REMOVE, path("f.wav"));
        act(UiAction.Type.PLAYER_REMOVE, path("e.wav"));
        assertEquals(0, ui.model().panelOffset());
    }

    @Test
    void volumeStepsSetValuesAndRepeatReachThePlayer() throws Exception {
        load("a.wav");
        act(UiAction.Type.PLAYER_TOGGLE, "");
        act(UiAction.Type.PLAYER_VOLUME, "-");
        assertEquals(0.6, ui.model().volume(), 1e-9);
        assertEquals(0.6, backend.last().volume, 1e-9);
        act(UiAction.Type.PLAYER_VOLUME, "+");
        act(UiAction.Type.PLAYER_VOLUME, "+");
        assertEquals(0.8, ui.model().volume(), 1e-9);
        act(UiAction.Type.PLAYER_VOLUME, "set:0.35");
        assertEquals(0.35, ui.model().volume(), 1e-9);
        act(UiAction.Type.PLAYER_VOLUME, "set:abc");
        act(UiAction.Type.PLAYER_VOLUME, "?");
        assertEquals(0.35, ui.model().volume(), 1e-9);
        act(UiAction.Type.PLAYER_REPEAT, "");
        assertTrue(ui.model().repeat());
    }

    @Test
    void effectsAppearOnlyForRealChangesAndNeverRepeat() throws Exception {
        load("a.wav", "b.wav");
        assertTrue(sink.log.isEmpty());                                  // adicionar não toca nem anima
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertEquals(List.of("play", "track:a"), sink.log);
        ui.onMusicChanged();
        ui.onMusicChanged();                                             // retorno assíncrono repetido
        assertEquals(2, sink.log.size());
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertEquals("pause", sink.log.get(2));
        act(UiAction.Type.PLAYER_TOGGLE, "");                            // retomar a mesma faixa: pulso, sem balão
        assertEquals(List.of("play", "track:a", "pause", "play"), sink.log);
        act(UiAction.Type.PLAYER_NEXT, "");
        assertEquals("track:b", sink.log.get(sink.log.size() - 1));
        sink.log.clear();
        act(UiAction.Type.PLAYER_VOLUME, "-");
        act(UiAction.Type.PLAYER_PANEL, "");
        assertTrue(sink.log.isEmpty());
    }

    @Test
    void autoAdvanceShowsTheNewTrackWhenTheAudioThreadReports() throws Exception {
        load("a.wav", "b.wav");
        act(UiAction.Type.PLAYER_TOGGLE, "");
        sink.log.clear();
        backend.last().finish();                                         // a termina sozinha; b começa
        ui.onMusicChanged();                                             // a janela repassa o aviso do áudio
        assertEquals(List.of("track:b"), sink.log);
    }

    @Test
    void aBrokenEffectNeverStopsTheMusic() throws Exception {
        sink.throwing = true;
        load("a.wav");
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertTrue(ui.model().playing());
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertTrue(ui.model().paused());
    }

    @Test
    void failedTrackIsReportedAsAWarningAndMarkedUnavailable() throws Exception {
        load("a.wav", "b.wav");
        backend.failing.addAll(List.of("a.wav", "b.wav"));
        notices.clear();
        act(UiAction.Type.PLAYER_TOGGLE, "");
        assertFalse(ui.model().playing());
        assertTrue(notices.stream().anyMatch(n -> n.startsWith("!") && n.contains("Nenhuma música")));
        assertTrue(ui.model().rows().stream().noneMatch(PlayerModel.Row::available));
    }

    @Test
    void emptyPlaylistExplainsInsteadOfPlaying() {
        act(UiAction.Type.PLAYER_TOGGLE, "");
        act(UiAction.Type.PLAYER_NEXT, "");
        assertFalse(ui.model().playing());
        assertTrue(notices.get(0).contains("Playlist vazia"));
        assertEquals(0, backend.openCount());
    }

    @Test
    void nonPlayerActionsAreIgnored() {
        act(UiAction.Type.TOGGLE_TASK, "x");
        act(UiAction.Type.NAV_LIST, "");
        assertFalse(ui.model().playing());
    }
}
