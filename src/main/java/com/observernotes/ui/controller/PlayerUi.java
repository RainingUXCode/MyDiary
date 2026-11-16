package com.observernotes.ui.controller;

import com.observernotes.music.MusicPlayer;
import com.observernotes.music.PlaybackState;
import com.observernotes.music.PlayerSnapshot;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Ponte entre a interface e o {@link MusicPlayer}: traduz ações do usuário em comandos do player,
 * guarda só o estado visual do painel da playlist (aberto/rolagem) e transforma mudanças reais do
 * player em efeitos visuais e avisos. Não conhece tarefas, observadores nem pontuação.
 */
public final class PlayerUi {

    private static final double VOLUME_STEP = 0.1;

    private final MusicPlayer music;
    private final Supplier<List<Path>> picker;
    private final BiConsumer<String, Boolean> notifier;
    private final EffectSink effects;

    private boolean panelOpen;
    private int offset;
    private PlaybackState lastState = PlaybackState.STOPPED;
    private String lastTitle = "";
    private String lastMessage = "";

    /**
     * @param picker   abre o seletor de arquivos e devolve o que o usuário escolheu (vazio se cancelou)
     * @param notifier mostra um aviso no rodapé (texto, é-alerta)
     */
    public PlayerUi(MusicPlayer music, Supplier<List<Path>> picker, BiConsumer<String, Boolean> notifier,
                    EffectSink effects) {
        this.music = music;
        this.picker = picker;
        this.notifier = notifier;
        this.effects = effects == null ? EffectSink.NONE : effects;
    }

    public PlayerModel model() {
        PlayerSnapshot s = music.snapshot();
        List<PlayerModel.Row> rows = s.tracks().stream()
                .map(t -> new PlayerModel.Row(t.path(), t.title(), t.available(), t.current())).toList();
        return new PlayerModel(true, s.state() == PlaybackState.PLAYING, s.state() == PlaybackState.PAUSED,
                s.currentTitle(), rows, s.volume(), s.repeat(), panelOpen, offset);
    }

    public void closePanel() {
        panelOpen = false;
    }

    /** Executa a ação do player. Chamado pelo controlador para qualquer {@code UiAction.Type.PLAYER_*}. */
    public void handle(UiAction a) {
        switch (a.type()) {
            case PLAYER_PREVIOUS -> music.previous();
            case PLAYER_NEXT -> music.next();
            case PLAYER_TOGGLE -> music.toggle();
            case PLAYER_ADD -> addFiles();
            case PLAYER_PANEL -> panelOpen = !panelOpen;
            case PLAYER_PLAY -> withTrack(a.key(), music::playIndex);
            case PLAYER_REMOVE -> withTrack(a.key(), music::remove);
            case PLAYER_UP -> withTrack(a.key(), i -> music.move(i, -1));
            case PLAYER_DOWN -> withTrack(a.key(), i -> music.move(i, 1));
            case PLAYER_REPEAT -> music.toggleRepeat();
            case PLAYER_VOLUME -> volume(a.key());
            case PLAYER_SCROLL -> scroll(a.key());
            default -> { }
        }
        clampOffset();
        onMusicChanged();
    }

    /**
     * Compara o player com o último estado visto e dispara efeitos/avisos só para o que realmente
     * mudou. Seguro para chamar várias vezes (também pelo retorno assíncrono do áudio).
     */
    public void onMusicChanged() {
        PlayerSnapshot s = music.snapshot();
        boolean wasPlaying = lastState == PlaybackState.PLAYING;
        boolean isPlaying = s.state() == PlaybackState.PLAYING;
        if (isPlaying != wasPlaying) {
            fx(() -> effects.musicState(isPlaying));
        }
        if (isPlaying && (lastState == PlaybackState.STOPPED || !s.currentTitle().equals(lastTitle))) {
            fx(() -> effects.musicTrack(s.currentTitle()));
        }
        lastState = s.state();
        lastTitle = s.currentTitle();
        if (!s.message().equals(lastMessage)) {
            lastMessage = s.message();
            if (!lastMessage.isEmpty()) {
                notifier.accept(lastMessage, true);
            }
        }
        clampOffset();
    }

    private void addFiles() {
        List<Path> chosen;
        try {
            chosen = picker.get();
        } catch (RuntimeException e) {
            notifier.accept("Não foi possível abrir o seletor de arquivos", true);
            return;
        }
        if (chosen == null || chosen.isEmpty()) {
            return;                                                      // cancelou
        }
        var r = music.addFiles(chosen);
        panelOpen = true;
        if (r.added() > 0) {
            String text = r.added() + (r.added() == 1 ? " música adicionada" : " músicas adicionadas");
            if (r.unsupported() > 0) {
                text += " (" + r.unsupported() + " de formato não suportado ignorada" + (r.unsupported() > 1 ? "s" : "") + ")";
            }
            notifier.accept(text, false);
        } else {
            notifier.accept(r.unsupported() > 0 ? "Formato não suportado: use MP3 ou WAV"
                    : "Essas músicas já estão na playlist", true);
        }
    }

    private void volume(String key) {
        if (key.equals("+")) {
            music.adjustVolume(VOLUME_STEP);
        } else if (key.equals("-")) {
            music.adjustVolume(-VOLUME_STEP);
        } else if (key.startsWith("set:")) {
            try {
                music.setVolume(Double.parseDouble(key.substring(4)));
            } catch (NumberFormatException ignored) {
                // chave inválida: ignora
            }
        }
    }

    private void scroll(String key) {
        int max = Math.max(0, music.snapshot().tracks().size() - PlayerModel.PANEL_ROWS);
        switch (key) {
            case "home" -> offset = 0;
            case "end" -> offset = max;
            default -> {
                try {
                    offset += Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    // ignora
                }
            }
        }
    }

    private void clampOffset() {
        int max = Math.max(0, music.snapshot().tracks().size() - PlayerModel.PANEL_ROWS);
        offset = Math.max(0, Math.min(offset, max));
    }

    private void withTrack(String path, java.util.function.IntConsumer action) {
        var tracks = music.snapshot().tracks();
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).path().equals(path)) {
                action.accept(i);
                return;
            }
        }
    }

    private void fx(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // efeito visual nunca interfere no áudio
        }
    }
}
