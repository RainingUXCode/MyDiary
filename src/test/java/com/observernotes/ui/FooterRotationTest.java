package com.observernotes.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.tools.MockupSample;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.ScreenModel;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Alternância de 6 s do rodapé: notificação ↔ "♪ Now Playing: Nome" (só estado visual, sem eventos de histórico). */
class FooterRotationTest {

    private static final long P = FooterRotation.PERIOD_MS;
    private static final long F = FooterRotation.FADE_MS;

    private static ScreenModel screen(String footer, String notice) {
        ScreenModel s = MockupSample.screen(PanelMode.TASKS);
        return new ScreenModel(s.mode(), s.tasks(), s.observers(), s.history(), s.starsLit(), s.starsTotal(),
                s.achievementLabel(), s.achievementState(), s.achievementTier(), footer, s.newAchievement(),
                s.views(), new ScreenModel.Feedback(notice, false, false, ""), 0);
    }

    private static PlayerModel player(boolean playing) {
        return new PlayerModel(true, playing, !playing, "Sweet Morning",
                List.of(new PlayerModel.Row("/m/a.mp3", "Sweet Morning", true, true)), 0.7, false, false, 0);
    }

    private static String text(FooterRotation.View v) {
        return v.current().text();
    }

    @Test
    void withoutMusicOnlyTheNotificationIsShownForever() {
        FooterRotation r = new FooterRotation();
        ScreenModel m = screen("Tarefa concluída!", "");
        assertEquals("Tarefa concluída!", text(r.update(0, m, PlayerModel.NONE)));
        assertEquals("Tarefa concluída!", text(r.update(60 * P, m, player(false))));   // pausada: só notificação
        assertEquals("Nenhum evento ainda", text(new FooterRotation().update(0, screen("", ""), PlayerModel.NONE)));
    }

    @Test
    void playingAlternatesEverySixSecondsAndPausingReturnsToTheNotification() {
        FooterRotation r = new FooterRotation();
        ScreenModel m = screen("Tarefa concluída!", "");
        r.update(0, m, PlayerModel.NONE);
        long t = 20 * P;                                                       // notificação já é antiga
        assertEquals("♪ Now Playing: Sweet Morning", text(r.update(t, m, player(true))));
        assertEquals("♪ Now Playing: Sweet Morning", text(r.update(t + P - 1, m, player(true))));
        assertEquals("Tarefa concluída!", text(r.update(t + P, m, player(true))));
        assertEquals("♪ Now Playing: Sweet Morning", text(r.update(t + 2 * P, m, player(true))));
        assertEquals("Tarefa concluída!", text(r.update(t + 2 * P + 10, m, player(false))));   // pausou
    }

    @Test
    void aNewNotificationShowsAtOnceAndStaysAtLeastSixSeconds() {
        FooterRotation r = new FooterRotation();
        r.update(0, screen("Antiga", ""), player(true));
        long t = 30 * P;
        r.update(t, screen("Antiga", ""), player(true));                        // música no ar
        long n = t + 100;
        assertEquals("Nova!", text(r.update(n, screen("Nova!", ""), player(true))));
        assertEquals("Nova!", text(r.update(n + P - 1, screen("Nova!", ""), player(true))));
        assertTrue(text(r.update(n + P, screen("Nova!", ""), player(true))).startsWith("♪ Now Playing"));
    }

    @Test
    void aTransientNoticeIsLatchedForSixSecondsEvenAfterItExpires() {
        FooterRotation r = new FooterRotation();
        r.update(0, screen("Evento", ""), player(true));
        long t = 30 * P;
        r.update(t, screen("Evento", ""), player(true));
        assertEquals("Aviso", text(r.update(t + 10, screen("Evento", "Aviso"), player(true))));
        assertEquals("Aviso", text(r.update(t + 4010, screen("Evento", ""), player(true))));   // aviso do app expirou
    }

    @Test
    void changesFadeSmoothlyAndTheRotationReportsWhenItIsAnimating() {
        FooterRotation r = new FooterRotation();
        ScreenModel m = screen("Evento", "");
        r.update(0, m, PlayerModel.NONE);
        long t = 30 * P;
        var v = r.update(t, m, player(true));
        assertTrue(v.fading());
        assertTrue(r.animating(t + F - 1));
        assertFalse(r.update(t + F + 1, m, player(true)).fading());
        assertFalse(r.animating(t + F + 1));
    }
}
