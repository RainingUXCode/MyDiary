package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fluxos completos: ação do usuário → AppCore → observadores → disco → reinício. */
class AppControllerIntegrationTest {

    private Path dir;
    private World w;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        w = new World(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    // ------------------------------------------------------------ criação

    @Test
    void startsEmptyAndHonest() {
        ScreenModel m = w.model();
        assertTrue(m.tasks().isEmpty());
        assertEquals(AchievementTier.NONE, m.achievementTier());
        assertEquals("", m.footerMessage());
        assertEquals(PanelMode.TASKS, m.mode());
    }

    @Test
    void addButtonCreatesTheTaskAndClearsTheField() {
        w.add("Estudar o Observer");
        assertEquals(1, w.model().tasks().size());
        assertEquals("Estudar o Observer", w.model().tasks().get(0).title());
        assertTrue(w.input.isEmpty());
        assertTrue(w.repaints > 0);
    }

    @Test
    void enterKeyCreatesTheTaskToo() {
        w.type("  Escrever o README  ");
        w.controller.handle(UiAction.of(UiAction.Type.SUBMIT));
        assertEquals("Escrever o README", w.model().tasks().get(0).title());
    }

    @Test
    void blankTitleIsRejectedWithAMessageAndNothingIsCreated() {
        w.add("   ");
        assertTrue(w.model().tasks().isEmpty());
        assertEquals("Escreva um título para a tarefa", w.model().feedback().notice());
        assertTrue(w.model().feedback().fieldHighlight());
        w.runScheduled();
        assertEquals("", w.model().feedback().notice());
    }

    @Test
    void seventhTaskCreatesAndSelectsASecondPage() {
        for (int i = 1; i <= 6; i++) {
            w.add("t" + i);
        }
        w.add("sétima");
        assertEquals(1, w.model().tasks().size());
        assertEquals("sétima", w.model().tasks().get(0).title());
        assertEquals(1, w.model().taskPages().current());
        assertTrue(w.input.isEmpty());
    }

    @Test
    void deletingFreesASlotAndTheSeventhCanBeCreated() {
        for (int i = 1; i <= 6; i++) {
            w.add("t" + i);
        }
        w.delete(2);
        assertEquals("", w.model().feedback().placeholder());
        w.add("nova");
        assertEquals(6, w.model().tasks().size());
        assertEquals("nova", w.model().tasks().get(5).title());
    }

    @Test
    void staleNoticeTimerDoesNotEraseANewerNotice() {
        w.add("");
        Runnable first = w.scheduled.get(0);
        w.add(" ");
        first.run();                                   // o aviso mais novo continua
        assertFalse(w.model().feedback().notice().isEmpty());
    }

    // ------------------------------------------------------------ concluir e pontuar

    @Test
    void completingUpdatesCounterAchievementsAndFooter() {
        w.add("a");
        w.add("b");
        w.toggle(0);
        ScreenModel m = w.model();
        assertTrue(m.tasks().get(0).done());
        assertEquals(1, m.views().done());
        assertEquals(2, m.views().total());
        assertEquals(1, m.starsLit());
        assertEquals(2, m.starsTotal());
        assertEquals(AchievementTier.STARS, m.achievementTier());
        assertEquals("1 de 16", m.achievementState());
        assertTrue(m.footerMessage().contains("Nova conquista: estrela"));
    }

    @Test
    void uncheckAndRecheckNeverEarnsAnotherStar() {
        w.add("a");
        w.toggle(0);
        for (int i = 0; i < 5; i++) {
            w.toggle(0);
        }
        assertEquals(1, w.core.service().achievements().totalScored());
        assertEquals("1 de 16", w.model().achievementState());
        w.toggle(0);
        w.toggle(0);
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void counterIsSeparateFromPermanentAchievements() {
        w.add("a");
        w.toggle(0);
        w.toggle(0);                                   // desmarca
        assertEquals(0, w.model().views().done());
        assertEquals("1 de 16", w.model().achievementState());
    }

    @Test
    void deletingTasksKeepsAchievements() {
        w.add("a");
        w.add("b");
        w.toggle(0);
        w.toggle(1);
        w.delete(0);
        w.delete(0);
        assertTrue(w.model().tasks().isEmpty());
        assertEquals(0, w.model().views().total());
        assertEquals("2 de 16", w.model().achievementState());
        assertEquals(AchievementTier.STARS, w.model().achievementTier());
    }

    @Test
    void medalsUseTheirOwnTier() {
        for (int round = 0; round < 4; round++) {          // 4 conclusões inéditas = bronze
            w.add("t" + round);
            w.toggle(0);
            w.delete(0);
        }
        assertEquals(AchievementTier.BRONZE, w.model().achievementTier());
        for (int round = 0; round < 4; round++) {
            w.add("u" + round);
            w.toggle(0);
            w.delete(0);
        }
        assertEquals(AchievementTier.SILVER, w.model().achievementTier());
    }

    @Test
    void staleKeyAffectsNothingAndTheRightTaskIsAlwaysHit() {
        w.add("a");
        w.add("b");
        w.add("c");
        String idA = w.id(0);
        String idC = w.id(2);
        w.delete(0);                                       // a some; c sobe para a linha 1
        w.controller.handle(new UiAction(UiAction.Type.TOGGLE_TASK, idA));   // clique antigo na tarefa excluída
        assertEquals(2, w.model().tasks().size());
        assertEquals(0, w.model().views().done());
        w.controller.handle(new UiAction(UiAction.Type.TOGGLE_TASK, idC));
        assertFalse(w.model().tasks().get(0).done());
        assertTrue(w.model().tasks().get(1).done());
        w.controller.handle(new UiAction(UiAction.Type.TOGGLE_TASK, "isto-nao-e-um-uuid"));
        assertEquals(1, w.model().views().done());
    }

    // ------------------------------------------------------------ reiniciar

    @Test
    void createdTasksSurviveRestart() {
        w.add("a");
        w.add("b");
        w.restart();
        assertEquals(2, w.model().tasks().size());
        assertEquals("b", w.model().tasks().get(1).title());
        assertEquals("", w.model().feedback().notice());
    }

    @Test
    void completionCounterAndAchievementsSurviveRestartWithoutNewEvents() {
        w.add("a");
        w.add("b");
        w.toggle(0);
        w.restart();
        ScreenModel m = w.model();
        assertTrue(m.tasks().get(0).done());
        assertEquals(1, m.views().done());
        assertEquals(1, m.starsLit());
        assertEquals("1 de 16", m.achievementState());
        assertTrue(m.footerMessage().contains("Concluída: a"));   // vem do histórico restaurado
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void uncheckingThenRestartingThenRecheckingDoesNotDuplicateRewards() {
        w.add("a");
        w.toggle(0);
        w.toggle(0);
        w.restart();
        w.toggle(0);
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void deletedTasksStayDeletedAndAchievementsStayAfterRestart() {
        w.add("a");
        w.toggle(0);
        w.delete(0);
        w.restart();
        assertTrue(w.model().tasks().isEmpty());
        assertEquals("1 de 16", w.model().achievementState());
    }

    @Test
    void progressionContinuesAcrossRestarts() {
        for (int i = 1; i <= 3; i++) {
            w.add("t" + i);
            w.toggle(w.model().tasks().size() - 1);
            w.restart();
        }
        assertEquals(3, w.core.service().achievements().totalScored());
        w.add("t4");
        w.toggle(w.model().tasks().size() - 1);
        assertEquals(4, w.core.service().achievements().totalScored());
        assertEquals(AchievementTier.BRONZE, w.model().achievementTier());
    }

    // ------------------------------------------------------------ observadores

    @Test
    void disablingCounterShowsOffAndReenablingResyncsWithTheCurrentState() {
        w.add("a");
        w.add("b");
        w.toggle(0);
        w.flipObserver("COUNTER");
        assertFalse(w.model().views().counterOn());
        w.toggle(1);                                       // muda enquanto desligado
        w.add("c");
        w.flipObserver("COUNTER");
        ScreenModel m = w.model();
        assertTrue(m.views().counterOn());
        assertEquals(2, m.views().done());
        assertEquals(3, m.views().total());
    }

    @Test
    void disablingAnyObserverNeverChangesAchievementsOrTasks() {
        w.add("a");
        w.toggle(0);
        for (String k : new String[] {"HISTORY", "COUNTER", "CONSTELLATION", "ACHIEVEMENTS"}) {
            w.flipObserver(k);
        }
        assertEquals(1, w.core.service().achievements().totalScored());
        assertEquals(1, w.model().tasks().size());
        w.toggle(0);
        w.toggle(0);                                       // com tudo desligado
        assertEquals(1, w.core.service().achievements().totalScored());
        for (String k : new String[] {"HISTORY", "COUNTER", "CONSTELLATION", "ACHIEVEMENTS"}) {
            w.flipObserver(k);
        }
        assertEquals("1 de 16", w.model().achievementState());
    }

    @Test
    void whileAchievementsObserverIsOffTheLedgerStillScoresAndViewCatchesUp() {
        w.add("a");
        w.flipObserver("ACHIEVEMENTS");
        w.toggle(0);
        assertEquals(1, w.core.service().achievements().totalScored());
        w.flipObserver("ACHIEVEMENTS");
        assertEquals("1 de 16", w.model().achievementState());
        assertEquals(AchievementTier.STARS, w.model().achievementTier());
    }

    @Test
    void observerPreferencesAreSavedAndRestored() {
        w.flipObserver("CONSTELLATION");
        w.flipObserver("HISTORY");
        w.restart();
        assertFalse(w.model().views().constellationOn());
        assertFalse(w.model().views().historyOn());
        assertTrue(w.model().views().counterOn());
        assertFalse(w.model().observers().get(0).enabled());
    }

    @Test
    void observerCardsReflectTheRegistry() {
        w.flipObserver("COUNTER");
        var cards = w.model().observers();
        assertEquals(4, cards.size());
        assertTrue(cards.get(0).enabled());
        assertFalse(cards.get(1).enabled());
    }

    // ------------------------------------------------------------ histórico e CLEAR

    @Test
    void historyShowsRealEventsMostRecentFirst() {
        w.add("a");
        w.toggle(0);
        w.delete(0);
        var rows = w.model().history();
        assertEquals(3, rows.size());
        assertEquals("Excluída: a", rows.get(0).text());
        assertEquals("Concluída: a", rows.get(1).text());
        assertEquals("Criada: a", rows.get(2).text());
        assertEquals("21:40", rows.get(0).time());
    }

    @Test
    void clearRemovesOnlyTheHistory() {
        w.add("a");
        w.add("b");
        w.toggle(0);
        w.controller.handle(UiAction.of(UiAction.Type.CLEAR));
        ScreenModel m = w.model();
        assertTrue(m.history().isEmpty());
        assertEquals(2, m.tasks().size());
        assertEquals("1 de 16", m.achievementState());
        w.restart();
        assertTrue(w.model().history().isEmpty());
        assertEquals(2, w.model().tasks().size());
        assertEquals(1, w.core.service().achievements().totalScored());
    }

    @Test
    void historyKeepsRecordingAfterClear() {
        w.add("a");
        w.controller.handle(UiAction.of(UiAction.Type.CLEAR));
        w.add("b");
        assertEquals(1, w.model().history().size());
        assertEquals("Criada: b", w.model().history().get(0).text());
    }

    // ------------------------------------------------------------ navegação

    @Test
    void navigationSwitchesPanelsWithoutLosingData() {
        w.add("a");
        w.toggle(0);
        w.go(PanelMode.OBSERVERS);
        assertEquals(PanelMode.OBSERVERS, w.model().mode());
        w.go(PanelMode.HISTORY);
        assertEquals(PanelMode.HISTORY, w.model().mode());
        assertEquals(2, w.model().history().size());
        w.go(PanelMode.TASKS);
        assertEquals(1, w.model().tasks().size());
        assertTrue(w.model().tasks().get(0).done());
        assertEquals("1 de 16", w.model().achievementState());
    }

    // ------------------------------------------------------------ rodapé

    @Test
    void footerFollowsRealEventsAndWorksEvenWithHistoryObserverOff() {
        w.flipObserver("HISTORY");
        w.add("ler");
        assertEquals("21:40 · Criada: ler", w.model().footerMessage());
        w.toggle(0);
        w.toggle(0);
        assertEquals("21:40 · Desmarcada: ler", w.model().footerMessage());
        w.delete(0);
        assertEquals("21:40 · Excluída: ler", w.model().footerMessage());
    }

    @Test
    void newAchievementBadgeAndPlayerAreNotFaked() {
        w.add("a");
        w.toggle(0);
        assertFalse(w.model().newAchievement());
    }

    // ------------------------------------------------------------ falhas de gravação

    @Test
    void failedStateSaveIsNeverPresentedAsSavedAndRecoversOnNextSuccess() {
        w.state.failing = true;
        w.add("a");
        assertEquals(1, w.model().tasks().size());           // continua na tela
        assertTrue(w.model().feedback().warning());
        assertTrue(w.model().feedback().notice().startsWith("Falha ao salvar"));
        w.restart();
        assertTrue(w.model().tasks().isEmpty());              // de fato não foi gravado
        w.state.failing = false;
        w.add("b");
        assertFalse(w.model().feedback().warning());
        w.restart();
        assertEquals(1, w.model().tasks().size());
    }

    @Test
    void failureStaysVisibleOnLaterNonSavingInteractions() {
        w.state.failing = true;
        w.add("a");
        w.go(PanelMode.HISTORY);
        assertTrue(w.model().feedback().warning());
    }

    @Test
    void failedSettingsSaveIsReportedAndClearedByASuccessfulToggle() {
        w.settings.failing = true;
        w.flipObserver("COUNTER");
        assertTrue(w.model().feedback().notice().startsWith("Falha ao salvar as preferências"));
        assertFalse(w.model().views().counterOn());           // a mudança vale na sessão
        w.settings.failing = false;
        w.flipObserver("COUNTER");
        assertEquals("", w.model().feedback().notice());
    }

    @Test
    void validationNoticeHasPriorityOverSaveWarningAndPersistsTheWarningAfter() {
        w.state.failing = true;
        w.add("a");
        w.add("");
        assertEquals("Escreva um título para a tarefa", w.model().feedback().notice());
        w.runScheduled();
        assertTrue(w.model().feedback().notice().startsWith("Falha ao salvar"));
    }

    // ------------------------------------------------------------ tolerância

    @Test
    void everyPanelModeRendersFromRealDataWithoutError() {
        w.add("a");
        w.toggle(0);
        for (PanelMode mode : PanelMode.values()) {
            w.go(mode);
            ScreenModel m = w.model();
            assertEquals(mode, m.mode());
            assertNotEquals(null, m.views());
        }
    }
}
