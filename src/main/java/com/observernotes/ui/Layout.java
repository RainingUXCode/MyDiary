package com.observernotes.ui;

import java.awt.geom.Rectangle2D;

/**
 * Medidas do espaço lógico da interface (a moldura do caderno define o tamanho) e as áreas de
 * cada elemento, medidas sobre o mockup. As áreas foram lidas em pixels do mockup e convertidas
 * por {@link #mock}; a conversão é única, então corrigir um desvio é mexer em um número aqui.
 */
public final class Layout {

    /** Largura da moldura (px lógicos). */
    public static final int WIDTH = 1397;
    /** Altura da moldura (px lógicos). */
    public static final int HEIGHT = 742;

    /** Converte um retângulo medido na grade de conferência do mockup (x0,y0,x1,y1) para o espaço lógico. */
    static Rectangle2D mock(double x0, double y0, double x1, double y1) {
        double ax = 12.8, bx = 0.8611, ay = 43.1, by = 0.8661;
        return new Rectangle2D.Double(ax + bx * x0, ay + by * y0, bx * (x1 - x0), by * (y1 - y0));
    }

    // --- Coluna esquerda ---
    public static final Rectangle2D LOGO = mock(126, 58, 338, 245);
    // Proporções relativas medidas no Canva: LIST 6,18 × 2,34; ALERTS 6,51 × 2,30;
    // RECORD 6,95 × 2,43. Os três permanecem no mesmo eixo central.
    public static final Rectangle2D NAV_LIST = mock(137.75, 264.45, 326.25, 338.55);
    public static final Rectangle2D NAV_ALERTS = mock(132.70, 361.55, 331.30, 434.45);
    public static final Rectangle2D NAV_RECORD = mock(126, 456, 338, 533);

    // --- Topo ---
    public static final Rectangle2D TEXT_FIELD = mock(358, 68, 1190, 133);
    public static final Rectangle2D ADD = mock(1222, 65, 1462, 133);

    // --- Painel central (Tarefas / Observadores / Histórico) ---
    public static final Rectangle2D MAIN_PANEL = mock(351, 170, 966, 655);
    public static final Rectangle2D MAIN_RIBBON = mock(395, 155, 617, 217);
    /** Faixa mais larga, para títulos longos (Observadores). */
    public static final Rectangle2D MAIN_RIBBON_WIDE = mock(380, 155, 650, 217);
    /** Botão CLEAR do histórico: fica por inteiro dentro do painel, sem tocar a borda nem a primeira linha. */
    public static final Rectangle2D CLEAR = mock(815, 180, 945, 229);
    public static final Rectangle2D MAIN_BADGE = mock(852, 152, 950, 216);
    /** Paginação centralizada no vão de ~202 px entre a faixa Tarefas e a nuvem. */
    public static final Rectangle2D TASK_PAGE_PREVIOUS = mock(688, 177, 725, 214);
    public static final Rectangle2D TASK_PAGE_NEXT = mock(744, 177, 781, 214);
    public static final double ROW_X0 = mock(381, 0, 938, 0).getX();
    public static final double ROW_W = mock(381, 0, 938, 0).getWidth();
    /** Topo (y, grade) de cada uma das 6 linhas e altura de cada uma. */
    private static final double[] ROW_TOP = {231, 298, 366, 433, 505, 572};
    private static final double[] ROW_BOTTOM = {290, 354, 424, 490, 560, 628};

    public static Rectangle2D row(int index) {
        return mock(381, ROW_TOP[index], 938, ROW_BOTTOM[index]);
    }

    public static int rowCount() {
        return ROW_TOP.length;
    }

    // --- Painel Resumo ---
    public static final Rectangle2D SUMMARY_PANEL = mock(991, 167, 1474, 650);
    public static final Rectangle2D SUMMARY_RIBBON = mock(1027, 153, 1228, 212);
    public static final Rectangle2D SUMMARY_HEART = mock(1037, 232, 1208, 352);
    public static final Rectangle2D SUMMARY_TEXT = mock(1236, 255, 1385, 325);
    public static final Rectangle2D PROGRESS_BAR = mock(1037, 364, 1430, 401);
    public static final Rectangle2D CONSTELLATION_TILE = mock(1060, 428, 1203, 556);
    public static final Rectangle2D CONSTELLATION_LABEL = mock(1020, 564, 1245, 630);
    public static final Rectangle2D ACHIEVEMENT_TILE = mock(1290, 428, 1390, 560);
    public static final Rectangle2D ACHIEVEMENT_LABEL = mock(1245, 564, 1440, 630);

    // --- Rodapé ---
    // Player musical: PLAYER_PILL reserva o espaço dos três controles (anterior / tocar-pausar /
    // próximo) e FOOTER_MESSAGE o da mensagem. Na etapa do player, os controles ganham ButtonId e
    // ação sem mudar nenhuma destas áreas.
    /** Cápsula compacta, centralizada no mesmo eixo dos três botões da coluna esquerda. */
    public static final Rectangle2D PLAYER_PILL = mock(144, 680, 320, 734);
    public static final Rectangle2D PLAYER_PREVIOUS = mock(151, 684, 197, 730);
    public static final Rectangle2D PLAYER_PAUSE = mock(209, 684, 255, 730);
    public static final Rectangle2D PLAYER_NEXT = mock(267, 684, 313, 730);
    /** O espaço liberado pelo player passa para mensagens de tarefas e nomes de músicas. */
    public static final Rectangle2D FOOTER_MESSAGE = mock(351, 680, 1212, 734);
    /** "+ Música" dentro do painel da playlist (acima da primeira linha, como o CLEAR do histórico). */
    // Alinha a borda direita à borda externa das linhas de música, antes da rolagem.
    public static final Rectangle2D ADD_MUSIC = mock(823, 184, 938, 224);
    /** Botão amarelo "♪ Playlist" (abre e fecha o painel da playlist). */
    /** Compensa a margem transparente interna do PNG de Playlist para igualar sua altura aparente ao rodapé. */
    public static final Rectangle2D NEW_BADGE = mock(1222.55, 674.49, 1414.15, 738.01);
    public static final Rectangle2D FOOTER_HEART = mock(1437, 690, 1475, 724);

    private Layout() { }
}
