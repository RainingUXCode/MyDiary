package com.observernotes.ui;

import com.observernotes.application.AppCore;
import com.observernotes.music.MusicPlayer;
import com.observernotes.music.SupportedFormats;
import com.observernotes.ui.controller.PlayerUi;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.controller.AppController;
import com.observernotes.ui.controller.SaveFailureTracker;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;

/** Janela principal: hospeda o canvas e liga a interface ao núcleo. */
public final class MainWindow extends JFrame {

    private final AppCore core;
    private MusicPlayer music;
    private PlayerUi playerUi;
    private NotebookCanvas canvas;
    private File lastMusicDir;

    public MainWindow(AppCore core, SaveFailureTracker failures) {
        this(core, failures, null);
    }

    /**
     * @param musicFactory cria o player recebendo o aviso "algo mudou" (pode vir de qualquer thread);
     *                     {@code null} = sem player (rodapé só visual)
     */
    public MainWindow(AppCore core, SaveFailureTracker failures, Function<Runnable, MusicPlayer> musicFactory) {
        super("Observer Notes");
        this.core = core;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        canvas = new NotebookCanvas(new AssetCatalog());
        AppController[] holder = new AppController[1];
        AppController controller = new AppController(core, failures, canvas.input(), ZoneId.systemDefault(),
                () -> {
                    canvas.setModel(holder[0].model());
                    canvas.setPlayer(holder[0].playerModel());
                }, MainWindow::later, canvas.effects());
        holder[0] = controller;
        if (musicFactory != null) {
            music = musicFactory.apply(() -> SwingUtilities.invokeLater(() -> {
                playerUi.onMusicChanged();
                canvas.setPlayer(controller.playerModel());
            }));
            playerUi = new PlayerUi(music, this::pickMusicFiles, controller::notifyUser, canvas.effects());
            controller.attachPlayer(playerUi);
        }
        canvas.setActionListener(controller::handle);
        canvas.setModel(controller.model());
        canvas.setPlayer(controller.playerModel());
        add(canvas);
        getContentPane().setPreferredSize(new Dimension(1100, 584));
        setMinimumSize(new Dimension(700, 400));
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeSafely();
            }
        });
    }

    /** Avisos da partida (arquivo recuperado, inválido...), mostrados em português compreensível. */
    public void showStartupMessages() {
        var messages = new java.util.ArrayList<>(core.startupMessages());
        if (music != null && !music.startupWarning().isEmpty()) {
            messages.add(music.startupWarning());
        }
        if (!messages.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n\n", messages), "Observer Notes",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Grava uma última vez; se não der, pergunta antes de fechar para não perder dados sem avisar. */
    private void closeSafely() {
        if (!core.saveNow()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Não foi possível salvar suas alterações. Se fechar agora, elas podem ser perdidas.\nFechar mesmo assim?",
                    "Observer Notes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (music != null) {
            music.close();                                  // para o áudio e libera a placa de som
        }
        dispose();
        System.exit(0);
    }

    /** Seletor de arquivos do sistema (várias músicas de uma vez). Devolve vazio se o usuário cancelar. */
    private List<Path> pickMusicFiles() {
        JFileChooser chooser = new JFileChooser(lastMusicDir);
        chooser.setDialogTitle("Adicionar músicas");
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Músicas (MP3, WAV, AIFF, AU)",
                SupportedFormats.EXTENSIONS.toArray(new String[0])));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return List.of();
        }
        lastMusicDir = chooser.getCurrentDirectory();
        return Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList();
    }

    private static void later(Runnable task, Integer millis) {
        Timer t = new Timer(millis, e -> task.run());
        t.setRepeats(false);
        t.start();
    }
}
