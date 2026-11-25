package com.observernotes.tools;

import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Ferramenta de desenvolvimento: renderiza o canvas em um PNG, sem abrir janela.
 * Uso: {@code PreviewRenderer saida.png largura altura [tasks|observers|history] [sample|empty] [texto] [none|bronze|silver|gold|max]}.
 * Com {@code texto}, o campo de texto aparece focado com esse conteúdo.
 */
public final class PreviewRenderer {

    private PreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int w = Integer.parseInt(args[1]);
        int h = Integer.parseInt(args[2]);
        PanelMode mode = args.length > 3 ? PanelMode.valueOf(args[3].toUpperCase()) : PanelMode.TASKS;
        boolean sample = args.length > 4 && args[4].equals("sample");
        NotebookCanvas canvas = new NotebookCanvas(new AssetCatalog());
        canvas.setSize(w, h);
        canvas.setModel(sample ? MockupSample.screen(mode) : ScreenModel.empty().withMode(mode));
        if (sample && args.length > 6) {
            ScreenModel b = canvas.model();
            canvas.setModel(new ScreenModel(b.mode(), b.tasks(), b.observers(), b.history(), b.littleNotes(),
                    "Conquistas", args[6].toLowerCase(),
                    com.observernotes.ui.model.AchievementTier.valueOf(args[6].toUpperCase()), b.footerMessage(),
                    b.newAchievement()));
        }
        if (args.length > 5 && !args[5].isEmpty()) {
            canvas.input().setFocused(true);
            canvas.input().paste(args[5]);
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        canvas.paintScene(g, w, h);
        g.dispose();
        ImageIO.write(out, "png", new File(args[0]));
    }
}
