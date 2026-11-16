package com.observernotes.ui;

import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.ScreenModel;

/**
 * Área de informação única do rodapé: alterna a cada 6 s entre a última notificação de atividade e
 * "♪ Now Playing: Nome". É só estado visual: não cria eventos no histórico nem mexe em tarefas, Observer ou
 * conquistas. O relógio entra como parâmetro ({@code now}, em ms), então a lógica é testável sem esperar.
 * <ul>
 *   <li>sem música tocando (parada, pausada ou sem playlist): só a notificação;</li>
 *   <li>tocando: alterna; ao começar a tocar, o nome da música aparece na hora;</li>
 *   <li>notificação nova: aparece na hora e fica pelo menos 6 s antes de a alternância continuar;</li>
 *   <li>trocas com deslizamento vertical curto, como um pequeno elevador.</li>
 * </ul>
 */
public final class FooterRotation {

    public static final long PERIOD_MS = 6000;
    public static final long SLIDE_MS = 400;
    /** Mantido como alias para ferramentas e testes antigos. */
    public static final long FADE_MS = SLIDE_MS;

    public enum Kind { EVENT, WARNING, PLACEHOLDER, PLAYING }

    public record Line(String text, Kind kind) { }

    /** Linhas do elevador e o instante em que cada uma começou a ser exibida. */
    public record View(Line current, Line previous, double mix, long currentSince, long previousSince) {
        public View(Line current, Line previous, double mix) {
            this(current, previous, mix, 0, 0);
        }

        public boolean fading() {
            return previous != null && mix < 1;
        }
    }

    public static final String EMPTY = "Nenhum evento ainda";

    private String lastNotice = "";
    private String lastFooter;                         // null = ainda não viu nada
    private Line notification = new Line(EMPTY, Kind.PLACEHOLDER);
    private Line shown = notification;
    private Line previous;
    private long fadeStart = Long.MIN_VALUE / 2;
    private long shownSince;
    private long previousSince;
    private long since;
    private boolean showingMusic;
    private boolean wasPlaying;
    private boolean marqueeActive;

    public View update(long now, ScreenModel m, PlayerModel pm) {
        return update(now, m, pm, PERIOD_MS);
    }

    /** @param minimumVisibleMs duração mínima necessária para o letreiro atual completar um ciclo legível */
    public View update(long now, ScreenModel m, PlayerModel pm, long minimumVisibleMs) {
        long holdMs = Math.max(PERIOD_MS, minimumVisibleMs);
        marqueeActive = minimumVisibleMs > PERIOD_MS;
        boolean first = lastFooter == null;
        boolean fresh = false;
        String footer = m.footerMessage();
        if (lastFooter == null || !footer.equals(lastFooter)) {
            fresh = lastFooter != null;
            lastFooter = footer;
            notification = footer.isEmpty() ? new Line(EMPTY, Kind.PLACEHOLDER) : new Line(footer, Kind.EVENT);
        }
        String notice = m.feedback().notice();
        if (!notice.isEmpty() && !notice.equals(lastNotice)) {
            fresh = true;
            notification = new Line(notice, m.feedback().warning() ? Kind.WARNING : Kind.EVENT);
        }
        lastNotice = notice;
        if (first) {                                       // primeira leitura: já nasce desenhado, sem fade
            shown = notification;
            shownSince = now;
            fresh = !notice.isEmpty();                     // só um aviso conta como "notificação nova"
            since = now - PERIOD_MS;
        }

        boolean music = pm.available() && pm.playing() && pm.hasTracks() && !pm.title().isEmpty();
        Line musicLine = music ? new Line("♪ Now Playing: " + pm.title(), Kind.PLAYING) : null;

        if (fresh) {
            showingMusic = false;
            since = now;
            show(notification, now);
        } else if (!music) {
            if (showingMusic) {
                showingMusic = false;
                since = now - PERIOD_MS;               // ao voltar a tocar, o nome da música entra na hora
            }
            show(notification, now);
        } else {
            if (now - since >= holdMs) {
                // começou a tocar (e a notificação já passou dos 6 s): mostra a música; senão, alterna
                showingMusic = wasPlaying ? !showingMusic : true;
                since = now;
            }
            show(showingMusic ? musicLine : notification, now);
        }
        wasPlaying = music;
        double t = Math.max(0, Math.min(1, (now - fadeStart) / (double) FADE_MS));
        double mix = t * t * (3 - 2 * t);                         // suaviza o começo e o fim do fade
        return new View(shown, previous != null && t < 1 ? previous : null, t >= 1 ? 1 : mix,
                shownSince, previousSince);
    }

    private void show(Line next, long now) {
        if (!next.equals(shown)) {
            previous = shown;
            previousSince = shownSince;
            shown = next;
            shownSince = now;
            fadeStart = now;
        }
    }

    public Line shownLine() {
        return shown;
    }

    /** {@code true} enquanto há fade em andamento (o canvas precisa continuar redesenhando). */
    public boolean animating(long now) {
        return now - fadeStart < SLIDE_MS || marqueeActive;
    }
}
