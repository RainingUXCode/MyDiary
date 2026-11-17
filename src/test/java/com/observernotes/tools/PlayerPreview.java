package com.observernotes.tools;

import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.UiAction;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Gera prévias do player (sem janela): rodapé parado/tocando, balãozinho, pulso e painel da playlist. */
public final class PlayerPreview {

    private PlayerPreview() { }

    private static PlayerModel model(int tracks, int current, boolean playing, boolean panel, int offset) {
        List<PlayerModel.Row> rows = new ArrayList<>();
        String[] names = {"Lofi para estudar", "Trilha do caderninho", "Sunset Drive", "Tema da Sue", "Chuva de verão",
                "Faixa que sumiu", "Piano suave"};
        for (int i = 0; i < tracks; i++) {
            rows.add(new PlayerModel.Row("/musicas/" + names[i % names.length] + ".mp3", names[i % names.length],
                    i != 5, i == current));
        }
        return new PlayerModel(true, playing, !playing && current >= 0, current >= 0 ? names[current % names.length] : "",
                rows, 0.7, panel && tracks > 3, panel, offset);
    }

    private static void save(NotebookCanvas c, File out) throws Exception {
        ImageIO.write(render(c), "png", out);
    }

    private static BufferedImage render(NotebookCanvas c) {
        BufferedImage img = new BufferedImage(1397, 742, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        c.paintScene(g, 1397, 742);
        g.dispose();
        return img;
    }

    /** Recorte no tamanho real: coluna esquerda ativa, coluna direita desativada. */
    private static void saveAudioButtonStates(NotebookCanvas c, File out) throws Exception {
        PlayerModel active = model(7, 1, true, true, 0);
        c.setPlayer(active);
        BufferedImage on = render(c);
        c.setPlayer(new PlayerModel(active.available(), active.playing(), active.paused(), active.title(), active.rows(),
                active.volume(), false, active.panelOpen(), active.panelOffset(), false, active.sfxVolume()));
        BufferedImage off = render(c);

        var repeat = MainScene.repeatRect();
        var sfx = MainScene.sfxToggleRect();
        int x = (int) Math.floor(Math.min(repeat.getX(), sfx.getX()) - 8);
        int y = (int) Math.floor(repeat.getY() - 8);
        int w = (int) Math.ceil(Math.max(repeat.getMaxX(), sfx.getMaxX()) + 8) - x;
        int h = (int) Math.ceil(sfx.getMaxY() + 8) - y;
        int gap = 18;
        BufferedImage preview = new BufferedImage(w * 2 + gap, h, BufferedImage.TYPE_INT_RGB);
        var g = preview.createGraphics();
        g.setColor(com.observernotes.ui.Palette.DESK);
        g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        g.drawImage(on.getSubimage(x, y, w, h), 0, 0, null);
        g.drawImage(off.getSubimage(x, y, w, h), w + gap, 0, null);
        g.dispose();
        ImageIO.write(preview, "png", out);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args[0]);
        dir.mkdirs();
        AtomicLong clock = new AtomicLong(1000);
        NotebookCanvas c = new NotebookCanvas(new AssetCatalog(), clock::get);
        c.setSize(1397, 742);
        c.setModel(MockupSample.screen(PanelMode.TASKS));
        saveAudioButtonStates(c, new File(dir, "audio-buttons-active-disabled.png"));

        c.setPlayer(model(7, 0, false, false, 0));
        save(c, new File(dir, "player-parado.png"));

        c.setPlayer(model(7, 1, true, false, 0));
        c.effects().musicState(true);
        c.effects().musicTrack("Trilha do caderninho");
        clock.addAndGet(300);
        save(c, new File(dir, "player-tocando-balao.png"));
        clock.addAndGet(3000);

        c.setPlayer(model(7, 1, true, true, 0));
        c.focusRing().set(new UiAction(UiAction.Type.PLAYER_UP, "/musicas/Sunset Drive.mp3"));
        save(c, new File(dir, "player-playlist.png"));

        c.focusRing().clear();
        c.setPlayer(model(7, 1, false, true, 2));
        save(c, new File(dir, "player-playlist-rolada-pausada.png"));

        c.setPlayer(model(0, -1, false, true, 0));
        save(c, new File(dir, "player-playlist-vazia.png"));

        c.setModel(MockupSample.screen(PanelMode.HISTORY));
        c.setPlayer(model(7, 3, true, false, 0));
        c.focusRing().set(UiAction.of(UiAction.Type.PLAYER_TOGGLE));
        save(c, new File(dir, "player-historico-foco.png"));
        System.exit(0);
    }
}
