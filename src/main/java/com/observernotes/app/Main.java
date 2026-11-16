package com.observernotes.app;

import com.observernotes.music.DefaultPlaylist;
import com.observernotes.music.JavaSoundBackend;
import com.observernotes.music.JsonPlaylistStore;
import com.observernotes.music.MusicPlayer;
import com.observernotes.persistence.UserDataDirectory;
import com.observernotes.ui.MainWindow;
import java.nio.file.Path;
import java.time.Clock;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Ponto de entrada. */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Path dir = UserDataDirectory.resolve();
            try {
                Bootstrap.Started started = Bootstrap.start(dir, Clock.systemUTC());
                try {
                    DefaultPlaylist.seedIfNeeded(dir, new JsonPlaylistStore(dir));    // 1ª execução: músicas padrão
                } catch (RuntimeException ignored) {
                    // a playlist padrão é um extra: o app abre mesmo assim
                }
                MainWindow window = new MainWindow(started.core(), started.failures(),
                        onChange -> new MusicPlayer(new JavaSoundBackend(), new JsonPlaylistStore(dir), onChange));
                window.setVisible(true);
                window.showStartupMessages();
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(null,
                        "Não foi possível iniciar o Observer Notes.\nPasta de dados: " + dir + "\n\n" + e.getMessage(),
                        "Observer Notes", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
