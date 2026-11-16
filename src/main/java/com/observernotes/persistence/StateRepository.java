package com.observernotes.persistence;

/** Porta de persistência do estado (tarefas, conquistas e histórico). */
public interface StateRepository {

    /** Nunca lança por dados ausentes ou inválidos; veja {@link LoadResult}. */
    LoadResult<AppSnapshot> load();

    /** @throws PersistenceException se não foi possível gravar; o arquivo anterior fica intacto */
    void save(AppSnapshot snapshot);
}
