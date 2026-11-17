package com.observernotes.ui.model;

import java.util.List;

/**
 * O que a interface precisa saber do player musical para desenhar (dados puros, sem áudio).
 * {@link #NONE} é o estado "sem player": o rodapé continua como na Etapa 4C.1 (só visual).
 */
public record PlayerModel(boolean available, boolean playing, boolean paused, String title, List<Row> rows,
                          double volume, boolean repeat, boolean panelOpen, int panelOffset,
                          boolean sfxEnabled, double sfxVolume) {

    /** Faixas visíveis ao mesmo tempo no painel da playlist (a sexta linha tem repetir e volume). */
    public static final int PANEL_ROWS = 4;

    /** @param path chave estável da faixa (o clique devolve o caminho, nunca a posição da linha) */
    public record Row(String path, String title, boolean available, boolean current) { }

    public static final PlayerModel NONE = new PlayerModel(false, false, false, "", List.of(), 0.7, false, false, 0,
            true, 0.30);

    /** Mantém compatibilidade com ferramentas e testes que não precisam conhecer SFX. */
    public PlayerModel(boolean available, boolean playing, boolean paused, String title, List<Row> rows,
                       double volume, boolean repeat, boolean panelOpen, int panelOffset) {
        this(available, playing, paused, title, rows, volume, repeat, panelOpen, panelOffset, true, 0.30);
    }

    public PlayerModel {
        rows = List.copyOf(rows);
        panelOffset = Math.max(0, Math.min(panelOffset, Math.max(0, rows.size() - PANEL_ROWS)));
        volume = Math.max(0.0, Math.min(1.0, volume));
        sfxVolume = Math.max(0.0, Math.min(1.0, sfxVolume));
    }

    public PlayerModel withSfx(boolean enabled, double volume) {
        return new PlayerModel(available, playing, paused, title, rows, this.volume, repeat, panelOpen, panelOffset,
                enabled, volume);
    }

    public int maxOffset() {
        return Math.max(0, rows.size() - PANEL_ROWS);
    }

    public boolean hasTracks() {
        return !rows.isEmpty();
    }
}
