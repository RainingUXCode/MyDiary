package com.observernotes.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenModelTest {

    @Test
    void emptyModelIsHonest() {
        ScreenModel m = ScreenModel.empty();
        assertTrue(m.tasks().isEmpty());
        assertTrue(m.history().isEmpty());
        assertEquals(0, m.doneCount());
        assertEquals(3, m.observers().size());
        assertTrue(m.observers().stream().allMatch(ObserverCardModel::enabled));
        assertEquals(PanelMode.TASKS, m.mode());
    }

    @Test
    void rejectsMoreThanSixTasks() {
        List<TaskRowModel> seven = Collections.nCopies(7, new TaskRowModel("x", false));
        assertThrows(IllegalArgumentException.class, () -> new ScreenModel(PanelMode.TASKS, seven, List.of(),
                List.of(), LittleNotesModel.empty(), "a", "b",
                com.observernotes.ui.model.AchievementTier.NONE, "", false));
    }

    @Test
    void listsAreDefensiveCopies() {
        java.util.ArrayList<TaskRowModel> src = new java.util.ArrayList<>();
        src.add(new TaskRowModel("a", true));
        ScreenModel m = new ScreenModel(PanelMode.TASKS, src, List.of(), List.of(), LittleNotesModel.empty(),
                "a", "b", com.observernotes.ui.model.AchievementTier.NONE, "", false);
        src.add(new TaskRowModel("b", false));
        assertEquals(1, m.tasks().size());
        assertThrows(UnsupportedOperationException.class, () -> m.tasks().add(new TaskRowModel("c", false)));
    }

    @Test
    void withModeKeepsEverythingElse() {
        ScreenModel m = ScreenModel.empty().withMode(PanelMode.HISTORY);
        assertEquals(PanelMode.HISTORY, m.mode());
        assertEquals(3, m.observers().size());
    }

    @Test
    void doneCountCountsOnlyDone() {
        ScreenModel m = new ScreenModel(PanelMode.TASKS, List.of(new TaskRowModel("a", true),
                new TaskRowModel("b", false), new TaskRowModel("c", true)), List.of(), List.of(),
                LittleNotesModel.empty(), "a", "b", com.observernotes.ui.model.AchievementTier.NONE, "", false);
        assertEquals(2, m.doneCount());
    }
}
