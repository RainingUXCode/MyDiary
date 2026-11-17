package com.observernotes.ui.assets;

/**
 * Catálogo de todos os PNGs usados pela interface.
 * Cada constante aponta para um arquivo em {@code src/main/resources/assets}.
 * Nenhum desenho é refeito: o código só carrega e escala estes arquivos.
 */
public enum Asset {
    // --- Moldura e estrutura -------------------------------------------------
    NOTEBOOK_FRAME("ui/caderno-moldura.png"),
    LOGO_HEART("ui/heart-stars.png"),
    TITLE_PINK("ui/titulo-rosa.png"),
    TITLE_PURPLE("ui/titulo-roxo.png"),
    BADGE_CLOUD("ui/cloud.png"),

    // --- Botões --------------------------------------------------------------
    BUTTON_LIST("ui/base-botao-list.png"),
    BUTTON_ALERTS("ui/base-botao-alerts.png"),
    BUTTON_RECORD("ui/base-botao-record.png"),
    BUTTON_ADD("ui/base-botao-add.png"),
    BUTTON_CLEAR("ui/base-botao-clear.png"),
    BUTTON_NEW("ui/base-botao-nova.png"),
    BUTTON_PLAYLIST("ui/botao-playlist.png"),

    // --- Tarefas -------------------------------------------------------------
    TASK_PENDING("ui/base-tarefa-pendente.png"),
    TASK_DONE("ui/base-tarefa-concluida.png"),
    TASK_EMPTY_SLOT("ui/campo_nova_tarefa.png"),
    CHECK_BOX("ui/check-area.png"),
    CHECK_MARK("ui/check-mark.png"),
    CLOSE_BUTTON("ui/close-button.png"),

    // --- Observadores e resumo ----------------------------------------------
    OBSERVER_CARD("ui/card_observador_base.png"),
    /** Cópia do cartão sem o cursor desenhado no original. */
    OBSERVER_CARD_CLEAN("ui/card_observador_limpo.png"),
    /** Cópia do campo tracejado sem o coração desenhado no original. */
    TEXT_FIELD_CLEAN("ui/campo_texto_limpo.png"),
    TOGGLE_ON("ui/on.png"),
    TOGGLE_OFF("ui/off.png"),
    ICON_HISTORY("ui/bookicon.png"),
    ICON_COUNTER("ui/heartbutterflyicon.png"),
    ICON_CONSTELLATION("ui/constelationicon.png"),
    SUMMARY_HEART("ui/bigpinkheart.png"),

    // --- Rodapé e player -----------------------------------------------------
    FOOTER_SLIDER("ui/base-rodape.png"),
    PLAYER_PREVIOUS("ui/anterior.png"),
    PLAYER_PAUSE("ui/pausar.png"),
    /** Gerado a partir do triângulo e do círculo do botão "próximo" (não havia PNG de "tocar" no mockup). */
    PLAYER_PLAY("ui/tocar.png"),
    PLAYER_NEXT("ui/proximo.png"),
    ICON_REPEAT("ui/icone-repetir.png"),
    ICON_SFX("ui/icone-sfx.png"),

    // --- Detalhes pequenos ---------------------------------------------------
    TINY_HEART("ui/tiny-heart.png"),
    TINY_STAR("ui/tiny-star.png"),
    TINY_BOOK("ui/tiny-book.png"),
    SPARK_LEFT("ui/tiny-left.png"),
    SPARK_RIGHT("ui/tiny-right.png"),
    HEART_PINK_DETAIL("ui/pink-detail-heart.png"),
    HEART_OUTLINE_SMALL("ui/small-heart.png"),

    // --- Conquistas ----------------------------------------------------------
    ACH_CARD_BASE("achievements/01_base_cartao.png"),
    ACH_CARD_REFERENCE("achievements/01_cartao_conquistas.png"),
    ACH_TITLE_RIBBON("achievements/02_faixa_titulo_sem_texto.png"),
    ACH_LEVEL_FIELD("achievements/04_campo_nivel_sem_texto.png"),
    ACH_PROGRESS_FIELD("achievements/05_campo_progresso_sem_texto.png"),
    ACH_MEDAL_BRONZE("achievements/02_medalha_bronze.png"),
    ACH_MEDAL_SILVER("achievements/03_medalha_prata.png"),
    ACH_MEDAL_GOLD("achievements/04_medalha_ouro.png"),
    ACH_ROSETTE_EMPTY("achievements/05_roseta_vazia.png"),
    ACH_STAR_ON("achievements/06_estrela_on.png"),
    ACH_STAR_OFF("achievements/07_estrela_off.png"),
    ACH_STAR_DECORATIVE("achievements/03_estrela_decorativa.png"),
    ACH_SPARKLES("achievements/08_brilhos.png"),
    ACH_GLINT_LEFT("achievements/10_brilho_esquerdo.png"),
    ACH_GLINT_RIGHT("achievements/11_brilho_direito.png"),
    ACH_MAX("achievements/09_conquista_maxima.png");

    private final String path;

    Asset(String path) {
        this.path = path;
    }

    /** Caminho relativo a {@code /assets/} no classpath. */
    public String path() {
        return path;
    }
}
