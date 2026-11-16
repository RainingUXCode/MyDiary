package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryScrollTest {

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

    private void events(int pairs) {
        for (int i = 0; i < pairs; i++) {
            w.add("t" + i);
            w.delete(w.model().tasks().size() - 1);
        }
    }

    private void scroll(String delta) {
        w.controller.handle(new UiAction(UiAction.Type.SCROLL_HISTORY, delta));
    }

    @Test
    void allOldEventsAreAvailableNotJustTheLatestSix() {
        events(5);                                                       // 10 eventos
        w.go(PanelMode.HISTORY);
        ScreenModel m = w.model();
        assertEquals(10, m.history().size());
        assertEquals(0, m.historyOffset());
        assertEquals("Excluída: t4", m.history().get(0).text());
        assertEquals("Criada: t0", m.history().get(9).text());
    }

    @Test
    void scrollMovesClampsAndSupportsHomeEnd() {
        events(5);
        w.go(PanelMode.HISTORY);
        scroll("3");
        assertEquals(3, w.model().historyOffset());
        scroll("99");
        assertEquals(4, w.model().historyOffset());                      // 10 - 6
        scroll("-99");
        assertEquals(0, w.model().historyOffset());
        scroll("end");
        assertEquals(4, w.model().historyOffset());
        scroll("home");
        assertEquals(0, w.model().historyOffset());
        scroll("lixo");
        assertEquals(0, w.model().historyOffset());
    }

    @Test
    void fewEntriesNeverScroll() {
        events(2);
        w.go(PanelMode.HISTORY);
        scroll("5");
        assertEquals(0, w.model().historyOffset());
    }

    @Test
    void scrollOutsideTheHistoryPanelIsIgnored() {
        events(5);
        scroll("3");
        w.go(PanelMode.HISTORY);
        assertEquals(0, w.model().historyOffset());
    }

    @Test
    void scrollingNeverWritesToDiskOrChangesData() {
        events(5);
        w.go(PanelMode.HISTORY);
        int saves = w.state.saves;
        scroll("2");
        scroll("end");
        assertEquals(saves, w.state.saves);
        assertEquals(10, w.core.history().entries().size());
    }

    @Test
    void clearResetsTheScrollAndKeepsClearWorking() {
        events(5);
        w.go(PanelMode.HISTORY);
        scroll("end");
        w.controller.handle(UiAction.of(UiAction.Type.CLEAR));
        assertTrue(w.model().history().isEmpty());
        assertEquals(0, w.model().historyOffset());
        w.restart();
        assertTrue(w.model().history().isEmpty());
    }

    @Test
    void offsetStaysValidWhenTheHistoryShrinks() {
        events(5);
        w.go(PanelMode.HISTORY);
        scroll("end");
        w.controller.handle(UiAction.of(UiAction.Type.CLEAR));
        w.add("a");
        assertEquals(0, w.model().historyOffset());
    }

    @Test
    void persistedHistoryLimitOfTwoHundredIsStillEnforcedAndScrollable() {
        events(105);                                                     // 210 eventos gerados
        w.go(PanelMode.HISTORY);
        assertEquals(200, w.model().history().size());
        scroll("end");
        assertEquals(194, w.model().historyOffset());
        w.restart();
        w.go(PanelMode.HISTORY);
        assertEquals(200, w.model().history().size());
        assertEquals(0, w.model().historyOffset());
    }
}
