package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Efeitos: só para fatos reais, nunca repetidos, e jamais interferem na pontuação ou na gravação. */
class AppControllerEffectsTest {

    private static final class Recorder implements EffectSink {
        final List<String> log = new ArrayList<>();
        RuntimeException failWith;

        private void rec(String s) {
            log.add(s);
            if (failWith != null) {
                throw failWith;
            }
        }

        @Override
        public void taskCreated(String taskId) {
            rec("created");
        }

        @Override
        public void taskCompleted(String taskId) {
            rec("completed");
        }

        @Override
        public void taskDeleted(TaskRowModel snapshot, int slot) {
            rec("deleted:" + snapshot.title() + "@" + slot);
        }

        @Override
        public void star(int starInTier) {
            rec("star" + starInTier);
        }

        @Override
        public void medal(AchievementTier tier) {
            rec("medal:" + tier);
        }
    }

    private Path dir;
    private Recorder rec;
    private World w;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        rec = new Recorder();
        w = new World(dir, rec);
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    /** Cria, conclui e exclui uma tarefa nova: cada uma vale um marco inédito. */
    private void earnOne(String title) {
        w.add(title);
        w.toggle(w.model().tasks().size() - 1);
        w.delete(w.model().tasks().size() - 1);
    }

    @Test
    void createCompleteDeleteEachRequestTheirEffect() {
        w.add("a");
        w.add("b");
        assertEquals(List.of("created", "created"), rec.log);
        rec.log.clear();
        w.toggle(1);
        assertTrue(rec.log.contains("completed"));
        rec.log.clear();
        w.delete(0);
        assertEquals(List.of("deleted:a@0"), rec.log);
    }

    @Test
    void uncheckingAndRejectedActionsProduceNoEffect() {
        w.add("a");
        w.toggle(0);
        rec.log.clear();
        w.toggle(0);                                                      // desmarca
        assertTrue(rec.log.isEmpty());
        w.add("   ");                                                     // recusado
        w.controller.handle(new UiAction(UiAction.Type.DELETE_TASK, "inexistente"));
        w.controller.handle(new UiAction(UiAction.Type.TOGGLE_TASK, java.util.UUID.randomUUID().toString()));
        assertTrue(rec.log.isEmpty());
    }

    @Test
    void seventhTaskOnANewPageProducesItsCreationEffect() {
        for (int i = 1; i <= 6; i++) {
            w.add("t" + i);
        }
        rec.log.clear();
        w.add("sétima");
        assertEquals(List.of("created"), rec.log);
    }

    @Test
    void starsAreCelebratedOnceEachWithTheirNumberThenBronze() {
        w.add("a");
        w.add("b");
        w.add("c");
        w.add("d");
        rec.log.clear();
        w.toggle(0);
        w.toggle(1);
        w.toggle(2);
        w.toggle(3);
        List<String> marks = rec.log.stream().filter(s -> s.startsWith("star") || s.startsWith("medal")).toList();
        assertEquals(List.of("star1", "star2", "star3", "medal:BRONZE"), marks);
    }

    @Test
    void uncheckAndRecheckNeverCelebratesAgain() {
        w.add("a");
        w.toggle(0);
        rec.log.clear();
        for (int i = 0; i < 6; i++) {
            w.toggle(0);
        }
        assertTrue(rec.log.stream().noneMatch(s -> s.startsWith("star") || s.startsWith("medal")));
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void medalsAndMaxHaveTheirOwnCelebrations() {
        for (int i = 1; i <= 16; i++) {
            earnOne("t" + i);
        }
        List<String> medals = rec.log.stream().filter(s -> s.startsWith("medal")).toList();
        assertEquals(List.of("medal:BRONZE", "medal:SILVER", "medal:GOLD", "medal:MAX"), medals);
        assertEquals(12, rec.log.stream().filter(s -> s.startsWith("star")).count());
    }

    @Test
    void restartingNeverReplaysOldCelebrations() {
        for (int i = 1; i <= 5; i++) {
            earnOne("t" + i);
        }
        assertFalse(rec.log.isEmpty());
        rec.log.clear();
        w.restart();
        w.model();
        w.go(PanelMode.OBSERVERS);
        w.go(PanelMode.TASKS);
        assertTrue(rec.log.isEmpty());
        w.add("novo");
        w.toggle(0);                                                     // só o marco realmente novo
        assertEquals(List.of("created", "completed", "star2"), rec.log.subList(0, 3));
    }

    @Test
    void disabledAchievementsObserverSkipsTheShowButStillScores() {
        w.flipObserver("ACHIEVEMENTS");
        w.add("a");
        rec.log.clear();
        w.toggle(0);
        assertTrue(rec.log.stream().noneMatch(s -> s.startsWith("star")));
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void brokenEffectsNeverAffectScoringStateOrSaving() {
        rec.failWith = new IllegalStateException("animação com defeito");
        w.add("a");
        w.toggle(0);
        w.add("b");
        w.delete(1);
        assertEquals(1, w.model().tasks().size());
        assertTrue(w.model().tasks().get(0).done());
        assertEquals(1, w.core.service().achievements().totalScored());
        assertFalse(w.model().feedback().warning());
        w.restart();                                                     // e tudo foi gravado
        assertEquals(1, w.model().tasks().size());
        assertTrue(w.model().tasks().get(0).done());
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void effectsDoNotChangeWhatIsSavedOrRestored() throws Exception {
        World plain = new World(TempFiles.create());
        try {
            for (World x : new World[] {w, plain}) {
                x.add("a");
                x.toggle(0);
                x.add("b");
                x.delete(1);
                x.restart();
            }
            assertEquals(plain.model().tasks().size(), w.model().tasks().size());
            assertEquals(plain.model().tasks().get(0).title(), w.model().tasks().get(0).title());
            assertEquals(plain.model().tasks().get(0).done(), w.model().tasks().get(0).done());
            assertEquals(plain.core.service().achievements().totalScored(),
                    w.core.service().achievements().totalScored());
            assertEquals(plain.model().history().stream().map(h -> h.text()).toList(),
                    w.model().history().stream().map(h -> h.text()).toList());
        } finally {
            TempFiles.deleteRecursively(plain.dir);
        }
    }

    @Test
    void failedSaveStillPlaysTheEffectButNeverPretendsItWasSaved() {
        w.state.failing = true;
        w.add("a");
        assertEquals(List.of("created"), rec.log);
        assertTrue(w.model().feedback().warning());
    }
}
