package com.observernotes.persistence;

import java.util.List;

/**
 * Resultado de carregar dados. Carregar nunca lança por causa de arquivo ausente ou
 * inválido: devolve um valor utilizável e explica o que aconteceu.
 *
 * @param messages avisos em português para mostrar ao usuário ou registrar
 */
public record LoadResult<T>(T value, Status status, List<String> messages) {

    public enum Status {
        /** Arquivo lido e validado. */
        LOADED,
        /** Nenhum arquivo existia: começo do zero, nada foi perdido. */
        FRESH,
        /** O arquivo principal não servia; os dados vieram do backup. */
        RECOVERED_FROM_BACKUP,
        /** Nenhum arquivo servia; começou do zero. Os arquivos ruins foram preservados. */
        DISCARDED
    }

    public LoadResult {
        messages = List.copyOf(messages);
    }

    public boolean hasWarnings() {
        return !messages.isEmpty();
    }
}
