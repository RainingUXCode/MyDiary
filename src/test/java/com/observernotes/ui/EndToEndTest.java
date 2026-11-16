package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.app.Bootstrap;
import com.observernotes.support.TempFiles;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.controller.AppController;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.scene.MainScene;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Do clique/tecla até o disco e de volta: canvas real (sem janela), controlador e núcleo com
 * arquivos reais. Eventos sintéticos; não substitui um teste manual com a janela aberta.
 */
class EndToEndTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private Path dir;
    private Bootstrap.Started started;
    private NotebookCanvas canvas;
    private AppController controller;

    private void launch(int w, int h) {
        started = Bootstrap.start(dir, Clock.systemUTC());
        canvas = new NotebookCanvas(new AssetCatalog());
        canvas.setSize(w, h);
        AppController[] holder = new AppController[1];
        controller = new AppController(started.core(), started.failures(), canvas.input(), ZoneOffset.UTC,
                () -> canvas.setModel(holder[0].model()), (t, ms) -> { });
        holder[0] = controller;
        canvas.setActionListener(controller::handle);
        canvas.setModel(controller.model());
    }

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    private int[] at(Rectangle2D logical) {
        double s = Math.min(canvas.getWidth() / (double) Layout.WIDTH, canvas.getHeight() / (double) Layout.HEIGHT);
        double ox = (canvas.getWidth() - Layout.WIDTH * s) / 2.0;
        double oy = (canvas.getHeight() - Layout.HEIGHT * s) / 2.0;
        return new int[] {(int) Math.round(ox + logical.getCenterX() * s), (int) Math.round(oy + logical.getCenterY() * s)};
    }

    private void click(Rectangle2D logical) {
        int[] p = at(logical);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, p[0], p[1], 0, false));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, p[0], p[1], 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, p[0], p[1], 1, false, MouseEvent.BUTTON1));
    }

    private void typeText(String s) {
        for (char c : s.toCharArray()) {
            for (var l : canvas.getKeyListeners()) {
                l.keyTyped(new KeyEvent(canvas, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
            }
        }
    }

    private void enter() {
        for (var l : canvas.getKeyListeners()) {
            l.keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
        }
    }

    @Test
    void fullSessionWithMouseAndKeyboardThenRestart() {
        launch(1100, 584);
        click(Layout.TEXT_FIELD);
        assertTrue(canvas.input().focused());
        typeText("Estudar o Observer");
        enter();                                             // Enter cria
        typeText("Desenhar o diagrama");
        click(Layout.ADD);                                   // botão ADD cria
        assertEquals(2, canvas.model().tasks().size());

        click(MainScene.checkRect(Layout.row(1)));           // conclui a 2ª
        assertTrue(canvas.model().tasks().get(1).done());
        assertFalse(canvas.model().tasks().get(0).done());
        assertEquals(1, canvas.model().views().done());

        click(Layout.NAV_ALERTS);
        assertEquals(PanelMode.OBSERVERS, canvas.model().mode());
        click(MainScene.toggleRect(MainScene.cardRect(1)));  // desliga o Contador
        assertFalse(canvas.model().views().counterOn());
        click(Layout.NAV_RECORD);
        assertEquals(PanelMode.HISTORY, canvas.model().mode());
        assertEquals(3, canvas.model().history().size());
        click(Layout.NAV_LIST);
        click(MainScene.closeRect(Layout.row(0)));           // exclui a 1ª
        assertEquals(1, canvas.model().tasks().size());

        // reinicia o app sobre os mesmos arquivos
        launch(700, 400);
        assertEquals(1, canvas.model().tasks().size());
        assertEquals("Desenhar o diagrama", canvas.model().tasks().get(0).title());
        assertTrue(canvas.model().tasks().get(0).done());
        assertFalse(canvas.model().views().counterOn());
        assertEquals("1 de 16", canvas.model().achievementState());

        click(Layout.NAV_RECORD);
        click(Layout.CLEAR);
        assertTrue(canvas.model().history().isEmpty());
        assertEquals(1, canvas.model().tasks().size());
    }

    @Test
    void clickOnTheCheckboxOfAnEmptyRowOrOnTheTitleDoesNothing() {
        launch(1397, 742);
        canvas.input().paste("a");
        controller.handle(com.observernotes.ui.widget.UiAction.of(com.observernotes.ui.widget.UiAction.Type.ADD));
        click(MainScene.checkRect(Layout.row(3)));
        click(Layout.row(0));
        assertFalse(canvas.model().tasks().get(0).done());
    }

    @Test
    void pressOnOneRowAndReleaseOnAnotherDoesNothing() {
        launch(1397, 742);
        for (String t : new String[] {"a", "b"}) {
            canvas.input().paste(t);
            controller.handle(com.observernotes.ui.widget.UiAction.of(com.observernotes.ui.widget.UiAction.Type.ADD));
        }
        int[] p0 = at(MainScene.checkRect(Layout.row(0)));
        int[] p1 = at(MainScene.checkRect(Layout.row(1)));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, p0[0], p0[1], 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, p1[0], p1[1], 1, false, MouseEvent.BUTTON1));
        assertEquals(0, canvas.model().views().done());
    }

    @Test
    void enterWithEmptyFieldWarnsAndSeventhTaskOpensASecondPage() {
        launch(1397, 742);
        click(Layout.TEXT_FIELD);
        enter();
        assertEquals("Escreva um título para a tarefa", canvas.model().feedback().notice());
        for (int i = 1; i <= 6; i++) {
            typeText("t" + i);
            enter();
        }
        assertEquals(6, canvas.model().tasks().size());
        typeText("extra");
        enter();
        assertTrue(canvas.input().isEmpty());
        assertEquals(1, canvas.model().tasks().size());
        assertEquals("extra", canvas.model().tasks().get(0).title());
        assertEquals(1, canvas.model().taskPages().current());
    }

    @Test
    void everyScreenRendersFromRealDataAtSeveralSizes() {
        launch(1100, 584);
        canvas.input().paste("Tarefa real");
        controller.handle(com.observernotes.ui.widget.UiAction.of(com.observernotes.ui.widget.UiAction.Type.ADD));
        controller.handle(new com.observernotes.ui.widget.UiAction(com.observernotes.ui.widget.UiAction.Type.TOGGLE_TASK,
                canvas.model().tasks().get(0).id()));
        for (PanelMode mode : PanelMode.values()) {
            controller.handle(com.observernotes.ui.widget.UiAction.of(switch (mode) {
                case TASKS -> com.observernotes.ui.widget.UiAction.Type.NAV_LIST;
                case OBSERVERS -> com.observernotes.ui.widget.UiAction.Type.NAV_ALERTS;
                case HISTORY -> com.observernotes.ui.widget.UiAction.Type.NAV_RECORD;
            }));
            for (int[] sz : new int[][] {{700, 400}, {1397, 742}, {1920, 1080}}) {
                BufferedImage img = new BufferedImage(sz[0], sz[1], BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                canvas.paintScene(g, sz[0], sz[1]);
                g.dispose();
                assertEquals(sz[0], img.getWidth());
            }
        }
    }
}
