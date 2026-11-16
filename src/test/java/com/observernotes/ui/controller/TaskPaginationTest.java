package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.support.TempFiles;
import com.observernotes.ui.Layout;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.ButtonId;
import com.observernotes.ui.widget.UiAction;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verificações localizadas da paginação, sem alterar eventos ou pontuação. */
class TaskPaginationTest {

    private Path dir;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        world = new World(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    @Test
    void seventhTaskCreatesAndSelectsSecondPageWhileSummaryRemainsGlobal() {
        add(7);
        var model = world.model();
        assertEquals(1, model.taskPages().current());
        assertEquals(2, model.taskPages().count());
        assertEquals(7, model.taskPages().totalTasks());
        assertEquals(1, model.tasks().size());
        assertEquals("t7", model.tasks().get(0).title());
        assertEquals(7, model.views().total());
    }

    @Test
    void navigationOnlyChangesTheVisibleSliceAndCreatesNoHistoryEvent() {
        add(8);
        int history = world.core.history().entries().size();
        world.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS));
        assertEquals(0, world.model().taskPages().current());
        assertEquals(6, world.model().tasks().size());
        assertEquals("t1", world.model().tasks().get(0).title());
        world.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_NEXT));
        assertEquals(1, world.model().taskPages().current());
        assertEquals(history, world.core.history().entries().size());
    }

    @Test
    void completionAndIdsRemainGlobalAcrossPages() {
        add(7);
        String lastId = world.model().tasks().get(0).id();
        world.toggle(0);
        world.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS));
        assertEquals(1, world.model().views().done());
        assertTrue(world.core.service().tasks().stream()
                .anyMatch(t -> t.id().toString().equals(lastId) && t.completed()));
    }

    @Test
    void selectedPageIsPersistedAndEmptyLastPageCollapsesAfterDeletion() {
        add(7);
        world.restart();
        assertEquals(1, world.model().taskPages().current());
        world.delete(0);
        assertEquals(0, world.model().taskPages().current());
        assertEquals(1, world.model().taskPages().count());
        assertEquals(6, world.model().taskPages().totalTasks());
        world.restart();
        assertEquals(0, world.model().taskPages().current());
        assertEquals(6, world.core.service().tasks().size());
    }

    @Test
    void arrowsStayCenteredInTheFreeGapAndRespectPageLimits() {
        assertEquals(false, Layout.MAIN_RIBBON.intersects(Layout.TASK_PAGE_PREVIOUS));
        assertEquals(false, Layout.MAIN_BADGE.intersects(Layout.TASK_PAGE_NEXT));
        double gapCenter = (Layout.MAIN_RIBBON.getMaxX() + Layout.MAIN_BADGE.getX()) / 2;
        double controlsCenter = (Layout.TASK_PAGE_PREVIOUS.getX() + Layout.TASK_PAGE_NEXT.getMaxX()) / 2;
        assertEquals(gapCenter, controlsCenter, 1.0);

        add(7);
        assertEquals(ButtonId.TASK_PAGE_PREVIOUS,
                MainScene.hitTest(center(Layout.TASK_PAGE_PREVIOUS), world.model()));
        assertEquals(null, MainScene.hitTest(center(Layout.TASK_PAGE_NEXT), world.model()));
        world.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS));
        assertEquals(null, MainScene.hitTest(center(Layout.TASK_PAGE_PREVIOUS), world.model()));
        assertEquals(ButtonId.TASK_PAGE_NEXT,
                MainScene.hitTest(center(Layout.TASK_PAGE_NEXT), world.model()));
    }

    private static Point2D center(java.awt.geom.Rectangle2D r) {
        return new Point2D.Double(r.getCenterX(), r.getCenterY());
    }

    private void add(int count) {
        for (int i = 1; i <= count; i++) {
            world.add("t" + i);
        }
    }
}
