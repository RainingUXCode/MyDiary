package com.observernotes.tools;

import com.observernotes.ui.FooterRotation;
import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.widget.UiAction;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Prévias do rodapé reorganizado, da playlist padrão e dos títulos longos (sem janela). */
public final class FooterPreview {

    private FooterPreview() { }

    static final String[] TITLES = {"Sweet Morning", "Starry Notes", "Cotton Candy", "Pink Café", "Soft Breeze",
            "Sunny Day", "Dreamy Days"};

    private static PlayerModel model(String[] titles, int current, boolean playing, boolean panel, int offset) {
        List<PlayerModel.Row> rows = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            rows.add(new PlayerModel.Row("/m/" + i + ".mp3", titles[i], true, i == current));
        }
        return new PlayerModel(true, playing, !playing, titles[current], rows, 0.7, false, panel, offset);
    }

    private static void save(NotebookCanvas c, File out) throws Exception {
        BufferedImage img = new BufferedImage(1397, 742, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        c.paintScene(g, 1397, 742);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args[0]);
        dir.mkdirs();
        AtomicLong clock = new AtomicLong(1_000_000);
        NotebookCanvas c = new NotebookCanvas(new AssetCatalog(), clock::get);
        c.setSize(1397, 742);
        c.setModel(MockupSample.screen(PanelMode.TASKS));

        c.setPlayer(model(TITLES, 0, false, false, 0));                      // pausada: só notificação
        save(c, new File(dir, "rodape-1-notificacao.png"));

        c.setPlayer(model(TITLES, 0, true, false, 0));                       // começou a tocar: "Now Playing"
        save(c, new File(dir, "rodape-fade.png"));
        clock.addAndGet(FooterRotation.FADE_MS + 100);
        save(c, new File(dir, "rodape-2-now-playing.png"));
        clock.addAndGet(FooterRotation.PERIOD_MS);
        save(c, new File(dir, "rodape-fade2.png"));
        clock.addAndGet(FooterRotation.FADE_MS + 100);
        save(c, new File(dir, "rodape-3-volta-notificacao.png"));

        String[] longOne = {"♫ Uma música com um nome gigantesco que não cabe no rodapé nem com muito esforço do mundo", "B"};
        c.setPlayer(model(longOne, 0, true, false, 0));
        clock.addAndGet(FooterRotation.PERIOD_MS + 100);
        save(c, new File(dir, "rodape-longo-fade.png"));
        clock.addAndGet(FooterRotation.FADE_MS + 100);
        save(c, new File(dir, "rodape-4-titulo-longo.png"));

        c.setPlayer(model(TITLES, 3, true, true, 0));
        c.focusRing().set(UiAction.of(UiAction.Type.PLAYER_ADD));
        save(c, new File(dir, "playlist-padrao.png"));
        c.focusRing().clear();
        c.setPlayer(model(TITLES, 3, true, true, 2));
        save(c, new File(dir, "playlist-padrao-rolada.png"));

        ScreenModel s = MockupSample.screen(PanelMode.TASKS);
        ScreenModel longTasks = new ScreenModel(s.mode(),
                List.of(new TaskRowModel("1", "Curta", false),
                        new TaskRowModel("2", "Preparar a apresentação de conclusão do semestre com todos os gráficos e referências", false),
                        new TaskRowModel("3", "Revisão ortográfica: ação, coração, órgão, avó, pé, três, ônibus, você", true),
                        new TaskRowModel("4", "Entregar o relatório final de Engenharia de Software ao professor orientador amanhã cedo", true)),
                s.observers(), s.history(), s.starsLit(), s.starsTotal(), s.achievementLabel(), s.achievementState(),
                s.achievementTier(), s.footerMessage(), s.newAchievement());
        c.setModel(longTasks);
        c.setPlayer(model(TITLES, 0, false, false, 0));
        save(c, new File(dir, "tarefas-titulos-longos.png"));
        System.exit(0);
    }
}
