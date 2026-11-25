package com.observernotes.ui.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.Layout;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.LittleNotesModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.widget.UiAction;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainSceneActionTest {

    private static Point2D center(Rectangle2D r) {
        return new Point2D.Double(r.getCenterX(), r.getCenterY());
    }

    private static ScreenModel tasks(String... ids) {
        return new ScreenModel(PanelMode.TASKS, java.util.Arrays.stream(ids)
                .map(i -> new TaskRowModel(i, "título " + i, false)).toList(), ScreenModel.defaultObservers(),
                List.of(), LittleNotesModel.empty(), "Conquistas", "nenhuma ainda",
                com.observernotes.ui.model.AchievementTier.NONE, "", false);
    }

    @Test
    void checkboxAndCloseButtonReturnTheKeyOfTheirOwnRow() {
        ScreenModel m = tasks("id-a", "id-b", "id-c");
        for (int i = 0; i < 3; i++) {
            Rectangle2D row = Layout.row(i);
            String id = m.tasks().get(i).id();
            assertEquals(new UiAction(UiAction.Type.TOGGLE_TASK, id),
                    MainScene.actionAt(center(MainScene.checkRect(row)), m));
            assertEquals(new UiAction(UiAction.Type.DELETE_TASK, id),
                    MainScene.actionAt(center(MainScene.closeRect(row)), m));
        }
    }

    @Test
    void afterTheListChangesTheSamePointHitsTheTaskNowInThatRow() {
        ScreenModel before = tasks("a", "b", "c");
        ScreenModel after = tasks("b", "c");
        Point2D row0 = center(MainScene.checkRect(Layout.row(0)));
        assertEquals("a", MainScene.actionAt(row0, before).key());
        assertEquals("b", MainScene.actionAt(row0, after).key());
        assertNull(MainScene.actionAt(center(MainScene.checkRect(Layout.row(2))), after));   // linha vazia
    }

    @Test
    void titleTextAndEmptySlotsAreNotClickable() {
        ScreenModel m = tasks("a");
        Rectangle2D row = Layout.row(0);
        assertNull(MainScene.actionAt(new Point2D.Double(row.getCenterX(), row.getCenterY()), m));
        assertNull(MainScene.actionAt(center(MainScene.checkRect(Layout.row(3))), m));
    }

    @Test
    void observerTogglesReturnTheObserverKey() {
        ScreenModel m = MockupSample.screen(PanelMode.OBSERVERS);
        for (int i = 0; i < 3; i++) {
            assertEquals(new UiAction(UiAction.Type.TOGGLE_OBSERVER, m.observers().get(i).key()),
                    MainScene.actionAt(center(MainScene.toggleRect(MainScene.cardRect(i))), m));
        }
        assertNull(MainScene.actionAt(center(MainScene.cardRect(0)), m));
    }

    @Test
    void clearOnlyExistsInTheHistoryPanel() {
        Point2D clear = center(Layout.CLEAR);
        assertEquals(UiAction.Type.CLEAR, MainScene.actionAt(clear, MockupSample.screen(PanelMode.HISTORY)).type());
        assertNull(MainScene.actionAt(clear, MockupSample.screen(PanelMode.TASKS)));
        assertNull(MainScene.actionAt(clear, MockupSample.screen(PanelMode.OBSERVERS)));
    }

    @Test
    void taskControlsAreNotActiveInOtherPanels() {
        Point2D check = center(MainScene.checkRect(Layout.row(0)));
        assertNull(MainScene.actionAt(check, MockupSample.screen(PanelMode.HISTORY)));
    }

    @Test
    void fixedButtonsMapToTheirActions() {
        ScreenModel m = ScreenModel.empty();
        assertEquals(UiAction.Type.NAV_LIST, MainScene.actionAt(center(Layout.NAV_LIST), m).type());
        assertEquals(UiAction.Type.NAV_ALERTS, MainScene.actionAt(center(Layout.NAV_ALERTS), m).type());
        assertEquals(UiAction.Type.NAV_RECORD, MainScene.actionAt(center(Layout.NAV_RECORD), m).type());
        assertEquals(UiAction.Type.ADD, MainScene.actionAt(center(Layout.ADD), m).type());
        assertNull(MainScene.actionAt(center(Layout.NEW_BADGE), m));
        assertNull(MainScene.actionAt(center(Layout.PLAYER_NEXT), m));
    }

    @Test
    void littleNotesHaveSeparateEditDeleteAndAddTargets() {
        ScreenModel base = MockupSample.screen(PanelMode.TASKS);
        var notes = new LittleNotesModel(List.of(new LittleNotesModel.Item("n1", "Beber água")), "");
        ScreenModel m = new ScreenModel(base.mode(), base.tasks(), base.observers(), base.history(), notes,
                base.achievementLabel(), base.achievementState(), base.achievementTier(), base.footerMessage(),
                base.newAchievement(), base.views(), base.feedback(), base.historyOffset(), base.taskPages());
        Rectangle2D card = MainScene.noteRect(0);
        assertEquals(new UiAction(UiAction.Type.NOTE_EDIT, "n1"),
                MainScene.actionAt(new Point2D.Double(card.getX() + 20, card.getCenterY()), m));
        assertEquals(UiAction.Type.NOTE_DELETE,
                MainScene.actionAt(new Point2D.Double(card.getMaxX() - 11, card.getY() + 11), m).type());
        assertEquals(UiAction.Type.NOTE_ADD, MainScene.actionAt(center(MainScene.noteAddRect(notes)), m).type());
    }
}
