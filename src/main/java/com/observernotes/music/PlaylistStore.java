package com.observernotes.music;

import java.io.IOException;

public interface PlaylistStore {

    /** Resultado da leitura: dados válidos (ou padrão) e, se algo estava errado, um aviso em português. */
    record Loaded(StoredPlayer data, String warning) { }

    Loaded load();

    void save(StoredPlayer data) throws IOException;

    /** Armazenamento que não grava nada (testes e execução sem pasta de dados). */
    PlaylistStore NONE = new PlaylistStore() {
        @Override
        public Loaded load() {
            return new Loaded(StoredPlayer.empty(), "");
        }

        @Override
        public void save(StoredPlayer data) { }
    };
}
