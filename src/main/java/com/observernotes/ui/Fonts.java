package com.observernotes.ui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tipografia do Observer Notes: uma só família, <b>Cloud Soft Bold</b>, carregada de dentro do aplicativo.
 * <ul>
 *   <li>{@link #display(float)}: títulos e botões (desenhados com contorno colorido onde o mockup tem);</li>
 *   <li>{@link #body(float)}: textos dinâmicos (tarefas, histórico, notificações, nomes de músicas, contadores,
 *       descrições), sempre sem contorno; o tamanho pedido é reduzido a 92% porque a Bold é larga;</li>
 *   <li>{@link #secondary(float)}: textos de apoio e placeholders (mesma fonte, 90%; a cor mais discreta vem da cena).</li>
 * </ul>
 * Nunito e Cloud Soft Light não são usadas. Cloud Soft é "uso pessoal": não há autorização para uso comercial nem
 * para redistribuir (ver {@code docs/fonts-licencas/}). Se o arquivo faltar, entra uma fonte do Java.
 */
public final class Fonts {

    static final String DISPLAY_FILE = "CloudSoft-Bold.otf";
    /** Texto que uma fonte precisa desenhar de verdade (não em branco) para servir aos textos em português. */
    static final String PROBE = "aeiouAEIOU0123456789áéíóúãõç";

    private final Font display;
    private final boolean displayCustom;

    private Fonts(Font display, boolean displayCustom) {
        this.display = display;
        this.displayCustom = displayCustom;
    }

    public static Fonts load() {
        Font d = tryLoad(DISPLAY_FILE);
        return new Fonts(d != null && isFullyDrawn(d) ? d : new Font(Font.SANS_SERIF, Font.BOLD, 12),
                d != null && isFullyDrawn(d));
    }

    /** Textos dinâmicos, sem contorno (Cloud Soft Bold, 92% do tamanho pedido). */
    public Font body(float size) {
        return display.deriveFont(size * 0.92f);
    }

    /** Textos secundários e placeholders (Cloud Soft Bold, 90%). */
    public Font secondary(float size) {
        return display.deriveFont(size * 0.90f);
    }

    /** Títulos e botões (Cloud Soft Bold). */
    public Font display(float size) {
        return display.deriveFont(size);
    }

    /** {@code true} se a Cloud Soft Bold foi carregada de dentro do aplicativo. */
    public boolean usesOriginalFonts() {
        return displayCustom;
    }

    /** Nome da fonte dos textos escritos (para documentação e testes). */
    public String bodyFontName() {
        return display.getFontName();
    }

    /** {@code true} se todos os caracteres de {@link #PROBE} têm contorno desenhado. */
    static boolean isFullyDrawn(Font font) {
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        for (int i = 0; i < PROBE.length(); i++) {
            String ch = PROBE.substring(i, i + 1);
            if (!font.canDisplay(ch.charAt(0))
                    || font.createGlyphVector(frc, ch).getOutline().getBounds2D().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Font tryLoad(String file) {
        try (InputStream in = Fonts.class.getResourceAsStream("/assets/fonts/" + file)) {
            if (in != null) {
                return Font.createFont(Font.TRUETYPE_FONT, in);      // OTF (CFF) também é aceito pelo Java 17
            }
        } catch (FontFormatException | IOException e) {
            System.getLogger("observernotes.ui").log(System.Logger.Level.WARNING,
                    "Fonte " + file + " inválida; usando a provisória", e);
        }
        return null;
    }
}
