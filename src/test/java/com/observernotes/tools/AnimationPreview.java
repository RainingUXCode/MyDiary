package com.observernotes.tools;

import com.observernotes.app.Bootstrap;
import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.controller.AppController;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.widget.UiAction;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/**
 * Ferramenta de desenvolvimento: executa uma sessão real com relógio manual e grava "folhas de
 * quadros" (PNG) das animações. Uso: {@code AnimationPreview <pasta-de-saida>}.
 */
public final class AnimationPreview {

    private static final AtomicLong NOW = new AtomicLong(1_000);

    private AnimationPreview() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File out = new File(args[0]);
        out.mkdirs();
        var started = Bootstrap.start(Files.createTempDirectory("on-anim"), Clock.systemUTC());
        NotebookCanvas canvas = new NotebookCanvas(new AssetCatalog(), NOW::get);
        canvas.setSize(1397, 742);
        AppController[] h = new AppController[1];
        AppController c = new AppController(started.core(), started.failures(), canvas.input(), ZoneId.of("America/Sao_Paulo"),
                () -> canvas.setModel(h[0].model()), (t, ms) -> { }, canvas.effects());
        h[0] = c;
        canvas.setModel(c.model());

        // 1) criar -> concluir -> excluir (tarefas)
        add(canvas, c, "Estudar o Observer");
        add(canvas, c, "Desenhar o diagrama");
        add(canvas, c, "Escrever o README");
        String second = canvas.model().tasks().get(1).id();
        NOW.addAndGet(500);
        // quadros: criação da 3ª, depois conclusão da 2ª
        sheet(canvas, new File(out, "anim-tarefas-criar.png"), new int[] {0, 80, 160, 300}, 3, 80);
        c.handle(new UiAction(UiAction.Type.TOGGLE_TASK, second));
        sheet(canvas, new File(out, "anim-tarefas-concluir.png"), new int[] {0, 120, 250, 400}, 4, 130);
        // estrela (1ª conquista) foi disparada junto
        NOW.addAndGet(2000);
        c.handle(new UiAction(UiAction.Type.DELETE_TASK, canvas.model().tasks().get(0).id()));
        sheet(canvas, new File(out, "anim-tarefas-excluir.png"), new int[] {0, 90, 180, 280}, 4, 90);

        // 2) estrelas e medalhas: conclui e exclui para ganhar marcos novos (sem repetir tarefas)
        for (int i = 2; i <= 16; i++) {
            NOW.addAndGet(5000);
            canvas.effects().clear();
            add(canvas, c, "T" + i);
            canvas.effects().clear();
            String id = canvas.model().tasks().get(canvas.model().tasks().size() - 1).id();
            c.handle(new UiAction(UiAction.Type.TOGGLE_TASK, id));
            int total = started.core().service().achievements().totalScored();
            if (total == 2 || total == 3) {
                sheet(canvas, new File(out, "anim-estrela-" + total + ".png"), new int[] {100, 350, 700, 1100}, 4, 0);
            }
            if (total == 4) {
                sheet(canvas, new File(out, "anim-bronze.png"), new int[] {150, 500, 1000, 1700, 2400}, 5, 0);
            }
            if (total == 8) {
                sheet(canvas, new File(out, "anim-prata.png"), new int[] {150, 500, 1000, 1700, 2400}, 5, 0);
            }
            if (total == 12) {
                sheet(canvas, new File(out, "anim-ouro.png"), new int[] {150, 500, 1000, 1700, 2400}, 5, 0);
            }
            if (total == 16) {
                sheet(canvas, new File(out, "anim-maxima.png"), new int[] {150, 500, 1000, 1800, 2800}, 5, 0);
            }
            canvas.effects().clear();
            c.handle(new UiAction(UiAction.Type.DELETE_TASK, id));
            if (total >= 16) {
                break;
            }
        }
        System.exit(0);       // o temporizador do Swing mantém a JVM viva
    }

    private static void add(NotebookCanvas canvas, AppController c, String title) {
        canvas.input().clear();
        canvas.input().paste(title);
        c.handle(UiAction.of(UiAction.Type.ADD));
    }

    /** Avança o relógio manual e junta os quadros lado a lado (recortados na área do efeito). */
    private static void sheet(NotebookCanvas canvas, File file, int[] times, int cols, int unused) throws Exception {
        long base = NOW.get();
        int w = 1397;
        int h = 742;
        int cw = 700;
        int ch = 371;
        BufferedImage[] frames = new BufferedImage[times.length];
        for (int i = 0; i < times.length; i++) {
            NOW.set(base + times[i]);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            canvas.paintScene(g, w, h);
            g.dispose();
            frames[i] = img;
        }
        NOW.set(base);
        int rows = (times.length + 1) / 2;
        BufferedImage sheet = new BufferedImage(cw * 2, ch * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int i = 0; i < frames.length; i++) {
            g.drawImage(frames[i], (i % 2) * cw, (i / 2) * ch, cw, ch, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }
}
