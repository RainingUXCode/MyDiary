package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.assets.AssetCatalog;

import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.FocusRing;
import com.observernotes.ui.widget.UiAction;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Interface do player: cliques, Tab/teclado, roda do mouse e desenho (eventos sintéticos, sem janela real). */
class PlayerCanvasTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final String A = "/m/a.mp3";
    private static final String B = "/m/b.mp3";

    private final AtomicLong clock = new AtomicLong(5_000);
    private final List<UiAction> actions = new ArrayList<>();
    private NotebookCanvas canvas;

    private static PlayerModel player(int tracks, boolean playing, boolean panel, int offset) {
        List<PlayerModel.Row> rows = new ArrayList<>();
        for (int i = 0; i < tracks; i++) {
            String p = i == 0 ? A : i == 1 ? B : "/m/t" + i + ".mp3";
            rows.add(new PlayerModel.Row(p, "faixa " + i, i != 2, i == 0));
        }
        return new PlayerModel(true, playing, false, tracks > 0 ? "faixa 0" : "", rows, 0.7, false, panel, offset);
    }

    private void start(ScreenModel m, PlayerModel p) {
        canvas = new NotebookCanvas(new AssetCatalog(), clock::get);
        canvas.setSize(1397, 742);
        canvas.setModel(m);
        canvas.setPlayer(p);
        canvas.setActionListener(actions::add);
    }

    private static Point2D center(Rectangle2D r) {
        return new Point2D.Double(r.getCenterX(), r.getCenterY());
    }

    private static int[] screen(Rectangle2D r) {
        double s = Math.min(1397 / (double) Layout.WIDTH, 742 / (double) Layout.HEIGHT);
        return new int[] {(int) Math.round((1397 - Layout.WIDTH * s) / 2 + r.getCenterX() * s),
            (int) Math.round((742 - Layout.HEIGHT * s) / 2 + r.getCenterY() * s)};
    }

    private BufferedImage shot() {
        BufferedImage img = new BufferedImage(1397, 742, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        canvas.paintScene(g, 1397, 742);
        g.dispose();
        return img;
    }

    private static int diff(BufferedImage a, BufferedImage b, Rectangle2D region) {
        int n = 0;
        int x0 = (int) region.getX();
        int y0 = (int) region.getY();
        int x1 = (int) region.getMaxX();
        int y1 = (int) region.getMaxY();
        for (int y = Math.max(0, y0); y < Math.min(742, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(1397, x1); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static final Rectangle2D WHOLE = new Rectangle2D.Double(0, 0, 1397, 742);

    private void press(int code) {
        canvas.handleKey(code, false, false);
    }

    // ---------------------------------------------------------------- cliques

    @Test
    void footerPlayerButtonsAreClickableOnlyWhenThereIsAPlayer() {
        ScreenModel m = MockupSample.screen(PanelMode.TASKS);
        assertNull(MainScene.actionAt(center(Layout.PLAYER_PAUSE), m));
        assertNull(MainScene.actionAt(center(Layout.NEW_BADGE), m));
        PlayerModel p = player(3, false, false, 0);
        assertEquals(UiAction.Type.PLAYER_TOGGLE, MainScene.actionAt(center(Layout.PLAYER_PAUSE), m, p).type());
        assertEquals(UiAction.Type.PLAYER_PREVIOUS, MainScene.actionAt(center(Layout.PLAYER_PREVIOUS), m, p).type());
        assertEquals(UiAction.Type.PLAYER_NEXT, MainScene.actionAt(center(Layout.PLAYER_NEXT), m, p).type());
        assertEquals(UiAction.Type.PLAYER_PANEL, MainScene.actionAt(center(Layout.NEW_BADGE), m, p).type());   // "♪ Playlist"
        assertNull(MainScene.actionAt(center(Layout.ADD_MUSIC), m, p));      // "+ Música" só existe com a playlist aberta
        PlayerModel open = player(3, false, true, 0);
        assertEquals(UiAction.Type.PLAYER_ADD, MainScene.actionAt(center(Layout.ADD_MUSIC), m, open).type());
    }

    @Test
    void openPlaylistCoversTheCentralPanelAndActsByPath() {
        ScreenModel m = MockupSample.screen(PanelMode.TASKS);
        PlayerModel p = player(4, true, true, 0);
        var row = MainScene.actionAt(center(Layout.row(0)), m, p);
        assertEquals(new UiAction(UiAction.Type.PLAYER_PLAY, A), row);   // não é a tarefa que está "por baixo"
        assertEquals(new UiAction(UiAction.Type.PLAYER_REMOVE, B),
                MainScene.actionAt(center(MainScene.playlistRemoveRect(1)), m, p));
        assertEquals(new UiAction(UiAction.Type.PLAYER_UP, B),
                MainScene.actionAt(center(MainScene.playlistUpRect(1)), m, p));
        assertEquals(new UiAction(UiAction.Type.PLAYER_DOWN, B),
                MainScene.actionAt(center(MainScene.playlistDownRect(1)), m, p));
        assertEquals(UiAction.Type.PLAYER_REPEAT, MainScene.actionAt(center(MainScene.repeatRect()), m, p).type());
        assertEquals(new UiAction(UiAction.Type.PLAYER_VOLUME, "+"),
                MainScene.actionAt(center(MainScene.volumePlusRect()), m, p));
        assertEquals(new UiAction(UiAction.Type.PLAYER_VOLUME, "-"),
                MainScene.actionAt(center(MainScene.volumeMinusRect()), m, p));
        Rectangle2D bar = MainScene.volumeBarRect();
        var set = MainScene.actionAt(new Point2D.Double(bar.getX() + bar.getWidth() * 0.5, bar.getCenterY()), m, p);
        assertEquals("set:0.50", set.key());
        assertNull(MainScene.actionAt(center(Layout.row(4)), m, player(2, true, true, 0)));   // linha livre
    }

    @Test
    void navAndFieldStillWorkWithThePlaylistOpen() {
        ScreenModel m = MockupSample.screen(PanelMode.TASKS);
        PlayerModel p = player(4, true, true, 0);
        assertEquals(UiAction.Type.NAV_ALERTS, MainScene.actionAt(center(Layout.NAV_ALERTS), m, p).type());
        assertEquals(UiAction.Type.ADD, MainScene.actionAt(center(Layout.ADD), m, p).type());
    }

    // ---------------------------------------------------------------- teclado

    @Test
    void tabReachesPlayerControlsLastAndPlaylistItemsReplaceTaskItems() {
        ScreenModel m = MockupSample.screen(PanelMode.TASKS);
        List<UiAction> closed = FocusRing.order(m, player(3, false, false, 0));
        assertEquals(UiAction.Type.PLAYER_PANEL, closed.get(closed.size() - 1).type());
        assertTrue(closed.contains(UiAction.of(UiAction.Type.PLAYER_TOGGLE)));
        assertTrue(closed.stream().anyMatch(a -> a.type() == UiAction.Type.TOGGLE_TASK));
        List<UiAction> open = FocusRing.order(m, player(3, false, true, 0));
        assertTrue(open.stream().noneMatch(a -> a.type() == UiAction.Type.TOGGLE_TASK));
        assertTrue(open.contains(new UiAction(UiAction.Type.PLAYER_REMOVE, B)));
        assertTrue(open.contains(new UiAction(UiAction.Type.PLAYER_VOLUME, "+")));
        assertEquals(FocusRing.order(m), FocusRing.order(m, PlayerModel.NONE));   // sem player: igual à 4C.1
    }

    @Test
    void tabWalksEveryControlOnceIncludingThePlayer() {
        start(MockupSample.screen(PanelMode.TASKS), player(3, false, false, 0));
        List<UiAction> order = FocusRing.order(canvas.model(), canvas.player());
        for (UiAction expected : order) {
            press(KeyEvent.VK_TAB);
            assertEquals(expected, canvas.focusRing().current());
        }
        assertTrue(actions.isEmpty());
    }

    @Test
    void playerButtonsRespondToSpaceAndEnterButRemoveOnlyToSpace() {
        start(MockupSample.screen(PanelMode.TASKS), player(3, true, true, 0));
        canvas.focusRing().set(UiAction.of(UiAction.Type.PLAYER_TOGGLE));
        press(KeyEvent.VK_ENTER);
        press(KeyEvent.VK_SPACE);
        assertEquals(2, actions.size());
        actions.clear();
        UiAction remove = new UiAction(UiAction.Type.PLAYER_REMOVE, B);
        canvas.focusRing().set(remove);
        press(KeyEvent.VK_ENTER);
        assertTrue(actions.isEmpty());                                   // Enter nunca remove
        press(KeyEvent.VK_SPACE);
        assertEquals(List.of(remove), actions);
    }

    @Test
    void escapeClosesThePlaylistOnlyWhenNothingHasFocus() {
        start(MockupSample.screen(PanelMode.TASKS), player(3, false, true, 0));
        canvas.focusRing().set(UiAction.of(UiAction.Type.PLAYER_REPEAT));
        press(KeyEvent.VK_ESCAPE);                                       // primeiro só tira o foco
        assertTrue(actions.isEmpty());
        press(KeyEvent.VK_ESCAPE);
        assertEquals(List.of(UiAction.of(UiAction.Type.PLAYER_PANEL)), actions);
    }

    @Test
    void arrowKeysScrollThePlaylistWhenItIsOpen() {
        start(MockupSample.screen(PanelMode.HISTORY), player(8, false, true, 0));
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_END);
        assertEquals(List.of(new UiAction(UiAction.Type.PLAYER_SCROLL, "1"),
                new UiAction(UiAction.Type.PLAYER_SCROLL, "end")), actions);
    }

    @Test
    void wheelOverThePillChangesVolumeAndOverThePlaylistScrolls() {
        start(MockupSample.screen(PanelMode.TASKS), player(8, false, true, 0));
        int[] pill = screen(Layout.PLAYER_PILL);
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL, 0, 0, pill[0], pill[1], 0,
                false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL, 0, 0, pill[0], pill[1], 0,
                false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        int[] panel = screen(Layout.MAIN_PANEL);
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseWheelEvent.MOUSE_WHEEL, 0, 0, panel[0], panel[1], 0,
                false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        assertEquals(List.of(new UiAction(UiAction.Type.PLAYER_VOLUME, "+"), new UiAction(UiAction.Type.PLAYER_VOLUME, "-"),
                new UiAction(UiAction.Type.PLAYER_SCROLL, "1")), actions);
    }

    // ---------------------------------------------------------------- desenho

    @Test
    void withoutAPlayerTheFooterIsExactlyTheApproved4C1Look() {
        start(MockupSample.screen(PanelMode.TASKS), PlayerModel.NONE);
        BufferedImage none = shot();
        canvas = new NotebookCanvas(new AssetCatalog(), clock::get);
        canvas.setSize(1397, 742);
        canvas.setModel(MockupSample.screen(PanelMode.TASKS));
        assertEquals(0, diff(none, shot(), WHOLE));
    }

    @Test
    void thePlayerChangesOnlyTheFooterAndAddsPlaylistWhenOpen() {
        start(MockupSample.screen(PanelMode.TASKS), PlayerModel.NONE);
        BufferedImage base = shot();
        canvas.setPlayer(player(3, false, false, 0));
        BufferedImage withPlayer = shot();
        assertTrue(diff(base, withPlayer, Layout.NEW_BADGE) > 100);          // "♪ Playlist" no botão amarelo
        assertTrue(diff(base, withPlayer, Layout.PLAYER_PILL) > 50);        // triângulo de tocar no lugar de pausar
        assertEquals(0, diff(base, withPlayer, Layout.MAIN_PANEL));
        canvas.setPlayer(player(3, false, true, 0));
        assertTrue(diff(withPlayer, shot(), Layout.MAIN_PANEL) > 500);
    }

    @Test
    void footerHasOneInfoAreaAndNotificationAloneWhenNothingPlays() {
        ScreenModel s = MockupSample.screen(PanelMode.TASKS);
        ScreenModel noisy = new ScreenModel(s.mode(), s.tasks(), s.observers(), s.history(), s.starsLit(), s.starsTotal(),
                s.achievementLabel(), s.achievementState(), s.achievementTier(), s.footerMessage(), s.newAchievement(),
                s.views(), new ScreenModel.Feedback("Limite de 6 tarefas", true, false, ""), 0);
        start(noisy, PlayerModel.NONE);
        BufferedImage base = shot();
        canvas.setPlayer(player(3, false, false, 0));                       // parado: o rodapé continua só com a notificação
        assertEquals(0, diff(base, shot(), Layout.FOOTER_MESSAGE));
        canvas.setPlayer(player(3, true, false, 0));                        // tocando: a notificação nova fica 6 s antes
        clock.addAndGet(1000);
        assertEquals(0, diff(base, shot(), Layout.FOOTER_MESSAGE));
    }

    @Test
    void playingAnimatesTheNoteAndMusicEffectsAppearThenVanish() {
        start(MockupSample.screen(PanelMode.TASKS), player(3, true, false, 0));
        shot();
        clock.addAndGet(FooterRotation.FADE_MS + 50);
        BufferedImage a = shot();                                           // tocando: começa com "♪ Now Playing"
        clock.addAndGet(FooterRotation.PERIOD_MS + FooterRotation.FADE_MS + 50);
        shot();
        clock.addAndGet(FooterRotation.FADE_MS + 50);
        assertTrue(diff(a, shot(), Layout.FOOTER_MESSAGE) > 100, "passados 6 s, volta à notificação");
        canvas.setPlayer(player(3, false, false, 0));
        BufferedImage idle = shot();
        canvas.effects().musicState(true);
        canvas.effects().musicTrack("Sunset Drive");
        clock.addAndGet(300);
        assertTrue(diff(idle, shot(), WHOLE) > 200);
        clock.addAndGet(4000);
        assertFalse(canvas.effects().isActive());
        assertEquals(0, diff(idle, shot(), WHOLE));
    }

    @Test
    void hoverAndFocusAreVisibleOnPlayerControls() {
        start(MockupSample.screen(PanelMode.TASKS), player(3, false, false, 0));
        BufferedImage base = shot();
        canvas.focusRing().set(UiAction.of(UiAction.Type.PLAYER_NEXT));
        assertTrue(diff(base, shot(), Layout.PLAYER_PILL) > 20);
        canvas.focusRing().set(UiAction.of(UiAction.Type.PLAYER_PANEL));
        assertTrue(diff(base, shot(), Layout.NEW_BADGE) > 20);
        canvas.focusRing().clear();
        int[] p = screen(Layout.PLAYER_NEXT);
        canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, p[0], p[1],
                0, false));
        assertTrue(diff(base, shot(), Layout.PLAYER_PILL) > 10, "hover no botão do player");
    }

    @Test
    void longTitlesEmptyListsAndScrollbarRenderSafely() {
        List<PlayerModel.Row> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(new PlayerModel.Row("/m/" + i, "Um título extremamente longo ".repeat(6) + i, i % 3 != 0, i == 4));
        }
        start(MockupSample.screen(PanelMode.OBSERVERS),
                new PlayerModel(true, true, false, rows.get(4).title(), rows, 1.0, true, true, 7));
        shot();
        canvas.setPlayer(player(0, false, true, 0));
        shot();
        canvas.setPlayer(new PlayerModel(true, false, false, "", List.of(), 0.0, false, false, 0));
        shot();
    }
}
