package com.observernotes.tools;

import com.observernotes.app.Bootstrap;
import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.controller.AppController;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.widget.TextInput;
import com.observernotes.ui.widget.UiAction;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import javax.imageio.ImageIO;

/**
 * Ferramenta de desenvolvimento: executa uma sessão real (núcleo + controlador, pasta temporária)
 * e renderiza as telas em PNG, sem abrir janela. Uso: {@code RealSessionPreview <pasta-de-saida>}.
 */
public final class RealSessionPreview {

    private RealSessionPreview() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File out = new File(args[0]);
        out.mkdirs();
        Path data = Files.createTempDirectory("observer-notes-preview");
        var started = Bootstrap.start(data, Clock.systemUTC());
        NotebookCanvas canvas = new NotebookCanvas(new AssetCatalog());
        canvas.setSize(1397, 742);
        AppController[] holder = new AppController[1];
        TextInput input = canvas.input();
        AppController c = new AppController(started.core(), started.failures(), input, ZoneId.of("America/Sao_Paulo"),
                () -> canvas.setModel(holder[0].model()), (t, ms) -> { });
        holder[0] = c;
        canvas.setModel(c.model());

        for (String t : new String[] {"Estudar o Observer", "Desenhar o diagrama", "Escrever o README",
            "Ensaiar a apresentação"}) {
            input.paste(t);
            c.handle(UiAction.of(UiAction.Type.ADD));
        }
        for (int i = 0; i < 2; i++) {
            c.handle(new UiAction(UiAction.Type.TOGGLE_TASK, canvas.model().tasks().get(i).id()));
        }
        render(canvas, new File(out, "real-tasks.png"));
        c.handle(UiAction.of(UiAction.Type.NAV_ALERTS));
        render(canvas, new File(out, "real-observers.png"));
        c.handle(UiAction.of(UiAction.Type.NAV_RECORD));
        render(canvas, new File(out, "real-history.png"));
        c.handle(UiAction.of(UiAction.Type.NAV_ALERTS));
        c.handle(new UiAction(UiAction.Type.TOGGLE_OBSERVER, "COUNTER"));
        c.handle(new UiAction(UiAction.Type.TOGGLE_OBSERVER, "CONSTELLATION"));
        render(canvas, new File(out, "real-observers-desligados.png"));
        c.handle(UiAction.of(UiAction.Type.NAV_LIST));
        input.paste("Sétima tarefa");
        c.handle(UiAction.of(UiAction.Type.ADD));
        c.handle(UiAction.of(UiAction.Type.ADD));
        for (String t : new String[] {"Quinta", "Sexta"}) {
            input.clear();
            input.paste(t);
            c.handle(UiAction.of(UiAction.Type.ADD));
        }
        input.clear();
        input.paste("Sétima tarefa");
        c.handle(UiAction.of(UiAction.Type.ADD));
        render(canvas, new File(out, "real-limite.png"));
    }

    private static void render(NotebookCanvas canvas, File f) throws Exception {
        BufferedImage img = new BufferedImage(1397, 742, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        canvas.paintScene(g, 1397, 742);
        g.dispose();
        ImageIO.write(img, "png", f);
    }
}
